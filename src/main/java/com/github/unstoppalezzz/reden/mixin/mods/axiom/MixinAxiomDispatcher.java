package com.github.unstoppalezzz.reden.mixin.mods.axiom;

import com.github.unstoppalezzz.reden.undo.AxiomKeys;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.world_modification.Dispatcher", remap = false)
public abstract class MixinAxiomDispatcher {
    @Inject(method = "callAction", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void reden$supersedeAxiomUndo(@Coerce Object action, Object argument, CallbackInfoReturnable<Object> cir) {
        Object result = AxiomKeys.supersededResult(action);
        if (result != null) cir.setReturnValue(result);
    }
}
