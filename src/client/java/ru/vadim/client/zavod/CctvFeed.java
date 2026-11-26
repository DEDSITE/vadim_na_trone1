package ru.vadim.client.zavod;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PerspectiveProjectionMatrixBuffer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import ru.vadim.VadimNaTrone;
import ru.vadim.client.ClientZone;
import ru.vadim.client.mixin.CctvCameraInvoker;
import ru.vadim.client.mixin.CctvGameRendererAccessor;

/**
 * Камера видеонаблюдения над стеной мониторов в цеху (этап 3в): раз в 1/10 с мир рисуется с её точки
 * в маленькую текстуру 192×144, а ЭЛТ (CRT_FEED) показывает её как живую картинку.
 * Голова камеры поворачивается так же, как на сервере (следит за ближайшим игроком), поэтому картинка
 * совпадает с тем, куда она смотрит. Любая ошибка — камера выключается, на экранах остаётся «CAM» с помехами.
 */
public final class CctvFeed {
	public static final Identifier TEX = VadimNaTrone.id("dynamic/cctv");
	/** Шарнир камеры и куда она смотрит без людей — как в ZavodInside.CAMS[1]. */
	private static final Vec3 PIVOT = new Vec3(-16.55, 70.25, -59.55);
	private static final float IDLE = -135f;
	/** Середина стены мониторов: картинку считаем, только когда игрок рядом. */
	private static final Vec3 WALL = new Vec3(-16.5, 65.5, -57.5);
	private static final int W = 192, H = 144;
	private static final long FRAME_NS = 1_000_000_000L / 10;

	private static TextureTarget target;
	private static FeedTexture tex;
	private static PerspectiveProjectionMatrixBuffer proj;
	private static final Camera cam = new Camera();
	private static boolean rendering, broken, has;
	private static long last;
	private static float yaw = IDLE, pitch = -20f, oYaw = IDLE, oPitch = -20f;
	private static int ticks;

	/** Живая картинка выключена (26.11, решение Димы): на мониторах цеха только помехи, мир второй раз не рисуем. */
	public static final boolean ENABLED = false;

	private CctvFeed() {}

	/** Есть свежая картинка с камеры (иначе на экране остаются помехи). */
	public static boolean live() { return has && !broken; }

	/** Цель, в которую сейчас рисуется мир (null — обычный кадр). */
	public static RenderTarget renderingTarget() { return rendering ? target : null; }

	public static boolean rendering() { return rendering; }

	/** Поворот головы камеры — та же логика, что у сервера (Zavod.tick), только по тикам клиента. */
	public static void tick(Minecraft mc) {
		oYaw = yaw;
		oPitch = pitch;
		if (!ClientZone.zavod() || mc.level == null) { has = false; return; }
		ticks++;
		Player best = null;
		double bd = 40 * 40;
		for (Player p : mc.level.players()) {
			if (p.isSpectator() || !ru.vadim.world.Zavod.insideShop(p.getX(), p.getY(), p.getZ())) continue;
			double d = p.distanceToSqr(PIVOT);
			if (d < bd) { bd = d; best = p; }
		}
		float wantYaw, wantPitch;
		if (best != null) {
			Vec3 to = best.getEyePosition().subtract(PIVOT);
			wantYaw = (float) Math.toDegrees(Math.atan2(-to.x, -to.z));
			wantPitch = (float) Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		} else {
			wantYaw = IDLE + 55f * Mth.sin(ticks / 180f);
			wantPitch = -18f;
		}
		yaw += Mth.clamp(Mth.wrapDegrees(wantYaw - yaw), -3.5f, 3.5f);
		pitch += Mth.clamp(Mth.clamp(wantPitch, -60f, 15f) - pitch, -3.5f, 3.5f);
	}

