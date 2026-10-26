package net.TreeOfSelf.PandaPrune;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class Pruner {
	private static final Pattern REGION_NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
	private static final int SECTOR = 4096;
	private static final int EXTERNAL_FLAG = 128;
	private static final String ATTACHMENTS = "fabric:attachments";
	private static final String OLD_CHUNKS = "prune_old_chunks";

	private Pruner() {
	}

	private static List<Path> dimensions(Path worldRoot) {
		Path dimensions = worldRoot.resolve("dimensions");
		if (!Files.isDirectory(dimensions)) return List.of();
		try (Stream<Path> walk = Files.walk(dimensions)) {
			return walk.filter(p -> p.getFileName().toString().equals("region") && Files.isDirectory(p))
					.map(Path::getParent).sorted().toList();
		} catch (IOException e) {
			PandaPrune.LOGGER.error("Prune: could not list dimensions", e);
			return List.of();
		}
	}

	private static String dimensionId(Path worldRoot, Path dim) {
		Path rel = worldRoot.resolve("dimensions").relativize(dim);
		if (rel.getNameCount() < 2) return null;
		return rel.getName(0) + ":" + rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/');
	}

	private static Path oldChunksFile(Path dir, String id) {
		return dir.resolve(id.replace(':', '/') + ".bin");
	}

	// chunks that existed before Prune was installed: their pre-install use was never tracked
	public static void recordOldChunks(Path worldRoot) {
		Path marker = worldRoot.resolve(OLD_CHUNKS);
		if (Files.isDirectory(marker)) return;
		Path tmp = worldRoot.resolve(OLD_CHUNKS + ".tmp");
		try {
			deleteTree(tmp);
			Files.createDirectories(tmp);
			for (Path dim : dimensions(worldRoot)) {
				String id = dimensionId(worldRoot, dim);
				if (id == null) continue;
				LongSet chunks = new LongOpenHashSet();
				for (Path file : regionFiles(dim.resolve("region"))) {
					try {
						Region region = Region.readHeader(file);
						for (int i = 0; i < 1024; i++) if (region.listed(i)) chunks.add(region.pos(i));
					} catch (IOException e) {
						throw new IOException("could not read " + file, e);
					}
				}
				Path out = oldChunksFile(tmp, id);
				Files.createDirectories(out.getParent());
				try (DataOutputStream data = new DataOutputStream(new java.io.BufferedOutputStream(
						new java.util.zip.GZIPOutputStream(Files.newOutputStream(out))))) {
					data.writeInt(chunks.size());
					for (long pos : chunks) data.writeLong(pos);
				}
				PandaPrune.LOGGER.info("Prune: {} has {} chunks from before Prune was installed, they are never pruned unless pruneOldChunks is on", id, chunks.size());
			}
			Files.move(tmp, marker, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			PandaPrune.LOGGER.error("Prune: could not record the chunks from before Prune was installed", e);
		}
	}

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) return;
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(p);
		}
	}

	private static LongSet loadOldChunks(Path worldRoot, String id) throws IOException {
		LongSet chunks = new LongOpenHashSet();
		Path file = oldChunksFile(worldRoot.resolve(OLD_CHUNKS), id);
		if (!Files.exists(file)) return chunks;
		try (DataInputStream data = new DataInputStream(new java.io.BufferedInputStream(
				new java.util.zip.GZIPInputStream(Files.newInputStream(file))))) {
			int n = data.readInt();
			for (int i = 0; i < n; i++) chunks.add(data.readLong());
		}
		return chunks;
	}

	public static void pruneWorld(Path worldRoot, PandaPruneConfig config) {
		if (!Files.isDirectory(worldRoot.resolve(OLD_CHUNKS))) {
			PandaPrune.LOGGER.error("Prune: no record of the chunks from before Prune was installed, not pruning");
			return;
		}
		for (Path dim : dimensions(worldRoot)) {
			String id = dimensionId(worldRoot, dim);
			if (id == null) continue;
			if (!config.pruneDimensions.isEmpty() && !config.pruneDimensions.contains(id)) continue;
			try {
				LongSet old = config.pruneOldChunks ? new LongOpenHashSet() : loadOldChunks(worldRoot, id);
				boolean pruned = pruneDimension(id, dim, config.pruneMinInhabitedTicks, old);
				// every old chunk was judged like any other, so the list is done; regenerated ones are new chunks
				if (config.pruneOldChunks && pruned) Files.deleteIfExists(oldChunksFile(worldRoot.resolve(OLD_CHUNKS), id));
			} catch (Exception e) {
				PandaPrune.LOGGER.error("Prune: {} failed, left as it was", id, e);
			}
		}
	}

	private record Scan(LongSet keep, LongSet withSpills, long total, long used, long old) {
	}

	private static boolean pruneDimension(String id, Path dim, long minTicks, LongSet old) throws IOException {
		long start = System.currentTimeMillis();
		Path regionDir = dim.resolve("region");
		Scan scan = scan(regionDir, minTicks, old);
		if (scan.total() > scan.old() && scan.used() == 0) {
			PandaPrune.LOGGER.warn("Prune: {} has {} chunks but none used, not pruning it", id, scan.total());
			return false;
		}
		AtomicLong before = new AtomicLong(), after = new AtomicLong(), records = new AtomicLong();
		for (String sub : new String[]{"region", "entities", "poi"}) {
			Path dir = dim.resolve(sub);
			if (!Files.isDirectory(dir)) continue;
			boolean chunks = sub.equals("region");
			regionFiles(dir).parallelStream().forEach(file -> {
				try {
					before.addAndGet(Files.size(file));
					after.addAndGet(rewrite(file, scan, chunks, records));
				} catch (Exception e) {
					PandaPrune.LOGGER.error("Prune: could not rewrite {}, left as it was", file, e);
				}
			});
		}
		PandaPrune.LOGGER.info("Prune: {} kept {} of {} chunks ({} used, {} from before install), {} -> {} MB, {} feature records dropped, {} s",
				id, scan.keep().size(), scan.total(), scan.used(), scan.old(), before.get() >> 20, after.get() >> 20,
				records.get(), (System.currentTimeMillis() - start) / 1000.0);
		return true;
	}

	private static List<Path> regionFiles(Path dir) throws IOException {
		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(p -> REGION_NAME.matcher(p.getFileName().toString()).matches()).toList();
		}
	}

	private static Scan scan(Path regionDir, long minTicks, LongSet old) throws IOException {
		LongSet keep = new LongOpenHashSet(), withSpills = new LongOpenHashSet();
		AtomicLong total = new AtomicLong(), used = new AtomicLong(), oldKept = new AtomicLong();
		regionFiles(regionDir).parallelStream().forEach(file -> {
			LongSet k = new LongOpenHashSet(), s = new LongOpenHashSet();
			try {
				Region region = Region.read(file);
				for (int i = 0; i < 1024; i++) {
					if (!region.exists(i)) continue;
					total.incrementAndGet();
					long pos = region.pos(i);
					if (old.contains(pos)) {
						k.add(pos);
						oldKept.incrementAndGet();
						continue;
					}
					CompoundTag tag = region.readTag(i);
					if (tag == null) {
						k.add(pos);
						continue;
					}
					if (tag.getLongOr("InhabitedTime", 0L) >= minTicks || tag.getCompoundOrEmpty(ATTACHMENTS).contains(PandaPrune.USED_ID)) {
						k.add(pos);
						used.incrementAndGet();
						if (tag.getCompoundOrEmpty(ATTACHMENTS).contains(FeatureSpills.SPILLS_ID)) s.add(pos);
					}
				}
			} catch (Exception e) {
				PandaPrune.LOGGER.error("Prune: could not read {}, keeping all of it", file, e);
				Matcher m = REGION_NAME.matcher(file.getFileName().toString());
				if (m.matches()) {
					int rx = Integer.parseInt(m.group(1)), rz = Integer.parseInt(m.group(2));
					for (int i = 0; i < 1024; i++) k.add(ChunkPos.pack(rx * 32 + (i & 31), rz * 32 + (i >> 5)));
				}
			}
			synchronized (keep) {
				keep.addAll(k);
				withSpills.addAll(s);
			}
		});
		return new Scan(keep, withSpills, total.get(), used.get(), oldKept.get());
	}

	private static long rewrite(Path file, Scan scan, boolean chunks, AtomicLong droppedRecords) throws IOException {
		Region region = Region.read(file);
		List<byte[]> payloads = new ArrayList<>();
		int[] indexes = new int[1024];
		int kept = 0;
		boolean changed = false;
		for (int i = 0; i < 1024; i++) {
			if (!region.exists(i)) continue;
			long pos = region.pos(i);
			if (!scan.keep().contains(pos)) {
				changed = true;
				region.deleteExternal(i);
				continue;
			}
			byte[] payload = region.raw(i);
			if (chunks && scan.withSpills().contains(pos)) {
				byte[] edited = dropKeptRecords(region, i, pos, scan.keep(), droppedRecords);
				if (edited != null) {
					payload = edited;
					changed = true;
				}
			}
			indexes[kept++] = i;
			payloads.add(payload);
		}
		if (!changed) return region.bytes.length;
		if (kept == 0) {
			Files.delete(file);
			return 0;
		}
		int sectors = 2;
		for (byte[] p : payloads) sectors += (p.length + SECTOR - 1) / SECTOR;
		ByteBuffer out = ByteBuffer.allocate(sectors * SECTOR);
		int sector = 2;
		for (int n = 0; n < kept; n++) {
			byte[] p = payloads.get(n);
			int count = (p.length + SECTOR - 1) / SECTOR;
			int i = indexes[n];
			out.putInt(i * 4, sector << 8 | count);
			out.putInt(SECTOR + i * 4, region.timestamp(i));
			out.put(sector * SECTOR, p);
			sector += count;
		}
		Path tmp = file.resolveSibling(file.getFileName() + ".prune");
		Files.write(tmp, out.array());
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		return out.capacity();
	}

	private static byte[] dropKeptRecords(Region region, int i, long pos, LongSet keep, AtomicLong dropped) {
		if (region.external(i)) return null;
		CompoundTag tag = region.readTag(i);
		if (tag == null) return null;
		CompoundTag attachments = tag.getCompoundOrEmpty(ATTACHMENTS);
		ListTag spills = attachments.getListOrEmpty(FeatureSpills.SPILLS_ID);
		int cx = ChunkPos.getX(pos), cz = ChunkPos.getZ(pos);
		boolean changed = false;
		for (int n = spills.size() - 1; n >= 0; n--) {
			int d = spills.getCompoundOrEmpty(n).getByteOr("d", (byte) 4);
			if (keep.contains(ChunkPos.pack(cx + d / 3 - 1, cz + d % 3 - 1))) {
				spills.remove(n);
				dropped.incrementAndGet();
				changed = true;
			}
		}
		if (!changed) return null;
		if (spills.isEmpty()) attachments.remove(FeatureSpills.SPILLS_ID);
		if (attachments.isEmpty()) tag.remove(ATTACHMENTS);
		return region.encode(i, tag);
	}

	private static final class Region {
		final Path file;
		final byte[] bytes;
		final ByteBuffer buf;
		final int rx, rz;

		private Region(Path file, byte[] bytes, int rx, int rz) {
			this.file = file;
			this.bytes = bytes;
			this.buf = ByteBuffer.wrap(bytes);
			this.rx = rx;
			this.rz = rz;
		}

		static Region read(Path file) throws IOException {
			Matcher m = REGION_NAME.matcher(file.getFileName().toString());
			if (!m.matches()) throw new IOException("not a region file: " + file);
			byte[] bytes = Files.readAllBytes(file);
			if (bytes.length < 2 * SECTOR) bytes = java.util.Arrays.copyOf(bytes, 2 * SECTOR);
			return new Region(file, bytes, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
		}

		static Region readHeader(Path file) throws IOException {
			Matcher m = REGION_NAME.matcher(file.getFileName().toString());
			if (!m.matches()) throw new IOException("not a region file: " + file);
			byte[] bytes = new byte[SECTOR];
			try (InputStream in = Files.newInputStream(file)) {
				in.readNBytes(bytes, 0, SECTOR);
			}
			return new Region(file, bytes, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
		}

		boolean listed(int i) {
			return buf.getInt(i * 4) != 0;
		}

		long pos(int i) {
			return ChunkPos.pack(rx * 32 + (i & 31), rz * 32 + (i >> 5));
		}

		int timestamp(int i) {
			return buf.getInt(SECTOR + i * 4);
		}

		private int offset(int i) {
			return (buf.getInt(i * 4) >>> 8) * SECTOR;
		}

		private int sectorCount(int i) {
			return buf.getInt(i * 4) & 0xFF;
		}

		boolean exists(int i) {
			int loc = buf.getInt(i * 4);
			if (loc == 0) return false;
			int offset = offset(i);
			return offset >= 2 * SECTOR && offset + 5 <= bytes.length;
		}

		byte[] raw(int i) {
			int offset = offset(i);
			int length = buf.getInt(offset);
			int stored = Math.min(sectorCount(i) * SECTOR, bytes.length - offset);
			int size = length > 0 && length + 4 <= stored ? length + 4 : stored;
			return java.util.Arrays.copyOfRange(bytes, offset, offset + size);
		}

		boolean external(int i) {
			return (bytes[offset(i) + 4] & EXTERNAL_FLAG) != 0;
		}

		private Path externalFile(int i) {
			ChunkPos pos = ChunkPos.unpack(pos(i));
			return file.resolveSibling("c." + pos.x() + "." + pos.z() + ".mcc");
		}

		void deleteExternal(int i) {
			if (!external(i)) return;
			try {
				Files.deleteIfExists(externalFile(i));
			} catch (IOException e) {
				PandaPrune.LOGGER.warn("Prune: could not delete {}", externalFile(i));
			}
		}

		CompoundTag readTag(int i) {
			try {
				int offset = offset(i);
				int length = buf.getInt(offset);
				int type = bytes[offset + 4];
				RegionFileVersion version = RegionFileVersion.fromId(type & ~EXTERNAL_FLAG);
				if (version == null) return null;
				InputStream raw = (type & EXTERNAL_FLAG) != 0
						? Files.newInputStream(externalFile(i))
						: new ByteArrayInputStream(bytes, offset + 5, length - 1);
				try (DataInputStream in = new DataInputStream(version.wrap(raw))) {
					return NbtIo.read(in, NbtAccounter.unlimitedHeap());
				}
			} catch (Exception e) {
				return null;
			}
		}

		byte[] encode(int i, CompoundTag tag) {
			try {
				int type = bytes[offset(i) + 4] & ~EXTERNAL_FLAG;
				RegionFileVersion version = RegionFileVersion.fromId(type);
				if (version == null) return null;
				ByteArrayOutputStream data = new ByteArrayOutputStream();
				try (DataOutputStream out = new DataOutputStream(version.wrap(data))) {
					NbtIo.write(tag, out);
				}
				byte[] compressed = data.toByteArray();
				if (compressed.length + 5 > 255 * SECTOR) return null;
				ByteBuffer payload = ByteBuffer.allocate(compressed.length + 5);
				payload.putInt(compressed.length + 1).put((byte) type).put(compressed);
				return payload.array();
			} catch (IOException e) {
				return null;
			}
		}
	}
}
