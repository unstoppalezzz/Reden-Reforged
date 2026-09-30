package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MovingPistonBlock.class)
public class MixinMovingPiston {
    @Overwrite
    @Nullable
    public BlockEntityTicker<PistonMovingBlockEntity> getTicker(Level level, BlockState blockState, BlockEntityType<PistonMovingBlockEntity> type) {
        //? if >=26.1 {
        return (world1, pos, state1, be) -> {
        //?} else {
        /*return (type == BlockEntityType.PISTON) ? (world1, pos, state1, be) -> {
        *///?}
            boolean shouldTrack = be.getProgress(1) >= 1.0f // current progress, delta=1
                    && !world1.isClientSide(); // server side
            if (shouldTrack) {
                if (be instanceof UndoableAccess access) {
                    UndoMixinHelper.pushRecord(access.getUndoId$reden(), () -> "piston block entity tick/" + pos.toShortString());
                    if (DebugKt.isDebug()) {
                        DebugKt.debugLogger.invoke("[piston] finishing move at " + pos.toShortString() + " carried=" + be.getMovedState()
                                + " source=" + be.isSourcePiston() + " undoId=" + access.getUndoId$reden()
                                + " recordExists=" + (UndoMixinHelper.INSTANCE.getRecording() != null));
                    }
                }
            }
            PistonMovingBlockEntity.tick(world1, pos, state1, be);
            if (shouldTrack) {
                if (be instanceof UndoableAccess) {
                    UndoMixinHelper.popRecord(() -> "piston block entity tick/" + pos.toShortString());
                }
            }
        //? if >=26.1 {
        };
        //?} else {
        /*} : null;
        *///?}
    }
}
