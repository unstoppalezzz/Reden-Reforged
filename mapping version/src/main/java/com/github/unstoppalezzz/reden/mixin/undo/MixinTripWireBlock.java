package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.access.PlayerData;
import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(TripWireBlock.class)
public class MixinTripWireBlock {
    private static final ArrayDeque<Boolean> reden$pushed = new ArrayDeque<>();

    private static String reden$reason(BlockPos pos) {
        return "tripwire self-trigger/" + pos.toShortString();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        boolean pushed = false;
        if (UndoMixinHelper.INSTANCE.getRecording() == null) {
            long id = UndoMixinHelper.taggedRecordId(pos);
            if (id != 0) {
                UndoMixinHelper.pushRecord(id, () -> reden$reason(pos));
                pushed = true;
            }
        }
        reden$pushed.push(pushed);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (!reden$pushed.isEmpty() && reden$pushed.pop()) {
            UndoMixinHelper.popRecord(() -> reden$reason(pos));
        }
    }

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void onEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci) {
        if (level.isClientSide()) return;
        int now = com.github.unstoppalezzz.reden.utils.UtilsKt.getServer().getTickCount();
        if (UndoMixinHelper.isFrozen(pos, now)) {
            ci.cancel();
            return;
        }
        boolean pushed = false;
       
        if (UndoMixinHelper.INSTANCE.getRecording() == null) {
            long id = UndoMixinHelper.taggedRecordId(pos);
            if (id != 0) {
                UndoMixinHelper.pushRecord(id, () -> reden$reason(pos));
                pushed = true;
            }
        }
        if (!pushed && entity instanceof ServerPlayer player) {
            UndoMixinHelper.playerStartRecording(player, PlayerData.UndoRecord.Cause.USE_BLOCK);
        }
        reden$pushed.push(pushed);
    }

    @Inject(method = "entityInside", at = @At("RETURN"))
    private void afterEntityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean bl, CallbackInfo ci) {
        if (level.isClientSide()) return;
        if (!reden$pushed.isEmpty() && reden$pushed.pop()) {
            UndoMixinHelper.popRecord(() -> reden$reason(pos));
        }
        if (entity instanceof ServerPlayer player) {
            UndoMixinHelper.playerStopRecording(player);
        }
    }
}
