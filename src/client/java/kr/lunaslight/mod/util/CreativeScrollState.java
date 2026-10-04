package kr.lunaslight.mod.util;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-32차: 부드러운 휠 - 크리에이티브 인벤토리(사용자: "부드러운 휠 크리에이티브 창에선 동작 안 함").
 *
 * 크리에이티브 창은 목록 위젯(EntryListWidget)이 아니라 화면이 직접 scrollPosition을 굴려서
 * SmoothScrollState의 설정자 가로채기가 안 통한다. 대신 바닐라 mouseScrolled가
 * "휠 값에 정비례해" 스크롤을 옮기는 점을 이용한다 - 휠 값을 통째로 받아 두고(취소),
 * 매 프레임 그중 일부만 진짜 mouseScrolled로 흘려보내면 같은 총량이 부드럽게 나눠 들어간다.
 * 필드나 핸들러를 건드리지 않아 버전 차이(1.15~26.x)에 안전하다.
 */
public final class CreativeScrollState {
	private CreativeScrollState() {
	}

	/** 화면 → {남은 휠 양, 마지막 프레임 nanos, 마우스 x, 마우스 y}. 화면이 닫히면 같이 사라짐. */
	private static final Map<Object, double[]> STATE = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Class<?>, Object> METHODS = new ConcurrentHashMap<>();
	private static final Object MISS = new Object();
	private static boolean applying;

	/** mixin(mouseScrolled HEAD)에서 호출. true면 가로챈 것(바닐라 처리 취소). */
	public static boolean intercept(Object screen, double mouseX, double mouseY, double vertical) {
		if (!SmoothScrollState.enabled || applying || screen == null || vertical == 0) {
			return false;
		}
		// 스크롤 막대가 없으면(아이템이 한 화면에 다 들어오면) 바닐라처럼 그냥 흘려보낸다
		if (method(screen.getClass()) == null || !hasScrollbar(screen)) {
			return false;
		}
		double[] st = STATE.get(screen);
		if (st == null) {
			st = new double[]{0, 0, mouseX, mouseY};
			STATE.put(screen, st);
		}
		st[0] += vertical;
		st[2] = mouseX;
		st[3] = mouseY;
		return true;
	}

	/** mixin(render HEAD)에서 매 프레임 호출 - 쌓인 휠 양을 조금씩 흘려보낸다. */
	public static void frame(Object screen) {
		double[] st = STATE.get(screen);
		if (st == null) {
			return;
		}
		long now = System.nanoTime();
		float dt = st[1] == 0 ? 0.016f : (float) Math.min(0.1, (now - st[1]) / 1_000_000_000.0);
		st[1] = now;
		double step = st[0] * (1.0 - Math.exp(-SmoothScrollState.speed * dt));
		if (Math.abs(st[0] - step) < 0.01) {
			step = st[0];
		}
		st[0] -= step;
		if (Math.abs(st[0]) < 0.0001) {
			STATE.remove(screen);
		}
		deliver(screen, st[2], st[3], step);
	}

	private static void deliver(Object screen, double mouseX, double mouseY, double amount) {
		Method m = method(screen.getClass());
		if (m == null) {
			STATE.remove(screen);
			return;
		}
		applying = true;
		try {
			if (m.getParameterCount() == 4) {
				m.invoke(screen, mouseX, mouseY, 0.0, amount);
			} else {
				m.invoke(screen, mouseX, mouseY, amount);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("creativeScroll:deliver", t);
			STATE.remove(screen);
		} finally {
			applying = false;
		}
	}

	private static final Map<Class<?>, Object> SCROLLBAR = new ConcurrentHashMap<>();

	private static boolean hasScrollbar(Object screen) {
		Class<?> cls = screen.getClass();
		Object cached = SCROLLBAR.get(cls);
		if (cached == null) {
			cached = MISS;
			outer:
			for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class
							&& LunaCompat.nameMatches(c, "hasScrollbar", m.getName())) {
						m.setAccessible(true);
						cached = m;
						break outer;
					}
				}
			}
			SCROLLBAR.put(cls, cached);
		}
		if (!(cached instanceof Method m)) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(m.invoke(screen));
		} catch (Throwable t) {
			return false;
		}
	}

	/** mouseScrolled(DDD)Z(≤1.20.1) 또는 (DDDD)Z(1.20.2+). */
	private static Method method(Class<?> cls) {
		Object cached = METHODS.get(cls);
		if (cached == null) {
			Method found = null;
			outer:
			for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
				for (Method m : c.getDeclaredMethods()) {
					int n = m.getParameterCount();
					if ((n != 3 && n != 4) || m.getReturnType() != boolean.class
							|| !LunaCompat.nameMatches(c, "mouseScrolled", m.getName())) {
						continue;
					}
					boolean allDouble = true;
					for (Class<?> p : m.getParameterTypes()) {
						allDouble &= p == double.class;
					}
					if (allDouble) {
						m.setAccessible(true);
						found = m;
						break outer;
					}
				}
			}
			METHODS.put(cls, found == null ? MISS : found);
			return found;
		}
		return cached instanceof Method m ? m : null;
	}
}
