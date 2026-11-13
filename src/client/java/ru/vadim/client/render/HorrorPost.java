package ru.vadim.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import org.lwjgl.system.MemoryUtil;
import ru.vadim.VadimNaTrone;
import ru.vadim.client.ClientAtmo;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

/**
 * Наш «шейдер» поверх кадра: цветокор, виньетка, зерно, эффект погони, лучи фонарей в тумане.
 * 1.21.11: свой RenderPipeline (stripuhoed:core/horror) вместо PostChain — данные идут своим UBO.
 * Если что-то пошло не так — тихо отключается, игра не падает.
 */
public final class HorrorPost {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder()
			.withLocation(VadimNaTrone.id("pipeline/horror"))
			.withVertexShader("core/screenquad")
			.withFragmentShader(VadimNaTrone.id("core/horror"))
			.withSampler("InSampler")
			.withSampler("DepthSampler")
			.withUniform("StripPost", UniformType.UNIFORM_BUFFER)
			.withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withoutBlend()
			.build());

	private static TextureTarget swap;
	private static GpuBuffer ubo;
	private static boolean failed = false;
	private static float time;

	private HorrorPost() {}

	/** Регистрация конвейера (статический инициализатор) — вызвать из onInitializeClient. */
	public static void init() {}

	public static void render(float deltaTicks) {
		if (failed || !ClientAtmo.active()) return;
		Minecraft mc = Minecraft.getInstance();
		try {
			RenderTarget main = mc.getMainRenderTarget();
			int w = main.width, h = main.height;
			if (swap == null) swap = new TextureTarget("vadim horror swap", w, h, false);
			else if (swap.width != w || swap.height != h) swap.resize(w, h);
			if (ubo == null || ubo.isClosed()) {
				ubo = RenderSystem.getDevice().createBuffer(() -> "vadim horror", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, FlashlightLights.POST_SIZE);
			}
			time += deltaTicks;
			while (time > 20f) time -= 20f;

			CommandEncoder enc = RenderSystem.getDevice().createCommandEncoder();
			ByteBuffer b = MemoryUtil.memCalloc(FlashlightLights.POST_SIZE);
			try {
				FlashlightLights.writePost(b, ClientAtmo.darkness(), ClientAtmo.chase(), ClientAtmo.cozy(), time / 20f, w, h);
				enc.writeToBuffer(ubo.slice(), b);
			} finally {
				MemoryUtil.memFree(b);
			}
			enc.copyTextureToTexture(main.getColorTexture(), swap.getColorTexture(), 0, 0, 0, 0, 0, w, h);

			TextureTarget depth = FlashlightLights.depth();
			var depthView = depth != null && depth.width == w && depth.height == h ? depth.getDepthTextureView() : main.getDepthTextureView();
			try (RenderPass pass = enc.createRenderPass(() -> "vadim horror", main.getColorTextureView(), OptionalInt.empty())) {
				pass.setPipeline(PIPELINE);
				pass.bindTexture("InSampler", swap.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
				pass.bindTexture("DepthSampler", depthView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
				pass.setUniform("StripPost", ubo);
				pass.draw(0, 3);
			}
		} catch (Exception e) {
			failed = true;
			VadimNaTrone.LOG.error("Пост-эффект отключён из-за ошибки", e);
		}
	}
}
