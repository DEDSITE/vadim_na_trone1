package ru.vadim.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.vadim.client.zavod.CctvFeed;

/** Пока рисуем кадр для камеры видеонаблюдения — мир рисуется в её маленькую текстуру, а не на экран. */
@Mixin(Minecraft.class)
public abstract class CctvMinecraftMixin {
	@Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
	private void vadim$cctvTarget(CallbackInfoReturnable<RenderTarget> cir) {
		RenderTarget t = CctvFeed.renderingTarget();
		if (t != null) cir.setReturnValue(t);
	}
}
