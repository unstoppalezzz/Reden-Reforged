package com.github.unstoppalezzz.reden.mixin.undo;

import java.util.ArrayDeque;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if >= 1.21.5 {
import net.minecraft.world.entity.InsideBlockEffectApplier;
//?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BasePressurePlateBlock.class)
public abstract class MixinPressurePlateBlock {
    @Shadow
    protected abstract int getSignalForState(BlockState state);

    @Inject(method = "entityInside", at = @At("HEAD"))
//? if >= 1.21.10 {
    private void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci) {
//?} else if >= 1.21.5 {
    /*private void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, CallbackInfo ci) {
*///?} else {
    /*private void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
*///?}
        if (entity instanceof ServerPlayer player) {
            if (getSignalForState(state) == 0) {
                UndoMixinHelper.playerStartRecording(player, PlayerData.UndoRecord.Cause.USE_BLOCK);
            }
            return;
        }
        long id = 0;
        if (!level.isClientSide() && UndoMixinHelper.INSTANCE.getRecording() == null) {
            id = UndoMixinHelper.recordIdForEntity(entity.getUUID());
        }
        if (id != 0) {
            DebugKt.debugLogger.invoke("[plate] " + entity.getType() + " at " + pos.toShortString() + " pressed plate, using record " + id);
            UndoMixinHelper.pushRecord(id, () -> "pressure plate entity/" + pos.toShortString());
        } else if (!level.isClientSide() && UndoMixinHelper.INSTANCE.getRecording() == null) {
            DebugKt.debugLogger.invoke("[plate] " + entity.getType() + " " + entity.getUUID() + " at " + pos.toShortString() + " pressed plate with NO record");
        }
        reden$pushed.push(id != 0);
    }

    @Unique
    private static final ArrayDeque<Boolean> reden$pushed = new ArrayDeque<>();

    @Inject(method = "entityInside", at = @At("RETURN"))
//? if >= 1.21.10 {
    private void afterEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci) {
//?} else if >= 1.21.5 {
    /*private void afterEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, CallbackInfo ci) {
*///?} else {
    /*private void afterEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
*///?}
        if (entity instanceof ServerPlayer player) {
            if (getSignalForState(state) == 0) {
                UndoMixinHelper.playerStopRecording(player);
            }
            return;
        }
        if (!reden$pushed.isEmpty() && reden$pushed.pop()) {
            UndoMixinHelper.popRecord(() -> "pressure plate entity/" + pos.toShortString());
        }
    }
}
