package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
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
        long id = UndoMixinHelper.inheritedRecordId();
        if (id != 0) {
            undoId = id;
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

    @WrapMethod(method = "moveCollidedEntities")
    private static void reden$trackCollided(Level level, BlockPos pos, float f, PistonMovingBlockEntity be, Operation<Void> original) {
        reden$withRecord(level, pos, be, () -> original.call(level, pos, f, be));
    }

    @WrapMethod(method = "moveStuckEntities")
    private static void reden$trackStuck(Level level, BlockPos pos, float f, PistonMovingBlockEntity be, Operation<Void> original) {
        reden$withRecord(level, pos, be, () -> original.call(level, pos, f, be));
    }

    @Unique
    private static void reden$withRecord(Level level, BlockPos pos, PistonMovingBlockEntity be, Runnable action) {
        long id = level.isClientSide() ? 0 : ((UndoableAccess) be).getUndoId$reden();
        if (id == 0 || UndoMixinHelper.INSTANCE.getRecording() != null) {
            action.run();
            return;
        }
        String reason = "piston entities/" + pos.toShortString();
        UndoMixinHelper.pushRecord(id, () -> reason);
        try {
            action.run();
        } finally {
            UndoMixinHelper.popRecord(() -> reason);
        }
    }
}
