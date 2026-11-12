package ru.vadim.client;

import net.fabricmc.api.ClientModInitializer;
import ru.vadim.VadimNaTrone;

/** Клиентская точка входа: HUD, звук, катсцены — будут тут. */
public class VadimNaTroneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VadimNaTrone.LOG.info("Клиент Вадима готов.");
	}
}
