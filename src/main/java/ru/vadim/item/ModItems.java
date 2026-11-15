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

	private ModItems() {}

	private static Item reg(String name) {
		return Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id(name),
				new Item(Ids.item(name).stacksTo(1).rarity(Rarity.EPIC)));
	}

	public static void init() {}
}
