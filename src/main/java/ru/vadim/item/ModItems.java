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

	private ModItems() {}

	private static Item reg(String name) {
		return Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id(name),
				new Item(Ids.item(name).stacksTo(1).rarity(Rarity.EPIC)));
	}

	public static void init() {}
}
