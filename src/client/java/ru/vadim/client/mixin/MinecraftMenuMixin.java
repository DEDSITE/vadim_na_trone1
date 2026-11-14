package ru.vadim.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.vadim.client.menu.BarMenuScreen;

/** Вместо ванильного главного меню — бар «Трон». */
@Mixin(Minecraft.class)
public abstract class MinecraftMenuMixin {
	@Shadow public ClientLevel level;

	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen vadim$menu(Screen s) {
		if (s == null ? level == null : s.getClass() == TitleScreen.class) return new BarMenuScreen();
		return s;
	}
}
