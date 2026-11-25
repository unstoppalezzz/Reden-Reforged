package com.github.unstoppalezzz.reden.mixin.mods.axiom;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "com.moulberry.axiom.packets.AxiomServerboundSetBlock",
        "com.moulberry.axiom.packets.AxiomServerboundSpawnEntity",
        "com.moulberry.axiom.packets.AxiomServerboundFixArea",
        "com.moulberry.axiom.packets.AxiomServerboundTickBlocks"
}, remap = false)
public abstract class MixinAxiomPackets {
    @Inject(method = "handle", at = @At("HEAD"), remap = false, require = 0)
    private void reden$beginAxiomEdit(MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        UndoMixinHelper.beginAxiomEdit(player);
    }

    @Inject(method = "handle", at = @At("RETURN"), remap = false, require = 0)
    private void reden$endAxiomEdit(MinecraftServer server, ServerPlayer player, CallbackInfo ci) {
        UndoMixinHelper.endAxiomEdit(player);
    }
}
