package ru.vadim.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ru.vadim.VadimNaTrone;
import ru.vadim.util.Ids;

import java.util.EnumMap;
import java.util.Map;

/**
 * Блоки завода (из интро Стрипухоеда): бетонный фонарный столб с «коброй» (плафон мерцает — LIT дёргает сервер)
 * и цепь с навесным замком (целая и перекушенная — на двери цеха, этап 3б). Ломать нельзя.
 */
public final class ZavodBlocks {
	private static BlockBehaviour.Properties props(String name, MapColor c, SoundType s) {
		return Ids.block(name).mapColor(c).strength(-1f, 3600000f).sound(s).noOcclusion().pushReaction(PushReaction.BLOCK);
	}

	public static final Block LAMP_POLE = reg("zavod_lamp_pole", new Deco(props("zavod_lamp_pole", MapColor.STONE, SoundType.STONE), new double[]{5, 0, 5, 11, 16, 11}, true));
	public static final Block LAMP_POLE_TOP = reg("zavod_lamp_pole_top", new Deco(props("zavod_lamp_pole_top", MapColor.STONE, SoundType.STONE), new double[]{5.5, 0, 5.5, 10.5, 16, 10.5}, true));
	public static final Block LAMP_HEAD = reg("zavod_lamp_head", new LampHead(props("zavod_lamp_head", MapColor.STONE, SoundType.STONE)
			.lightLevel(s -> s.getValue(LampHead.LIT) ? 14 : 0)));
	/** Только для дисплеев (висит на двери цеха). */
	public static final Block CHAIN = reg("zavod_chain", new Deco(props("zavod_chain", MapColor.METAL, SoundType.CHAIN), new double[]{5, 1, 4, 11, 9, 11}, false));
	public static final Block CHAIN_CUT = reg("zavod_chain_cut", new Deco(props("zavod_chain_cut", MapColor.METAL, SoundType.CHAIN), new double[]{5, 0, 3, 12, 9, 11}, false));

