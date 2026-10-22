package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.KillCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(KillCommand.class)
public class MixinKillCommand {
    @Inject(method = "kill", at = @At("HEAD"))
    private static void onKill(CommandSourceStack source, Collection<? extends Entity> targets, CallbackInfoReturnable<Integer> cir) {
        for (Entity target : targets) {
            if (target instanceof ServerPlayer player) {
                UndoMixinHelper.playerKilled(player);
            }
        }
    }
}
