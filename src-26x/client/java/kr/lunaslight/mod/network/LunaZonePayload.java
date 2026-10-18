package kr.lunaslight.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * 49-407차: LUNA FESTIVAL 서버 → 클라 {@code luna:zone}. 내용 = UTF-8 JSON 바이트 전체(길이 접두 없음).
 * 받는 쪽으로 등록해야 Fabric이 minecraft:register로 알리고 서버가 보낸다. {@link kr.lunaslight.mod.util.LunaMapData} 참고.
 */
public record LunaZonePayload(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LunaZonePayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("luna", "zone"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LunaZonePayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBytes(p.json().getBytes(StandardCharsets.UTF_8)),
			buf -> {
				byte[] b = new byte[buf.readableBytes()];
				buf.readBytes(b);
				return new LunaZonePayload(new String(b, StandardCharsets.UTF_8));
			});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
