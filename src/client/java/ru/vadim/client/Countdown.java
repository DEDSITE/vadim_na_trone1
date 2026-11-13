package ru.vadim.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Крупный отсчёт 3-2-1 посреди экрана перед стартом. */
public final class Countdown {
	private static int n = -1;
	private static int age = 0;

	private Countdown() {}

	public static void show(int value) {
		n = value;
		age = 0;
	}

	public static void tick() {
		if (n >= 0 && ++age > 22) n = -1;
	}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		if (n < 0) return;
		Minecraft mc = Minecraft.getInstance();
		String s = n > 0 ? String.valueOf(n) : "ИГРА НАЧИНАЕТСЯ";
		float a = 1f - Math.max(0, age - 12) / 10f;
		int alpha = (int) (Math.max(0.05f, a) * 255) << 24;
		g.pose().pushMatrix();
		g.pose().translate(g.guiWidth() / 2f, g.guiHeight() / 2f - 20);
		float sc = n > 0 ? 5f : 2.5f;
		g.pose().scale(sc, sc);
		g.drawCenteredString(mc.font, Component.literal(s), 0, -4, alpha | 0xE8C77A);
		g.pose().popMatrix();
	}
}
