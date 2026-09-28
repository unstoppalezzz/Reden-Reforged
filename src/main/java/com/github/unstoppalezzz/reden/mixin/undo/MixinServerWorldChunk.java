package com.github.unstoppalezzz.reden.mixin.undo;

import com.github.unstoppalezzz.reden.mixinhelper.UndoMixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
//? if >= 1.21.9 {
import net.minecraft.world.level.chunk.PalettedContainerFactory;
//?} else {
/*import net.minecraft.core.Registry;
*///?}
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class MixinServerWorldChunk extends ChunkAccess {
    @Shadow @Final Level level;

//? if >= 1.21.9 {
    public MixinServerWorldChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, PalettedContainerFactory palettedContainerFactory, long l, @Nullable LevelChunkSection[] levelChunkSections, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, palettedContainerFactory, l, levelChunkSections, blendingData);
    }
//?} else {
    /*public MixinServerWorldChunk(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor, Registry<Biome> registry, long l, @Nullable LevelChunkSection[] levelChunkSections, @Nullable BlendingData blendingData) {
        super(chunkPos, upgradeData, levelHeightAccessor, registry, l, levelChunkSections, blendingData);
    }
*///?}

    @Inject(
            method = "setBlockState",
            at = @At("HEAD")
    )
    //? if < 1.21.5 {
    /*private void monitorSetBlock(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
    *///?} else {
    private void monitorSetBlock(BlockPos pos, BlockState state, int i, CallbackInfoReturnable<BlockState> cir) {
    //?}
        if (level instanceof ServerLevel serverLevel) {
            UndoMixinHelper.monitorSetBlock(serverLevel, pos, state);
        }
    }

    @Inject(
            method = "setBlockState",
            at = @At("TAIL")
    )
    //? if < 1.21.5 {
    /*private void afterSetBlock(BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
    *///?} else {
    private void afterSetBlock(BlockPos pos, BlockState state, int i, CallbackInfoReturnable<BlockState> cir) {
    //?}
        if (level instanceof ServerLevel serverLevel) {
            UndoMixinHelper.postSetBlock(serverLevel, pos, state, false);
        }
    }
}
