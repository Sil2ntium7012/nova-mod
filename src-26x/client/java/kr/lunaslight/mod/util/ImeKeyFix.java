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
	/**
	 * 49-467차(사용자: "어떤 분이 E로 인벤이 안 닫힌대 - 닫히다 창이 다시 열려"): 인벤토리에서 E를 누르면 화면이 그 키로 닫히는데, 바닐라는 화면이
	 * 먹은 키를 마크 키 눌림으로 안 남긴다 - 손이 아직 E에 있는 다음 틱에 우리가 "물리로는 눌렸는데 마크 키는 안 눌림"으로 보고 E를 다시 넣어
	 * 인벤토리가 또 열렸다. 이제 게임 화면(화면 없음)에서 <b>새로 누른 순간</b>(직전 틱엔 안 눌림)만 대신 넣는다. 화면이 열려 있는 동안에도
	 * 물리 상태는 기억해 두어, 화면에서 누른 채 넘어온 키는 새 누름으로 치지 않는다.
	 */
	private static final boolean[] LAST_PHYS = new boolean[26];
	private static boolean lastWasScreen = true;

	/*
	 * 49-467차의 closeByInventoryKey(입력기가 E를 먹으면 대신 닫기)는 49-471차에 뺐다(사용자: "이제 E를 수십 번 눌러야 인벤이 열려"):
	 * 게임에서 E로 인벤토리가 열린 바로 다음 틱에 손이 아직 E에 있으면 "상자 화면에서 E를 새로 누름"으로 보고 도로 닫았다.
	 * 이제 글 쓰는 칸이 아닌 화면에서도 입력기를 떼어 두므로 바닐라가 E를 그대로 받아 닫는다.
	 */

	/**
	 * 49-475차: 그래도 입력기가 E를 먹는 때(Ixeris 같은 모드, 다른 입력기 프로그램)를 위한 안전망 - 49-467차 것을 고쳐 다시 넣었다.
	 * 467차는 E로 인벤토리를 연 바로 다음 틱에 손이 아직 E에 있으면 "새로 누름"으로 보고 도로 닫았다(수십 번 눌러야 열림). 이제
	 * 화면에 들어온 첫 틱은 기준만 잡고, 화면이 3틱 넘게 열려 있을 때 E를 새로 누른 것만 본다. 한 틱 기다려 같은 화면이면(게임이 못 받음) 닫는다.
	 */
	private static Object pendingClose;
	private static Object ageScreen;
	private static int screenAge;

	private static void closeFallback(Minecraft mc, Object screen) {
		if (screen != ageScreen) {
			ageScreen = screen;
			screenAge = 0;
			pendingClose = null;
			return;
		}
		screenAge++;
		if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) || screenAge < 3
				|| HangulInput.textFocused() || !resolve()) {
			pendingClose = null;
			return;
		}
		if (pendingClose != null) {
			Object p = pendingClose;
			pendingClose = null;
			if (p == screen && mc.player != null) {
				mc.player.closeContainer();
				return;
			}
		}
		try {
			Object k = LunaCompat.boundKeyOfBinding(mc.options.keyInventory);
			if (k instanceof InputConstants.Key key && "KEYSYM".equals(key.getType().name())
					&& key.getValue() >= InputConstants.KEY_A && key.getValue() <= InputConstants.KEY_Z) {
				int li = key.getValue() - InputConstants.KEY_A;
				if (physicallyDown('A' + li) && !LAST_PHYS[li]) {
					pendingClose = screen;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("imeKeyFix:close", t);
		}
	}

	private static void rememberPhys() {
		if (!resolve()) {
			return;
		}
		for (int i = 0; i < 26; i++) {
			LAST_PHYS[i] = physicallyDown('A' + i);
		}
	}

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

	/** F3(디버그 키), Ctrl, Alt 중 하나라도 눌려 있나 - 그때 글자 키는 바닐라 조합키라 대신 넣지 않는다. */
	private static boolean comboHeld(Minecraft mc) {
		try {
			if (mc.options.keyDebugModifier.isDown()) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		return physicallyDown(0x72) || physicallyDown(0x11) || physicallyDown(0x12);   // VK_F3, VK_CONTROL, VK_MENU(Alt)
	}

	private static java.lang.reflect.Method overlayGetter;
	private static boolean overlayLooked;

	/** 리소스 로딩 화면이 떠 있나(26.1~26.2 Minecraft.getOverlay). */
	private static boolean loading(Minecraft mc) {
		if (!overlayLooked) {
			overlayLooked = true;
			try {
				overlayGetter = Minecraft.class.getMethod("getOverlay");
			} catch (Throwable t) {
				overlayGetter = null;
			}
		}
		try {
			return overlayGetter != null && overlayGetter.invoke(mc) != null;
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
		// 49-468차(사용자: "여전히 한글에서 잘 안 움직여, 제대로 해 봐, korean-chat-patch처럼"): 그 모드(CocoaInput 기반)처럼 글 쓰는 화면이
		// 아닐 때는 윈도우 입력기를 게임 창에서 아예 떼어 낸다(ImmAssociateContext(창, NULL)). 그러면 한글 상태여도 윈도우가 글자 키를 먹지 않고
		// 진짜 키(W A S D …)로 보내 바닐라가 그대로 받는다. 화면(채팅, 상자 …)이 열리면 다시 붙여 예전처럼 한글을 친다.
		// (26.1~26.2만 - 49-390차에 늘 떼어 두었더니 바닐라 입력칸과 싸웠던 건 채팅 중에도 떼서였다. 게임 화면에서는 입력칸이 없다.)
		// 49-471차: 게임 화면만이 아니라 글 쓰는 칸이 없는 화면(인벤토리, 상자 …)에서도 뗀다 - 한글 상태여도 E로 바로 닫힌다.
		// 채팅, 표지판, 책, 초점 받은 입력칸(검색, 모루 …)일 때만 붙여 한글을 친다.
		try {
			boolean detach = mc != null && mc.isWindowActive() && !HangulInput.textFocused();
			if (detach) {
				WinIme.detach();
			} else {
				WinIme.restore();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("imeKeyFix:detach", t);
		}
		if (mc == null || mc.player == null || LunaCompat.screenOf(mc) != null || !mc.isWindowActive()) {
			if (!FORCED.isEmpty()) {
				releaseAll();
			}
			lastWasScreen = true;
			if (mc != null && mc.isWindowActive()) {
				closeFallback(mc, LunaCompat.screenOf(mc));
				rememberPhys();   // 화면에서 누른 채 넘어오는 키를 새 누름으로 치지 않게
			} else {
				pendingClose = null;
			}
			return;
		}
		ageScreen = null;
		pendingClose = null;
		if (!resolve()) {
			return;
		}
		boolean[] now = new boolean[26];
		for (int i = 0; i < 26; i++) {
			now[i] = physicallyDown('A' + i);
		}
		if (lastWasScreen) {
			// 화면이 막 닫힌 틱: 지금 눌려 있는 키는 화면에서 누른 것(닫은 E 등) - 새 누름이 아니다
			lastWasScreen = false;
			System.arraycopy(now, 0, LAST_PHYS, 0, 26);
			return;
		}
		// 49-509차(사용자: "F3+T 하면 리소스팩 로딩 뒤에 채팅이 열려 버린대"): F3+T는 바닐라가 디버그 키로 쓰고 T(채팅) 키를 일부러 안 누른다.
		// 그걸 "물리로는 눌렸는데 마크 키가 안 눌림 = 입력기가 먹음"으로 보고 T를 대신 넣어서, 로딩이 끝나자마자 채팅이 열렸다.
		// F3(디버그 키)나 Ctrl/Alt를 누르고 있을 때, 그리고 로딩 화면 중에는 대신 넣지 않는다(그 사이 누른 키는 새 누름으로 치지 않게 기억만).
		if (comboHeld(mc) || loading(mc)) {
			System.arraycopy(now, 0, LAST_PHYS, 0, 26);
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
				int li = code - InputConstants.KEY_A;
				boolean phys = now[li];
				boolean edge = phys && !LAST_PHYS[li];
				if (phys && !km.isDown() && (edge || FORCED.contains(km))) {
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
		System.arraycopy(now, 0, LAST_PHYS, 0, 26);
	}
}
