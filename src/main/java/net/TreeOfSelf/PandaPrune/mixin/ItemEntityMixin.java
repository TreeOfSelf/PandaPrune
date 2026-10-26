package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMixin {
	@Unique
	private boolean wasOnGround = false;

	@Inject(method = "tick", at = @At("TAIL"))
	private void onItemTick(CallbackInfo ci) {
		ItemEntity item = (ItemEntity) (Object) this;
		boolean onGround = item.onGround();
		if (onGround && !wasOnGround) {
			LevelChunk chunk = item.level().getChunkAt(item.blockPosition());
			PandaPrune.protect(chunk);
		}
		wasOnGround = onGround;
	}
}
