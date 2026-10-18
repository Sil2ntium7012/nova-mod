package kr.lunaslight.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.impl.chat.HangulInputModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 49-313차(사용자: "한글챗 내장 만들어봐야 할 거 같아" → 모든 입력칸, 한/영 키, 윈도우 입력기는 모드가 알아서 끔): 모드 내장 한글 입력.
 *
 * <p>흐름: 키보드 훅(KeyboardKeyMixin, KeyboardCharMixin)이 여기로 먼저 보낸다.
 * <ul>
 *   <li><b>한/영 키</b>(진짜 한/영 키, 또는 [한글 입력] 전환 키 - 기본 오른쪽 Alt)로 한글/영문을 바꾼다.</li>
 *   <li>한글 상태에서 <b>입력칸에 초점이 있을 때만</b> 영문 글자를 자모로 바꿔 조합한다({@link HangulComposer}). 조합 중인 글자는
 *       입력칸에 진짜 글자로 넣고, 다음 자모가 오면 그 한 글자를 지우고(백스페이스) 새 글자를 넣는다 - 게임의 키보드 처리로 다시
 *       보내므로 채팅, 표지판, 책, 모루, 검색창, 우리 화면 어디든 같은 길로 들어간다.</li>
 *   <li>키 설정(이동, 인벤토리 등)은 키 이벤트로 가고 우리는 글자 이벤트만 바꾸므로 한글 상태에서도 그대로 먹는다.</li>
 *   <li>조합은 글자가 아닌 키(스페이스, 엔터, 방향키, 숫자 등), 마우스 클릭, 화면이나 초점이 바뀌면 끝난다.</li>
 * </ul>
 * 윈도우 입력기는 매 틱 게임 창에서 떼어 낸다({@link WinIme}) - 윈도우가 한글 상태여도 키가 영문으로 온다.
 */
public final class HangulInput {
	private HangulInput() {
	}

	/** 우리가 다시 보내는 키/글자(훅이 이것까지 다시 조합하지 않게). */
	public static boolean synthetic;

	private static boolean korean;
	/** 49-386차: 윈도우 입력기가 붙어 있고 한글 상태(자모가 입력기에서 옴). 표시용. */
	private static boolean osKorean;
	/** 49-315차: 마지막으로 한/영을 바꾼 때(표시 애니메이션). */
	private static long toggledAt;
	/** 49-327차: 글자를 친 입력칸(이 칸에서 글자를 치기 전에는 한글/영어 표시를 안 띄운다). */
	private static Object typedFor;
	private static final HangulComposer COMP = new HangulComposer();
	private static boolean shiftDown;
	private static Object lastScreen;
	private static Object lastFocus;
	private static long window;

	/** KeyboardHandler의 keyPress/charTyped를 다시 부르는 길(KeyboardInvokerMixin). */
	public interface Emitter {
		void key(long window, int key, int scancode, int action, int mods);

		void chr(long window, int codePoint);
	}

	private static Emitter emitter;

	public static void setEmitter(Emitter e) {
		emitter = e;
	}

	public static boolean korean() {
		return korean;
	}

	private static boolean on() {
		return HangulInputModule.on();
	}

