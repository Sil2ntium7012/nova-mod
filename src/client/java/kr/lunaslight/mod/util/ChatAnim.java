package kr.lunaslight.mod.util;

/**
 * 49-251차(사용자: "채팅 올라올 때 애니메이션 넣어 줘, 그 모드처럼"): 채팅 애니메이션(Chat Animation 모드 같은 것).
 *
 * <p>새 채팅 줄이 들어오면 채팅창 전체가 그 줄 높이만큼 아래에서 시작해 부드럽게 제자리로 올라온다(새 줄은 아래에서 밀고 들어온다).
 * 연달아 오면 남은 거리에 더해서 이어 올라간다. ChatAnimMixin이 줄 추가 앞뒤({@link #beforeAdd}/{@link #afterAdd})와 채팅 그리기 앞에서
 * {@link #offset()}만큼 아래로 옮겨 그린다.
 */
public final class ChatAnim {
	private ChatAnim() {
	}

	/** 채팅 기능의 [올라오는 애니메이션] 설정(매 틱 채팅 기능이 맞춘다). */
	public static volatile boolean enabled;
	private static final long DURATION_MS = 220L;

	private static float startPx;
	private static long startMs;
	private static int beforeSize = -1;

	private static java.lang.reflect.Field listField;
	private static java.lang.reflect.Method lineHeightM, scaleM;
	private static boolean resolved;

	private static void resolve(Object chat) {
		if (resolved) {
			return;
		}
		resolved = true;
		try {
			listField = LunaCompat.findField(chat.getClass(), "visibleMessages");
			if (listField != null) {
				listField.setAccessible(true);
			}
		} catch (Throwable ignored) {
		}
		lineHeightM = LunaCompat.findAnyMethod(chat.getClass(), "getLineHeight");
		scaleM = LunaCompat.findAnyMethod(chat.getClass(), "getChatScale");
	}

	private static int size(Object chat) {
		resolve(chat);
		try {
			Object l = listField == null ? null : listField.get(chat);
			return l instanceof java.util.List<?> list ? list.size() : -1;
		} catch (Throwable t) {
			return -1;
		}
	}

	/** 화면 픽셀(GUI 단위)로 본 채팅 한 줄 높이 = 줄 높이 × 채팅 크기. */
	private static float lineHeightPx(Object chat) {
		resolve(chat);
		int lh = 9;
		double scale = 1.0;
		try {
			if (lineHeightM != null && lineHeightM.invoke(chat) instanceof Number n) {
				lh = n.intValue();
			}
			if (scaleM != null && scaleM.invoke(chat) instanceof Number n) {
				scale = n.doubleValue();
			}
		} catch (Throwable ignored) {
		}
		return (float) (Math.max(1, lh) * Math.max(0.1, scale));
	}

	public static void beforeAdd(Object chat) {
		beforeSize = enabled ? size(chat) : -1;
	}

	public static void afterAdd(Object chat) {
		if (!enabled || beforeSize < 0) {
			return;
		}
		int added = size(chat) - beforeSize;
		beforeSize = -1;
		if (added <= 0) {
			added = 1;   // 줄 수 제한에 걸려 오래된 줄이 빠지면 크기가 안 늘 수 있다
		}
		added = Math.min(added, 4);   // 한꺼번에 많이 들어오면(다시 접기 등) 크게 튀지 않게
		long now = System.currentTimeMillis();
		startPx = Math.min(current(now) + added * lineHeightPx(chat), 60f);
		startMs = now;
	}

	private static float current(long now) {
		float t = (now - startMs) / (float) DURATION_MS;
		if (t >= 1f || startPx <= 0f) {
			return 0f;
		}
		float k = 1f - t;
		return startPx * k * k * k;   // 끝에서 부드럽게 멈춤(ease-out cubic)
	}

	/** 지금 채팅을 아래로 옮겨 그릴 거리(GUI 단위). 0이면 안 옮긴다. */
	public static float offset() {
		if (!enabled) {
			return 0f;
		}
		return current(System.currentTimeMillis());
	}
}
