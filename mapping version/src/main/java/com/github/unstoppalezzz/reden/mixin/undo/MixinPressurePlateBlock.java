package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BasePressurePlateBlock.class)
public class MixinPressurePlateBlock {
    @Inject(method = "entityInside", at = @At("HEAD"))
    private void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            UndoMixinHelper.playerStartRecording(player, PlayerData.UndoRecord.Cause.USE_BLOCK);
        }
    }

    @Inject(method = "entityInside", at = @At("RETURN"))
    private void afterEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci) {
        if (entity instanceof ServerPlayer player) {
            UndoMixinHelper.playerStopRecording(player);
        }
    }
}
