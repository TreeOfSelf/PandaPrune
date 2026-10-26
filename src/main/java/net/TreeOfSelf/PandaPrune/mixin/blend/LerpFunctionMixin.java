package net.TreeOfSelf.PandaPrune.mixin.blend;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.generator.SimpleDensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.LerpFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.SliceFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LerpFunction.class)
public abstract class LerpFunctionMixin {
	@Shadow @Final private DensityFunction alpha;
	@Shadow @Final private DensityFunction first;
	@Shadow @Final private DensityFunction second;

	@Inject(method = "compileSampler", at = @At("HEAD"), cancellable = true)
	private void additiveOffset(DensityFunction.CompileContext context, CallbackInfoReturnable<DensitySampler> cir) {
		if (PandaPrune.CONFIG.differenceBlending()
				&& pandaprune$unslice(this.alpha) == SimpleDensityFunction.BLEND_ALPHA
				&& pandaprune$unslice(this.first) == SimpleDensityFunction.BLEND_OFFSET) {
			cir.setReturnValue(DensityFunctions.add(this.second, this.first).compileSampler(context));
		}
	}

	@Unique
	private static DensityFunction pandaprune$unslice(DensityFunction function) {
		while (function instanceof SliceFunction slice) function = slice.input();
		return function;
	}
}
