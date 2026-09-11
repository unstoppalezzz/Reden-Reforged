package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PistonMovingBlockEntity.class)
public class MixinPistonEntity implements UndoableAccess {
    @Unique
    long undoId;

    @Override
    public long getUndoId$reden() {
        return undoId;
    }

    @Override
    public void setUndoId$reden(long undoId) {
        this.undoId = undoId;
    }

    @Inject(method = "<init>(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;ZZ)V", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        PlayerData.UndoRecord recording = UndoMixinHelper.INSTANCE.getRecording();
        if (recording != null) {
            undoId = recording.getId();
        }
    }

    @Inject(method = "finalTick", at = @At("HEAD"))
    private void beforeFinish(CallbackInfo ci) {
        if (undoId != 0) {
        }
    }

    @Inject(method = "finalTick", at = @At("RETURN"))
    private void afterFinish(CallbackInfo ci) {
        if (undoId != 0) {
        }
    }
}
