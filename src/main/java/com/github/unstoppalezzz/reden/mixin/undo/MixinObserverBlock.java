package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ObserverBlock.class)
public class MixinObserverBlock {
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!state.getValue(BlockStateProperties.POWERED)) {
            UndoMixinHelper.observerFired(pos);
        }
    }
}
