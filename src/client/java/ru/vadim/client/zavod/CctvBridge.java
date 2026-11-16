package ru.vadim.client.zavod;

/**
 * Флаг для Camera Overhaul: камера наблюдения цеха (CctvFeed) зовёт Camera.setup для своей Camera каждый кадр,
 * а Camera Overhaul держит одно общее состояние наклона — от этого экран игрока на заводе дёргало.
 * NewsCameraMixin ставит otherCamera на время setup не главной камеры, CameraOverhaulGuardMixin в это время молчит.
 */
public final class CctvBridge {
	private CctvBridge() {}

	/** true, пока идёт Camera.setup не главной камеры. */
	public static boolean otherCamera;
}
