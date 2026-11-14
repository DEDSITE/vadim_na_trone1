package ru.vadim.client.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.vadim.client.news.ClientNews;

/** Катсцена: без хотбара и прицела — только картинка. */
@Mixin(Gui.class)
public abstract class NewsGuiMixin {
	@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
	private void vadim$hideHotbar(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
		if (ClientNews.active()) ci.cancel();
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void vadim$hideCross(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
		if (ClientNews.active()) ci.cancel();
	}
}
