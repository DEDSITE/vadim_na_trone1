package ru.vadim.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.InteractionResult;
import ru.vadim.VadimNaTrone;
import ru.vadim.block.ModBlocks;
import ru.vadim.client.particle.AmbientParticles;
import ru.vadim.client.particle.DustMoteParticle;
import ru.vadim.client.render.BarmanRenderer;
import ru.vadim.client.render.CarRenderer;
import ru.vadim.client.render.FlashlightLights;
import ru.vadim.client.render.HorrorPost;
import ru.vadim.client.render.LampLights;
import ru.vadim.client.render.NotesBoardRenderer;
import ru.vadim.client.render.ShowcaseRenderer;
import ru.vadim.client.render.TvRenderer;
import ru.vadim.client.render.VanCarRenderer;
import ru.vadim.client.screen.BarmanScreen;
import ru.vadim.client.sound.BarJazz;
import ru.vadim.entity.BarmanEntity;
import ru.vadim.entity.ModEntities;
import ru.vadim.net.HubPayloads;
import ru.vadim.particle.ModParticles;

/** Клиент: свет и туман, рендеры бара, джаз из колонок, диалог с барменом, отсчёт старта, катсцена с новостями. */
public class VadimNaTroneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HorrorPost.init();
		WorldRenderEvents.END_MAIN.register(ctx -> FlashlightLights.captureDepth());
		// мультик Вадима на мониторе комнаты 1 (этап 4б)
		WorldRenderEvents.AFTER_ENTITIES.register(ru.vadim.client.room.Room1Video::render);
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.room.Room1Video::tick);
		ParticleFactoryRegistry.getInstance().register(ModParticles.DUST, DustMoteParticle.Provider::new);

		EntityRendererRegistry.register(ModEntities.BARMAN, BarmanRenderer::new);
		EntityRendererRegistry.register(ModEntities.CAR, CarRenderer::new);
		EntityRendererRegistry.register(ModEntities.VAN, VanCarRenderer::new);
		// комната 3: мини-Говнозавр и скелет с ракетой
		net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(ru.vadim.client.render.GovnozavrModel.LAYER,
				ru.vadim.client.render.GovnozavrModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.GOVNOZAVR, ru.vadim.client.render.GovnozavrRenderer::new);
		EntityRendererRegistry.register(ModEntities.ROCKET_SKELETON, net.minecraft.client.renderer.entity.SkeletonRenderer::new);
		BlockEntityRenderers.register(ModBlocks.NOTES_BE, NotesBoardRenderer::new);
		BlockEntityRenderers.register(ModBlocks.TV_BE, TvRenderer::new);
		BlockEntityRenderers.register(ru.vadim.block.ZavodBlocks.CRT_FEED_BE, ru.vadim.client.render.CrtFeedRenderer::new);
		BlockEntityRenderers.register(ModBlocks.SHOWCASE_BE, ShowcaseRenderer::new);
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, ModBlocks.NOTES, ModBlocks.READY, ModBlocks.SPEAKER,
				ru.vadim.block.ZavodBlocks.CHAIN, ru.vadim.block.ZavodBlocks.CHAIN_CUT, ru.vadim.block.ZavodBlocks.LAMP_HEAD,
				ru.vadim.block.ZavodBlocks.CABLES, ru.vadim.block.ZavodBlocks.CABLE_HANG);
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.TRANSLUCENT, ModBlocks.GARLAND, ModBlocks.SHOWCASE, ModBlocks.VAN);

		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.menu.MenuAudio::tick);
		ClientTickEvents.END_CLIENT_TICK.register(MenuShot::tick);
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.MenuShot.TYPE, (p, ctx) -> MenuShot.start());
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null) {
				BarJazz.stopAll();
				ru.vadim.client.sound.ZavodAmbience.stopAll();
				return;
			}
			ClientAtmo.tick();
			LampLights.tick(mc);
			AmbientParticles.tick(mc);
			BarJazz.tick(mc);
			ru.vadim.client.sound.ZavodAmbience.tick(mc);
			Countdown.tick();
		});
		// катсцена с новостями (этап 2)
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.news.ClientNews::tick);
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.NewsStart.TYPE, (p, ctx) -> ru.vadim.client.news.ClientNews.onStart(p));
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.NewsVotes.TYPE, (p, ctx) -> ru.vadim.client.news.ClientNews.onVotes(p));
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.NewsEnd.TYPE, (p, ctx) -> ru.vadim.client.news.ClientNews.onEnd(p));
		HudRenderCallback.EVENT.register(ru.vadim.client.news.ClientNews::render);

		// ПКМ по бармену — разговор (только на клиенте, сервер не нужен)
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof BarmanEntity)) return InteractionResult.PASS;
			if (level.isClientSide()) Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new BarmanScreen()));
			return InteractionResult.SUCCESS;
		});

		// завод, взлом двери (этап 3б): подсказки и журнал охранника
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.ZavodHint.TYPE, (p, ctx) -> ru.vadim.client.zavod.ZavodHud.onHint(p));
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.ZavodJournal.TYPE, (p, ctx) -> ctx.client().setScreen(new ru.vadim.client.zavod.JournalScreen()));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> ru.vadim.client.zavod.ZavodHud.tick());
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.zavod.CctvFeed::tick);
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.zavod.ZavodFinalClient::tick);
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.ZavodFinal.TYPE, (p, ctx) -> ru.vadim.client.zavod.ZavodFinalClient.onPacket(p));
		HudRenderCallback.EVENT.register(ru.vadim.client.zavod.ZavodFinalClient::render);
		HudRenderCallback.EVENT.register(ru.vadim.client.zavod.ZavodHud::render);

		// комната 1 «Стулья» (этап 4)
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.Room1State.TYPE, (p, ctx) -> ru.vadim.client.room.Room1Client.onState(p));
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.Room1Keypad.TYPE, (p, ctx) -> ru.vadim.client.room.KeypadScreen.onResult(p));
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.room.Room1Client::tick);
		HudRenderCallback.EVENT.register(ru.vadim.client.room.Room1Client::render);
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.room.Room4Client::tick);
		HudRenderCallback.EVENT.register(ru.vadim.client.room.Room4Client::render);
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.Room4Panel.TYPE, (p, ctx) -> ru.vadim.client.room.Room4PanelScreen.onPanel(p));
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.Room4Fx.TYPE, (p, ctx) -> ru.vadim.client.room.Room4Client.onFx(p));
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.Room2State.TYPE, (p, ctx) -> ru.vadim.client.room.Room2Client.onState(p));
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.room.Room2Client::tick);
		HudRenderCallback.EVENT.register(ru.vadim.client.room.Room2Client::render);
		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.Room3State.TYPE, (p, ctx) -> ru.vadim.client.room.Room3Client.onState(p));
		ClientTickEvents.END_CLIENT_TICK.register(ru.vadim.client.room.Room3Client::tick);
		HudRenderCallback.EVENT.register(ru.vadim.client.room.Room3Client::render);

		ClientPlayNetworking.registerGlobalReceiver(HubPayloads.Countdown.TYPE, (p, ctx) -> Countdown.show(p.n()));
		HudRenderCallback.EVENT.register(Countdown::render);
		VadimNaTrone.LOG.info("Клиент Вадима готов.");
	}
}
