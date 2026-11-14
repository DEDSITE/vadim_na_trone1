package ru.vadim.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import ru.vadim.VadimNaTrone;

import java.util.List;

/**
 * Разговор с барменом Тимофеем — вариант A «Латунь и орех» из Claude Design (холст «Диалог бармена»).
 * Всё рисуется в «координатах макета» 1280×720 и масштабируется под экран (низ окна прижат к низу экрана).
 * Шрифты — PT Serif / PT Sans / PT Mono (assets/vadim_na_trone/font, лицензия ParaType Free Font).
 * Ответ — мышкой или цифрой 1–5, Esc — уйти. Сервер не нужен: просто реплики.
 */
public class BarmanScreen extends Screen {
	private record Line(String ask, String answer) {}

	private static final List<Line> LINES = List.of(
			new Line("Налей чего-нибудь", "Сначала игра, потом бар. Такие правила заведения. Кнопка «Готов» — у двери, когда все соберутся."),
			new Line("Кто такой Вадим?", "Тсс. Не здесь. Он всё слышит… Говорят, сидит где-то на своём троне и смотрит."),
			new Line("Что там по телеку?", "Опять люди пропадают. Третий случай за месяц. Одному, вроде, удалось выбраться… Включу потом, сами посмотрите."),
			new Line("Что за лопаты на стене?", "Музейные экспонаты. Лопата дежаву и лопата аниме-психа. Тронешь — будешь мыть полы до утра."),
			new Line("А записки?", "Записки Короля. Все шестнадцать. Собирали всей толпой, теперь висят. Почитай, мудрые вещи пишет.")
	);

	private static final Identifier PORTRAIT = VadimNaTrone.id("textures/gui/barman_portrait.png");
	private static final FontDescription SERIF_IT = font("pt_serif_italic"), SERIF_B = font("pt_serif_bold"),
			SANS = font("pt_sans"), SANS_B = font("pt_sans_bold"), MONO = font("pt_mono");
	/** Высота строки шрифта при size=11 в font json. */
	private static final float EM = 11f;

	// палитра макета
	private static final int GOLD = 0xFFC9A24A, GOLD_LIGHT = 0xFFE8C77A, GOLD_HOT = 0xFFFFE2A0, BROWN_IN = 0xFF5A3A17,
			BROWN_DEEP = 0xFF22150C, PANEL_TOP = 0xFF2A1A0F, PANEL_BOT = 0xFF1A100A, QUOTE = 0xFFF1E6CF, ITEM = 0xFFD9CCB2,
			NUM = 0xFF8A7350, SUB = 0xFFA8916A, FRAME_BG = 0xFF2B1A0E;

	// раскладка в координатах макета
	private static final int L = 96, R = 1280 - 96, BOTTOM = 720 - 40, PORTRAIT_W = 236, GAP = 28;
	private static final int PANEL_X = L + PORTRAIT_W + GAP, PANEL_PAD_X = 30, PANEL_PAD_TOP = 26;
	private static final int ROW_H = 44, ROW_GAP = 6;

	private String answer = "Чего надо? Только быстро, у меня тут смена.";
	private int picked = -1;
	private float s = 1f, ox = 0f, oy = 0f;
	private int panelTop, listTop, exitX0, exitY0, exitX1, exitY1;

	public BarmanScreen() {
		super(Component.literal("Тимофей, бармен"));
	}

	private static FontDescription font(String name) {
		return new FontDescription.Resource(VadimNaTrone.id(name));
	}

	private static Component txt(String s, FontDescription f) {
		return Component.literal(s).withStyle(st -> st.withFont(f));
	}

	@Override public boolean isPauseScreen() { return false; }

	/** Фон не затемняем ванильным блюром — рисуем свой полупрозрачный слой. */
	@Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

	private void layout() {
		s = Math.min(width / 1280f, height / 720f);
		ox = (width - 1280 * s) / 2f;
		oy = height - 720 * s;
	}

