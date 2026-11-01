package kr.lunaslight.mod.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 49-89차(8-17, 사용자: "마크 키 지정에서 모든 키·마우스 2개씩 지정 가능하게"): 마인크래프트 키바인딩마다
 * <b>보조 키</b> 하나를 더 둔다. 바닐라는 키 하나만 받으므로, 입력이 키바인딩으로 흘러 들어가는 정적 길목
 * {@code KeyBinding.setKeyPressed(Key, boolean)} / {@code onKeyPressed(Key)}에서 <b>보조 키가 오면 원래 키가
 * 온 것처럼 한 번 더 흘려보낸다</b>({@code KeyBindingAltMixin}). 그래서 걷기·점프·인벤토리처럼 눌림 상태를 보는
 * 것도, 열기처럼 눌린 횟수를 세는 것도 그대로 된다. 화면이 열린 채 키를 보는 {@code matchesKey/matchesMouse}는
 * 1.15.2~1.21.8 시그니처에만 붙였다(1.21.9+는 KeyInput/Click로 갈려 못 붙임 - 그쪽은 게임 중 조작만 된다).
 *
 * <p>값은 {@code ShortcutsModule}의 "alt:<번역키>" 설정이 들고, 바뀔 때마다 {@link #rebuild}로 여기 표를 새로 만든다.
 * 표는 "보조 InputUtil.Key → 원래 bound Key"라 프레임마다 한 번 map.get이면 끝이다.
 */
public final class AltKeys {
	private AltKeys() {
	}

	/** 보조 Key → 원래 키바인딩에 묶인 Key. */
	private static volatile Map<Object, Object> altToReal = new HashMap<>();
	/** 49-457차: 보조 키에 같이 눌러야 하는 Ctrl/Shift/Alt(MOD_* 비트). 없으면 아무 때나. */
	private static volatile Map<Object, Integer> altMods = new HashMap<>();
	/**
	 * 49-125차(사용자: "서버에서 우클/좌클 키보드로 키 변경 금지"): 공격(좌클릭)/사용(우클릭)에 <b>키보드</b> 보조 키를
	 * 둔 것들. 멀티플레이 서버에 있는 동안({@link #serverMode})은 이 보조 키를 흘려보내지 않는다(싱글에서만 동작).
	 */
	private static volatile java.util.Set<Object> keyboardClickAlts = new java.util.HashSet<>();
	private static volatile boolean serverMode;
	private static final ThreadLocal<Boolean> REENTER = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/** 설정 → 표. (번역키, Luna 키 코드) 쌍을 받아 원래 키를 찾는다. */
	public static void rebuild(Map<String, Integer> altByTranslationKey, net.minecraft.client.Minecraft client) {
		Map<Object, Object> next = new HashMap<>();
		Map<Object, Integer> nextMods = new HashMap<>();
		java.util.Set<Object> blocked = new java.util.HashSet<>();
		try {
			for (LunaCompat.VanillaBinding vb : LunaCompat.vanillaKeyBindings(client)) {
				Integer code = altByTranslationKey.get(vb.id());
				if (code == null || code < 0) {
					continue;
				}
				Object alt = LunaCompat.inputKeyFromLunaCode(code);
				Object real = LunaCompat.boundKeyOfBinding(vb.binding());
				if (alt != null && real != null && !alt.equals(real)) {
					next.put(alt, real);
					int mods = LunaCompat.keyModifiers(code);   // 49-457차: 조합 보조 키(Ctrl + R 등)
					if (mods != 0) {
						nextMods.put(alt, mods);
					}
					if (isClickBinding(vb.id()) && !LunaCompat.isMouseKeyCode(LunaCompat.baseKey(code))) {
						blocked.add(alt);
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("altKeys:rebuild", t);
		}
		altToReal = next;
		altMods = nextMods;
		keyboardClickAlts = blocked;
	}

	/** 공격(좌클릭)·사용(우클릭) 바닐라 키바인딩인지. */
	public static boolean isClickBinding(String translationKey) {
		return "key.attack".equals(translationKey) || "key.use".equals(translationKey);
	}

	/** 지금 멀티플레이 서버에 있는지 - ShortcutsModule이 틱마다 갱신. */
	public static void setServerMode(boolean server) {
		serverMode = server;
	}

	public static boolean isServerMode() {
		return serverMode;
	}

	/**
	 * 이 키바인드 설정에 이 키를 지정해도 되는지. 서버에 있는 동안 공격/사용의 보조 키를 <b>키보드 키</b>로 두는 것만 막는다
	 * (마우스 버튼은 됨). 막히면 그 이유(화면에 띄울 문구), 아니면 null.
	 */
	public static String blockedReason(String settingId, int lunaKeyCode) {
		if (settingId == null || !settingId.startsWith("alt:") || !isClickBinding(settingId.substring(4))) {
			return null;
		}
		if (lunaKeyCode < 0 || LunaCompat.isMouseKeyCode(LunaCompat.baseKey(lunaKeyCode))) {
			return null;
		}
		return serverMode ? "서버에서는 공격/사용 키를 키보드로 바꿀 수 없습니다" : null;
	}

	public static boolean isEmpty() {
		return altToReal.isEmpty();
	}

	/** 이 Key가 누군가의 보조 키면 그 원래 Key, 아니면 null. 재진입(우리가 흘려보낸 호출) 중엔 null. */
	public static Object realFor(Object key) {
		return realFor(key, true);
	}

	/**
	 * 49-457차: pressing = 누르는 쪽(눌림, 클릭, 화면의 키 확인)이면 조합 보조 키의 Ctrl/Shift/Alt가 눌려 있어야 한다.
	 * 떼는 쪽은 조합과 상관없이 넘긴다(보조키를 먼저 떼도 원래 키가 눌린 채로 남지 않게).
	 */
	public static Object realFor(Object key, boolean pressing) {
		if (key == null || REENTER.get()) {
			return null;
		}
		if (pressing) {
			Integer need = altMods.get(key);
			if (need != null && (LunaCompat.heldModifiersNow() & need) != need) {
				return null;
			}
		}
		if (serverMode && keyboardClickAlts.contains(key)) {
			return null;   // 49-125차: 서버에서는 공격/사용의 키보드 보조 키를 흘려보내지 않는다
		}
		return altToReal.get(key);
	}

	/** 원래 키로 한 번 더 흘려보내는 동안 재진입을 막는다. */
	public static void forward(Runnable r) {
		REENTER.set(Boolean.TRUE);
		try {
			r.run();
		} finally {
			REENTER.set(Boolean.FALSE);
		}
	}
}
