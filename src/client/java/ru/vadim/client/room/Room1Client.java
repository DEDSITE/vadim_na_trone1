package ru.vadim.client.room;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import ru.vadim.VadimNaTrone;
import ru.vadim.client.ClientZone;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;
import ru.vadim.world.Room1;

/**
 * Комната 1 на клиенте: взгляд во вступлении (монитор → ракеты → монитор → прямо), тряска и сердцебиение,
 * ограничение поворота головы на стуле, сидящие смотрят телом по стулу, чернота при проигрыше,
 * Enter — пропуск правил (когда проголосуют все). Время — по своим тикам от пакета Room1State.
 */
public final class Room1Client {
	private static final FontDescription MONO = new FontDescription.Resource(VadimNaTrone.id("pt_mono"));
	private static final float YAW_LIMIT = 100f, PITCH_UP = -70f, PITCH_DOWN = 65f;

	private static int phase = Room1.P_NONE;
	private static long phaseStart;   // в тиках клиента
	private static int timeLeft = Room1.TOTAL;
	private static long timeAt;
	private static float seatYaw = Float.NaN;
	private static boolean tied, withIntro = true, voted, enterWas;
	private static long ticks;
	private static float fromYaw, fromPitch;
	private static boolean haveFrom;
	private static long nextBeat;
	private static int shake;
	private static boolean released = true;
	private static float lastYaw, lastPitch;

	private static Music music;

	private Room1Client() {}

	/** Музыка Димы, пока идёт таймер: с конца вступления, по кругу; гаснет при проигрыше/победе/выходе. */
	private static final class Music extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
		float target = 0.8f;
		int age;

		Music() {
			super(ModSounds.ROOM1_MUSIC, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
			looping = true;
			delay = 0;
			relative = true;
			attenuation = Attenuation.NONE;
			volume = 0.001f;
		}

		@Override
		public void tick() {
			age++;
			volume += (Math.max(0f, target) - volume) * 0.06f;
			if (target <= 0f && volume < 0.01f) stop();
		}
	}

	private static void musicTick(Minecraft mc) {
		boolean want = here() && phase == Room1.P_GAME && released;
		if (want) {
			if (music == null || (!mc.getSoundManager().isActive(music) && music.age > 40)) {
				music = new Music();
				mc.getSoundManager().play(music);
			}
			music.target = 0.8f;
		} else if (music != null) {
			music.target = 0f;
			if (!here()) { mc.getSoundManager().stop(music); music = null; }
			else if (music.isStopped()) music = null;
		}
	}

	public static void onState(HubPayloads.Room1State p) {
		Minecraft mc = Minecraft.getInstance();
		long st = ticks - p.phaseTick();
		if (p.phase() != phase) {
			if (p.phase() == Room1.P_INTRO) { haveFrom = false; voted = false; released = false; }
			if (p.phase() == Room1.P_GAME) nextBeat = ticks + 40;
			VadimNaTrone.LOG.info("[room1] фаза {} (тик {}), привязан {}", p.phase(), p.phaseTick(), p.tied());
			phase = p.phase();
			phaseStart = st;
		} else if (Math.abs(st - phaseStart) > 3) phaseStart = st;
		timeLeft = p.timeLeft();
		timeAt = ticks;
		seatYaw = p.seatYaw();
		tied = p.tied();
		withIntro = p.intro();
	}

	private static boolean here() { return ClientZone.rooms(); }

	/** Время вступления в тиках (= кадр мультика при 20 к/с) или −1, если вступления нет. */
	public static float introTime(float pt) {
		if (!here() || phase != Room1.P_INTRO) return -1f;
		return t(pt);
	}

	private static float t(float pt) { return (ticks - phaseStart + pt); }

	private static int left() { return (int) Math.max(0, timeLeft - (phase == Room1.P_GAME ? ticks - timeAt : 0)); }

	/** Привязан к стулу: ни шагу, ни шифта. */
	public static boolean frozen() { return here() && tied; }

