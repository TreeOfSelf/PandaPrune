package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ExplosionImplMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(
			method = "explode",
			at = @At(
					value = "INVOKE_ASSIGN",
					target = "Lnet/minecraft/world/level/ServerExplosion;calculateExplodedPositions()Ljava/util/List;"
			),
			locals = LocalCapture.CAPTURE_FAILHARD
	)
	private void onGetBlocksToDestroy(CallbackInfoReturnable<Integer> cir, List<BlockPos> toBlow) {
		for (BlockPos pos : toBlow) {
			onBlockDestroyedByExplosion(level, pos);
		}
	}

	@Unique
	private void onBlockDestroyedByExplosion(ServerLevel world, BlockPos pos) {
		LevelChunk chunk = world.getChunkAt(pos);
		PandaPrune.protect(chunk);
	}
}
