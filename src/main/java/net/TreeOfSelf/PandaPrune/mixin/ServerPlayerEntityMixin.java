package net.TreeOfSelf.PandaPrune.mixin;

import net.TreeOfSelf.PandaPrune.PandaPrune;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin {
	@Inject(method = "removeVehicle", at = @At("HEAD"))
	public void removeVehicle(CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		ServerLevel world = self.level();
		Player playerEntity = (Player) (Object) this;
		BlockPos pos = playerEntity.blockPosition();
		LevelChunk chunk = world.getChunkAt(pos);
		PandaPrune.protect(chunk);
	}
}
