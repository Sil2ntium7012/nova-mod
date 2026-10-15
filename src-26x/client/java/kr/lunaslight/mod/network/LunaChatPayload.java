package kr.lunaslight.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * 49-360차: LUNA FESTIVAL 서버의 채팅 상태 패킷 {@code luna:chat}(서버 → 클라).
 * 내용 = UTF-8 JSON 바이트 전체(길이 접두 없음) - 읽을 때 남은 바이트를 전부 문자열로 읽는다.
 * 예: {"v":1,"say":"say","team":true,"admin":false}
 * 이 타입을 받는 쪽으로 등록하면 Fabric이 minecraft:register를 보내고, 서버는 글자 탭 줄을 안 보낸다.
 */
public record LunaChatPayload(String json) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LunaChatPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("luna", "chat"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LunaChatPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBytes(p.json().getBytes(StandardCharsets.UTF_8)),
			buf -> {
				byte[] b = new byte[buf.readableBytes()];
				buf.readBytes(b);
				return new LunaChatPayload(new String(b, StandardCharsets.UTF_8));
			});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
