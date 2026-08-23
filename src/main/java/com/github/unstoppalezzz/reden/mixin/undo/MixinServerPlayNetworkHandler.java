package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerPlayNetworkHandler {
    @Shadow
    @Final
    public ServerPlayer player;

    @Inject(method = "handleInteract", at = @At("HEAD"))
    public void beforePlayerUseEntity(ServerboundInteractPacket packet, CallbackInfo ci) {
        UndoMixinHelper.playerStartRecording(player, PlayerData.UndoRecord.Cause.USE_ENTITY);
    }

    @Inject(method = "handleInteract", at = @At("RETURN"))
    public void afterPlayerUseEntity(ServerboundInteractPacket packet, CallbackInfo ci) {
        UndoMixinHelper.playerStopRecording(player);
    }
}
