package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.SettingsPage;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;

/**
 * 49-245차: <b>창 모드</b>(예전 49-63차 "테두리 없는 창"을 다시 짰다).
 *
 * <p>사용자: "테두리 없는 전체화면이어야 하는데 이상하고, F11 누르면 테두리 없는 전체화면이 막 꼬여. 그냥 설정에 전체화면,
 * 테두리 없는 전체화면, 창화면 이렇게 3개 둬. F11 누르면 전체화면이든 테두리 없는 전체화면이든 창화면이 됐다가, 다시 누르면
 * 원래 창으로(테두리 없음이나 전체를 저장해 놔서)".
 *
 * <p>예전엔 켜고 끄는 기능이라 바닐라 전체화면(F11, 비디오 설정)과 상태가 따로 놀아 꼬였다. 이제 늘 켜져 있고 [창 모드] 하나가
 * 창 상태의 주인이다.
 * <ul>
 *   <li><b>전체화면</b> = 마인크래프트 독점 전체화면.</li>
 *   <li><b>테두리 없는 전체화면</b> = 창 테두리를 끄고 주 모니터를 꽉 채운 창(GLFW). 26.3+(SDL)는 게임의 "독점 전체 화면"을 끈 전체 화면.</li>
 *   <li><b>창화면</b> = 보통 창. 테두리 없는 창을 켜기 전의 자리와 크기로 돌아간다.</li>
 * </ul>
 * F11(바닐라 전체화면 키)은 바닐라 대신 여기서 받는다: 전체화면류 → 창화면, 창화면 → 마지막으로 쓴 전체화면류(숨은 설정에 저장).
 * 비디오 설정의 전체화면 버튼처럼 밖에서 창을 바꾸면 그 상태를 따라간다.
 */
public class BorderlessWindowModule extends Module {

	public enum Mode {
		FULLSCREEN("전체화면"),
		BORDERLESS("테두리 없는 전체화면"),
		WINDOWED("창화면");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static final String GLFW = "org.lwjgl.glfw.GLFW";
	private static final int GLFW_DECORATED = 0x20005;

	public static BorderlessWindowModule instance;

	private final EnumSetting<Mode> mode = register(new EnumSetting<>("window_mode", "창 모드",
			"전체화면, 테두리 없는 전체화면, 창화면 중에서 고릅니다. F11은 창화면과 고른 전체화면을 오갑니다.", Mode.WINDOWED, Mode.class));
	/** F11로 창화면이 되기 전의 전체화면 종류(다시 F11을 누르면 이걸로 돌아간다). */
	private final EnumSetting<Mode> lastFull = register(new EnumSetting<>("last_full", "마지막 전체화면", "",
			Mode.BORDERLESS, Mode.class));
	/** 처음 한 번 지금 창 상태(또는 예전 "테두리 없는 창" 켜짐)를 창 모드로 옮겼는지. */
	private final BooleanSetting inited = register(new BooleanSetting("mode_init", "", "", false));

	{
		lastFull.setHidden(true);
		inited.setHidden(true);
	}

	/** 테두리 없는 창을 켜기 전 창(되돌릴 때). */
	private boolean saved;
	private int savedX, savedY, savedW, savedH, savedDecorated = 1;
	/** 49-299차: 독점 전체화면이 실제로 풀리길 기다리는 남은 틱(0 = 안 기다림). */
	private int pendingBorderless;

