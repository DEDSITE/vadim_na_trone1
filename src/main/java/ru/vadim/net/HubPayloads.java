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
}
