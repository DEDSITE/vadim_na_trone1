package ru.vadim.client.room;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import ru.vadim.VadimNaTrone;
import ru.vadim.client.ClientZone;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;
import ru.vadim.world.Room2;

/**
 * Комната 2 «Студия» на клиенте: вспышка при ударе током (тряску даёт ZavodFinal phase 1), сердцебиение без остановки
 * на последней жизни, чернота при смерти, в кабинке во время раунда встать нельзя (NewsInputMixin).
 */
public final class Room2Client {
	private static int phase = Room2.P_NONE, lives = 3, shocks = -1;
	private static boolean locked;
	private static long ticks, phaseStart, flashAt = -100, nextBeat, unblackAt = -100;

	private Room2Client() {}

	public static void onState(HubPayloads.Room2State p) {
		long st = ticks - p.phaseTick();
		if (p.phase() != phase) {
			VadimNaTrone.LOG.info("[room2] фаза {} (тик {}), жизней {}", p.phase(), p.phaseTick(), p.lives());
			if (phase == Room2.P_LOST) unblackAt = ticks;
			phase = p.phase();
			phaseStart = st;
		} else if (Math.abs(st - phaseStart) > 3) phaseStart = st;
		if (shocks >= 0 && p.shocks() > shocks) flashAt = ticks;
		shocks = p.shocks();
		lives = p.lives();
		locked = p.locked();
	}

	private static boolean here() {
		Minecraft mc = Minecraft.getInstance();
		return ClientZone.rooms() && mc.player != null && Room2.inStudio(mc.player.getX(), mc.player.getZ());
	}

	/** Сидишь в кабинке, раунд идёт — ни шагу, ни шифта. */
	public static boolean locked() { return here() && locked; }

	public static void tick(Minecraft mc) {
		if (mc.level == null) return;
		if (!mc.isPaused()) ticks++;
		if (!here()) { phase = Room2.P_NONE; locked = false; shocks = -1; return; }
		boolean playing = phase == Room2.P_TITLE || phase == Room2.P_ROUND || phase == Room2.P_BREAK;
		if (playing && lives == 1 && ticks >= nextBeat) {
			mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.ZAVOD_HEARTBEAT.location(), SoundSource.MASTER, 0.75f, 1.1f,
					SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
			nextBeat = ticks + 34;
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		if (!here()) return;
		float pt = dt.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth(), h = g.guiHeight();
		// удар током: бело-голубая вспышка, гаснет за 12 тиков
		float f = (ticks - flashAt + pt) / 12f;
		if (f >= 0 && f < 1) {
			float a = (1f - f) * (1f - f) * 0.75f;
			g.fill(0, 0, w, h, argb(a, (ticks / 2) % 2 == 0 ? 0xDFF6FF : 0x7FDCFF));
		}
		// смерть: чернота
		if (phase == Room2.P_LOST) {
			float t = (ticks - phaseStart + pt);
			float black = smooth((t - 6) / 24f);
			if (black > 0) g.fill(0, 0, w, h, argb(black, 0));
		} else {
			// после смерти уже стоим у кабинок — чернота спадает
			float u = (ticks - unblackAt + pt) / 30f;
			if (u >= 0 && u < 1) g.fill(0, 0, w, h, argb(1f - smooth(u), 0));
		}
	}

	private static int argb(float a, int rgb) {
		return ((int) (Mth.clamp(a, 0f, 1f) * 255) << 24) | (rgb & 0xFFFFFF);
	}
}
