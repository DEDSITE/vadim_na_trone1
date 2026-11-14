package ru.vadim.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Где светят фонарики (до 5 штук) и фонари. Данные уходят:
 *  - в шейдеры мира — пятно света на поверхностях. В 1.21.11 они едут хвостом UBO «Fog»
 *    (см. FogRendererMixin и assets/minecraft/shaders/include/fog.glsl);
 *  - в пост-эффект — видимый луч в тумане (HorrorPost, свой UBO).
 */
public final class FlashlightLights {
	/** 0–4 — фары машин (CarLights), 5–8 — лампы и фонари (LampLights). */
	public static final int MAX = 9;
	public static final int PLAYERS = 5;
	/** Хвост UBO «Fog»: vec4 StripFlashA[9]; vec4 StripFlashB[9]. */
	public static final int EXTRA = MAX * 16 * 2;

	static final float[][] A = new float[MAX][4];
	static final float[][] B = new float[MAX][4];
	static final Matrix4f INV_VIEW_PROJ = new Matrix4f();

	private static TextureTarget depthCopy;
	/** Копия тумана для руки в первом лице: тот же туман, но без фонарей. */
	private static GpuBuffer handFog;
	private static final Vector4f fogColor = new Vector4f();
	private static final float[] fogVals = new float[6];

	private FlashlightLights() {}

	/** Начало кадра (FogRenderer.setupFog): собрать источники света. 0–4 — фары машин, 5–8 — лампы и фонари. */
	public static void update(Camera camera, float pt) {
		Vec3 cam = camera.position();
		for (int i = 0; i < MAX; i++) A[i][3] = 0f;
		if (!ru.vadim.client.ClientAtmo.active()) return;
		CarLights.fill(A, B, 0, PLAYERS, cam, pt);
		LampLights.fill(A, B, PLAYERS, cam);
	}

	/** Смещение хвоста в UBO «Fog» (std140: массив vec4 выравнивается на 16). */
	public static int offset() {
		return (FogRenderer.FOG_UBO_SIZE + 15) & ~15;
	}

	public static int fogSize() {
		return offset() + EXTRA;
	}

	/** Записать фонари в буфер тумана (абсолютная позиция начала блока — pos). off = все погашены. */
	public static void write(ByteBuffer raw, int pos, boolean off) {
		ByteBuffer buf = raw.duplicate().order(java.nio.ByteOrder.nativeOrder());
		int o = pos + offset();
		for (int i = 0; i < MAX; i++) {
			buf.putFloat(o, A[i][0]); buf.putFloat(o + 4, A[i][1]); buf.putFloat(o + 8, A[i][2]); buf.putFloat(o + 12, off ? 0f : A[i][3]);
			o += 16;
		}
		for (int i = 0; i < MAX; i++) {
			buf.putFloat(o, B[i][0]); buf.putFloat(o + 4, B[i][1]); buf.putFloat(o + 8, B[i][2]); buf.putFloat(o + 12, B[i][3]);
			o += 16;
		}
	}

	/** Обнулить хвост (буфер «без тумана» для интерфейса). */
	public static void zero(ByteBuffer raw, int pos) {
		ByteBuffer buf = raw.duplicate().order(java.nio.ByteOrder.nativeOrder());
		int o = pos + offset();
		for (int i = 0; i < EXTRA; i += 4) buf.putFloat(o + i, 0f);
		// буфер «без тумана» создаётся по flip() — двигаем позицию за хвост, иначе он короче нашего размера
		// и FogRenderer.getBuffer падает на slice(0, fogSize)
		raw.position(Math.max(raw.position(), pos + fogSize()));
	}

	/** Запомнить, какой туман записан в мир в этом кадре (для копии под руку). */
	public static void rememberFog(Vector4f color, float envStart, float envEnd, float rdStart, float rdEnd, float skyEnd, float cloudEnd) {
		fogColor.set(color);
		fogVals[0] = envStart; fogVals[1] = envEnd; fogVals[2] = rdStart; fogVals[3] = rdEnd; fogVals[4] = skyEnd; fogVals[5] = cloudEnd;
	}

	/** Перед рукой в первом лице: подменить туман на копию без фонарей (рука в другой системе координат). */
	public static void beforeHand() {
		int size = fogSize();
		if (handFog == null || handFog.isClosed()) {
			handFog = RenderSystem.getDevice().createBuffer(() -> "vadim hand fog", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, size);
		}
		ByteBuffer b = MemoryUtil.memCalloc(size);
		try {
			b.putFloat(0, fogColor.x); b.putFloat(4, fogColor.y); b.putFloat(8, fogColor.z); b.putFloat(12, fogColor.w);
			for (int i = 0; i < 6; i++) b.putFloat(16 + i * 4, fogVals[i]);
			write(b, 0, true);
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(handFog.slice(), b);
		} finally {
			MemoryUtil.memFree(b);
		}
		RenderSystem.setShaderFog(handFog.slice(0, size));
	}

	/** Матрицы кадра (LevelRenderer.renderLevel): для лучей в тумане. */
	public static void matrices(Matrix4f view, Matrix4f proj) {
		INV_VIEW_PROJ.set(proj).mul(view).invert();
	}

	/** После мира, до руки: сохранить глубину — по ней пост-эффект строит лучи в тумане. */
	public static void captureDepth() {
		if (!ru.vadim.client.ClientAtmo.active()) return;
		RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
		if (depthCopy == null) {
			depthCopy = new TextureTarget("vadim depth copy", main.width, main.height, true);
		} else if (depthCopy.width != main.width || depthCopy.height != main.height) {
			depthCopy.resize(main.width, main.height);
		}
		depthCopy.copyDepthFrom(main);
	}

	static TextureTarget depth() {
		return depthCopy;
	}

	/** Данные для UBO пост-эффекта «StripPost» (std140). */
	static void writePost(ByteBuffer b, float darkness, float chase, float nightCam, float time, float w, float h) {
		int o = 0;
		float[] m = new float[16];
		INV_VIEW_PROJ.get(m);
		for (int i = 0; i < 16; i++) b.putFloat(o + i * 4, m[i]);
		o += 64;
		for (int i = 0; i < MAX; i++) {
			b.putFloat(o, A[i][0]); b.putFloat(o + 4, A[i][1]); b.putFloat(o + 8, A[i][2]); b.putFloat(o + 12, A[i][3]);
			o += 16;
		}
		for (int i = 0; i < MAX; i++) {
			b.putFloat(o, B[i][0]); b.putFloat(o + 4, B[i][1]); b.putFloat(o + 8, B[i][2]); b.putFloat(o + 12, B[i][3]);
			o += 16;
		}
		b.putFloat(o, darkness); b.putFloat(o + 4, chase); b.putFloat(o + 8, nightCam); b.putFloat(o + 12, time);
		o += 16;
		b.putFloat(o, w); b.putFloat(o + 4, h);
	}

	static final int POST_SIZE = 64 + MAX * 32 + 16 + 16;
}
