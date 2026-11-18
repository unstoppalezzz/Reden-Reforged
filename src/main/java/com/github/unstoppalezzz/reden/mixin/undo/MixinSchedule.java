package com.github.unstoppalezzz.reden.mixin.undo;

//? if <1.18 {
/*import com.github.unstoppalezzz.reden.access.TickListAccess;
*///?}
import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
//? if >=1.18 {
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//?} else {
/*import net.minecraft.world.level.ServerTickList;
import net.minecraft.world.level.TickNextTickData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.18 {
import java.util.function.BiConsumer;
//?} else {
/*import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
*///?}
import java.util.function.Predicate;

//? if >=1.18 {
@Mixin(LevelTicks.class)
@SuppressWarnings("rawtypes")
public class MixinSchedule {
//?} else {
/*@Mixin(ServerTickList.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class MixinSchedule<T> implements TickListAccess {
    @Shadow @Final private Set<TickNextTickData<T>> tickNextTickSet;
    @Shadow @Final private Set<TickNextTickData<T>> tickNextTickList;

    @Shadow protected abstract void addTickData(TickNextTickData<T> data);
*///?}

    @Inject(
            //? if >=1.18 {
            method = "runCollectedTicks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
            )
            //?} else {
            /*method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
            *///?}
    )
    //? if >=1.18 {
    private <T> void onRunSchedule(BiConsumer<BlockPos, T> biConsumer, CallbackInfo ci, @Local ScheduledTick scheduledTick) {
        BlockPos pos = scheduledTick.pos();
        Object type = scheduledTick.type();
    //?} else {
    /*private void onRunSchedule(CallbackInfo ci, @Local TickNextTickData<T> scheduledTick) {
        BlockPos pos = scheduledTick.pos;
        Object type = scheduledTick.getType();
    *///?}
        long undoId = ((UndoableAccess) scheduledTick).getUndoId$reden();
        long attributed = UndoMixinHelper.attributedRecordId(undoId);
        UndoMixinHelper.pushRecord(attributed, () -> "scheduled tick/" + pos.toShortString());
        if (DebugKt.isDebug()) {
            DebugKt.debugLogger.invoke("[tick] running at " + pos.toShortString() + " type=" + type
                    + " undoId=" + undoId + " attributed=" + attributed
                    + " recordExists=" + (UndoMixinHelper.INSTANCE.getRecording() != null));
        }
    }

    @Inject(
            //? if >=1.18 {
            method = "runCollectedTicks",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"
            )
            //?} else {
            /*method = "tick",
            at = @At(
                    value = "INVOKE",
                    shift = At.Shift.AFTER,
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
            *///?}
    )
    //? if >=1.18 {
    private <T> void afterRunSchedule(BiConsumer<BlockPos, T> biConsumer, CallbackInfo ci, @Local ScheduledTick scheduledTick) {
        BlockPos pos = scheduledTick.pos();
    //?} else {
    /*private void afterRunSchedule(CallbackInfo ci, @Local TickNextTickData<T> scheduledTick) {
        BlockPos pos = scheduledTick.pos;
    *///?}
        UndoMixinHelper.popRecord(() -> "scheduled tick/" + pos.toShortString());
    }

    @WrapOperation(
            //? if >=1.18 {
            method = "runCollectedTicks",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/BiConsumer;accept(Ljava/lang/Object;Ljava/lang/Object;)V"
            )
            //?} else {
            /*method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
            *///?}
    )
    //? if >=1.18 {
    private void wrapRunSchedule(BiConsumer consumer, Object pos, Object type, Operation<Void> original, @Local ScheduledTick scheduledTick) {
        BlockPos tickPos = scheduledTick.pos();
        long triggerTick = scheduledTick.triggerTick();
    //?} else {
    /*private void wrapRunSchedule(Consumer consumer, Object data, Operation<Void> original) {
        TickNextTickData<T> scheduledTick = (TickNextTickData<T>) data;
        BlockPos tickPos = scheduledTick.pos;
        long triggerTick = scheduledTick.triggerTick;
    *///?}
        int now = com.github.unstoppalezzz.reden.utils.UtilsKt.getServer().getTickCount();
        int until = UndoMixinHelper.frozenUntil(tickPos, now);
        if (until == -1) {
            //? if >=1.18 {
            original.call(consumer, pos, type);
            //?} else {
            /*original.call(consumer, data);
            *///?}
            return;
        }
        long deferredTo = triggerTick + (until - now) + 1;
        DebugKt.debugLogger.invoke("[tick] frozen at " + tickPos.toShortString() + " until=" + until + " now=" + now
                + ", DEFERRED (not dropped) to " + deferredTo);
        //? if >=1.18 {
        ((LevelTicks) (Object) this).schedule(new ScheduledTick(scheduledTick.type(), tickPos, deferredTo, 0L));
        //?} else {
        /*addTickData(new TickNextTickData<>(tickPos, scheduledTick.getType(), deferredTo, scheduledTick.priority));
        *///?}
    }

    //? if >=1.18 {
    @ModifyVariable(method = "clearArea", at = @At("STORE"), ordinal = 0)
    private Predicate filterClearArea(Predicate predicate) {
        Predicate filter = UndoMixinHelper.tickClearFilter;
        return filter == null ? predicate : predicate.and(filter);
    }
    //?} else {
    /*@Override
    public void clearArea$reden(BoundingBox box) {
        Predicate<Object> filter = UndoMixinHelper.tickClearFilter;
        Iterator<TickNextTickData<T>> it = tickNextTickList.iterator();
        while (it.hasNext()) {
            TickNextTickData<T> tick = it.next();
            if (box.isInside(tick.pos) && (filter == null || filter.test(tick))) {
                it.remove();
                tickNextTickSet.remove(tick);
            }
        }
    }
    *///?}

    //? if >=1.18 {
    @Inject(method = "schedule", at = @At("HEAD"))
    private <T> void onAddSchedule(ScheduledTick<T> scheduledTick, CallbackInfo ci) {
        BlockPos pos = scheduledTick.pos();
        long triggerTick = scheduledTick.triggerTick();
    //?} else {
    /*@Inject(method = "addTickData", at = @At("HEAD"))
    private void onAddSchedule(TickNextTickData<T> scheduledTick, CallbackInfo ci) {
        BlockPos pos = scheduledTick.pos;
        long triggerTick = scheduledTick.triggerTick;
    *///?}
        long id = UndoMixinHelper.inheritedRecordId();
        if (id != 0) {
            DebugKt.debugLogger.invoke("Scheduled tick at " + pos + ", adding it into record " + id);
            ((UndoableAccess) scheduledTick).setUndoId$reden(id);
        } else if (DebugKt.isDebug()) {
            DebugKt.debugLogger.invoke("Scheduled tick at " + pos + " with NO record (trigger tick " + triggerTick + ")");
        }
    }
}
