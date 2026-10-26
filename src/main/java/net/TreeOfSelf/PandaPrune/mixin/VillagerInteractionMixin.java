package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Villager.class)
public class VillagerInteractionMixin {
	@Inject(method = "mobInteract", at = @At("HEAD"))
	private void onVillagerInteraction(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		Villager villager = (Villager) (Object) this;
		onVillagerInteracted(player, villager);
	}

	@Unique
	private void onVillagerInteracted(Player player, Villager villager) {
		LevelChunk chunk = player.level().getChunkAt(villager.blockPosition());
		PandaPrune.protect(chunk);
	}
}
