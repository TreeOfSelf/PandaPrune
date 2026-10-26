package net.TreeOfSelf.PandaPrune.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.TreeOfSelf.PandaPrune.FreshTerrain;
import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.TreeOfSelf.PandaPrune.mixin.blend.BlendingDataAccessor;
import net.TreeOfSelf.PandaPrune.DiffBlender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Blender.class)
public abstract class BlenderMixin implements DiffBlender {
	@Unique private static final int HEIGHT_RANGE_CELLS = 27;
	@Unique private static final double DENSITY_RANGE_CELLS = 2.0;
	// density difference at which an old cell counts as fully different
	@Unique private static final double DENSITY_MISMATCH = 0.05;
	@Unique private static final float BLOCKS_PER_OFFSET = 128.0F;
	// 2 cells = 8 blocks, so a join is never steeper than 1 in 8
	@Unique private static final double CELLS_PER_BLOCK_OF_DELTA = 2.0;
	@Unique private static final double CELLS_PER_BLOCK_UNDER_WATER = 0.5;
	@Unique private static final int INSIDE_COUNT = 7;
	@Unique private static final int EDGE_MEDIAN_RADIUS = 2;
	@Unique private static final float DEAD_ZONE_BLOCKS = 1.0F;

	@Shadow @Final private Long2ObjectOpenHashMap<BlendingData> heightAndBiomeBlendingData;
	@Shadow @Final private Long2ObjectOpenHashMap<BlendingData> densityBlendingData;
	@Shadow @Final private static Noise SHIFT_NOISE;

	@Unique private Long2ObjectMap<BlendingData> pandaprune$fresh;
	@Unique private Long2ObjectMap<float[]> pandaprune$deltas;
	@Unique private Long2ObjectMap<float[]> pandaprune$ranges;

	@Inject(method = "of", at = @At("HEAD"), cancellable = true)
	private static void noBlending(WorldGenRegion region, CallbackInfoReturnable<Blender> cir) {
		if (!PandaPrune.CONFIG.chunkBlending) cir.setReturnValue(Blender.empty());
	}

	@Inject(method = "of", at = @At("RETURN"))
	private static void attachFresh(WorldGenRegion region, CallbackInfoReturnable<Blender> cir) {
		Blender blender = cir.getReturnValue();
		if (region != null && blender != Blender.empty() && PandaPrune.CONFIG.differenceBlending()) {
			((DiffBlender) blender).pandaprune$attach(region);
		}
	}

	@Override
	public void pandaprune$attach(WorldGenRegion region) {
		ServerLevel level = region.getLevel();
		int seaLevel = level.getChunkSource().getGenerator().getSeaLevel();
		Long2ObjectOpenHashMap<BlendingData> fresh = new Long2ObjectOpenHashMap<>();
		Long2ObjectOpenHashMap<float[]> surfaces = new Long2ObjectOpenHashMap<>();
		for (Long2ObjectMap.Entry<BlendingData> entry : this.heightAndBiomeBlendingData.long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			FreshTerrain.Fresh data = FreshTerrain.get(level, ChunkPos.getX(key), ChunkPos.getZ(key), entry.getValue());
			if (data == null) return;
			fresh.put(key, data.data());
			surfaces.put(key, data.surface());
		}
		Long2ObjectOpenHashMap<float[]> deltas = new Long2ObjectOpenHashMap<>();
		Long2ObjectOpenHashMap<float[]> ranges = new Long2ObjectOpenHashMap<>();
		for (Long2ObjectMap.Entry<BlendingData> entry : this.heightAndBiomeBlendingData.long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			float[] oldHeights = ((BlendingDataAccessor) entry.getValue()).pandaprune$heights();
			float[] freshHeights = ((BlendingDataAccessor) fresh.get(key)).pandaprune$heights();
			float[] raw = pandaprune$rawDeltas(region.getChunk(ChunkPos.getX(key), ChunkPos.getZ(key)), oldHeights, freshHeights, seaLevel);
			float[] smoothed = pandaprune$smoothDeltas(raw);
			deltas.put(key, smoothed);
			ranges.put(key, pandaprune$ranges(smoothed, oldHeights, freshHeights, surfaces.get(key)));
		}
		this.pandaprune$deltas = deltas;
		this.pandaprune$ranges = ranges;
		this.pandaprune$fresh = fresh;
	}

