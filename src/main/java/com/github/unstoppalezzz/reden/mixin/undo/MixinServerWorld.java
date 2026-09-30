package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockEventData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public abstract class MixinServerWorld {
    @ModifyArg(
            method = "blockEvent",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/ObjectLinkedOpenHashSet;add(Ljava/lang/Object;)Z",
                    remap = false
            )
    )
    private Object beforeAddSyncedBlockEvent(Object event) { // BlockEvent
        if (event instanceof UndoableAccess access) {
            PlayerData.UndoRecord recording = UndoMixinHelper.INSTANCE.getRecording();
            if (recording != null) {
                access.setUndoId$reden(recording.getId());
            }
            if (DebugKt.isDebug() && event instanceof BlockEventData data) {
                DebugKt.debugLogger.invoke("[block event] queued at " + data.pos().toShortString() + " block=" + data.block()
                        + " params=" + data.paramA() + "/" + data.paramB()
                        + " record=" + (recording != null ? recording.getId() : 0)
                        + " inherited=" + UndoMixinHelper.inheritedRecordId());
            }
        }
        return event;
    }

    @Inject(
            method = "doBlockEvent",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.BEFORE,
                    target = "Lnet/minecraft/world/level/block/state/BlockState;triggerEvent(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;II)Z"
            )
    )
    private void beforeProcessBlockEvent(BlockEventData event, CallbackInfoReturnable<Boolean> cir) {
        long undoId = ((UndoableAccess) event).getUndoId$reden();
        long attributed = UndoMixinHelper.attributedRecordId(undoId);
        UndoMixinHelper.pushRecord(attributed, () -> "block event/" + event.pos().toShortString());
        if (DebugKt.isDebug()) {
            DebugKt.debugLogger.invoke("[block event] running at " + event.pos().toShortString() + " block=" + event.block()
                    + " params=" + event.paramA() + "/" + event.paramB()
                    + " undoId=" + undoId + " attributed=" + attributed
                    + " recordExists=" + (UndoMixinHelper.INSTANCE.getRecording() != null));
        }
    }

    @Inject(
            method = "doBlockEvent",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Lnet/minecraft/world/level/block/state/BlockState;triggerEvent(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;II)Z"
            )
    )
    private void afterProcessBlockEvent(BlockEventData event, CallbackInfoReturnable<Boolean> cir) {
        UndoMixinHelper.popRecord(() -> "block event/" + event.pos().toShortString());
    }

    @Inject(
            method = "addEntity",
            at = @At("RETURN")
    )
    private void afterSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        UndoMixinHelper.isInitializingEntity = false;
    }

    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void beforeAddBlockEvent(BlockPos pos, net.minecraft.world.level.block.Block block, int id, int param, CallbackInfo ci) {
        if (UndoMixinHelper.isFrozen(pos, com.github.unstoppalezzz.reden.utils.UtilsKt.getServer().getTickCount())) {
            DebugKt.debugLogger.invoke("[block event] cancelled at frozen " + pos.toShortString() + " block=" + block);
            ci.cancel();
        }
    }
}
