package com.github.unstoppalezzz.reden.network

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.access.BlockEntityInterface
import com.github.unstoppalezzz.reden.access.ChunkSectionInterface
import com.github.unstoppalezzz.reden.access.PlayerData
import com.github.unstoppalezzz.reden.access.PlayerData.Companion.data
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.modified
import com.github.unstoppalezzz.reden.undo.SurvivalUndo
import com.github.unstoppalezzz.reden.utils.debugLogger
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

        private fun rescheduleRestoredEntityTriggers(world: ServerLevel, restoredPositions: List<BlockPos>) {
            restoredPositions.forEach { pos ->
                val state = world.getBlockState(pos)
                val block = state.block
                if (block is net.minecraft.world.level.block.TripWireBlock ||
                    block is net.minecraft.world.level.block.DetectorRailBlock ||
                    block is net.minecraft.world.level.block.BasePressurePlateBlock
                ) {
                    world.scheduleTick(pos, block, ENTITY_TRIGGER_RECHECK_DELAY)
                }
            }
        }

        private const val ENTITY_TRIGGER_RECHECK_DELAY = 10

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
            return sec.getModifyTime(pos) < entry.time && isUndo
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
            record.data.forEach { (posLong, entry) ->
                val pos = BlockPos.of(posLong)
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
                    movingRestores += Triple(pos, pos.relative(moveDir.opposite), carried)
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
                    (world.getBlockEntity(pos) as? BlockEntityInterface)?.saveLastNbt()
                }
            }

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
            rescheduleRestoredEntityTriggers(world, restoredPositions)

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
            rescheduleRestoredEntityTriggers(world, restoredPositions)

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
                if (last.data.isNotEmpty() || last.entities.isNotEmpty()) {
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
                        view.undo.add(it.undoRecord)
                        sendStatus(1)
                    }
                } ?: sendStatus(2)

                else -> sendStatus(65536)
            }
        }
    }
}