	// ---- цех внутри (этап 3в): техника Вадима (текстуры — макет Claude Design «Цех — техника Вадима»)
	/** Серверная стойка: низ и верх (2 блока), огоньки мигают анимацией текстуры. */
	public static final Block RACK = reg("zavod_rack", new Deco(props("zavod_rack", MapColor.COLOR_BLACK, SoundType.METAL).lightLevel(s -> 3), new double[]{1, 0, 1, 15, 16, 15}, true));
	public static final Block RACK_TOP = reg("zavod_rack_top", new Deco(props("zavod_rack_top", MapColor.COLOR_BLACK, SoundType.METAL).lightLevel(s -> 3), new double[]{1, 0, 1, 15, 16, 15}, true));
	/** Старый ЭЛТ-монитор: CAM=false — помехи, true — камера двора «CAM 01». */
	public static final Block CRT = reg("zavod_crt", new Crt(props("zavod_crt", MapColor.SAND, SoundType.METAL).lightLevel(s -> 6)));
	/** ЭЛТ с живой картинкой с камеры над стеной мониторов (рисует клиент, CrtFeedRenderer). */
	public static final Block CRT_FEED = reg("zavod_crt_feed", new CrtFeed(props("zavod_crt_feed", MapColor.SAND, SoundType.METAL).lightLevel(s -> 6)));
	public static final net.minecraft.world.level.block.entity.BlockEntityType<CrtFeedEntity> CRT_FEED_BE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, VadimNaTrone.id("zavod_crt_feed"),
			net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(CrtFeedEntity::new, CRT_FEED).build());
	/** ЭЛТ с надписью «Хочешь сыграть в игру?». */
	public static final Block CRT_PLAY = reg("zavod_crt_play", new Crt(props("zavod_crt_play", MapColor.SAND, SoundType.METAL).lightLevel(s -> 7)));
	/** Плоская панель 2×2 «Хочешь сыграть в игру?»: PART 0 — левый верх, 1 — правый верх, 2 — левый низ, 3 — правый низ (если смотреть на экран). */
	public static final Block PANEL = reg("zavod_panel", new Panel(props("zavod_panel", MapColor.COLOR_BLACK, SoundType.METAL).lightLevel(s -> 8)));
	/** Лампа дневного света под потолком (на цепи), FACING — вдоль какой оси, LIT — горит. */
	public static final Block TUBE = reg("zavod_tube", new Tube(props("zavod_tube", MapColor.METAL, SoundType.METAL).noCollision()
			.lightLevel(s -> s.getValue(Tube.LIT) ? 14 : 0)));
	/** Пучок кабелей на полу (как ковёр, ходить можно), FACING — вдоль какой оси. */
	public static final Block CABLES = reg("zavod_cables", new Deco(props("zavod_cables", MapColor.COLOR_BLACK, SoundType.WOOL).noCollision(), new double[]{0, 0, 0, 16, 1, 16}, false));
	/** Кабель, свисающий с потолка; END — оборванный конец с жилами (искрит). */
	public static final Block CABLE_HANG = reg("zavod_cable_hang", new CableHang(props("zavod_cable_hang", MapColor.COLOR_BLACK, SoundType.WOOL).noCollision()));

	private ZavodBlocks() {}

	private static Block reg(String name, Block b) {
		return Registry.register(BuiltInRegistries.BLOCK, VadimNaTrone.id(name), b);
	}

	public static void init() {}

	/** Декор с поворотом: box — коробка для взгляда на север (в пикселях), collide — можно ли упереться. */
	public static class Deco extends HorizontalDirectionalBlock {
		private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
		private final boolean collide;

		public Deco(Properties p, double[] box, boolean collide) {
			super(p);
			this.collide = collide;
			for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotate(box, d));
			registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
		}

		static VoxelShape rotate(double[] b, Direction f) {
			double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
			return switch (f) {
				case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
				case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
				case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
				default -> Block.box(x0, b[1], z0, x1, b[4], z1);
			};
		}

		@Override
		protected MapCodec<? extends HorizontalDirectionalBlock> codec() { throw new UnsupportedOperationException(); }

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

		@Override
		public BlockState getStateForPlacement(BlockPlaceContext ctx) {
			return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
		}

		@Override
		protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
			return shapes.get(s.getValue(FACING));
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
			return collide ? shapes.get(s.getValue(FACING)) : Shapes.empty();
		}
	}

	/** Голова фонаря: «кобра» на кронштейне в сторону FACING (плафон ~0.7 блока впереди), LIT — горит ли. */
	public static class LampHead extends Deco {
		public static final BooleanProperty LIT = BlockStateProperties.LIT;

		LampHead(Properties p) {
			super(p, new double[]{6, 0, 0, 10, 9, 10}, true);
			registerDefaultState(defaultBlockState().setValue(LIT, true));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
	}

	public static class Crt extends Deco {
		public static final BooleanProperty CAM = BooleanProperty.create("cam");

		Crt(Properties p) {
			super(p, new double[]{1, 0, 2, 15, 14, 15}, true);
			registerDefaultState(defaultBlockState().setValue(CAM, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, CAM); }
	}

	public static class CrtFeed extends Crt implements net.minecraft.world.level.block.EntityBlock {
		CrtFeed(Properties p) { super(p); }
		@Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new CrtFeedEntity(pos, st); }
	}

	public static class CrtFeedEntity extends net.minecraft.world.level.block.entity.BlockEntity {
		public CrtFeedEntity(BlockPos pos, BlockState st) { super(CRT_FEED_BE, pos, st); }
	}

	public static class Panel extends Deco {
		public static final net.minecraft.world.level.block.state.properties.IntegerProperty PART =
				net.minecraft.world.level.block.state.properties.IntegerProperty.create("part", 0, 3);

		Panel(Properties p) {
			super(p, new double[]{0, 0, 13, 16, 16, 16}, true);
			registerDefaultState(defaultBlockState().setValue(PART, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, PART); }
	}

	public static class Tube extends Deco {
		public static final BooleanProperty LIT = BlockStateProperties.LIT;

		Tube(Properties p) {
			super(p, new double[]{0, 12, 5, 16, 16, 11}, false);
			registerDefaultState(defaultBlockState().setValue(LIT, true));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LIT); }
	}

	public static class CableHang extends Block {
		public static final BooleanProperty END = BooleanProperty.create("end");
		private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);

		CableHang(Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(END, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(END); }

		@Override
		protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

		@Override
		protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
	}
}
