package kr.lunaslight.mod.util;

import java.lang.reflect.Method;

/**
 * 49-313차: 윈도우 한글 입력기(IME)를 게임 창에서만 떼어 낸다(사용자: "윈도우 한글 입력기가 켜진 채로 들어오면 모드가 알아서 끔").
 *
 * <p>imm32.dll의 {@code ImmAssociateContext(창, NULL)} = 이 창에는 입력기를 안 붙인다. 그러면 한글 상태로 들어와도 키가 영문
 * 그대로 오고(키 설정도 한글 상태에서 먹는다), 한글은 모드가 직접 조합한다(HangulComposer). 떼어 낸 입력기는 기억해 두었다가
 * 기능을 끄면 돌려 붙인다.
 *
 * <p>네이티브 호출은 LWJGL이 원래 갖고 있는 것만 쓴다(WindowsLibrary로 dll을 열고 JNI.invokeP/PP/PPP로 부름 - 1.14.4 ~ 26.3의
 * LWJGL 3.2 ~ 3.4 모두 이름이 같다). 전부 리플렉션이라 GLFW가 없는 26.3(SDL)에서도 그대로 된다. 창은 GetActiveWindow로 찾는다
 * (게임 창을 만든 렌더 스레드에서 부르므로 게임 창이 앞에 있을 때만 그 창이 나온다). 윈도우가 아니거나 하나라도 못 찾으면 조용히 안 한다.
 * 게임 쪽(GLFW/SDL, 26.x 바닐라 입력칸)이 입력기를 다시 붙일 수 있어 매 틱 확인한다(이미 떼어 있으면 아무 일도 안 함).
 */
public final class WinIme {
	private WinIme() {
	}

	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");

	private static boolean resolved;
	private static boolean broken;
	private static long fnGetActiveWindow;
	private static long fnImmAssociateContext;
	private static long fnImmGetContext;
	private static long fnImmReleaseContext;
	private static Method invokeP;
	private static Method invokePP;
	private static Method invokePPP;

	/** 떼어 낸 입력기(창, 입력기). 돌려 붙일 때 쓴다. */
	private static long savedHwnd;
	private static long savedHimc;

	private static boolean resolve() {
		if (resolved) {
			return !broken;
		}
		resolved = true;
		if (!WINDOWS) {
			broken = true;
			return false;
		}
		try {
			Class<?> lib = Class.forName("org.lwjgl.system.windows.WindowsLibrary");
			Object user32 = lib.getConstructor(String.class).newInstance("user32");
			Object imm32 = lib.getConstructor(String.class).newInstance("imm32");
			Method addr = lib.getMethod("getFunctionAddress", CharSequence.class);
			fnGetActiveWindow = (Long) addr.invoke(user32, "GetActiveWindow");
			fnImmAssociateContext = (Long) addr.invoke(imm32, "ImmAssociateContext");
			fnImmGetContext = (Long) addr.invoke(imm32, "ImmGetContext");
			fnImmReleaseContext = (Long) addr.invoke(imm32, "ImmReleaseContext");
			Class<?> jni = Class.forName("org.lwjgl.system.JNI");
			invokeP = jniMethod(jni, "P", 1);
			invokePP = jniMethod(jni, "PP", 2);
			invokePPP = jniMethod(jni, "PPP", 3);
			if (fnGetActiveWindow == 0 || fnImmAssociateContext == 0 || invokeP == null || invokePP == null || invokePPP == null) {
				broken = true;
			}
		} catch (Throwable t) {
			broken = true;
			LunaCompat.warnOnce("winIme:resolve", t);
		}
		return !broken;
	}

