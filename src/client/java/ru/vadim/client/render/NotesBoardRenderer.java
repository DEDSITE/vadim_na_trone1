package ru.vadim.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ModBlocks;

import java.util.List;

/**
 * Стенд записок: бумажки с цитатами Короля нарисованы прямо на пробковой доске — читаются без клика, как картины.
 * Сетка 4×4, сверху табличка «ЗАПИСКИ КОРОЛЯ n/16». Координаты — в пикселях щита (64 × 38, лицо на юг при facing=south).
 */
public class NotesBoardRenderer implements BlockEntityRenderer<ModBlocks.NotesEntity, NotesBoardRenderer.State> {
	private static final Identifier PAPER = VadimNaTrone.id("textures/block/board/paper.png");
	private final Font font;

	public static class State extends BlockEntityRenderState {
		int count;
		String[] notes = new String[16];
	}

	public NotesBoardRenderer(BlockEntityRendererProvider.Context ctx) { this.font = ctx.font(); }

	@Override public boolean shouldRenderOffScreen() { return true; }
	@Override public int getViewDistance() { return 32; }
	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(ModBlocks.NotesEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay crumble) {
		BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, crumble);
		s.count = be.count;
		for (int i = 0; i < 16 && i < be.notes.length; i++) s.notes[i] = String.valueOf(be.notes[i]);
	}

	@Override
	public void submit(State be, PoseStack ps, SubmitNodeCollector buf, CameraRenderState cam) {
		int light = be.lightCoords;
		Direction f = be.blockState.getValue(ModBlocks.NotesBoard.FACING);
		ps.pushPose();
		ps.translate(0.5, 0, 0.5);
		ps.mulPose(Axis.YP.rotationDegrees(-switch (f) { case WEST -> 90; case NORTH -> 180; case EAST -> 270; default -> 0; }));
		ps.translate(-0.5, 0, -0.5);
		ps.scale(1 / 16f, 1 / 16f, 1 / 16f);   // дальше всё в пикселях; лицо щита — z = 9
		int lit = Math.max(light, 0x00F000F0 & light);
		// табличка
		quad(buf, ps, 22, 41.2f, 42, 44.2f, 9.06f, 0xFFC9BF9F, light);
		text(ps, buf, Component.translatable("vadim_na_trone.notes.title", be.count).getString(), 32, 41.6f, 0.11f, 0xFF5A0E0A, light, true, 30);
		for (int i = 0; i < 16; i++) {
			int col = i % 4, row = i / 4;
			float x0 = 3.5f + col * 14.5f, y1 = 39.5f - row * 8.2f, x1 = x0 + 12.5f, y0 = y1 - 7.2f;
			if (i < be.count) {
				float tilt = ((i * 37) % 7 - 3) * 0.6f;
				ps.pushPose();
				ps.translate((x0 + x1) / 2, (y0 + y1) / 2, 0);
				ps.mulPose(Axis.ZP.rotationDegrees(tilt));
				ps.translate(-(x0 + x1) / 2, -(y0 + y1) / 2, 0);
				quad(buf, ps, x0, y0, x1, y1, 9.08f, 0xFFECE7D3, light);
				quad(buf, ps, (x0 + x1) / 2 - 0.5f, y1 - 1.2f, (x0 + x1) / 2 + 0.5f, y1 - 0.2f, 9.12f, 0xFFB8231C, light);
				String q = Component.translatable("vadim_na_trone.quote." + be.notes[i]).getString();
				List<FormattedCharSequence> lines = font.split(Component.literal(q), 108);
				float sc = 0.085f;
				float ty = y1 - 1.6f;
				for (int k = 0; k < Math.min(lines.size(), 5); k++) {
					drawSeq(ps, buf, lines.get(k), x0 + 0.7f, ty, sc, 0xFF2A2420, light);
					ty -= 1.0f;
				}
				text(ps, buf, Component.translatable("vadim_na_trone.notes.sign").getString(), x1 - 0.6f, y0 + 1.2f, 0.07f, 0xFF7A0E0A, light, false, 0);
				ps.popPose();
			} else {
				text(ps, buf, String.valueOf(i + 1), (x0 + x1) / 2, (y0 + y1) / 2 + 0.6f, 0.12f, 0xFF7A6248, light, true, 0);
			}
		}
		ps.popPose();
	}

	private void quad(SubmitNodeCollector buf, PoseStack ps, float x0, float y0, float x1, float y1, float z, int argb, int light) {
		int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
		buf.submitCustomGeometry(ps, RenderTypes.entityCutoutNoCull(PAPER), (p, vc) -> {
			Matrix4f m = p.pose();
			vc.addVertex(m, x0, y0, z).setColor(r, g, b, a).setUv(0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
			vc.addVertex(m, x1, y0, z).setColor(r, g, b, a).setUv(1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
			vc.addVertex(m, x1, y1, z).setColor(r, g, b, a).setUv(1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
			vc.addVertex(m, x0, y1, z).setColor(r, g, b, a).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 0, 1);
		});
	}

	/** Текст на щите: (x, y) — левый верх строки (или центр при center), масштаб — пикселей на единицу шрифта. */
	private void text(PoseStack ps, SubmitNodeCollector buf, String s, float x, float y, float sc, int color, int light, boolean center, int maxW) {
		ps.pushPose();
		ps.translate(x, y, 9.15f);
		ps.scale(sc, -sc, sc);
		float w = font.width(s);
		float dx = center ? -w / 2f : (maxW == 0 && !center ? -w : 0);
		buf.submitText(ps, dx, 0, FormattedCharSequence.forward(s, Style.EMPTY), false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
		ps.popPose();
	}

	private void drawSeq(PoseStack ps, SubmitNodeCollector buf, FormattedCharSequence s, float x, float y, float sc, int color, int light) {
		ps.pushPose();
		ps.translate(x, y, 9.15f);
		ps.scale(sc, -sc, sc);
		buf.submitText(ps, 0, 0, s, false, Font.DisplayMode.POLYGON_OFFSET, light, color, 0, 0);
		ps.popPose();
	}
}
