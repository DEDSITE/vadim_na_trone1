package ru.vadim.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * Кадр для главного меню: сервер ставит игрока в точку съёмки (/vadim menushot), клиент прячет
 * интерфейс, ждёт, пока прогрузится свет, и сохраняет скриншот в run/screenshots (vadim_menu_*.png).
 */
public final class MenuShot {
	private static int wait = -1;
	private static boolean hadGui;

	private MenuShot() {}

	public static void start() {
		Minecraft mc = Minecraft.getInstance();
		hadGui = mc.options.hideGui;
		mc.options.hideGui = true;
		wait = 60;
	}

	public static void tick(Minecraft mc) {
		if (wait < 0) return;
		if (--wait > 0) return;
		wait = -1;
		Screenshot.grab(mc.gameDirectory, "vadim_menu_" + System.currentTimeMillis() + ".png", mc.getMainRenderTarget(), 1,
				msg -> mc.execute(() -> {
					mc.options.hideGui = hadGui;
					if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Кадр для меню снят ✔"), false);
				}));
	}
}
