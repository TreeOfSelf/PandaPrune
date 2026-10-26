package net.TreeOfSelf.PandaPrune;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaPrune implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("PandaPrune");
	public static PandaPruneConfig CONFIG = new PandaPruneConfig();

	// InhabitedTime alone does not mark the chunk unsaved, so it would never reach disk
	public static void protect(ChunkAccess chunk) {
		if (chunk.getInhabitedTime() < CONFIG.pruneMinInhabitedTicks) {
			chunk.setInhabitedTime(CONFIG.pruneMinInhabitedTicks);
			chunk.markUnsaved();
		}
	}

	@Override
	public void onInitialize() {
		CONFIG = PandaPruneConfig.load();
		FeatureSpills.init();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			if (CONFIG.pruneOnStartup) Pruner.pruneWorld(server.getWorldPath(LevelResource.ROOT), CONFIG);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			long fresh = FreshTerrain.generated.get();
			if (fresh > 0) {
				LOGGER.info("Blending: generated fresh terrain for {} old chunks, {} ms each on average (biomes {}, noise {}, surface {}, carvers {}, data {})",
						fresh, String.format("%.2f", FreshTerrain.generateNanos.get() / 1e6 / fresh),
						String.format("%.2f", FreshTerrain.phaseNanos[0].get() / 1e6 / fresh), String.format("%.2f", FreshTerrain.phaseNanos[1].get() / 1e6 / fresh),
						String.format("%.2f", FreshTerrain.phaseNanos[2].get() / 1e6 / fresh), String.format("%.2f", FreshTerrain.phaseNanos[3].get() / 1e6 / fresh),
						String.format("%.2f", FreshTerrain.phaseNanos[4].get() / 1e6 / fresh));
			}
			FreshTerrain.clear();
			if (FeatureSpills.restoredChunks.get() > 0) {
				LOGGER.info("Restored {} feature blocks from neighbours into {} regenerated chunks",
						FeatureSpills.restoredBlocks.get(), FeatureSpills.restoredChunks.get());
			}
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			LevelChunk chunk = world.getChunkAt(hitResult.getBlockPos());
			protect(chunk);
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
			LevelChunk chunk = world.getChunkAt(pos);
			protect(chunk);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LevelChunk chunk = handler.player.level().getChunkAt(handler.player.blockPosition());
			protect(chunk);
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			LevelChunk chunk = entity.level().getChunkAt(entity.blockPosition());
			protect(chunk);
		});
	}
}
