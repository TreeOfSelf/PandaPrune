package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.FeatureSpills;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
	@Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
	private void restoreAndRecord(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
		if (level instanceof WorldGenRegion region) FeatureSpills.begin(region, chunk);
	}

	@Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
	private void storeRecords(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager, CallbackInfo ci) {
		if (level instanceof WorldGenRegion region) FeatureSpills.end(region, chunk);
	}
}
