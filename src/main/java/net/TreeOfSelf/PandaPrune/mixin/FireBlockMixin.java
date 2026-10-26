package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public class FireBlockMixin {
	@Inject(
			method = "tick",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/FireBlock;getStateWithAge(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/block/state/BlockState;",
					shift = At.Shift.AFTER
			)
	)
	private void onFireActuallySpread(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci, @Local BlockPos.MutableBlockPos testPos) {
		if (!level.isClientSide()) {
			onFireSpreadTo(level, testPos);
		}
	}

	@Unique
	private void onFireSpreadTo(Level world, BlockPos pos) {
		LevelChunk chunk = world.getChunkAt(pos);
		PandaPrune.protect(chunk);
	}
}
