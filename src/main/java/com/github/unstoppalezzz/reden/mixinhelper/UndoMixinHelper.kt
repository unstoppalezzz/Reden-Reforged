package com.github.unstoppalezzz.reden.mixinhelper

import com.github.unstoppalezzz.reden.Reden
import com.github.unstoppalezzz.reden.access.BlockEntityInterface
import com.github.unstoppalezzz.reden.access.ChunkSectionInterface
import com.github.unstoppalezzz.reden.access.PlayerData
import com.github.unstoppalezzz.reden.access.PlayerData.Companion.data
import com.github.unstoppalezzz.reden.access.UndoableAccess
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.monitorSetBlock
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.playerStartRecording
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.playerStopRecording
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.popRecord
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.pushRecord
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.recordId
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.recording
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.undoRecords
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper.undoRecordsMap
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks


object UndoMixinHelper {
    private const val PRIMARY_CAPTURE_RADIUS = 2
    private const val SECONDARY_CAPTURE_RADIUS = 3

    private var throttleTick = Int.MIN_VALUE
    private val genericOriginsExpandedThisTick = mutableSetOf<Long>()

    private fun resetPerTickThrottlesIfNeeded(tick: Int) {
        if (tick != throttleTick) {
            throttleTick = tick
            genericOriginsExpandedThisTick.clear()
        }
    }

    private fun shouldSkipGenericSnapshotOrigin(world: ServerLevel, origin: BlockPos, isDirectTrigger: Boolean): Boolean {
        if (isDirectTrigger) return false
        resetPerTickThrottlesIfNeeded(world.server.tickCount)
        return !genericOriginsExpandedThisTick.add(origin.asLong())
    }