	/** Вступление/проигрыш: без интерфейса и руки. */
	public static boolean cinematic() {
		if (!here()) return false;
		return phase == Room1.P_INTRO || phase == Room1.P_LOST || (phase == Room1.P_WAIT && !Float.isNaN(seatYaw))
				|| (phase == Room1.P_GAME && !released);
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) return;
		if (!mc.isPaused()) ticks++;
		musicTick(mc);
		if (!here()) { phase = Room1.P_NONE; return; }
		if (shake > 0) shake--;
		// сидящие (на дисплее-стуле) телом по стулу
		for (AbstractClientPlayer p : mc.level.players()) {
			Entity v = p.getVehicle();
			if (v instanceof Display.ItemDisplay) {
				p.setYBodyRot(v.getYRot());
				p.yBodyRotO = v.getYRot();
			}
		}
		float t = t(0);
		if (phase == Room1.P_INTRO) {
			if (!haveFrom && mc.player != null) { fromYaw = mc.player.getYRot(); fromPitch = mc.player.getXRot(); haveFrom = true; }
			if ((int) t == Room1.INTRO_ROCKETS + 6) {
				shake = 40;
				mc.getSoundManager().play(ui(1f, 1f));
			}
			// Enter — голос за пропуск правил
			boolean down = mc.screen == null && InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_ENTER);
			if (down && !enterWas && !voted && t < Room1.INTRO_ROCKETS - 20) {
				voted = true;
				ClientPlayNetworking.send(new HubPayloads.Room1Skip());
			}
			enterWas = down;
		}
		if (phase == Room1.P_GAME && !released) {
			// камера возвращается «прямо» за 20 тиков после конца вступления, потом отдаём управление
			if (t >= 20 || !withIntro) {
				released = true;
				if (mc.player != null && !Float.isNaN(seatYaw) && withIntro) { mc.player.setYRot(lastYaw); mc.player.setXRot(lastPitch); mc.player.yRotO = lastYaw; mc.player.xRotO = lastPitch; }
			}
		}
		// сердцебиение учащается к концу таймера
		if (phase == Room1.P_GAME && ticks >= nextBeat) {
			int s = left() / 20;
			if (s <= 180) {
				int period = s > 90 ? 110 : s > 30 ? 70 : 40;
				float vol = s > 90 ? 0.35f : s > 30 ? 0.55f : 0.85f;
				float pitch = s > 90 ? 0.9f : s > 30 ? 1.0f : 1.2f;
				mc.getSoundManager().play(ui(vol, pitch));
				nextBeat = ticks + period;
			} else nextBeat = ticks + 20;
		}
	}

	private static SoundInstance ui(float vol, float pitch) {
		return new SimpleSoundInstance(ModSounds.ZAVOD_HEARTBEAT.location(), SoundSource.MASTER, vol, pitch, SoundInstance.createUnseededRandom(),
				false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
	}

	// ---------- камера ----------

	public record Look(float yaw, float pitch) {}

	private static Look toward(Vec3 eye, Vec3 at) {
		Vec3 d = at.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new Look(yaw, pitch);
	}

	private static Look mix(Look a, Look b, float k) {
		k = smooth(k);
		return new Look(a.yaw + Mth.wrapDegrees(b.yaw - a.yaw) * k, Mth.lerp(k, a.pitch, b.pitch));
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** Куда смотрит камера во вступлении (null — смотрит сам игрок). */
	public static Look camera(float pt) {
		if (!here() || Float.isNaN(seatYaw)) return null;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return null;
		Vec3 eye = mc.player.getEyePosition(pt);
		double r = Math.toRadians(seatYaw);
		Vec3 fwd = new Vec3(-Math.sin(r), 0, Math.cos(r));
		Look monitor = toward(eye, Room1.MONITOR);
		Look rocket = toward(eye, eye.add(fwd.scale(0.28)).add(0, 2.4, 0));
		Look ahead = new Look(seatYaw, 8f);
		Look from = haveFrom ? new Look(fromYaw, fromPitch) : new Look(mc.player.getYRot(), mc.player.getXRot());
		float t = t(pt);
		Look out;
		if (phase == Room1.P_INTRO) {
			if (t < Room1.INTRO_ROCKETS) out = mix(from, monitor, t / 40f);
			else if (t < Room1.INTRO_MOCK) out = mix(monitor, rocket, (t - Room1.INTRO_ROCKETS) / 15f);
			else out = mix(rocket, monitor, (t - Room1.INTRO_MOCK) / 20f);
		} else if (phase == Room1.P_GAME && !released && withIntro) out = mix(monitor, ahead, t / 20f);
		else return null;
		if (shake > 0) {
			float s = (shake - pt) / 40f;
			out = new Look(out.yaw + (float) Math.sin((ticks + pt) * 2.9) * 1.6f * s, out.pitch + (float) Math.sin((ticks + pt) * 3.7) * 1.2f * s);
		}
		lastYaw = out.yaw;
		lastPitch = out.pitch;
		return out;
	}

	/** Наклон камеры (тряска при ракетах) — из GameRenderer.bobHurt. */
	public static void applyPose(PoseStack pose, float pt) {
		if (shake <= 0 || !here()) return;
		float s = (shake - pt) / 40f;
		pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin((ticks + pt) * 2.3) * 2.5f * s));
	}

	/** Голову на стуле не вывернуть: ±100° от стула, вверх/вниз ограничено. */
	public static void clamp(Entity e) {
		if (!here() || Float.isNaN(seatYaw) || !tied) return;
		float d = Mth.wrapDegrees(e.getYRot() - seatYaw);
		if (Math.abs(d) > YAW_LIMIT) {
			float y = seatYaw + Math.copySign(YAW_LIMIT, d);
			e.setYRot(y);
			e.yRotO = y;
		}
		float p = Mth.clamp(e.getXRot(), PITCH_UP, PITCH_DOWN);
		if (p != e.getXRot()) { e.setXRot(p); e.xRotO = p; }
	}

	// ---------- экран ----------

	public static void render(GuiGraphics g, DeltaTracker dt) {
		if (!here()) return;
		Minecraft mc = Minecraft.getInstance();
		float pt = dt.getGameTimeDeltaPartialTick(false);
		int w = g.guiWidth(), h = g.guiHeight();
		float t = t(pt);
		if (phase == Room1.P_INTRO || (phase == Room1.P_GAME && !released)) {
			int bar = Math.round(h * 0.09f);
			float k = phase == Room1.P_INTRO ? smooth(t / 30f) : 1f - smooth(t / 20f);
			int b = Math.round(bar * k);
			g.fill(0, 0, w, b, 0xFF000000);
			g.fill(0, h - b, w, h, 0xFF000000);
			if (phase == Room1.P_INTRO && t < Room1.INTRO_ROCKETS - 20) {
				String s = voted ? "ГОЛОС ЗА ПРОПУСК ОТДАН" : "ENTER — ПРОПУСТИТЬ ПРАВИЛА";
				Component c = Component.literal(s).withStyle(st -> st.withFont(MONO));
				g.drawString(mc.font, c, w - mc.font.width(c) - 12, h - b + Math.max(2, (b - 9) / 2), 0x88B8B0A0, false);
			}
		}
		if (phase == Room1.P_LOST) {
			float a = smooth((t - 12f) / 20f);
			if (a > 0) g.fill(0, 0, w, h, (int) (a * 255) << 24);
			if (t > 30) {
				Component c = Component.literal("ВРЕМЯ ВЫШЛО. ЗАНОВО.").withStyle(st -> st.withFont(MONO));
				int col = ((int) (Mth.clamp((t - 30) / 15f, 0, 1) * 200) << 24) | 0xD9453A;
				g.drawString(mc.font, c, (w - mc.font.width(c)) / 2, h / 2 - 4, col, false);
			}
		}
		if (phase == Room1.P_WAIT && !withIntro) {
			// перезапуск после проигрыша: чернота спадает
			float a = 1f - smooth((t - 30f) / 30f);
			if (a > 0) g.fill(0, 0, w, h, (int) (a * 255) << 24);
		}
	}
}
