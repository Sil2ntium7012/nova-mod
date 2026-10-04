package kr.lunaslight.mod.util;

import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 49-216차: 채팅 수신 알림 - Fabric 메시지 이벤트(ClientReceiveMessageEvents)가 없는 1.14.4~1.19.2 판.
 * 채팅창 입구 믹스인(ChatHudMixin의 addMessage(Text), 1.19.1+는 ChatSignedMixin의 서명 채팅 입구)이 넘겨 주는
 * 줄을 그대로 알린다. 이 시대엔 시스템 메시지도 플레이어 채팅도 전부 그 입구로 들어온다(바이트코드 확인 -
 * ChatHudMixin 주석). 한 줄이 두 입구를 거쳐 올 수 있어(1.19.1 시스템 메시지) 같은 객체는 한 번만 알린다.
 */
public final class ChatEvents {
	private ChatEvents() {
	}

	public interface Listener {
		void onMessage(Text message);
	}

	private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
	private static Object last;

	public static void register(Listener l) {
		LISTENERS.add(l);
	}

	public static void fromHud(Text message) {
		if (message == null || message == last) {
			return;
		}
		last = message;
		for (Listener l : LISTENERS) {
			try {
				l.onMessage(message);
			} catch (Throwable t) {
				LunaCompat.warnOnce("chatEvents", t);
			}
		}
	}
}
