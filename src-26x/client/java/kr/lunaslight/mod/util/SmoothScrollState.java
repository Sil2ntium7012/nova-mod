package kr.lunaslight.mod.util;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-24차: 부드러운 휠 재작성. 예전(49-23차)엔 휠 이벤트를 가로채 프레임마다 Screen#mouseScrolled로 나눠 보냈는데,
 * 화면 종류 판정/이벤트 훅이 버전마다 불안정해 "휠이 아예 안 먹는" 문제가 났다. 이제 목록 위젯 자체의 스크롤
 * 설정자(EntryListWidget#setScrollAmount ≤1.21.3 / ScrollableWidget#setScrollY 1.21.4+)를 mixin으로 가로채
 * 목표값만 기억하고, 목록이 그려질 때마다(renderList) 현재값을 목표로 지수 보간해 진짜 설정자를 부른다.
 * 휠 이벤트는 바닐라 그대로라 어떤 화면에서도 스크롤 자체가 막히지 않고, 목록이 안 그려지는 위젯(프레임 훅이
 * 없는 클래스)은 400ms 안에 감지해 그 클래스는 다시는 가로채지 않는다(안전장치).
 */
public final class SmoothScrollState {
	private SmoothScrollState() {
	}

	public static volatile boolean enabled;
	public static volatile float speed = 14f;

	/** 위젯 → {목표값, 마지막 프레임 nanos(0 = 아직 없음), 첫 가로채기 nanos}. 위젯이 사라지면 같이 사라짐. */
	private static final Map<Object, double[]> STATE = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Set<Class<?>> BROKEN = ConcurrentHashMap.newKeySet();
	private static boolean applying;

	private static final Map<Class<?>, Method[]> METHODS = new ConcurrentHashMap<>(); // [getter, setter, max]

	private static Method[] methods(Class<?> cls) {
		return METHODS.computeIfAbsent(cls, c -> new Method[]{
				find(c, "getScrollAmount", "getScrollY"),
				findSetter(c),
				find(c, "getMaxScroll", "getMaxScrollY")});
	}

	private static Method find(Class<?> cls, String... names) {
		for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (m.getParameterCount() != 0) {
					continue;
				}
				for (String n : names) {
					if (LunaCompat.nameMatches(c, n, m.getName())) {
						m.setAccessible(true);
						return m;
					}
				}
			}
		}
		return null;
	}

	private static Method findSetter(Class<?> cls) {
		for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Method m : c.getDeclaredMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == double.class
						&& (LunaCompat.nameMatches(c, "setScrollAmount", m.getName()) || LunaCompat.nameMatches(c, "setScrollY", m.getName()))) {
					m.setAccessible(true);
					return m;
				}
			}
		}
		return null;
	}

	private static double current(Object widget) {
		Method m = methods(widget.getClass())[0];
		if (m == null) {
			return Double.NaN;
		}
		try {
			Object v = m.invoke(widget);
			return v instanceof Number n ? n.doubleValue() : Double.NaN;
		} catch (Throwable t) {
			return Double.NaN;
		}
	}

	private static double maxScroll(Object widget) {
		Method m = methods(widget.getClass())[2];
		if (m == null) {
			return Double.NaN;
		}
		try {
			Object v = m.invoke(widget);
			return v instanceof Number n ? n.doubleValue() : Double.NaN;
		} catch (Throwable t) {
			return Double.NaN;
		}
	}

	/** mixin(설정자 HEAD)에서 호출. true면 가로챈 것(바닐라 설정 취소). */
	public static boolean intercept(Object widget, double amount) {
		if (!enabled || applying || widget == null || BROKEN.contains(widget.getClass())) {
			return false;
		}
		Method[] ms = methods(widget.getClass());
		if (ms[0] == null || ms[1] == null) {
			return false;
		}
		double cur = current(widget);
		if (Double.isNaN(cur)) {
			return false;
		}
		long now = System.nanoTime();
		double[] st = STATE.get(widget);
		if (st == null) {
			st = new double[]{cur, 0, now};
			STATE.put(widget, st);
		} else if (st[1] == 0 && now - st[2] > 400_000_000L) {
			// 프레임 훅이 한 번도 안 돎(renderList를 안 쓰는 위젯) → 이 클래스는 포기하고 바닐라로
			BROKEN.add(widget.getClass());
			STATE.remove(widget);
			return false;
		}
		// 상대 누적: 바닐라는 "현재값 ± 한 칸"을 넘기므로 (요청 - 현재)만큼 목표를 옮긴다(빠른 연속 휠도 총량 보존)
		double target = st[0] + (amount - cur);
		double max = maxScroll(widget);
		if (!Double.isNaN(max)) {
			target = Math.max(0, Math.min(max, target));
		} else {
			target = Math.max(0, target);
		}
		st[0] = target;
		return true;
	}

	/** mixin(renderList HEAD)에서 매 프레임 호출 - 현재값을 목표로 보간해 진짜 설정자를 부른다. */
	public static void frame(Object widget) {
		double[] st = STATE.get(widget);
		if (st == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = st[1] == 0 ? 0.016f : (float) Math.min(0.1, (now - st[1]) / 1_000_000_000.0);
		st[1] = now;
		double cur = current(widget);
		if (Double.isNaN(cur)) {
			STATE.remove(widget);
			return;
		}
		double target = st[0];
		double next = cur + (target - cur) * (1.0 - Math.exp(-speed * dt));
		if (Math.abs(target - next) < 0.05) {
			next = target;
		}
		if (next != cur) {
			Method setter = methods(widget.getClass())[1];
			applying = true;
			try {
				setter.invoke(widget, next);
			} catch (Throwable t) {
				LunaCompat.warnOnce("smoothScroll:set", t);
				STATE.remove(widget);
				return;
			} finally {
				applying = false;
			}
		}
		if (next == target) {
			STATE.remove(widget);
		}
	}
}
