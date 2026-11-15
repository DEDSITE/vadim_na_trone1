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

	/** Завод (этап 3): двор — ветер и далёкий город (фоном, не позиционно). */
	public static final SoundEvent ZAVOD_WIND = register("zavod_wind");
	public static final SoundEvent ZAVOD_CITY = register("zavod_city");
	/** Двор: гул цеха из-за стены (позиционно от фасада), гудение ламп вывески, их «моргание», искры, фонарь вкл-горит-выкл. */
	public static final SoundEvent ZAVOD_FACTORY_OUT = register("zavod_factory_out");
	public static final SoundEvent ZAVOD_LAMP_BUZZ = register("zavod_lamp_buzz");
	public static final SoundEvent ZAVOD_LAMP_FLICKER1 = register("zavod_lamp_flicker1");
	public static final SoundEvent ZAVOD_LAMP_FLICKER2 = register("zavod_lamp_flicker2");
	public static final SoundEvent ZAVOD_LAMP_FLICKER3 = register("zavod_lamp_flicker3");
	public static final SoundEvent ZAVOD_LAMP_FLICKER4 = register("zavod_lamp_flicker4");
	public static final SoundEvent ZAVOD_LAMP_FLICKER5 = register("zavod_lamp_flicker5");
	public static final SoundEvent ZAVOD_SPARK = register("zavod_spark");
	public static final SoundEvent ZAVOD_LAMP_CYCLE = register("zavod_lamp_cycle");
	/** Цех (этап 3в): гул трансформаторов и вентиляция (фон), гул серверов (от стоек), упавшая железка. */
	public static final SoundEvent ZAVOD_FACTORY = register("zavod_factory");
	public static final SoundEvent ZAVOD_VENT = register("zavod_vent");
	public static final SoundEvent ZAVOD_SERVERS = register("zavod_servers");
	public static final SoundEvent ZAVOD_DEBRIS = register("zavod_debris");
	public static final SoundEvent ZAVOD_MONITORS = register("zavod_monitors");
	/** Взлом двери (этап 3б): лопнула цепь. */
	public static final SoundEvent ZAVOD_CHAIN_SNAP = register("zavod_chain_snap");

	private ModSounds() {}

	private static SoundEvent register(String name) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, VadimNaTrone.id(name), SoundEvent.createVariableRangeEvent(VadimNaTrone.id(name)));
	}

	public static void init() {}
}
