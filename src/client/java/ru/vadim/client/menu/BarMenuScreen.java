package ru.vadim.client.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import ru.vadim.VadimNaTrone;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Главное меню: кадр бара изнутри (снят в игре командой /vadim menushot), за окнами ездят машины —
 * фары, габариты, отсвет на асфальте. Играет тот же джаз, что в баре.
 * Машины рисуются только внутри стёкол (WINDOWS — прямоугольники окон в долях кадра).
 */
public class BarMenuScreen extends TitleScreen {
	private static final Identifier BG = VadimNaTrone.id("textures/gui/menu_bg.png");
	private static final int BG_W = 1920, BG_H = 1080;
	/** Стёкла четырёх окон в кадре menushot: x0, y0, x1, y1 (доли ширины/высоты кадра). */
	static float[][] WINDOWS = {
			{0.000f, 0.375f, 0.140f, 0.580f},
			{0.197f, 0.392f, 0.312f, 0.576f},
			{0.354f, 0.410f, 0.445f, 0.576f},
			{0.475f, 0.420f, 0.568f, 0.566f}};
	/** Где в окне дорога: доля высоты окна для ближней и дальней полосы. */
	private static final float LANE_NEAR = 0.80f, LANE_FAR = 0.62f;
	private static final String[] ITEMS = {"Одиночная игра", "Сетевая игра", "Настройки", "Выйти"};
	private static final Random R = new Random();

	private record Car(float speed, boolean near, int color, float len, boolean taxi, float start) {}
	private final List<Car> cars = new ArrayList<>();
	private int age = 0, nextCar = 30;

	@Override protected void init() {}
	@Override public void added() {}
	@Override public void removed() {}

	@Override
	public void tick() {
		age++;
		if (--nextCar <= 0) {
			nextCar = 90 + R.nextInt(160);
			boolean near = R.nextBoolean();
			int[] colors = {0xFF5A1414, 0xFF14243F, 0xFFB8B8B4, 0xFF111113, 0xFF223A26, 0xFFC99A14};
			boolean taxi = R.nextFloat() < 0.2f;
			cars.add(new Car((near ? 1 : -1) * (0.0035f + R.nextFloat() * 0.002f), near,
					taxi ? 0xFFC99A14 : colors[R.nextInt(colors.length - 1)], 0.11f + R.nextFloat() * 0.04f, taxi, age));
		}
		cars.removeIf(c -> (age - c.start) * Math.abs(c.speed) > 1.5f);
	}

	private float scale() { return Math.max(1f, width / 342f); }
	private int rowH() { return Math.round(scale() * 14); }
	private int rowX() { return Math.round(width * 0.045f); }
	private int rowY(int i) { return height - 22 - rowH() * (ITEMS.length - i); }

	private int hovered(double mx, double my) {
		for (int i = 0; i < ITEMS.length; i++) {
			int w = Math.round(font.width(ITEMS[i]) * scale());
			if (mx >= rowX() && mx <= rowX() + w && my >= rowY(i) && my < rowY(i) + rowH()) return i;
		}
		return -1;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		float t = age + pt;
		// кадр: заполняем экран без полос
		g.fill(0, 0, width, height, 0xFF050608);
		float aspect = (float) width / height;
		int uw = BG_W, uh = BG_H, u = 0, v = 0;
		if (aspect > (float) BG_W / BG_H) { uh = Math.round(BG_W / aspect); v = (BG_H - uh) / 2; }
		else { uw = Math.round(BG_H * aspect); u = (BG_W - uw) / 2; }
		g.blit(RenderPipelines.GUI_TEXTURED, BG, 0, 0, u, v, width, height, uw, uh, BG_W, BG_H);
		// машины за стёклами: едут одной линией через все окна, видны только в стёклах
		int[][] rect = new int[WINDOWS.length][];
		for (int k = 0; k < WINDOWS.length; k++) {
			float[] w = WINDOWS[k];
			rect[k] = new int[]{Math.round((w[0] * BG_W - u) / uw * width), Math.round((w[1] * BG_H - v) / uh * height),
					Math.round((w[2] * BG_W - u) / uw * width), Math.round((w[3] * BG_H - v) / uh * height)};
		}
		float sx0 = rect[0][0], sx1 = rect[rect.length - 1][2];
		for (int[] r : rect) {
			if (r[2] <= r[0] || r[3] <= r[1]) continue;
			g.enableScissor(Math.max(0, r[0]), r[1], r[2], r[3]);
			for (Car c : cars) drawCar(g, c, t, sx0, sx1, r[0], r[1], r[2], r[3]);
			g.disableScissor();
		}
		// тёплая виньетка
		g.fillGradient(0, height - height / 3, width, height, 0x00000000, 0xB0050302);
		logo(g, t);
		menu(g, mx, my);
		g.drawString(font, "Minecraft 1.21.11 / Fabric · Вадим на Троне · не распространять", rowX(), height - 12, 0xFF6E665A, true);
		if (t < 40) g.fill(0, 0, width, height, (int) ((1f - t / 40f) * 255) << 24);
	}

