package kr.lunaslight.mod.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 49-24차: 키보드 원시 입력 허브. KeyboardKeyMixin(Keyboard#onKey HEAD, 1.15.2~1.21.8 (JIIII)V)이 부르고,
 * 처리기가 true를 돌려주면 바닐라 처리(핫바 슬롯 선택 등)를 취소한다. Alt+1~3 핫바 줄 교체가 쓴다.
 * 1.21.9+는 onKey(J, I, KeyInput)로 바뀌어 믹스인이 적용되지 않는데(require=0), 그때는 모듈이 틱 폴링으로
 * 대신 처리한다(바닐라 슬롯 선택은 같이 일어남).
 */
public final class KeyHook {
	private KeyHook() {
	}

	public interface Handler {
		/** GLFW key/scancode/action(1=press,0=release,2=repeat)/modifiers. 소비했으면 true. */
		boolean onKey(int key, int scancode, int action, int modifiers);
	}

	private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();
	private static volatile boolean mixinActive;

	public static void register(Handler h) {
		if (h != null && !HANDLERS.contains(h)) {
			HANDLERS.add(h);
		}
	}

	/** 믹스인이 실제로 붙어 호출되고 있는지(폴링 폴백 여부 판단용). */
	public static boolean isMixinActive() {
		return mixinActive;
	}

	public static boolean dispatch(int key, int scancode, int action, int modifiers) {
		mixinActive = true;
		for (Handler h : HANDLERS) {
			try {
				if (h.onKey(key, scancode, action, modifiers)) {
					return true;
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("keyHook", t);
			}
		}
		return false;
	}
}
