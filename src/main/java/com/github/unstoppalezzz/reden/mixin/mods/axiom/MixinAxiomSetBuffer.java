package com.github.unstoppalezzz.reden.mixin.mods.axiom;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.packets.AxiomServerboundSetBuffer", remap = false)
public abstract class MixinAxiomSetBuffer {
    @Inject(method = "applyBlockBufferServer", at = @At("HEAD"), remap = false, require = 0)
    private static void reden$beginAxiomBufferEdit(@Coerce Object buffer, ServerLevel level, @Coerce Object region, ServerPlayer player, CallbackInfo ci) {
        UndoMixinHelper.beginAxiomBufferEdit(player, level, buffer);
    }

    @Inject(method = "applyBlockBufferServer", at = @At("RETURN"), remap = false, require = 0)
    private static void reden$endAxiomBufferEdit(@Coerce Object buffer, ServerLevel level, @Coerce Object region, ServerPlayer player, CallbackInfo ci) {
        if (player != null) UndoMixinHelper.endAxiomEdit(player);
    }
}