	/** 매 틱: 윈도우 입력기 떼기, 화면이 바뀌었으면 조합 끝. */
	public static void tick() {
		if (!on()) {
			COMP.reset();
			return;
		}
		// 49-390차(사용자: "한글챗 되긴 되는데 한글 상태에서 키가 안 먹어"): 26.1~26.2는 바닐라가 윈도우 입력기를 직접 다룬다
		// (TextInputManager, GLFW_IME - 입력칸 밖에서는 끄고 입력칸에서는 다시 켬). 우리가 매 틱 입력기를 창에서 떼어 내면 바닐라와 싸워서
		// 조합이 깨지고(자모가 따로) 한글 상태에서 키가 안 먹었다. 이제 26.1~26.2는 떼지 않고 입력기를 "꺼짐"으로만 둔다
		// (바닐라가 켜려는 것도 TextInputImeMixin이 막음) - 키는 영문으로 오고 한글은 우리가 조합한다. 26.3(SDL)은 예전처럼 뗀다.
		if (LunaInput.SDL) {
			WinIme.detach();
		} else {
			WinIme.restore();   // 예전 판에서 떼어 둔 게 있으면 돌려 붙인다(한 번만)
			boolean f = windowFocused();
			if (f != wasFocused) {
				onWindowFocus(f);   // 49-398차: 포커스 콜백(WindowFocusImeMixin)을 놓쳤을 때
			}
			if (f) {
				onOsImeChanged();   // 49-395차: 입력기가 켜져 있으면 한/영 누름으로 보고 끈다
				osImeOff();
			}
		}
		Minecraft mc = Minecraft.getInstance();
		Object screen = mc == null ? null : LunaCompat.screenOf(mc);
		if (screen != lastScreen) {
			lastScreen = screen;
			COMP.reset();
		}
	}

	private static java.lang.reflect.Method glfwGetInputMode;
	private static java.lang.reflect.Method glfwSetInputMode;
	private static boolean glfwLooked;
	/** GLFW_IME(바닐라 TextInputManager가 쓰는 값). */
	private static final int GLFW_IME = 0x33007;

	/** 49-390차: 우리 한글 입력이 켜져 있으면 바닐라가 윈도우 입력기를 켜지 못하게(26.1~26.2, TextInputImeMixin). */
	public static boolean blockOsIme() {
		return on() && !LunaInput.SDL;
	}

	/**
	 * 49-398차(사용자: "마크로 가면 영어로 계속 변환된대 윈도우"): 게임 창에 있는 동안 윈도우 입력기를 꺼 두는데(키를 영문으로 받으려고),
	 * 윈도우 한/영 상태는 창끼리 같이 쓰여서 다른 창으로 나가도 영어로 남았다. 이제 창을 떠날 땐 노바 한/영 상태대로 입력기를 켜고/끄고
	 * 돌려주고, 창에 들어올 땐 윈도우 한/영 상태를 노바 상태로 가져온다(윈도우가 한글이었으면 게임에서도 한글).
	 */
	private static boolean wasFocused;

	private static boolean windowFocused() {
		try {
			Minecraft mc = Minecraft.getInstance();
			return mc != null && mc.getWindow() != null && mc.getWindow().isFocused();
		} catch (Throwable t) {
			return false;
		}
	}

	/** WindowFocusImeMixin(창 포커스 콜백)과 tick에서. */
	public static void onWindowFocus(boolean focused) {
		if (focused == wasFocused) {
			return;
		}
		wasFocused = focused;
		if (!on() || LunaInput.SDL) {
			return;
		}
		COMP.reset();
		if (focused) {
			boolean open = osImeOpen();
			if (open != korean) {
				korean = open;
				osKorean = false;
				toggledAt = System.nanoTime();   // 같이 오는 입력기 알림을 한/영 누름으로 또 치지 않게
			}
			osImeOff();
		} else {
			osImeSet(korean);
		}
	}

	/** 윈도우 입력기를 "꺼짐"으로(GLFW_IME = 0). GLFW는 리플렉션 - 26.3(SDL)에는 없다. */
	private static void osImeOff() {
		osImeSet(false);
	}

	private static void lookGlfw() throws ReflectiveOperationException {
		if (!glfwLooked) {
			glfwLooked = true;
			Class<?> g = Class.forName("org.lwjgl.glfw.GLFW");
			glfwGetInputMode = g.getMethod("glfwGetInputMode", long.class, int.class);
			glfwSetInputMode = g.getMethod("glfwSetInputMode", long.class, int.class, int.class);
		}
	}

