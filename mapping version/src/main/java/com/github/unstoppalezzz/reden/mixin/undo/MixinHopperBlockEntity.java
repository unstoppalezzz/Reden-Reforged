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
