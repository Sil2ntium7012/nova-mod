package kr.lunaslight.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 49-458차(사용자: "영어일 때 마크 WASD 안 눌리잖아 이런 키들이" - 키바인드 키 두 개를 겹쳐 두고 싶었던 이유): 윈도우 입력기 상태에 따라
 * 게임이 글자 키(W A S D, E, Q …)를 못 받는 때가 있다(26.1~26.2는 바닐라가 입력기를 다루고, 한국어 입력기가 글자 키를 먼저 먹으면 GLFW가 키를
 * 안 보내거나 -1로 보냄). 그래서 <b>실제로 눌린 키(물리 키 상태)</b>를 직접 보고 마크 키를 맞춘다.
 *
 * <ul>
 *   <li>매 틱(화면 없음, 게임 창이 앞일 때): 글자 키(A~Z)에 묶인 마크 키마다 user32 GetAsyncKeyState(가상 키 = 같은 글자)로 눌림을 본다.</li>
 *   <li>눌려 있는데 마크 키가 안 눌린 상태 = 게임이 키를 못 받은 것 → 눌림 + 한 번 누름(인벤토리 열기 같은 것)으로 넣고, 떼면 뗀다.</li>
 *   <li>게임이 제대로 받고 있으면(마크 키가 이미 눌림) 아무것도 안 한다 - 그래서 영어/한글 어느 상태든 같은 동작.</li>
 * </ul>
 * 윈도우 + GLFW(26.1~26.2)만. 26.3(SDL)은 키를 물리 자리로 받아서 필요 없다. 네이티브 호출은 LWJGL 것만(WinIme와 같은 방식).
 */
public final class ImeKeyFix {
	private ImeKeyFix() {
	}

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

	private static boolean resolved;
	private static long fnGetAsyncKeyState;
	private static Method invokeI;
	/** 우리가 눌러 둔 마크 키(뗄 때 우리가 뗀다). */
	private static final Set<KeyMapping> FORCED = new HashSet<>();
	private static boolean logged;

	private static boolean resolve() {
		if (resolved) {
			return invokeI != null && fnGetAsyncKeyState != 0;
		}
		resolved = true;
		if (!WINDOWS || LunaInput.SDL) {
			return false;
		}
		try {
			Class<?> lib = Class.forName("org.lwjgl.system.windows.WindowsLibrary");
			Object user32 = lib.getConstructor(String.class).newInstance("user32");
			fnGetAsyncKeyState = (Long) lib.getMethod("getFunctionAddress", CharSequence.class).invoke(user32, "GetAsyncKeyState");
			invokeI = Class.forName("org.lwjgl.system.JNI").getMethod("invokeI", int.class, long.class);
		} catch (Throwable t) {
			invokeI = null;
			LunaCompat.warnOnce("imeKeyFix:resolve", t);
		}
		return invokeI != null && fnGetAsyncKeyState != 0;
	}

	private static boolean physicallyDown(int vk) {
		try {
			int r = (Integer) invokeI.invoke(null, vk, fnGetAsyncKeyState);
			return (r & 0x8000) != 0;
		} catch (Throwable t) {
			return false;
		}
	}

	private static void releaseAll() {
		for (KeyMapping km : FORCED) {
			try {
				km.setDown(false);
			} catch (Throwable ignored) {
			}
		}
		FORCED.clear();
	}

	public static void tick(Minecraft mc) {
		if (!WINDOWS || LunaInput.SDL) {
			return;
		}
		if (mc == null || mc.player == null || LunaCompat.screenOf(mc) != null || !mc.isWindowActive()) {
			if (!FORCED.isEmpty()) {
				releaseAll();
			}
			return;
		}
		if (!resolve()) {
			return;
		}
		try {
			for (KeyMapping km : mc.options.keyMappings) {
				if (km == null || km.isUnbound() || km instanceof net.minecraft.client.ToggleKeyMapping) {   // 켜고 끄는 키는 눌림 뜻이 달라 뺌
					continue;
				}
				Object k = LunaCompat.boundKeyOfBinding(km);
				if (!(k instanceof InputConstants.Key key) || !"KEYSYM".equals(key.getType().name())) {
					continue;
				}
				int code = key.getValue();
				if (code < InputConstants.KEY_A || code > InputConstants.KEY_Z) {
					continue;   // 글자 키만(GLFW A~Z = 윈도우 가상 키 'A'~'Z')
				}
				int vk = 'A' + (code - InputConstants.KEY_A);
				boolean phys = physicallyDown(vk);
				if (phys && !km.isDown()) {
					km.setDown(true);
					if (FORCED.add(km)) {
						KeyMapping.click(key);
						if (!logged) {
							logged = true;
							kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 입력기 때문에 게임이 못 받은 키를 물리 키로 대신 넣습니다 (첫 키: " + km.getName() + ")");
						}
					}
				} else if (!phys && FORCED.remove(km)) {
					km.setDown(false);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("imeKeyFix", t);
		}
	}
}
