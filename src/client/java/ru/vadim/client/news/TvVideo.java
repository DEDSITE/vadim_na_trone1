package ru.vadim.client.news;

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
 * Ролик новостей на экране телека: 740 JPEG-кадров 640×360, 20 кадров/с (assets/vadim_na_trone/tv_news/).
 * Приём как у видео главного меню: кадры читаются и декодируются в фоновом потоке, номер кадра берётся
 * из времени катсцены (оно общее у всех игроков), на поток рендера отдаётся готовый массив пикселей.
 */
public final class TvVideo {
	public static final Identifier TEX = VadimNaTrone.id("dynamic/tv_news");
	public static final int W = 640, H = 360, FRAMES = 740, FPS = 20;

	private static byte[][] data;
	private static DynamicTexture tex;
	private static volatile int[] ready;
	private static volatile boolean running;
	private static volatile boolean loaded;
	private static volatile int shown = -1;
	private static Thread worker;
	private static boolean warned;

	private TvVideo() {}

	/** Старт катсцены: начинаем грузить кадры заранее (до включения телека ~2.5 с). */
	public static void prepare() {
		if (worker != null && worker.isAlive()) return;
		running = true;
		loaded = false;
		ready = null;
		shown = -1;
		worker = new Thread(TvVideo::run, "vadim-tv-news");
		worker.setDaemon(true);
		worker.start();
	}

	/** Конец катсцены: поток останавливается, байты кадров остаются в памяти на случай повтора. */
	public static void release() {
		running = false;
		worker = null;
		loaded = false;
	}

	public static boolean hasFrame() { return tex != null && loaded; }

	private static void run() {
		try {
			if (data == null) {
				byte[][] d = new byte[FRAMES][];
				var rm = Minecraft.getInstance().getResourceManager();
				for (int i = 0; i < FRAMES; i++) {
					Identifier id = VadimNaTrone.id(String.format("tv_news/%04d.jpg", i + 1));
					try (InputStream in = rm.open(id)) { d[i] = in.readAllBytes(); }
				}
				data = d;
				VadimNaTrone.LOG.info("[news] ролик загружен: {} кадров", FRAMES);
			}
			int[] buf = new int[W * H];
			int[] out = new int[W * H];
			while (running) {
				int f = ClientNews.active() ? (int) (ClientNews.videoTime() * FPS) : shown;
				if (f >= 0 && f < FRAMES && f != shown && ready == null) {
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(data[f]));
					if (img == null || img.getWidth() != W || img.getHeight() != H) {
						if (!warned) {
							warned = true;
							VadimNaTrone.LOG.warn("[news] кадр {} не прочитался: {}", f, img == null ? "null" : img.getWidth() + "x" + img.getHeight());
						}
					}
					if (img != null && img.getWidth() == W && img.getHeight() == H) {
						img.getRGB(0, 0, W, H, buf, 0, W);
						for (int i = 0; i < out.length; i++) {
							int c = buf[i];   // ARGB -> ABGR
							out[i] = 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF);
						}
						ready = out;
						shown = f;
					}
				}
				Thread.sleep(4);
			}
		} catch (Throwable t) {
			VadimNaTrone.LOG.warn("Ролик на телеке не запустился: {}", t.toString());
			running = false;
		}
	}

	/** На потоке рендера (из TvRenderer): заливает свежий кадр в текстуру. */
	public static void upload() {
		int[] f = ready;
		if (f == null) return;
		if (tex == null) {
			tex = new DynamicTexture(() -> "vadim tv news", W, H, false) {
				{ sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
						.getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR); }
			};
			Minecraft.getInstance().getTextureManager().register(TEX, tex);
		}
		NativeImage img = tex.getPixels();
		if (img == null) return;
		IntBuffer ib = MemoryUtil.memIntBuffer(img.getPointer(), W * H);
		ib.put(f);
		tex.upload();
		ready = null;
		if (!loaded) VadimNaTrone.LOG.info("[news] телек: кадр залит в текстуру");
		loaded = true;
	}
}
