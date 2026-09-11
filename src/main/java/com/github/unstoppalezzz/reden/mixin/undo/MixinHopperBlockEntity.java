package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.BlockEntityInterface;
import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hopper's item transfer mutates its inventory directly, without ever going through
 * {@code LevelChunk.setBlockState} - so {@link com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper#monitorSetBlock}
 * never sees it coming. The only signal we get is {@code BlockEntity.setChanged()}, and vanilla
 * always calls that AFTER the mutation already happened - there is no "about to change" hook for
 * container contents. So whichever code path is first to call saveLastNbt() on the hopper after
 * the transfer (its own setChanged hook, or a comparator/hopper expanded search sweeping past it)
 * ends up caching the ALREADY-mutated inventory as the undo baseline, not the true pre-transfer one.
 * <p>
 * pushItemsTick is the hopper's own ticker entry point, called once per hopper per tick, BEFORE
 * its internal suckInItems/tryMoveItems/ejectItems logic runs. Snapshotting here guarantees the
 * cache always holds the true pre-transfer state before this tick can mutate anything, closing
 * that race regardless of what triggers the capture afterward. saveLastNbt$reden() already
 * no-ops if something saved this tick, so this is cheap even when nothing is about to change.
 */
@Mixin(HopperBlockEntity.class)
public abstract class MixinHopperBlockEntity implements UndoableAccess {
    @Unique
    private long undoId;

    @Unique
    private boolean reden$pushedRecordThisTick;

    @Override
    public long getUndoId$reden() {
        return undoId;
    }

    @Override
    public void setUndoId$reden(long l) {
        undoId = l;
    }

    @Inject(method = "pushItemsTick", at = @At("HEAD"))
    private static void reden$captureBeforeTransfer(
            Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, CallbackInfo ci
    ) {
        if (level instanceof ServerLevel && !level.isClientSide()) {
            MixinHopperBlockEntity self = (MixinHopperBlockEntity) (Object) blockEntity;
            boolean recordingActive = UndoMixinHelper.INSTANCE.getRecording() != null;
            // This fires every tick for every loaded hopper world-wide, so only pay for the NBT
            // serialize when it could possibly matter: a recording is active right now, or this
            // hopper carries a tag from one that might get reinstated below. For every other
            // hopper - the overwhelming majority outside of active undo tracking - nothing
            // downstream ever reads lastSavedNbt, since monitorSetBlock now bails immediately
            // without a recording too.
            if (recordingActive || self.undoId != 0) {
                ((BlockEntityInterface) blockEntity).saveLastNbt$reden();
            }

            if (!recordingActive && self.undoId != 0) {
                self.reden$pushedRecordThisTick = UndoMixinHelper.pushRecord(
                        self.undoId, () -> "hopper self-tick/" + pos.toShortString());
            } else {
                self.reden$pushedRecordThisTick = false;
            }
        }
    }

    @Inject(method = "pushItemsTick", at = @At("RETURN"))
    private static void reden$popRecordAfterTransfer(
            Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity, CallbackInfo ci
    ) {
        if (level instanceof ServerLevel && !level.isClientSide()) {
            MixinHopperBlockEntity self = (MixinHopperBlockEntity) (Object) blockEntity;
            if (self.reden$pushedRecordThisTick) {
                self.reden$pushedRecordThisTick = false;
                UndoMixinHelper.popRecord(() -> "hopper self-tick/" + pos.toShortString());
            }
        }
    }
}
