package com.github.unstoppalezzz.reden.mixin.mods.malilib;

import com.github.unstoppalezzz.reden.access.NamedSliderConfig;
import fi.dy.masa.malilib.config.IConfigInteger;
import fi.dy.masa.malilib.config.gui.SliderCallbackInteger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SliderCallbackInteger.class, remap = false)
public abstract class MixinSliderCallbackInteger {
    @Shadow @Final private IConfigInteger config;

    @Inject(method = "getFormattedDisplayValue", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void reden$namedSliderValue(CallbackInfoReturnable<String> cir) {
        if (config instanceof NamedSliderConfig named) cir.setReturnValue(named.getSliderDisplayName());
    }

    @Inject(method = "getMaxSteps", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void reden$narrowNamedSliderHandle(CallbackInfoReturnable<Integer> cir) {
        if (config instanceof NamedSliderConfig) cir.setReturnValue(1000);
    }
}
