package kr.lunaslight.mod.util;

/**
 * 49-39차: 화면(Screen)이 아닌 곳(작업대 옆 패널의 검색창 등)이 키보드 글자 입력을 받게 하는 허브.
 * LunaCompat.ensureTextCapture()가 GLFW의 key/charmods 콜백을 감싸(마우스 클릭 계수기와 같은 방식,
 * 버전 무관·믹스인 불필요) 대상이 "받고 싶다"(wants)고 하는 동안엔 글자·키를 여기로 보내고 마인크래프트
 * 에는 넘기지 않는다(E를 눌러도 인벤토리가 안 닫히고, 한글 IME 조합 결과도 charmods로 그대로 온다).
 */
public final class TextCapture {
	private TextCapture() {
	}

	public interface Target {
		/** 지금 입력을 받을 상태인가(검색창에 포커스가 있는가). */
		boolean wants();

		/** 글자 하나(코드포인트). */
		void onChar(int codePoint);

		/**
		 * GLFW 키 이벤트. action 1 = 누름, 2 = 반복, 0 = 뗌. true면 마인크래프트에 넘기지 않는다.
		 * (Backspace/Enter/Esc 처리, 나머지 글자 키도 대개 삼켜야 E로 창이 닫히지 않는다)
		 */
		boolean onKey(int key, int action, int modifiers);
	}

	private static volatile Target target;

	public static void setTarget(Target t) {
		target = t;
	}

	public static boolean active() {
		Target t = target;
		try {
			return t != null && t.wants();
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 콜백 래퍼에서: 소비했으면 true. */
	public static boolean feedChar(int codePoint) {
		Target t = target;
		if (t == null) {
			return false;
		}
		try {
			if (!t.wants()) {
				return false;
			}
			t.onChar(codePoint);
			return true;
		} catch (Throwable e) {
			LunaCompat.warnOnce("textCapture:char", e);
			return false;
		}
	}

	public static boolean feedKey(int key, int action, int modifiers) {
		Target t = target;
		if (t == null) {
			return false;
		}
		try {
			return t.wants() && t.onKey(key, action, modifiers);
		} catch (Throwable e) {
			LunaCompat.warnOnce("textCapture:key", e);
			return false;
		}
	}
}
