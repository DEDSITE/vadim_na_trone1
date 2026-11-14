package ru.vadim.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryUtil;
import ru.vadim.VadimNaTrone;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.IntBuffer;

/**
 * Зацикленное видео на весь экран в главном меню (запись Димы: без первых 15 с и последних 20 с):
 * 1230 JPEG-кадров 1280×720, 20 кадров/с, стык склеен кроссфейдом. Пока видео грузится — menu_bg.png (первый кадр).
 * Кадры читаются и декодируются в фоновом потоке, на поток рендера отдаётся только готовый массив пикселей.
 */
public final class MenuVideo {
	public static final Identifier TEX = VadimNaTrone.id("dynamic/menu_video");
	public static final int W = 1280, H = 720, FRAMES = 1230, FPS = 20;

	private static byte[][] data;
	private static DynamicTexture tex;
	private static volatile int[] ready;   // ABGR, готов к заливке
	private static volatile boolean running;
	private static volatile boolean loaded;
	private static volatile boolean paused;
	private static Thread worker;
	private static long startMs;

	private MenuVideo() {}

	/** Запуск при открытии меню (повторный вызов безопасен). */
	public static void start() {
		paused = false;
		if (worker != null) return;
		running = true;
		startMs = System.currentTimeMillis();
		worker = new Thread(MenuVideo::run, "vadim-menu-video");
		worker.setDaemon(true);
		worker.start();
	}

	/** Меню закрыто — поток спит, ничего не декодирует. */
	public static void stop() { paused = true; }

	public static boolean hasFrame() { return tex != null && loaded; }

	private static void run() {
		try {
			if (data == null) {
				byte[][] d = new byte[FRAMES][];
				var rm = Minecraft.getInstance().getResourceManager();
				for (int i = 0; i < FRAMES; i++) {
					Identifier id = VadimNaTrone.id(String.format("menu_video/%04d.jpg", i + 1));
					try (InputStream in = rm.open(id)) { d[i] = in.readAllBytes(); }
				}
				data = d;
			}
			int last = -1;
			int[] buf = new int[W * H];
			int[] out = new int[W * H];   // один буфер: поток рендера копирует его до того, как обнулит ready
			while (running) {
				if (paused) { Thread.sleep(50); continue; }
				int f = (int) (((System.currentTimeMillis() - startMs) * FPS / 1000) % FRAMES);
				if (f != last && ready == null) {
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(data[f]));
					if (img != null && img.getWidth() == W && img.getHeight() == H) {
						img.getRGB(0, 0, W, H, buf, 0, W);
						for (int i = 0; i < out.length; i++) {
							int c = buf[i];   // ARGB -> ABGR
							out[i] = 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF);
						}
						ready = out;
						last = f;
					}
				}
				Thread.sleep(5);
			}
		} catch (Throwable t) {
			VadimNaTrone.LOG.warn("Видео в меню не запустилось: {}", t.toString());
			running = false;
		}
	}

	/** Вызывать на потоке рендера перед отрисовкой: заливает свежий кадр в текстуру. */
	public static void upload() {
		int[] f = ready;
		if (f == null) return;
		if (tex == null) {
			tex = new DynamicTexture(() -> "vadim menu video", W, H, false) {
				{ sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
						.getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR); }   // плавное растяжение 720p
			};
			Minecraft.getInstance().getTextureManager().register(TEX, tex);
		}
		NativeImage img = tex.getPixels();
		if (img == null) return;
		IntBuffer ib = MemoryUtil.memIntBuffer(img.getPointer(), W * H);
		ib.put(f);
		tex.upload();
		ready = null;
		loaded = true;
	}
}
