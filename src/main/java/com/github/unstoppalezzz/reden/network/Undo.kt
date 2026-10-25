package com.github.unstoppalezzz.reden.network

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.access.BlockEntityInterface
import com.github.unstoppalezzz.reden.access.ChunkSectionInterface
import com.github.unstoppalezzz.reden.access.PlayerData
import com.github.unstoppalezzz.reden.access.PlayerData.Companion.data
import com.github.unstoppalezzz.reden.access.UndoableAccess
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.modified
import com.github.unstoppalezzz.reden.undo.SurvivalUndo
import com.github.unstoppalezzz.reden.utils.debugLogger
import com.github.unstoppalezzz.reden.utils.isDebug
import com.github.unstoppalezzz.reden.utils.multiver.*
import com.github.unstoppalezzz.reden.utils.server
import com.github.unstoppalezzz.reden.utils.setBlockNoPP
import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
//?}
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.item.PrimedTnt
import net.minecraft.world.level.block.entity.BlockEntity

@Serializable
class Undo(
    val status: Int = 0
) : CustomPacketPayload {
    override fun type() = ID

    companion object : PacketCodecHelper<Undo> by PacketCodec(Reden.identifier("undo")) {
        const val STATUS_CREATIVE_RECORD = 17
        const val STATUS_ENTITY_GONE = 18
        const val REQUEST_CREATIVE_ONLY = 128
        const val REQUEST_ALLOW_SURVIVAL = 129

        private fun isUpdateSensitive(state: net.minecraft.world.level.block.state.BlockState): Boolean {
            val block = state.block
            return block is net.minecraft.world.level.block.TripWireBlock ||
                block is net.minecraft.world.level.block.TripWireHookBlock ||
                block is net.minecraft.world.level.block.DetectorRailBlock ||
                block is net.minecraft.world.level.block.ObserverBlock ||
                block is net.minecraft.world.level.block.DispenserBlock ||
                block is net.minecraft.world.level.block.piston.PistonBaseBlock ||
                block is net.minecraft.world.level.block.piston.PistonHeadBlock
        }

        private fun refreshComparatorState(world: ServerLevel, pos: BlockPos) {
            try {
                val state = world.getBlockState(pos)
                if (state.block == net.minecraft.world.level.block.Blocks.COMPARATOR) {
                    world.updateNeighbourForOutputSignal(pos, state.block)
                }
            } catch (_: Throwable) {
            }
        }

        private fun refreshHopperState(world: ServerLevel, pos: BlockPos) {
            try {
                val state = world.getBlockState(pos)
                if (state.block == net.minecraft.world.level.block.Blocks.HOPPER) {
                    world.updateNeighbourForOutputSignal(pos, state.block)
                }
            } catch (_: Throwable) {
            }
        }

        private fun refreshComparatorOutput(world: ServerLevel, pos: BlockPos) {
            val state = world.getBlockState(pos)
            if (state.block != net.minecraft.world.level.block.Blocks.COMPARATOR &&
                state.block != net.minecraft.world.level.block.Blocks.HOPPER
            ) {
                return
            }

            try {
                world.updateNeighbourForOutputSignal(pos, state.block)
            } catch (_: Throwable) {
            }

            for (dir in net.minecraft.core.Direction.values()) {
                val neighborPos = pos.relative(dir)
                val neighborState = world.getBlockState(neighborPos)
                if (neighborState.block == net.minecraft.world.level.block.Blocks.COMPARATOR ||
                    neighborState.block == net.minecraft.world.level.block.Blocks.HOPPER ||
                    neighborState.hasAnalogOutputSignal()
                ) {
                    try {
                        world.updateNeighbourForOutputSignal(neighborPos, neighborState.block)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        private fun rescheduleRestoredEntityTriggers(world: ServerLevel, restoredPositions: List<BlockPos>, recordId: Long) {
            val triggers = restoredPositions.filter { pos ->
                val block = world.getBlockState(pos).block
                block is net.minecraft.world.level.block.TripWireBlock ||
                    block is net.minecraft.world.level.block.DetectorRailBlock ||
                    block is net.minecraft.world.level.block.BasePressurePlateBlock
            }
            if (triggers.isEmpty()) return
            UndoMixinHelper.pushRecord(recordId) { "recheck entity triggers" }
            try {
                triggers.forEach { pos -> world.scheduleTick(pos, world.getBlockState(pos).block, ENTITY_TRIGGER_RECHECK_DELAY) }
            } finally {
                UndoMixinHelper.popRecord { "recheck entity triggers" }
            }
        }

        private const val ENTITY_TRIGGER_RECHECK_DELAY = 10

        private fun unpowerObserver(state: net.minecraft.world.level.block.state.BlockState) =
            if (state.block is net.minecraft.world.level.block.ObserverBlock &&
                state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED)
            ) state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED, false) else state

        private const val OBSERVER_PULSE_DELAY = 2
        private const val OBSERVER_CLOCK_PERIOD = 4

        private fun settleObservers(world: ServerLevel, record: PlayerData.UndoRedoRecord, restoredPositions: List<BlockPos>) {
            val observerBlock = net.minecraft.world.level.block.Blocks.OBSERVER
            val facingProp = net.minecraft.world.level.block.ObserverBlock.FACING
            val poweredProp = net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED
            fun wasPowered(pos: BlockPos) = record.data[pos.asLong()]?.state
                ?.let { it.block is net.minecraft.world.level.block.ObserverBlock && it.getValue(poweredProp) } == true
            fun hasTick(pos: BlockPos) = world.blockTicks.hasScheduledTick(pos, observerBlock)

            val observers = restoredPositions.filter { world.getBlockState(it).block is net.minecraft.world.level.block.ObserverBlock }
            val clocks = LinkedHashSet<Pair<BlockPos, BlockPos>>()
            observers.forEach { pos ->
                val facing = world.getBlockState(pos).getValue(facingProp)
                val partner = pos.relative(facing)
                val partnerState = world.getBlockState(partner)
                if (partnerState.block is net.minecraft.world.level.block.ObserverBlock && partnerState.getValue(facingProp) == facing.opposite) {
                    clocks += if (pos.asLong() < partner.asLong()) pos to partner else partner to pos
                }
            }
            fun wasRecordedObserver(pos: BlockPos) =
                record.data[pos.asLong()]?.state?.block is net.minecraft.world.level.block.ObserverBlock
            val running = clocks.filter { (a, b) ->
                wasPowered(a) || wasPowered(b) || hasTick(a) || hasTick(b) || (wasRecordedObserver(a) && wasRecordedObserver(b))
            }
            val keep = running.flatMap { listOf(it.first, it.second) }.toSet()

            observers.filter { it !in keep }
                .forEach { world.blockTicks.clearArea(net.minecraft.world.level.levelgen.structure.BoundingBox(it)) }

            val restart = running.filter { (a, b) -> !hasTick(a) && !hasTick(b) }
            if (restart.isEmpty()) return
            val now = com.github.unstoppalezzz.reden.utils.server.tickCount
            UndoMixinHelper.pushRecord(record.id) { "restart observer clocks" }
            try {
                restart.forEach { (a, b) ->
                    val fireA = UndoMixinHelper.lastObserverFire(a)
                    val fireB = UndoMixinHelper.lastObserverFire(b)
                    val (target, fired) = when {
                        fireA != null && (fireB == null || fireA >= fireB) -> a to fireA
                        fireB != null -> b to fireB
                        else -> a to now + OBSERVER_PULSE_DELAY
                    }
                    val delay = Math.floorMod(fired - now - OBSERVER_PULSE_DELAY, OBSERVER_CLOCK_PERIOD) + OBSERVER_PULSE_DELAY
                    if (isDebug) debugLogger(
                        "[undo] restarting observer clock ${a.toShortString()} <-> ${b.toShortString()}: " +
                            "kicking ${target.toShortString()} in $delay ticks (beat of tick $fired)"
                    )
                    world.scheduleTick(target, observerBlock, delay)
                }
            } finally {
                UndoMixinHelper.popRecord { "restart observer clocks" }
            }
        }

        private fun destroyPrimedTntAt(world: ServerLevel, pos: BlockPos) {
            val center = net.minecraft.world.phys.Vec3.atCenterOf(pos)
            val tntEntities = world.getEntitiesOfClass(
                net.minecraft.world.entity.item.PrimedTnt::class.java,
                net.minecraft.world.phys.AABB.ofSize(center, 1.5, 1.5, 1.5)
            ) { entity -> entity.blockPosition() == pos || entity.position().distanceToSqr(center) < 1.0 }

            if (tntEntities.isNotEmpty()) {
                tntEntities.forEach { it.discard() }
            }
        }

        private fun isStale(world: ServerLevel, pos: BlockPos, entry: PlayerData.Entry, isUndo: Boolean): Boolean {
            val sec = world.getChunk(pos).run { getSection(getSectionIndex(pos.y)) } as ChunkSectionInterface
            if (!isUndo) return false
            val modifyTime = sec.getModifyTime(pos)
            if (modifyTime == 0 && sec.createdTick() > entry.time) return false
            return modifyTime < entry.time
        }

        internal fun willRestore(world: ServerLevel, pos: BlockPos, entry: PlayerData.Entry, isUndo: Boolean) =
            !isStale(world, pos, entry, isUndo) &&
                entry.state.block !is net.minecraft.world.level.block.piston.MovingPistonBlock

        internal fun loadBlockEntity(world: ServerLevel, pos: BlockPos, entry: PlayerData.Entry): BlockEntity? {
            val be = entry.beType?.create(pos, entry.state) ?: return null
            when (val beData = entry.beData) {
                null -> {}
                is CompoundTag -> {
                    //? if < 1.20.5 {
                    /*be.load(beData)
                    *///?} elif <= 1.21.5 {
                    /*be.loadWithComponents(beData, world.registryAccess())
                    *///?} else {
                    be.loadWithComponents(
                        net.minecraft.world.level.storage.TagValueInput.create(
                            net.minecraft.util.ProblemReporter.DISCARDING,
                            world.registryAccess(),
                            beData
                        )
                    )
                    //?}
                }

                //? if >=1.20.5 {
                is DataComponentMap -> {
                    val prototype = entry.state.block.asItem().components()
                    be.applyComponents(prototype, DataComponentPatch.builder().apply {
                        beData.forEach { typedDataComponent ->
                            this.set(typedDataComponent)
                        }
                    }.build())
                }
                //?}

                else -> {
                    throw IllegalArgumentException("Unsupported block entity data type: ${beData::class.java}")
                }
            }
            return be
        }

        private fun operate(world: ServerLevel, record: PlayerData.UndoRedoRecord, redoRecord: PlayerData.RedoRecord?, isUndo: Boolean = true, survival: Boolean = false) {
            val restoredPositions = mutableListOf<BlockPos>()
            val skippedStale = mutableListOf<BlockPos>()
            val skippedMoving = mutableListOf<BlockPos>()
            val movingRestores = mutableListOf<Triple<BlockPos, BlockPos, net.minecraft.world.level.block.state.BlockState>>()
            debugLogger(
                "[undo] ${if (isUndo) "undo" else "redo"} record ${record.id} at tick ${com.github.unstoppalezzz.reden.utils.server.tickCount}: " +
                    "${record.data.size} blocks, ${record.entities.size} entities, active=${UndoMixinHelper.recording?.id}"
            )
            record.data.forEach { (posLong, recorded) ->
                val pos = BlockPos.of(posLong)
                val entry = unpowerObserver(recorded.state).let { if (it === recorded.state) recorded else recorded.copy(state = it) }
                val currentState = world.getBlockState(pos)
                val sec = world.getChunk(pos).run { getSection(getSectionIndex(pos.y)) } as ChunkSectionInterface
                if (isStale(world, pos, entry, isUndo)) {
                    skippedStale += pos
                    debugLogger(
                        "[undo] skip STALE ${pos.toShortString()}: modifyTime=${sec.getModifyTime(pos)} < entryTime=${entry.time}, " +
                            "current=$currentState, recorded=${entry.state}"
                    )
                    return@forEach
                }
                if (entry.state != currentState) {
                    debugLogger("[undo] restore ${pos.toShortString()}: $currentState -> ${entry.state}")
                }
                if (entry.state.block is net.minecraft.world.level.block.piston.MovingPistonBlock) {
                    val tag = entry.beData as? CompoundTag
                    val carried = tag?.get("blockState")
                        ?.let { net.minecraft.world.level.block.state.BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, it).result().orElse(null) }
                    //? if >= 1.21.5 {
                    val isSource = tag?.getBooleanOr("source", false) ?: false
                    //?} else {
                    /*val isSource = tag?.getBoolean("source") ?: false
                    *///?}
                    if (tag == null || carried == null || isSource) {
                        skippedMoving += pos
                        debugLogger(
                            "[undo] skip MOVING ${pos.toShortString()}: tag=${tag != null} carried=$carried source=$isSource, " +
                                "current=$currentState (left untouched)"
                        )
                        return@forEach
                    }
                    //? if >= 1.21.5 {
                    val facing = net.minecraft.core.Direction.from3DDataValue(tag.getIntOr("facing", 0))
                    val moveDir = if (tag.getBooleanOr("extending", true)) facing else facing.opposite
                    //?} else {
                    /*val facing = net.minecraft.core.Direction.from3DDataValue(tag.getInt("facing"))
                    val moveDir = if (!tag.contains("extending") || tag.getBoolean("extending")) facing else facing.opposite
                    *///?}
                    movingRestores += Triple(pos, pos.relative(moveDir.opposite), unpowerObserver(carried))
                    debugLogger(
                        "[undo] moving entry ${pos.toShortString()}: will set AIR there and put $carried back at " +
                            "${pos.relative(moveDir.opposite).toShortString()} (currently ${world.getBlockState(pos.relative(moveDir.opposite))})"
                    )
                    return@forEach
                }
                world.modified(pos, entry.time)

                world.setBlockNoPP(pos, entry.state)
                restoredPositions += pos
                val restoredState = world.getBlockState(pos)
                if (entry.state.block == net.minecraft.world.level.block.Blocks.TNT || restoredState.block == net.minecraft.world.level.block.Blocks.TNT) {
                    destroyPrimedTntAt(world, pos)
                }
                entry.beType?.let { beType ->
                    if (!entry.state.hasBlockEntity()) {
                        return@let
                    }

                    val currentState = world.getBlockState(pos)
                    if (currentState.isAir || !currentState.`is`(entry.state.block)) {
                        return@let
                    }

                    val be = loadBlockEntity(world, pos, entry) ?: return@let
                    world.setBlockEntity(be)
                    if (isUndo) (world.getBlockEntity(pos) as? UndoableAccess)?.undoId = record.id
                    (world.getBlockEntity(pos) as? BlockEntityInterface)?.saveLastNbt()
                }
            }

            if (isUndo) {
                UndoMixinHelper.dropPendingTicks(world, record.data.keys.map(BlockPos::of), record.id)
                UndoMixinHelper.suppressPlatePresses(world, restoredPositions)
            }
            settleObservers(world, record, restoredPositions)

            //? if >=26.1 {
            movingRestores.forEach { (dest, _, _) ->
                world.modified(dest, com.github.unstoppalezzz.reden.utils.server.tickCount)
                world.setBlockNoPP(dest, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                restoredPositions += dest
            }
            movingRestores.forEach { (dest, origin, carried) ->
                world.modified(origin, com.github.unstoppalezzz.reden.utils.server.tickCount)
                world.setBlockNoPP(origin, carried)
                restoredPositions += origin
            }

        
            val now = com.github.unstoppalezzz.reden.utils.server.tickCount
            UndoMixinHelper.freezePositions(
                restoredPositions.filter { isUpdateSensitive(world.getBlockState(it)) }, now + 1, now
            )
            rescheduleRestoredEntityTriggers(world, restoredPositions, record.id)

            restoredPositions
                .filter { pos ->
                    val state = world.getBlockState(pos)
                    state.block == net.minecraft.world.level.block.Blocks.COMPARATOR ||
                        state.block == net.minecraft.world.level.block.Blocks.HOPPER
                }
                .forEach { pos -> refreshComparatorOutput(world, pos) }
            //?}

            val rideRelations = mutableListOf<Triple<net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity?, List<net.minecraft.world.entity.Entity>>>()
            record.entities.forEach {
                val entity = world.getEntity(it.key)
                if (entity != null && it.value != PlayerData.NotExistEntityEntry &&
                    (entity.isPassenger || entity.passengers.isNotEmpty())
                ) {
                    rideRelations += Triple(entity, entity.vehicle, entity.passengers.toList())
                }
                val looseDrop = survival && isUndo && it.value != PlayerData.NotExistEntityEntry &&
                    SurvivalUndo.isLooseDrop(it.value.entity)
                if (looseDrop) {
                    debugLogger("undo entity ${it.key} is a loose drop, skipping in survival")
                    return@forEach
                }
                if (entity == null) {
                    if (it.value != PlayerData.NotExistEntityEntry) {
                        val entry = it.value
                        if (entry.nbt.size() == 0) return@forEach
                        debugLogger("undo entity ${it.key} spawning")
                        val newEntity = entry.entity!!.spawn(world,
//? if < 1.20.5
                        /*null as CompoundTag?,*/
                        { newEntity ->
                            newEntity.uuid = it.key
                        },
//? if <= 1.21.1 {
                        /*entry.pos, net.minecraft.world.entity.MobSpawnType.COMMAND, false, false)
*///?} else {
                        entry.pos, net.minecraft.world.entity.EntitySpawnReason.COMMAND, false, false)
//?}
                        if (newEntity != null) {
                            newEntity.load(entry.nbt)
                            if (isUndo) UndoMixinHelper.holdRestoredTntMinecart(newEntity)
                            redoRecord?.entities?.put(it.key, PlayerData.NotExistEntityEntry) // add entity info to redo record
                        }
                    }
                } else {
                    redoRecord?.entities?.put(
                        it.key, PlayerData.EntityEntryImpl(
                            entity.type,
                            entity.saveWithoutId(CompoundTag()),
                            entity.blockPosition()
                        )
                    )
                    val dropped = if (survival) SurvivalUndo.droppedStack(record, it.key) else null
                    if (it.value == PlayerData.NotExistEntityEntry && entity is ItemEntity && dropped != null &&
                        SurvivalUndo.sameItem(entity.item, dropped) && entity.item.count > dropped.count
                    ) {
                        debugLogger("undo entity ${it.key} shrinking by ${dropped.count}")
                        entity.item = entity.item.copyWithCount(entity.item.count - dropped.count)
                    } else if (it.value == PlayerData.NotExistEntityEntry) {
                        debugLogger("undo entity ${it.key} removing")
                        entity.discard()
                    } else if (it.value.nbt.size() == 0) {
                        debugLogger("undo entity ${it.key} has empty nbt, skipping")
                    } else {
                        val entry = it.value
                        debugLogger("undo entity ${it.key} reading nbt")
                        if (entity is Mob) {
                            entity.removeFreeWill()
                        }
                        entity.load(entry.nbt)
                        if (isUndo) UndoMixinHelper.holdRestoredTntMinecart(entity)
                    }
                }
            }

            rideRelations.forEach { (entity, vehicleBefore, passengersBefore) ->
                if (entity.isRemoved) return@forEach
                if (vehicleBefore != null && !vehicleBefore.isRemoved && entity.vehicle != vehicleBefore) {
//? if >= 1.21.9 {
                    entity.startRiding(vehicleBefore, true, false)
//?} else {
                    /*entity.startRiding(vehicleBefore, true)
*///?}
                }
                passengersBefore.forEach { passenger ->
                    if (!passenger.isRemoved && passenger.vehicle != entity) {
//? if >= 1.21.9 {
                        passenger.startRiding(entity, true, false)
//?} else {
                        /*passenger.startRiding(entity, true)
*///?}
                    }
                }
            }

            //? if <26.1 {
            /*movingRestores.forEach { (dest, _, _) ->
                world.modified(dest, com.github.unstoppalezzz.reden.utils.server.tickCount)
                world.setBlockNoPP(dest, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                restoredPositions += dest
            }
            movingRestores.forEach { (dest, origin, carried) ->
                world.modified(origin, com.github.unstoppalezzz.reden.utils.server.tickCount)
                world.setBlockNoPP(origin, carried)
                restoredPositions += origin
            }

        
            val now = com.github.unstoppalezzz.reden.utils.server.tickCount
            UndoMixinHelper.freezePositions(
                restoredPositions.filter { isUpdateSensitive(world.getBlockState(it)) }, now + 1, now
            )
            rescheduleRestoredEntityTriggers(world, restoredPositions, record.id)

            restoredPositions
                .filter { pos ->
                    val state = world.getBlockState(pos)
                    state.block == net.minecraft.world.level.block.Blocks.COMPARATOR ||
                        state.block == net.minecraft.world.level.block.Blocks.HOPPER
                }
                .forEach { pos -> refreshComparatorOutput(world, pos) }
            *///?}

            val frozen = restoredPositions.filter { isUpdateSensitive(world.getBlockState(it)) }
            debugLogger(
                "[undo] record ${record.id} done at tick ${com.github.unstoppalezzz.reden.utils.server.tickCount}: " +
                    "restored=${restoredPositions.size}, skippedStale=${skippedStale.map { it.toShortString() }}, " +
                    "skippedMoving=${skippedMoving.map { it.toShortString() }}, " +
                    "frozen(1 tick)=${frozen.map { "${it.toShortString()}:${world.getBlockState(it).block}" }}"
            )
        }
        private fun <T: PlayerData.UndoRedoRecord> MutableList<T>.lastValid(): T? {
            while (this.isNotEmpty()) {
                val last = this.last()
                val killedPlayers = when (last) {
                    is PlayerData.UndoRecord -> last.killedPlayers
                    is PlayerData.RedoRecord -> last.undoRecord.killedPlayers
                    else -> emptyMap()
                }
                if (last.data.isNotEmpty() || last.entities.isNotEmpty() || killedPlayers.isNotEmpty()) {
                    return last
                }
                UndoMixinHelper.removeRecord(last.id)
                this.removeLast()
            }
            return null
        }
        fun register() {
            //? if >=1.20.5 {
            //? if >=26.1 {
            PayloadTypeRegistry.serverboundPlay().register(ID, CODEC)
            PayloadTypeRegistry.clientboundPlay().register(ID, CODEC)
            //?} else {
            /*PayloadTypeRegistry.playC2S().register(ID, CODEC)
            // the client side receiver (registerClientPackets) needs the clientbound type as well
            PayloadTypeRegistry.playS2C().register(ID, CODEC)
            *///?}
            ServerPlayNetworking.registerGlobalReceiver(ID) { packet, context ->
                //? if >=26.1 {
                handle(packet, context.player()) { status -> context.responseSender().sendPacket(Undo(status)) }
                //?} else {
                /*handle(packet, context.player()) { status -> sendStatusMessage(context.player(), status) }
                *///?}
            }
            //?} else {
            /*ServerPlayNetworking.registerGlobalReceiver(ID) { server, player, _, buf, _ ->
                val packet = decode(buf)
                server.execute { handle(packet, player) { status -> sendStatusMessage(player, status) } }
            }
            *///?}
        }

        //? if <26.1 {
        /*private fun sendStatusMessage(player: ServerPlayer, status: Int) {
            val msg = when (status) {
                0 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.rollback_success"))
                1 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.restore_success"))
                2 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.no_blocks_info"))
                16 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.no_permission"))
                STATUS_CREATIVE_RECORD -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.creative_record"))
                STATUS_ENTITY_GONE -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.entity_gone"))
                32 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.not_recording"))
                64 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.busy"))
                65536 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.unknown_error"))
                else -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.unknown_status"))
            }
            player.sendSystemMessage(msg)
        }
        *///?}

        private fun ServerPlayer.serverLevelCompat(): ServerLevel =
            //? if <= 1.21.5
            /*serverLevel()*/
            //? if >= 1.21.6
            level()

        private fun canAffectKilledPlayer(player: ServerPlayer, uuid: java.util.UUID): Boolean =
            uuid == player.uuid || SurvivalUndo.hasKillOthersPermission(player)

        private fun restoreKilledPlayers(player: ServerPlayer, record: PlayerData.UndoRecord) {
            record.killedPlayers.forEach { (uuid, snapshot) ->
                var target = server.playerList.getPlayer(uuid) ?: return@forEach
                if (!canAffectKilledPlayer(player, uuid)) {
                    player.sendSystemMessage(Text.translatable("reden.message.undo.base",
                        Text.translatable("reden.message.undo.no_kill_others_permission", target.scoreboardName)))
                    return@forEach
                }
                if (target.isDeadOrDying) {
                    target.connection.handleClientCommand(
                        net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                            net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                        )
                    )
                    target = server.playerList.getPlayer(uuid) ?: return@forEach
                }
                val nbt = snapshot.nbt.copy()
                nbt.remove("ActiveEffects")
                nbt.remove("active_effects")
                target.load(nbt)
                val level = server.getLevel(snapshot.level) ?: target.serverLevelCompat()
                //? if >= 1.21.2 {
                target.teleportTo(level, snapshot.x, snapshot.y, snapshot.z, emptySet(), snapshot.yRot, snapshot.xRot, true)
                //?} else {
                /*target.teleportTo(level, snapshot.x, snapshot.y, snapshot.z, snapshot.yRot, snapshot.xRot)
                *///?}
                target.onUpdateAbilities()
                debugLogger("[undo] restored killed player ${target.scoreboardName}")
            }
        }

        private fun killPlayersAgain(player: ServerPlayer, record: PlayerData.UndoRecord) {
            record.killedPlayers.keys.toList().forEach { uuid ->
                if (!canAffectKilledPlayer(player, uuid)) return@forEach
                val target = server.playerList.getPlayer(uuid) ?: return@forEach
                if (target.isDeadOrDying) return@forEach
                record.killedPlayers[uuid] = UndoMixinHelper.snapshotPlayer(target)
                UndoMixinHelper.suppressDeathDrops = true
                try {
                    //? if >= 1.21.2 {
                    target.kill(target.serverLevelCompat())
                    //?} else {
                    /*target.kill()
                    *///?}
                } finally {
                    UndoMixinHelper.suppressDeathDrops = false
                }
            }
        }

        private fun refused(plan: SurvivalUndo.Plan?, player: ServerPlayer, sendStatus: (Int) -> Unit) = when (plan) {
            is SurvivalUndo.Plan.Refused -> true.also { sendStatus(plan.status) }
            is SurvivalUndo.Plan.Missing -> true.also { player.sendSystemMessage(SurvivalUndo.missingMessage(plan)) }
            else -> false
        }

        private fun handle(packet: Undo, player: ServerPlayer, sendStatus: (Int) -> Unit) {
            val view = player.data()
            if (packet.status == REQUEST_CREATIVE_ONLY || packet.status == REQUEST_ALLOW_SURVIVAL) {
                view.creativeOnly = packet.status == REQUEST_CREATIVE_ONLY
                return
            }
            if (!view.canRecord) {
                sendStatus(16)
                return
            }
            debugLogger(
                "[undo] request status=${packet.status}: undo=${view.undo.map { it.id }} redo=${view.redo.map { it.id }} " +
                    "isRecording=${view.isRecording} active=${UndoMixinHelper.recording?.id} " +
                    "liveRecords=${UndoMixinHelper.undoRecordsMap.keys}"
            )
            UndoMixinHelper.playerStopRecording(player)
            if (UndoMixinHelper.recording != null) {
                Reden.LOGGER.error("Undo when a record is still active, id=" + UndoMixinHelper.recording?.id)
                // 不取消跟踪会导致undo的更改也被记录，边读边写异常
                UndoMixinHelper.undoRecords.clear()
            }
            val world =
                //? if <= 1.21.5
                /*player.serverLevel()*/
                //? if >= 1.21.6
                player.level()
            val survival = !player.isCreative
            when (packet.status) {
                0 -> view.undo.lastValid()?.let { undoRecord ->
                    val plan = if (survival) SurvivalUndo.planUndo(world, player, undoRecord) else null
                    if (refused(plan, player, sendStatus)) return
                    view.undo.removeLast()
                    UndoMixinHelper.removeRecord(undoRecord.id)
                    server.execute {
                        view.redo.add(
                            PlayerData.RedoRecord(
                                id = undoRecord.id,
                                lastChangedTick = -1,
                                undoRecord = undoRecord
                            ).apply {
                                data.putAll(undoRecord.data.keys.associateWith { posLong ->
                                    this.fromWorld( // add entity info to this redo record
                                        world,
                                        BlockPos.of(posLong),
                                        true
                                    )
                                })
                                entities.clear()
                            }
                        )
                        if (plan is SurvivalUndo.Plan.Ok) SurvivalUndo.apply(player, plan.total)
                        operate(world, undoRecord, view.redo.last(), survival = survival)
                        if (plan is SurvivalUndo.Plan.Ok) SurvivalUndo.afterUndoApplied(world, view.redo.last(), plan.inventoryPart)
                        restoreKilledPlayers(player, undoRecord)
                        sendStatus(0)
                    }
                } ?: sendStatus(2)

                1 -> view.redo.lastValid()?.let {
                    val plan = if (survival) SurvivalUndo.planRedo(world, player, it) else null
                    if (refused(plan, player, sendStatus)) return
                    view.redo.removeLast()
                    server.execute {
                        if (plan is SurvivalUndo.Plan.Ok) SurvivalUndo.apply(player, plan.total)
                        operate(world, it, null, isUndo = false, survival = survival)
                        killPlayersAgain(player, it.undoRecord)
                        view.undo.add(it.undoRecord)
                        sendStatus(1)
                    }
                } ?: sendStatus(2)

                else -> sendStatus(65536)
            }
        }
    }
}
