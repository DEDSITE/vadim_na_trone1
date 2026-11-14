package ru.vadim.client.menu;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.vadim.VadimNaTrone;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Картинки меню (нарисованы в Claude Design, отрендерены в 2× размере макета 1280×720).
 * Грузим сами в DynamicTexture с линейным фильтром — иначе при уменьшении текст рвётся «лесенкой».
 */
final class MenuTex {
	record Tex(Identifier id, int w, int h) {}
	private static final Map<String, Tex> CACHE = new HashMap<>();

	private MenuTex() {}

	/** null, если файла нет или он не прочитался (тогда элемент просто не рисуется). */
	static Tex get(String name) {
		if (CACHE.containsKey(name)) return CACHE.get(name);
		Tex t = null;
		Identifier src = VadimNaTrone.id("textures/gui/menu/" + name + ".png");
		try (InputStream in = Minecraft.getInstance().getResourceManager().open(src)) {
			NativeImage img = NativeImage.read(in);
			Identifier id = VadimNaTrone.id("dynamic/menu_" + name);
			DynamicTexture tex = new DynamicTexture(() -> "vadim menu " + name, img) {
				{ sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR); }
			};
			Minecraft.getInstance().getTextureManager().register(id, tex);
			t = new Tex(id, img.getWidth(), img.getHeight());
		} catch (Exception e) {
			VadimNaTrone.LOG.warn("Меню: не загрузилась картинка {}: {}", name, e.toString());
		}
		CACHE.put(name, t);
		return t;
	}
}
