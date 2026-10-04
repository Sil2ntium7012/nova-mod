package kr.lunaslight.mod.util;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.text.Text;

/**
 * 49-216차: 채팅 수신 알림(채팅 기록, TPS 읽기). 모듈이 Fabric 이벤트를 직접 쓰면 그 이벤트가 없는
 * 1.14.4~1.19.2에서 모듈 파일 자체를 빼야 했다(채팅/TPS/채팅 검색이 통째로 없던 이유). 이벤트를 이 한 곳에 모으고
 * 시대별로 두 벌을 둔다 - 이 파일은 Fabric 이벤트가 있는 1.19.3+ 판(loom-common.gradle이 골라 얹는다).
 * 옛 판(compat/chat-events-legacy)은 ChatHudMixin/ChatSignedMixin이 넘겨 주는 줄로 같은 일을 한다.
 */
public final class ChatEvents {
	private ChatEvents() {
	}

	public interface Listener {
		void onMessage(Text message);
	}

	/** 액션 바가 아닌 게임 메시지 + 플레이어 채팅. */
	public static void register(Listener l) {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				l.onMessage(message);
			}
		});
		try {
			ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
				l.onMessage(message));
		} catch (Throwable ignored) {
		}
	}

	/** 채팅창 입구(믹스인)에서 - 이 판은 Fabric 이벤트가 이미 알려 주므로 할 일 없음. */
	public static void fromHud(Text message) {
	}
}