	private static void osImeSet(boolean open) {
		try {
			lookGlfw();
			if (glfwGetInputMode == null) {
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			long h = mc == null || mc.getWindow() == null ? 0L : mc.getWindow().handle();
			if (h == 0L) {
				return;
			}
			Object cur = glfwGetInputMode.invoke(null, h, GLFW_IME);
			if (cur instanceof Integer i && (i != 0) != open) {
				glfwSetInputMode.invoke(null, h, GLFW_IME, open ? 1 : 0);
			}
		} catch (Throwable t) {
			glfwGetInputMode = null;
			LunaCompat.warnOnce("hangul:osIme", t);
		}
	}

	/**
	 * 49-395차(사용자: "어떤 분이 한글이 아예 안 써진대", "채팅을 1글자라도 안 치면 ENG/한글이 안 변해"): 26.1~26.2는 입력기를 떼지 않고
	 * "꺼짐"으로만 두는데(49-390차), 그러면 한/영 키가 게임 키로 안 오고 윈도우 입력기가 먹어서 자기를 "켜짐"으로 바꾼다 - 우리는 그걸 몰라
	 * 한글로 안 바뀌었다. 이제 입력기가 켜진 걸 보면(바닐라 입력기 상태 알림 TextInputImeMixin + 매 틱) 그걸 한/영 키로 보고 바꾼 뒤 다시 끈다.
	 * 키 이벤트와 입력기 알림이 한 번 누름에 둘 다 올 수 있어 0.2초 안의 두 번째는 무시한다.
	 */
	private static void toggleKorean() {
		long now = System.nanoTime();
		if (toggledAt != 0 && now - toggledAt < 200_000_000L) {
			return;
		}
		korean = !korean;
		osKorean = false;
		toggledAt = now;
		COMP.reset();
	}

	/** TextInputImeMixin(바닐라가 입력기 상태 바뀜 알림을 받을 때)과 tick에서. 입력기가 켜졌으면 한/영 누름으로 본다. */
	/** 49-398차: 창 밖에서는 안 본다(창을 떠날 때 우리가 켜 준 입력기를 한/영 누름으로 치지 않게). 들어올 땐 onWindowFocus가 맞춘다. */
	public static void onOsImeChanged() {
		if (!on() || LunaInput.SDL || !wasFocused) {
			return;
		}
		if (osImeOpen()) {
			toggleKorean();
			osImeOff();
		}
	}

	private static boolean osImeOpen() {
		try {
			lookGlfw();
			if (glfwGetInputMode == null) {
				return false;
			}
			Minecraft mc = Minecraft.getInstance();
			long h = mc == null || mc.getWindow() == null ? 0L : mc.getWindow().handle();
			return h != 0L && glfwGetInputMode.invoke(null, h, GLFW_IME) instanceof Integer i && i != 0;
		} catch (Throwable t) {
			return false;
		}
	}

	/** 기능을 끌 때. */
	public static void disabled() {
		COMP.reset();
		korean = false;
		WinIme.restore();
	}

	/** 마우스 버튼을 눌렀다(커서가 옮겨졌을 수 있으니 조합 끝). */
	public static void mouseClicked() {
		COMP.reset();
	}

	private static boolean hangulKey(int key, int scancode) {
		if (LunaInput.SDL) {
			return key == 144;   // SDL_SCANCODE_LANG1(한/영)
		}
		if (key != -1) {
			return false;
		}
		int s = scancode & 0xFF;
		return s == 0x72 || s == 0xF2;   // 윈도우 한/영 키(입력기를 떼면 알 수 없는 키로 온다)
	}

	private static boolean modifierKey(int key) {
		return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
				|| key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
				|| key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
				|| key == InputConstants.KEY_CAPSLOCK || key == LunaInput.KEY_LSUPER;
	}

	/** 키 이벤트. true면 게임에 넘기지 않는다. action 1 누름, 2 반복, 0 뗌. */
	public static boolean onKey(long win, int key, int scancode, int action, int mods) {
		if (synthetic || !on()) {
			return false;
		}
		window = win;
		if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
			shiftDown = action != 0;
		}
		if (action == 0) {
			return false;
		}
		boolean typing = textFocused();
		if (action == 1 && (hangulKey(key, scancode) || (key == HangulInputModule.toggleKey() && key != -1))) {
			toggleKorean();
			if (!LunaInput.SDL) {
				osImeOff();   // 한/영 키가 윈도우 입력기를 켰으면 바로 끈다(한글은 우리가)
			}
			return typing;
		}
		if (!COMP.composing()) {
			return false;
		}
		if (key == InputConstants.KEY_BACKSPACE && typing && (mods & 0x2) == 0) {   // Ctrl+백스페이스(단어 지우기)는 그대로
			String rest = COMP.backspace();
			if (rest == null) {
				return false;
			}
			sendBackspace(win);
			sendText(win, rest);
			return true;
		}
		boolean letter = key >= InputConstants.KEY_A && key <= InputConstants.KEY_Z;
		if (modifierKey(key) || (letter && (mods & 0x6) == 0)) {   // Ctrl/Alt 없는 글자 키 = 곧 글자가 온다
			return false;
		}
		COMP.reset();
		return false;
	}

