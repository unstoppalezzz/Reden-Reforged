package com.github.unstoppalezzz.reden.mixin.mods.g4mespeed_capture_playback;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.g4mesoft.captureplayback.stream.handler.GSServerWorldSignalEventContext", remap = false)
public abstract class MixinWorldSignalEventContext {
    @Shadow @Final private ServerLevel world;

    @Unique private int reden$depth = 0;
    @Unique private boolean reden$pushed = false;

    @Unique
    private void reden$begin() {
        if (reden$depth++ == 0) reden$pushed = UndoMixinHelper.beginPlaybackDispatch(world);
    }

    @Unique
    private void reden$end() {
        if (--reden$depth == 0 && reden$pushed) {
            reden$pushed = false;
            UndoMixinHelper.endPlaybackDispatch();
        }
    }

    @Inject(method = "dispatchBlockEvent", at = @At("HEAD"), remap = false, require = 0)
    private void reden$beforeBlockEvent(CallbackInfoReturnable<Boolean> cir) { reden$begin(); }

    @Inject(method = "dispatchBlockEvent", at = @At("RETURN"), remap = false, require = 0)
    private void reden$afterBlockEvent(CallbackInfoReturnable<Boolean> cir) { reden$end(); }

    @Inject(method = "dispatchNeighborUpdate", at = @At("HEAD"), remap = false, require = 0)
    private void reden$beforeNeighborUpdate(CallbackInfo ci) { reden$begin(); }

    @Inject(method = "dispatchNeighborUpdate", at = @At("RETURN"), remap = false, require = 0)
    private void reden$afterNeighborUpdate(CallbackInfo ci) { reden$end(); }

    @Inject(method = "setState", at = @At("HEAD"), remap = false, require = 0)
    private void reden$beforeSetState(CallbackInfoReturnable<Boolean> cir) { reden$begin(); }

    @Inject(method = "setState", at = @At("RETURN"), remap = false, require = 0)
    private void reden$afterSetState(CallbackInfoReturnable<Boolean> cir) { reden$end(); }
}
