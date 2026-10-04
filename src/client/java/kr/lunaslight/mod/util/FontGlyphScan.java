package kr.lunaslight.mod.util;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-159차: 서버 리소스팩 적용(F3+T) 끝에서 화면이 85~96초 멈추던 원인.
 *
 * StallWatch 스택 3번이 모두 FontStorage(26.x: FontSet)의 글꼴 고르기 안쪽,
 * "글자마다 글꼴 목록을 처음부터 훑는" 바닐라 반복(glyph = font.getGlyph(codePoint)) 한 줄에 있었다.
 * playfarm 팩은 minecraft:default 한 글꼴에 bitmap 제공자가 1535개라,
 * 바닐라 unifont 글자(수만 개)마다 1537개 글꼴을 다 물어본다(글자 수 x 글꼴 수).
 *
 * 여기서는 글꼴마다 "자기가 가진 글자 목록"을 한 번씩만 읽어 글자별 첫 글꼴 번호를 미리 구한다.
 * 그 뒤 바닐라 람다를 "그 번호부터 시작하는 목록"으로 다시 만들어 글자마다 부른다.
 * 그래서 고르는 규칙(첫 글꼴, MISSING 처리, 폭 기록, 쓰인 글꼴 모음)은 바닐라 코드 그대로다.
 *
 * 안전장치:
 * - 람다 모양이 예상과 다르거나 무엇이든 실패하면 바닐라 그대로 돈다.
 * - 공백(32)과 어느 목록에도 없는 글자는 원래 람다로 돌린다(1.19 이하의 32 특례와 같은 결과).
 * - 글꼴이 적으면(32개 미만) 손대지 않는다.
 * - -Dluna.fontscan=false로 끌 수 있다.
 * MC 타입을 안 쓰므로 main과 26x가 같은 파일이다(메서드는 "인자 없고 IntSet을 돌려주는 것"으로 찾는다).
 */
public final class FontGlyphScan {

	private static final int MIN_FONTS = 32;
	private static final boolean OFF = "false".equalsIgnoreCase(System.getProperty("luna.fontscan"));
	private static volatile boolean broken;
	private static final Map<Class<?>, Object> PROVIDED = new ConcurrentHashMap<>();
	private static final Object NONE = new Object();

	private FontGlyphScan() {}

	public static void forEach(IntSet codePoints, IntConsumer original) {
		if (OFF || broken || codePoints == null || original == null) {
			if (codePoints != null) runAll(codePoints, original);
			return;
		}
		Plan plan;
		try {
			plan = plan(original);
		} catch (Throwable t) {
			broken = true;
			log("[Nova] 글꼴 빠른 길 끔(준비 실패, 바닐라로 진행): " + t);
			plan = null;
		}
		if (plan == null) {
			runAll(codePoints, original);
			return;
		}
		long t0 = System.nanoTime();
		IntIterator it = codePoints.iterator();
		while (it.hasNext()) {
			int cp = it.nextInt();
			int i = cp == 32 ? -1 : plan.first.get(cp);
			IntConsumer c = i < 0 ? original : plan.at(i, original);
			c.accept(cp);
		}
		long ms = (System.nanoTime() - t0) / 1_000_000L;
		if (plan.fonts.size() >= 256 || ms >= 500) {
			log("[Nova] 글꼴 빠른 길: 글꼴 " + plan.fonts.size() + "개, 글자 " + codePoints.size() + "개, " + ms + "ms");
		}
	}

	/**
	 * 바닐라 그대로(원래 람다에 글자를 하나씩). 49-162차: IntSet.forEach(original)는 옛 fastutil(1.15.2 ~ 1.17.1)에서
	 * forEach(IntConsumer)와 forEach(Consumer)가 둘 다 맞아 "ambiguous"로 빌드가 깨져서 직접 돈다.
	 */
	private static void runAll(IntSet codePoints, IntConsumer original) {
		IntIterator it = codePoints.iterator();
		while (it.hasNext()) {
			original.accept(it.nextInt());
		}
	}

	/** 원래 람다를 "목록 i번부터" 버전으로 다시 만들 재료. */
	private static final class Plan {
		final Constructor<?> ctor;
		final Object[] args;
		final int listArg;
		final List<?> fonts;
		final Int2IntOpenHashMap first;
		final IntConsumer[] made;
		final Class<?> listType;
		boolean failed;

