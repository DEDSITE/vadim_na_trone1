package ru.vadim.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import ru.vadim.client.ClientZone;
import ru.vadim.sound.ModSounds;
import ru.vadim.world.Zavod;

/**
 * Фон завода (по кругу): во дворе — ветер и далёкий город (не позиционно), гул цеха из-за фасада
 * и гудение ламп вывески (позиционно).
 * В цехе двор затихает за пару секунд (внутренние звуки — этап 3в).
 */
public final class ZavodAmbience {
	private static final float WIND = 0.7f, CITY = 0.45f;
	private static Bed wind, city;
	/** Позиционные петли: гул цеха из-за фасада и гудение ламп вывески. */
	private static Spot factory, buzz;
	/** В цеху: гул трансформаторов и вентиляция (фон), гул серверов от стоек. */
	private static Bed hum, vent;
	private static Spot servers, monitors;
	/** 0..1 — насколько слышен двор (1 — на улице, 0 — в цехе). */
	private static float outdoor = 0f;

	private ZavodAmbience() {}

	public static void tick(Minecraft mc) {
		if (ClientZone.rooms() && mc.player != null) {
			// комнаты Вадима: тот же гул и вентиляция, что в цеху, потише; двор и стойки молчат
			for (var b : new java.util.ArrayList<>(java.util.Arrays.asList(wind, city, factory, buzz, servers, monitors)))
				if (b != null) mc.getSoundManager().stop(b);
			wind = city = null;
			factory = buzz = servers = monitors = null;
			hum = ensure(mc, hum, ModSounds.ZAVOD_FACTORY);
			vent = ensure(mc, vent, ModSounds.ZAVOD_VENT);
			hum.target = 0.22f;
			vent.target = 0.26f;
			return;
		}
		if (!ClientZone.zavod() || mc.player == null) {
			stopAll();
			outdoor = 0f;
			return;
		}
		boolean inside = Zavod.insideShop(mc.player.getX(), mc.player.getY(), mc.player.getZ());
		outdoor += ((inside ? 0f : 1f) - outdoor) * 0.03f;
		wind = ensure(mc, wind, ModSounds.ZAVOD_WIND);
		city = ensure(mc, city, ModSounds.ZAVOD_CITY);
		wind.target = WIND * outdoor;
		city.target = CITY * outdoor;
		factory = ensureSpot(mc, factory, ModSounds.ZAVOD_FACTORY_OUT, 0.5, 70, Zavod.SHOP_Z1 + 1.5, 0.8f);
		buzz = ensureSpot(mc, buzz, ModSounds.ZAVOD_LAMP_BUZZ, 0.5, 78.5, Zavod.SHOP_Z1 + 1.5, 0.6f);
		factory.target = factory.loud * outdoor;
		buzz.target = buzz.loud * outdoor;
		hum = ensure(mc, hum, ModSounds.ZAVOD_FACTORY);
		vent = ensure(mc, vent, ModSounds.ZAVOD_VENT);
		servers = ensureSpot(mc, servers, ModSounds.ZAVOD_SERVERS, ru.vadim.world.ZavodInside.SERVERS.x, ru.vadim.world.ZavodInside.SERVERS.y,
				ru.vadim.world.ZavodInside.SERVERS.z, 0.5f);
		hum.target = 0.32f * (1f - outdoor);
		vent.target = 0.32f * (1f - outdoor);
		servers.target = servers.loud * (1f - outdoor);
		// белый шум мониторов у стены с экранами — еле слышно
		monitors = ensureSpot(mc, monitors, ModSounds.ZAVOD_MONITORS, -16.4, 65.8, -57.5, 0.3f);
		monitors.target = monitors.loud * (1f - outdoor);
	}

	private static Spot ensureSpot(Minecraft mc, Spot b, SoundEvent ev, double x, double y, double z, float loud) {
		if (b != null && (mc.getSoundManager().isActive(b) || b.age < 40)) return b;
		Spot n = new Spot(ev, x, y, z, loud);
		mc.getSoundManager().play(n);
		return n;
	}

	private static Bed ensure(Minecraft mc, Bed b, SoundEvent ev) {
		if (b != null && mc.getSoundManager().isActive(b)) return b;
		if (b != null && b.age < 40) return b;   // звук ещё не успел стартовать
		Bed n = new Bed(ev);
		mc.getSoundManager().play(n);
		return n;
	}

	public static void stopAll() {
		Minecraft mc = Minecraft.getInstance();
		if (wind != null) mc.getSoundManager().stop(wind);
		if (city != null) mc.getSoundManager().stop(city);
		if (factory != null) mc.getSoundManager().stop(factory);
		if (buzz != null) mc.getSoundManager().stop(buzz);
		if (hum != null) mc.getSoundManager().stop(hum);
		if (vent != null) mc.getSoundManager().stop(vent);
		if (servers != null) mc.getSoundManager().stop(servers);
		if (monitors != null) mc.getSoundManager().stop(monitors);
		wind = city = hum = vent = null;
		factory = buzz = servers = monitors = null;
	}

	private static class Bed extends AbstractTickableSoundInstance {
		float target = 0f;
		int age = 0;

		Bed(SoundEvent ev) {
			super(ev, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			relative = true;
			attenuation = Attenuation.NONE;
			volume = 0.001f;
		}

		@Override
		public void tick() {
			age++;
			// плавно: ~2 с на вход
			volume += (Math.max(0.001f, target) - volume) * 0.05f;
		}
	}

	/** Звук на месте (дальность — attenuation_distance в sounds.json). */
	private static class Spot extends AbstractTickableSoundInstance {
		float target = 0f;
		final float loud;
		int age = 0;

		Spot(SoundEvent ev, double x, double y, double z, float loud) {
			super(ev, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.loud = loud;
			looping = true;
			delay = 0;
			attenuation = Attenuation.LINEAR;
			volume = 0.001f;
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public void tick() {
			age++;
			volume += (Math.max(0.001f, target) - volume) * 0.05f;
		}
	}
}
