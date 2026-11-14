package ru.vadim.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import ru.vadim.VadimNaTrone;
import ru.vadim.entity.BarmanEntity;

/**
 * Бармен Тимофей: геометрия игрока (слой PLAYER со вторым слоем скина), но обычный «гуманоидный» рендер,
 * а не игроковый (Avatar). Игроковый путь берёт скин из профиля (и его трогают моды анимаций) — из-за этого
 * вместо нашего скина рисовался Стив. Текстура — HD-скин 512×512: скин бармена + фото лица Тимофея спереди головы.
 */
public class BarmanRenderer extends HumanoidMobRenderer<BarmanEntity, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
	private static final Identifier TEX = VadimNaTrone.id("textures/entity/barman.png");

	public BarmanRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f);
	}

	@Override public Identifier getTextureLocation(HumanoidRenderState s) { return TEX; }
	@Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
}
