package net.TreeOfSelf.PandaPrune;

import net.TreeOfSelf.PandaPrune.mixin.blend.BlendingDataAccessor;
import net.TreeOfSelf.PandaPrune.mixin.blend.ChunkGeneratorInvoker;
import net.TreeOfSelf.PandaPrune.mixin.blend.NoiseBasedChunkGeneratorInvoker;
import net.minecraft.core.CompositeDirection.Direction8;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FreshTerrain {
	private static final int MAX_CACHED = 2048;
	private static final Map<ResourceKey<Level>, Map<Long, CompletableFuture<Fresh>>> CACHE = new ConcurrentHashMap<>();

	public static final AtomicLong generated = new AtomicLong();
	public static final AtomicLong generateNanos = new AtomicLong();
	public static final AtomicLong[] phaseNanos = {new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};
	private static final boolean CARVERS = !Boolean.getBoolean("pandaprune.freshNoCarvers");

	private FreshTerrain() {
	}

	public static void clear() {
		CACHE.clear();
	}

	public static Fresh get(ServerLevel level, int chunkX, int chunkZ, BlendingData old) {
		Map<Long, CompletableFuture<Fresh>> map = CACHE.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>());
		long key = ChunkPos.pack(chunkX, chunkZ);
		CompletableFuture<Fresh> future = map.get(key);
		if (future == null) {
			CompletableFuture<Fresh> mine = new CompletableFuture<>();
			future = map.putIfAbsent(key, mine);
			if (future == null) {
				if (map.size() > MAX_CACHED) map.entrySet().removeIf(e -> e.getValue().isDone() && e.getKey() != key);
				try {
					long start = System.nanoTime();
					mine.complete(generate(level, new ChunkPos(chunkX, chunkZ), old));
					generateNanos.addAndGet(System.nanoTime() - start);
					generated.incrementAndGet();
				} catch (Throwable t) {
					PandaPrune.LOGGER.error("Could not generate fresh terrain at chunk {} {}, blending it the vanilla way", chunkX, chunkZ, t);
					mine.complete(null);
				}
				return mine.join();
			}
		}
		return future.join();
	}

	private static Fresh generate(ServerLevel level, ChunkPos pos, BlendingData old) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) return null;
		NoiseBasedChunkGeneratorInvoker invoker = (NoiseBasedChunkGeneratorInvoker) (Object) noiseGenerator;
		RandomState randomState = level.getChunkSource().randomState();
		NoiseGeneratorSettings settings = noiseGenerator.generatorSettings().value();
		MaterialRule materialRule = settings.materialRule().value();

		long t0 = System.nanoTime();
		ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
		((ChunkGeneratorInvoker) generator).pandaprune$doCreateBiomes(Blender.empty(), randomState, chunk);
		chunk.setPersistedStatus(ChunkStatus.BIOMES);
		long t1 = System.nanoTime();

		DensityBufferPool pool = randomState.acquireDensityBufferPool();
		long t2, t3, t4;
		try {
			Climate.Sampler climate = randomState.createClimateSampler(SamplerContext.builder().enableCaches().useBufferArena(pool).build());
			int minQuartX = QuartPos.fromBlock(pos.getMinBlockX()) - 1;
			int minQuartY = QuartPos.fromBlock(chunk.getMinY());
			int minQuartZ = QuartPos.fromBlock(pos.getMinBlockZ()) - 1;
			int quartsY = QuartPos.fromBlock(chunk.getHeight());
			BiomeResolver around = generator.getBiomeSource().createResolverForChunk(climate, minQuartX, minQuartY, minQuartZ, 6, quartsY, 6);
			Set<Holder<Biome>> possibleBiomes = new HashSet<>();
			for (int qx = 0; qx < 6; qx++) {
				for (int qz = 0; qz < 6; qz++) {
					for (int qy = 0; qy < quartsY; qy++) possibleBiomes.add(around.getNoiseBiome(minQuartX + qx, minQuartY + qy, minQuartZ + qz));
				}
			}
			BiomeResolver uncached = generator.getBiomeSource().createUncachedResolver(randomState);
			BiomeResolver biomes = (quartX, quartY, quartZ) -> {
				if (quartX > minQuartX && quartX < minQuartX + 5 && quartZ > minQuartZ && quartZ < minQuartZ + 5) return chunk.getNoiseBiome(quartX, quartY, quartZ);
				if (quartX >= minQuartX && quartX < minQuartX + 6 && quartZ >= minQuartZ && quartZ < minQuartZ + 6 && quartY >= minQuartY && quartY < minQuartY + quartsY) {
					return around.getNoiseBiome(quartX, quartY, quartZ);
				}
				return uncached.getNoiseBiome(quartX, quartY, quartZ);
			};
			BiomeManager biomeManager = level.getBiomeManager().withDifferentSource(biomes);

			NoiseSettings noiseSettings = settings.noiseSettings().clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
			DensityVolume volume = new DensityVolume(16, noiseSettings.height(), 16, pos.getMinBlockX(), noiseSettings.minY(), pos.getMinBlockZ());
			try (NoiseChunk noiseChunk = new NoiseChunk(randomState, null, settings, invoker.pandaprune$globalFluidPicker().get(), Blender.empty(), volume)) {
				invoker.pandaprune$doFill(noiseChunk, chunk);
				t2 = System.nanoTime();
				invoker.pandaprune$buildSurface(chunk, noiseChunk, randomState, biomeManager, possibleBiomes, materialRule);
				t3 = System.nanoTime();
				if (CARVERS) invoker.pandaprune$generateCarvers(chunk, Blender.empty(), noiseChunk, randomState, biomeManager, null, materialRule);
			}
			t4 = System.nanoTime();
		} finally {
			randomState.releaseDensityBufferPool(pool);
		}

		LevelHeightAccessor area = old.getAreaWithOldGeneration();
		BlendingData fresh = BlendingData.unpack(new BlendingData.Packed(area.getMinSectionY(), area.getMaxSectionY() + 1, Optional.empty()));
		((BlendingDataAccessor) fresh).pandaprune$calculateData(chunk, EnumSet.allOf(Direction8.class));
		long t5 = System.nanoTime();
		phaseNanos[0].addAndGet(t1 - t0);
		phaseNanos[1].addAndGet(t2 - t1);
		phaseNanos[2].addAndGet(t3 - t2);
		phaseNanos[3].addAndGet(t4 - t3);
		phaseNanos[4].addAndGet(t5 - t4);
		float[] heights = ((BlendingDataAccessor) fresh).pandaprune$heights();
		float[] surface = new float[heights.length];
		for (int i = 0; i < heights.length; i++) {
			int x = Math.min(15, BlendingDataAccessor.pandaprune$getX(i) * 4);
			int z = Math.min(15, BlendingDataAccessor.pandaprune$getZ(i) * 4);
			surface[i] = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
		}
		return new Fresh(fresh, surface);
	}

	public record Fresh(BlendingData data, float[] surface) {
	}
}
