package ru.vadim.client.news;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import ru.vadim.VadimNaTrone;
import ru.vadim.net.HubPayloads;
import ru.vadim.sound.ModSounds;

/**
 * Этап 2 — катсцена «Новости по телеку» (клиент). Всё считается от тика старта, который прислал сервер,
 * поэтому у всех игроков картинка идёт синхронно:
 *  0–3 с    камера плавно уезжает из глаз игрока к телеку над стойкой, гаснет свет, затихает джаз;
 *  2.5 с    Тимофей щёлкает пультом, 2.6 с — телек включается, пошёл ролик (37 с, звук — из телека);
 *  3–36 с   медленный наезд на экран;
 *  36–39.2  камера влетает в экран (в ролике в это время уже помехи), 38.8–39.6 — в черноту;
 *  40 с     сервер переносит всех ко входу в заброшку, после загрузки — выход из черноты.
 * Enter — голос за пропуск (сработает, когда проголосуют все).
 */
public final class ClientNews {
	public static final float VIDEO_AT = 2.6f, VIDEO_LEN = 37f, STATIC_AT = 37.6f, PUSH_AT = 36f, BLACK_AT = 38.8f, END_AT = 40f;
	/** Экран телека: центр и куда смотрит камера (ТВ в (0,66,−10), экран на южной стороне блока). */
	private static final Vec3 SCREEN = new Vec3(0.5, 66.75, -9.84);
	private static final Vec3 P1 = new Vec3(0.5, 66.35, -4.4);     // конец пролёта: видно Тимофея и телек
	private static final Vec3 P2 = new Vec3(0.5, 66.72, -7.1);     // медленный наезд во время ролика
	private static final Vec3 P3 = new Vec3(0.5, 66.75, -9.27);    // влетели в экран

	public record Cam(Vec3 pos, float yaw, float pitch) {}

	private static boolean active = false;
	private static long startTick = 0;
	private static boolean skipped = false;
	private static Vec3 fromPos = null;
	private static float fromYaw, fromPitch;
	private static boolean tvOnPlayed, newsPlayed, staticPlayed;
	private static NewsSound newsSound;
	private static int votes = 0, voteTotal = 0;
	private static boolean voted = false, enterWasDown = false;
	/** После переноса: чернота спадает за 1.5 с. */
	private static int arriveFade = 0;
	private static final int ARRIVE_TICKS = 30;
	private static boolean waitingArrive = false;
	/** Свой счётчик тиков клиента (идёт, пока есть мир). */
	private static long ticks = 0;

	private ClientNews() {}

	// ---------- сеть ----------

	public static void onStart(HubPayloads.NewsStart p) {
		Minecraft mc = Minecraft.getInstance();
		boolean first = !active;
		active = true;
		// сервер присылает, сколько тиков прошло от старта; считаем от своего счётчика тиков,
		// а не от времени мира — у клиентов по сети оно может отличаться от сервера
		long st = ticks - p.startTick();
		if (first || skipped != p.skipped() || Math.abs(st - startTick) > 3) startTick = st;
		VadimNaTrone.LOG.info("[news] старт/синхр: прошло {} тиков (first={}, skipped={})", p.startTick(), first, p.skipped());
		if (first) {
			skipped = false;
			tvOnPlayed = newsPlayed = staticPlayed = false;
			votes = voteTotal = 0;
			voted = false;
			waitingArrive = false;
			arriveFade = 0;
			if (mc.player != null) {
				fromPos = mc.player.getEyePosition();
				fromYaw = mc.player.getYRot();
				fromPitch = mc.player.getXRot();
			} else {
				fromPos = P1;
				fromYaw = 180f;
				fromPitch = 0f;
			}
			TvVideo.prepare();
		}
		if (p.skipped()) {
			skipped = true;
			tvOnPlayed = newsPlayed = true;
			stopNews();
		}
	}

	public static void onVotes(HubPayloads.NewsVotes p) {
		votes = p.votes();
		voteTotal = p.total();
	}

