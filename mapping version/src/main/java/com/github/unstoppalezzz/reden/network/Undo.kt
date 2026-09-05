package com.github.unstoppalezzz.reden.network

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.access.BlockEntityInterface
import com.github.unstoppalezzz.reden.access.ChunkSectionInterface
import com.github.unstoppalezzz.reden.access.PlayerData
import com.github.unstoppalezzz.reden.access.PlayerData.Companion.data
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.modified
import com.github.unstoppalezzz.reden.utils.debugLogger
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

@Serializable
class Undo(
    val status: Int = 0
) : CustomPacketPayload {
    override fun type() = ID

    companion object : PacketCodecHelper<Undo> by PacketCodec(Reden.identifier("undo")) {
        private fun operate(world: ServerLevel, record: PlayerData.UndoRedoRecord, redoRecord: PlayerData.RedoRecord?, isUndo: Boolean = true) {
            debugLogger("undoing record ${record.id}, isUndo=$isUndo")
            record.data.forEach { (posLong, entry) ->
                val pos = BlockPos.of(posLong)
                debugLogger("undo ${pos}, ${entry.state}")
                // set block
                val sec = world.getChunk(pos).run { getSection(getSectionIndex(pos.y)) } as ChunkSectionInterface
                if (sec.getModifyTime(pos) < entry.time && isUndo) {
                    debugLogger("undo $pos skipped (${sec.getModifyTime(pos)} < ${entry.time})")
                    return@forEach
                }
                world.modified(pos, entry.time)
                // two situations:
                // if isUndo, the block is modified by the undo record, so we need to set the modify time to the undo record's time
                // if isRedo, the block is modified by the player operations, so we need to set the modify time to the current time
                //  luckily, the redo record's time is the current time
                world.setBlockNoPP(pos, entry.state)
                // Only update adjacent comparators so they refresh their outputs
                try {
                    for (dir in net.minecraft.core.Direction.values()) {
                        val np = pos.relative(dir)
                        val ns = world.getBlockState(np)
                        val b = ns.block
                        if (b is net.minecraft.world.level.block.ComparatorBlock) {
                            try {
                                try {
                                    val facing = ns.getValue(net.minecraft.world.level.block.ComparatorBlock.FACING)
                                    var out = -1
                                    try {
                                        val cls = net.minecraft.world.level.block.ComparatorBlock::class.java
                                        val m = cls.getDeclaredMethod("getAnalogOutputSignal", net.minecraft.world.level.block.state.BlockState::class.java, net.minecraft.world.level.Level::class.java, net.minecraft.core.BlockPos::class.java, net.minecraft.core.Direction::class.java)
                                        m.isAccessible = true
                                        out = (m.invoke(null, ns, world, np, facing) as Int)
                                    } catch (e: NoSuchMethodException) {
                                        try {
                                            val cls2 = net.minecraft.world.level.block.entity.ComparatorBlockEntity::class.java
                                            val m2 = cls2.getDeclaredMethod("getAnalogOutputSignal", net.minecraft.world.level.Level::class.java, net.minecraft.core.BlockPos::class.java, net.minecraft.core.Direction::class.java)
                                            m2.isAccessible = true
                                            out = (m2.invoke(null, world, np, facing) as Int)
                                        } catch (e2: NoSuchMethodException) {
                                            val beIns = world.getBlockEntity(np)
                                            if (beIns is net.minecraft.world.level.block.entity.ComparatorBlockEntity) {
                                                try {
                                                    try {
                                                        val m3 = beIns::class.java.getDeclaredMethod("getAnalogOutputSignal")
                                                        m3.isAccessible = true
                                                        out = (m3.invoke(beIns) as Int)
                                                    } catch (nsme: NoSuchMethodException) {
                                                        try {
                                                            val m3 = beIns::class.java.getDeclaredMethod("getComparatorOutput")
                                                            m3.isAccessible = true
                                                            out = (m3.invoke(beIns) as Int)
                                                        } catch (ignored: Throwable) {
                                                        }
                                                    }
                                                } catch (ignored: Throwable) {
                                                }
                                            }
                                        }
                                    }
                                    if (out >= 0) {
                                        val be = world.getBlockEntity(np)
                                        if (be is net.minecraft.world.level.block.entity.ComparatorBlockEntity) {
                                            be.setOutputSignal(out)
                                            // notify clients of block entity change
                                            world.sendBlockUpdated(np, ns, ns, 3)
                                        }
                                    }
                                } catch (ignored: Throwable) {
                                }
                            } catch (ignored: Throwable) {
                            }
                        }
                    }
                } catch (t: Throwable) {
                    debugLogger("Failed to refresh adjacent comparators at $pos: $t")
                }
                // clear schedules
//                world.syncedBlockEventQueue.removeIf { it.pos == pos }
//                val blockTickScheduler = world.getChunk(pos).blockTickScheduler as ChunkTickScheduler
//                val fluidTickScheduler = world.getChunk(pos).fluidTickScheduler as ChunkTickScheduler
//                blockTickScheduler.removeTicksIf { it.pos == pos }
//                fluidTickScheduler.removeTicksIf { it.pos == pos }
                // apply block entity
                entry.beType?.let { beType ->
                    debugLogger("undo block entity ${pos}, $beType")
                    if (entry.state.hasBlockEntity()) {
                        // Dont use EntityBlock.newBlockEntity, piston bug
                        val be = beType.create(pos, entry.state)
                            ?: return@let
                        val beData = entry.beData ?: return@let

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
                        (world.getBlockEntity(pos) as BlockEntityInterface).saveLastNbt()
                    }
                }
            }
            record.entities.forEach {
                val entity = world.getEntity(it.key)
                if (entity == null) {
                    if (it.value != PlayerData.NotExistEntityEntry) {
                        val entry = it.value
                        debugLogger("undo entity ${it.key} spawning")
                        val newEntity = entry.entity!!.spawn(world, { newEntity ->
                            // Note: uuid is different from the original one, set it manually
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
                    if (it.value == PlayerData.NotExistEntityEntry) {
                        debugLogger("undo entity ${it.key} removing")
                        entity.discard()
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
        }
        private fun <T: PlayerData.UndoRedoRecord> MutableList<T>.lastValid(): T? {
            while (this.isNotEmpty()) {
                val last = this.last()
                if (last.data.isNotEmpty() || last.entities.isNotEmpty()) {
                    return last
                }
                // if the last record is empty, remove it
                UndoMixinHelper.removeRecord(last.id)
                this.removeLast()
            }
            return null
        }
        fun register() {
            // Register codec for client->server payloads
            PayloadTypeRegistry.playC2S().register(ID, CODEC)
            // Instead of sending a custom S2C packet back (which requires client-side payload registration),
            // we will send localized system messages directly from server to avoid unknown-payload warnings.
            ServerPlayNetworking.registerGlobalReceiver(ID) { packet, context ->
                val view = context.player().data()
                fun sendStatus(status: Int) {
                    val msg = when (status) {
                        0 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.rollback_success"))
                        1 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.restore_success"))
                        2 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.no_blocks_info"))
                        16 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.no_permission"))
                        32 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.not_recording"))
                        64 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.busy"))
                        65536 -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.unknown_error"))
                        else -> Text.translatable("reden.message.undo.base", Text.translatable("reden.message.undo.unknown_status"))
                    }
                    context.player().sendSystemMessage(msg)
                }
                if (!view.canRecord) {
                    sendStatus(16)
                    return@registerGlobalReceiver
                }
                UndoMixinHelper.playerStopRecording(context.player())
                if (UndoMixinHelper.recording != null) {
                    Reden.LOGGER.error("Undo when a record is still active, id=" + UndoMixinHelper.recording?.id)
                    // 不取消跟踪会导致undo的更改也被记录，边读边写异常
                    UndoMixinHelper.undoRecords.clear()
                }
                when (packet.status) {
                    0 -> view.undo.lastValid()?.let { undoRecord ->
                        view.undo.removeLast()
                        UndoMixinHelper.removeRecord(undoRecord.id) // no longer monitoring rollbacked record
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
