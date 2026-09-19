package com.github.unstoppalezzz.reden.network

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.access.BlockEntityInterface
import com.github.unstoppalezzz.reden.access.ChunkSectionInterface
import com.github.unstoppalezzz.reden.access.PlayerData
import com.github.unstoppalezzz.reden.access.PlayerData.Companion.data
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.modified
import com.github.unstoppalezzz.reden.utils.multiver.*
import com.github.unstoppalezzz.reden.utils.server
import com.github.unstoppalezzz.reden.utils.setBlockNoPP
import kotlinx.serialization.Serializable
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.item.PrimedTnt

@Serializable
class Undo(
    val status: Int = 0
) : CustomPacketPayload {
    override fun type() = ID

    companion object : PacketCodecHelper<Undo> by PacketCodec(Reden.identifier("undo")) {
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

        private fun destroyPrimedTntAt(world: ServerLevel, pos: BlockPos) {
            val center = net.minecraft.world.phys.Vec3.atCenterOf(pos)
            val tntEntities = world.getEntitiesOfClass(
                PrimedTnt::class.java,
                net.minecraft.world.phys.AABB.ofSize(center, 1.5, 1.5, 1.5)
            ) { entity -> entity.blockPosition() == pos || entity.position().distanceToSqr(center) < 1.0 }

            if (tntEntities.isNotEmpty()) {
                tntEntities.forEach { it.discard() }
            }
        }

        private fun operate(world: ServerLevel, record: PlayerData.UndoRedoRecord, redoRecord: PlayerData.RedoRecord?, isUndo: Boolean = true) {
            val restoredPositions = mutableListOf<BlockPos>()
            val skippedStale = mutableListOf<BlockPos>()
            val skippedMoving = mutableListOf<BlockPos>()
            val movingRestores = mutableListOf<Triple<BlockPos, BlockPos, net.minecraft.world.level.block.state.BlockState>>()
            record.data.forEach { (posLong, entry) ->
                val pos = BlockPos.of(posLong)
                val currentState = world.getBlockState(pos)
                val sec = world.getChunk(pos).run { getSection(getSectionIndex(pos.y)) } as ChunkSectionInterface
                if (sec.getModifyTime(pos) < entry.time && isUndo) {
                    skippedStale += pos
                    return@forEach
                }
                if (entry.state.block is net.minecraft.world.level.block.piston.MovingPistonBlock) {
                    val tag = entry.beData as? CompoundTag
                    val carried = tag?.get("blockState")
                        ?.let { net.minecraft.world.level.block.state.BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, it).result().orElse(null) }
                    if (tag == null || carried == null || tag.getBooleanOr("source", false)) {
                        skippedMoving += pos
                        return@forEach
                    }
                    val facing = net.minecraft.core.Direction.from3DDataValue(tag.getIntOr("facing", 0))
                    val moveDir = if (tag.getBooleanOr("extending", true)) facing else facing.opposite
                    movingRestores += Triple(pos, pos.relative(moveDir.opposite), carried)
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

                    val be = beType.create(pos, entry.state)
                    if (be == null) {
                        return@let
                    }

                    val beData = entry.beData
                    if (beData == null) {
                        world.setBlockEntity(be)
                        (world.getBlockEntity(pos) as? BlockEntityInterface)?.saveLastNbt()
                        return@let
                    }

                    when (beData) {
                        is CompoundTag -> {
                            //? if <= 1.21.5 {
                            /*be.loadWithComponents(beData, world.registryAccess())
                            *///?} elif >= 1.21.6 {
                            be.loadWithComponents(
                                net.minecraft.world.level.storage.TagValueInput.create(
                                    net.minecraft.util.ProblemReporter.DISCARDING,
                                    world.registryAccess(),
                                    beData
                                )
                            )
                            //?}
                        }

                        is DataComponentMap -> {
                            val prototype = entry.state.block.asItem().components()
                            be.applyComponents(prototype, DataComponentPatch.builder().apply {
                                beData.forEach { typedDataComponent ->
                                    this.set(typedDataComponent)
                                }
                            }.build())
                        }

                        else -> {
                            throw IllegalArgumentException("Unsupported block entity data type: ${beData::class.java}")
                        }
                    }
                    world.setBlockEntity(be)
                    (world.getBlockEntity(pos) as? BlockEntityInterface)?.saveLastNbt()
                }
            }

            movingRestores.forEach { (dest, _, _) ->
                world.modified(dest, com.github.unstoppalezzz.reden.utils.gameTick)
                world.setBlockNoPP(dest, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
                restoredPositions += dest
            }
            movingRestores.forEach { (dest, origin, carried) ->
                world.modified(origin, com.github.unstoppalezzz.reden.utils.gameTick)
                world.setBlockNoPP(origin, carried)
                restoredPositions += origin
            }

        
            val now = com.github.unstoppalezzz.reden.utils.gameTick
            UndoMixinHelper.freezePositions(
                restoredPositions.filter { isUpdateSensitive(world.getBlockState(it)) }, now + 1, now
            )

            restoredPositions
                .filter { pos ->
                    val state = world.getBlockState(pos)
                    state.block == net.minecraft.world.level.block.Blocks.COMPARATOR ||
                        state.block == net.minecraft.world.level.block.Blocks.HOPPER
                }
                .forEach { pos -> refreshComparatorOutput(world, pos) }

        
            val rideRelations = mutableListOf<Triple<net.minecraft.world.entity.Entity, net.minecraft.world.entity.Entity?, List<net.minecraft.world.entity.Entity>>>()
            record.entities.forEach {
                val entity = world.getEntity(it.key)
                if (entity != null && it.value != PlayerData.NotExistEntityEntry &&
                    (entity.isPassenger || entity.passengers.isNotEmpty())
                ) {
                    rideRelations += Triple(entity, entity.vehicle, entity.passengers.toList())
                }
                if (entity == null) {
                    if (it.value != PlayerData.NotExistEntityEntry) {
                        val entry = it.value
                        if (entry.nbt.size() == 0) return@forEach
                        val newEntity = entry.entity!!.spawn(world, { newEntity ->
                            newEntity.uuid = it.key
                        },
//? if <= 1.21.1 {
                        /*entry.pos, net.minecraft.world.entity.MobSpawnType.COMMAND, false, false)
*///?} else {
                        entry.pos, net.minecraft.world.entity.EntitySpawnReason.COMMAND, false, false)
//?}
                        if (newEntity != null) {
                            newEntity.load(entry.nbt)
                            redoRecord?.entities?.put(it.key, PlayerData.NotExistEntityEntry)
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
                    if (it.value == PlayerData.NotExistEntityEntry) {
                        entity.discard()
                    } else if (it.value.nbt.size() == 0) {
                    } else {
                        val entry = it.value
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
                    entity.startRiding(vehicleBefore, true, false)
                }
                passengersBefore.forEach { passenger ->
                    if (!passenger.isRemoved && passenger.vehicle != entity) {
                        passenger.startRiding(entity, true, false)
                    }
                }
            }
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
            PayloadTypeRegistry.serverboundPlay().register(ID, CODEC)
            PayloadTypeRegistry.clientboundPlay().register(ID, CODEC)
            ServerPlayNetworking.registerGlobalReceiver(ID) { packet, context ->
                val view = context.player().data()
                fun sendStatus(status: Int) = context.responseSender().sendPacket(Undo(status))
                if (!view.canRecord) {
                    sendStatus(16)
                    return@registerGlobalReceiver
                }
                UndoMixinHelper.playerStopRecording(context.player())
                if (UndoMixinHelper.recording != null) {
                    // 不取消跟踪会导致undo的更改也被记录，边读边写异常
                    UndoMixinHelper.undoRecords.clear()
                }
                when (packet.status) {
                    0 -> view.undo.lastValid()?.let { undoRecord ->
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
                                            //? if <= 1.21.5
                                            /*context.player().serverLevel(),*/
                                            //? if >= 1.21.6
                                            context.player().level(),
                                            BlockPos.of(posLong),
                                            true
                                        )
                                    })
                                    entities.clear()
                                }
                            )
                            operate(
                                //? if <= 1.21.5
                                /*context.player().serverLevel(),*/
                                //? if >= 1.21.6
                                context.player().level(),
                                undoRecord,
                                view.redo.last()
                            )
                            sendStatus(0)
                        }
                    } ?: sendStatus(2)

                    1 -> view.redo.lastValid()?.let {
                        view.redo.removeLast()
                        server.execute {
                            operate(
                                //? if <= 1.21.5
                                /*context.player().serverLevel(),*/
                                //? if >= 1.21.6
                                context.player().level(),
                                it,
                                null,
                                isUndo = false
                            )
                            view.undo.add(it.undoRecord)
                            sendStatus(1)
                        }
                    } ?: sendStatus(2)

                    else -> sendStatus(65536)
                }
            }
        }
    }
}
