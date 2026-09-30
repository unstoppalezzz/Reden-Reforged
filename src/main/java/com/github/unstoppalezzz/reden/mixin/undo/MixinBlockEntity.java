package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.BlockEntityInterface;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
//? if >= 1.21.6 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
//?}
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class MixinBlockEntity implements BlockEntityInterface {
    @Shadow @Nullable protected Level level;
    @Final @Shadow protected BlockPos worldPosition;
    @Shadow private BlockState blockState;
    @Shadow private DataComponentMap components;
    //? if < 1.21.6 {
    /*@Shadow public abstract CompoundTag saveWithId(HolderLookup.Provider provider);
    *///?} else {
    @Shadow public abstract void saveWithId(net.minecraft.world.level.storage.ValueOutput par1);
    //?}

    @Unique CompoundTag lastSavedNbt = null;
    @Unique int lastSaveTime = 0;
    @Unique DataComponentMap lastComponents = null;

    @Override
    public void saveLastNbt$reden() {
        if (level != null && !level.isClientSide()) {
            DebugKt.debugLogger.invoke("before saving lastNBT at " + worldPosition.toShortString() + ", nbt=" + lastSavedNbt + ", components=" + components);
            if (lastSaveTime == com.github.unstoppalezzz.reden.utils.UtilsKt.getServer().getTickCount()) {
                return;
            }
            reden$snapshot("reden manually");
        }
    }

    @Unique
    private void reden$snapshot(String cause) {
        if (isComponentsValid(components)) {
            lastComponents = components;
            DebugKt.debugLogger.invoke("saved lastComponents at " + worldPosition.toShortString() + ", cause=" + cause + ", " + lastComponents);
        } else if (level != null) {
//? if >= 26.1 {
            try {
                var vo = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                        net.minecraft.util.ProblemReporter.DISCARDING,
                        level.registryAccess()
                );
                this.saveWithId(vo);
                lastSavedNbt = vo.buildResult();
            } catch (Throwable t) {
            }
//?} else if >= 1.21.6 {
            /*TagValueOutput vo = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            this.saveWithId(vo);
            lastSavedNbt = vo.buildResult();
*///?} else {
            /*lastSavedNbt = this.saveWithId(level.registryAccess());
*///?}
            DebugKt.debugLogger.invoke("saved lastNBT at " + worldPosition.toShortString() + ", cause=" + cause + ", " + lastSavedNbt);
        }
        lastSaveTime = com.github.unstoppalezzz.reden.utils.UtilsKt.getServer().getTickCount();
    }

    @Unique
    private boolean isComponentsValid(DataComponentMap lastComponents) {
        return false;
    }

    @Override
    public @Nullable Object getLastSavedNbt$reden() {
        if (isComponentsValid(lastComponents)) {
            DebugKt.debugLogger.invoke("getLastSavedNbt at " + worldPosition.toShortString() + ", using lastComponents=" + lastComponents);
            return lastComponents;
        } else if (lastSavedNbt != null) {
            DebugKt.debugLogger.invoke("getLastSavedNbt at " + worldPosition.toShortString() + ", using lastSavedNbt=" + lastSavedNbt);
            return lastSavedNbt;
        } else {
            DebugKt.debugLogger.invoke("getLastSavedNbt at " + worldPosition.toShortString() + ", no saved data");
            return null;
        }
    }

    @Inject(
            method = "setChanged()V",
            at = @At("HEAD")
    )
    private void onBlockEntityChanged(CallbackInfo ci) {
        if (level instanceof ServerLevel serverLevel) {
            UndoMixinHelper.postSetBlock(serverLevel, worldPosition, blockState, true);
        }
    }

    @Inject(
            method = "setChanged()V",
            at = @At("TAIL")
    )
    private void reden$refreshSnapshotAfterChange(CallbackInfo ci) {
        if (level instanceof ServerLevel) {
            reden$snapshot("after setChanged");
        }
    }

    @Inject(
            method = "setLevel",
            at = @At("TAIL")
    )
    private void reden$snapshotOnLevelSet(CallbackInfo ci) {
        if (level instanceof ServerLevel && lastSavedNbt == null && lastComponents == null) {
            reden$snapshot("level set");
        }
    }

    @Inject(
            method = "loadWithComponents",
            at = @At("TAIL")
    )
    private void onReadNbt(CallbackInfo ci) {
        DebugKt.debugLogger.invoke("init: before saving lastNBT at " + worldPosition.toShortString() + ", data=" + lastSavedNbt);
            if (lastSavedNbt == null && lastComponents == null) {
            if (isComponentsValid(components)) {
                lastComponents = components;
                DebugKt.debugLogger.invoke("init: saved lastComponents at " + worldPosition.toShortString() + ", cause=reden init, " + lastComponents);
            } else if (level != null) {
//? if >= 26.1 {
                try {
                    var vo = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                            net.minecraft.util.ProblemReporter.DISCARDING,
                            level.registryAccess()
                    );
                    this.saveWithId(vo);
                    lastSavedNbt = vo.buildResult();
                } catch (Throwable t) {
                }
//?} else if >= 1.21.6 {
                /*TagValueOutput vo = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                this.saveWithId(vo);
                lastSavedNbt = vo.buildResult();
*///?} else {
                /*lastSavedNbt = this.saveWithId(level.registryAccess());
*///?}
                DebugKt.debugLogger.invoke("init: saved lastNBT at " + worldPosition.toShortString() + ", cause=reden init, " + lastSavedNbt);
            }
        } else {
            DebugKt.debugLogger.invoke("init: skip saving lastNBT at " + worldPosition.toShortString());
        }
    }
}
