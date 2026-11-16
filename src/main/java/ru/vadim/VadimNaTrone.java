package ru.vadim;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.vadim.block.ModBlocks;
import ru.vadim.entity.ModEntities;
import ru.vadim.item.ModItems;
import ru.vadim.net.HubPayloads;
import ru.vadim.particle.ModParticles;
import ru.vadim.sound.ModSounds;
import ru.vadim.world.Hub;
import ru.vadim.world.HubCommands;
import ru.vadim.world.Traffic;

/** Точка входа мода «Вадим на Троне» (общая часть: сервер + клиент). */
public class VadimNaTrone implements ModInitializer {
	public static final String MOD_ID = "vadim_na_trone";
	public static final Logger LOG = LoggerFactory.getLogger("Вадим на Троне");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(HubPayloads.Countdown.TYPE, HubPayloads.Countdown.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.MenuShot.TYPE, HubPayloads.MenuShot.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.NewsStart.TYPE, HubPayloads.NewsStart.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.NewsVotes.TYPE, HubPayloads.NewsVotes.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.NewsEnd.TYPE, HubPayloads.NewsEnd.CODEC);
		PayloadTypeRegistry.playC2S().register(HubPayloads.NewsSkip.TYPE, HubPayloads.NewsSkip.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.ZavodHint.TYPE, HubPayloads.ZavodHint.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.ZavodJournal.TYPE, HubPayloads.ZavodJournal.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.ZavodFinal.TYPE, HubPayloads.ZavodFinal.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.Room1State.TYPE, HubPayloads.Room1State.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.Room1Keypad.TYPE, HubPayloads.Room1Keypad.CODEC);
		PayloadTypeRegistry.playC2S().register(HubPayloads.Room1Code.TYPE, HubPayloads.Room1Code.CODEC);
		PayloadTypeRegistry.playC2S().register(HubPayloads.Room1Skip.TYPE, HubPayloads.Room1Skip.CODEC);
		PayloadTypeRegistry.playS2C().register(HubPayloads.Room2State.TYPE, HubPayloads.Room2State.CODEC);
		ModSounds.init();
		ModParticles.init();
		ModItems.init();
		ModBlocks.init();
		ru.vadim.block.ZavodBlocks.init();
		ModEntities.init();
		Hub.register();
		ru.vadim.world.News.register();
		Traffic.register();
		ru.vadim.world.ZavodDoor.register();
		ru.vadim.world.Room1.register();
		ru.vadim.world.Room2.register();
		HubCommands.register();
		LOG.info("Вадим сел на трон. Хочешь сыграть в игру?");
	}
}
