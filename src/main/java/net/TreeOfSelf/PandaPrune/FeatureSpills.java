package net.TreeOfSelf.PandaPrune;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

public final class FeatureSpills {
	// entries: palette index << 20 | (y + 2048) << 8 | x << 4 | z
	public record Spill(byte dir, List<BlockState> palette, int[] entries, List<CompoundTag> blockEntities) {
		public static final Codec<Spill> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BYTE.fieldOf("d").forGetter(Spill::dir),
				BlockState.CODEC.listOf().fieldOf("p").forGetter(Spill::palette),
				Codec.INT_STREAM.<int[]>xmap(IntStream::toArray, Arrays::stream).fieldOf("e").forGetter(Spill::entries),
				CompoundTag.CODEC.listOf().optionalFieldOf("b", List.of()).forGetter(Spill::blockEntities)
		).apply(i, Spill::new));
	}

	public static final String SPILLS_ID = "panda-prune:spills";
	public static final AttachmentType<List<Spill>> SPILLS = AttachmentRegistry.createPersistent(
			Identifier.parse(SPILLS_ID), Spill.CODEC.listOf());
	public static final AttachmentType<Integer> RECEIVED = AttachmentRegistry.createPersistent(
			Identifier.parse("panda-prune:received"), Codec.INT);

	public static final AtomicLong restoredChunks = new AtomicLong();
	public static final AtomicLong restoredBlocks = new AtomicLong();

	private static final class Recorder {
		final WorldGenRegion region;
		final ChunkPos center;
		@SuppressWarnings("unchecked")
		final Long2ObjectLinkedOpenHashMap<BlockState>[] byDir = new Long2ObjectLinkedOpenHashMap[9];

		Recorder(WorldGenRegion region, ChunkPos center) {
			this.region = region;
			this.center = center;
		}
	}

	private static final ThreadLocal<Recorder> RECORDER = new ThreadLocal<>();

	private FeatureSpills() {
	}

	public static void init() {
	}

	// index 0..8 for an offset of -1..1, 4 = the chunk itself
	private static int dir(int dx, int dz) {
		return (dx + 1) * 3 + (dz + 1);
	}

	private static AttachmentTarget target(ChunkAccess chunk) {
		return (AttachmentTarget) chunk;
	}

	public static void begin(WorldGenRegion region, ChunkAccess chunk) {
		if (!PandaPrune.CONFIG.recordFeatures()) return;
		ChunkPos pos = chunk.getPos();
		Integer received = target(chunk).getAttached(RECEIVED);
		int got = received == null ? 0 : received;
		int restored = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				if ((got & (1 << dir(dx, dz))) != 0) continue;
				ChunkAccess neighbour = region.getChunk(pos.x() + dx, pos.z() + dz);
				List<Spill> spills = target(neighbour).getAttached(SPILLS);
				if (spills == null) continue;
				byte toUs = (byte) dir(-dx, -dz);
				for (Spill spill : spills) {
					if (spill.dir() == toUs) restored += apply(region, chunk, spill);
				}
			}
		}
		if (received != null) target(chunk).removeAttached(RECEIVED);
		if (restored > 0) {
			restoredChunks.incrementAndGet();
			restoredBlocks.addAndGet(restored);
		}
		RECORDER.set(new Recorder(region, pos));
	}

	private static int apply(WorldGenRegion region, ChunkAccess chunk, Spill spill) {
		int minX = chunk.getPos().getMinBlockX();
		int minZ = chunk.getPos().getMinBlockZ();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int entry : spill.entries()) {
			int index = entry >>> 20;
			if (index >= spill.palette().size()) continue;
			pos.set(minX + ((entry >> 4) & 15), ((entry >> 8) & 0xFFF) - 2048, minZ + (entry & 15));
			region.setBlock(pos, spill.palette().get(index), 2, 512);
		}
		for (CompoundTag tag : spill.blockEntities()) {
			chunk.setBlockEntityNbt(tag.copy());
		}
		return spill.entries().length;
	}

	public static void onSetBlock(WorldGenRegion region, BlockPos pos, BlockState state) {
		record(region, pos, state);
	}

	public static void forget(WorldGenRegion region, BlockPos pos) {
		record(region, pos, null);
	}

	private static void record(WorldGenRegion region, BlockPos pos, BlockState state) {
		Recorder recorder = RECORDER.get();
		if (recorder == null || recorder.region != region) return;
		int dx = (pos.getX() >> 4) - recorder.center.x();
		int dz = (pos.getZ() >> 4) - recorder.center.z();
		if (dx == 0 && dz == 0) return;
		if (dx < -1 || dx > 1 || dz < -1 || dz > 1) return;
		int d = dir(dx, dz);
		Long2ObjectLinkedOpenHashMap<BlockState> map = recorder.byDir[d];
		if (state == null) {
			if (map != null) map.remove(pos.asLong());
			return;
		}
		if (map == null) map = recorder.byDir[d] = new Long2ObjectLinkedOpenHashMap<>();
		map.put(pos.asLong(), state);
	}

	public static void end(WorldGenRegion region, ChunkAccess chunk) {
		Recorder recorder = RECORDER.get();
		if (recorder == null || recorder.region != region) return;
		RECORDER.remove();
		ChunkPos center = recorder.center;
		List<Spill> spills = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int d = 0; d < 9; d++) {
			Long2ObjectLinkedOpenHashMap<BlockState> map = recorder.byDir[d];
			if (map == null || map.isEmpty()) continue;
			int dx = d / 3 - 1, dz = d % 3 - 1;
			ChunkAccess neighbour = region.getChunk(center.x() + dx, center.z() + dz);
			Object2IntOpenHashMap<BlockState> paletteIndex = new Object2IntOpenHashMap<>();
			List<BlockState> palette = new ArrayList<>();
			List<CompoundTag> blockEntities = new ArrayList<>();
			int[] entries = new int[map.size()];
			int n = 0;
			for (var e : map.long2ObjectEntrySet()) {
				BlockState state = e.getValue();
				int index = paletteIndex.computeIfAbsent(state, s -> {
					palette.add((BlockState) s);
					return palette.size() - 1;
				});
				if (index >= 4096) continue;
				pos.set(e.getLongKey());
				if (pos.getY() < -2048 || pos.getY() > 2047) continue;
				entries[n++] = index << 20 | (pos.getY() + 2048) << 8 | (pos.getX() & 15) << 4 | (pos.getZ() & 15);
				if (state.hasBlockEntity()) {
					CompoundTag tag = neighbour.getBlockEntityNbtForSaving(pos, region.registryAccess());
					if (tag != null) blockEntities.add(tag);
				}
			}
			spills.add(new Spill((byte) d, List.copyOf(palette), Arrays.copyOf(entries, n), List.copyOf(blockEntities)));
			if (neighbour.getPersistedStatus().isBefore(ChunkStatus.FEATURES)) {
				int bit = 1 << dir(-dx, -dz);
				Integer old = target(neighbour).getAttached(RECEIVED);
				target(neighbour).setAttached(RECEIVED, (old == null ? 0 : old) | bit);
			}
		}
		if (spills.isEmpty()) target(chunk).removeAttached(SPILLS);
		else target(chunk).setAttached(SPILLS, spills);
	}
}
