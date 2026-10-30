package kr.lunaslight.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 49-431차: 너굴마을 스킬 키(NG-SkillKeydown 1.2.0과 같은 채널, 같은 모양 - 그 모드 jar를 읽어 확인).
 * <ul>
 *   <li>{@code ng:skillkey} 서버 → 클라: [1, 방식(1 = F1~F4, 2 = R F C V, 그 밖 = 없음), 스킬 모드(1 = 켬)]</li>
 *   <li>{@code ng:skillkey2} 서버 → 클라: [2, 스킬 모드, 4바이트 정수(big-endian) GLFW 키 4개(1~4번 스킬)] - 18바이트 이상</li>
 *   <li>{@code ng:skillcast} 클라 → 서버: 스킬 번호 1바이트(1~4)</li>
 * </ul>
 */
public final class NeogulSkillPayloads {
	private NeogulSkillPayloads() {
	}

	private static StreamCodec<RegistryFriendlyByteBuf, Bytes> bytesCodec(CustomPacketPayload.Type<Bytes> type) {
		return StreamCodec.of(
				(buf, p) -> buf.writeBytes(p.data()),
				buf -> {
					byte[] b = new byte[buf.readableBytes()];
					buf.readBytes(b);
					return new Bytes(type, b);
				});
	}

	/** 바이트 그대로인 서버 → 클라 신호(두 채널이 같은 꼴). */
	public record Bytes(CustomPacketPayload.Type<Bytes> kind, byte[] data) implements CustomPacketPayload {
		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return kind;
		}
	}

	public static final CustomPacketPayload.Type<Bytes> STATE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ng", "skillkey"));
	public static final CustomPacketPayload.Type<Bytes> STATE2 = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ng", "skillkey2"));
	public static final StreamCodec<RegistryFriendlyByteBuf, Bytes> STATE_CODEC = bytesCodec(STATE);
	public static final StreamCodec<RegistryFriendlyByteBuf, Bytes> STATE2_CODEC = bytesCodec(STATE2);

	/** 스킬 시전(클라 → 서버). */
	public record Cast(int slot) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Cast> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("ng", "skillcast"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cast> CODEC = StreamCodec.of(
				(buf, p) -> buf.writeByte(p.slot()),
				buf -> new Cast(buf.readByte()));

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
