package kr.lunaslight.mod.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 49-23차: 마우스 휠 가로채기 허브. MouseScrollMixin(Mouse#onMouseScroll HEAD)이 줌 다음으로 여기에
 * 묻고, 등록된 처리기 중 하나라도 true를 돌려주면 바닐라 처리(핫바 슬롯 이동/화면 스크롤)를 취소한다.
 * Alt+휠 핫바 줄 교체(HotbarRowSwapModule), 부드러운 휠(SmoothScrollModule)이 쓴다.
 * 모듈 클래스를 mixin에서 직접 참조하지 않게 분리(src/ 와 src-26x/ 양쪽 트리에 동일 파일).
 */
public final class ScrollHook {
	private ScrollHook() {
	}

	public interface Handler {
		/** 원시 휠 값(GLFW, 위 = 양수). 소비했으면 true. */
		boolean onScroll(double horizontal, double vertical);
	}

	private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();

	public static void register(Handler handler) {
		if (handler != null && !HANDLERS.contains(handler)) {
			HANDLERS.add(handler);
		}
	}

	public static boolean dispatch(double horizontal, double vertical) {
		for (Handler h : HANDLERS) {
			try {
				if (h.onScroll(horizontal, vertical)) {
					return true;
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("scrollHook", t);
			}
		}
		return false;
	}
}