	private float dx(double mx) { return (float) ((mx - ox) / s); }
	private float dy(double my) { return (float) ((my - oy) / s); }

	// ------------------------------------------------------------------ рисование

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		layout();
		g.fill(0, 0, width, height, 0x8C080503);
		float mdx = dx(mx), mdy = dy(my);

		g.pose().pushMatrix();
		g.pose().translate(ox, oy);
		g.pose().scale(s, s);

		int panelW = R - PANEL_X, textW = panelW - PANEL_PAD_X * 2;
		// реплика: курсив с засечками, 27px
		float qs = 27f / EM;
		List<FormattedCharSequence> quote = font.split(txt("«" + answer + "»", SERIF_IT), (int) (textW / qs));
		int quoteH = Math.round(quote.size() * EM * 1.32f * qs);
		int listH = LINES.size() * ROW_H + (LINES.size() - 1) * ROW_GAP;
		int panelH = PANEL_PAD_TOP + quoteH + 16 + 1 + 16 + listH + 4 + 16 + 44 + 24;
		int portraitH = 8 + 214 + 8 + 6 + 12 + 30 + 12 + 18;
		int h = Math.max(panelH, portraitH);
		panelTop = BOTTOM - h;

		// --- портрет в латунной рамке
		int px = L, py = panelTop;
		g.fill(px, py, px + PORTRAIT_W, py + 236, FRAME_BG);
		frame(g, px, py, px + PORTRAIT_W, py + 236);
		g.blit(RenderPipelines.GUI_TEXTURED, PORTRAIT, px + 11, py + 11, 0f, 0f, 214, 214, 256, 256, 256, 256);
		centered(g, txt("ТИМОФЕЙ", SERIF_B), px + PORTRAIT_W / 2f, py + 236 + 12, 26f, GOLD_LIGHT);
		centered(g, txt("БАРМЕН · БАР «ТРОН»", SANS), px + PORTRAIT_W / 2f, py + 236 + 12 + 34, 13f, SUB);

		// --- панель реплики
		int x0 = PANEL_X, y0 = panelTop, x1 = R, y1 = BOTTOM;
		g.fillGradient(x0, y0, x1, y1, PANEL_TOP, PANEL_BOT);
		frame(g, x0, y0, x1, y1);
		g.fill(x0 + 5, y0 + 5, x1 - 5, y0 + 6, BROWN_DEEP);

		int cx = x0 + PANEL_PAD_X, cy = y0 + PANEL_PAD_TOP;
		for (FormattedCharSequence line : quote) {
			text(g, line, cx, cy, qs, QUOTE);
			cy += Math.round(EM * 1.32f * qs);
		}
		cy += 16;
		// разделитель: золото, гаснущее к краям
		int dw = textW, half = dw / 2;
		for (int i = 0; i < dw; i += 4) {
			float a = 1f - Math.abs(i - half) / (float) half;
			g.fill(cx + i, cy, cx + i + 4, cy + 1, ((int) (a * 200) << 24) | 0x7A5A28);
		}
		cy += 17;

		// --- ответы
		listTop = cy;
		int hot = rowAt(mdx, mdy);
		for (int i = 0; i < LINES.size(); i++) {
			int ry = cy + i * (ROW_H + ROW_GAP);
			boolean on = i == hot || (hot < 0 && i == picked);
			if (on) {
				g.fill(cx - 14, ry, x1 - PANEL_PAD_X + 14, ry + ROW_H, 0x1FE8C77A);
				g.fill(cx - 14, ry, cx - 10, ry + ROW_H, GOLD_LIGHT);
			}
			float ty = ry + (ROW_H - EM * 19f / EM) / 2f;
			text(g, txt("[" + (i + 1) + "]", MONO), cx, ry + (ROW_H - 15f) / 2f + 1, 15f / EM, on ? GOLD_LIGHT : NUM);
			text(g, txt(LINES.get(i).ask(), on ? SANS_B : SANS), cx + 40, ty, 19f / EM, on ? GOLD_HOT : ITEM);
		}
		cy += listH + 4 + 16;

