package ru.vadim.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vadim.client.news.ClientNews;

/** Катсцена: камера оторвана от игрока — его руку не рисуем. */
@Mixin(ItemInHandRenderer.class)
public abstract class NewsHandMixin {
	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void vadim$hideHand(float pt, PoseStack pose, SubmitNodeCollector buf, LocalPlayer player, int light, CallbackInfo ci) {
		if (ClientNews.active() || ru.vadim.client.zavod.ZavodFinalClient.locked()) ci.cancel();
	}
}
