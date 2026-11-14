package ru.vadim.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ModBlocks;
import ru.vadim.client.news.ClientNews;
import ru.vadim.client.news.TvVideo;

/**
 * Телевизор над баром: корпус 2.4×1.45 блока, тёмный экран с бликом. В катсцене с новостями поверх стекла
 * идёт ролик (TvVideo), кадр выбирается по общему времени катсцены.
 */
public class TvRenderer implements BlockEntityRenderer<ModBlocks.TvEntity, TvRenderer.State> {
	private static final Identifier TEX = VadimNaTrone.id("textures/block/tv.png");
	private static final float W = 2.4f, H = 1.45f;

	public static class State extends BlockEntityRenderState {
		float time;
		boolean video;
	}

	public TvRenderer(BlockEntityRendererProvider.Context ctx) {}

	@Override public boolean shouldRenderOffScreen() { return true; }
	@Override public int getViewDistance() { return 48; }
	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(ModBlocks.TvEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay crumble) {
		BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, crumble);
		s.time = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + pt;
		// катсцена: свежий кадр ролика в текстуру (мы на потоке рендера)
		s.video = ClientNews.tvOn();
		if (s.video) TvVideo.upload();
		s.video = s.video && TvVideo.hasFrame();
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		Direction f = s.blockState.getValue(ModBlocks.Facing.FACING);
		ps.pushPose();
		ps.translate(0.5, 0.5, 0.5);
		ps.mulPose(Axis.YP.rotationDegrees(-switch (f) { case WEST -> 90; case NORTH -> 180; case EAST -> 270; default -> 0; }));
		// задняя стенка корпуса у стены (z = -0.5), экран смотрит на +Z
		float z0 = -0.48f, z1 = -0.34f;
		int light = s.lightCoords;
		// корпус: рамка (u 0..0.5) и экран (u 0.5..1) в одной текстуре tv.png 32×16
		box(out, ps, -W / 2, -H / 2 + 0.25f, z0, W / 2, H / 2 + 0.25f, z1, light);
		screen(out, ps, -W / 2 + 0.08f, -H / 2 + 0.33f, W / 2 - 0.08f, H / 2 + 0.17f, z1 + 0.002f, light, s.time);
		if (s.video) video(out, ps, -W / 2 + 0.08f, -H / 2 + 0.33f, W / 2 - 0.08f, H / 2 + 0.17f, z1 + 0.004f);
		ps.popPose();
	}

	private void box(SubmitNodeCollector out, PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
		out.submitCustomGeometry(ps, RenderTypes.entityCutout(TEX), (p, vc) -> {
			Matrix4f m = p.pose();
			float u0 = 0f, u1 = 0.5f, v0 = 0f, v1 = 1f;
			// перед (рамка)
			v(vc, m, p, x0, y0, z1, u0, v1, light, 0, 0, 1); v(vc, m, p, x1, y0, z1, u1, v1, light, 0, 0, 1);
			v(vc, m, p, x1, y1, z1, u1, v0, light, 0, 0, 1); v(vc, m, p, x0, y1, z1, u0, v0, light, 0, 0, 1);
			// бока, верх, низ
			v(vc, m, p, x0, y0, z0, u0, v1, light, -1, 0, 0); v(vc, m, p, x0, y0, z1, u0 + 0.05f, v1, light, -1, 0, 0);
			v(vc, m, p, x0, y1, z1, u0 + 0.05f, v0, light, -1, 0, 0); v(vc, m, p, x0, y1, z0, u0, v0, light, -1, 0, 0);
			v(vc, m, p, x1, y0, z1, u0, v1, light, 1, 0, 0); v(vc, m, p, x1, y0, z0, u0 + 0.05f, v1, light, 1, 0, 0);
			v(vc, m, p, x1, y1, z0, u0 + 0.05f, v0, light, 1, 0, 0); v(vc, m, p, x1, y1, z1, u0, v0, light, 1, 0, 0);
			v(vc, m, p, x0, y1, z1, u0, v0, light, 0, 1, 0); v(vc, m, p, x1, y1, z1, u1, v0, light, 0, 1, 0);
			v(vc, m, p, x1, y1, z0, u1, v0 + 0.05f, light, 0, 1, 0); v(vc, m, p, x0, y1, z0, u0, v0 + 0.05f, light, 0, 1, 0);
			v(vc, m, p, x0, y0, z0, u0, v1, light, 0, -1, 0); v(vc, m, p, x1, y0, z0, u1, v1, light, 0, -1, 0);
			v(vc, m, p, x1, y0, z1, u1, v1 - 0.05f, light, 0, -1, 0); v(vc, m, p, x0, y0, z1, u0, v1 - 0.05f, light, 0, -1, 0);
		});
	}

	/** Экран. Сейчас выключен: тёмное стекло с лёгким бликом. */
	private void screen(SubmitNodeCollector out, PoseStack ps, float x0, float y0, float x1, float y1, float z, int light, float time) {
		out.submitCustomGeometry(ps, RenderTypes.entityCutout(TEX), (p, vc) -> {
			Matrix4f m = p.pose();
			v(vc, m, p, x0, y0, z, 0.5f, 1f, light, 0, 0, 1); v(vc, m, p, x1, y0, z, 1f, 1f, light, 0, 0, 1);
			v(vc, m, p, x1, y1, z, 1f, 0f, light, 0, 0, 1); v(vc, m, p, x0, y1, z, 0.5f, 0f, light, 0, 0, 1);
		});
	}

	/** Ролик новостей поверх тёмного стекла: светится сам (без освещения), как настоящий экран. */
	private void video(SubmitNodeCollector out, PoseStack ps, float x0, float y0, float x1, float y1, float z) {
		out.submitCustomGeometry(ps, RenderTypes.eyes(TvVideo.TEX), (p, vc) -> {
			Matrix4f m = p.pose();
			int full = 0xF000F0;
			v(vc, m, p, x0, y0, z, 0f, 1f, full, 0, 0, 1); v(vc, m, p, x1, y0, z, 1f, 1f, full, 0, 0, 1);
			v(vc, m, p, x1, y1, z, 1f, 0f, full, 0, 0, 1); v(vc, m, p, x0, y1, z, 0f, 0f, full, 0, 0, 1);
		});
	}

	private static void v(com.mojang.blaze3d.vertex.VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float x, float y, float z,
	                      float u, float vv, int light, float nx, float ny, float nz) {
		vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
	}
}
