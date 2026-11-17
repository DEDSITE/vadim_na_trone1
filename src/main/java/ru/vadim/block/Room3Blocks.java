package ru.vadim.block;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import ru.vadim.VadimNaTrone;
import ru.vadim.util.Ids;

/**
 * Комната 3 «Мини-мир» (этап 6): говно (блок из Стрипухоеда — кирка ломает его как руду, падает само себя)
 * и «небо» из блоков (стены и потолок комнаты: голубой фон, облака, солнце).
 */
public final class Room3Blocks {
	/** Говно: модель кучки из Стрипухоеда; ломается вдвое дольше руды (незеритовой киркой ~1 с). */
	public static final Block GOVNO = Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id("govno"),
			new Pile(Ids.block("govno").mapColor(MapColor.COLOR_BROWN).strength(6f, 6f).requiresCorrectToolForDrops().sound(SoundType.MUD).noOcclusion(),
					Block.box(2, 0, 2, 14, 9, 14)));
	/** Ракета Вадима на месте следующего источника говна: ломается вчетверо дольше говна (~4 с), ничего не роняет. */
	public static final Block ROCKET = Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id("room3_rocket"),
			new Pile(Ids.block("room3_rocket").mapColor(MapColor.COLOR_PINK).strength(24f, 24f).requiresCorrectToolForDrops().sound(SoundType.WOOL).noOcclusion(),
					Block.box(2, 0, 2, 14, 16, 14)));
	public static final Item GOVNO_ITEM = Registry.register(BuiltInRegistries.ITEM, VadimNaTrone.id("govno"),
			new BlockItem(GOVNO, Ids.blockItem("govno")));
	public static final Block SKY = deco("room3_sky", MapColor.COLOR_LIGHT_BLUE);
	public static final Block CLOUD = deco("room3_cloud", MapColor.SNOW);
	public static final Block SUN = deco("room3_sun", MapColor.COLOR_YELLOW);

	private Room3Blocks() {}

	/** Блок не во весь куб: своя форма (кучка, ракета). */
	public static final class Pile extends Block {
		private final net.minecraft.world.phys.shapes.VoxelShape shape;

		Pile(Properties p, net.minecraft.world.phys.shapes.VoxelShape shape) {
			super(p);
			this.shape = shape;
		}

		@Override
		protected net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState st, net.minecraft.world.level.BlockGetter l,
				net.minecraft.core.BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
			return shape;
		}
	}

	private static Block deco(String name, MapColor c) {
		return Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id(name),
				new Block(Ids.block(name).mapColor(c).strength(-1f, 3600000f).sound(SoundType.STONE)));
	}

	public static void init() {}
}
