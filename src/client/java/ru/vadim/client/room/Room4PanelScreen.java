package ru.vadim.client.room;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import ru.vadim.VadimNaTrone;
import ru.vadim.net.HubPayloads;
import ru.vadim.world.Room4Power;

/**
 * Комната 4 (7б): экран щитка по макету Claude Design «Электрик — щиток»: 3 предохранителя, 5 рубильников с силой тока,
 * прибор нагрузки (зелёная зона, красное — перегрузка), кнопка «ПОДАТЬ ТОК». Рисуется в виртуальных координатах 520×320.
 */
public final class Room4PanelScreen extends Screen {
	private static final FontDescription MONO = new FontDescription.Resource(VadimNaTrone.id("pt_mono"));
	private static final FontDescription SANS = new FontDescription.Resource(VadimNaTrone.id("pt_sans"));
	private static final int VW = 520, VH = 320;
	private static Room4PanelScreen open;

	private final int sector, zMin, zMax;
	private final int[] values = new int[5];
	private int mask, result, resultTicks;

	private Room4PanelScreen(HubPayloads.Room4Panel p) {
		super(Component.literal("Щиток"));
		sector = p.sector();
		zMin = p.zoneMin();
		zMax = p.zoneMax();
		for (int k = 0; k < 5 && k < p.values().size(); k++) values[k] = p.values().get(k);
		mask = p.mask();
		open = this;
	}

	public static void onPanel(HubPayloads.Room4Panel p) {
		Minecraft mc = Minecraft.getInstance();
		if (open != null && mc.screen == open && open.sector == p.sector()) {
			open.mask = p.mask();
			if (p.result() != 0) { open.result = p.result(); open.resultTicks = 36; }
			return;
		}
		if (p.result() == 0) mc.setScreen(new Room4PanelScreen(p));
	}

	@Override public boolean isPauseScreen() { return false; }

	@Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

	@Override public void removed() { if (open == this) open = null; }

	@Override
	public void tick() {
		if (resultTicks > 0 && --resultTicks == 0) onClose();
	}

	private float scale() { return Math.min(width / (VW + 40f), height / (VH + 40f)); }
	private int ox() { return Math.round((width - VW * scale()) / 2f); }
	private int oy() { return Math.round((height - VH * scale()) / 2f); }

