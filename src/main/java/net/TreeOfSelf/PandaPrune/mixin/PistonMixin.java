package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public class PistonMixin {
	@Shadow
	@Final
	private Level level;

	@Inject(method = "resolve", at = @At("RETURN"))
	private void onCalculatePush(CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			PistonStructureResolver handler = (PistonStructureResolver) (Object) this;

			List<BlockPos> movedBlocks = handler.getToPush();

			Direction pushDirection = handler.getPushDirection();

			for (BlockPos originalPos : movedBlocks) {
				BlockPos newPos = originalPos.relative(pushDirection);
				onBlockPushedFromTo(level, newPos);
			}
		}
	}

	@Unique
	private void onBlockPushedFromTo(Level world, BlockPos to) {
		LevelChunk chunk = world.getChunkAt(to);
		PandaPrune.protect(chunk);
	}
}
