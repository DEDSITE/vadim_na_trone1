package ru.vadim.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import ru.vadim.VadimNaTrone;
import ru.vadim.entity.CarEntity;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;

/** Машины за окном (GeckoLib): седан разных цветов, такси, грузовик и летающий опелелёт. Фары светятся (glowmask). */
public class CarRenderer extends GeoEntityRenderer<CarEntity, EntityRenderState> {
	static final DataTicket<String> KIND = DataTicket.create("vadim_car_kind", String.class);
	static final DataTicket<Integer> PAINT = DataTicket.create("vadim_car_paint", Integer.class);

	public CarRenderer(EntityRendererProvider.Context ctx) {
		super(ctx, new Model());
		withRenderLayer(new AutoGlowingGeoLayer<>(this));
		shadowRadius = 1.2f;
	}

	@Override
	public RenderType getRenderType(EntityRenderState s, Identifier tex) {
		return RenderTypes.entityTranslucent(tex);
	}

	@Override
	public void scaleModelForRender(software.bernie.geckolib.renderer.base.RenderPassInfo<EntityRenderState> info, float w, float h) {
		String k = info.renderState() instanceof GeoRenderState g ? g.getOrDefaultGeckolibData(KIND, "SEDAN") : "SEDAN";
		if (k.equals("OPEL")) super.scaleModelForRender(info, w * 0.55f, h * 0.55f);
		else super.scaleModelForRender(info, w, h);
	}

	static class Model extends GeoModel<CarEntity> {
		private static String name(GeoRenderState s) {
			return switch (s.getOrDefaultGeckolibData(KIND, "SEDAN")) {
				case "TAXI" -> "car_taxi";
				case "TRUCK" -> "car_truck";
				case "OPEL" -> "car_opel";
				default -> "car_sedan";
			};
		}

		@Override
		public Identifier getModelResource(GeoRenderState s) {
			return VadimNaTrone.id("entity/" + name(s));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState s) {
			int paint = s.getOrDefaultGeckolibData(PAINT, 0);
			return VadimNaTrone.id("textures/entity/car/" + switch (s.getOrDefaultGeckolibData(KIND, "SEDAN")) {
				case "TAXI" -> "taxi.png";
				case "TRUCK" -> "truck_" + (paint % 2) + ".png";
				case "OPEL" -> "opel.png";
				default -> "sedan_" + paint + ".png";
			});
		}

		@Override
		public Identifier getAnimationResource(CarEntity e) {
			return VadimNaTrone.id("entity/" + switch (e.kind()) {
				case TAXI -> "car_taxi";
				case TRUCK -> "car_truck";
				case OPEL -> "car_opel";
				default -> "car_sedan";
			});
		}

		@Override
		public void addAdditionalStateData(CarEntity e, Object related, GeoRenderState s) {
			s.addGeckolibData(KIND, e.kind().name());
			s.addGeckolibData(PAINT, Math.floorMod(e.paint(), 6));
		}
	}
}