		// --- низ: подсказка и «уйти»
		text(g, txt("ЛКМ ИЛИ 1–5 — СПРОСИТЬ", SANS), cx, cy + 15, 14f / EM, NUM);
		Component exit = txt("ESC · ЛАДНО, Я ПОШЁЛ", SANS_B);
		float es = 14f / EM;
		int ew = Math.round(font.width(exit) * es) + 36;
		exitX1 = x1 - PANEL_PAD_X;
		exitX0 = exitX1 - ew;
		exitY0 = cy;
		exitY1 = cy + 44;
		boolean exitHot = mdx >= exitX0 && mdx < exitX1 && mdy >= exitY0 && mdy < exitY1;
		outline(g, exitX0, exitY0, exitX1, exitY1, 2, exitHot ? GOLD_LIGHT : 0xFF7A5A28);
		if (exitHot) g.fill(exitX0 + 2, exitY0 + 2, exitX1 - 2, exitY1 - 2, 0x1FE8C77A);
		text(g, exit, exitX0 + 18, exitY0 + (44 - 14) / 2f + 1, es, exitHot ? GOLD_HOT : GOLD);

		g.pose().popMatrix();
		super.render(g, mx, my, pt);
	}

	/** Латунная рамка как в макете: 3px золото, внутри 2px тёмная латунь. */
	private void frame(GuiGraphics g, int x0, int y0, int x1, int y1) {
		outline(g, x0, y0, x1, y1, 3, GOLD);
		outline(g, x0 + 3, y0 + 3, x1 - 3, y1 - 3, 2, BROWN_IN);
	}

	private void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int t, int c) {
		g.fill(x0, y0, x1, y0 + t, c);
		g.fill(x0, y1 - t, x1, y1, c);
		g.fill(x0, y0 + t, x0 + t, y1 - t, c);
		g.fill(x1 - t, y0 + t, x1, y1 - t, c);
	}

	private void text(GuiGraphics g, Component c, float x, float y, float scale, int color) {
		text(g, c.getVisualOrderText(), x, y, scale, color);
	}

	private void text(GuiGraphics g, FormattedCharSequence c, float x, float y, float scale, int color) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.drawString(font, c, 0, 0, color, false);
		g.pose().popMatrix();
	}

	private void centered(GuiGraphics g, Component c, float cx, float y, float px, int color) {
		float sc = px / EM;
		text(g, c, cx - font.width(c) * sc / 2f, y, sc, color);
	}

	// ------------------------------------------------------------------ ввод

	private int rowAt(float x, float y) {
		if (x < PANEL_X || x > R) return -1;
		for (int i = 0; i < LINES.size(); i++) {
			int ry = listTop + i * (ROW_H + ROW_GAP);
			if (y >= ry && y < ry + ROW_H) return i;
		}
		return -1;
	}

	private void pick(int i) {
		picked = i;
		answer = LINES.get(i).answer();
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
		if (ev.button() != 0) return super.mouseClicked(ev, dbl);
		layout();
		float x = dx(ev.x()), y = dy(ev.y());
		int i = rowAt(x, y);
		if (i >= 0) {
			pick(i);
			return true;
		}
		if (x >= exitX0 && x < exitX1 && y >= exitY0 && y < exitY1) {
			onClose();
			return true;
		}
		return super.mouseClicked(ev, dbl);
	}

	@Override
	public boolean keyPressed(KeyEvent ev) {
		int k = ev.key();
		if (k >= 49 && k < 49 + LINES.size()) {   // клавиши 1–5 (GLFW_KEY_1 = 49)
			pick(k - 49);
			return true;
		}
		return super.keyPressed(ev);
	}
}
