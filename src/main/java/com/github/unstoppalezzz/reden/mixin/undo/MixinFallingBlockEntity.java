package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FallingBlockEntity.class)
public abstract class MixinFallingBlockEntity extends Entity implements UndoableAccess {
    @Unique private boolean pushedRecord$reden;

    public MixinFallingBlockEntity(EntityType<?> entityType, Level level) {
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
                DebugKt.debugLogger.invoke("Falling block spawned, adding it into record " + id);
                setUndoId$reden(id);
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void beforeTick(CallbackInfo ci) {
        pushedRecord$reden = false;
        if (level().isClientSide()) return;
        long id = getUndoId$reden();
        if (id == 0 || !UndoMixinHelper.INSTANCE.getUndoRecordsMap().containsKey(id)) return;
        UndoMixinHelper.pushRecord(id, () -> "falling block tick/" + getId());
        pushedRecord$reden = true;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(CallbackInfo ci) {
        if (pushedRecord$reden) {
            pushedRecord$reden = false;
            UndoMixinHelper.popRecord(() -> "falling block tick/" + getId());
        }
    }
}
