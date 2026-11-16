package ru.vadim.client.room;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import ru.vadim.VadimNaTrone;
import ru.vadim.world.Room1;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.IntBuffer;

/**
 * Мультик Вадима на мониторе комнаты 1 (этап 4б): 1040 JPEG-кадров 640×400, 20 к/с
 * (assets/vadim_na_trone/room1_video/, рендер Remotion _video/src/Vadim.tsx).
 * Кадр = тик вступления (Room1Client.introTime), т.е. phaseTick сервера — голос из монитора идёт по тем же тикам.
 * Кадры декодируются в фоновом потоке, рисуются прямоугольником на стекле монитора (entitySolid, полный свет).
 */
public final class Room1Video {
	public static final Identifier TEX = VadimNaTrone.id("dynamic/room1_video");
	public static final int W = 640, H = 400, FRAMES = 1040;

	/** Стекло монитора: item_display room1_monitor (модель 16×10 px на z 7.5..8.5, масштаб 2.3), без рамки. */
	private static final double HX = 1.088, HY = 0.657, Z = 0.074;

	private static byte[][] data;
	private static DynamicTexture tex;
	private static volatile int[] ready;
	private static volatile int want = -1, shown = -1;
	private static volatile boolean running;
	private static Thread worker;
	private static volatile boolean broken;
	private static boolean warned, loggedFirst;

	private Room1Video() {}

	/** Каждый тик клиента: в комнатах держим поток с кадрами, вне комнат — выключаем. */
	public static void tick(Minecraft mc) {
		boolean need = ru.vadim.client.ClientZone.rooms();
		if (need && !broken && (worker == null || !worker.isAlive())) {
			running = true;
			worker = new Thread(Room1Video::run, "vadim-room1-video");
			worker.setDaemon(true);
			worker.start();
		} else if (!need && worker != null) {
			running = false;
			worker = null;
			shown = -1;
		}
	}

	private static void run() {
		try {
			if (data == null) {
				byte[][] d = new byte[FRAMES][];
				var rm = Minecraft.getInstance().getResourceManager();
				for (int i = 0; i < FRAMES; i++) {
					try (InputStream in = rm.open(VadimNaTrone.id(String.format("room1_video/%04d.jpg", i + 1)))) { d[i] = in.readAllBytes(); }
				}
				data = d;
				VadimNaTrone.LOG.info("[room1] мультик загружен: {} кадров", FRAMES);
			}
			int[] buf = new int[W * H];
			while (running) {
				int f = want;
				if (f >= 0 && f < FRAMES && f != shown && ready == null) {
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(data[f]));
					if (img != null && img.getWidth() == W && img.getHeight() == H) {
						int[] out = new int[W * H];
						img.getRGB(0, 0, W, H, buf, 0, W);
						for (int i = 0; i < out.length; i++) {
							int c = buf[i];   // ARGB -> ABGR
							out[i] = 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF);
						}
						ready = out;
						shown = f;
					} else if (!warned) {
						warned = true;
						VadimNaTrone.LOG.warn("[room1] кадр мультика {} не прочитался", f);
					}
				}
				Thread.sleep(4);
			}
		} catch (Throwable t) {
			VadimNaTrone.LOG.warn("[room1] мультик не запустился: {}", t.toString());
			running = false;
			broken = true;   // не перезапускать каждый тик (нет кадров и т.п.)
		}
	}

	/** На потоке рендера: свежий кадр в текстуру. */
	private static boolean upload() {
		int[] f = ready;
		if (f != null) {
			if (tex == null) {
				tex = new DynamicTexture(() -> "vadim room1 video", W, H, false) {
					{ sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
							.getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR); }
				};
				Minecraft.getInstance().getTextureManager().register(TEX, tex);
			}
			NativeImage img = tex.getPixels();
			if (img != null) {
				IntBuffer ib = MemoryUtil.memIntBuffer(img.getPointer(), W * H);
				ib.put(f);
				tex.upload();
			}
			ready = null;
			if (!loggedFirst) { loggedFirst = true; VadimNaTrone.LOG.info("[room1] мультик: первый кадр на мониторе"); }
		}
		return tex != null && shown >= 0;
	}

	/** WorldRenderEvents.AFTER_ENTITIES: мультик поверх стекла монитора, пока идёт вступление. */
	public static void render(WorldRenderContext ctx) {
		float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float t = Room1Client.introTime(pt);
		if (t < 0) { want = -1; return; }
		want = Math.min(FRAMES - 1, (int) t);
		if (!upload()) return;
		Vec3 cam = ctx.worldState().cameraRenderState.pos;
		PoseStack ps = ctx.matrices();
		ps.pushPose();
		ps.translate(Room1.MONITOR.x - cam.x, Room1.MONITOR.y - cam.y, Room1.MONITOR.z - cam.z);
		ctx.commandQueue().submitCustomGeometry(ps, RenderTypes.entitySolid(TEX), (p, vc) -> {
			Matrix4f m = p.pose();
			int full = 0xF000F0;
			// экран смотрит на +Z (в комнату): обход против часовой со стороны зрителя
			vc.addVertex(m, (float) -HX, (float) -HY, (float) Z).setColor(255, 255, 255, 255).setUv(0f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(p, 0, 0, 1);
			vc.addVertex(m, (float) HX, (float) -HY, (float) Z).setColor(255, 255, 255, 255).setUv(1f, 1f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(p, 0, 0, 1);
			vc.addVertex(m, (float) HX, (float) HY, (float) Z).setColor(255, 255, 255, 255).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(p, 0, 0, 1);
			vc.addVertex(m, (float) -HX, (float) HY, (float) Z).setColor(255, 255, 255, 255).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(full).setNormal(p, 0, 0, 1);
		});
		ps.popPose();
	}
}
