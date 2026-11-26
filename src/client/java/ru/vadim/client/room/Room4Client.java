package ru.vadim.client.room;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import ru.vadim.VadimNaTrone;
import ru.vadim.client.ClientZone;
import ru.vadim.sound.ModSounds;
import ru.vadim.world.Room4;

/**
 * Комната 4 «Бэкрумс», клиент: VHS-фильтр (макет Claude Design «VHS-фильтр — комната 4», одобрен Димой 26.11) и случайные
 * фоновые треки Димы («иногда включается», тихо).
 * VHS: развёртка, лёгкий тёплый тон, виньетка, катящаяся полоса, ● REC мигает раз в секунду, CAM N (сектор игрока), таймкод с
 * момента входа, дата. glitch (0..1) — сбой (разлучник/удар, этап 7д): полосы, шум, сильнее сдвиг цветов, «TRACKING».
 */
public final class Room4Client {
	private static final FontDescription MONO = new FontDescription.Resource(VadimNaTrone.id("pt_mono"));
	private static final Identifier VIGNETTE = VadimNaTrone.id("textures/gui/room4/vignette.png");
	/** Сбой картинки 0..1 (выставляет логика разлучника/ударов). */
	public static float glitch = 0f;
	private static int ticks = -1, cam = 1, ambWait = 600, scream = 0;
	private static final Identifier SMILER = VadimNaTrone.id("textures/item/room4/smiler.png");

	/** Скример улыбаки: лицо на весь экран ~0,8 с с тряской и сбоем картинки. */
	public static void onFx(ru.vadim.net.HubPayloads.Room4Fx p) { if (p.kind() == 1) scream = 16; }
	private static SoundInstance amb;
	private static final java.util.Random rnd = new java.util.Random();

	private Room4Client() {}

	public static void tick(Minecraft mc) {
		if (!ClientZone.room4() || mc.player == null) {
			ticks = -1;
			glitch = 0f;
			if (amb != null) { mc.getSoundManager().stop(amb); amb = null; }
			ambWait = 600;
			return;
		}
		if (ticks < 0) {   // только вошли: номер камеры = ближайший спавн сектора
			double bd = Double.MAX_VALUE;
			for (int s = 0; s < 3; s++) {
				Vec3 sp = Room4.spawn(s);
				double d = mc.player.distanceToSqr(sp);
				if (d < bd) { bd = d; cam = s + 1; }
			}
			ticks = 0;
		}
		ticks++;
		if (scream > 0) scream--;
		// фоновые треки: раз в 40–120 с, если ничего не играет
		if (amb != null && !mc.getSoundManager().isActive(amb)) amb = null;
		if (amb == null && --ambWait <= 0) {
			amb = SimpleSoundInstance.forUI(ModSounds.ROOM4_AMB[rnd.nextInt(ModSounds.ROOM4_AMB.length)], 1f, 0.3f);
			mc.getSoundManager().play(amb);
			ambWait = 800 + rnd.nextInt(1600);
		}
	}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		if (!ClientZone.room4() || ticks < 0) return;
		Minecraft mc = Minecraft.getInstance();
		int w = g.guiWidth(), h = g.guiHeight();
		float k = h / 720f;
		float gl = Math.max(0f, Math.min(1f, scream > 0 ? 1f : glitch));
		// тон плёнки и развёртка
		g.fill(0, 0, w, h, 0x18302810);
		for (int y = 0; y < h; y += 2) g.fill(0, y, w, y + 1, 0x24000000);
		// катящаяся полоса
		int band = (int) ((ticks * 0.9f) % (h + 30)) - 15;
		g.fill(0, band, w, band + Math.max(2, Math.round(5 * k)), 0x12FFFFFF);
		// сбой: съехавшие полосы и шум внизу
		if (gl > 0.01f) {
			java.util.Random r = new java.util.Random(ticks / 2);
			int n = 2 + Math.round(gl * 7);
			for (int i = 0; i < n; i++) {
				int y = r.nextInt(h), bh = 1 + r.nextInt(Math.max(2, Math.round(10 * k)));
				int a = (int) (gl * (30 + r.nextInt(60)));
				g.fill(0, y, w, y + bh, (a << 24) | 0xFFFFFF);
			}
			int ny = Math.round(h * 0.78f);
			for (int x = 0; x < w; x += 3) if (r.nextFloat() < 0.5f) g.fill(x, ny, x + 2, ny + Math.round(16 * k), ((int) (gl * 110) << 24) | 0xE0E0E0);
		}
		if (scream > 0) {   // лицо улыбаки во весь экран, дёргается
			java.util.Random r = new java.util.Random(ticks);
			int size = Math.round(h * (1.25f + (16 - scream) * 0.03f));
			int sx = (w - size) / 2 + r.nextInt(31) - 15, sy = (h - size) / 2 + r.nextInt(31) - 15;
			g.fill(0, 0, w, h, (scream % 3 == 0 ? 0xC0FFFFFF : 0xE0000000));
			g.blit(RenderPipelines.GUI_TEXTURED, SMILER, sx, sy, 0f, 0f, size, size, 64, 64, 64, 64);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, w, h, 256, 144, 256, 144);
		// надписи
		Font font = mc.font;
		float s = 34f * k / 9f;   // ~34 px на 720
		int pad = Math.round(40 * k);
		int shift = Math.max(1, Math.round((1.2f + gl * 2.5f) * k * 2));
		boolean on = (ticks / 20) % 2 == 0;
		if (on) {
			int r = Math.round(10 * k), cx = pad + r, cy = pad + Math.round(17 * k);
			g.fill(cx - r, cy - r + 2, cx + r, cy + r - 2, 0xFFFF2B2B);
			g.fill(cx - r + 2, cy - r, cx + r - 2, cy + r, 0xFFFF2B2B);
		}
		vtext(g, font, "REC", pad + Math.round(30 * k), pad, s, shift);
		String c = "CAM " + cam;
		vtext(g, font, c, w - pad - Math.round(font.width(mono(c)) * s), pad, s, shift);
		int f = ticks % 20, sec = ticks / 20;
		String tc = String.format("SP  %02d:%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60, f * 30 / 20);
		int by = h - pad - Math.round(9 * s) - Math.round(46 * k);   // над хотбаром
		vtext(g, font, tc, pad, by, s, shift);
		String date = "НОЯ 26 1996";
		vtext(g, font, date, w - pad - Math.round(font.width(mono(date)) * s), by, s, shift);
		if (gl > 0.6f && (ticks / 3) % 2 == 0) {
			String t = "TRACKING";
			float ts = s * 1.4f;
			vtext(g, font, t, Math.round((w - font.width(mono(t)) * ts) / 2f), Math.round(h * 0.45f), ts, shift * 2);
		}
	}

	private static Component mono(String t) { return Component.literal(t).withStyle(st -> st.withFont(MONO)); }

	/** Текст «с плёнки»: красная и синяя копии со сдвигом, сверху белый. */
	private static void vtext(GuiGraphics g, Font font, String t, int x, int y, float s, int shift) {
		Component c = mono(t);
		draw(g, font, c, x - shift, y, s, 0xA0FF3030);
		draw(g, font, c, x + shift, y, s, 0xA03AA0FF);
		draw(g, font, c, x, y, s, 0xF0F4F1E6);
	}

	private static void draw(GuiGraphics g, Font font, Component c, float x, float y, float s, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(s, s);
		g.drawString(font, c, 0, 0, color, false);
		g.pose().popMatrix();
	}
}
