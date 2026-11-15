package ru.vadim.client.zavod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import ru.vadim.VadimNaTrone;
import ru.vadim.client.ClientZone;
import ru.vadim.net.HubPayloads;
import ru.vadim.world.ZavodDoor;

/**
 * Подсказки взлома двери (этап 3б) внизу экрана — по макету Claude Design «Взлом двери — HUD, журнал, лом»
 * (стиль «плёнка»): затемнение снизу, метка PT Mono с точкой, текст PT Sans, прогресс — кадры киноплёнки.
 * Раскладка в координатах макета 1280×720, масштаб по высоте экрана.
 */
public final class ZavodHud {
	private static final FontDescription MONO = new FontDescription.Resource(VadimNaTrone.id("pt_mono"));
	private static final FontDescription SANS = new FontDescription.Resource(VadimNaTrone.id("pt_sans"));
	private static final float EM = 11f;
	private static final int RED = 0xFFD9453A, GOLD = 0xFFE8C77A, TEXT = 0xFFEFE6D6;

	private static int hint = 0;
	private static float progress = 0f, shown = 0f;
	/** 0..1 — проявление подсказки (плавно появляется и гаснет). */
	private static float alpha = 0f;
	private static int lastHint = 0;

	private ZavodHud() {}

	public static void onHint(HubPayloads.ZavodHint p) {
		hint = p.hint();
		progress = p.progress();
		if (hint != 0) lastHint = hint;
	}

	public static void tick() {
		if (!ClientZone.zavod()) { hint = 0; alpha = 0f; return; }
		alpha += ((hint != 0 ? 1f : 0f) - alpha) * 0.25f;
		shown += (progress - shown) * 0.4f;
	}

	private record Line(String label, int color, String key, String text, boolean bar) {}

	private static Line line(int h) {
		return switch (h) {
			case ZavodDoor.H_FIND -> new Line("ЗАДАЧА", RED, null, "Цепь на двери. Поищи, чем её сбить", false);
			case ZavodDoor.H_TAKE -> new Line("РЯДОМ", GOLD, "ПКМ", "взять лом", false);
			case ZavodDoor.H_GO -> new Line("ЛОМ У ТЕБЯ", GOLD, "ПКМ", "зажми по цепи на двери с ломом в руке", false);
			case ZavodDoor.H_HELP -> new Line("ПОМОГИ", GOLD, "ПКМ", "зажми по цепи вместе с тем, у кого лом", false);
			case ZavodDoor.H_NEED -> new Line("НУЖЕН ВТОРОЙ", RED, null, "Одному не сломать — пусть кто-то тоже зажмёт ПКМ по цепи", true);
			case ZavodDoor.H_PRY -> new Line("ДЕРЖИТЕ", GOLD, null, "Не отпускайте — цепь поддаётся", true);
			case ZavodDoor.H_DONE -> new Line("ГОТОВО", GOLD, null, "Цепь сбита. Дверь открыта", false);
			default -> null;
		};
	}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		if (alpha < 0.02f || !ClientZone.zavod()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		Line ln = line(hint != 0 ? hint : lastHint);
		if (ln == null) return;
		Font font = mc.font;
		int w = g.guiWidth(), h = g.guiHeight();
		float s = h / 720f;
		int a = Math.round(alpha * 255);

		// затемнение снизу (150 px макета), к низу гуще
		int band = Math.round(150 * s);
		g.fillGradient(0, h - band, w, h - band / 2, 0, ((int) (a * 0.55f) << 24));
		g.fill(0, h - band / 2, w, h, ((int) (a * 0.6f) << 24));

		g.pose().pushMatrix();
		g.pose().translate(w / 2f, h);
		g.pose().scale(s, s);
		// снизу вверх: плёнка (если есть), текст, метка; низ блока — над хотбаром, сердцами и едой (~52 px интерфейса)
		float y = -(52f / s) - 10;
		if (ln.bar) {
			strip(g, -192, y - 22, shown, a);
			y -= 22 + 10;
		}
		// текст (22 px) с клавишей
		float ts = 22f / EM;
		Component txt = Component.literal(ln.text).withStyle(st -> st.withFont(SANS));
		float tw = font.width(txt) * ts;
		float keyW = 0;
		Component key = ln.key == null ? null : Component.literal(ln.key).withStyle(st -> st.withFont(MONO));
		float ks = 15f / EM;
		if (key != null) keyW = font.width(key) * ks + 14 + 8;
		float x0 = -(tw + keyW) / 2f;
		float ty = y - 24;
		if (key != null) {
			int kx0 = Math.round(x0), ky0 = Math.round(ty + 1), kx1 = Math.round(x0 + keyW - 8), ky1 = Math.round(ty + 23);
			int c = (a << 24) | (TEXT & 0xFFFFFF);
			g.fill(kx0, ky0, kx1, ky0 + 1, c);
			g.fill(kx0, ky1 - 1, kx1, ky1, c);
			g.fill(kx0, ky0, kx0 + 1, ky1, c);
			g.fill(kx1 - 1, ky0, kx1, ky1, c);
			text(g, font, key, x0 + 7, ty + 5, ks, c);
		}
		text(g, font, txt, x0 + keyW, ty, ts, (a << 24) | (TEXT & 0xFFFFFF));
		// метка: точка + разрядка 3 px (буквы рисуем по одной — в шрифте нет тонких пробелов)
		float ls = 13f / EM, track = 3f;
		float lw = 16;
		for (int i = 0; i < ln.label.length(); i++)
			lw += font.width(Component.literal(String.valueOf(ln.label.charAt(i))).withStyle(st -> st.withFont(MONO))) * ls + (i < ln.label.length() - 1 ? track : 0);
		float ly = ty - 10 - 15;
		int lc = (a << 24) | (ln.color & 0xFFFFFF);
		int dx = Math.round(-lw / 2f);
		g.fill(dx, Math.round(ly + 3), dx + 8, Math.round(ly + 11), lc);
		float cx = -lw / 2f + 16;
		for (int i = 0; i < ln.label.length(); i++) {
			Component ch = Component.literal(String.valueOf(ln.label.charAt(i))).withStyle(st -> st.withFont(MONO));
			text(g, font, ch, cx, ly, ls, lc);
			cx += font.width(ch) * ls + track;
		}
		g.pose().popMatrix();
	}

	/** Плёнка 384×22: перфорация сверху и снизу, 24 кадра; заполненные — золото. */
	private static void strip(GuiGraphics g, float x, float y, float p, int a) {
		int x0 = Math.round(x), y0 = Math.round(y);
		g.fill(x0, y0, x0 + 384, y0 + 22, (a << 24) | 0x0B0B0B);
		int hole = (a << 24) | 0x3B3B3B;
		for (int i = 0; i < 24; i++) {
			int hx = x0 + 6 + Math.round(i * (372 - 6) / 23f);
			g.fill(hx, y0 + 3, hx + 6, y0 + 6, hole);
			g.fill(hx, y0 + 16, hx + 6, y0 + 19, hole);
		}
		int filled = Math.round(p * 24);
		for (int i = 0; i < 24; i++) {
			int fx = x0 + 6 + Math.round(i * (372 - 13) / 23f);
			g.fill(fx, y0 + 6 + 3, fx + 13, y0 + 6 + 3 + 4, i < filled ? ((a << 24) | 0xE8C77A) : ((a << 24) | 0x262626));
		}
	}

	private static void text(GuiGraphics g, Font font, Component c, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.drawString(font, c, 0, 0, color, false);
		g.pose().popMatrix();
	}
}
