package ru.vadim.client;

import net.minecraft.client.Camera;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;

/**
 * Атмосфера мода: туман, тёплый свет, пост-эффект. Работает в хабе (ночной город) и на заводе.
 * Дальше сюда добавятся комнаты Вадима.
 */
public final class ClientAtmo {
	/** Ночной город: тёмно-синий туман, улица уходит в темноту. */
	private static final float[] HUB_FOG = {0.035f, 0.042f, 0.065f};
	/** Завод: холодный серо-зелёный туман, сырость и пыль. */
	private static final float[] ZAVOD_FOG = {0.042f, 0.050f, 0.050f};

	private ClientAtmo() {}

	public static void tick() {}

	public static boolean active() { return ClientZone.hub() || ClientZone.zavod() || ClientZone.rooms(); }
	/** 0..1 — насколько темно (для пост-эффекта: виньетка, зерно). */
	public static float darkness() { return ClientZone.hub() ? 0.55f : ClientZone.zavod() ? 0.5f : ClientZone.rooms() ? (ClientZone.studio() ? 0.35f : 0.6f) : 0f; }
	public static float chase() { return 0f; }
	/** 1 — уютная сцена (бар): цвета не приглушаем, виньетка мягче. */
	public static float cozy() { return ClientZone.hub() ? 1f : 0f; }

	public static boolean fogApplies(Camera camera) {
		if (!active()) return false;
		if (camera.getFluidInCamera() != FogType.NONE) return false;
		Entity e = camera.entity();
		return !(e instanceof LivingEntity living
				&& (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS)));
	}

	/** Газ (3г): туман зеленеет и сгущается. */
	private static final float[] GAS_FOG = {0.10f, 0.20f, 0.05f};

	public static float[] fogColor() {
		float[] c = (ClientZone.hub() ? HUB_FOG : ZAVOD_FOG).clone();
		float g = ru.vadim.client.zavod.ZavodFinalClient.green();
		for (int i = 0; i < 3; i++) c[i] += (GAS_FOG[i] - c[i]) * g;
		return c;
	}
	public static float fogStart() {
		float base = ClientZone.hub() ? 18f : ClientZone.studio() ? 8f : ClientZone.rooms() ? 2f : 6f;
		return base * (1f - ru.vadim.client.zavod.ZavodFinalClient.green());
	}
	public static float fogEnd() {
		float base = ClientZone.hub() ? 72f : ClientZone.studio() ? 44f : ClientZone.rooms() ? 30f : 84f;
		return base + (7f - base) * ru.vadim.client.zavod.ZavodFinalClient.green();
	}
}
