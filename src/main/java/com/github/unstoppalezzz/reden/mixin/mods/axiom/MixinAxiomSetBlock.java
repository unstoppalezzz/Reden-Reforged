package com.github.unstoppalezzz.reden.mixin.mods.axiom;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBlock", remap = false)
public abstract class MixinAxiomSetBlock {
    @Inject(method = "setWithoutUpdates", at = @At("HEAD"), remap = false, require = 0)
    private static void reden$beforeSetWithoutUpdates(ServerLevel level, BlockPos pos, BlockState state, CallbackInfo ci) {
        UndoMixinHelper.monitorSetBlock(level, pos, state);
    }
}
