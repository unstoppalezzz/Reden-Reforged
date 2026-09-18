package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public abstract class MixinTntEntity extends Entity implements UndoableAccess {
    public MixinTntEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
            method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
            at = @At("RETURN")
    )
    private void onInit(EntityType<?> entityType, Level level, CallbackInfo ci) {
        if (!level.isClientSide()) {
            long id = UndoMixinHelper.inheritedRecordId();
            if (id != 0) {
                DebugKt.debugLogger.invoke("TNT spawned, adding it into record " + id);
                setUndoId$reden(id);
            }
        }
    }

    @Inject(method = "explode", at = @At("HEAD"))
    private void beforeExplode(CallbackInfo ci) {
        UndoMixinHelper.pushRecord(getUndoId$reden(), () -> "tnt explode/" + getId());
    }

    @Inject(method = "explode", at = @At("TAIL"))
    private void afterExplode(CallbackInfo ci) {
        UndoMixinHelper.popRecord(() -> "tnt explode/" + getId());
    }
}
