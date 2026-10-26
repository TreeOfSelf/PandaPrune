package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void onEntityTick(CallbackInfo ci) {
		Entity entity = (Entity) (Object) this;
		if (isKept(entity)) {
			onNameTaggedEntityTick(entity);
		}
	}

	@Inject(method = "interact", at = @At("HEAD"))
	private void interact(Player player, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
		Entity entity = (Entity) (Object) this;
		LevelChunk chunk = entity.level().getChunkAt(entity.blockPosition());
		PandaPrune.protect(chunk);
	}

	@Unique
	private static boolean isKept(Entity entity) {
		if (entity.hasCustomName()) return true;
		if (entity instanceof Leashable leashable && leashable.isLeashed()) return true;
		if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) return true;
		if (entity instanceof Mob mob && mob.isPersistenceRequired()) return true;
		return entity.hasControllingPassenger() || entity.hasExactlyOnePlayerPassenger();
	}

	@Unique
	private void onNameTaggedEntityTick(Entity entity) {
		LevelChunk chunk = entity.level().getChunkAt(entity.blockPosition());
		PandaPrune.protect(chunk);
	}
}
