package ru.vadim.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.vadim.entity.CarEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Свет фар: ближайшие к камере машины получают по слоту в шейдере (режим «фонарик» — узкий конус вперёд
 * и чуть вниз). Конус ложится на асфальт и стены домов и видим в тумане, как в Стрипухоеде у фонариков.
 */
public final class CarLights {
	private CarLights() {}

	public static void fill(float[][] a, float[][] b, int from, int slots, Vec3 cam, float pt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		List<CarEntity> cars = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) if (e instanceof CarEntity c && c.kind() != CarEntity.Kind.OPEL) cars.add(c);
		cars.sort(Comparator.comparingDouble(c -> c.position().distanceToSqr(cam)));
		int n = 0;
		for (CarEntity c : cars) {
			if (n >= slots) break;
			float dir = Math.signum(c.speed());
			if (dir == 0) continue;
			Vec3 p = c.getPosition(pt);
			double front = c.kind() == CarEntity.Kind.TRUCK ? 2.8 : c.kind() == CarEntity.Kind.VAN ? 3.2 : 2.3;
			Vec3 o = p.add(dir * front, 0.65, 0);
			int i = from + n;
			a[i][0] = (float) (o.x - cam.x);
			a[i][1] = (float) (o.y - cam.y);
			a[i][2] = (float) (o.z - cam.z);
			a[i][3] = 1.6f;
			Vec3 d = new Vec3(dir, -0.18, 0).normalize();
			b[i][0] = (float) d.x;
			b[i][1] = (float) d.y;
			b[i][2] = (float) d.z;
			b[i][3] = 0f;
			n++;
		}
	}
}
