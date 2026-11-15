package ru.vadim.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import ru.vadim.client.ClientZone;
import ru.vadim.particle.ModParticles;

/** Пылинки в воздухе бара: медленно висят, видны в тёплом свете ламп. */
public final class AmbientParticles {
	private AmbientParticles() {}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused()) return;
		RandomSource r = mc.level.random;
		if (ClientZone.zavod()) {   // пыль в цеху — вокруг игрока, видна в свете ламп
			if (!ru.vadim.world.Zavod.insideShop(mc.player.getX(), mc.player.getY(), mc.player.getZ())) return;
			for (int i = 0; i < 3; i++) {
				double x = mc.player.getX() + (r.nextDouble() - 0.5) * 24, y = mc.player.getEyeY() + (r.nextDouble() - 0.5) * 6,
						z = mc.player.getZ() + (r.nextDouble() - 0.5) * 24;
				if (ru.vadim.world.Zavod.insideShop(x, y, z)) mc.level.addParticle(ModParticles.DUST, x, y, z, 0, 0, 0);
			}
			return;
		}
		if (!ClientZone.hub()) return;
		for (int i = 0; i < 2; i++) {
			double x = -9 + r.nextDouble() * 19, y = 64.3 + r.nextDouble() * 4.4, z = -10 + r.nextDouble() * 13;
			if (mc.player.distanceToSqr(x, y, z) > 14 * 14) continue;
			mc.level.addParticle(ModParticles.DUST, x, y, z, 0, 0, 0);
		}
	}
}
