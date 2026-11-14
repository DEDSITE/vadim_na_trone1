package ru.vadim.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import ru.vadim.VadimNaTrone;

public final class ModSounds {
	/** Джаз в баре: играет из колонок по углам (позиционно) и в главном меню. */
	public static final SoundEvent BAR_JAZZ = register("bar_jazz");
	public static final SoundEvent MENU_JAZZ = register("menu_jazz");
	/** Катсцена: ролик новостей из телека (позиционно), щелчок пульта Тимофея, включение ТВ, помехи. */
	public static final SoundEvent NEWS_TV = register("news_tv");
	public static final SoundEvent TV_CLICK = register("tv_click");
	public static final SoundEvent TV_ON = register("tv_on");
	public static final SoundEvent TV_STATIC = register("tv_static");

	private ModSounds() {}

	private static SoundEvent register(String name) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, VadimNaTrone.id(name), SoundEvent.createVariableRangeEvent(VadimNaTrone.id(name)));
	}

	public static void init() {}
}