		Plan(Constructor<?> ctor, Object[] args, int listArg, List<?> fonts, Int2IntOpenHashMap first) {
			this.ctor = ctor;
			this.listType = ctor.getParameterTypes()[listArg];
			this.args = args;
			this.listArg = listArg;
			this.fonts = fonts;
			this.first = first;
			this.made = new IntConsumer[fonts.size()];
		}

		IntConsumer at(int i, IntConsumer original) {
			if (i == 0 || failed) return original;
			IntConsumer c = made[i];
			if (c != null) return c;
			try {
				Object[] a = args.clone();
				List<?> sub = fonts.subList(i, fonts.size());
				// 람다가 목록을 ArrayList로 잡은 버전이면 subList 보기로는 못 넣으니 복사본을 넣는다
				if (!listType.isInstance(sub)) sub = new ArrayList<>(sub);
				a[listArg] = sub;
				c = (IntConsumer) ctor.newInstance(a);
			} catch (Throwable t) {
				// 만들기에 실패하면 원래 람다로(느리지만 결과는 같다) - 한 번만 알린다
				if (!failed) {
					failed = true;
					log("[Nova] 글꼴 빠른 길 일부 실패(바닐라로 진행): " + t);
				}
				c = original;
			}
			made[i] = c;
			return c;
		}
	}

	private static Plan plan(IntConsumer original) throws Exception {
		Class<?> cls = original.getClass();
		if (!cls.isSynthetic() && !cls.getName().contains("$$Lambda")) return null;

		List<Field> fields = new ArrayList<>();
		for (Field f : cls.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers())) fields.add(f);
		}
		Constructor<?>[] ctors = cls.getDeclaredConstructors();
		if (ctors.length != 1) return null;
		Constructor<?> ctor = ctors[0];
		Class<?>[] params = ctor.getParameterTypes();
		if (params.length != fields.size()) return null;

		Object[] args = new Object[params.length];
		int listArg = -1;
		for (int i = 0; i < params.length; i++) {
			Field f = fields.get(i);
			if (!params[i].isAssignableFrom(f.getType())) return null;
			f.setAccessible(true);
			args[i] = f.get(original);
			if (args[i] instanceof List) {
				if (listArg >= 0) return null; // List가 둘이면 어느 게 글꼴 목록인지 모른다
				listArg = i;
			}
		}
		if (listArg < 0) return null;
		List<?> fonts = (List<?>) args[listArg];
		int n = fonts.size();
		if (n < MIN_FONTS) return null;
		ctor.setAccessible(true);

		Int2IntOpenHashMap first = new Int2IntOpenHashMap();
		first.defaultReturnValue(-1);
		for (int i = 0; i < n; i++) {
			Object font = fonts.get(i);
			if (font == null) return null;
			IntSet provided = provided(font);
			if (provided == null) return null;
			IntIterator it = provided.iterator();
			while (it.hasNext()) first.putIfAbsent(it.nextInt(), i);
		}
		return new Plan(ctor, args, listArg, fonts, first);
	}

	/** Font#getProvidedGlyphs(1.16+) / GlyphProvider#getSupportedGlyphs(26.x) - 이름 대신 모양으로 찾는다(실행 중 이름은 intermediary). */
	private static IntSet provided(Object font) throws Exception {
		Object m = PROVIDED.computeIfAbsent(font.getClass(), FontGlyphScan::findProvided);
		if (m == NONE) return null;
		return (IntSet) ((Method) m).invoke(font);
	}

	private static Object findProvided(Class<?> cls) {
		Method found = null;
		for (Method m : cls.getMethods()) {
			if (Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) continue;
			if (!IntSet.class.isAssignableFrom(m.getReturnType())) continue;
			if (found != null && !found.getName().equals(m.getName())) return NONE; // 둘 이상이면 모른다
			if (found == null || m.getReturnType() == IntSet.class) found = m;
		}
		if (found == null) return NONE;
		try {
			found.setAccessible(true);
		} catch (Throwable ignored) {
			// 공개 인터페이스 메서드라 없어도 대개 불린다
		}
		return found;
	}

	private static void log(String msg) {
		try {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn(msg);
		} catch (Throwable t) {
			System.out.println(msg);
		}
	}
}
