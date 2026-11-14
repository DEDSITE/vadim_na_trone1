package ru.vadim.client.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import ru.vadim.VadimNaTrone;

/**
 * Главное меню, вариант A «Плёнка» из Claude Design (артефакт «Главное меню — Вадим на Троне»).
 * Фон — зацикленное видео бара (MenuVideo, цветокоррекция «плёнки» уже вшита в кадры),
 * поверх: затемнение слева + виньетка (overlay.png), полосы развёртки, логотип и пункты меню картинками.
 * Всё раскладывается в координатах макета 1280×720, масштаб по высоте экрана, привязка к левому краю.
 */
public class BarMenuScreen extends TitleScreen {
	private static final Identifier BG = VadimNaTrone.id("textures/gui/menu_bg.png");
	private static final int BG_W = 1920, BG_H = 1080;
	/** Пункты меню в макете: x, первый y, ширина, высота, шаг. */
	private static final int IX = 60, IY = 420, IW = 420, IH = 48, ISTEP = 52, COUNT = 4;

	private int age = 0;

	@Override protected void init() {}
	@Override public void added() { MenuVideo.start(); }
	@Override public void removed() { MenuVideo.stop(); }

	@Override
	public void tick() { age++; }

	/** Масштаб макета 1280×720 → экран (в GUI-единицах). */
	private float s() { return height / 720f; }

	private int hovered(double mx, double my) {
		float s = s();
		double x = mx / s, y = my / s;
		for (int i = 0; i < COUNT; i++)
			if (x >= IX && x < IX + IW && y >= IY + i * ISTEP && y < IY + i * ISTEP + IH) return i;
		return -1;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		float t = age + pt;
		g.fill(0, 0, width, height, 0xFF050605);
		// фон: кадр/видео без полос (обрезаем по краям под пропорции экрана)
		float aspect = (float) width / height;
		int uw = BG_W, uh = BG_H, u = 0, v = 0;
		if (aspect > (float) BG_W / BG_H) { uh = Math.round(BG_W / aspect); v = (BG_H - uh) / 2; }
		else { uw = Math.round(BG_H * aspect); u = (BG_W - uw) / 2; }
		g.blit(RenderPipelines.GUI_TEXTURED, BG, 0, 0, u, v, width, height, uw, uh, BG_W, BG_H);
		MenuVideo.upload();
		if (MenuVideo.hasFrame()) {
			float vu = u / (float) BG_W * MenuVideo.W, vv = v / (float) BG_H * MenuVideo.H;
			int vw = Math.round(uw / (float) BG_W * MenuVideo.W), vh = Math.round(uh / (float) BG_H * MenuVideo.H);
			g.blit(RenderPipelines.GUI_TEXTURED, MenuVideo.TEX, 0, 0, vu, vv, width, height, vw, vh, MenuVideo.W, MenuVideo.H);
		}
		// затемнение слева и виньетка — на весь экран
		MenuTex.Tex ov = MenuTex.get("overlay");
		if (ov != null) g.blit(RenderPipelines.GUI_TEXTURED, ov.id(), 0, 0, 0, 0, width, height, ov.w(), ov.h(), ov.w(), ov.h());
		scanlines(g);

		// макет 1280×720
		float s = s();
		g.pose().pushMatrix();
		g.pose().scale(s, s);
		draw(g, "logo", 72, 150);
		// мигающий курсор после «Хочешь сыграть в игру?»
		if ((System.currentTimeMillis() / 530) % 2 == 0) g.fill(337, 383, 348, 385, 0xFFE0352B);
		int hot = hovered(mx, my);
		for (int i = 0; i < COUNT; i++) draw(g, (i == hot ? "h" : "n") + i, IX, IY + i * ISTEP);
		draw(g, "foot", 72, 665);
		g.pose().popMatrix();

		if (t < 40) g.fill(0, 0, width, height, (int) ((1f - t / 40f) * 255) << 24);
	}

	/** Картинка из макета (текстура в 2× размере) в точке макета x, y. */
	private void draw(GuiGraphics g, String name, int x, int y) {
		MenuTex.Tex tx = MenuTex.get(name);
		if (tx == null) return;
		g.blit(RenderPipelines.GUI_TEXTURED, tx.id(), x, y, 0, 0, tx.w() / 2, tx.h() / 2, tx.w(), tx.h(), tx.w(), tx.h());
	}

	/** Полосы развёртки VHS: 1 тёмная строка из 3 в настоящих пикселях экрана. */
	private void scanlines(GuiGraphics g) {
		int gs = minecraft.getWindow().getGuiScale();
		int pw = width * gs, ph = height * gs;
		g.pose().pushMatrix();
		g.pose().scale(1f / gs, 1f / gs);
		for (int y = 0; y < ph; y += 3) g.fill(0, y, pw, y + 1, 0x38000000);
		g.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ev, boolean dbl) {
		int i = ev.button() == 0 ? hovered(ev.x(), ev.y()) : -1;
		if (i < 0) return false;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
		switch (i) {
			case 0 -> minecraft.setScreen(new SelectWorldScreen(this));
			case 1 -> minecraft.setScreen(minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this));
			case 2 -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
			default -> minecraft.stop();
		}
		return true;
	}
}
