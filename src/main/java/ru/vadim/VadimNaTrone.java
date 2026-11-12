package ru.vadim;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Точка входа мода «Вадим на Троне» (общая часть: сервер + клиент). */
public class VadimNaTrone implements ModInitializer {
	public static final String MOD_ID = "vadim_na_trone";
	public static final Logger LOG = LoggerFactory.getLogger("Вадим на Троне");

	@Override
	public void onInitialize() {
		LOG.info("Вадим сел на трон. Хочешь сыграть в игру?");
	}
}
