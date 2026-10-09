package kr.lunaslight.mod.util;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.impl.chat.HangulInputModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 49-313차(사용자: "한글챗 내장 만들어봐야 할 거 같아" → 모든 입력칸, 한/영 키, 윈도우 입력기는 모드가 알아서 끔): 모드 내장 한글 입력.
 *
 * <p>흐름: GLFW 키/글자 콜백을 감싼 곳(LunaCompat.ensureTextCapture)이 여기로 먼저 보낸다(1.14.4 ~ 1.21.11 모두 같은 길).
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
	/** 49-315차: 마지막으로 한/영을 바꾼 때(표시 애니메이션). */
	private static long toggledAt;
	private static final HangulComposer COMP = new HangulComposer();
	private static boolean shiftDown;
	private static Object lastScreen;
	private static Object lastFocus;
	private static long window;

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
		WinIme.detach();
		MinecraftClient mc = MinecraftClient.getInstance();
		LunaCompat.ensureTextCapture();       // GLFW 키/글자 콜백 감싸기(한 번)
		LunaCompat.ensureMouseClickCounter(); // 클릭하면 조합 끝
		Object screen = mc == null ? null : mc.currentScreen;
		if (screen != lastScreen) {
			lastScreen = screen;
			COMP.reset();
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
		if (key != -1) {
			return false;
		}
		int sc = scancode & 0xFF;
		return sc == 0x72 || sc == 0xF2;   // 윈도우 한/영 키(입력기를 떼면 알 수 없는 키로 온다)
	}

	private static boolean modifierKey(int key) {
		return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
				|| key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
				|| key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
				|| key == GLFW.GLFW_KEY_CAPS_LOCK || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
	}

	/** 키 이벤트. true면 게임에 넘기지 않는다. action 1 누름, 2 반복, 0 뗌. */
	public static boolean onKey(long win, int key, int scancode, int action, int mods) {
		if (synthetic || !on()) {
			return false;
		}
		window = win;
		if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
			shiftDown = action != 0;
		}
		if (action == 0) {
			return false;
		}
		boolean typing = textFocused();
		if (action == 1 && (hangulKey(key, scancode) || (key == HangulInputModule.toggleKey() && key != -1))) {
			korean = !korean;
			toggledAt = System.nanoTime();
			COMP.reset();
			return typing;
		}
		if (!COMP.composing()) {
			return false;
		}
		if (key == GLFW.GLFW_KEY_BACKSPACE && typing && (mods & 0x2) == 0) {   // Ctrl+백스페이스(단어 지우기)는 그대로
			String rest = COMP.backspace();
			if (rest == null) {
				return false;
			}
			sendBackspace(win);
			sendText(win, rest);
			return true;
		}
		boolean letter = key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z;
		if (modifierKey(key) || (letter && (mods & 0x6) == 0)) {   // Ctrl/Alt 없는 글자 키 = 곧 글자가 온다
			return false;
		}
		COMP.reset();
		return false;
	}

	/** 글자 이벤트. true면 게임에 넘기지 않는다(대신 우리가 조합한 글자를 보낸다). */
	public static boolean onChar(long win, int codePoint, int mods) {
		if (synthetic || !on()) {
			return false;
		}
		window = win;
		shiftDown = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
		if (!korean || !textFocused()) {
			COMP.reset();
			return false;
		}
		boolean upper = codePoint >= 'A' && codePoint <= 'Z';
		if (upper && !shiftDown) {
			COMP.reset();   // Caps Lock: 영문 대문자 그대로
			return false;
		}
		char jamo = HangulComposer.jamoOf(codePoint, shiftDown);
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
		synthetic = true;
		try {
			int sc = 14;
			try {
				sc = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_BACKSPACE);
			} catch (Throwable ignored) {
			}
			LunaCompat.emitKey(win, GLFW.GLFW_KEY_BACKSPACE, sc, GLFW.GLFW_PRESS, 0);
			LunaCompat.emitKey(win, GLFW.GLFW_KEY_BACKSPACE, sc, GLFW.GLFW_RELEASE, 0);
		} catch (Throwable t) {
			LunaCompat.warnOnce("hangul:key", t);
		} finally {
			synthetic = false;
		}
	}

	private static void sendText(long win, String s) {
		if (s.isEmpty()) {
			return;
		}
		synthetic = true;
		try {
			for (int i = 0; i < s.length(); i++) {
				LunaCompat.emitChar(win, s.charAt(i), 0);
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
		MinecraftClient mc = MinecraftClient.getInstance();
		Object s = mc == null ? null : mc.currentScreen;
		if (s == null) {
			return false;
		}
		if (s instanceof ChatScreen || textScreen(s.getClass())) {
			return true;
		}
		if (lunaTextScreen(s.getClass())) {
			return true;
		}
		return focusedBox(s) != null;
	}

	private static final Map<Class<?>, Boolean> TEXT_SCREEN = new java.util.HashMap<>();
	private static final String[] TEXT_SCREENS = {
		"net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen",
		"net.minecraft.client.gui.screen.ingame.SignEditScreen",
		"net.minecraft.client.gui.screen.ingame.BookEditScreen"};

	/** 표지판, 책 쓰기 화면(입력칸 위젯 없이 화면이 직접 글자를 받는다). 런타임 이름이 버전마다 달라 Yarn 이름으로 비교. */
	private static boolean textScreen(Class<?> c) {
		Boolean b = TEXT_SCREEN.get(c);
		if (b == null) {
			b = false;
			outer:
			for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
				for (String n : TEXT_SCREENS) {
					if (LunaCompat.classIs(k, n)) {
						b = true;
						break outer;
					}
				}
			}
			TEXT_SCREEN.put(c, b);
		}
		return b;
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

	private static Class<?> multiLineBox;
	private static Class<?> recipeBook;
	private static boolean classesLooked;

	private static void lookClasses() {
		if (classesLooked) {
			return;
		}
		classesLooked = true;
		try {
			multiLineBox = LunaCompat.classForName("net.minecraft.client.gui.widget.EditBoxWidget");   // 1.19.3+
		} catch (Throwable ignored) {
		}
		try {
			recipeBook = LunaCompat.classForName("net.minecraft.client.gui.screen.recipebook.RecipeBookWidget");
		} catch (Throwable ignored) {
		}
	}

	/** 초점을 받은 입력칸(TextFieldWidget, 여러 줄 입력칸). 화면의 초점 요소, 화면 필드, 레시피 책 안까지 본다. 없으면 null. */
	private static Object focusedBox(Object screen) {
		lookClasses();
		try {
			Object f = LunaCompat.callNoArg(screen, "getFocused");
			if (boxFocused(f)) {
				return f;
			}
			for (Field fd : fieldsOf(screen.getClass())) {
				Object v = fd.get(screen);
				if (boxFocused(v)) {
					return v;
				}
				if (v != null && recipeBook != null && recipeBook.isInstance(v)) {
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

	private static boolean boxFocused(Object o) {
		if (o instanceof TextFieldWidget) {
			if (!Boolean.TRUE.equals(LunaCompat.callNoArg(o, "isFocused"))) {
				return false;
			}
			Object vis = LunaCompat.callNoArg(o, "isVisible");
			Object ed = LunaCompat.getFieldValue(o, "editable");
			return !Boolean.FALSE.equals(vis) && !Boolean.FALSE.equals(ed);
		}
		if (o != null && multiLineBox != null && multiLineBox.isInstance(o)) {
			return Boolean.TRUE.equals(LunaCompat.callNoArg(o, "isFocused"));
		}
		return false;
	}

	private static List<Field> fieldsOf(Class<?> c) {
		List<Field> out = FIELDS.get(c);
		if (out == null) {
			lookClasses();
			out = new ArrayList<>();
			for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
				for (Field f : k.getDeclaredFields()) {
					Class<?> t = f.getType();
					if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					if (TextFieldWidget.class.isAssignableFrom(t)
							|| (multiLineBox != null && multiLineBox.isAssignableFrom(t))
							|| (recipeBook != null && recipeBook.isAssignableFrom(t))) {
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
		MinecraftClient mc = MinecraftClient.getInstance();
		Object s = mc == null ? null : mc.currentScreen;
		Object box = s == null ? null : focusedBox(s);
		return box != null ? box : s;
	}

	// ==================== 한/A 표시 ====================

	public static void drawIndicator(Object screen, DrawContext ctx) {
		if (!on() || !HangulInputModule.showIndicator() || !(screen instanceof Screen) || !textFocused()) {
			return;
		}
		Screen sc = (Screen) screen;
		MinecraftClient mc = MinecraftClient.getInstance();
		int anchorRight;
		int anchorTop;
		Object box = screen instanceof ChatScreen ? null : focusedBox(screen);
		if (screen instanceof ChatScreen) {
			anchorRight = sc.width - 2;
			anchorTop = sc.height - 14;
		} else if (box != null && intOf(box, "getWidth", "width") > 0) {
			anchorRight = intOf(box, "getX", "x") + intOf(box, "getWidth", "width");
			anchorTop = intOf(box, "getY", "y");
		} else {
			anchorRight = sc.width - 4;
			anchorTop = sc.height - 4;
		}
		drawBadge(ctx, mc.textRenderer, anchorRight, anchorTop);
	}


	/**
	 * 49-315차(사용자: "채팅 끝에도 표시해 주고 좀 예쁘게 - 지금 너무 밋밋하고 A 한 이게 뭐야"): 입력칸 오른쪽 끝 바로 위에 뜨는 작은 알약.
	 * 한글이면 테마색 그라데이션 + 밝은 테두리 + 흰 점과 "한글", 영어면 어두운 유리 + 회색 점과 "영어". 바꾸는 순간 0.18초 동안 색과 폭이
	 * 부드럽게 넘어가고 살짝 떠올랐다 내려앉는다. 아래에 옅은 그림자. 채팅은 입력줄 오른쪽 끝 위에 붙는다(입력 글자를 안 가린다).
	 */
	private static void drawBadge(DrawContext ctx, net.minecraft.client.font.TextRenderer font, int right, int top) {
		float t = Math.min(1f, (System.nanoTime() - toggledAt) / 180_000_000f);
		float e = 1f - (1f - t) * (1f - t) * (1f - t);   // 끝에서 느려지게
		boolean ko = korean;
		String label = ko ? "한글" : "영어";
		String prevLabel = ko ? "영어" : "한글";
		int h = 11;
		int dot = 3;
		int padL = 5;
		int padR = 5;
		int gap = 3;
		int wNow = padL + dot + gap + LunaDraw.width(font, label) + padR;
		int wPrev = padL + dot + gap + LunaDraw.width(font, prevLabel) + padR;
		int w = Math.round(wPrev + (wNow - wPrev) * e);
		int lift = t < 1f ? Math.round((float) Math.sin(t * Math.PI) * 2f) : 0;
		int x = right - w;
		int y = top - h - 2 - lift;
		if (y < 0) {
			y = top + 14 + 2;
		}
		int accent = LunaDraw.ACCENT | 0xFF000000;
		int koTop = LunaDraw.lerpColor(accent, 0xFFFFFFFF, 0.20f);
		int koBottom = LunaDraw.lerpColor(accent, 0xFF000000, 0.15f);
		int koBorder = LunaDraw.lerpColor(accent, 0xFFFFFFFF, 0.45f);
		int enTop = 0xF22C3038;
		int enBottom = 0xF21B1E24;
		int enBorder = 0x66FFFFFF;
		float k = ko ? e : 1f - e;   // 1 = 한글 색
		int gTop = LunaDraw.lerpColor(enTop, koTop, k);
		int gBottom = LunaDraw.lerpColor(enBottom, koBottom, k);
		int border = LunaDraw.lerpColor(enBorder, koBorder, k);
		int dotColor = LunaDraw.lerpColor(0xFF8E959D, 0xFFFFFFFF, k);
		int textColor = LunaDraw.lerpColor(0xFFD5D9DE, 0xFFFFFFFF, k);
		LunaDraw.roundRect(ctx, x, y + 1, w, h, h / 2, 0x40000000);                 // 그림자
		LunaDraw.roundRectGradient(ctx, x, y, w, h, h / 2, gTop, gBottom);
		LunaDraw.roundRectOutline(ctx, x, y, w, h, h / 2, border);
		LunaDraw.roundRect(ctx, x + padL, y + (h - dot) / 2, dot, dot, 1, dotColor);   // 상태 점
		int tx = x + padL + dot + gap;
		int alpha = Math.round(255 * Math.min(1f, 0.35f + 0.65f * e));
		LunaDraw.text(ctx, font, label, tx, y + 2, (textColor & 0x00FFFFFF) | (alpha << 24));
	}
	private static int intOf(Object o, String method, String field) {
		Object v = LunaCompat.callNoArg(o, method);
		if (v instanceof Integer) {
			return (Integer) v;
		}
		v = LunaCompat.getFieldValue(o, field);
		return v instanceof Integer ? (Integer) v : 0;
	}
}
