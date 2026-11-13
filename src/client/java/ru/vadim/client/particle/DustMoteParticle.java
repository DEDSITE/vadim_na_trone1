package ru.vadim.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Пылинка: еле заметно дрейфует в воздухе. Особенно видна в луче фонаря. */
public class DustMoteParticle extends SingleQuadParticle {
	protected DustMoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
		super(level, x, y, z, sprites.get(level.getRandom()));
		this.lifetime = 100 + random.nextInt(100);
		this.quadSize = 0.012f + random.nextFloat() * 0.012f;
		this.gravity = 0.0f;
		this.hasPhysics = false;
		this.friction = 0.98f;
		this.xd = (random.nextFloat() - 0.5f) * 0.006;
		this.yd = (random.nextFloat() - 0.5f) * 0.004;
		this.zd = (random.nextFloat() - 0.5f) * 0.006;
		this.alpha = 0.0f;
	}

	@Override
	public void tick() {
		super.tick();
		xd += (random.nextFloat() - 0.5f) * 0.0008;
		yd += (random.nextFloat() - 0.5f) * 0.0006;
		zd += (random.nextFloat() - 0.5f) * 0.0008;
		int left = lifetime - age;
		alpha = Math.min(1f, Math.min(age / 25f, left / 25f)) * 0.7f;
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
		                               double xSpeed, double ySpeed, double zSpeed, net.minecraft.util.RandomSource rnd) {
			return new DustMoteParticle(level, x, y, z, sprites);
		}
	}
}
