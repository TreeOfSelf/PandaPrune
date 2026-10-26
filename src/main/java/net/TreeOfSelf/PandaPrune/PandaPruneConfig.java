package net.TreeOfSelf.PandaPrune;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PandaPruneConfig {
	private static final Path FILE = Path.of("config", "PandaPrune.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean chunkBlending = true;
	public String blendMode = "difference";
	public boolean pruneOnStartup = false;
	// 6000 ticks = 5 minutes
	public long pruneMinInhabitedTicks = 6000;
	public List<String> pruneDimensions = new ArrayList<>();
	public boolean restoreFeatures = true;

	public static PandaPruneConfig load() {
		PandaPruneConfig config = new PandaPruneConfig();
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				PandaPruneConfig read = GSON.fromJson(reader, PandaPruneConfig.class);
				if (read != null) config = read;
			} catch (Exception e) {
				PandaPrune.LOGGER.error("Could not read {}, using defaults", FILE, e);
			}
		}
		if (config.blendMode == null) config.blendMode = "difference";
		if (config.pruneDimensions == null) config.pruneDimensions = new ArrayList<>();
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			PandaPrune.LOGGER.error("Could not write {}", FILE, e);
		}
		return config;
	}

	public boolean differenceBlending() {
		return chunkBlending && !"vanilla".equalsIgnoreCase(blendMode);
	}

	public boolean recordFeatures() {
		return pruneOnStartup && restoreFeatures;
	}
}