	/** 49-299차: 창이 지금 실제로 모니터에 걸린(독점 전체화면) 상태인지 - GLFW에 직접 묻는다(마크의 전체화면 값은 한 프레임 먼저 바뀐다). */
	private boolean onMonitor() {
		try {
			long handle = handle();
			if (handle == 0L) {
				return false;
			}
			Object m = call("glfwGetWindowMonitor", new Class<?>[]{long.class}, handle);
			return m instanceof Long l && l != 0L;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 테두리 없는 창이 지금 창에 걸려 있는지. */
	private boolean borderlessOn;
	/** 마지막으로 창에 적용한 모드(null = 아직). */
	private Mode applied;
	/** 적용 직후 몇 틱은 바깥 변경 감지를 쉰다(창 전환이 한 틱 늦게 보이는 버전 대비). */
	private int settle;
	/** 설정 파일에서 읽은 예전 켜짐 값(예전 "테두리 없는 창"을 켜 뒀으면 테두리 없는 전체화면으로 옮긴다). */
	private boolean legacyEnabled;

	public BorderlessWindowModule() {
		super("borderless_window", "창 모드", ModuleCategory.VIEW,
				"전체화면, 테두리 없는 전체화면, 창화면");
		settingsPage(SettingsPage.GRAPHICS);
		alwaysOn();
		instance = this;
		kr.lunaslight.mod.util.KeyHook.register(this::onKey);
	}

	@Override
	public void setEnabledSilently(boolean value) {
		legacyEnabled = value;
		super.setEnabledSilently(value);
	}

	// ==================== 매 틱: 설정 ↔ 창 맞추기 ====================

	@Override
	public void onTick() {
		if (client == null || handle() == 0L) {
			return;
		}
		if (!inited.get()) {
			inited.setValue(true);
			Mode start;
			if (legacyEnabled && borderlessSupported() || sdlBorderlessOn()) {
				start = Mode.BORDERLESS;
			} else {
				start = exclusiveOn() ? Mode.FULLSCREEN : Mode.WINDOWED;
			}
			mode.setValue(start);
			if (start != Mode.WINDOWED) {
				lastFull.setValue(start);
			}
			saveConfig();
		}
		if (applied != null && settle > 0) {
			settle--;
		} else if (applied != null) {
			followOutside();
		}
		if (pendingBorderless > 0) {
			pendingBorderless--;
			if (mode.get() != Mode.BORDERLESS || applied != Mode.BORDERLESS) {
				pendingBorderless = 0;
			} else if (!onMonitor() || pendingBorderless == 0) {
				pendingBorderless = 0;
				doBorderless();
				settle = 10;
			}
		}
		Mode want = mode.get();
		if (want == Mode.BORDERLESS && !borderlessSupported()) {
			want = Mode.FULLSCREEN;
		}
		if (want != applied) {
			apply(want);
		}
	}

	/** 비디오 설정의 전체화면 버튼 등 바깥에서 창을 바꿨으면 그 상태를 창 모드로 받아들인다. */
	private void followOutside() {
		boolean ex = exclusiveOn();
		Mode now;
		if (ex) {
			now = Mode.FULLSCREEN;
		} else if (sdlMode()) {
			now = sdlBorderlessOn() ? Mode.BORDERLESS : Mode.WINDOWED;
		} else {
			now = applied == Mode.BORDERLESS ? Mode.BORDERLESS : Mode.WINDOWED;
		}
		if (now == applied || (applied == Mode.BORDERLESS && now == Mode.FULLSCREEN && !borderlessSupported())) {
			return;
		}
		if (now == Mode.FULLSCREEN && borderlessOn) {
			undoBorderless();
		}
		applied = now;
		mode.setValue(now);
		if (now != Mode.WINDOWED) {
			lastFull.setValue(now);
		}
		saveConfig();
	}

	private void apply(Mode m) {
		try {
			switch (m) {
				case FULLSCREEN -> {
					if (borderlessOn) {
						undoBorderless();
					}
					setExclusive(true);
				}
				case BORDERLESS -> {
					if (sdlMode()) {
						sdlSet(true, false);
					} else {
						setExclusive(false);
						// 49-299차(사용자: "테두리 없는 화면 ↔ 전체화면 바꿀 때 가끔 작은 테두리 없는 마크 화면이 돼"): 독점 전체화면 끄기는
						// 마크가 다음 프레임에 창을 "창 모드 크기"로 되돌리며 처리한다. 예전엔 같은 틱에 테두리 없는 창을 만들어 놔서, 한 프레임 뒤
						// 마크가 그 창을 예전 작은 크기로 줄였다(테두리는 꺼진 채). 창이 실제로 모니터에서 빠진 뒤에 테두리 없는 창을 만든다.
						if (onMonitor()) {
							pendingBorderless = 40;
						} else {
							doBorderless();
						}
					}
				}
				default -> {
					setExclusive(false);
					if (borderlessOn) {
						undoBorderless();
					}
				}
			}
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("windowMode", t);
		}
		applied = m;
		settle = 10;
	}

	// ==================== F11 ====================

	/** 전체화면 키: 전체화면류 → 창화면, 창화면 → 마지막 전체화면류. 바닐라 전환은 막는다. */
	private boolean onKey(int key, int scancode, int action, int modifiers) {
		if (action != 1 || client == null || handle() == 0L || key != fullscreenKeyCode()) {
			return false;
		}
		Mode cur = mode.get();
		if (cur == Mode.WINDOWED) {
			Mode back = lastFull.get() == Mode.WINDOWED ? Mode.FULLSCREEN : lastFull.get();
			mode.setValue(back);
		} else {
			lastFull.setValue(cur);
			mode.setValue(Mode.WINDOWED);
		}
		saveConfig();
		return true;
	}

	/** 처음 설정 화면(FirstSetup)용: 0 창화면, 1 전체화면, 2 테두리 없는 전체화면. */
	public static int windowModeInt() {
		BorderlessWindowModule b = instance;
		if (b == null) {
			return 0;
		}
		return switch (b.mode.get()) {
			case FULLSCREEN -> 1;
			case BORDERLESS -> 2;
			default -> 0;
		};
	}

	public static void setWindowModeInt(int v) {
		BorderlessWindowModule b = instance;
		if (b == null) {
			return;
		}
		Mode m = v == 1 ? Mode.FULLSCREEN : v == 2 ? Mode.BORDERLESS : Mode.WINDOWED;
		b.mode.setValue(m);
		if (m != Mode.WINDOWED) {
			b.lastFull.setValue(m);
		}
		b.inited.setValue(true);
		b.onTick();
	}

	private static void saveConfig() {
		try {
			kr.lunaslight.mod.config.LunaClientConfig.save();
		} catch (Throwable ignored) {
		}
	}

	private int fullscreenKeyCode() {
		try {
			Object opts = client.options;
			java.lang.reflect.Field f = kr.lunaslight.mod.util.LunaCompat.findField(opts.getClass(), "fullscreenKey");
			if (f == null) {
				f = kr.lunaslight.mod.util.LunaCompat.findField(opts.getClass(), "keyFullscreen");
			}
			int c = f == null ? -1 : kr.lunaslight.mod.util.LunaCompat.boundKeyCode(f.get(opts));
			if (c >= 0) {
				return c;
			}
		} catch (Throwable ignored) {
		}
		return 300;   // GLFW_KEY_F11
	}

	// ==================== 창 다루기 ====================

	public boolean borderlessSupported() {
		return setAttrib() != null || sdlMode();
	}

	/** GLFW 버전은 독점/테두리 없음을 옵션으로 가르지 않는다(26.3+ SDL 전용). */
	private boolean sdlMode() {
		return false;
	}

	private boolean sdlBorderlessOn() {
		return false;
	}

	private void sdlSet(boolean on, boolean exclusive) {
	}

	/** 지금 마인크래프트 독점 전체화면인지. */
	private boolean exclusiveOn() {
		try {
			return WindowAccess.of(client).isFullscreen();
		} catch (Throwable t) {
			return false;
		}
	}

	/** 독점 전체화면 켜기/끄기 + 옵션 파일 값도 같이(다음에 켤 때 그대로). */
	private void setExclusive(boolean on) {
		setFullscreenOption(on);
		if (exclusiveOn() != on) {
			WindowAccess.of(client).toggleFullscreen();
		}
	}

	/** 1.19+는 SimpleOption, 그 전은 boolean 필드. */
	private void setFullscreenOption(boolean value) {
		try {
			Object opts = client.options;
			Object so = kr.lunaslight.mod.util.LunaCompat.callNoArg(opts, "getFullscreen");
			if (so != null) {
				kr.lunaslight.mod.util.LunaCompat.call1(so, "setValue", Boolean.valueOf(value));
				return;
			}
			java.lang.reflect.Field f = kr.lunaslight.mod.util.LunaCompat.findField(opts.getClass(), "fullscreen");
			if (f != null && f.getType() == boolean.class) {
				f.setAccessible(true);
				f.setBoolean(opts, value);
			}
		} catch (Throwable ignored) {
		}
	}

	private int winX() {
		return WindowAccess.of(client).getX();
	}

	private int winY() {
		return WindowAccess.of(client).getY();
	}

	private int winW() {
		return WindowAccess.of(client).getWidth();
	}

	private int winH() {
		return WindowAccess.of(client).getHeight();
	}

	/** 테두리 끄고 주 모니터를 꽉 채운다(GLFW). 켜기 전 창 자리와 크기를 적어 둔다. */
	private void doBorderless() {
		long handle = handle();
		if (handle == 0L || setAttrib() == null) {
			return;
		}
		if (!saved) {
			savedX = winX();
			savedY = winY();
			savedW = winW();
			savedH = winH();
			savedDecorated = 1;   // 되돌릴 땐 늘 테두리 있는 창(예전 판이 테두리를 끈 채 남겼어도)
			saved = true;
		}
		int[] mx = new int[1];
		int[] my = new int[1];
		Object monitor = call("glfwGetPrimaryMonitor", new Class<?>[]{});
		if (!(monitor instanceof Long mon) || mon == 0L) {
			return;
		}
		call("glfwGetMonitorPos", new Class<?>[]{long.class, int[].class, int[].class}, mon, mx, my);
		Object vid = call("glfwGetVideoMode", new Class<?>[]{long.class}, mon);
		int w = intOf(invoke0(vid, "width"));
		int h = intOf(invoke0(vid, "height"));
		if (w <= 0 || h <= 0) {
			return;
		}
		call("glfwSetWindowAttrib", new Class<?>[]{long.class, int.class, int.class}, handle, GLFW_DECORATED, 0);
		call("glfwSetWindowPos", new Class<?>[]{long.class, int.class, int.class}, handle, mx[0], my[0]);
		// 49-298차(사용자 사진: "전체화면 하면 마우스 커서가 이렇게 안 보여" - 커서가 보라색 사선으로 깨짐): 모니터와 크기가 꼭 같은 창은
		// 윈도우/그래픽 드라이버가 독점 전체화면처럼 다뤄(화면 바로 넘기기) 사용자 지정 마우스 포인터가 깨져 보였다. 아래로 1픽셀 더 크게
		// 만들어 그 취급을 피한다(넘친 1줄은 화면 밖이라 안 보인다).
		call("glfwSetWindowSize", new Class<?>[]{long.class, int.class, int.class}, handle, w, h + 1);
		borderlessOn = true;
	}

	/** 테두리 없는 창을 풀고 켜기 전 창으로. 독점 전체화면 중이면 테두리만 되돌리고 "창 모드 크기" 기억만 고친다. */
	private void undoBorderless() {
		long handle = handle();
		borderlessOn = false;
		if (handle == 0L || !saved || setAttrib() == null) {
			saved = false;
			return;
		}
		call("glfwSetWindowAttrib", new Class<?>[]{long.class, int.class, int.class}, handle, GLFW_DECORATED, savedDecorated);
		if (!exclusiveOn()) {
			call("glfwSetWindowSize", new Class<?>[]{long.class, int.class, int.class}, handle, Math.max(1, savedW), Math.max(1, savedH));
			call("glfwSetWindowPos", new Class<?>[]{long.class, int.class, int.class}, handle, savedX, savedY);
		}
		rememberWindowed(savedX, savedY, savedW, savedH);
		saved = false;
	}

	/** 마인크래프트 Window의 "창 모드 자리/크기" 기억을 고친다(yarn windowedX.. / mojang windowedX..). 못 찾으면 그냥 둔다. */
	private void rememberWindowed(int x, int y, int w, int h) {
		try {
			Object win = kr.lunaslight.mod.util.LunaCompat.callNoArg(client, "getWindow");
			if (win == null) {
				return;
			}
			String[] names = {"windowedX", "windowedY", "windowedWidth", "windowedHeight"};
			int[] vals = {x, y, w, h};
			for (int i = 0; i < names.length; i++) {
				java.lang.reflect.Field f = kr.lunaslight.mod.util.LunaCompat.findField(win.getClass(), names[i]);
				if (f != null && f.getType() == int.class) {
					f.setAccessible(true);
					f.setInt(win, vals[i]);
				}
			}
		} catch (Throwable ignored) {
			// 기억을 못 고쳐도 창 자체는 되돌아갔다
		}
	}

	// ==================== 잔손 ====================

	private long handle() {
		return client == null || WindowAccess.of(client) == null ? 0L : WindowAccess.of(client).getHandle();
	}

	private static int intOf(Object v) {
		return v instanceof Integer i ? i : 0;
	}

	/**
	 * 인자 없는 메서드 하나 부르기(GLFWVidMode의 width()/height()용). LunaCompat의 리플렉션을 안 쓰는
	 * 이유: 그쪽은 이름을 <b>마인크래프트 매핑</b>으로 번역해 보는데, 여기 대상은 LWJGL 클래스라
	 * 번역 대상이 아니다(26.x 포팅 번역기도 같은 이유로 이 이름을 건드리면 안 된다).
	 */
	private static Object invoke0(Object target, String method) {
		try {
			return target == null ? null : target.getClass().getMethod(method).invoke(target);
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * 이 LWJGL에 장식 끄기 함수가 있는지(GLFW 3.3+). 없으면 null - 그 버전에서는 카드가 잠긴다.
	 * 한 번 찾아 두고 계속 쓴다(창을 만지는 건 켜고 끌 때뿐이라 이걸로 충분하다).
	 */
	private static java.lang.reflect.Method setAttribCache;
	private static boolean setAttribResolved;

	private static java.lang.reflect.Method setAttrib() {
		if (!setAttribResolved) {
			setAttribResolved = true;
			setAttribCache = (java.lang.reflect.Method) rawMethod("glfwSetWindowAttrib",
					new Class<?>[]{long.class, int.class, int.class});
		}
		return setAttribCache;
	}

	/**
	 * GLFW의 정적 메서드 하나 찾기. <b>LunaCompat의 리플렉션을 안 쓴다</b> - 그쪽은 이름을 마인크래프트
	 * 매핑으로 번역해 보는데 LWJGL은 번역 대상이 아니다(엉뚱한 이름으로 찾다 놓칠 이유를 안 만든다).
	 */
	private static Object rawMethod(String method, Class<?>[] params) {
		try {
			return Class.forName(GLFW).getMethod(method, params);
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static Object call(String method, Class<?>[] params, Object... args) {
		try {
			java.lang.reflect.Method m = (java.lang.reflect.Method) rawMethod(method, params);
			return m == null ? null : m.invoke(null, args);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
