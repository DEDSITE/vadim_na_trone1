package ru.vadim.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import ru.vadim.block.ZavodBlocks;
import ru.vadim.client.zavod.CctvFeed;

/** ЭЛТ с камерой: поверх стекла — живая картинка с камеры видеонаблюдения (CctvFeed). */
public class CrtFeedRenderer implements BlockEntityRenderer<ZavodBlocks.CrtFeedEntity, CrtFeedRenderer.State> {
	private static final Identifier DARK = VadimNaTrone.id("textures/block/tv.png");

	public static class State extends BlockEntityRenderState {
		boolean live;
	}

	public CrtFeedRenderer(BlockEntityRendererProvider.Context ctx) {}

	@Override public State createRenderState() { return new State(); }
	@Override public int getViewDistance() { return 32; }

	@Override
	public void extractRenderState(ZavodBlocks.CrtFeedEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay crumble) {
		BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, crumble);
		// в кадре самой камеры экран не рисуем — иначе «туннель» из картинки в картинке
		s.live = CctvFeed.live() && !CctvFeed.rendering();
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		if (!s.live) return;
		Direction f = s.blockState.getValue(ZavodBlocks.Deco.FACING);
		ps.pushPose();
		ps.translate(0.5, 0.5, 0.5);
		ps.mulPose(Axis.YP.rotationDegrees(switch (f) { case EAST -> -90; case SOUTH -> -180; case WEST -> -270; default -> 0; }));
		// экран модели (смотрит на −Z): x 2..14, y 1..13 px, стекло на z = 1.4 px
		float x0 = 2 / 16f - 0.5f, x1 = 14 / 16f - 0.5f, y0 = 1 / 16f - 0.5f, y1 = 13 / 16f - 0.5f;
		float z = 1.4f / 16f - 0.5f;
		out.submitCustomGeometry(ps, RenderTypes.entityCutout(DARK), (p, vc) ->
				quad(vc, p, x0, y0, x1, y1, z - 0.003f, 0.5f, 1f, 1f, 0f, 0xF000F0, 40, 40, 40));
		// картинка 4:3 на квадратном экране — берём середину; у текстуры цели низ кадра в v = 0
		// непрозрачный слой (eyes складывается по альфе, а у кадра мира альфа бывает нулевой → было чёрное)
		out.submitCustomGeometry(ps, RenderTypes.entitySolid(CctvFeed.TEX), (p, vc) ->
				quad(vc, p, x0, y0, x1, y1, z - 0.006f, 0.875f, 0f, 0.125f, 1f, 0xF000F0, 205, 230, 210));
		ps.popPose();
	}

	/** Квад лицом к −Z (и обратная сторона на всякий случай). uL/uR — u у левого/правого края, vB/vT — v снизу/сверху. */
	private static void quad(VertexConsumer vc, PoseStack.Pose p, float x0, float y0, float x1, float y1, float z,
	                         float uL, float vB, float uR, float vT, int light, int r, int g, int b) {
		Matrix4f m = p.pose();
		// зритель смотрит с −Z на +Z: его «лево» — это +X
		v(vc, m, p, x1, y0, z, uL, vB, light, r, g, b); v(vc, m, p, x0, y0, z, uR, vB, light, r, g, b);
		v(vc, m, p, x0, y1, z, uR, vT, light, r, g, b); v(vc, m, p, x1, y1, z, uL, vT, light, r, g, b);
		v(vc, m, p, x1, y1, z, uL, vT, light, r, g, b); v(vc, m, p, x0, y1, z, uR, vT, light, r, g, b);
		v(vc, m, p, x0, y0, z, uR, vB, light, r, g, b); v(vc, m, p, x1, y0, z, uL, vB, light, r, g, b);
	}

	private static void v(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float x, float y, float z, float u, float vv, int light,
	                      int r, int g, int b) {
		vc.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(u, vv).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, -1);
	}
}
