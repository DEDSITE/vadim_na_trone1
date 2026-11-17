package ru.vadim.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import ru.vadim.VadimNaTrone;
import ru.vadim.entity.GovnozavrEntity;

/** Мини-Говнозавр: модель босса 5,4 блока, уменьшенная до 2 блоков (86 px → 32 px). */
public class GovnozavrRenderer extends MobRenderer<GovnozavrEntity, GovnozavrRenderState, GovnozavrModel> {
	private static final Identifier TEX = VadimNaTrone.id("textures/entity/govnozavr.png");
	private static final float SCALE = 32f / 86f;

	public GovnozavrRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new GovnozavrModel(ctx.bakeLayer(GovnozavrModel.LAYER)), 0.5f);
	}

	@Override public GovnozavrRenderState createRenderState() { return new GovnozavrRenderState(); }
	@Override public Identifier getTextureLocation(GovnozavrRenderState s) { return TEX; }

	@Override
	public void extractRenderState(GovnozavrEntity e, GovnozavrRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		s.attack = e.getAttackAnim(pt);
	}

	@Override
	protected void scale(GovnozavrRenderState s, PoseStack pose) {
		pose.scale(SCALE, SCALE, SCALE);
	}
}