	private int sum() {
		int s = 0;
		for (int k = 0; k < 5; k++) if ((mask >> k & 1) != 0) s += values[k];
		return s;
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, 0xB0000000);
		float s = scale();
		g.pose().pushMatrix();
		g.pose().translate(ox(), oy());
		g.pose().scale(s, s);
		// корпус
		g.fill(-4, -4, VW + 4, VH + 4, 0xFF2E302C);
		g.fill(0, 0, VW, VH, 0xFF61655F);
		for (int[] c : new int[][]{{8, 8}, {VW - 16, 8}, {8, VH - 16}, {VW - 16, VH - 16}}) g.fill(c[0], c[1], c[0] + 8, c[1] + 8, 0xFF9A9D96);
		// шапка
		g.fill(22, 14, 214, 36, 0xFFD9D2B4);
		text(g, "ЩИТОК · СЕКТОР " + (sector + 1), 30, 19, 1.25f, 0xFF1E1D18, MONO);
		boolean ok = result == 2;
		g.fill(VW - 150, 20, VW - 140, 30, ok ? 0xFF4FD13E : 0xFFC2362B);
		text(g, ok ? "ТОК ПОДАН" : "НЕТ ПИТАНИЯ", VW - 134, 19, 1.05f, 0xFFE8E2C6, MONO);
		// предохранители
		g.fill(22, 50, 140, 180, 0xFF4A4E49);
		text(g, "ПРЕДОХРАНИТЕЛИ", 28, 56, 0.8f, 0xFFE8E2C6, MONO);
		for (int k = 0; k < 3; k++) {
			int x = 32 + k * 36;
			g.fill(x, 74, x + 28, 150, 0xFF1C1D1B);
			g.fill(x + 7, 80, x + 21, 144, 0xFF8B7A3E);
			g.fill(x + 9, 84, x + 19, 140, 0xFFEFE8CC);
			g.fill(x + 12, 96, x + 16, 128, 0xFF5B5141);
			text(g, "F" + (k + 1), x + 7, 156, 0.8f, 0xFFCFC9AD, MONO);
		}
		// рубильники
		for (int k = 0; k < 5; k++) {
			int x = 160 + k * 70, y = 50;
			boolean on = (mask >> k & 1) != 0;
			g.fill(x, y, x + 56, y + 120, 0xFF2B2D2A);
			g.fill(x + 18, y + 10, x + 38, y + 22, 0xFFA17B3A);
			g.fill(x + 18, y + 98, x + 38, y + 110, 0xFFA17B3A);
			g.pose().pushMatrix();
			g.pose().translate(x + 28, y + 104);
			g.pose().rotate(on ? 0f : (float) Math.toRadians(-58));
			g.fill(-4, -90, 4, 0, 0xFFC9CDCA);
			g.fill(-12, -102, 12, -88, 0xFF1B1B19);
			g.pose().popMatrix();
			g.fill(x + 4, y + 126, x + 52, y + 142, 0xFFD9D2B4);
			text(g, values[k] + " А", x + 10, y + 129, 1f, 0xFF1E1D18, MONO);
			text(g, on ? "ВКЛ" : "ВЫКЛ", x + 16, y + 148, 0.8f, on ? 0xFF7FC06A : 0xFF9A9583, MONO);
		}
		// прибор нагрузки
		g.fill(22, 196, 262, 306, 0xFFE7E0C4);
		int cx = 142, cy = 290, r = 80;
		for (int v = 0; v <= 100; v++) {
			double a = Math.toRadians(180 - v * 1.8);
			int px = (int) Math.round(cx + r * Math.cos(a)), py = (int) Math.round(cy - r * Math.sin(a));
			int col = v >= Room4Power.OVERLOAD ? 0xFFC2362B : (v >= zMin && v <= zMax) ? 0xFF4F9A3E : 0xFFB9B190;
			g.fill(px - 3, py - 3, px + 3, py + 3, col);
			if (v % 10 == 0) {
				int tx = (int) Math.round(cx + (r - 16) * Math.cos(a)), ty = (int) Math.round(cy - (r - 16) * Math.sin(a));
				text(g, String.valueOf(v), tx - 5, ty - 3, 0.6f, 0xFF2A281F, MONO);
			}
		}
		int sm = Math.min(105, sum());
		double na = Math.toRadians(180 - sm * 1.8);
		for (int d = 0; d < r - 8; d += 2) {
			int px = (int) Math.round(cx + d * Math.cos(na)), py = (int) Math.round(cy - d * Math.sin(na));
			g.fill(px - 1, py - 1, px + 2, py + 2, 0xFFB8241B);
		}
		g.fill(cx - 6, cy - 6, cx + 6, cy + 6, 0xFF2A281F);
		text(g, "НАГРУЗКА " + sum() + " А", 34, 202, 0.85f, 0xFF2A281F, MONO);
		// подсказка и кнопка
		text(g, "Включай рубильники, пока стрелка", 284, 200, 0.95f, 0xFFF0EAD0, SANS);
		text(g, "не встанет в зелёную зону.", 284, 214, 0.95f, 0xFFF0EAD0, SANS);
		text(g, "Красное — замыкание.", 284, 228, 0.95f, 0xFFF0EAD0, SANS);
		boolean hover = inButton(mx, my);
		g.fill(284, 250, 470, 290, 0xFF5A1612);
		g.fill(286, 250, 468, 286, hover ? 0xFFD0423A : 0xFFB8302A);
		text(g, "ПОДАТЬ ТОК", 318, 261, 1.4f, 0xFFFFF3E0, MONO);
		text(g, "ESC — закрыть", 372, 298, 0.8f, 0xFFCFC9AD, MONO);
		// результат
		if (result == 1) {
			g.fill(0, 0, VW, VH, 0x90C2362B);
			text(g, "КОРОТКОЕ ЗАМЫКАНИЕ", 118, 150, 2f, 0xFFFFFFFF, MONO);
		} else if (result == 2) {
			g.fill(0, 0, VW, VH, 0x704FD13E);
			text(g, "ТОК ПОДАН", 182, 150, 2f, 0xFFFFFFFF, MONO);
		}
		g.pose().popMatrix();
	}

	private boolean inButton(double mx, double my) {
		float s = scale();
		double x = (mx - ox()) / s, y = (my - oy()) / s;
		return x >= 284 && x < 470 && y >= 250 && y < 290;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() == 0 && result == 0) {
			float s = scale();
			double x = (e.x() - ox()) / s, y = (e.y() - oy()) / s;
			for (int k = 0; k < 5; k++) {
				int bx = 160 + k * 70;
				if (x >= bx && x < bx + 56 && y >= 50 && y < 192) {
					mask ^= 1 << k;
					ClientPlayNetworking.send(new HubPayloads.Room4PanelAct(sector, mask, false));
					return true;
				}
			}
			if (inButton(e.x(), e.y())) {
				ClientPlayNetworking.send(new HubPayloads.Room4PanelAct(sector, mask, true));
				return true;
			}
		}
		return super.mouseClicked(e, dbl);
	}

	private void text(GuiGraphics g, String t, float x, float y, float sc, int color, FontDescription f) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(sc, sc);
		g.drawString(font, Component.literal(t).withStyle(st -> st.withFont(f)), 0, 0, color, false);
		g.pose().popMatrix();
	}
}
