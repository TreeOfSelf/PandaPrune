package net.TreeOfSelf.PandaPrune.mixin.blend;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorInvoker {
	@Invoker("doCreateBiomes")
	void pandaprune$doCreateBiomes(Blender blender, RandomState randomState, ChunkAccess protoChunk);
}
