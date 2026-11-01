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

	/**
	 * 49-467차: 입력기가 E를 먹으면 인벤토리를 E로 닫지도 못했다. 상자류 화면에서(글 쓰는 칸이 아닐 때) 인벤토리 키를 새로 누른 걸 보고,
	 * 한 틱 기다려도 같은 화면이 그대로면(게임이 키를 못 받음) 대신 닫는다. 게임이 받았으면 그 사이 이미 닫혀 있어 아무 일 없다.
	 */
	private static Object pendingClose;

	private static void closeByInventoryKey(Minecraft mc) {
		Object screen = LunaCompat.screenOf(mc);
		if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) || HangulInput.textFocused() || !resolve()) {
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
		try {
			boolean inGame = mc != null && mc.player != null && LunaCompat.screenOf(mc) == null && mc.isWindowActive();
			if (inGame) {
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
				closeByInventoryKey(mc);
				rememberPhys();   // 화면에서 누른 채 넘어오는 키를 새 누름으로 치지 않게
			} else {
				pendingClose = null;
			}
			return;
		}
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
