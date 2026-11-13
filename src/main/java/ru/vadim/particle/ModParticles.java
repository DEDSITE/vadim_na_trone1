package ru.vadim.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import ru.vadim.VadimNaTrone;

public final class ModParticles {
	/** Пылинки, висящие в воздухе (видно в лучах ламп). */
	public static final SimpleParticleType DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, VadimNaTrone.id("dust"), FabricParticleTypes.simple(true));

	private ModParticles() {}

	public static void init() {}
}
