package kr.lunaslight.mod.util;

/**
 * 49-41차: 거리 재기(MeasureModule)가 MinecraftClient의 공격/사용 클릭을 가로채기 위한 훅.
 * 믹스인(MeasureClickMixin)이 doAttack/doItemUse/handleBlockBreaking 머리에서 부른다 - 모듈 클래스를
 * 믹스인이 직접 참조하지 않게 분리(HealthBarHook·ScrollHook과 같은 이유).
 */
public final class MeasureHook {
	private MeasureHook() {
	}

	public interface Handler {
		/** 지금 클릭을 재기로 먹을 상태인가(막대기 + 웅크리기 등). */
		boolean active();

		/** 좌클릭(공격). true면 바닐라 공격/블록 깨기를 취소. */
		boolean onAttack();

		/** 우클릭(사용). true면 바닐라 사용을 취소. */
		boolean onUse();

		/** 49-253차: 가운데 클릭(블록 집기). true면 바닐라 집기를 취소. active()와 상관없이 불린다. */
		default boolean onPick() {
			return false;
		}

		/** 49-258차: 우클릭(사용) 직전에 늘 불린다(먹지 않음) - 설계도가 놓을 블록을 손에 쥐여 준다. */
		default void beforeUse() {
		}
	}

	/** 49-253차: 거리 재기 + 설계도(나무도끼 지점 찍기) - 클릭을 먹는 쪽이 여럿이 됐다. 먼저 active인 쪽이 먹는다. */
	private static final java.util.List<Handler> HANDLERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	public static void set(Handler h) {
		add(h);
	}

	public static void add(Handler h) {
		if (h != null && !HANDLERS.contains(h)) {
			HANDLERS.add(h);
		}
	}

	public static boolean active() {
		for (Handler h : HANDLERS) {
			try {
				if (h.active()) {
					return true;
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("measure:active", t);
			}
		}
		return false;
	}

	public static boolean attack() {
		for (Handler h : HANDLERS) {
			try {
				if (h.active()) {
					return h.onAttack();
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("measure:attack", t);
			}
		}
		return false;
	}

	public static boolean use() {
		for (Handler h : HANDLERS) {
			try {
				h.beforeUse();
			} catch (Throwable t) {
				LunaCompat.warnOnce("measure:beforeUse", t);
			}
		}
		for (Handler h : HANDLERS) {
			try {
				if (h.active()) {
					return h.onUse();
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("measure:use", t);
			}
		}
		return false;
	}

	public static boolean pick() {
		for (Handler h : HANDLERS) {
			try {
				if (h.onPick()) {
					return true;
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("measure:pick", t);
			}
		}
		return false;
	}
}
