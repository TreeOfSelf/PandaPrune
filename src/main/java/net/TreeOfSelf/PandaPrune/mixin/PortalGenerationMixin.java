package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PortalShape.class)
public class PortalGenerationMixin {
	@Shadow @Final private BlockPos bottomLeft;
	@Shadow @Final private int width;
	@Shadow @Final private int height;
	@Shadow @Final private Direction rightDir;

	@Inject(method = "createPortalBlocks", at = @At("TAIL"))
	private void onPortalCreated(LevelAccessor world, CallbackInfo ci) {
		for (int w = 0; w < width; w++) {
			for (int h = 0; h < height; h++) {
				BlockPos pos = bottomLeft
						.relative(rightDir, w)
						.relative(Direction.UP, h);
				onPortalBlockGenerated(world, pos.immutable());
			}
		}
	}

	@Unique
	private void onPortalBlockGenerated(LevelAccessor world, BlockPos pos) {
		ChunkAccess chunk = world.getChunkSource().getChunk(
				SectionPos.blockToSectionCoord(pos.getX()),
				SectionPos.blockToSectionCoord(pos.getZ()),
				ChunkStatus.FULL,
				true
		);
		if (chunk != null) PandaPrune.protect(chunk);
	}
}
