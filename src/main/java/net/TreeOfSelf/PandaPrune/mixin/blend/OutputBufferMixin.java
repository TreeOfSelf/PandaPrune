package net.TreeOfSelf.PandaPrune.mixin.blend;

import net.TreeOfSelf.PandaPrune.DiffBlender;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Blender.OutputBuffer.class)
public abstract class OutputBufferMixin {
	@Shadow @Final private Blender blender;

	@Shadow
	protected abstract int getIndex(int blockX, int blockZ);

	@Inject(method = "getAlpha", at = @At("HEAD"), cancellable = true)
	private void diffAlpha(int blockX, int blockZ, CallbackInfoReturnable<Float> cir) {
		if (((DiffBlender) this.blender).pandaprune$isDiff()) cir.setReturnValue(1.0F);
	}

	@Inject(method = "getOffset", at = @At("HEAD"), cancellable = true)
	private void diffOffset(int blockX, int blockZ, CallbackInfoReturnable<Float> cir) {
		DiffBlender diff = (DiffBlender) this.blender;
		if (diff.pandaprune$isDiff() && this.getIndex(blockX, blockZ) == -1) cir.setReturnValue(diff.pandaprune$offset(blockX, blockZ));
	}
}
