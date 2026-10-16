package kr.lunaslight.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * 49-374차: LUNA FESTIVAL 서버로 보내는 키 신호 {@code luna:key}(클라 → 서버). 내용 = UTF-8 문자열 바이트 전체(길이 접두 없음).
 * "C" = 핫바 C 칸 쓰기(물약), "X" = 내 능력 창. 서버는 "Q", "F"도 받는다.
 */
public record LunaKeyPayload(String key) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LunaKeyPayload> TYPE =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("luna", "key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LunaKeyPayload> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeBytes(p.key().getBytes(StandardCharsets.UTF_8)),
			buf -> {
				byte[] b = new byte[buf.readableBytes()];
				buf.readBytes(b);
				return new LunaKeyPayload(new String(b, StandardCharsets.UTF_8));
			});

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
