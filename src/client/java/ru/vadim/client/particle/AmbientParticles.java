package ru.vadim.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import ru.vadim.client.ClientZone;
import ru.vadim.particle.ModParticles;

/** Пылинки в воздухе бара: медленно висят, видны в тёплом свете ламп. */
public final class AmbientParticles {
	private AmbientParticles() {}

	public static void tick(Minecraft mc) {
		if (!ClientZone.hub() || mc.level == null || mc.player == null || mc.isPaused()) return;
		RandomSource r = mc.level.random;
		for (int i = 0; i < 2; i++) {
			double x = -9 + r.nextDouble() * 19, y = 64.3 + r.nextDouble() * 4.4, z = -10 + r.nextDouble() * 13;
			if (mc.player.distanceToSqr(x, y, z) > 14 * 14) continue;
			mc.level.addParticle(ModParticles.DUST, x, y, z, 0, 0, 0);
		}
	}
}
