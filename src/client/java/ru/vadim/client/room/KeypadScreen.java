package ru.vadim.client.room;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import org.lwjgl.glfw.GLFW;
import ru.vadim.VadimNaTrone;
import ru.vadim.net.HubPayloads;

/**
 * Кодовый замок кладовки (комната 1). Панель — картинка из макета Claude Design «Комната 1 — ассеты» (артборд
 * «Кодовый замок», 360×560 в 2×). Цифры — мышью по клавишам или с клавиатуры; Backspace/C — стереть, Enter/OK — проверить.
 */
public final class KeypadScreen extends Screen {
	private static final Identifier PAD = VadimNaTrone.id("textures/gui/room1/keypad.png");
	private static final int TW = 720, TH = 1120, LW = 360, LH = 560;
	private static final FontDescription MONO = new FontDescription.Resource(VadimNaTrone.id("pt_mono"));
	/** Клавиши в координатах макета 360×560: подпись, x, y, w, h. */
	private static final String[] KEYS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "OK"};
	private static final int[][] RECT = {{30, 143}, {136, 143}, {242, 143}, {30, 231}, {136, 231}, {242, 231}, {30, 319}, {136, 319}, {242, 319},
			{30, 407}, {136, 407}, {242, 407}};
	private static final int KW = 88, KH = 72;
	private static final int[] DISPLAY = {31, 31, 298, 90};

	private static KeypadScreen open;
	private String typed = "";
	private int pressedKey = -1, pressedFor = 0;
	private int result = 0, resultFor = 0;
	private boolean waiting = false;

	public KeypadScreen() {
		super(Component.literal("Кодовый замок"));
		open = this;
	}

	public static void onResult(HubPayloads.Room1Keypad p) {
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (p.result() == 0) { mc.setScreen(new KeypadScreen()); return; }
		if (open != null && mc.screen == open) {
			open.result = p.result();
			open.resultFor = p.result() == 1 ? 18 : 14;
			open.waiting = false;
		}
	}

	@Override public boolean isPauseScreen() { return false; }

	@Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

	@Override
	public void removed() {
		if (open == this) open = null;
	}

	private float scale() { return Math.min((height - 24f) / LH, (width - 20f) / LW); }

	private int ox() { return Math.round((width - LW * scale()) / 2f); }

	private int oy() { return Math.max(4, Math.round((height - 16 - LH * scale()) / 2f)); }

	@Override
	public void tick() {
		if (pressedFor > 0) pressedFor--;
		if (resultFor > 0 && --resultFor == 0) {
			if (result == 1) onClose();
			else { typed = ""; result = 0; }
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		g.fill(0, 0, width, height, 0xB8060708);
		float s = scale();
		int x0 = ox(), y0 = oy();
		g.blit(RenderPipelines.GUI_TEXTURED, PAD, x0, y0, 0f, 0f, Math.round(LW * s), Math.round(LH * s), TW, TH, TW, TH);
		// подсветка клавиш: наведение и нажатие
		for (int i = 0; i < KEYS.length; i++) {
			int kx = x0 + Math.round(RECT[i][0] * s), ky = y0 + Math.round(RECT[i][1] * s);
			int kw = Math.round(KW * s), kh = Math.round(KH * s);
			boolean hover = mx >= kx && mx < kx + kw && my >= ky && my < ky + kh;
			if (i == pressedKey && pressedFor > 0) g.fill(kx, ky, kx + kw, ky + kh, 0x66000000);
			else if (hover) g.fill(kx, ky, kx + kw, ky + kh, 0x1EFFFFFF);
		}
		// табло: набранные цифры, «_» на пустых местах; после ответа — зелёное/красное
		int dx = x0 + Math.round(DISPLAY[0] * s), dy = y0 + Math.round(DISPLAY[1] * s);
		int dw = Math.round(DISPLAY[2] * s), dh = Math.round(DISPLAY[3] * s);
		if (result == 1) g.fill(dx, dy, dx + dw, dy + dh, 0x5530C040);
		if (result == 2 && (resultFor / 3) % 2 == 0) g.fill(dx, dy, dx + dw, dy + dh, 0x66FF2A1F);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			if (i > 0) sb.append(' ');
			sb.append(i < typed.length() ? typed.charAt(i) : '_');
		}
		String shown = result == 1 ? "ОТКРЫТО" : result == 2 ? "НЕВЕРНО" : sb.toString();
		Component c = Component.literal(shown).withStyle(st -> st.withFont(MONO));
		float ts = dh * 0.55f / 9f;
		int col = result == 1 ? 0xFF7FD36A : 0xFFFF3B2E;
		g.pose().pushMatrix();
		g.pose().translate(dx + dw / 2f - font.width(c) * ts / 2f, dy + dh / 2f - 4.5f * ts);
		g.pose().scale(ts, ts);
		g.drawString(font, c, 0, 0, col, false);
		g.pose().popMatrix();
	}

	private void press(int i) {
		if (waiting || resultFor > 0) return;
		pressedKey = i;
		pressedFor = 4;
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.STONE_BUTTON_CLICK_ON, 1.6f, 0.4f));
		String k = KEYS[i];
		if (k.equals("C")) typed = typed.isEmpty() ? "" : typed.substring(0, typed.length() - 1);
		else if (k.equals("OK")) submit();
		else if (typed.length() < 4) typed += k;
	}

	private void submit() {
		if (typed.length() < 4) return;
		waiting = true;
		ClientPlayNetworking.send(new HubPayloads.Room1Code(typed));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() == 0) {
			float s = scale();
			int x0 = ox(), y0 = oy();
			for (int i = 0; i < KEYS.length; i++) {
				int kx = x0 + Math.round(RECT[i][0] * s), ky = y0 + Math.round(RECT[i][1] * s);
				if (e.x() >= kx && e.x() < kx + KW * s && e.y() >= ky && e.y() < ky + KH * s) { press(i); return true; }
			}
		}
		return super.mouseClicked(e, dbl);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		int k = e.key();
		if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9) { press(indexOf(String.valueOf(k - GLFW.GLFW_KEY_0))); return true; }
		if (k >= GLFW.GLFW_KEY_KP_0 && k <= GLFW.GLFW_KEY_KP_9) { press(indexOf(String.valueOf(k - GLFW.GLFW_KEY_KP_0))); return true; }
		if (k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) { press(9); return true; }
		if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) { press(11); return true; }
		return super.keyPressed(e);
	}

	private static int indexOf(String k) {
		for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(k)) return i;
		return 0;
	}
}
