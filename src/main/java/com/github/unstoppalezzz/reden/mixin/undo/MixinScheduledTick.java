package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.UndoableAccess;
//? if >=1.18 {
import net.minecraft.world.ticks.ScheduledTick;
//?} else {
/*import net.minecraft.world.level.TickNextTickData;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

//? if >=1.18 {
@Mixin(ScheduledTick.class)
//?} else {
/*@Mixin(TickNextTickData.class)
*///?}
public class MixinScheduledTick implements UndoableAccess {
    @Unique
    long undoId;

    @Override
    public long getUndoId$reden() {
        return undoId;
    }

    @Override
    public void setUndoId$reden(long l) {
        undoId = l;
    }
}
