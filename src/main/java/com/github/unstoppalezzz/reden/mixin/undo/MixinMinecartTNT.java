package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
// @formatter:off
//? if >= 1.21.11 {
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
//?} else {
/*import net.minecraft.world.entity.vehicle.MinecartTNT;
*///?}
// @formatter:on
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartTNT.class)
public abstract class MixinMinecartTNT {
    @Unique private boolean pushedRecord$reden;

    @Inject(method = "destroy", at = @At("HEAD"))
    private void beforeDestroy(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        UndoMixinHelper.recordEntityState(self);
    }

    @Inject(method = "primeFuse", at = @At("HEAD"))
    private void beforePrime(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        UndoMixinHelper.releaseTntMinecart(self);
        long id = UndoMixinHelper.inheritedRecordId();
        if (id == 0) return;
        DebugKt.debugLogger.invoke("TNT minecart primed, adding it into record " + id);
        UndoMixinHelper.recordEntityState(self);
        ((UndoableAccess) this).setUndoId$reden(id);
    }

    @Inject(method = "explode(Lnet/minecraft/world/damagesource/DamageSource;D)V", at = @At("HEAD"), cancellable = true)
    private void beforeExplode(DamageSource source, double power, CallbackInfo ci) {
        pushedRecord$reden = false;
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide()) return;
        boolean impact = source == null || source.getDirectEntity() == null;
        if (impact && !((MinecartTNT) (Object) this).isPrimed() && UndoMixinHelper.isTntMinecartHeld(self)) {
            ci.cancel();
            return;
        }
        UndoMixinHelper.releaseTntMinecart(self);
        long id = ((UndoableAccess) this).getUndoId$reden();
        if (id == 0 || !UndoMixinHelper.INSTANCE.getUndoRecordsMap().containsKey(id)) {
            id = UndoMixinHelper.recordIdForEntity(self.getUUID());
        }
        if (id == 0) return;
        UndoMixinHelper.pushRecord(id, () -> "tnt minecart explode/" + self.getId());
        pushedRecord$reden = true;
        UndoMixinHelper.recordEntityState(self);
    }

    @Inject(method = "explode(Lnet/minecraft/world/damagesource/DamageSource;D)V", at = @At("RETURN"))
    private void afterExplode(DamageSource source, double power, CallbackInfo ci) {
        if (pushedRecord$reden) {
            pushedRecord$reden = false;
            UndoMixinHelper.popRecord(() -> "tnt minecart explode/" + ((Entity) (Object) this).getId());
        }
    }
}
