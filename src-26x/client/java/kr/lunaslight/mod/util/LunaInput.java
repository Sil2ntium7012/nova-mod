package kr.lunaslight.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-215차: 26.3 대응 - <b>GLFW가 SDL로 바뀜</b>(Fabric 26.3 공지, NeoForge 26.3 이전 안내서).
 *
 * <ul>
 *   <li>26.3 게임에는 lwjgl-glfw 자체가 없다(26.3.json 라이브러리 목록 실측). {@code org.lwjgl.glfw.*}를
 *       직접 부르는 코드는 26.3에서 컴파일도 안 되고 실행하면 바로 죽는다 - 26.x 트리는 GLFW/SDL을
 *       전부 이 클래스(리플렉션)로만 부른다.</li>
 *   <li>키 값: 26.2까지는 GLFW 키 코드, 26.3은 SDL 스캔코드. {@link InputConstants}의 KEY_* 상수는
 *       컴파일할 때 jar에 박히므로 <b>버전별 jar가 각자 맞는 값</b>을 갖는다 - 그래서 키 상수는 GLFW_*가
 *       아니라 InputConstants.KEY_*만 쓴다.</li>
 *   <li>마우스 버튼: GLFW 0 왼쪽/1 오른쪽/2 가운데 → SDL 1 왼쪽/2 가운데/3 오른쪽. 우리 코드는 전부
 *       <b>GLFW 번호</b>로 생각한다 - 입력이 들어오는 곳({@link #toGlfwButton})과 상태를 묻는 곳에서만 바꾼다.</li>
 * </ul>
 */
public final class LunaInput {
	private LunaInput() {
	}

	/** 이 jar가 SDL(26.3+)용으로 컴파일됐는지. 상수라서 jar마다 정해진다. */
	public static final boolean SDL = InputConstants.MOUSE_BUTTON_LEFT != 0;

	/** 26.2까지 KEY_LSUPER, 26.3은 KEY_LGUI. */
	public static final int KEY_LSUPER = keyField(-1, "KEY_LSUPER", "KEY_LGUI");
	/** 메뉴(앱) 키 - InputConstants에 없다. GLFW_KEY_MENU / SDL_SCANCODE_APPLICATION. */
	public static final int KEY_MENU = SDL ? 101 : 348;

	private static int keyField(int fallback, String... names) {
		for (String n : names) {
			try {
				return InputConstants.class.getField(n).getInt(null);
			} catch (Throwable ignored) {
			}
		}
		return fallback;
	}

	// ==================== 마우스 버튼 번호 ====================

	/** 게임이 준 버튼 번호 → GLFW 번호(우리 코드 기준). */
	public static int toGlfwButton(int b) {
		if (!SDL) {
			return b;
		}
		return switch (b) {
			case 1 -> 0;
			case 2 -> 2;
			case 3 -> 1;
			default -> b >= 4 ? b - 1 : b;
		};
	}

	/** GLFW 번호 → 이 게임의 버튼 번호. */
	public static int fromGlfwButton(int b) {
		if (!SDL) {
			return b;
		}
		return switch (b) {
			case 0 -> 1;
			case 1 -> 3;
			case 2 -> 2;
			default -> b >= 3 ? b + 1 : b;
		};
	}

	// ==================== 리플렉션 도우미 ====================

	private static final Map<String, Object> CACHE = new HashMap<>();
	private static final Object MISSING = new Object();

	private static Method staticMethod(String cls, String name, Class<?>... params) {
		String key = cls + "#" + name + params.length;
		Object hit = CACHE.get(key);
		if (hit == null) {
			try {
				hit = Class.forName(cls).getMethod(name, params);
			} catch (Throwable t) {
				hit = MISSING;
			}
			CACHE.put(key, hit);
		}
		return hit instanceof Method m ? m : null;
	}

	private static Object call(String cls, String name, Class<?>[] params, Object... args) {
		Method m = staticMethod(cls, name, params);
		if (m == null) {
			return null;
		}
		try {
			return m.invoke(null, args);
		} catch (Throwable t) {
			LunaCompat.warnOnce("input:" + name, t);
			return null;
		}
	}

	private static final String GLFW = "org.lwjgl.glfw.GLFW";
	private static final String SDL_MOUSE = "org.lwjgl.sdl.SDLMouse";
	private static final String SDL_VIDEO = "org.lwjgl.sdl.SDLVideo";
	private static final String SDL_SURFACE = "org.lwjgl.sdl.SDLSurface";

	private static long handle(Minecraft mc) {
		try {
			return mc.getWindow().handle();
		} catch (Throwable t) {
			return 0L;
		}
	}

	// ==================== 마우스 ====================

	/** 지금 그 마우스 버튼(GLFW 번호)이 눌려 있는지. */
	public static boolean mouseDown(Minecraft mc, int glfwButton) {
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		if (SDL) {
			Object r = call(SDL_MOUSE, "SDL_GetMouseState", new Class<?>[]{java.nio.FloatBuffer.class, java.nio.FloatBuffer.class},
					null, null);
			if (r instanceof Integer mask) {
				int b = fromGlfwButton(glfwButton);
				return b >= 1 && b <= 32 && (mask & (1 << (b - 1))) != 0;
			}
		} else {
			Object r = call(GLFW, "glfwGetMouseButton", new Class<?>[]{long.class, int.class}, handle(mc), glfwButton);
			if (r instanceof Integer v) {
				return v == 1;
			}
		}
		// 마지막 수단: 게임이 기억하는 버튼 상태(왼/오/가운데만)
		try {
			return switch (glfwButton) {
				case 0 -> mc.mouseHandler.isLeftPressed();
				case 1 -> mc.mouseHandler.isRightPressed();
				case 2 -> mc.mouseHandler.isMiddlePressed();
				default -> false;
			};
		} catch (Throwable t) {
			return false;
		}
	}

	/** 마우스 자리(창 좌표 - 화면 배율 적용 전). 못 읽으면 null. */
	public static double[] cursorPos(Minecraft mc) {
		if (mc == null || mc.getWindow() == null) {
			return null;
		}
		if (SDL) {
			try {
				// LWJGL은 힙 버퍼를 못 받는다 - 직접 버퍼로
				java.nio.FloatBuffer x = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
				java.nio.FloatBuffer y = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
				Object r = call(SDL_MOUSE, "SDL_GetMouseState", new Class<?>[]{java.nio.FloatBuffer.class, java.nio.FloatBuffer.class}, x, y);
				if (r != null) {
					return new double[]{x.get(0), y.get(0)};
				}
			} catch (Throwable ignored) {
			}
		} else {
			double[] x = new double[1];
			double[] y = new double[1];
			if (staticMethod(GLFW, "glfwGetCursorPos", long.class, double[].class, double[].class) != null) {
				call(GLFW, "glfwGetCursorPos", new Class<?>[]{long.class, double[].class, double[].class}, handle(mc), x, y);
				return new double[]{x[0], y[0]};
			}
		}
		try {
			return new double[]{mc.mouseHandler.xpos(), mc.mouseHandler.ypos()};
		} catch (Throwable t) {
			return null;
		}
	}

	/** 마우스를 창 좌표 (x, y)로 옮긴다. */
	public static boolean setCursorPos(Minecraft mc, double x, double y) {
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		if (SDL) {
			if (staticMethod(SDL_MOUSE, "SDL_WarpMouseInWindow", long.class, float.class, float.class) == null) {
				return false;
			}
			call(SDL_MOUSE, "SDL_WarpMouseInWindow", new Class<?>[]{long.class, float.class, float.class},
					handle(mc), (float) x, (float) y);
			return true;
		}
		if (staticMethod(GLFW, "glfwSetCursorPos", long.class, double.class, double.class) == null) {
			return false;
		}
		call(GLFW, "glfwSetCursorPos", new Class<?>[]{long.class, double.class, double.class}, handle(mc), x, y);
		return true;
	}

	private static boolean cursorHiddenByUs;

	/** 보이는 커서를 숨긴다(마우스가 잡혀 있으면 건드리지 않음). 숨겼으면 true. */
	public static boolean hideCursor(Minecraft mc) {
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		try {
			if (mc.mouseHandler.isMouseGrabbed()) {
				return false;
			}
		} catch (Throwable ignored) {
		}
		if (SDL) {
			Object vis = call(SDL_MOUSE, "SDL_CursorVisible", new Class<?>[0]);
			if (Boolean.TRUE.equals(vis)) {
				call(SDL_MOUSE, "SDL_HideCursor", new Class<?>[0]);
				cursorHiddenByUs = true;
			}
			return cursorHiddenByUs;
		}
		Object mode = call(GLFW, "glfwGetInputMode", new Class<?>[]{long.class, int.class}, handle(mc), 0x00033001);
		if (mode instanceof Integer m && m == 0x00034001) {
			call(GLFW, "glfwSetInputMode", new Class<?>[]{long.class, int.class, int.class}, handle(mc), 0x00033001, 0x00034002);
			cursorHiddenByUs = true;
		}
		return cursorHiddenByUs;
	}

	/** {@link #hideCursor}로 숨긴 커서를 되돌린다. */
	public static void showCursor(Minecraft mc) {
		if (!cursorHiddenByUs || mc == null || mc.getWindow() == null) {
			return;
		}
		cursorHiddenByUs = false;
		if (SDL) {
			// 49-233차(사용자: "마우스 포인터가 모든 화면에서 안 보여", 26.3): SDL은 GLFW와 달리 커서 보이기가 마우스 잡기(상대 모드)와
			// 따로 남는다. 예전엔 게임이 마우스를 잡은 때(화면 닫음)엔 되돌리기를 건너뛰고 표시만 지워서, 다음에 화면을 열어도 커서가
			// 계속 숨은 채였다. 잡혀 있어도 늘 되돌린다(잡힌 동안은 상대 모드가 알아서 숨긴다).
			call(SDL_MOUSE, "SDL_ShowCursor", new Class<?>[0]);
			return;
		}
		Object mode = call(GLFW, "glfwGetInputMode", new Class<?>[]{long.class, int.class}, handle(mc), 0x00033001);
		if (mode instanceof Integer m && m == 0x00034002) {
			call(GLFW, "glfwSetInputMode", new Class<?>[]{long.class, int.class, int.class}, handle(mc), 0x00033001, 0x00034001);
		}
	}

	/** 창 좌표 → 프레임버퍼 픽셀 배율(윈도우 배율 150%면 1.5). */
	public static double framebufferRatio(Minecraft mc) {
		try {
			int sw = mc.getWindow().getScreenWidth();
			return sw > 0 ? mc.getWindow().getWidth() / (double) sw : 1.0;
		} catch (Throwable t) {
			return 1.0;
		}
	}

	// ==================== 클립보드 ====================

	public static String clipboard(Minecraft mc) {
		try {
			String s = mc.keyboardHandler.getClipboard();
			return s == null ? "" : s;
		} catch (Throwable t) {
			return "";
		}
	}

	// ==================== 글자 입력(SDL) ====================

	/**
	 * SDL은 "글자 입력 중"을 직접 켜야 글자가 들어온다(안 켜면 charTyped가 아예 안 옴 - Fabric 26.3 공지).
	 * 바닐라 입력칸은 알아서 켜고, 우리 화면/검색창은 여기서 켠다. 26.2까지는 할 일 없음.
	 */
	public static void textInput(Object owner, boolean on) {
		if (!SDL || owner == null) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			Object tim = mc.getClass().getMethod("textInputManager").invoke(mc);
			if (tim == null) {
				return;
			}
			tim.getClass().getMethod(on ? "startTextInput" : "stopTextInput", Object.class).invoke(tim, owner);
		} catch (Throwable t) {
			LunaCompat.warnOnce("input:textInput", t);
		}
	}

	/**
	 * 49-221차: 26.1~26.2(GLFW) 한글 입력. 바닐라 TextInputManager는 글자 입력 중이 아니면 매 틱 IME를 영문으로
	 * 끈다. 우리 입력칸에 초점이 있는 동안 startTextInput()을 켜 두면 사용자가 쓰던 한/영 상태가 돌아오고 유지된다.
	 * 26.3(SDL)은 textInput()이 화면 단위로 이미 켜 두므로 여기서는 아무 일도 안 한다. TextInputManager가 없는 버전도 무시.
	 */
	public static void imeText(boolean on) {
		if (SDL) {
			return;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			Object tim = mc.getClass().getMethod("textInputManager").invoke(mc);
			if (tim == null) {
				return;
			}
			tim.getClass().getMethod(on ? "startTextInput" : "stopTextInput").invoke(tim);
		} catch (NoSuchMethodException ignored) {
			// 이 버전엔 없음
		} catch (Throwable t) {
			LunaCompat.warnOnce("input:imeText", t);
		}
	}

	/**
	 * 49-228차: 폴더/파일 열기. 26.3에서 Util.getPlatform().openUri가 없어지고 com.mojang.blaze3d.Blaze3D.openPath로
	 * 옮겨져 26.3 빌드가 깨졌다. 26.3 쪽을 먼저, 없으면 예전 OS.openPath/openUri, 마지막으로 자바 Desktop.
	 */
	public static void openPath(java.nio.file.Path p) {
		try {
			Class.forName("com.mojang.blaze3d.Blaze3D").getMethod("openPath", java.nio.file.Path.class).invoke(null, p);
			return;
		} catch (Throwable ignored) {
		}
		try {
			Object os = Class.forName("net.minecraft.util.Util").getMethod("getPlatform").invoke(null);
			try {
				os.getClass().getMethod("openPath", java.nio.file.Path.class).invoke(os, p);
			} catch (NoSuchMethodException e) {
				os.getClass().getMethod("openUri", java.net.URI.class).invoke(os, p.toUri());
			}
			return;
		} catch (Throwable ignored) {
		}
		try {
			java.awt.Desktop.getDesktop().open(p.toFile());
		} catch (Throwable t) {
			LunaCompat.warnOnce("input:openPath", t);
		}
	}

	// ==================== 키 ====================

	/** KeyEvent의 두 번째 값(26.2까지 scancode, 26.3 keycode). 레코드 순서로 읽는다. */
	public static int secondCode(Object keyEvent) {
		try {
			java.lang.reflect.RecordComponent[] rc = keyEvent.getClass().getRecordComponents();
			if (rc != null && rc.length >= 2) {
				return (Integer) rc[1].getAccessor().invoke(keyEvent);
			}
		} catch (Throwable ignored) {
		}
		return 0;
	}

	/** 키보드 키 코드 → InputConstants.Key (26.2까지 Type.KEYSYM, 26.3 Type.KEYBOARD). */
	public static InputConstants.Key keyboardKey(int code) {
		InputConstants.Type t = null;
		for (String n : new String[]{"KEYSYM", "KEYBOARD"}) {
			try {
				t = InputConstants.Type.valueOf(n);
				break;
			} catch (Throwable ignored) {
			}
		}
		if (t == null) {
			t = InputConstants.Type.values()[0];
		}
		return t.getOrCreate(code);
	}

	// ==================== 저장된 키 옮기기(GLFW ↔ SDL) ====================

	/** 26.2 InputConstants 실측값(이름 → GLFW 코드). SDL 쪽은 이 jar의 InputConstants에서 이름으로 읽는다. */
	private static final String GLFW_TABLE = "KEY_0=48,KEY_1=49,KEY_2=50,KEY_3=51,KEY_4=52,KEY_5=53,KEY_6=54,KEY_7=55,KEY_8=56,"
			+ "KEY_9=57,KEY_A=65,KEY_B=66,KEY_C=67,KEY_D=68,KEY_E=69,KEY_F=70,KEY_G=71,KEY_H=72,KEY_I=73,KEY_J=74,KEY_K=75,"
			+ "KEY_L=76,KEY_M=77,KEY_N=78,KEY_O=79,KEY_P=80,KEY_Q=81,KEY_R=82,KEY_S=83,KEY_T=84,KEY_U=85,KEY_V=86,KEY_W=87,"
			+ "KEY_X=88,KEY_Y=89,KEY_Z=90,KEY_F1=290,KEY_F2=291,KEY_F3=292,KEY_F4=293,KEY_F5=294,KEY_F6=295,KEY_F7=296,"
			+ "KEY_F8=297,KEY_F9=298,KEY_F10=299,KEY_F11=300,KEY_F12=301,KEY_F13=302,KEY_F14=303,KEY_F15=304,KEY_F16=305,"
			+ "KEY_F17=306,KEY_F18=307,KEY_F19=308,KEY_F20=309,KEY_F21=310,KEY_F22=311,KEY_F23=312,KEY_F24=313,"
			+ "KEY_NUMLOCK=282,KEY_NUMPAD0=320,KEY_NUMPAD1=321,KEY_NUMPAD2=322,KEY_NUMPAD3=323,KEY_NUMPAD4=324,"
			+ "KEY_NUMPAD5=325,KEY_NUMPAD6=326,KEY_NUMPAD7=327,KEY_NUMPAD8=328,KEY_NUMPAD9=329,KEY_NUMPADCOMMA=330,"
			+ "KEY_NUMPADENTER=335,KEY_NUMPADEQUALS=336,KEY_DOWN=264,KEY_LEFT=263,KEY_RIGHT=262,KEY_UP=265,KEY_ADD=334,"
			+ "KEY_APOSTROPHE=39,KEY_BACKSLASH=92,KEY_COMMA=44,KEY_EQUALS=61,KEY_GRAVE=96,KEY_LBRACKET=91,KEY_MINUS=45,"
			+ "KEY_MULTIPLY=332,KEY_PERIOD=46,KEY_RBRACKET=93,KEY_SEMICOLON=59,KEY_SLASH=47,KEY_SPACE=32,KEY_TAB=258,"
			+ "KEY_LALT=342,KEY_LCONTROL=341,KEY_LSHIFT=340,KEY_LSUPER=343,KEY_RALT=346,KEY_RCONTROL=345,KEY_RSHIFT=344,"
			+ "KEY_RSUPER=347,KEY_RETURN=257,KEY_ESCAPE=256,KEY_BACKSPACE=259,KEY_DELETE=261,KEY_END=269,KEY_HOME=268,"
			+ "KEY_INSERT=260,KEY_PAGEDOWN=267,KEY_PAGEUP=266,KEY_CAPSLOCK=280,KEY_PAUSE=284,KEY_SCROLLLOCK=281,"
			+ "KEY_PRINTSCREEN=283,KEY_MENU=348";

	private static Map<Integer, Integer> glfwToSdl;
	private static Map<Integer, Integer> sdlToGlfw;

	private static synchronized void buildTables() {
		if (glfwToSdl != null) {
			return;
		}
		glfwToSdl = new HashMap<>();
		sdlToGlfw = new HashMap<>();
		for (String pair : GLFW_TABLE.split(",")) {
			String[] kv = pair.split("=");
			int glfw = Integer.parseInt(kv[1]);
			String name = kv[0];
			int sdl;
			if (name.equals("KEY_MENU")) {
				sdl = 101;
			} else {
				String sdlName = switch (name) {
					case "KEY_LSUPER" -> "KEY_LGUI";
					case "KEY_RSUPER" -> "KEY_RGUI";
					default -> name;
				};
				if (!SDL) {
					continue;   // 26.2까지는 SDL 값을 읽을 데가 없다 - 거꾸로 옮길 일도 없음
				}
				try {
					sdl = InputConstants.class.getField(sdlName).getInt(null);
				} catch (Throwable t) {
					continue;
				}
			}
			glfwToSdl.put(glfw, sdl);
			sdlToGlfw.put(sdl, glfw);
		}
	}

	/**
	 * 저장된 키 값을 이 게임의 값으로. {@code fromSdl}은 저장할 때의 방식. 조합키 비트와 마우스 인코딩은 유지
	 * (마우스는 우리 코드가 늘 GLFW 번호라 옮길 필요 없음).
	 */
	public static int migrateKey(int stored, boolean fromSdl) {
		if (stored < 0 || fromSdl == SDL || !SDL) {
			return stored;
		}
		int mods = stored & LunaCompat.MOD_MASK;
		int base = stored & LunaCompat.KEY_MASK;
		if (LunaCompat.isMouseKeyCode(base)) {
			return stored;
		}
		buildTables();
		Integer to = glfwToSdl.get(base);
		return to == null ? -1 : (to | mods);
	}

	// ==================== 창(전체 화면) ====================

	private static Method windowMethod(Object window, String name, Class<?>... params) {
		try {
			return window.getClass().getMethod(name, params);
		} catch (Throwable t) {
			return null;
		}
	}

	/** 지금 전체 화면인지(테두리 없는 전체 화면 포함). */
	public static boolean isFullscreen(Object window) {
		if (window == null) {
			return false;
		}
		try {
			Method m = windowMethod(window, "isFullscreen");
			if (m != null) {
				return (Boolean) m.invoke(window);
			}
			Field f = window.getClass().getDeclaredField("fullscreen");
			f.setAccessible(true);
			return f.getBoolean(window);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 26.3+: 게임 자체에 "독점 전체 화면" 옵션이 있다(끄면 테두리 없는 전체 화면). */
	public static boolean hasExclusiveFullscreenOption(Minecraft mc) {
		return SDL && optionInstance(mc, "exclusiveFullscreen") != null;
	}

	private static Object optionInstance(Minecraft mc, String name) {
		try {
			return mc.options.getClass().getMethod(name).invoke(mc.options);
		} catch (Throwable t) {
			return null;
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setBoolOption(Minecraft mc, String name, boolean value) {
		Object oi = optionInstance(mc, name);
		if (oi instanceof net.minecraft.client.OptionInstance inst) {
			inst.set(Boolean.valueOf(value));
		}
	}

	/**
	 * 전체 화면 켜기/끄기. {@code exclusive}는 26.3+에서만 의미가 있다(false = 테두리 없는 전체 화면).
	 * 26.2까지는 옵션 값과 실제 창(toggleFullScreen)을 맞춘다.
	 */
	public static void setFullscreen(Minecraft mc, boolean on, boolean exclusive) {
		if (mc == null || mc.getWindow() == null) {
			return;
		}
		Object w = mc.getWindow();
		try {
			if (hasExclusiveFullscreenOption(mc)) {
				setBoolOption(mc, "exclusiveFullscreen", exclusive);
				setBoolOption(mc, "fullscreen", on);
				Method ex = windowMethod(w, "setExclusiveFullscreen", boolean.class);
				if (ex != null) {
					ex.invoke(w, exclusive);
				}
				Method fs = windowMethod(w, "setFullscreen", boolean.class);
				if (fs != null) {
					fs.invoke(w, on);
				}
				return;
			}
			setBoolOption(mc, "fullscreen", on);
			if (isFullscreen(w) != on) {
				Method t = windowMethod(w, "toggleFullScreen");
				if (t != null) {
					t.invoke(w);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("input:fullscreen", t);
		}
	}

	/** 26.3+: 지금 독점 전체 화면인지(26.2까지는 전체 화면이면 늘 독점). */
	public static boolean isExclusiveFullscreen(Minecraft mc) {
		if (mc == null || mc.getWindow() == null) {
			return false;
		}
		Object w = mc.getWindow();
		Method m = windowMethod(w, "isExclusiveFullscreen");
		try {
			return m != null ? (Boolean) m.invoke(w) : isFullscreen(w);
		} catch (Throwable t) {
			return false;
		}
	}

	// ==================== 창 아이콘 ====================

	/** 크기별 RGBA 픽셀(직접 버퍼)들로 창 아이콘을 바꾼다. */
	public static boolean setWindowIcon(Minecraft mc, List<int[]> sizes, List<ByteBuffer> pixels) {
		if (mc == null || mc.getWindow() == null || sizes.isEmpty()) {
			return false;
		}
		long h = handle(mc);
		try {
			if (SDL) {
				// 가장 큰 것 하나(SDL이 알아서 줄인다). RGBA 바이트 순서 = ABGR8888(리틀 엔디언)
				int best = 0;
				for (int i = 1; i < sizes.size(); i++) {
					if (sizes.get(i)[0] > sizes.get(best)[0]) {
						best = i;
					}
				}
				int s = sizes.get(best)[0];
				Class<?> surfCls = Class.forName("org.lwjgl.sdl.SDL_Surface");
				Method create = Class.forName(SDL_SURFACE).getMethod("SDL_CreateSurfaceFrom", int.class, int.class, int.class, ByteBuffer.class, int.class);
				Object surface = create.invoke(null, s, s, 376840196, pixels.get(best), s * 4);
				if (surface == null) {
					return false;
				}
				try {
					Class.forName(SDL_VIDEO).getMethod("SDL_SetWindowIcon", long.class, surfCls).invoke(null, h, surface);
				} finally {
					Class.forName(SDL_SURFACE).getMethod("SDL_DestroySurface", surfCls).invoke(null, surface);
				}
				return true;
			}
			Class<?> imgCls = Class.forName("org.lwjgl.glfw.GLFWImage");
			Object images = imgCls.getMethod("malloc", int.class).invoke(null, sizes.size());
			try {
				Class<?> bufCls = images.getClass();
				Method position = bufCls.getMethod("position", int.class);
				Method width = bufCls.getMethod("width", int.class);
				Method height = bufCls.getMethod("height", int.class);
				Method pix = bufCls.getMethod("pixels", ByteBuffer.class);
				for (int i = 0; i < sizes.size(); i++) {
					position.invoke(images, i);
					width.invoke(images, sizes.get(i)[0]);
					height.invoke(images, sizes.get(i)[0]);
					pix.invoke(images, pixels.get(i));
				}
				position.invoke(images, 0);
				Method set = null;
				for (Method m : Class.forName(GLFW).getMethods()) {
					if (m.getName().equals("glfwSetWindowIcon") && m.getParameterCount() == 2
							&& m.getParameterTypes()[1].isAssignableFrom(bufCls)) {
						set = m;
						break;
					}
				}
				if (set == null) {
					return false;
				}
				set.invoke(null, h, images);
				return true;
			} finally {
				try {
					images.getClass().getMethod("free").invoke(images);
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("input:windowIcon", t);
			return false;
		}
	}
}
