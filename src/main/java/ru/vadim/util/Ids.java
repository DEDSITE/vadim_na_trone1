package ru.vadim.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import ru.vadim.VadimNaTrone;

/** 1.21.2+: id блока/предмета должен лежать в Properties до конструктора (иначе «Block id not set»). */
public final class Ids {
	private Ids() {}

	public static BlockBehaviour.Properties block(String name) {
		return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, VadimNaTrone.id(name)));
	}

	public static Item.Properties item(String name) {
		return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, VadimNaTrone.id(name)));
	}

	/** Предмет-блок: id предмета = id блока, название берётся из block.vadim_na_trone.*. */
	public static Item.Properties blockItem(String name) {
		return item(name).useBlockDescriptionPrefix();
	}

	public static ResourceKey<Block> blockKey(String name) {
		return ResourceKey.create(Registries.BLOCK, VadimNaTrone.id(name));
	}
}
