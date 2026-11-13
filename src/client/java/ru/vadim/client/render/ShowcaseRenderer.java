package ru.vadim.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import ru.vadim.block.ModBlocks;
import ru.vadim.item.ModItems;

/** Витрина: внутри медленно покачивается лопата-экспонат, подсвеченная изнутри. */
public class ShowcaseRenderer implements BlockEntityRenderer<ModBlocks.ShowcaseEntity, ShowcaseRenderer.State> {
	private final net.minecraft.client.renderer.item.ItemModelResolver items;

	public static class State extends BlockEntityRenderState {
		final ItemStackRenderState item = new ItemStackRenderState();
		float time;
	}

	public ShowcaseRenderer(BlockEntityRendererProvider.Context ctx) { this.items = ctx.itemModelResolver(); }

	@Override public State createRenderState() { return new State(); }

	@Override
	public void extractRenderState(ModBlocks.ShowcaseEntity be, State s, float pt, Vec3 cam, ModelFeatureRenderer.CrumblingOverlay crumble) {
		BlockEntityRenderer.super.extractRenderState(be, s, pt, cam, crumble);
		ModBlocks.Exhibit ex = be.getBlockState().getValue(ModBlocks.Showcase.EXHIBIT);
		ItemStack stack = new ItemStack(ex == ModBlocks.Exhibit.ANIME ? ModItems.SHOVEL_ANIME : ModItems.SHOVEL_DEZHAVU);
		items.updateForTopItem(s.item, stack, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
		s.time = (be.getLevel() != null ? be.getLevel().getGameTime() : 0) + pt;
	}

	@Override
	public void submit(State s, PoseStack ps, SubmitNodeCollector out, CameraRenderState cam) {
		Direction f = s.blockState.getValue(ModBlocks.Facing.FACING);
		ps.pushPose();
		ps.translate(0.5, 0.5, 0.5);
		ps.mulPose(Axis.YP.rotationDegrees(-switch (f) { case WEST -> 90; case NORTH -> 180; case EAST -> 270; default -> 0; }));
		ps.translate(0, 0.02, -0.28);
		ps.mulPose(Axis.YP.rotationDegrees((float) Math.sin(s.time * 0.03) * 12f));
		ps.mulPose(Axis.ZP.rotationDegrees(-45));
		ps.scale(0.62f, 0.62f, 0.62f);
		s.item.submit(ps, out, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
		ps.popPose();
	}
}