	public static void onEnd(HubPayloads.NewsEnd p) {
		stopNews();
		active = false;
		TvVideo.release();
		if (p.moved()) {
			waitingArrive = true;   // держим черноту, пока мир заброшки не загрузится
			arriveFade = 0;
		}
	}

	// ---------- время ----------

	public static boolean active() { return active; }

	/** Секунды от старта (с долей тика). Отрицательно — ещё не началось. */
	public static float time(float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return 0f;
		return (ticks - startTick + partial) / 20f;
	}

	private static float now() {
		return time(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	private static float k(float t, float a, float b) { return Mth.clamp((t - a) / (b - a), 0f, 1f); }

	/** Телек включён (на экране ролик). */
	public static boolean tvOn() { return active && now() >= VIDEO_AT; }

	/** Время внутри ролика, секунды. */
	public static float videoTime() { return Mth.clamp(now() - VIDEO_AT, 0f, VIDEO_LEN - 0.01f); }

	/** Множитель для подвесных ламп (шейдерные конусы): гаснут до 25 %. */
	public static float lampFactor() {
		if (!active) return 1f;
		return 1f - 0.75f * smooth(k(now(), 0.5f, 2.5f));
	}

	/** Множитель блочного света (лампы, гирлянда) — зал темнеет, телек светится. */
	public static float blockLightFactor() {
		if (!active) return 1f;
		return 1f - 0.5f * smooth(k(now(), 0.5f, 2.5f));
	}

	/** Громкость джаза: затихает за 2 с и больше не возвращается до конца катсцены. */
	public static float jazzFactor() {
		if (!active) return 1f;
		return 1f - smooth(k(now(), 0f, 2f));
	}

	// ---------- камера ----------

	private static Vec3 lerp(Vec3 a, Vec3 b, float f) {
		return new Vec3(Mth.lerp(f, a.x, b.x), Mth.lerp(f, a.y, b.y), Mth.lerp(f, a.z, b.z));
	}

	private static float lookYaw(Vec3 from, Vec3 to) {
		return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
	}

	private static float lookPitch(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		return (float) -Math.toDegrees(Math.atan2(to.y - from.y, Math.sqrt(dx * dx + dz * dz)));
	}

	public static Cam camera(float partial) {
		float t = time(partial);
		Vec3 pos;
		if (t < 3f) {
			pos = lerp(fromPos, P1, smooth(k(t, 0f, 3f)));
		} else if (t < PUSH_AT) {
			// за первые 9 с подъезжаем к P2, дальше еле заметно дышим вперёд
			float a = smooth(k(t, 3f, 12f));
			pos = lerp(P1, P2, a).add(0, 0, -0.25 * k(t, 12f, PUSH_AT));
		} else {
			Vec3 p2 = P2.add(0, 0, -0.25);
			float f = k(t, PUSH_AT, 39.2f);
			pos = lerp(p2, P3, f * f * f);   // разгон: в конце буквально влетаем в экран
		}
		float yaw = lookYaw(pos, SCREEN), pitch = lookPitch(pos, SCREEN);
		if (t < 3f) {
			// плавно разворачиваем взгляд из того, куда смотрел игрок
			float f = smooth(k(t, 0f, 2.6f));
			yaw = fromYaw + Mth.wrapDegrees(yaw - fromYaw) * f;
			pitch = Mth.lerp(f, fromPitch, pitch);
		}
		// лёгкая «ручная» камера
		float sway = t < PUSH_AT ? 1f : 1f - k(t, PUSH_AT, 37f);
		yaw += (float) (Math.sin(t * 0.7) * 0.25 + Math.sin(t * 1.9) * 0.08) * sway;
		pitch += (float) (Math.sin(t * 0.9 + 1.3) * 0.18) * sway;
		return new Cam(pos, yaw, pitch);
	}

	// ---------- тик: звук, пропуск, выход из черноты ----------

	public static void tick(Minecraft mc) {
		if (mc.level != null && !mc.isPaused()) ticks++;
		if (waitingArrive) {
			// мир заброшки прогрузился — выходим из черноты
			if (mc.level != null && mc.player != null && mc.screen == null && !ru.vadim.client.ClientZone.hub()) {
				waitingArrive = false;
				arriveFade = ARRIVE_TICKS;
			}
		} else if (arriveFade > 0) arriveFade--;

		if (!active) return;
		if (mc.level == null || mc.player == null) {
			stopNews();
			active = false;
			return;
		}
		float t = now();
		if (!tvOnPlayed && t >= VIDEO_AT - 0.05f) {
			tvOnPlayed = true;
			playAt(ModSounds.TV_ON, 0.8f);
		}
		if (!newsPlayed && t >= VIDEO_AT) {
			newsPlayed = true;
			newsSound = new NewsSound();
			mc.getSoundManager().play(newsSound);
		}
		if (skipped && !staticPlayed && t >= STATIC_AT) {
			staticPlayed = true;
			playAt(ModSounds.TV_STATIC, 1f);
		}
		// Enter — голос за пропуск (один раз)
		boolean down = mc.screen == null && InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_ENTER);
		if (down && !enterWasDown && !voted && !skipped && t < STATIC_AT - 1f
				&& ClientPlayNetworking.canSend(HubPayloads.NewsSkip.TYPE)) {
			voted = true;
			ClientPlayNetworking.send(new HubPayloads.NewsSkip());
		}
		enterWasDown = down;
		// таймаут: если сервер так и не прислал конец — выходим сами
		if (t > END_AT + 10f) {
			stopNews();
			active = false;
			TvVideo.release();
		}
	}

	private static void playAt(SoundEvent e, float vol) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(e, SoundSource.RECORDS, vol, 1f,
				SoundInstance.createUnseededRandom(), SCREEN.x, SCREEN.y, SCREEN.z));
	}

	private static void stopNews() {
		if (newsSound != null) {
			Minecraft.getInstance().getSoundManager().stop(newsSound);
			newsSound = null;
		}
	}

	/** Звук ролика из телека: стоит на месте, слышно громче, когда камера подъезжает. */
	private static final class NewsSound extends AbstractTickableSoundInstance {
		NewsSound() {
			super(ModSounds.NEWS_TV, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
			x = SCREEN.x;
			y = SCREEN.y;
			z = SCREEN.z;
			volume = 1f;
			attenuation = Attenuation.LINEAR;
			looping = false;
		}

		@Override public void tick() { if (!active) stop(); }
	}

	// ---------- экран: полосы кино, затемнение, подсказка про пропуск ----------

	public static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth(), h = g.guiHeight();
		if (!active) {
			if (waitingArrive) g.fill(0, 0, w, h, 0xFF000000);
			else if (arriveFade > 0) {
				float a = (arriveFade - dt.getGameTimeDeltaPartialTick(false)) / ARRIVE_TICKS;
				g.fill(0, 0, w, h, (int) (Mth.clamp(a, 0f, 1f) * 255) << 24);
			}
			return;
		}
		float t = time(dt.getGameTimeDeltaPartialTick(false));
		int bar = (int) (h * 0.11f * smooth(k(t, 0f, 1.2f)));
		if (bar > 0) {
			g.fill(0, 0, w, bar, 0xFF000000);
			g.fill(0, h - bar, w, h, 0xFF000000);
		}
		// вспышка-моргание при щелчке пульта
		if (t >= VIDEO_AT - 0.05f && t < VIDEO_AT + 0.12f && !skipped) g.fill(0, 0, w, h, 0x22FFFFFF);
		float black = smooth(k(t, BLACK_AT, END_AT - 0.4f));
		if (black > 0) g.fill(0, 0, w, h, (int) (black * 255) << 24);
		// подсказка про пропуск — в нижней полосе, мелко
		if (!skipped && t > 3f && t < STATIC_AT - 1f && bar > 10) {
			String s = voted
					? "пропуск: " + Math.max(votes, 1) + " / " + Math.max(voteTotal, 1)
					: "Enter — пропустить" + (votes > 0 ? "  (" + votes + " / " + voteTotal + ")" : "");
			int y = h - bar / 2 - 4;
			g.drawString(mc.font, Component.literal(s), w - mc.font.width(s) - 12, y, 0x88B8B0A0, false);
		}
	}
}
