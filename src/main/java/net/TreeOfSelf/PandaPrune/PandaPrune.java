package net.TreeOfSelf.PandaPrune;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PandaPrune implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("PandaPrune");
	public static PandaPruneConfig CONFIG = new PandaPruneConfig();

	// own flag, not InhabitedTime (that also drives local difficulty); unknown attachments are dropped if the mod is removed
	public static final String USED_ID = "panda-prune:used";
	public static final AttachmentType<Boolean> USED = AttachmentRegistry.createPersistent(Identifier.parse(USED_ID), Codec.BOOL);

	public static void protect(ChunkAccess chunk) {
		AttachmentTarget target = (AttachmentTarget) chunk;
		if (!target.hasAttached(USED)) {
			target.setAttached(USED, true);
			chunk.markUnsaved();
		}
	}

	@Override
	public void onInitialize() {
		CONFIG = PandaPruneConfig.load();
		FeatureSpills.init();

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			Pruner.recordOldChunks(server.getWorldPath(LevelResource.ROOT));
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