	@Override
	public boolean pandaprune$isDiff() {
		return this.pandaprune$fresh != null;
	}

	@Inject(method = "blendOffsetAndFactor(Lnet/minecraft/world/level/levelgen/densityfunction/DensityVolume;)Lnet/minecraft/world/level/levelgen/blending/Blender$OutputBuffer;",
			at = @At("HEAD"), cancellable = true)
	private void diffOffsets(DensityVolume volume, CallbackInfoReturnable<Blender.OutputBuffer> cir) {
		if (this.pandaprune$fresh == null) return;
		int minCellX = QuartPos.fromBlock(volume.minBlockX());
		int minCellZ = QuartPos.fromBlock(volume.minBlockZ());
		int cellCountX = QuartPos.fromBlock(volume.maxBlockX()) + 1 - minCellX + 1;
		int cellCountZ = QuartPos.fromBlock(volume.maxBlockZ()) + 1 - minCellZ + 1;
		float[] alphas = new float[cellCountX * cellCountZ];
		float[] offsets = new float[cellCountX * cellCountZ];
		for (int cellZ = 0; cellZ < cellCountZ; cellZ++) {
			for (int cellX = 0; cellX < cellCountX; cellX++) {
				int index = cellX + cellZ * cellCountX;
				alphas[index] = 1.0F;
				offsets[index] = this.pandaprune$offset(QuartPos.toBlock(minCellX + cellX), QuartPos.toBlock(minCellZ + cellZ));
			}
		}
		cir.setReturnValue(new Blender.OutputBuffer((Blender) (Object) this, minCellX, minCellZ, cellCountX, cellCountZ, alphas, offsets));
	}

	@Override
	public float pandaprune$offset(int blockX, int blockZ) {
		int cellX = QuartPos.fromBlock(blockX);
		int cellZ = QuartPos.fromBlock(blockZ);
		float fixed = this.pandaprune$fixedDelta(cellX, cellZ);
		if (!Float.isNaN(fixed)) return fixed / BLOCKS_PER_OFFSET;

		double totalWeight = 0.0;
		double weightedDeltas = 0.0;
		for (Long2ObjectMap.Entry<BlendingData> entry : this.heightAndBiomeBlendingData.long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			float[] oldHeights = ((BlendingDataAccessor) entry.getValue()).pandaprune$heights();
			float[] deltas = this.pandaprune$deltas.get(key);
			float[] ranges = this.pandaprune$ranges.get(key);
			int minCellX = QuartPos.fromSection(ChunkPos.getX(key));
			int minCellZ = QuartPos.fromSection(ChunkPos.getZ(key));
			for (int i = 0; i < oldHeights.length; i++) {
				if (oldHeights[i] == Float.MAX_VALUE) continue;
				double distance = Mth.length(cellX - (minCellX + BlendingDataAccessor.pandaprune$getX(i)), cellZ - (minCellZ + BlendingDataAccessor.pandaprune$getZ(i)));
				if (distance > HEIGHT_RANGE_CELLS) continue;
				double weight = 1.0 / (distance * distance * distance * distance);
				float delta = deltas[i];
				double range = ranges[i];
				double alpha = Mth.clamp(distance / (range + 1), 0.0, 1.0);
				alpha = 3.0 * alpha * alpha - 2.0 * alpha * alpha * alpha;
				weightedDeltas += delta * (1.0 - alpha) * weight;
				totalWeight += weight;
			}
		}
		if (totalWeight == 0.0) return 0.0F;
		return (float) (weightedDeltas / totalWeight / BLOCKS_PER_OFFSET);
	}

	@Unique
	private static float[] pandaprune$smoothDeltas(float[] raw) {
		int n = raw.length;
		float[] out = new float[n];
		float[] window = new float[2 * EDGE_MEDIAN_RADIUS + 1];
		for (int i = 0; i < n; i++) {
			if (Float.isNaN(raw[i])) continue;
			int groupStart = i < INSIDE_COUNT ? 0 : INSIDE_COUNT;
			int groupEnd = i < INSIDE_COUNT ? INSIDE_COUNT : n;
			int count = 0;
			for (int j = Math.max(groupStart, i - EDGE_MEDIAN_RADIUS); j < Math.min(groupEnd, i + EDGE_MEDIAN_RADIUS + 1); j++) {
				if (!Float.isNaN(raw[j])) window[count++] = raw[j];
			}
			java.util.Arrays.sort(window, 0, count);
			float median = count % 2 == 1 ? window[count / 2] : (window[count / 2 - 1] + window[count / 2]) / 2.0F;
			out[i] = Math.signum(median) * Math.max(0.0F, Math.abs(median) - DEAD_ZONE_BLOCKS);
		}
		return out;
	}

