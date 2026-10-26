package net.TreeOfSelf.PandaPrune.mixin.blend;

import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;
import java.util.function.Supplier;

@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorInvoker {
	@Accessor("globalFluidPicker")
	Supplier<Aquifer.FluidPicker> pandaprune$globalFluidPicker();

	@Invoker("doFill")
	void pandaprune$doFill(NoiseChunk noiseChunk, ChunkAccess chunk);

	@Invoker("buildSurface")
	void pandaprune$buildSurface(ChunkAccess protoChunk, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager,
								 Set<Holder<Biome>> possibleBiomes, MaterialRule materialRule);

	@Invoker("generateCarvers")
	void pandaprune$generateCarvers(ChunkAccess chunk, Blender blender, NoiseChunk noiseChunk, RandomState randomState, BiomeManager biomeManager,
									WorldGenRegion carverBiomeRegion, MaterialRule materialRule);
}
