package ru.vadim.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ru.vadim.VadimNaTrone;

public final class HubPayloads {
	private HubPayloads() {}

	/** Отсчёт перед стартом: 3, 2, 1, 0 = поехали. */
	public record Countdown(int n) implements CustomPacketPayload {
		public static final Type<Countdown> TYPE = new Type<>(VadimNaTrone.id("countdown"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Countdown> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, Countdown::n, Countdown::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Снять кадр для главного меню (клиент прячет интерфейс и делает скриншот). */
	public record MenuShot() implements CustomPacketPayload {
		public static final Type<MenuShot> TYPE = new Type<>(VadimNaTrone.id("menu_shot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, MenuShot> CODEC = StreamCodec.unit(new MenuShot());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Катсцена с новостями: старт (или пересинхронизация после пропуска) — тик мира, от которого клиент считает время. */
	public record NewsStart(long startTick, boolean skipped) implements CustomPacketPayload {
		public static final Type<NewsStart> TYPE = new Type<>(VadimNaTrone.id("news_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, NewsStart> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_LONG, NewsStart::startTick, ByteBufCodecs.BOOL, NewsStart::skipped, NewsStart::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Сколько игроков уже проголосовали за пропуск. */
	public record NewsVotes(int votes, int total) implements CustomPacketPayload {
		public static final Type<NewsVotes> TYPE = new Type<>(VadimNaTrone.id("news_votes"));
		public static final StreamCodec<RegistryFriendlyByteBuf, NewsVotes> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, NewsVotes::votes, ByteBufCodecs.VAR_INT, NewsVotes::total, NewsVotes::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Конец катсцены: moved = игроков перенесли ко входу в заброшку (иначе — отменили, остаёмся в баре). */
	public record NewsEnd(boolean moved) implements CustomPacketPayload {
		public static final Type<NewsEnd> TYPE = new Type<>(VadimNaTrone.id("news_end"));
		public static final StreamCodec<RegistryFriendlyByteBuf, NewsEnd> CODEC =
				StreamCodec.composite(ByteBufCodecs.BOOL, NewsEnd::moved, NewsEnd::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Клиент → сервер: голос за пропуск катсцены. */
	public record NewsSkip() implements CustomPacketPayload {
		public static final Type<NewsSkip> TYPE = new Type<>(VadimNaTrone.id("news_skip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, NewsSkip> CODEC = StreamCodec.unit(new NewsSkip());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Завод, взлом двери: подсказка внизу экрана (0 — нет) и прогресс 0..1. */
	public record ZavodHint(int hint, float progress) implements CustomPacketPayload {
		public static final Type<ZavodHint> TYPE = new Type<>(VadimNaTrone.id("zavod_hint"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ZavodHint> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, ZavodHint::hint, ByteBufCodecs.FLOAT, ZavodHint::progress, ZavodHint::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Завод: открыть журнал охранника. */
	public record ZavodJournal() implements CustomPacketPayload {
		public static final Type<ZavodJournal> TYPE = new Type<>(VadimNaTrone.id("zavod_journal"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ZavodJournal> CODEC = StreamCodec.unit(new ZavodJournal());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}
}