	/** 글자 이벤트. true면 게임에 넘기지 않는다(대신 우리가 조합한 글자를 보낸다). */
	public static boolean onChar(long win, int codePoint) {
		if (synthetic || !on()) {
			return false;
		}
		window = win;
		boolean typing = textFocused();
		if (typing) {
			typedFor = focusKey();
		}
		// 49-386차(사용자 사진: 채팅에 "wa ㅇㅁㅈ da ㅈㅁ" - 자모가 하나씩 따로 들어감, 표시는 ENG): 윈도우 입력기가 창에 다시 붙어
		// (떼기가 안 먹은 경우) 한글 상태면 입력기가 자모를 하나씩 보낸다. 그 자모를 우리가 받아 조합한다.
		// 영문 글자는 입력기가 영문 상태라는 뜻이라 예전처럼(우리 한/영 상태대로) 둔다.
		boolean osJamo = typing && codePoint >= 0x3131 && codePoint <= 0x3163;
		if (typing && !osJamo && codePoint >= 'A' && codePoint <= 'z') {
			osKorean = false;
		}
		if (osJamo) {
			if (!osKorean) {
				osKorean = true;
				toggledAt = System.nanoTime();
			}
		} else if (!korean || !typing) {
			COMP.reset();
			return false;
		}
		boolean upper = codePoint >= 'A' && codePoint <= 'Z';
		if (upper && !shiftDown) {
			COMP.reset();   // Caps Lock: 영문 대문자 그대로
			return false;
		}
		char jamo = osJamo ? (char) codePoint : HangulComposer.jamoOf(codePoint, shiftDown);
		if (jamo == 0) {
			COMP.reset();
			return false;
		}
		Object focus = focusKey();
		if (focus != lastFocus) {
			lastFocus = focus;
			COMP.reset();
		}
		boolean had = COMP.composing();
		HangulComposer.Result r = COMP.input(jamo);
		if (had) {
			sendBackspace(win);
		}
		sendText(win, r.commit + r.preedit);
		return true;
	}

	private static void sendBackspace(long win) {
		Emitter e = emitter;
		if (e == null) {
			return;
		}
		synthetic = true;
		try {
			int second = LunaInput.SDL ? 8 : 14;   // 26.3: SDL 키코드(SDLK_BACKSPACE), 26.2까지: 윈도우 스캔코드
			e.key(win, InputConstants.KEY_BACKSPACE, second, 1, 0);
			e.key(win, InputConstants.KEY_BACKSPACE, second, 0, 0);
		} catch (Throwable t) {
			LunaCompat.warnOnce("hangul:key", t);
		} finally {
			synthetic = false;
		}
	}

