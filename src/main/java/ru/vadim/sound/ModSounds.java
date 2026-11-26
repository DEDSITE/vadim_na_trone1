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
	public static final SoundEvent ZAVOD_DEBRIS_ROD = register("zavod_debris_rod");
	public static final SoundEvent ZAVOD_HATCH_MOVE = register("zavod_hatch_move");
	public static final SoundEvent ZAVOD_HATCH_SLAM = register("zavod_hatch_slam");
	public static final SoundEvent ZAVOD_HATCH_SLAM_BED = register("zavod_hatch_slam_bed");
	public static final SoundEvent ZAVOD_GAS = register("zavod_gas");
	public static final SoundEvent ZAVOD_HEARTBEAT = register("zavod_heartbeat");
	public static final SoundEvent ZAVOD_MONITORS = register("zavod_monitors");
	/** Взлом двери (этап 3б): лопнула цепь. */
	public static final SoundEvent ZAVOD_CHAIN_SNAP = register("zavod_chain_snap");

	/** Комната 1 (этап 4): музыка Димы, пока идёт таймер (поток, по кругу). */
	public static final SoundEvent ROOM1_MUSIC = register("room1_music");

	/** Комната 1: реплики Вадима (озвучка Димы, ElevenLabs), звучат из монитора. r1_05 Дима убрал. */
	public static final SoundEvent R1_01 = register("room1_r1_01");
	public static final SoundEvent R1_02 = register("room1_r1_02");
	public static final SoundEvent R1_03 = register("room1_r1_03");
	public static final SoundEvent R1_04 = register("room1_r1_04");
	public static final SoundEvent R1_06 = register("room1_r1_06");
	public static final SoundEvent R2_01 = register("room1_r2_01");
	public static final SoundEvent R2_02 = register("room1_r2_02");
	public static final SoundEvent R_LOSE = register("room1_r_lose");
	public static final SoundEvent R_WIN = register("room1_r_win");

	/** Комната 2 «Студия» (этап 5): пластинка с правилами (голос Димы, обработан «рацией»), проигрыш, победа. */
	public static final SoundEvent ROOM2_RULES = register("room2_rules");
	public static final SoundEvent ROOM2_LOSE = register("room2_r_lose");
	public static final SoundEvent ROOM2_WIN = register("room2_r_win");
	/** 13 записей для «Уха»: по 8 звуков (пердёж, Тимофей ×4, Вадим ×3), пауза 0,4 с. Последовательности — Room2.SEQS. */
	public static final SoundEvent[] ROOM2_REC = new SoundEvent[13];
	static {
		for (int i = 0; i < 13; i++) ROOM2_REC[i] = register(String.format("room2_rec_%02d", i + 1));
	}

	/** Комната 3 «Мини-мир» (этап 6): музыка мира — трек от Димы (Effectoid — Mizuna Steps 5), по кругу. */
	public static final SoundEvent ROOM3_MUSIC = register("room3_music");
	/** Табло комнаты 3 едет (вниз и вверх): два звука Димы, сведены в один моно-файл (Power Down Serpentine + лязг), стартуют вместе. */
	public static final SoundEvent ROOM3_BOARD = register("room3_board");
	/** Комната 4 «Бэкрумс» (этап 7), файлы Димы: выход активирован (до кода), сработал разлучник, игрок в темноте с улыбаками,
	 *  три фоновых «иногда включается» (тихо). */
	public static final SoundEvent ROOM4_EXIT = register("room4_exit");
	public static final SoundEvent ROOM4_SPLIT = register("room4_split");
	public static final SoundEvent ROOM4_DARK = register("room4_dark");
	public static final SoundEvent[] ROOM4_AMB = {register("room4_amb1"), register("room4_amb2"), register("room4_amb3")};

	private ModSounds() {}

	private static SoundEvent register(String name) {
		return Registry.register(BuiltInRegistries.SOUND_EVENT, VadimNaTrone.id(name), SoundEvent.createVariableRangeEvent(VadimNaTrone.id(name)));
	}

	public static void init() {}
}
