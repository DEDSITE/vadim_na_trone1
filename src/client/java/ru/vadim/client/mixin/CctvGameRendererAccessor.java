package ru.vadim.client.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface CctvGameRendererAccessor {
	@Accessor("resourcePool") CrossFrameResourcePool vadim$pool();
	@Accessor("fogRenderer") FogRenderer vadim$fog();
}
