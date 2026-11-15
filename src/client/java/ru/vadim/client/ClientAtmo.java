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

	public static boolean active() { return ClientZone.hub() || ClientZone.zavod(); }
	/** 0..1 — насколько темно (для пост-эффекта: виньетка, зерно). */
	public static float darkness() { return ClientZone.hub() ? 0.55f : ClientZone.zavod() ? 0.5f : 0f; }
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

	public static float[] fogColor() { return (ClientZone.zavod() ? ZAVOD_FOG : HUB_FOG).clone(); }
	public static float fogStart() { return ClientZone.zavod() ? 6f : 18f; }
	public static float fogEnd() { return ClientZone.zavod() ? 84f : 72f; }
}
