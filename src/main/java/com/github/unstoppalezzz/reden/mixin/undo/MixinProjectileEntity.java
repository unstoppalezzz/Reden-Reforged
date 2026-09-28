package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class MixinProjectileEntity extends Entity implements UndoableAccess, TraceableEntity {
    public MixinProjectileEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
            method = "onHit",
            at = @At("HEAD")
    )
    private void beforeHit(HitResult hitResult, CallbackInfo ci) {
        if (this.getOwner() instanceof ServerPlayer) {
            UndoMixinHelper.pushRecord(getUndoId$reden(), () -> "projectile hit/" + getId());
        }
    }

    @Inject(
            method = "onHit",
            at = @At("RETURN")
    )
    private void afterHit(HitResult hitResult, CallbackInfo ci) {
        if (this.getOwner() instanceof ServerPlayer) {
            UndoMixinHelper.popRecord(() -> "projectile hit/" + getId());
        }
    }

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void initUndoId(EntityType<?> entityType, Level level, CallbackInfo ci) {
        if (!level.isClientSide()) {
            PlayerData.UndoRecord recording = UndoMixinHelper.INSTANCE.getRecording();
            if (recording != null) {
                DebugKt.debugLogger.invoke("Projectile spawned, adding it into record " + recording.getId());
                setUndoId$reden(recording.getId());
            }
        }
    }
}