	/** Машина в окне: силуэт кузова, светящиеся фары/габариты и пятно света на дороге. */
	private void drawCar(GuiGraphics g, Car c, float t, float sx0, float sx1, int x0, int y0, int x1, int y1) {
		float span = sx1 - sx0, h = y1 - y0;
		float prog = Math.abs((t - c.start) * c.speed);   // доля ширины всех окон
		float carW = c.len * 7f * h * (c.near ? 1f : 0.8f), carH = carW * 0.32f;
		float baseY = y0 + h * (c.near ? LANE_NEAR : LANE_FAR);
		float x = c.speed > 0 ? sx0 - carW + prog * (span + carW) : sx1 - prog * (span + carW);
		boolean right = c.speed > 0;
		int dim = c.near ? 0xFF : 0xC0;
		// свет фар на асфальте
		float hx = right ? x + carW : x;
		for (int i = 0; i < 6; i++) {
			float len = carW * (0.5f + i * 0.18f);
			int a = (int) (40 - i * 6) << 24;
			int lx0 = Math.round(right ? hx : hx - len), lx1 = Math.round(right ? hx + len : hx);
			g.fill(lx0, Math.round(baseY - carH * 0.05f), lx1, Math.round(baseY + carH * 0.12f), a | 0xFFE7B0);
		}
		// кузов
		int body = c.color & 0x00FFFFFF | (dim << 24);
		g.fill(Math.round(x), Math.round(baseY - carH * 0.62f), Math.round(x + carW), Math.round(baseY), body);
		float roofX0 = x + carW * (right ? 0.25f : 0.18f), roofX1 = x + carW * (right ? 0.82f : 0.75f);
		g.fill(Math.round(roofX0), Math.round(baseY - carH), Math.round(roofX1), Math.round(baseY - carH * 0.6f), body);
		g.fill(Math.round(roofX0 + carW * 0.04f), Math.round(baseY - carH * 0.92f), Math.round(roofX1 - carW * 0.04f),
				Math.round(baseY - carH * 0.64f), 0xE0182030);
		if (c.taxi) g.fill(Math.round((roofX0 + roofX1) / 2 - carW * 0.06f), Math.round(baseY - carH * 1.12f),
				Math.round((roofX0 + roofX1) / 2 + carW * 0.06f), Math.round(baseY - carH), 0xFFFFD040);
		// колёса
		for (float k : new float[]{0.2f, 0.78f})
			g.fill(Math.round(x + carW * k - carH * 0.17f), Math.round(baseY - carH * 0.12f), Math.round(x + carW * k + carH * 0.17f),
					Math.round(baseY + carH * 0.06f), 0xFF050505);
		// фары и габариты
		float fy = baseY - carH * 0.38f;
		int fx = Math.round(right ? x + carW - 2 : x), bx = Math.round(right ? x : x + carW - 2);
		g.fill(fx, Math.round(fy), fx + 2, Math.round(fy + carH * 0.12f) + 1, 0xFFFFF6D8);
		g.fill(bx, Math.round(fy), bx + 2, Math.round(fy + carH * 0.12f) + 1, 0xFFE02018);
		for (int i = 1; i <= 3; i++) {
			int a = (int) (60 / i) << 24;
			g.fill(fx - i * 3, Math.round(fy - i * 2), fx + 2 + i * 3, Math.round(fy + carH * 0.12f + i * 2), a | 0xFFF2C8);
		}
	}

	private void logo(GuiGraphics g, float t) {
		float s = scale() * 2.4f;
		float flick = (age % 173 > 168) ? 0.35f : 1f;
		g.pose().pushMatrix();
		g.pose().translate(width - width * 0.05f, height * 0.08f);
		g.pose().scale(s, s);
		String a = "ВАДИМ", b = "НА ТРОНЕ";
		int col = ((int) (flick * 255) << 24) | 0xE8C77A;
		g.drawString(font, a, -font.width(a), 0, col, true);
		g.pose().scale(0.6f, 0.6f);
		g.drawString(font, b, -font.width(b), 17, ((int) (flick * 255) << 24) | 0xC23A2A, true);
		g.pose().popMatrix();
		float s2 = scale() * 0.9f;
		g.pose().pushMatrix();
		g.pose().translate(width - width * 0.05f, height * 0.08f + s * 9 + s * 0.6f * 12);
		g.pose().scale(s2, s2);
		String sub = "хочешь сыграть в игру?";
		g.drawString(font, sub, -font.width(sub), 0, 0xFF9C9284, true);
		g.pose().popMatrix();
	}

	private void menu(GuiGraphics g, int mx, int my) {
		int hot = hovered(mx, my);
		float s = scale();
		for (int i = 0; i < ITEMS.length; i++) {
			boolean on = i == hot;
			g.pose().pushMatrix();
			g.pose().translate(rowX(), rowY(i));
			g.pose().scale(s, s);
			g.drawString(font, ITEMS[i], 0, 0, on ? 0xFFE8C77A : 0xFFCEC6B4, true);
			if (on) g.fill(0, 9, font.width(ITEMS[i]), 10, 0xFFE8C77A);
			g.pose().popMatrix();
		}
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

	static float clamp(float v) { return Mth.clamp(v, 0f, 1f); }
}