	@Unique
	private static float[] pandaprune$ranges(float[] deltas, float[] oldHeights, float[] freshHeights, float[] freshSurface) {
		float[] ranges = new float[deltas.length];
		for (int i = 0; i < deltas.length; i++) {
			float delta = deltas[i];
			if (delta == 0.0F || oldHeights[i] == Float.MAX_VALUE || freshHeights[i] == Float.MAX_VALUE) continue;
			float visible = Math.abs(delta);
			if (delta > 0.0F) {
				float waterTop = freshSurface[i] - 1.0F;
				if (waterTop > freshHeights[i]) visible = Math.max(0.0F, Math.min(delta, oldHeights[i] - waterTop));
			}
			ranges[i] = (float) Math.min(HEIGHT_RANGE_CELLS, Math.max(visible * CELLS_PER_BLOCK_OF_DELTA, Math.abs(delta) * CELLS_PER_BLOCK_UNDER_WATER));
		}
		return ranges;
	}

	@Unique
	private static float[] pandaprune$rawDeltas(ChunkAccess oldChunk, float[] oldHeights, float[] freshHeights, int seaLevel) {
		float[] raw = new float[oldHeights.length];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < raw.length; i++) {
			if (oldHeights[i] == Float.MAX_VALUE || freshHeights[i] == Float.MAX_VALUE) {
				raw[i] = Float.NaN;
				continue;
			}
			float delta = oldHeights[i] - freshHeights[i];
			int above = Mth.floor(oldHeights[i]) + 1;
			if (delta < 0 && above < seaLevel) {
				pos.set(oldChunk.getPos().getMinBlockX() + Math.min(15, BlendingDataAccessor.pandaprune$getX(i) * 4), above,
						oldChunk.getPos().getMinBlockZ() + Math.min(15, BlendingDataAccessor.pandaprune$getZ(i) * 4));
				BlockState state = oldChunk.getBlockState(pos);
				boolean wet = !state.getFluidState().isEmpty() || state.is(BlockTags.ICE);
				if (!wet) delta = Math.max(delta, Math.min(0.0F, seaLevel - 1 - freshHeights[i]));
			}
			raw[i] = delta;
		}
		return raw;
	}

	@Unique
	private static int pandaprune$heightIndex(int localX, int localZ) {
		if (localX == 4 || localZ == 4) return INSIDE_COUNT + localX + 4 - localZ;
		if (localX == 0 || localZ == 0) return 3 - localX + localZ;
		return -1;
	}

	@Inject(method = "blendDensity", at = @At("HEAD"), cancellable = true)
	private void diffDensity(int blockX, int blockY, int blockZ, float noiseValue, CallbackInfoReturnable<Float> cir) {
		if (this.pandaprune$fresh == null) return;
		int cellX = QuartPos.fromBlock(blockX);
		int cellY = blockY / 8;
		int cellZ = QuartPos.fromBlock(blockZ);
		float[] fixed = this.pandaprune$fixed(cellX, cellY, cellZ, false);
		if (fixed != null) {
			double pull = pandaprune$mismatch(fixed[0], fixed[1]) * pandaprune$terrainDiffers(this.pandaprune$fixedDelta(cellX, cellZ));
			cir.setReturnValue((float) Mth.lerp(pull, noiseValue, fixed[0]));
			return;
		}

		double totalWeight = 0.0;
		double weightedDensities = 0.0;
		double weightedMismatch = 0.0;
		double closest = Double.POSITIVE_INFINITY;
		for (Long2ObjectMap.Entry<BlendingData> entry : this.densityBlendingData.long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			BlendingDataAccessor old = (BlendingDataAccessor) entry.getValue();
			float[][] oldColumns = old.pandaprune$densities();
			float[][] freshColumns = ((BlendingDataAccessor) this.pandaprune$fresh.get(key)).pandaprune$densities();
			float[] deltas = this.pandaprune$deltas.get(key);
			int minCellX = QuartPos.fromSection(ChunkPos.getX(key));
			int minCellZ = QuartPos.fromSection(ChunkPos.getZ(key));
			int minCellY = old.pandaprune$getColumnMinY();
			int minYIndex = Math.max(0, cellY - 1 - minCellY);
			int maxYIndex = Math.min(old.pandaprune$cellCountPerColumn(), cellY + 1 - minCellY);
			for (int i = 0; i < oldColumns.length; i++) {
				float[] oldColumn = oldColumns[i];
				if (oldColumn == null) continue;
				float[] freshColumn = freshColumns[i];
				double differs = pandaprune$terrainDiffers(deltas[i]);
				int testCellX = minCellX + BlendingDataAccessor.pandaprune$getX(i);
				int testCellZ = minCellZ + BlendingDataAccessor.pandaprune$getZ(i);
				for (int yIndex = minYIndex; yIndex < maxYIndex; yIndex++) {
					double distance = Mth.length(cellX - testCellX, (cellY - (yIndex + minCellY)) * 2, cellZ - testCellZ);
					if (distance > DENSITY_RANGE_CELLS) continue;
					if (distance < closest) closest = distance;
					float oldDensity = oldColumn[yIndex] * 0.1F;
					float freshDensity = freshColumn == null ? Float.MAX_VALUE : freshColumn[yIndex] * 0.1F;
					double weight = 1.0 / (distance * distance * distance * distance);
					weightedDensities += oldDensity * weight;
					weightedMismatch += pandaprune$mismatch(oldDensity, freshDensity) * differs * weight;
					totalWeight += weight;
				}
			}
		}
		if (closest == Double.POSITIVE_INFINITY) {
			cir.setReturnValue(noiseValue);
			return;
		}
		double alpha = Mth.clamp(closest / 3.0, 0.0, 1.0);
		double pull = (1.0 - alpha) * (weightedMismatch / totalWeight);
		cir.setReturnValue((float) Mth.lerp(pull, noiseValue, weightedDensities / totalWeight));
	}

	@Unique
	private static double pandaprune$terrainDiffers(float smoothedDelta) {
		return Float.isNaN(smoothedDelta) ? 1.0 : Mth.clamp(Math.abs(smoothedDelta) / 2.0, 0.0, 1.0);
	}

	@Unique
	private static double pandaprune$mismatch(float oldDensity, float freshDensity) {
		if (freshDensity == Float.MAX_VALUE) return 1.0;
		return Mth.clamp(Math.abs(oldDensity - freshDensity) / DENSITY_MISMATCH, 0.0, 1.0);
	}

	@Inject(method = "blendBiome", at = @At("HEAD"), cancellable = true)
	private void diffBiome(int quartX, int quartY, int quartZ, CallbackInfoReturnable<Holder<Biome>> cir) {
		if (this.pandaprune$fresh == null) return;
		double closest = Double.POSITIVE_INFINITY;
		Holder<Biome> closestOld = null;
		Holder<Biome> closestFresh = null;
		for (Long2ObjectMap.Entry<BlendingData> entry : this.heightAndBiomeBlendingData.long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			BlendingData oldData = entry.getValue();
			LevelHeightAccessor area = oldData.getAreaWithOldGeneration();
			int minQuartY = QuartPos.fromBlock(area.getMinY());
			if (quartY < minQuartY || quartY > QuartPos.fromBlock(area.getMaxY())) continue;
			int quartIndex = quartY - minQuartY;
			List<List<Holder<Biome>>> oldBiomes = ((BlendingDataAccessor) oldData).pandaprune$biomes();
			List<List<Holder<Biome>>> freshBiomes = ((BlendingDataAccessor) this.pandaprune$fresh.get(key)).pandaprune$biomes();
			int minCellX = QuartPos.fromSection(ChunkPos.getX(key));
			int minCellZ = QuartPos.fromSection(ChunkPos.getZ(key));
			for (int i = 0; i < oldBiomes.size(); i++) {
				List<Holder<Biome>> column = oldBiomes.get(i);
				if (column == null) continue;
				Holder<Biome> biome = column.get(quartIndex);
				if (biome == null) continue;
				double distance = Mth.length(quartX - (minCellX + BlendingDataAccessor.pandaprune$getX(i)), quartZ - (minCellZ + BlendingDataAccessor.pandaprune$getZ(i)));
				if (distance > HEIGHT_RANGE_CELLS || distance >= closest) continue;
				closest = distance;
				closestOld = biome;
				List<Holder<Biome>> freshColumn = freshBiomes.get(i);
				closestFresh = freshColumn == null ? null : freshColumn.get(quartIndex);
			}
		}
		if (closestOld == null || closestOld.equals(closestFresh)) {
			cir.setReturnValue(null);
			return;
		}
		float shift = SHIFT_NOISE.get(quartX, 0.0, quartZ) * 12.0F;
		float alpha = Mth.clamp(((float) closest + shift) / (HEIGHT_RANGE_CELLS + 1), 0.0F, 1.0F);
		cir.setReturnValue(alpha > 0.5F ? null : closestOld);
	}

	@Unique
	private float pandaprune$fixedDelta(int cellX, int cellZ) {
		int chunkX = QuartPos.toSection(cellX);
		int chunkZ = QuartPos.toSection(cellZ);
		boolean minX = (cellX & 3) == 0;
		boolean minZ = (cellZ & 3) == 0;
		float value = this.pandaprune$fixedDeltaIn(chunkX, chunkZ, cellX, cellZ);
		if (Float.isNaN(value) && minX && minZ) value = this.pandaprune$fixedDeltaIn(chunkX - 1, chunkZ - 1, cellX, cellZ);
		if (Float.isNaN(value) && minX) value = this.pandaprune$fixedDeltaIn(chunkX - 1, chunkZ, cellX, cellZ);
		if (Float.isNaN(value) && minZ) value = this.pandaprune$fixedDeltaIn(chunkX, chunkZ - 1, cellX, cellZ);
		return value;
	}

	@Unique
	private float pandaprune$fixedDeltaIn(int chunkX, int chunkZ, int cellX, int cellZ) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		BlendingData old = this.heightAndBiomeBlendingData.get(key);
		if (old == null) return Float.NaN;
		int index = pandaprune$heightIndex(cellX - QuartPos.fromSection(chunkX), cellZ - QuartPos.fromSection(chunkZ));
		if (index < 0 || ((BlendingDataAccessor) old).pandaprune$heights()[index] == Float.MAX_VALUE) return Float.NaN;
		return this.pandaprune$deltas.get(key)[index];
	}

	@Unique
	private float[] pandaprune$fixed(int cellX, int cellY, int cellZ, boolean height) {
		int chunkX = QuartPos.toSection(cellX);
		int chunkZ = QuartPos.toSection(cellZ);
		boolean minX = (cellX & 3) == 0;
		boolean minZ = (cellZ & 3) == 0;
		float[] value = this.pandaprune$fixedIn(chunkX, chunkZ, cellX, cellY, cellZ, height);
		if (value == null && minX && minZ) value = this.pandaprune$fixedIn(chunkX - 1, chunkZ - 1, cellX, cellY, cellZ, height);
		if (value == null && minX) value = this.pandaprune$fixedIn(chunkX - 1, chunkZ, cellX, cellY, cellZ, height);
		if (value == null && minZ) value = this.pandaprune$fixedIn(chunkX, chunkZ - 1, cellX, cellY, cellZ, height);
		return value;
	}

	@Unique
	private float[] pandaprune$fixedIn(int chunkX, int chunkZ, int cellX, int cellY, int cellZ, boolean height) {
		long key = ChunkPos.pack(chunkX, chunkZ);
		BlendingData old = this.heightAndBiomeBlendingData.get(key);
		if (old == null) return null;
		int localX = cellX - QuartPos.fromSection(chunkX);
		int localZ = cellZ - QuartPos.fromSection(chunkZ);
		BlendingDataAccessor oldAccess = (BlendingDataAccessor) old;
		float oldValue = height ? oldAccess.pandaprune$getHeight(localX, cellY, localZ) : oldAccess.pandaprune$getDensity(localX, cellY, localZ);
		if (oldValue == Float.MAX_VALUE) return null;
		BlendingDataAccessor fresh = (BlendingDataAccessor) this.pandaprune$fresh.get(key);
		float freshValue = height ? fresh.pandaprune$getHeight(localX, cellY, localZ) : fresh.pandaprune$getDensity(localX, cellY, localZ);
		return new float[]{oldValue, freshValue};
	}
}
