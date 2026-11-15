package ru.vadim.client.zavod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

/**
 * Этап 3г на клиенте: тряска при ударе гермодвери, газ (зелёный туман, виньетка, качание), падение,
 * сердцебиение, чернота и пробуждение в комнате (камера поднимается с пола). Время — по своим тикам клиента
 * от пакета сервера (ZavodFinal phase 2 присылает, сколько тиков газа уже прошло).
 */
public final class ZavodFinalClient {
	private static final float FALL_AT = 8f, BLACK_AT = 10f, BLACK_FULL = 11.5f, GAS_MAX = 40f;
	private static final float WAKE_LIE = 1.2f, WAKE_LEN = 4f, WAKE_FADE = 1.6f;
	private static final double DROP = 1.3;

	private static long ticks;
	private static long gasStart = -1, wakeStart = -1;
	private static int shake;
	private static boolean heart;
	private static float side = 1f;
	private static SoundInstance gasSound;

	private ZavodFinalClient() {}

	public static void onPacket(HubPayloads.ZavodFinal p) {
		Minecraft mc = Minecraft.getInstance();
		switch (p.phase()) {
			case 1 -> shake = 16;
			case 2 -> {
				long st = ticks - p.elapsed();
				if (gasStart < 0) {
					gasStart = st;
					heart = false;
					side = mc.player != null && mc.player.getRandom().nextBoolean() ? 1f : -1f;
					gasSound = ui(ModSounds.ZAVOD_GAS, 0.9f);
					mc.getSoundManager().play(gasSound);
				} else if (Math.abs(st - gasStart) > 3) gasStart = st;
			}
			case 3 -> {
				if (gasSound != null) mc.getSoundManager().stop(gasSound);
				gasSound = null;
				gasStart = -1;
				wakeStart = ticks;
			}
			default -> {}
		}
	}

	private static SoundInstance ui(SoundEvent e, float vol) {
		return new SimpleSoundInstance(e.location(), SoundSource.MASTER, vol, 1f, SoundInstance.createUnseededRandom(),
				false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) return;
		if (!mc.isPaused()) ticks++;
		if (shake > 0) shake--;
		float g = gas(0f);
		if (g >= 0 && !heart && g >= FALL_AT + 0.4f) {
			heart = true;
			mc.getSoundManager().play(ui(ModSounds.ZAVOD_HEARTBEAT, 1f));
		}
		if (g > GAS_MAX) gasStart = -1;   // сервер так и не перенёс — не держим черноту вечно
		if (wakeStart >= 0 && wake(0f) > WAKE_LEN) wakeStart = -1;
	}

	/** Секунды газа (−1 — газа нет). */
	private static float gas(float pt) { return gasStart < 0 ? -1f : (ticks - gasStart + pt) / 20f; }

	private static float wake(float pt) { return wakeStart < 0 ? -1f : (ticks - wakeStart + pt) / 20f; }

	private static float pt() { return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false); }

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** Управление, рука и интерфейс выключены: упал / лежишь / встаёшь. */
	public static boolean locked() {
		float g = gas(0f);
		return (g >= FALL_AT) || (wakeStart >= 0 && wake(0f) < WAKE_LEN - 0.3f);
	}

	/** 0..1 — насколько газ заполнил комнату (для тумана). */
	public static float green() {
		float g = gas(pt());
		return g < 0 ? 0f : smooth(g / 6f);
	}

	// ---------- камера ----------

	/** Наклоны камеры (из GameRenderer.bobHurt): качание в газу, падение на бок, подъём. */
	public static void applyPose(PoseStack pose, float partial) {
		float roll = 0f, pitch = 0f;
		if (shake > 0) {
			float s = (shake - partial) / 16f;
			roll += (float) Math.sin((ticks + partial) * 2.7) * 2.2f * s;
			pitch += (float) Math.sin((ticks + partial) * 3.9) * 1.6f * s;
		}
		float g = gas(partial);
		if (g >= 0) {
			float k = smooth(g / FALL_AT);
			roll += (float) Math.sin(g * 1.3) * 4f * k;
			pitch += (float) Math.sin(g * 0.8 + 1.0) * 2.5f * k;
			if (g >= FALL_AT) {
				float a = Mth.clamp((g - FALL_AT) / 0.7f, 0f, 1f);
				float e = 1f - (1f - a) * (1f - a);
				roll += side * 78f * e;
				pitch += 18f * e;
				float hit = g - FALL_AT - 0.7f;
				if (hit > 0 && hit < 0.5f) roll += (float) Math.sin(hit * 40) * (0.5f - hit) * 6f;
			}
		}
		float w = wake(partial);
		if (w >= 0) {
			float up = smooth((w - WAKE_LIE) / (WAKE_LEN - WAKE_LIE));
			roll += side * 78f * (1f - up) + (float) Math.sin(w * 1.1) * 2f * (1f - up);
			pitch += -12f * (1f - up);
		}
		if (roll != 0f) pose.mulPose(Axis.ZP.rotationDegrees(roll));
		if (pitch != 0f) pose.mulPose(Axis.XP.rotationDegrees(pitch));
	}

	/** На сколько опустить камеру (лежишь на полу). */
	public static double cameraDrop(float partial) {
		float g = gas(partial);
		if (g >= FALL_AT) {
			float a = Mth.clamp((g - FALL_AT) / 0.7f, 0f, 1f);
			return DROP * a * a;
		}
		float w = wake(partial);
		if (w >= 0) return DROP * (1f - smooth((w - WAKE_LIE) / (WAKE_LEN - WAKE_LIE)));
		return 0;
	}

	// ---------- экран ----------

	public static void render(GuiGraphics gr, DeltaTracker dt) {
		float p = dt.getGameTimeDeltaPartialTick(false);
		int w = gr.guiWidth(), h = gr.guiHeight();
		float g = gas(p);
		if (g >= 0) {
			float k = smooth(g / 6f);
			gr.fill(0, 0, w, h, argb(0.22f * k, 0x2E4A12));   // зелёная муть
			vignette(gr, w, h, 0.25f + 0.6f * smooth(g / FALL_AT), 0x050A02);
			float black = smooth((g - BLACK_AT) / (BLACK_FULL - BLACK_AT));
			if (black > 0) gr.fill(0, 0, w, h, argb(black, 0));
			return;
		}
		float wk = wake(p);
		if (wk >= 0) {
			vignette(gr, w, h, 0.7f * (1f - smooth(wk / WAKE_LEN)), 0);
			float black = 1f - smooth(wk / WAKE_FADE);
			if (black > 0) gr.fill(0, 0, w, h, argb(black, 0));
		}
	}

	/** Виньетка: рамки от краёв к центру с убывающей плотностью. */
	private static void vignette(GuiGraphics gr, int w, int h, float strength, int rgb) {
		if (strength <= 0.01f) return;
		int steps = 18;
		int maxX = w / 3, maxY = h / 3;
		for (int i = 0; i < steps; i++) {
			float f = 1f - (float) i / steps;
			float a = strength * f * f * 0.16f;
			int x = maxX * i / steps, y = maxY * i / steps;
			int x2 = maxX * (i + 1) / steps, y2 = maxY * (i + 1) / steps;
			int c = argb(a, rgb);
			gr.fill(0, y, w, y2, c);
			gr.fill(0, h - y2, w, h - y, c);
			gr.fill(x, y2, x2, h - y2, c);
			gr.fill(w - x2, y2, w - x, h - y2, c);
		}
	}

	private static int argb(float a, int rgb) {
		return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
	}
}
