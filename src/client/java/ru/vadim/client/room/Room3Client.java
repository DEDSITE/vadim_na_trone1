package ru.vadim.client.room;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ru.vadim.VadimNaTrone;
import ru.vadim.client.ClientZone;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;
import ru.vadim.world.Room3;

/**
 * Комната 3 «Мини-мир» на клиенте: взгляд всех переводится на табло, когда оно опускается (старт) и уезжает (победа);
 * чернота при «заново» и при падении в пустоту; музыка мира (трек Димы) по кругу. Свет — LightTextureMixin (всё светлое).
 */
public final class Room3Client {
	/** Громкость музыки — на слух как джаз в баре (джаз −21 LUFS × 0,15 из 4 колонок, трек −16 LUFS). */
	private static final float MUSIC = 0.12f;
	private static final int LOOK = 55;
	private static int phase = -1;
	private static long ticks, phaseStart;
	private static Music music;

	private Room3Client() {}

	public static void onState(HubPayloads.Room3State p) {
		long st = ticks - p.phaseTick();
		if (p.phase() != phase) {
			VadimNaTrone.LOG.info("[room3] фаза {} (тик {})", p.phase(), p.phaseTick());
			phase = p.phase();
			phaseStart = st;
		} else if (Math.abs(st - phaseStart) > 3) phaseStart = st;
	}

	private static float t(float pt) { return ticks - phaseStart + pt; }

	public static void tick(Minecraft mc) {
		if (mc.level == null) { stopMusic(); return; }
		if (!mc.isPaused()) ticks++;
		if (!ClientZone.room3() || mc.player == null) { phase = -1; stopMusic(); return; }
		// музыка мира
		if (music == null || music.isStopped()) {
			music = new Music();
			mc.getSoundManager().play(music);
		}
		music.target = phase == Room3.P_DONE ? 0f : phase == Room3.P_WON ? (t(0) < Room3.COLLAPSE ? MUSIC : Math.max(0f, MUSIC * (1f - (t(0) - Room3.COLLAPSE) / 60f))) : MUSIC;
		// взгляд на табло: опускается (старт) / уезжает (победа)
		float tt = t(0);
		if ((phase == Room3.P_GAME || phase == Room3.P_WON) && tt >= 0 && tt < LOOK) {
			float k = Mth.clamp(tt / 50f, 0f, 1f);
			k = k * k * (3f - 2f * k);
			double by = phase == Room3.P_GAME ? Mth.lerp(k, Room3.BOARD_UP, Room3.BOARD_DOWN) : Mth.lerp(k, Room3.BOARD_DOWN, Room3.BOARD_UP);
			by = Math.min(by, Room3.SKY - 2.0);
			Vec3 eye = mc.player.getEyePosition();
			double dx = Room3.BOARD_X - eye.x, dy = by - 0.6 - eye.y, dz = Room3.BOARD_Z - eye.z;
			float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			float a = tt < 8 ? 0.15f + tt * 0.03f : 0.4f;
			float ny = mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * a;
			float np = mc.player.getXRot() + (pitch - mc.player.getXRot()) * a;
			mc.player.setYRot(ny);
			mc.player.setXRot(Mth.clamp(np, -90f, 90f));
			mc.player.setYHeadRot(ny);
		}
	}

	private static void stopMusic() {
		if (music != null) {
			Minecraft.getInstance().getSoundManager().stop(music);
			music = null;
		}
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		if (!ClientZone.room3() || phase < 0) return;
		float pt = dt.getGameTimeDeltaPartialTick(false);
		float tt = t(pt);
		float black = 0f;
		if (phase == Room3.P_RESET) black = tt < 60 ? smooth(tt / 22f) : 1f - smooth((tt - 60) / 30f);
		else if (phase == Room3.P_WON && tt >= Room3.COLLAPSE + 50) black = smooth((tt - Room3.COLLAPSE - 50) / (Room3.WIN_BLACK - Room3.COLLAPSE - 50f));
		if (black > 0) g.fill(0, 0, g.guiWidth(), g.guiHeight(), ((int) (black * 255) << 24));
	}

	private static final class Music extends AbstractTickableSoundInstance {
		float target = MUSIC;

		Music() {
			super(ModSounds.ROOM3_MUSIC, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			volume = 0.001f;
			relative = true;
			attenuation = Attenuation.NONE;
		}

		@Override
		public void tick() {
			if (!ClientZone.room3()) { stop(); return; }
			volume += (Math.max(0.001f, target) - volume) * 0.05f;
		}

		@Override public boolean canStartSilent() { return true; }
	}
}
