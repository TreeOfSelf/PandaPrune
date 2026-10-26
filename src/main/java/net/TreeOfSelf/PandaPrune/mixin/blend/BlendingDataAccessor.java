package net.TreeOfSelf.PandaPrune.mixin.blend;

import net.minecraft.core.CompositeDirection.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Set;

@Mixin(BlendingData.class)
public interface BlendingDataAccessor {
	@Accessor("heights")
	float[] pandaprune$heights();

	@Accessor("densities")
	float[][] pandaprune$densities();

	@Accessor("biomes")
	List<List<Holder<Biome>>> pandaprune$biomes();

	@Invoker("calculateData")
	void pandaprune$calculateData(ChunkAccess chunk, Set<Direction8> newSides);

	@Invoker("getHeight")
	float pandaprune$getHeight(int cellX, int cellY, int cellZ);

	@Invoker("getDensity")
	float pandaprune$getDensity(int cellX, int cellY, int cellZ);

	@Invoker("getColumnMinY")
	int pandaprune$getColumnMinY();

	@Invoker("cellCountPerColumn")
	int pandaprune$cellCountPerColumn();

	@Invoker("getX")
	static int pandaprune$getX(int index) {
		throw new AssertionError();
	}

	@Invoker("getZ")
	static int pandaprune$getZ(int index) {
		throw new AssertionError();
	}
}
