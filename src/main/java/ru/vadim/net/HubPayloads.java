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

	/** Катсцена с новостями: старт / пересинхронизация — startTick = сколько тиков прошло от старта (может быть < 0). */
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

	/** Финал завода (3г): phase 1 — захлопнулась гермодверь, 2 — газ (elapsed — тиков от начала газа), 3 — очнулись в комнате. */
	public record ZavodFinal(int phase, int elapsed) implements CustomPacketPayload {
		public static final Type<ZavodFinal> TYPE = new Type<>(VadimNaTrone.id("zavod_final"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ZavodFinal> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, ZavodFinal::phase, ByteBufCodecs.VAR_INT, ZavodFinal::elapsed, ZavodFinal::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/**
	 * Комната 1 (этап 4): состояние для клиента. phase — Room1.P_*, phaseTick — тиков от начала фазы,
	 * timeLeft — тиков до конца таймера, seatYaw — куда смотрит стул (NaN — не сидишь), tied — привязан (встать нельзя),
	 * intro — с полным вступлением (false — перезапуск после проигрыша).
	 */
	public record Room1State(int phase, int phaseTick, int timeLeft, float seatYaw, boolean tied, boolean intro) implements CustomPacketPayload {
		public static final Type<Room1State> TYPE = new Type<>(VadimNaTrone.id("room1_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room1State> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Room1State::phase, ByteBufCodecs.VAR_INT, Room1State::phaseTick, ByteBufCodecs.VAR_INT, Room1State::timeLeft,
				ByteBufCodecs.FLOAT, Room1State::seatYaw, ByteBufCodecs.BOOL, Room1State::tied, ByteBufCodecs.BOOL, Room1State::intro, Room1State::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Комната 1: открыть экран кодового замка (result: 0 — открыть, 1 — код верный, 2 — неверный). */
	public record Room1Keypad(int result) implements CustomPacketPayload {
		public static final Type<Room1Keypad> TYPE = new Type<>(VadimNaTrone.id("room1_keypad"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room1Keypad> CODEC =
				StreamCodec.composite(ByteBufCodecs.VAR_INT, Room1Keypad::result, Room1Keypad::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Клиент → сервер: набранный на замке код. */
	public record Room1Code(String code) implements CustomPacketPayload {
		public static final Type<Room1Code> TYPE = new Type<>(VadimNaTrone.id("room1_code"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room1Code> CODEC =
				StreamCodec.composite(ByteBufCodecs.stringUtf8(8), Room1Code::code, Room1Code::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Клиент → сервер: голос за пропуск вступления комнаты 1. */
	public record Room1Skip() implements CustomPacketPayload {
		public static final Type<Room1Skip> TYPE = new Type<>(VadimNaTrone.id("room1_skip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room1Skip> CODEC = StreamCodec.unit(new Room1Skip());
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/**
	 * Комната 2 «Студия» (этап 5): состояние для клиента. phase — Room2.P_*, phaseTick — тиков от начала фазы,
	 * lives — жизни (3..0), locked — сидишь в кабинке и встать нельзя, shocks — счётчик ударов током (вспышка при росте).
	 */
	public record Room2State(int phase, int phaseTick, int lives, boolean locked, int shocks) implements CustomPacketPayload {
		public static final Type<Room2State> TYPE = new Type<>(VadimNaTrone.id("room2_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room2State> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Room2State::phase, ByteBufCodecs.VAR_INT, Room2State::phaseTick, ByteBufCodecs.VAR_INT, Room2State::lives,
				ByteBufCodecs.BOOL, Room2State::locked, ByteBufCodecs.VAR_INT, Room2State::shocks, Room2State::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Комната 3 «Мини-мир»: фаза, тик фазы, сколько осталось (тики). */
	public record Room3State(int phase, int phaseTick, int timeLeft) implements CustomPacketPayload {
		public static final Type<Room3State> TYPE = new Type<>(VadimNaTrone.id("room3_state"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room3State> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Room3State::phase, ByteBufCodecs.VAR_INT, Room3State::phaseTick, ByteBufCodecs.VAR_INT, Room3State::timeLeft,
				Room3State::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/**
	 * Комната 4 (7б): экран щитка. sector — 0..2, values — сила тока 5 рубильников, mask — какие включены,
	 * zoneMin..zoneMax — зелёная зона, result: 0 — открыть/обновить, 1 — короткое замыкание, 2 — ток подан.
	 */
	public record Room4Panel(int sector, java.util.List<Integer> values, int mask, int zoneMin, int zoneMax, int result) implements CustomPacketPayload {
		public static final Type<Room4Panel> TYPE = new Type<>(VadimNaTrone.id("room4_panel"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room4Panel> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Room4Panel::sector, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(8)), Room4Panel::values,
				ByteBufCodecs.VAR_INT, Room4Panel::mask, ByteBufCodecs.VAR_INT, Room4Panel::zoneMin, ByteBufCodecs.VAR_INT, Room4Panel::zoneMax,
				ByteBufCodecs.VAR_INT, Room4Panel::result, Room4Panel::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}

	/** Комната 4 (7б), клиент → сервер: рубильники щитка (mask) и «ПОДАТЬ ТОК» (supply). */
	public record Room4PanelAct(int sector, int mask, boolean supply) implements CustomPacketPayload {
		public static final Type<Room4PanelAct> TYPE = new Type<>(VadimNaTrone.id("room4_panel_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Room4PanelAct> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Room4PanelAct::sector, ByteBufCodecs.VAR_INT, Room4PanelAct::mask, ByteBufCodecs.BOOL, Room4PanelAct::supply, Room4PanelAct::new);
		@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	}
}
