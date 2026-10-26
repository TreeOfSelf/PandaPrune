package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.portal.PortalForcer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PortalForcer.class)
public class PortalForcerMixin {
	@Shadow @Final private ServerLevel level;

	@Inject(method = "createPortal", at = @At("RETURN"))
	private void onExitPortalCreated(BlockPos origin, Direction.Axis axis, CallbackInfoReturnable<Optional<BlockUtil.FoundRectangle>> cir) {
		cir.getReturnValue().ifPresent(rect -> {
			// the portal frame can straddle a chunk border
			BlockPos corner = rect.minCorner;
			for (int dx = -1; dx <= rect.axis1Size; dx++) {
				for (int dz = -1; dz <= rect.axis1Size; dz++) {
					protect(corner.offset(dx, 0, dz));
				}
			}
		});
	}

	@Unique
	private void protect(BlockPos pos) {
		LevelChunk chunk = this.level.getChunkAt(pos);
		PandaPrune.protect(chunk);
	}
}