	private static void sendText(long win, String s) {
		Emitter e = emitter;
		if (e == null || s.isEmpty()) {
			return;
		}
		synthetic = true;
		try {
			for (int i = 0; i < s.length(); i++) {
				e.chr(win, s.charAt(i));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("hangul:char", t);
		} finally {
			synthetic = false;
		}
	}

	// ==================== 입력칸에 초점이 있는가 ====================

	private static final Map<Class<?>, List<Field>> FIELDS = new java.util.HashMap<>();

	/** 지금 글자를 받을 입력칸이 있는가(채팅, 표지판, 책, 초점 받은 입력칸, 우리 화면의 입력, 패널 검색창). */
	public static boolean textFocused() {
		if (TextCapture.active()) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		Object s = mc == null ? null : LunaCompat.screenOf(mc);
		if (s == null) {
			return false;
		}
		if (s instanceof ChatScreen
				|| s instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen
				|| s instanceof net.minecraft.client.gui.screens.inventory.BookEditScreen) {
			return true;
		}
		if (lunaTextScreen(s.getClass())) {
			return true;
		}
		return focusedBox(s) != null;
	}

	/** 우리 화면(LunaScreenBase) 중 글자를 받는 것(lunaCharTyped를 직접 가진 화면). 받을 칸이 없으면 글자를 그냥 무시한다. */
	private static boolean lunaTextScreen(Class<?> c) {
		Boolean b = LUNA_TEXT.get(c);
		if (b == null) {
			b = false;
			for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
				if (k.getName().equals("kr.lunaslight.mod.gui.LunaScreenBase")) {
					break;
				}
				try {
					k.getDeclaredMethod("lunaCharTyped", char.class);
					b = true;
					break;
				} catch (NoSuchMethodException ignored) {
				}
			}
			LUNA_TEXT.put(c, b);
		}
		return b;
	}

	private static final Map<Class<?>, Boolean> LUNA_TEXT = new java.util.HashMap<>();

