package ru.vadim.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import ru.vadim.VadimNaTrone;
import ru.vadim.util.Ids;

/** Экспонаты бара: лопаты из Говномесов (только для витрин, в руки игрокам не выдаются). */
public final class ModItems {
	public static final Item SHOVEL_DEZHAVU = reg("shovel_dezhavu");
	public static final Item SHOVEL_ANIME = reg("shovel_anime");

	/** Лом из будки охранника (этап 3б): им вскрывают цепь на двери цеха, после взлома пропадает. */
	public static final Item CROWBAR = Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id("crowbar"),
			new Item(Ids.item("crowbar").stacksTo(1)));

	/** Комната 1 «Стулья» (этап 4): маникюрные ножницы (режут только верёвки) и болторез из кладовки (перекусывает цепи). */
	public static final Item SCISSORS = Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id("scissors"),
			new Item(Ids.item("scissors").stacksTo(1)));
	public static final Item CUTTERS = Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id("cutters"),
			new Item(Ids.item("cutters").stacksTo(1)));

	/** Комната 2 «Студия» (этап 5): пластинка Вадима с правилами — лежит на полу, ставят в проигрыватель. */
	public static final Item RECORD = Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id("record_vadim"),
			new Item(Ids.item("record_vadim").stacksTo(1)));

	/** Комната 4 (7б): предохранитель для щитка — подбирает только электрик. */
	public static final Item FUSE = Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id("room4_fuse"),
			new Item(Ids.item("room4_fuse").stacksTo(9)));

	private ModItems() {}

	private static Item reg(String name) {
		return Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id(name),
				new Item(Ids.item(name).stacksTo(1).rarity(Rarity.EPIC)));
	}

	public static void init() {}
}
