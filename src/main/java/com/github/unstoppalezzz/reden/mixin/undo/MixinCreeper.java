package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.CreeperAccess;
import com.github.unstoppalezzz.reden.access.UndoableAccess;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import com.github.unstoppalezzz.reden.utils.multiver.MultiVersionKt;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Creeper.class)
public abstract class MixinCreeper extends Entity implements CreeperAccess {
    @Shadow @Final private static EntityDataAccessor<Boolean> DATA_IS_IGNITED;
    @Shadow private int swell;
    @Shadow public abstract void setSwellDir(int direction);

    @Unique private boolean pushedRecord$reden;

    public MixinCreeper(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void clearFuse$reden() {
        entityData.set(DATA_IS_IGNITED, false);
        setSwellDir(-1);
        swell = 0;
    }

    @Inject(method = "ignite", at = @At("HEAD"))
    private void beforeIgnite(CallbackInfo ci) {
        if (MultiVersionKt.getLevelCompat(this).isClientSide()) return;
        long id = UndoMixinHelper.inheritedRecordId();
        if (id == 0) return;
        DebugKt.debugLogger.invoke("Creeper ignited, adding it into record " + id);
        UndoMixinHelper.recordEntityState(this);
        ((UndoableAccess) this).setUndoId$reden(id);
    }

    @Inject(method = "explodeCreeper", at = @At("HEAD"))
    private void beforeExplode(CallbackInfo ci) {
        pushedRecord$reden = false;
        if (MultiVersionKt.getLevelCompat(this).isClientSide()) return;
        long id = ((UndoableAccess) this).getUndoId$reden();
        if (id == 0 || !UndoMixinHelper.INSTANCE.getUndoRecordsMap().containsKey(id)) return;
        UndoMixinHelper.pushRecord(id, () -> "creeper explode/" + getId());
        pushedRecord$reden = true;
    }

    @Inject(method = "explodeCreeper", at = @At("RETURN"))
    private void afterExplode(CallbackInfo ci) {
        if (pushedRecord$reden) {
            pushedRecord$reden = false;
            UndoMixinHelper.popRecord(() -> "creeper explode/" + getId());
        }
    }
}