	/** 초점을 받은 입력칸(EditBox, 여러 줄 입력칸). 화면의 초점 요소, 화면 필드, 레시피 책 안까지 본다. 없으면 null. */
	private static Object focusedBox(Object screen) {
		try {
			if (screen instanceof Screen sc) {
				Object f = sc.getFocused();
				if (boxFocused(f)) {
					return f;
				}
				// 49-338차(사용자: 서버 대화 상자(닉네임 입력)에서 "이 화면에서 한글이 안 돼"): 입력칸이 화면의 필드가 아니라 레이아웃/스크롤
				// 컨테이너 안에 들어 있는 화면(26.x 서버 Dialog 등)은 getFocused가 컨테이너를 돌려준다. 초점 사슬을 따라 내려가고,
				// 그래도 없으면 자식 트리를 훑어 초점 받은 입력칸을 찾는다.
				Object deep = deepFocus(f, 0);
				if (deep != null) {
					return deep;
				}
				deep = findFocusedBox(sc, 0);
				if (deep != null) {
					return deep;
				}
			}
			for (Field f : fieldsOf(screen.getClass())) {
				Object v = f.get(screen);
				if (boxFocused(v)) {
					return v;
				}
				if (v instanceof net.minecraft.client.gui.screens.recipebook.RecipeBookComponent) {
					for (Field g : fieldsOf(v.getClass())) {
						Object w = g.get(v);
						if (boxFocused(w)) {
							return w;
						}
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("hangul:focus", t);
		}
		return null;
	}

	/** 초점 사슬(컨테이너 → 그 안의 초점 → …)을 따라 입력칸까지. */
	private static Object deepFocus(Object o, int depth) {
		if (o == null || depth > 8) {
			return null;
		}
		if (boxFocused(o)) {
			return o;
		}
		if (o instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c) {
			return deepFocus(c.getFocused(), depth + 1);
		}
		return null;
	}

	/** 자식 트리 전체에서 초점 받은 입력칸(깊이 6, 500개까지). */
	private static Object findFocusedBox(Object o, int depth) {
		if (!(o instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c) || depth > 6) {
			return null;
		}
		int n = 0;
		for (Object child : c.children()) {
			if (++n > 500) {
				break;
			}
			if (boxFocused(child)) {
				return child;
			}
			Object r = findFocusedBox(child, depth + 1);
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	private static boolean boxFocused(Object o) {
		if (o instanceof EditBox e) {
			return e.canConsumeInput();
		}
		if (o instanceof MultiLineEditBox m) {
			return m.isFocused() && m.visible;
		}
		return false;
	}

	private static List<Field> fieldsOf(Class<?> c) {
		List<Field> out = FIELDS.get(c);
		if (out == null) {
			out = new ArrayList<>();
			for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
				for (Field f : k.getDeclaredFields()) {
					Class<?> t = f.getType();
					if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					if (EditBox.class.isAssignableFrom(t) || MultiLineEditBox.class.isAssignableFrom(t)
							|| net.minecraft.client.gui.screens.recipebook.RecipeBookComponent.class.isAssignableFrom(t)) {
						try {
							f.setAccessible(true);
							out.add(f);
						} catch (Throwable ignored) {
						}
					}
				}
			}
			FIELDS.put(c, out);
		}
		return out;
	}

	/** 조합이 이어지는 "같은 자리"인지 보는 열쇠(화면 + 초점 입력칸). */
	private static Object focusKey() {
		Minecraft mc = Minecraft.getInstance();
		Object s = mc == null ? null : LunaCompat.screenOf(mc);
		Object box = s == null ? null : focusedBox(s);
		return box != null ? box : s;
	}

	// ==================== 한/A 표시 ====================

	/**
	 * 49-324차(사용자: "영어 한글 뜨는 게 너무 촌스럽고 채팅에도 뜨기 해 달라고 그리고 글자 끝에 뜨게 해 달라니까"): 표시가 입력칸 오른쪽
	 * 끝 위에 떠서(채팅은 화면 오른쪽 끝) 안 보였다. 이제 <b>친 글자 바로 뒤</b>(커서 자리 다음)에 붙는다 - 채팅, 검색창, 모루 등 모든
	 * 입력칸(EditBox). 칸 끝까지 글자가 차면 칸 오른쪽 안쪽에 멈춘다. 여러 줄 칸, 표지판, 책, 우리 화면은 칸 위치를 알 수 없어 예전 자리(칸 오른쪽 위).
	 */
	public static void drawIndicator(Object screen, GuiGraphicsExtractor ctx) {
		if (!on() || !HangulInputModule.showIndicator() || !(screen instanceof Screen sc) || !textFocused()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		net.minecraft.client.gui.Font font = mc.font;
		Object box = focusedBox(screen);
		String typedValue = box instanceof EditBox eb ? eb.getValue() : null;
		if (!visibleNow(box != null ? box : screen, typedValue)) {
			return;
		}
		if (screen instanceof ChatScreen && typedValue != null && typedValue.startsWith("/")) {
			return;   // 49-340차(사용자: "명령어 쓸 때에는 안 뜨게 - 어차피 영어인데"): 채팅에서 /로 시작하면 표시 안 함
		}
		if (box instanceof EditBox e && e.isVisible()) {
			String value = e.getValue();
			int disp = intField(e, "displayPos", 0);
			disp = Math.max(0, Math.min(disp, value.length()));
			boolean bordered = e.isBordered();
			int tx = intField(e, "textX", bordered ? e.getX() + 4 : e.getX());
			int ty = intField(e, "textY", bordered ? e.getY() + (e.getHeight() - 8) / 2 : e.getY());
			int inner = Math.max(0, e.getInnerWidth());
			int shown = Math.min(LunaDraw.width(font, value.substring(disp)), inner);
			// 49-395차(사용자: "글자랑 너무 떨어져"): 위 대각선 대신 글자 끝(커서 _ 뒤) 바로 옆, 글자 줄 가운데에
			int x = tx + shown + 7;
			int limit = Math.min(e.getX() + e.getWidth() - (bordered ? 2 : 0), sc.width - 1);
			drawChip(ctx, font, x, ty + 4 - CHIP_H / 2 - 1, limit);
			return;
		}
		if (box instanceof AbstractWidget aw) {
			int w = chipWidth(font);
			drawChip(ctx, font, aw.getX() + aw.getWidth() - w, aw.getY() - 12, sc.width - 1);
		} else {
			int w = chipWidth(font);
			drawChip(ctx, font, sc.width - 4 - w, sc.height - 16, sc.width - 1);
		}
	}

	/** 지금 표시의 진하기(0 = 안 띄움). drawChip이 곱한다. */
	private static float chipFade = 1f;

	/**
	 * 49-327차(사용자: "채팅이나 글 치기 전까지는 영어 한글 안 뜨게"): 칸이 열리기만 해서는 안 띄운다. 그 칸에서 글자를 쳤고(칸에 글자가
	 * 남아 있을 때 - 다 지우면 다시 숨김) 그때만 띄운다. 한/영을 바꾼 직후 1.5초는 글자를 안 쳤어도 잠깐 띄웠다가 흐려지며 사라진다.
	 */
	private static boolean visibleNow(Object focus, String value) {
		boolean typed = typedFor != null && typedFor == focus && (value == null || !value.isEmpty());
		if (typed) {
			chipFade = 1f;
			return true;
		}
		long since = System.nanoTime() - toggledAt;
		if (toggledAt == 0 || since >= 1_500_000_000L) {
			return false;
		}
		chipFade = since <= 1_200_000_000L ? 1f : (1_500_000_000L - since) / 300_000_000f;
		return true;
	}

	private static int intField(Object o, String name, int fallback) {
		Object v = LunaCompat.getFieldValue(o, name);
		return v instanceof Integer i ? i : fallback;
	}

	private static String chipLabel() {
		return korean || osKorean ? "한글" : "ENG";
	}

	private static int chipWidth(net.minecraft.client.gui.Font font) {
		return LunaDraw.width(font, chipLabel()) + 8;
	}

	private static final int CHIP_H = 11;

	/**
	 * 49-327차(사용자: "오른대각에 뜨게, 배경은 인게임 UI 따라서 깔끔하게, 영어는 ENG, 한글은 한글, 색은 같고"): 작은 카드 하나 -
	 * 지금 화면 스킨의 카드 색, 테두리, 아래 두께(크림 = 깔끔한 입체, 미드나잇/네온 = 그 스킨 카드)를 그대로 쓰고 글자는 본문색 하나.
	 * 바꿀 때 0.12초 동안 나타나고, 한/영 직후 잠깐 띄울 때는 흐려지며 사라진다(chipFade). 칸 위가 화면 밖이면 칸 아래로.
	 */
	private static void drawChip(GuiGraphicsExtractor ctx, net.minecraft.client.gui.Font font, int x, int y, int limitRight) {
		String label = chipLabel();
		int w = chipWidth(font);
		int h = CHIP_H;
		if (x + w > limitRight) {
			x = limitRight - w;
		}
		if (y < 0) {
			y = y + h + 16;
		}
		float t = Math.min(1f, (System.nanoTime() - toggledAt) / 120_000_000f);
		float a = (0.3f + 0.7f * (1f - (1f - t) * (1f - t))) * Math.max(0f, Math.min(1f, chipFade));
		float prev = LunaDraw.alpha();
		LunaDraw.setAlpha(prev * a);
		try {
			int r = 3;
			int fill = LunaDraw.surfaceCard();
			int line = LunaDraw.surfaceLine();
			if (!LunaDraw.skinCard(ctx, x, y, w, h, r, fill, line)) {
				LunaDraw.roundRect(ctx, x, y + 1, w, h, r, LunaDraw.soft() ? LunaDraw.CREAM_LIP : LunaDraw.surfaceEdge());
				LunaDraw.roundRect(ctx, x, y, w, h, r, line);
				LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, r - 1, fill);
			}
			LunaDraw.text(ctx, font, label, x + 4, LunaDraw.textY(y, h), LunaDraw.TEXT);
		} finally {
			LunaDraw.setAlpha(prev);
		}
	}

}
