package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
//? if >=1.20.5
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SynchedEntityData.class)
public class MixinDataTracker {
    @Shadow
    @Final
    //? if >=1.20.5 {
    private SyncedDataHolder entity;
    //?} else {
    /*private Entity entity;
    *///?}

    @Inject(
            method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V",
            at = @At("HEAD")
    )
    private <T> void beforeDataSet(EntityDataAccessor<T> entityDataAccessor, T object, boolean bl, CallbackInfo ci) {
        if ((Object) entity instanceof Entity modifiedEntity) {
            if (modifiedEntity.level().isClientSide()) return;
            UndoMixinHelper.tryAddRelatedEntity(modifiedEntity);
        }
    }
}