	/** Из GameRenderer.renderLevel (до основного кадра): при необходимости нарисовать кадр камеры. */
	public static void render(DeltaTracker dt) {
		if (!ENABLED || broken || rendering) return;
		Minecraft mc = Minecraft.getInstance();
		if (!ClientZone.zavod() || mc.level == null || mc.player == null
				|| mc.player.distanceToSqr(WALL) > 22 * 22
				|| mc.options.improvedTransparency().get()) {
			has = false;
			return;
		}
		long now = System.nanoTime();
		if (has && now - last < FRAME_NS) return;
		last = now;
		try {
			if (target == null) {
				target = new TextureTarget("vadim cctv", W, H, true);
				proj = new PerspectiveProjectionMatrixBuffer("vadim cctv");
				tex = new FeedTexture();
				mc.getTextureManager().register(TEX, tex);
			}
			float pt = dt.getGameTimeDeltaPartialTick(false);
			float y = Mth.rotLerp(pt, oYaw, yaw), p = Mth.lerp(pt, oPitch, pitch);
			// у модели камеры объектив смотрит в −Z после rotateY(yaw)·rotateX(pitch) → углы Minecraft:
			float mcYaw = 180f - y, mcPitch = -p;
			cam.setup(mc.level, mc.player, true, false, pt);
			CctvCameraInvoker ci = (CctvCameraInvoker) cam;
			ci.vadim$setRotation(mcYaw, mcPitch);
			ci.vadim$setPosition(PIVOT.add(Vec3.directionFromRotation(mcPitch, mcYaw).scale(0.85)));
			cam.tick();   // небо/свет у камеры считаются от её позиции (attributeProbe)

			int rd = mc.options.getEffectiveRenderDistance();
			Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(108), (float) W / H, 0.05f, Math.max(64f, rd * 16f));
			Matrix4f view = new Matrix4f().rotation(cam.rotation().conjugate(new Quaternionf()));
			CctvGameRendererAccessor gr = (CctvGameRendererAccessor) mc.gameRenderer;
			// позиция камеры для сущностей берётся из общего состояния кадра (его заполняет GameRenderer
			// для главной камеры) — подменяем на время своего кадра, иначе игроки рисуются «вокруг объектива»
			net.minecraft.client.renderer.state.CameraRenderState cs = gr.vadim$levelState().cameraRenderState;
			Vec3 sPos = cs.pos, sEnt = cs.entityPos;
			net.minecraft.core.BlockPos sBlock = cs.blockPos;
			Quaternionf sRot = cs.orientation;
			boolean sInit = cs.initialized;
			cs.pos = cam.position();
			cs.blockPos = cam.blockPosition();
			cs.entityPos = mc.player.getPosition(pt);
			cs.orientation = new Quaternionf(cam.rotation());
			cs.initialized = true;
			rendering = true;
			// моды анимаций (Not Enough Animations и т.п.) прячут своего игрока, если вид «от первого лица» —
			// на время кадра камеры считаем, что вид от третьего лица
			net.minecraft.client.CameraType sType = mc.options.getCameraType();
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			RenderSystem.backupProjectionMatrix();
			try {
				RenderSystem.setProjectionMatrix(proj.getBuffer(projection), ProjectionType.PERSPECTIVE);
				Vector4f fogColor = gr.vadim$fog().setupFog(cam, rd, dt, 0f, mc.level);
				mc.levelRenderer.renderLevel(gr.vadim$pool(), dt, false, cam, view, projection, projection,
						gr.vadim$fog().getBuffer(FogRenderer.FogMode.WORLD), fogColor, true);
				// полупрозрачные модели (игроки рисуются translucent) сбрасываются из общего буфера позже,
				// уже в основном кадре — дорисовываем их сейчас, в кадр камеры, с её поворотом
				org.joml.Matrix4fStack mv = RenderSystem.getModelViewStack();
				mv.pushMatrix();
				mv.mul(view);
				try {
					mc.renderBuffers().bufferSource().endBatch();
				} finally {
					mv.popMatrix();
				}
				lastEntities = gr.vadim$levelState().entityRenderStates.size();
			} finally {
				rendering = false;
				mc.options.setCameraType(sType);
				RenderSystem.restoreProjectionMatrix();
				cs.pos = sPos;
				cs.entityPos = sEnt;
				cs.blockPos = sBlock;
				cs.orientation = sRot;
				cs.initialized = sInit;
			}
			tex.show(target);
			has = true;
			debugDump(mc);
		} catch (Throwable t) {
			rendering = false;
			broken = true;
			has = false;
			VadimNaTrone.LOG.warn("Камера видеонаблюдения выключена: {}", t.toString());
		}
	}

	private static int dumps;
	private static int lastEntities;
	private static long lastDump;

	/** Отладка: пару раз за запуск сохраняем кадр камеры в cctv_debug_N.png и пишем в лог среднюю яркость. */
	private static void debugDump(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (dumps >= 6 || now - lastDump < 4000) return;
		lastDump = now;
		int n = ++dumps;
		VadimNaTrone.LOG.info("[cctv] кадр {}: камера {} yaw {} pitch {}, сущностей в кадре {}", n, cam.position(), cam.yRot(), cam.xRot(), lastEntities);
		net.minecraft.client.Screenshot.takeScreenshot(target, img -> {
			try {
				long sum = 0;
				int w = img.getWidth(), h = img.getHeight();
				for (int y = 0; y < h; y += 2) for (int x = 0; x < w; x += 2) {
					int c = img.getPixel(x, y);
					sum += (c & 0xFF) + (c >> 8 & 0xFF) + (c >> 16 & 0xFF);
				}
				VadimNaTrone.LOG.info("[cctv] кадр {}: {}x{}, средняя яркость {}", n, w, h, sum / 3 / Math.max(1, (w / 2) * (h / 2)));
				img.writeToFile(mc.gameDirectory.toPath().resolve("cctv_debug_" + n + ".png"));
			} catch (Exception e) {
				VadimNaTrone.LOG.warn("[cctv] не сохранил кадр: {}", e.toString());
			} finally {
				img.close();
			}
		});
	}

	/** Текстура-обёртка над цветом нашей цели (саму цель не закрываем вместе с текстурой). */
	private static final class FeedTexture extends AbstractTexture {
		void show(RenderTarget t) {
			texture = t.getColorTexture();
			textureView = t.getColorTextureView();
			sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		}

		@Override public void close() {}
	}
}
