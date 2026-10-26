package net.TreeOfSelf.PandaPrune.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.TreeOfSelf.PandaPrune.FeatureSpills;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// ores skip WorldGenRegion.setBlock; only real ores are recorded
@Mixin(OreFeature.class)
public class OreFeatureMixin {
	@WrapOperation(method = "doPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"))
	private BlockState recordSpill(LevelChunkSection section, int x, int y, int z, BlockState state, boolean checkThreading,
								   Operation<BlockState> original, @Local(argsOnly = true) WorldGenLevel level, @Local BlockPos.MutableBlockPos orePos) {
		BlockState old = original.call(section, x, y, z, state, checkThreading);
		if (level instanceof WorldGenRegion region) {
			if (state.is(ConventionalBlockTags.ORES)) FeatureSpills.onSetBlock(region, orePos, state);
			else FeatureSpills.forget(region, orePos);
		}
		return old;
	}
}
