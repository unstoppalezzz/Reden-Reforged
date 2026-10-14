package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class MixinItemEntity {
    @Inject(method = "setItem", at = @At("RETURN"))
    private void afterSetItem(ItemStack itemStack, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide()) {
            UndoMixinHelper.itemEntityStackSet(self);
        }
    }
}
