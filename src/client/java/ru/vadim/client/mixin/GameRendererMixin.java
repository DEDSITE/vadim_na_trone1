package ru.vadim.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vadim.client.render.FlashlightLights;
import ru.vadim.client.render.HorrorPost;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** Пост-эффект сразу после отрисовки мира (вместе с рукой), до интерфейса. */
	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER))
	private void vadim$horrorPost(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		HorrorPost.render(deltaTracker.getGameTimeDeltaTicks());
	}

	/** Катсцена с новостями: постоянный угол обзора, чтобы кадр с телеком был у всех одинаковый. */
	@com.llamalad7.mixinextras.injector.ModifyReturnValue(method = "getFov", at = @At("RETURN"))
	private float vadim$newsFov(float fov, net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting) {
		return useFovSetting && ru.vadim.client.news.ClientNews.active() ? 60f : fov;
	}

	/** Камера видеонаблюдения в цеху: её кадр рисуем до основного (в свою текстуру). */
	@Inject(method = "renderLevel", at = @At("HEAD"))
	private void vadim$cctv(DeltaTracker deltaTracker, CallbackInfo ci) {
		ru.vadim.client.zavod.CctvFeed.render(deltaTracker);
	}

	/** Финал завода: качание в газу, падение на бок, подъём с пола. */
	@Inject(method = "bobHurt", at = @At("HEAD"))
	private void vadim$finalPose(com.mojang.blaze3d.vertex.PoseStack pose, float partial, CallbackInfo ci) {
		ru.vadim.client.zavod.ZavodFinalClient.applyPose(pose, partial);
	}

	/** Рука в первом лице рисуется в другой системе координат — гасим для неё фонари. */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(FZLorg/joml/Matrix4f;)V"))
	private void vadim$beforeHand(DeltaTracker deltaTracker, CallbackInfo ci) {
		FlashlightLights.beforeHand();
	}
}
