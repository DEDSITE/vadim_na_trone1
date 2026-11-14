package ru.vadim.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import ru.vadim.block.ModBlocks;
import ru.vadim.block.VanData;
import ru.vadim.entity.CarEntity;

/**
 * Буханка «Влог конченных» из Стрипухоеда: рисуем её же блочные модели (157 частей), уменьшенные до размера машины.
 * Перед буханки — сторона x=0 модели; едет туда, куда смотрит.
 */
public class VanCarRenderer extends EntityRenderer<CarEntity, VanCarRenderer.State> {
	private static final float SCALE = 0.6f;

	public static class State extends EntityRenderState {
		float dir = 1f;
	}

	public VanCarRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		shadowRadius = 1.4f;
	}

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(CarEntity e, State s, float pt) {
		super.extractRenderState(e, s, pt);
		s.dir = Math.signum(e.speed()) == 0 ? 1 : Math.signum(e.speed());
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		ps.pushPose();
		// модель смотрит в -X; едем на +X — разворачиваем
		if (s.dir > 0) ps.mulPose(Axis.YP.rotationDegrees(180));
		ps.scale(SCALE, SCALE, SCALE);
		ps.translate(-VanData.SIZE_X / 2.0, 0, -VanData.SIZE_Z / 2.0);
		BlockState base = ModBlocks.VAN.defaultBlockState();
		for (int i = 0; i < VanData.PARTS; i++) {
			int[] c = VanData.CELLS[i];
			ps.pushPose();
			ps.translate(c[0], c[1], c[2]);
			out.submitBlock(ps, base.setValue(ModBlocks.VanPart.PART, i), c[3] > 0 ? 0xF000F0 : s.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			ps.popPose();
		}
		ps.popPose();
		super.submit(s, ps, out, cam);
	}
}
