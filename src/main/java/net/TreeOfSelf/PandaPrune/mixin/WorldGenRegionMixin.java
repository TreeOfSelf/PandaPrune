package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.FeatureSpills;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public class WorldGenRegionMixin {
	@Inject(method = "setBlock", at = @At("RETURN"))
	private void recordSpill(BlockPos pos, BlockState state, int updateFlags, int updateLimit, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) FeatureSpills.onSetBlock((WorldGenRegion) (Object) this, pos, state);
	}
}