	/** JNI.invoke{sig}(long × n) → long. 아주 옛 LWJGL은 call{sig}. */
	private static Method jniMethod(Class<?> jni, String sig, int longs) {
		Class<?>[] params = new Class<?>[longs];
		java.util.Arrays.fill(params, long.class);
		for (String prefix : new String[]{"invoke", "call"}) {
			try {
				Method m = jni.getMethod(prefix + sig, params);
				if (m.getReturnType() == long.class) {
					return m;
				}
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static long call(Method m, long... args) throws Exception {
		Object[] a = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			a[i] = args[i];
		}
		return (Long) m.invoke(null, a);
	}

	/** 게임 창이 앞에 있으면 입력기를 떼어 낸다(매 틱 불러도 된다). 렌더 스레드에서만. */
	public static void detach() {
		if (!resolve()) {
			return;
		}
		try {
			long hwnd = call(invokeP, fnGetActiveWindow);
			if (hwnd == 0) {
				return;
			}
			long himc = call(invokePP, hwnd, fnImmGetContext);
			if (himc == 0) {
				return;   // 이미 떼어 있다
			}
			if (fnImmReleaseContext != 0) {
				Method rel = jniInt();
				if (rel != null) {
					rel.invoke(null, hwnd, himc, fnImmReleaseContext);
				}
			}
			long prev = call(invokePPP, hwnd, 0L, fnImmAssociateContext);
			if (prev != 0 && (savedHimc == 0 || savedHwnd != hwnd)) {
				savedHwnd = hwnd;
				savedHimc = prev;
			}
		} catch (Throwable t) {
			broken = true;
			LunaCompat.warnOnce("winIme:detach", t);
		}
	}

	/** 떼어 낸 입력기를 돌려 붙인다(기능을 끌 때). */
	public static void restore() {
		if (broken || savedHimc == 0 || savedHwnd == 0) {
			return;
		}
		try {
			call(invokePPP, savedHwnd, savedHimc, fnImmAssociateContext);
		} catch (Throwable t) {
			LunaCompat.warnOnce("winIme:restore", t);
		}
		savedHimc = 0;
		savedHwnd = 0;
	}

	private static Method relMethod;
	private static boolean relLooked;

	/** JNI.invokePPI(long, long, long) → int (ImmReleaseContext). 없으면 null(안 불러도 새는 건 핸들 하나). */
	private static Method jniInt() {
		if (!relLooked) {
			relLooked = true;
			try {
				Class<?> jni = Class.forName("org.lwjgl.system.JNI");
				for (String prefix : new String[]{"invoke", "call"}) {
					try {
						relMethod = jni.getMethod(prefix + "PPI", long.class, long.class, long.class);
						break;
					} catch (Throwable ignored) {
					}
				}
			} catch (Throwable ignored) {
			}
		}
		return relMethod;
	}

	// ==================== 49-400차: 한/영(변환 모드) ====================
	// 사용자: "한글 상태에서 한/영 키를 2번 눌러야 영어가 돼". 한국어 입력기는 "열림/닫힘"과 따로 "한글/영문" 변환 모드를 기억한다.
	// 우리가 입력기를 닫아도 변환 모드는 한글로 남아 있어서, 다음 한/영 누름이 한글→영문으로만 바뀌고(열림 알림 없음) 노바가 몰랐다.
	// 이제 변환 모드도 직접 보고(한글이면 한/영 누름) 늘 영문으로 돌려 둔다 - 누를 때마다 같은 쪽(영문→한글)으로만 바뀐다.

	private static boolean convLooked;
	private static boolean convBroken;
	private static long fnGetConv;
	private static long fnSetConv;
	private static long fnGetKeyboardLayout;
	private static Method invokePPPI;
	private static Method invokePIII;
	private static Method invokeIP;
	private static Method win32Window;
	private static Method memGetInt;
	private static long buf;

	private static boolean resolveConv() {
		if (convLooked) {
			return !convBroken;
		}
		convLooked = true;
		if (!resolve()) {
			convBroken = true;
			return false;
		}
		try {
			Class<?> lib = Class.forName("org.lwjgl.system.windows.WindowsLibrary");
			Object user32 = lib.getConstructor(String.class).newInstance("user32");
			Object imm32 = lib.getConstructor(String.class).newInstance("imm32");
			Method addr = lib.getMethod("getFunctionAddress", CharSequence.class);
			fnGetConv = (Long) addr.invoke(imm32, "ImmGetConversionStatus");
			fnSetConv = (Long) addr.invoke(imm32, "ImmSetConversionStatus");
			fnGetKeyboardLayout = (Long) addr.invoke(user32, "GetKeyboardLayout");
			Class<?> jni = Class.forName("org.lwjgl.system.JNI");
			invokePPPI = jni.getMethod("invokePPPI", long.class, long.class, long.class, long.class);
			invokePIII = jni.getMethod("invokePI", long.class, int.class, int.class, long.class);
			invokeIP = jni.getMethod("invokeP", int.class, long.class);
			Class<?> mu = Class.forName("org.lwjgl.system.MemoryUtil");
			memGetInt = mu.getMethod("memGetInt", long.class);
			buf = (Long) mu.getMethod("nmemAlloc", long.class).invoke(null, 8L);
			try {
				win32Window = Class.forName("org.lwjgl.glfw.GLFWNativeWin32").getMethod("glfwGetWin32Window", long.class);
			} catch (Throwable ignored) {
			}
			if (fnGetConv == 0 || fnSetConv == 0 || buf == 0) {
				convBroken = true;
			}
		} catch (Throwable t) {
			convBroken = true;
			LunaCompat.warnOnce("winIme:conv", t);
		}
		return !convBroken;
	}

	/** 게임 창(GLFW 창 → HWND, 안 되면 GetActiveWindow). */
	private static long hwndOf(long glfwWindow) throws Exception {
		if (win32Window != null && glfwWindow != 0L) {
			long h = (Long) win32Window.invoke(null, glfwWindow);
			if (h != 0L) {
				return h;
			}
		}
		return call(invokeP, fnGetActiveWindow);
	}

	/** 지금 자판이 한국어인가(다른 나라 입력기의 "자국어 모드"를 한글로 치지 않게). */
	private static boolean koreanLayout() {
		try {
			if (fnGetKeyboardLayout == 0) {
				return true;
			}
			long hkl = (Long) invokeIP.invoke(null, 0, fnGetKeyboardLayout);
			return (hkl & 0xFFFF) == 0x0412;
		} catch (Throwable t) {
			return true;
		}
	}

	/** 한국어 입력기 변환 모드: 1 한글, 0 영문, -1 모름(윈도우 아님, 입력기 없음 등). */
	public static int hangulMode(long glfwWindow) {
		if (!WINDOWS || !resolveConv()) {
			return -1;
		}
		long hwnd = 0, himc = 0;
		try {
			if (!koreanLayout()) {
				return -1;
			}
			hwnd = hwndOf(glfwWindow);
			himc = hwnd == 0 ? 0 : call(invokePP, hwnd, fnImmGetContext);
			if (himc == 0) {
				return -1;
			}
			int ok = (Integer) invokePPPI.invoke(null, himc, buf, buf + 4, fnGetConv);
			if (ok == 0) {
				return -1;
			}
			return ((Integer) memGetInt.invoke(null, buf) & 1) != 0 ? 1 : 0;
		} catch (Throwable t) {
			convBroken = true;
			LunaCompat.warnOnce("winIme:getConv", t);
			return -1;
		} finally {
			release(hwnd, himc);
		}
	}

	/** 변환 모드를 한글/영문으로(바뀔 때만). */
	public static void setHangulMode(long glfwWindow, boolean hangul) {
		if (!WINDOWS || !resolveConv()) {
			return;
		}
		long hwnd = 0, himc = 0;
		try {
			if (!koreanLayout()) {
				return;
			}
			hwnd = hwndOf(glfwWindow);
			himc = hwnd == 0 ? 0 : call(invokePP, hwnd, fnImmGetContext);
			if (himc == 0) {
				return;
			}
			if ((Integer) invokePPPI.invoke(null, himc, buf, buf + 4, fnGetConv) == 0) {
				return;
			}
			int conv = (Integer) memGetInt.invoke(null, buf);
			int sent = (Integer) memGetInt.invoke(null, buf + 4);
			int want = hangul ? conv | 1 : conv & ~1;
			if (want != conv) {
				invokePIII.invoke(null, himc, want, sent, fnSetConv);
			}
		} catch (Throwable t) {
			convBroken = true;
			LunaCompat.warnOnce("winIme:setConv", t);
		} finally {
			release(hwnd, himc);
		}
	}

	private static void release(long hwnd, long himc) {
		if (himc == 0 || fnImmReleaseContext == 0) {
			return;
		}
		try {
			Method rel = jniInt();
			if (rel != null) {
				rel.invoke(null, hwnd, himc, fnImmReleaseContext);
			}
		} catch (Throwable ignored) {
		}
	}
}
