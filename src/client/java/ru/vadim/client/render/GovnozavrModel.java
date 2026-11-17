package ru.vadim.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import ru.vadim.VadimNaTrone;

/**
 * Мини-Говнозавр: модель босса Тимофея из Говномесов (кубы, текстура 256×128), рост 86 px — рендер уменьшает до 2 блоков.
 * Голова 24×22×20, туловище 32×36×20, руки 12×40×12, ноги 12×28×12.
 */
public class GovnozavrModel extends EntityModel<GovnozavrRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(VadimNaTrone.id("govnozavr"), "main");
	private final ModelPart body, head, rightArm, leftArm, rightLeg, leftLeg;

	public GovnozavrModel(ModelPart root) {
		super(root);
		body = root.getChild("body");
		head = body.getChild("head");
		rightArm = body.getChild("right_arm");
		leftArm = body.getChild("left_arm");
		rightLeg = root.getChild("right_leg");
		leftLeg = root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition r = mesh.getRoot();
		PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(88, 0).addBox(-16f, -36f, -10f, 32f, 36f, 20f),
				PartPose.offset(0f, -4f, 0f));
		body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-12f, -22f, -10f, 24f, 22f, 20f), PartPose.offset(0f, -36f, -1f));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 56).addBox(-6f, -2f, -6f, 12f, 40f, 12f), PartPose.offset(-22f, -32f, 0f));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(48, 56).addBox(-6f, -2f, -6f, 12f, 40f, 12f), PartPose.offset(22f, -32f, 0f));
		r.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(96, 56).addBox(-6f, 0f, -6f, 12f, 28f, 12f), PartPose.offset(-8f, -4f, 0f));
		r.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(144, 56).addBox(-6f, 0f, -6f, 12f, 28f, 12f), PartPose.offset(8f, -4f, 0f));
		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	public void setupAnim(GovnozavrRenderState s) {
		super.setupAnim(s);
		head.yRot = s.yRot * Mth.DEG_TO_RAD;
		head.xRot = s.xRot * Mth.DEG_TO_RAD;
		float swing = Mth.cos(s.walkAnimationPos * 0.6662f) * 0.9f * s.walkAnimationSpeed;
		rightLeg.xRot = swing;
		leftLeg.xRot = -swing;
		rightArm.xRot = -swing * 0.7f;
		leftArm.xRot = swing * 0.7f;
		float breathe = Mth.sin(s.ageInTicks * 0.07f) * 0.05f;
		rightArm.zRot = 0.10f + breathe;
		leftArm.zRot = -0.10f - breathe;
		body.xRot = 0.06f;
		// удар: обе руки вперёд-вниз, как у зомби
		if (s.attack > 0f) {
			float k = Mth.sin(s.attack * Mth.PI);
			rightArm.xRot = -1.7f * k;
			leftArm.xRot = -1.7f * k;
			body.xRot = 0.06f + 0.25f * k;
		}
	}
}
