package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Commands.class)
public class MixinCommands {
    @WrapOperation(
            method = "performCommand",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/SharedConstants;IS_RUNNING_IN_IDE:Z"
            )
    )
    private boolean printException(Operation<Boolean> original) {
        return true;
    }

    @Inject(
            method = "performCommand",
            at = @At(
                    value = "INVOKE",
                    //? if >=1.20.3 {
                    target = "Lnet/minecraft/commands/Commands;executeCommandInContext(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/function/Consumer;)V",
                    //?} elif >=1.19.1 {
                    /*target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/ParseResults;)I",
                    *///?} else {
                    /*target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/StringReader;Ljava/lang/Object;)I",
                    *///?}
                    shift = At.Shift.BEFORE
            )
    )
    // performCommand returned int before 1.20.3
    //? if >=1.20.3 {
    private void onExecute(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
        CommandSourceStack source = parseResults.getContext().getSource();
    //?} elif >=1.19.1 {
    /*private void onExecute(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfoReturnable<Integer> ci) {
        CommandSourceStack source = parseResults.getContext().getSource();
    *///?} else {
    /*private void onExecute(CommandSourceStack source, String command, CallbackInfoReturnable<Integer> ci) {
    *///?}
        if (reden$isTickCommand(command)) return;
        if (source.getEntity() instanceof ServerPlayer player) {
            UndoMixinHelper.playerStartRecording(player, PlayerData.UndoRecord.Cause.COMMAND);
        }
    }

    @Inject(
            method = "performCommand",
            at = @At(
                    value = "INVOKE",
                    //? if >=1.20.3 {
                    target = "Lnet/minecraft/commands/Commands;executeCommandInContext(Lnet/minecraft/commands/CommandSourceStack;Ljava/util/function/Consumer;)V",
                    //?} elif >=1.19.1 {
                    /*target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/ParseResults;)I",
                    *///?} else {
                    /*target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/StringReader;Ljava/lang/Object;)I",
                    *///?}
                    shift = At.Shift.AFTER
            )
    )
    // performCommand returned int before 1.20.3
    //? if >=1.20.3 {
    private void afterExecute(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo ci) {
        CommandSourceStack source = parseResults.getContext().getSource();
    //?} elif >=1.19.1 {
    /*private void afterExecute(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfoReturnable<Integer> ci) {
        CommandSourceStack source = parseResults.getContext().getSource();
    *///?} else {
    /*private void afterExecute(CommandSourceStack source, String command, CallbackInfoReturnable<Integer> ci) {
    *///?}
        if (reden$isTickCommand(command)) return;
        if (source.getEntity() instanceof ServerPlayer player) {
            UndoMixinHelper.playerStopRecording(player);
        }
    }

    /** /tick freeze, /tick step etc. only control the tick rate; they are not an undoable player action. */
    @Unique
    private static boolean reden$isTickCommand(String command) {
        String trimmed = command.stripLeading();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        return trimmed.equals("tick") || trimmed.startsWith("tick ");
    }
}
