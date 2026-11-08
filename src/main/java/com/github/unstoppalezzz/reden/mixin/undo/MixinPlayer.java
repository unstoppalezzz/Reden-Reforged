package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.11
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >=1.21.11 {
@Mixin(Player.class)
//?} else {
/*@Mixin(ServerPlayer.class)
*///?}
public class MixinPlayer {
    @Inject(
            //? if >=1.21.11 {
            method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD")
            //?} else {
            /*method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;attack(Lnet/minecraft/world/entity/Entity;)V",
                    shift = At.Shift.BEFORE
            )
            *///?}
    )
    private void onAttack(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            UndoMixinHelper.playerStartRecording(player, PlayerData.UndoRecord.Cause.ATTACK_ENTITY);
        }
    }

    @Inject(
            //? if >=1.21.11 {
            method = "attack(Lnet/minecraft/world/entity/Entity;)V",
            at = @At("RETURN")
            //?} else {
            /*method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;attack(Lnet/minecraft/world/entity/Entity;)V",
                    shift = At.Shift.AFTER
            )
            *///?}
    )
    private void afterAttack(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            UndoMixinHelper.playerStopAttackRecording(player);
        }
    }

    //? if >=1.21.11 {
    @Inject(method = "stabAttack", at = @At("HEAD"))
    private void beforeStab(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            UndoMixinHelper.playerStartRecording(player, PlayerData.UndoRecord.Cause.ATTACK_ENTITY, 1);
        }
    }

    @Inject(method = "stabAttack", at = @At("RETURN"))
    private void afterStab(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player) {
            UndoMixinHelper.playerStopAttackRecording(player);
        }
    }
    //?}
}
