package ru.vadim.client.zavod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import ru.vadim.VadimNaTrone;

/**
 * Журнал охранника Геннадия (этап 3б, ПКМ по журналу на столе в КПП). Лист целиком — картинка из макета
 * Claude Design («Взлом двери — HUD, журнал, лом», артборд «Журнал охранника»), 1240×1400, с прозрачными полями.
 */
public final class JournalScreen extends Screen {
	private static final Identifier SHEET = VadimNaTrone.id("textures/gui/zavod/journal.png");
	private static final int TW = 1240, TH = 1400;
	private static final FontDescription MONO = new FontDescription.Resource(VadimNaTrone.id("pt_mono"));

	public JournalScreen() {
		super(Component.literal("Журнал охранника"));
	}

	@Override public boolean isPauseScreen() { return false; }

	@Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, 0xB8060708);
		// лист: по высоте экрана с полями сверху/снизу
		float s = Math.min((height - 34f) / TH, (width - 20f) / TW);
		int w = Math.round(TW * s), h = Math.round(TH * s);
		int x = (width - w) / 2, y = Math.max(4, (height - 24 - h) / 2);
		g.blit(RenderPipelines.GUI_TEXTURED, SHEET, x, y, 0f, 0f, w, h, TW, TH, TW, TH);
		Component esc = Component.literal("ESC — ЗАКРЫТЬ").withStyle(st -> st.withFont(MONO));
		g.drawString(font, esc, (width - font.width(esc)) / 2, height - 16, 0xFF9A9186, false);
	}
}
