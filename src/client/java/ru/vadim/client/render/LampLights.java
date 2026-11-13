package ru.vadim.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Лампы рядом с камерой: раз в секунду ищем подвесные лампы бара и уличные фонари,
 * четыре ближайших отдаём в шейдер (слоты 5–8, режим «лампа»): тёплый конус вниз и луч в тумане.
 */
public final class LampLights {
	public static final int SLOTS = 4;
	private static final int R = 22, RY = 6;
	private static final List<BlockPos> found = new ArrayList<>();
	private static int scanIn = 0;

	private LampLights() {}

	/** 1 — подвесная лампа, 2 — голова уличного фонаря, 0 — не лампа. */
	private static int kind(BlockState st) {
		Block b = st.getBlock();
		var id = BuiltInRegistries.BLOCK.getKey(b);
		String path = id.getPath();
		if (path.equals("tavern_lantern") || path.equals("chain_lantern") || path.equals("lantern")) {
			return st.hasProperty(BlockStateProperties.HANGING) && st.getValue(BlockStateProperties.HANGING) ? 1 : 0;
		}
		if (path.equals("double_street_lamp") || path.equals("classic_street_lamp")) {
			for (var p : st.getProperties())
				if (p.getName().equals("part")) return st.getValue(p).toString().equals("top") ? 2 : 0;
		}
		return 0;
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			found.clear();
			return;
		}
		if (--scanIn > 0) return;
		scanIn = 20;
		found.clear();
		BlockPos c = mc.player.blockPosition();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -R; x <= R; x++)
			for (int z = -R; z <= R; z++)
				for (int y = -RY; y <= RY; y++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					if (kind(mc.level.getBlockState(m)) != 0) found.add(m.immutable());
				}
	}

	/** Заполнить слоты: [i][0..2] — позиция лампы относительно камеры, [3] — сила; B — направление (вниз) и режим 1. */
	public static void fill(float[][] a, float[][] b, int from, Vec3 cam) {
		Minecraft mc = Minecraft.getInstance();
		List<BlockPos> list = new ArrayList<>(found);
		list.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(cam)));
		int n = 0;
		for (BlockPos p : list) {
			if (n >= SLOTS || mc.level == null) break;
			int k = kind(mc.level.getBlockState(p));
			if (k == 0) continue;
			Vec3 head = Vec3.atCenterOf(p).add(0, k == 2 ? 0.2 : -0.1, 0);
			int i = from + n;
			a[i][0] = (float) (head.x - cam.x);
			a[i][1] = (float) (head.y - cam.y);
			a[i][2] = (float) (head.z - cam.z);
			a[i][3] = k == 2 ? 1f : 0.75f;
			b[i][0] = 0f;
			b[i][1] = -1f;
			b[i][2] = 0f;
			b[i][3] = 1f;
			n++;
		}
		for (int i = from + n; i < from + SLOTS; i++) a[i][3] = 0f;
	}
}
