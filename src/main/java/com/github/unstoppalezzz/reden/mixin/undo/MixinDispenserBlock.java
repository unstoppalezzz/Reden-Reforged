package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
//? if >=1.19
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(DispenserBlock.class)
public class MixinDispenserBlock {
    private static final ArrayDeque<Boolean> reden$pushed = new ArrayDeque<>();

    private static String reden$reason(BlockPos pos) {
        return "dispenser self-tick/" + pos.toShortString();
    }

    @Inject(method = "dispenseFrom", at = @At("HEAD"))
    //? if >=1.20.2 {
    private void reden$captureDispenserSnapshot(ServerLevel level, BlockState state, BlockPos pos, CallbackInfo ci) {
    //?} else {
    /*private void reden$captureDispenserSnapshot(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        BlockState state = level.getBlockState(pos);
    *///?}
        UndoMixinHelper.captureDispenserSnapshot(level, pos, pos.relative(state.getValue(DispenserBlock.FACING)));
    }

    @Inject(method = "tick", at = @At("HEAD"))
    //? if >=1.19 {
    private void onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
    //?} else {
    /*private void onTick(BlockState state, ServerLevel level, BlockPos pos, java.util.Random random, CallbackInfo ci) {
    *///?}
        boolean pushed = false;
        if (UndoMixinHelper.INSTANCE.getRecording() == null) {
            long id = UndoMixinHelper.taggedRecordId(pos);
            if (id != 0) {
                UndoMixinHelper.pushRecord(id, () -> reden$reason(pos));
                pushed = true;
            }
        }
        reden$pushed.push(pushed);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    //? if >=1.19 {
    private void afterTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
    //?} else {
    /*private void afterTick(BlockState state, ServerLevel level, BlockPos pos, java.util.Random random, CallbackInfo ci) {
    *///?}
        if (!reden$pushed.isEmpty() && reden$pushed.pop()) {
            UndoMixinHelper.popRecord(() -> reden$reason(pos));
        }
    }
}
