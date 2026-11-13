package ru.vadim.client.render;

import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import ru.vadim.VadimNaTrone;
import ru.vadim.entity.BarmanEntity;

/** Бармен Тимофей: модель игрока, скин бармена с лицом Тимофея (_assets/skins → textures/entity/barman.png). */
public class BarmanRenderer extends HumanoidMobRenderer<BarmanEntity, AvatarRenderState, PlayerModel> {
	private static final Identifier TEX = VadimNaTrone.id("textures/entity/barman.png");

	public BarmanRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new PlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
	}

	@Override public Identifier getTextureLocation(AvatarRenderState s) { return TEX; }
	@Override public AvatarRenderState createRenderState() { return new AvatarRenderState(); }

	@Override
	public void extractRenderState(BarmanEntity e, AvatarRenderState s, float pt) {
		super.extractRenderState(e, s, pt);
		s.showHat = s.showJacket = s.showLeftPants = s.showRightPants = s.showLeftSleeve = s.showRightSleeve = true;
	}
}
