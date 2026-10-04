package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.util.LunaInput;
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
						doBorderless();
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
			int c = kr.lunaslight.mod.util.LunaCompat.boundKeyCode(client.options.keyFullscreen);
			if (c >= 0) {
				return c;
			}
		} catch (Throwable ignored) {
		}
		return com.mojang.blaze3d.platform.InputConstants.KEY_F11;
	}

	// ==================== 창 다루기 ====================

	public boolean borderlessSupported() {
		return setAttrib() != null || sdlMode();
	}

	/**
	 * 49-215차: 26.3+는 GLFW가 없고(SDL), 대신 게임 자체에 "독점 전체 화면" 옵션이 있다 - 그걸 끈 전체 화면이 곧 테두리 없는 전체화면.
	 * 그래서 26.3에서는 창을 직접 만지지 않고 그 두 옵션을 맞춘다.
	 */
	private boolean sdlMode() {
		return client != null && LunaInput.hasExclusiveFullscreenOption(client);
	}

	/** 26.3+: 지금 테두리 없는 전체화면(전체 화면 + 독점 끔)인지. */
	private boolean sdlBorderlessOn() {
		return sdlMode() && LunaInput.isFullscreen(client.getWindow()) && !LunaInput.isExclusiveFullscreen(client);
	}

	private void sdlSet(boolean on, boolean exclusive) {
		LunaInput.setFullscreen(client, on, exclusive);
	}

	/** 지금 독점 전체화면인지(26.2까지는 전체 화면이면 늘 독점). */
	private boolean exclusiveOn() {
		try {
			if (!LunaInput.isFullscreen(client.getWindow())) {
				return false;
			}
			return !sdlMode() || LunaInput.isExclusiveFullscreen(client);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 독점 전체화면 켜기/끄기(옵션 값도 같이). 끌 때 26.3+의 테두리 없는 전체화면도 같이 끝난다. */
	private void setExclusive(boolean on) {
		if (on) {
			if (!exclusiveOn()) {
				sdlSet(true, true);
			}
		} else if (LunaInput.isFullscreen(client.getWindow())) {
			sdlSet(false, true);
		}
	}

	private int winX() {
		return client.getWindow().getX();
	}

	private int winY() {
		return client.getWindow().getY();
	}

	private int winW() {
		return client.getWindow().getScreenWidth();
	}

	private int winH() {
		return client.getWindow().getScreenHeight();
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
		call("glfwSetWindowSize", new Class<?>[]{long.class, int.class, int.class}, handle, w, h);
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
		return client == null || client.getWindow() == null ? 0L : client.getWindow().handle();
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
