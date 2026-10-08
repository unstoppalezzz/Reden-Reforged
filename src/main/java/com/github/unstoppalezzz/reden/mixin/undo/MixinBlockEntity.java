package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.BlockEntityInterface;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import com.github.unstoppalezzz.reden.utils.DebugKt;
import net.minecraft.core.BlockPos;
//? if >=1.20.5 {
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
//?}
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
    //? if >=1.20.5 {
    @Shadow private DataComponentMap components;
    //?} else {
    /*// data components don't exist before 1.20.5
    @Unique private final Object components = null;
    *///?}
    //? if < 1.20.5 {
    /*@Shadow public abstract CompoundTag saveWithId();
    *///?} elif < 1.21.6 {
    /*@Shadow public abstract CompoundTag saveWithId(HolderLookup.Provider provider);
    *///?} else {
    @Shadow public abstract void saveWithId(net.minecraft.world.level.storage.ValueOutput par1);
    //?}

    @Unique CompoundTag lastSavedNbt = null;
    @Unique int lastSaveTime = 0;
    @Unique Object lastComponents = null;

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
*///?} elif >= 1.20.5 {
            /*lastSavedNbt = this.saveWithId(level.registryAccess());
*///?} else {
            /*lastSavedNbt = this.saveWithId();
*///?}
            DebugKt.debugLogger.invoke("saved lastNBT at " + worldPosition.toShortString() + ", cause=" + cause + ", " + lastSavedNbt);
        }
//? if =1.21.5 || =1.21.7 || =1.21.8 {
        /*if (level instanceof ServerLevel serverLevel) {
            lastSaveTime = serverLevel.getServer().getTickCount();
        }
*///?} else {
        lastSaveTime = com.github.unstoppalezzz.reden.utils.UtilsKt.getServer().getTickCount();
//?}
    }

    @Unique
    private boolean isComponentsValid(Object lastComponents) {
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
            //? if >=1.20.5 {
            method = "loadWithComponents",
            //?} else {
            /*method = "load",
            *///?}
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
*///?} elif >= 1.20.5 {
                /*lastSavedNbt = this.saveWithId(level.registryAccess());
*///?} else {
                /*lastSavedNbt = this.saveWithId();
*///?}
                DebugKt.debugLogger.invoke("init: saved lastNBT at " + worldPosition.toShortString() + ", cause=reden init, " + lastSavedNbt);
            }
        } else {
            DebugKt.debugLogger.invoke("init: skip saving lastNBT at " + worldPosition.toShortString());
        }
    }
}