    private fun collectNearbyCapturePositions(origin: BlockPos, radius: Int): LinkedHashSet<BlockPos> {
        val positions = linkedSetOf<BlockPos>()
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                for (dz in -radius..radius) {
                    positions += BlockPos(origin.x + dx, origin.y + dy, origin.z + dz)
                }
            }
        }
        return positions
    }

    private fun collectPrimaryCapturePositions(origin: BlockPos): LinkedHashSet<BlockPos> =
        collectNearbyCapturePositions(origin, PRIMARY_CAPTURE_RADIUS)

    private fun collectSecondaryCapturePositions(origin: BlockPos): LinkedHashSet<BlockPos> {
        val primary = collectPrimaryCapturePositions(origin)
        val secondary = collectNearbyCapturePositions(origin, SECONDARY_CAPTURE_RADIUS)
        secondary.removeAll(primary)
        return secondary
    }

    private fun collectExpandedCapturePositions(origin: BlockPos): LinkedHashSet<BlockPos> {
        return collectNearbyCapturePositions(origin, SECONDARY_CAPTURE_RADIUS)
    }

    private fun isRelevantRedstoneComponent(state: BlockState): Boolean {
        val block = state.block
        return state.hasAnalogOutputSignal() ||
            block is net.minecraft.world.level.block.DiodeBlock ||
            block is net.minecraft.world.level.block.ObserverBlock ||
            block is net.minecraft.world.level.block.LeverBlock ||
            block is net.minecraft.world.level.block.ButtonBlock ||
            block is net.minecraft.world.level.block.PressurePlateBlock ||
            block is net.minecraft.world.level.block.RedstoneTorchBlock ||
            block is net.minecraft.world.level.block.RedStoneWireBlock ||
            block is net.minecraft.world.level.block.TargetBlock ||
            block is net.minecraft.world.level.block.NoteBlock ||
            block is net.minecraft.world.level.block.TripWireBlock ||
            block is net.minecraft.world.level.block.TripWireHookBlock ||
            block is net.minecraft.world.level.block.DaylightDetectorBlock ||
            block is net.minecraft.world.level.block.RedstoneLampBlock ||
            block is net.minecraft.world.level.block.BaseRailBlock ||
            block is net.minecraft.world.level.block.piston.PistonBaseBlock ||
            block is net.minecraft.world.level.block.piston.PistonHeadBlock ||
            block is net.minecraft.world.level.block.piston.MovingPistonBlock
    }

    private fun shouldRunRedstoneNeighborhoodScan(state: BlockState): Boolean {
        val block = state.block
        return block is net.minecraft.world.level.block.DiodeBlock ||
            block is net.minecraft.world.level.block.ObserverBlock ||
            block is net.minecraft.world.level.block.LeverBlock ||
            block is net.minecraft.world.level.block.ButtonBlock ||
            block is net.minecraft.world.level.block.PressurePlateBlock ||
            block is net.minecraft.world.level.block.RedstoneTorchBlock ||
            block is net.minecraft.world.level.block.TargetBlock ||
            block is net.minecraft.world.level.block.NoteBlock ||
            block is net.minecraft.world.level.block.TripWireBlock ||
            block is net.minecraft.world.level.block.TripWireHookBlock ||
            block is net.minecraft.world.level.block.DaylightDetectorBlock ||
            block is net.minecraft.world.level.block.RedstoneLampBlock ||
            block is net.minecraft.world.level.block.BaseRailBlock ||
            block is net.minecraft.world.level.block.piston.PistonBaseBlock ||
            block is net.minecraft.world.level.block.piston.PistonHeadBlock ||
            block is net.minecraft.world.level.block.piston.MovingPistonBlock ||
            block == Blocks.HOPPER
    }

    @JvmField
    var isRestoring = false
    class UndoRecordEntry(val id: Long, val record: PlayerData.UndoRecord?, val reason: String)
    private var recordId = 20060210L
    val undoRecordsMap: MutableMap<Long, PlayerData.UndoRecord> = HashMap()
    internal val undoRecords = mutableListOf<UndoRecordEntry>()


    fun cleanup() {
        undoRecordsMap.clear()
        undoRecords.clear()
    }

    private fun filterLogById(undoId: Long) =
        undoId != 0L

    @JvmStatic
    fun pushRecord(id: Long, reasonSupplier: () -> String): Boolean {
        val reason = reasonSupplier()
        return undoRecords.add(
            UndoRecordEntry(
                id,
                undoRecordsMap[id],
                reason
            )
        )
    }
    @JvmStatic
    fun popRecord(reasonSupplier: () -> String): UndoRecordEntry {
        val reason = reasonSupplier()
        if (reason != undoRecords.last().reason) {
            throw IllegalStateException("Cannot pop record with different reason: $reason != ${undoRecords.last().reason}")
        }
        return undoRecords.removeLast()
    }
    val recording: PlayerData.UndoRecord? get() = undoRecords.lastOrNull()?.record

    private fun captureBaselineIfAbsent(
        world: ServerLevel,
        pos: BlockPos,
        transform: (PlayerData.Entry) -> PlayerData.Entry = { it }
    ) {
        val rec = recording ?: return
        val key = pos.asLong()
        if (rec.data.containsKey(key)) return
        world.modified(pos)
        val be = world.getChunk(pos).getBlockEntity(pos)
        if (be is net.minecraft.world.level.block.entity.HopperBlockEntity) {
            (be as? UndoableAccess)?.undoId = rec.id
        }
        (be as? BlockEntityInterface)?.saveLastNbt()
        val entry = transform(rec.fromWorld(world, pos, true))
        rec.data.putIfAbsent(key, entry)
    }

    private fun captureComparatorSnapshot(world: ServerLevel, pos: BlockPos) {
        try {
            val state = world.getBlockState(pos)
            if (state.block != Blocks.COMPARATOR) return
            val be = world.getBlockEntity(pos) as? BlockEntityInterface ?: return
            be.saveLastNbt()
        } catch (_: Throwable) {
        }
    }

    private fun captureHopperSnapshot(world: ServerLevel, pos: BlockPos) {
        try {
            val state = world.getBlockState(pos)
            if (state.block != Blocks.HOPPER) return
            val be = world.getBlockEntity(pos) as? BlockEntityInterface ?: return
            be.saveLastNbt()
        } catch (_: Throwable) {
        }
    }

    private fun captureNearbyComparatorOrHopperNeighbors(world: ServerLevel, pos: BlockPos) {
        for (dir in Direction.values()) {
            val npos = pos.relative(dir)
            captureBaselineIfAbsent(world, npos)
        }

        val localCandidates = linkedSetOf<BlockPos>()
        for (dx in -SECONDARY_CAPTURE_RADIUS..SECONDARY_CAPTURE_RADIUS) {
            for (dy in -SECONDARY_CAPTURE_RADIUS..SECONDARY_CAPTURE_RADIUS) {
                for (dz in -SECONDARY_CAPTURE_RADIUS..SECONDARY_CAPTURE_RADIUS) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    localCandidates += BlockPos(pos.x + dx, pos.y + dy, pos.z + dz)
                }
            }
        }

        for (candidate in localCandidates) {
            val state = world.getBlockState(candidate)
            if (state.block == Blocks.AIR) continue
            if (state.block == Blocks.REDSTONE_WIRE || state.block is net.minecraft.world.level.block.BaseRailBlock ||
                isRelevantRedstoneComponent(state) || state.block == Blocks.HOPPER || state.block == Blocks.COMPARATOR
            ) {
                captureBaselineIfAbsent(world, candidate)
            }
        }
    }

    private fun captureNearbyRedstoneSnapshot(
        world: ServerLevel,
        pos: BlockPos,
        isDirectTrigger: Boolean = true,
        visitedThisPass: MutableSet<Long> = mutableSetOf()
    ) {
        try {
            if (!visitedThisPass.add(pos.asLong())) {
                return
            }

            val originSkip = shouldSkipGenericSnapshotOrigin(world, pos, isDirectTrigger)
            if (originSkip) {
                return
            }

            val candidates = collectExpandedCapturePositions(pos)
            for (candidate in candidates) {
                val state = world.getBlockState(candidate)
                if (state.block == Blocks.AIR) continue
                if (candidate == pos || state.block == Blocks.REDSTONE_WIRE || state.block is net.minecraft.world.level.block.BaseRailBlock ||
                    isRelevantRedstoneComponent(state)
                ) {
                    captureBaselineIfAbsent(world, candidate)
                }
            }

        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun monitorSetBlock(world: ServerLevel, pos: BlockPos, blockState: BlockState) {
        if (isRestoring) return
        world.modified(pos)

        if (recording == null) return

        captureComparatorSnapshot(world, pos)
        captureHopperSnapshot(world, pos)
        if (shouldRunRedstoneNeighborhoodScan(blockState)) {
            captureNearbyRedstoneSnapshot(world, pos)
        }

        captureBaselineIfAbsent(world, pos)
        try {
            if (blockState.block == Blocks.COMPARATOR || blockState.block == Blocks.HOPPER) {
                captureNearbyComparatorOrHopperNeighbors(world, pos)
            }
        } catch (_: Throwable) {
        }
        recording?.lastChangedTick = world.server.tickCount
    }


    @JvmStatic
    fun monitorSetBlock(blockEntity: Any?) {
        if (isRestoring) return
        if (blockEntity !is BlockEntity) return
        val world = blockEntity.level
        if (world is ServerLevel) {
            world.modified(blockEntity.blockPos)
            if (recording == null) return

            captureComparatorSnapshot(world, blockEntity.blockPos)
            captureHopperSnapshot(world, blockEntity.blockPos)
            if (shouldRunRedstoneNeighborhoodScan(blockEntity.blockState)) {
                captureNearbyRedstoneSnapshot(world, blockEntity.blockPos)
            }

            captureBaselineIfAbsent(world, blockEntity.blockPos)
            try {
                if (blockEntity.blockState.block == Blocks.COMPARATOR || blockEntity.blockState.block == Blocks.HOPPER) {
                    captureNearbyComparatorOrHopperNeighbors(world, blockEntity.blockPos)
                }
            } catch (_: Throwable) {
            }
            recording?.lastChangedTick = world.server.tickCount
        }
    }

    fun ServerLevel.modified(pos: BlockPos, time: Int = server.tickCount) = getChunk(pos).run {
        //? if <= 1.21.1
        /*isUnsaved = true*/
        //? if >= 1.21.2
        markUnsaved()
        getSection(getSectionIndex(pos.y)) as ChunkSectionInterface
    }.setModifyTime(pos, time)

    @JvmStatic
    fun postSetBlock(world: ServerLevel, pos: BlockPos, finalState: BlockState, beChangeOnly: Boolean) {
        if (isRestoring) return
        val be = world.getBlockEntity(pos) as BlockEntityInterface?
        if (be != null) {
            val data = be.lastSavedNbt

            if (beChangeOnly) {
                world.modified(pos)
                captureBaselineIfAbsent(world, pos) { entry ->
                    if (data != null) entry.copy(beData = data) else entry
                }
            }
        }
    }

    /**
     * 此函数有危险副作用
     *
     * 使用此函数将**立刻**产生缓存的副作用
     *
     * 此缓存可能在没有确认的情况下不经检查直接调用
     */
    private fun addRecord(
        cause: PlayerData.UndoRecord.Cause,
        player: ServerPlayer
    ): PlayerData.UndoRecord {
        if (undoRecords.size != 0) {
            throw IllegalStateException("Cannot add record when there is already one.")
        }
        val undoRecord = PlayerData.UndoRecord(
            id = recordId,
            //? if <= 1.21.5
            /*lastChangedTick = player.server.tickCount,*/
            //? if >= 1.21.6
            lastChangedTick = player.level().server.tickCount,
            cause = cause
        )
        undoRecordsMap[recordId] = undoRecord
        recordId++
        return undoRecord
    }

    internal fun removeRecord(id: Long) = undoRecordsMap.remove(id)

    @Suppress("unused")
    @JvmStatic
    fun playerStartRecording(player: ServerPlayer) = playerStartRecording(player, PlayerData.UndoRecord.Cause.UNKNOWN)
    @JvmStatic
    fun playerStartRecording(
        player: ServerPlayer,
        cause: PlayerData.UndoRecord.Cause
    ) {
        val playerView = player.data()
        if (!playerView.canRecord) return
        if (!playerView.isRecording) {
            playerView.isRecording = true
            val record = addRecord(cause, player)
            playerView.undo.add(record)
            pushRecord(record.id) { "player recording/${player.scoreboardName}/$cause" }
        }
    }

    @JvmStatic
    fun playerStopRecording(player: ServerPlayer) {
        val playerView = player.data()
        if (playerView.isRecording) {
            playerView.isRecording = false
            popRecord { "player recording/${player.scoreboardName}/${recording?.cause}" }
            playerView.redo
                .onEach { removeRecord(it.id) }
                .clear()
            var sum = playerView.undo.map(PlayerData.UndoRecord::getMemorySize).sum()
            val allowedUndoSizeInBytes = 30 * 1024 * 1024
            if (allowedUndoSizeInBytes >= 0) {
                while (sum > allowedUndoSizeInBytes) {
                    removeRecord(playerView.undo.first().id)
                    playerView.undo.removeFirst()
                    sum = playerView.undo.map(PlayerData.UndoRecord::getMemorySize).sum()
                }
            }
        }
    }

    private fun playerQuit(player: ServerPlayer) =
        player.data().undo.forEach { removeRecord(it.id) }

    @JvmStatic
    fun tryAddRelatedEntity(entity: Entity) {
        if (entity.noPhysics) return
        if (entity is ServerPlayer) return
        if (!isInitializingEntity) {
            recording?.entities?.computeIfAbsent(entity.uuid) {
                PlayerData.EntityEntryImpl(
                    entity.type,
                    //? if < 1.21.6 {
                    /*CompoundTag().apply(entity::saveWithoutId),
                    *///?} else {
                    net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        entity.level().registryAccess()
                    ).apply(entity::saveWithoutId).buildResult(),
                    //?}
                    entity.blockPosition()
                )
            }
        }
    }

    @JvmField var isInitializingEntity = false

    @JvmStatic
    fun entitySpawned(entity: Entity) {
        if (entity is ServerPlayer) return
        recording?.entities?.putIfAbsent(entity.uuid, PlayerData.NotExistEntityEntry)
    }

    init {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> playerQuit(handler.player) }
    }
}
