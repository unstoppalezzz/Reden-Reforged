package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(LevelTicks.class)
@SuppressWarnings("rawtypes")
public class MixinSchedule {
    @Inject(
            method = "runCollectedTicks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
            )
    )
    private <T> void onRunSchedule(BiConsumer<BlockPos, T> biConsumer, CallbackInfo ci, @Local ScheduledTick scheduledTick) {
        if (1 == 1) {
            long undoId = ((UndoableAccess) scheduledTick).getUndoId$reden();
            UndoMixinHelper.pushRecord(UndoMixinHelper.attributedRecordId(undoId), () -> "scheduled tick/" + scheduledTick.pos().toShortString());
        }
    }
    @Inject(
            method = "runCollectedTicks",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"
            )
    )
    private <T> void afterRunSchedule(BiConsumer<BlockPos, T> biConsumer, CallbackInfo ci, @Local ScheduledTick scheduledTick) {
        if (1 == 1) {
            UndoMixinHelper.popRecord(() -> "scheduled tick/" + scheduledTick.pos().toShortString());
        }
    }
    @WrapOperation(
            method = "runCollectedTicks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"
            )
    )
    private void wrapRunSchedule(BiConsumer consumer, Object pos, Object type, Operation<Void> original, @Local ScheduledTick scheduledTick) {
        int now = com.github.unstoppalezzz.reden.utils.UtilsKt.getServer().getTickCount();
        int until = UndoMixinHelper.frozenUntil(scheduledTick.pos(), now);
        if (until == -1) {
            original.call(consumer, pos, type);
            return;
        }
        ((LevelTicks) (Object) this).schedule(new ScheduledTick(
                scheduledTick.type(), scheduledTick.pos(), scheduledTick.triggerTick() + (until - now) + 1, 0L));
    }

    @Inject(
            method = "schedule",
            at = @At(
                    value = "HEAD"
            )
    )
    private <T> void onAddSchedule(ScheduledTick<T> scheduledTick, CallbackInfo ci) {
        long id = UndoMixinHelper.inheritedRecordId();
        if (id != 0) {
            DebugKt.debugLogger.invoke("Scheduled tick at " + scheduledTick.pos() + ", adding it into record " + id);
            // inherit parent (or lingering) id
            ((UndoableAccess) scheduledTick).setUndoId$reden(id);
        }
    }
}
