package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-7차: "키 지정을 한번에 볼 수 있는 공간을 키보드 모양으로" - 실제 키보드 배열을 그려서
 * 어떤 키에 어떤 기능이 지정됐는지 보여주고, 같은 키에 2개 이상 지정되면 빨간 경고를 띄움.
 * 모든 모듈의 토글 키 + 기능별 단축키(KeybindSetting 전부) + 메뉴 열기(R-Shift 고정)를 수집.
 *
 * 49-39차 재구성(사용자: "키 지정도 크게 해서 오른쪽에 키 지정 따로 할 수 있게 · 원까지 떠 있는 건 투머치 ·
 * 제목은 그냥 '키 지정'"): 왼쪽 = 키보드(색만, 점·숫자 없음), **오른쪽 = 항상 떠 있는 키 지정 패널**
 * (기능 목록 + 현재 키 칩). 줄을 누르면 "키를 누르세요" 상태가 되어 다음 키 입력(조합키 포함)이 그 기능에 지정되고,
 * 키보드 그림의 키를 눌러도 지정된다. 칩의 ✕ = 해제. 키보드 키에 마우스를 올리면 오른쪽 목록에서 그 키의
 * 기능들이 강조된다. 예전 팝업은 없앰.
 */
public class LunaKeybindsScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 600, 340);

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			if (lunaV.needsInit()) {
				lunaInit0();
				lunaV.markInit();
			}
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected void init() {
		boolean e = lunaV.enter();
		try {
			lunaInit0();
			lunaV.markInit();
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseClicked0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseScrolled0(mouseX * k, mouseY * k, verticalAmount);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaKeyPressed0(keyCode, scanCode, modifiers);
		} finally {
			lunaV.exit(e);
		}
	}
	private final Screen parent;

	private record KeyDef(String label, int code, float units) {
	}

	private static final List<List<KeyDef>> ROWS = new ArrayList<>();

	static {
		List<KeyDef> r0 = new ArrayList<>();
		r0.add(new KeyDef("ESC", GLFW.GLFW_KEY_ESCAPE, 1f));
		for (int i = 0; i < 12; i++) {
			r0.add(new KeyDef("F" + (i + 1), GLFW.GLFW_KEY_F1 + i, 1f));
		}
		r0.add(new KeyDef("", -1, 2f)); // 자리 맞춤용 빈칸
		ROWS.add(r0);

		List<KeyDef> r1 = new ArrayList<>();
		r1.add(new KeyDef("`", GLFW.GLFW_KEY_GRAVE_ACCENT, 1f));
		String digits = "1234567890";
		for (int i = 0; i < 10; i++) {
			r1.add(new KeyDef(String.valueOf(digits.charAt(i)), GLFW.GLFW_KEY_1 + ((i + 1) % 10 == 0 ? -1 : i)
				, 1f));
		}
		r1.set(10, new KeyDef("0", GLFW.GLFW_KEY_0, 1f));
		r1.add(new KeyDef("-", GLFW.GLFW_KEY_MINUS, 1f));
		r1.add(new KeyDef("=", GLFW.GLFW_KEY_EQUAL, 1f));
		r1.add(new KeyDef("⌫", GLFW.GLFW_KEY_BACKSPACE, 2f));
		ROWS.add(r1);

		List<KeyDef> r2 = new ArrayList<>();
		r2.add(new KeyDef("TAB", GLFW.GLFW_KEY_TAB, 1.5f));
		for (char c : "QWERTYUIOP".toCharArray()) {
			r2.add(new KeyDef(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'A'), 1f));
		}
		r2.add(new KeyDef("[", GLFW.GLFW_KEY_LEFT_BRACKET, 1f));
		r2.add(new KeyDef("]", GLFW.GLFW_KEY_RIGHT_BRACKET, 1f));
		r2.add(new KeyDef("\\", GLFW.GLFW_KEY_BACKSLASH, 1.5f));
		ROWS.add(r2);

		List<KeyDef> r3 = new ArrayList<>();
		r3.add(new KeyDef("CAPS", GLFW.GLFW_KEY_CAPS_LOCK, 1.75f));
		for (char c : "ASDFGHJKL".toCharArray()) {
			r3.add(new KeyDef(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'A'), 1f));
		}
		r3.add(new KeyDef(";", GLFW.GLFW_KEY_SEMICOLON, 1f));
		r3.add(new KeyDef("'", GLFW.GLFW_KEY_APOSTROPHE, 1f));
		r3.add(new KeyDef("⏎", GLFW.GLFW_KEY_ENTER, 2.25f));
		ROWS.add(r3);

		List<KeyDef> r4 = new ArrayList<>();
		r4.add(new KeyDef("SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT, 2.25f));
		for (char c : "ZXCVBNM".toCharArray()) {
			r4.add(new KeyDef(String.valueOf(c), GLFW.GLFW_KEY_A + (c - 'A'), 1f));
		}
		r4.add(new KeyDef(",", GLFW.GLFW_KEY_COMMA, 1f));
		r4.add(new KeyDef(".", GLFW.GLFW_KEY_PERIOD, 1f));
		r4.add(new KeyDef("/", GLFW.GLFW_KEY_SLASH, 1f));
		r4.add(new KeyDef("SHIFT", GLFW.GLFW_KEY_RIGHT_SHIFT, 2.75f));
		ROWS.add(r4);

		List<KeyDef> r5 = new ArrayList<>();
		r5.add(new KeyDef("CTRL", GLFW.GLFW_KEY_LEFT_CONTROL, 1.5f));
		r5.add(new KeyDef("WIN", GLFW.GLFW_KEY_LEFT_SUPER, 1.25f));
		r5.add(new KeyDef("ALT", GLFW.GLFW_KEY_LEFT_ALT, 1.25f));
		r5.add(new KeyDef("", GLFW.GLFW_KEY_SPACE, 6.5f));
		r5.add(new KeyDef("ALT", GLFW.GLFW_KEY_RIGHT_ALT, 1.25f));
		r5.add(new KeyDef("MENU", GLFW.GLFW_KEY_MENU, 1.25f));
		r5.add(new KeyDef("CTRL", GLFW.GLFW_KEY_RIGHT_CONTROL, 2f));
		ROWS.add(r5);
	}

	/** 키코드 → 지정된 기능 이름들. */
	private final Map<Integer, List<String>> bindings = new HashMap<>();
	/** 49-22차: 키코드 → 마크(바닐라/다른 모드) 기능 이름들 - "키보드 화면에 마크 기본 키 표시". */
	private final Map<Integer, List<String>> vanilla = new HashMap<>();
	private int conflictCount;
	private int overlapCount;   // Luna 키가 마크 키와 겹치는 수
	private final List<String> mouseLines = new ArrayList<>(); // 마우스 버튼 지정 목록

	private record Bindable(Module module, KeybindSetting setting) {
		String label() {
			String own = userText();
			// 49-245차: 묶음이 있는 키(시점 고정의 "시점 2" 등)는 묶음 이름을 앞에 붙여 어느 키인지 보이게
			String g = setting.getGroupName();
			return module.getDisplayName() + " | " + (own != null ? own : g != null ? g + " " + setting.getDisplayName() : setting.getDisplayName());
		}

		/**
		 * 49-69차(4-10): 내가 만든 키("내 키" 모듈)는 <b>번호 대신 실제로 보낼 글</b>을 보여 준다.
		 * "내 키 · 3번 키"는 키보드 그림에서 아무 도움이 안 된다 - "내 키 · /home"이라고 떠야
		 * 어느 키가 무엇인지 한눈에 보인다.
		 *
		 * <p>모듈 이름을 박아 두지 않고 <b>설정 id 규칙</b>(keyN ↔ textN)으로 찾는다 - 나중에 같은
		 * 모양의 모듈이 생겨도 그대로 동작한다.
		 */
		private String userText() {
			String id = setting.getId();
			if (id == null || !id.startsWith("key")) {
				return null;
			}
			String twin = "text" + id.substring(3);
			for (Setting<?> s : module.getSettings()) {
				if (twin.equals(s.getId()) && s instanceof kr.lunaslight.mod.module.setting.StringSetting str) {
					String v = str.get();
					return v == null || v.isBlank() ? null : v.trim();
				}
			}
			return null;
		}
	}

	private final List<Bindable> bindables = new ArrayList<>();
	private int listening = -1;        // 키 입력을 기다리는 bindables 인덱스(-1 = 없음)
	private final LunaConfirm keyConfirm = new LunaConfirm();   // 49-124차: 키 삭제 확인 창
	private int hoverKey = -1;         // 키보드 그림에서 마우스가 올라간 키
	private float listScroll;
	private String editNotice;
	private long editNoticeUntil;

	public LunaKeybindsScreen(Screen parent) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("키 지정"));
		this.parent = parent;
	}

	private void lunaInit0() {
		bindables.clear();
		// 49-245차(사용자: "키 지정에서 키바인드는 없애줘, 왜 있는 거야"): [키바인드] 페이지의 칸(내 키 24칸 + 마크 보조 키)은
		// 목록에서 뺀다 - 그건 키바인드 페이지에서 고친다. 지정된 키는 키보드 그림에는 그대로 보인다(겹침 확인용).
		List<Bindable> shown = new ArrayList<>();
		for (Module m : ModuleManager.get().all()) {
			for (Setting<?> s : m.getSettings()) {
				if (s instanceof KeybindSetting kb) {
					if (kb.isHidden() && !kb.isBound() && !"shortcuts".equals(m.getId())) {
						continue;   // 49-245차: 숨은 빈 칸(시점 고정의 안 쓰는 시점 등)
					}
					Bindable b = new Bindable(m, kb);
					if ("shortcuts".equals(m.getId())) {
						if (kb.isBound()) {
							shown.add(b);
						}
						continue;
					}
					bindables.add(b);
				}
			}
		}
		bindings.clear();
		bindings.computeIfAbsent(GLFW.GLFW_KEY_RIGHT_SHIFT, k -> new ArrayList<>()).add("Nova 메뉴 열기 (기본)");
		// 49-24차: 조합키는 실제 키(기본 키) 자리에 "Ctrl + …" 접두어로 표시. 겹침은 조합 전체가 같을 때만.
		Map<Integer, Integer> fullCount = new HashMap<>();
		shown.addAll(bindables);
		for (Bindable b : shown) {
			KeybindSetting kb = b.setting();
			if (!kb.isBound()) {
				continue;
			}
			int mods = kr.lunaslight.mod.util.LunaCompat.keyModifiers(kb.getKeyCode());
			String prefix = "";
			if (mods != 0) {
				String full = kb.getKeyName();
				int cut = full.lastIndexOf(" + ");
				prefix = cut > 0 ? full.substring(0, cut + 3) : "";
			}
			bindings.computeIfAbsent(kb.getBaseKey(), k -> new ArrayList<>()).add(prefix + b.label());
			fullCount.merge(kb.getKeyCode(), 1, Integer::sum);
		}
		conflictCount = 0;
		for (Integer n : fullCount.values()) {
			if (n > 1) {
				conflictCount++;
			}
		}
		vanilla.clear();
		try {
			for (kr.lunaslight.mod.util.LunaCompat.VanillaKey vk : kr.lunaslight.mod.util.LunaCompat.vanillaKeys(this.client)) {
				vanilla.computeIfAbsent(vk.code(), k -> new ArrayList<>()).add(vk.label());
			}
		} catch (Throwable ignored) {
		}
		overlapCount = 0;
		for (Integer code : bindings.keySet()) {
			if (vanilla.containsKey(code)) {
				overlapCount++;
			}
		}
		mouseLines.clear();
		for (Map.Entry<Integer, List<String>> en : bindings.entrySet()) {
			if (kr.lunaslight.mod.util.LunaCompat.isMouseKeyCode(en.getKey())) {
				mouseLines.add(kr.lunaslight.mod.util.LunaCompat.keyDisplayName(en.getKey()) + " = " + String.join(", ", en.getValue()));
			}
		}
	}

	// ==================== 배치 ====================

	private static final int GAP = 3;
	private static final int ROW_H = 18;       // 기능 줄 높이
	private static final int HEAD_H = 40;
	private static final int FOOT_H = 30;

	private float rowUnits() {
		float units = 0;
		for (KeyDef k : ROWS.get(1)) {
			units += k.units();
		}
		return units;
	}

	/**
	 * 오른쪽 키 지정 패널 폭. 49-217차(사용자 제보: 전체 화면 + 큰 GUI 배율에서 키보드 왼쪽과 목록 오른쪽이 화면 밖으로
	 * 잘림): 고정 236이던 것을 화면 폭의 1/3쯤(150~236)으로.
	 */
	private int sideW() {
		return Math.max(150, Math.min(236, (width - 16) * 34 / 100));
	}

	/**
	 * 키 하나(1u)의 폭 - 화면 폭에 맞춰 고른다. 49-217차: 아래 한계를 24에서 12로 낮췄다(24로 묶여 있어 GUI 배율 4 같은
	 * 좁은 화면에선 패널이 화면보다 넓어져 양옆이 잘렸다). 좁으면 키 글자를 줄여 쓴다(fitLabel).
	 */
	private int unit() {
		int avail = width - 16 - 36 - 12 - sideW();
		int gaps = (ROWS.get(1).size() - 1) * GAP;
		int u = (int) ((avail - gaps) / rowUnits());
		// 49-245차(사용자: "키캡 네모 모양으로"): 1칸 키를 정사각형으로 - 높이에 들어가는 크기와 폭에 들어가는 크기 중 작은 쪽
		int availH = height - 16 - HEAD_H - 6 - 10 - FOOT_H - (mouseLines.isEmpty() ? 0 : 14);
		int byH = (availH + GAP) / ROWS.size() - GAP;
		return Math.max(12, Math.min(30, Math.min(u, byH)));
	}

	/** 키 높이 = 1칸 키 폭(정사각형). */
	private int keyH() {
		return unit();
	}

	/** 키 칸에 들어가게 글자를 줄인다: 그대로 → F10이면 10 → 앞 글자만. */
	private String fitLabel(String label, int w) {
		if (LunaDraw.width(textRenderer, label) <= w - 4) {
			return label;
		}
		if (label.length() > 1 && label.charAt(0) == 'F' && Character.isDigit(label.charAt(1))) {
			String num = label.substring(1);
			if (LunaDraw.width(textRenderer, num) <= w - 2) {
				return num;
			}
		}
		for (int n = label.length() - 1; n >= 1; n--) {
			String cut = label.substring(0, n);
			if (LunaDraw.width(textRenderer, cut) <= w - 2) {
				return cut;
			}
		}
		return "";
	}

	private int kbWidth() {
		return Math.round(rowUnits() * unit() + (ROWS.get(1).size() - 1) * GAP);
	}

	private int panelW() {
		return 18 + kbWidth() + 18 + sideW() + 12;
	}

	private int kbHeight() {
		return ROWS.size() * (keyH() + GAP) - GAP;
	}

	private int panelH() {
		int h = HEAD_H + 6 + kbHeight() + 10 + FOOT_H + (mouseLines.isEmpty() ? 0 : 14);
		return Math.min(h, height - 16);
	}

	private int panelX() {
		return Math.max(4, (width - panelW()) / 2);
	}

	private int panelY() {
		return Math.max(4, (height - panelH()) / 2);
	}

	private int sideX() {
		return panelX() + 18 + kbWidth() + 18;
	}

	private int sideY() {
		return panelY() + HEAD_H + 6;
	}

	private int sideH() {
		return panelH() - HEAD_H - 6 - 10;
	}

	private int listTop() {
		return sideY() + 22;
	}

	private int listRows() {
		return Math.max(1, (sideH() - 22 - 6) / ROW_H);
	}

	// ==================== 그리기 ====================

	private void lunaRender0(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.applyAlpha(0x88000000));

		int pw = panelW();
		int ph = panelH();
		int px = panelX();
		int py = panelY();
		LunaDraw.panel3d(ctx, px, py, pw, ph, 10);   // 49-227차

		// 헤더: 뒤로 + 제목 + 요약
		boolean backHover = LunaDraw.in(mouseX, mouseY, px + 12, py + 10, 20, 20);
		LunaDraw.card3d(ctx, px + 12, py + 10, 20, 20, backHover ? 1f : 0f, false);   // 49-227차
		LunaIcons.draw(ctx, textRenderer, LunaIcons.BACK, px + 12 + (20 - LunaIcons.SIZE) / 2, LunaDraw.iconY(py + 10, 20), LunaDraw.TEXT);
		int ty = LunaDraw.textY(py + 10, 20);
		LunaIcons.draw(ctx, textRenderer, LunaIcons.KEYBOARD, px + 40, LunaDraw.iconBesideText(ty), LunaDraw.ACCENT);
		LunaDraw.text(ctx, textRenderer, "키 지정", px + 40 + LunaIcons.SIZE + 6, ty, LunaDraw.TEXT);
		String sub = bindings.size() + "개 키 사용 중";
		if (conflictCount > 0) {
			sub = "중복 " + conflictCount + " | " + sub;
		}
		LunaDraw.text(ctx, textRenderer, sub, px + pw - 12 - LunaDraw.width(textRenderer, sub), ty,
			conflictCount > 0 ? 0xFFFF8B82 : LunaDraw.TEXT_DIM);
		LunaDraw.fadeLine(ctx, px + 12, py + HEAD_H - 4, pw - 24, LunaDraw.ACCENT);

		// 키보드(왼쪽)
		hoverKey = -1;
		int ky = py + HEAD_H + 6;
		int unit = unit();
		List<String> hoverList = null;
		for (List<KeyDef> row : ROWS) {
			int kx = px + 18;
			for (KeyDef k : row) {
				int w = Math.round(k.units() * unit + (k.units() - 1) * GAP);
				if (k.code() >= 0) {
					List<String> bound = bindings.get(k.code());
					List<String> mc = vanilla.get(k.code());
					boolean has = bound != null && !bound.isEmpty();
					boolean hasMc = mc != null && !mc.isEmpty();
					boolean conflict = bound != null && bound.size() > 1;
					boolean overlap = has && hasMc;
					boolean hover = LunaDraw.in(mouseX, mouseY, kx, ky, w, keyH());
					if (hover) {
						hoverKey = k.code();
					}
					int bg = conflict ? 0xF03A1512 : overlap ? 0xF0342512 : has ? 0xF01C2118 : hasMc ? 0xF0131A22 : 0xF014161A;
					int border = conflict ? 0x99E05A50 : overlap ? 0x99F59E0B : has ? 0x66A9D973 : hasMc ? 0x4D60A5FA : 0x14FFFFFF;
					if (hover) {
						border = conflict ? 0xCCFF6B5E : overlap ? 0xCCF59E0B : has ? 0xCCA9D973 : hasMc ? 0x9960A5FA : 0x30FFFFFF;
					}
					int tone = conflict ? 0xFFE05A50 : overlap ? 0xFFF59E0B : has ? LunaDraw.ACCENT : hasMc ? 0xFF60A5FA : 0;
					if (LunaDraw.soft()) {
						// 49-245차(사용자: "반전 색상이 안 보여, 같은 색 계열로"): 크림에선 검은 키캡 대신 바탕과 같은 밝은 칸에 그 색을 옅게 섞는다
						bg = tone == 0 ? LunaDraw.CARD : LunaDraw.lerpColor(LunaDraw.CARD, tone, 0.20f);
						border = tone == 0 ? LunaDraw.CARD_BORDER : LunaDraw.lerpColor(LunaDraw.CARD, tone, hover ? 0.85f : 0.55f);
						if (tone == 0 && hover) {
							border = LunaDraw.lerpColor(LunaDraw.CARD_BORDER, LunaDraw.TEXT, 0.35f);
						}
					}
					if (listening >= 0 && hover) {
						border = LunaDraw.withAlpha(LunaDraw.ACCENT, 0xE6);
						bg = LunaDraw.lerpColor(bg, LunaDraw.ACCENT, 0.15f);
					}
					LunaDraw.roundRectBordered(ctx, kx, ky, w, keyH(), 2, bg, border);   // 49-245차: 각진 키캡
					int labelColor = conflict ? 0xFFFF9B93 : overlap ? 0xFFFFC46B : has ? 0xFFCDE8A8 : hasMc ? 0xFF9CC4EE : LunaDraw.TEXT_DIM;
					if (LunaDraw.soft()) {
						labelColor = tone == 0 ? LunaDraw.TEXT_SUB : LunaDraw.lerpColor(tone, 0xFF2A2118, 0.45f);
					}
					String label = fitLabel(k.label(), w);
					int lw = LunaDraw.width(textRenderer, label);
					LunaDraw.text(ctx, textRenderer, label, kx + (w - lw) / 2, LunaDraw.textY(ky, keyH()), labelColor);
					if (hover && (has || hasMc) && listening < 0) {
						hoverList = new ArrayList<>();
						if (has) {
							hoverList.addAll(bound);
						}
						if (hasMc) {
							for (String m : mc) {
								hoverList.add("마크 | " + m);
							}
						}
					}
				}
				kx += w + GAP;
			}
			ky += keyH() + GAP;
		}

		// 하단: 상태 한 줄 (+ 마우스 버튼 지정 목록)
		int fy = py + ph - 22 - (mouseLines.isEmpty() ? 0 : 14);
		int kbRight = px + 18 + kbWidth();
		LunaDraw.fadeLine(ctx, px + 18, fy - 8, kbRight - px - 18, 0xFFFFFFFF);
		if (!mouseLines.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, "마우스: " + String.join("  |  ", mouseLines), kbRight - px - 18),
				px + 18, fy + 14, LunaDraw.TEXT_SUB);
		}
		String foot;
		int footColor;
		if (listening >= 0) {
			foot = "설정할 키를 누르세요 (Esc 취소 | Backspace 해제)";
			footColor = LunaDraw.ACCENT;
		} else if (conflictCount > 0) {
			foot = "빨강 = 같은 키에 둘 이상 | 주황 = 마크 키와 겹침 | 파랑 = 마크 키";
			footColor = 0xFFFF8B82;
		} else {
			foot = "강조색 = Nova 키 | 파랑 = 마크 키 | 주황 = 겹침";
			footColor = LunaDraw.TEXT_DIM;
		}
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, foot, kbRight - px - 18), px + 18, fy, footColor);

		renderSide(ctx, mouseX, mouseY);

		// 호버 툴팁(맨 위에)
		if (hoverList != null) {
			int tw = 0;
			for (String s : hoverList) {
				tw = Math.max(tw, LunaDraw.width(textRenderer, s));
			}
			int th = hoverList.size() * 12 + 10;
			int tx = Math.min(mouseX + 10, width - tw - 18);
			int tty = Math.min(mouseY + 10, height - th - 6);
			LunaDraw.panel3d(ctx, tx, tty, tw + 14, th, 7);   // 49-227차: 사진 시안 판
			int yy = tty + 5;
			for (String s : hoverList) {
				LunaDraw.text(ctx, textRenderer, s, tx + 7, yy, LunaDraw.TEXT);
				yy += 12;
			}
		}
		if (editNotice != null && System.currentTimeMillis() < editNoticeUntil) {
			int nw = LunaDraw.width(textRenderer, editNotice) + 16;
			int nx = sideX() + (sideW() - nw) / 2;
			int ny = py + ph + 6;
			LunaDraw.roundRectBordered(ctx, nx, ny, nw, 16, 4, LunaDraw.soft() ? LunaDraw.CARD : 0xF00E1116, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x6B));
			LunaDraw.textCentered(ctx, textRenderer, editNotice, nx + nw / 2, LunaDraw.textY(ny, 16), LunaDraw.ACCENT);
		}
	}

	/** 오른쪽: 항상 떠 있는 키 지정 패널 - 기능 목록 + 현재 키 칩. */
	private void renderSide(DrawContext ctx, int mouseX, int mouseY) {
		int sx = sideX();
		int sy = sideY();
		int sh = sideH();
		LunaDraw.card3d(ctx, sx, sy, sideW(), sh, 6, 0, 0);   // 49-227차
		int hty = LunaDraw.textY(sy, 22);
		LunaDraw.text(ctx, textRenderer, "키 지정", sx + 10, hty, LunaDraw.TEXT);
		String cnt = bindables.size() + "개";
		LunaDraw.text(ctx, textRenderer, cnt, sx + sideW() - 10 - LunaDraw.width(textRenderer, cnt), hty, LunaDraw.TEXT_DIM);
		LunaDraw.fadeLine(ctx, sx + 10, sy + 21, sideW() - 20, LunaDraw.ACCENT);

		int rows = listRows();
		int top = listTop();
		listScroll = Math.max(0, Math.min(Math.max(0, bindables.size() - rows), listScroll));
		int first = (int) listScroll;
		lunaV.scissor(ctx, sx, top, sx + sideW(), top + rows * ROW_H);
		for (int i = first; i < Math.min(first + rows, bindables.size()); i++) {
			Bindable b = bindables.get(i);
			int ry = top + (i - first) * ROW_H;
			boolean hov = LunaDraw.in(mouseX, mouseY, sx + 4, ry, sideW() - 8, ROW_H);
			boolean isListening = listening == i;
			boolean onHoverKey = hoverKey >= 0 && b.setting().isBound() && b.setting().getBaseKey() == hoverKey;
			if (isListening) {
				LunaDraw.roundRect(ctx, sx + 4, ry, sideW() - 8, ROW_H - 1, 3, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x33));
			} else if (onHoverKey) {
				LunaDraw.roundRect(ctx, sx + 4, ry, sideW() - 8, ROW_H - 1, 3, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x1F));
			} else if (hov) {
				LunaDraw.roundRect(ctx, sx + 4, ry, sideW() - 8, ROW_H - 1, 3, LunaDraw.soft() ? 0x14000000 : 0x14FFFFFF);
			}
			String cur = b.setting().isBound() ? b.setting().getKeyName() : "";
			int chipW = cur.isEmpty() ? 0 : LunaDraw.width(textRenderer, cur) + 10;
			int rty = LunaDraw.textY(ry, ROW_H - 1);
			int nameW = sideW() - 20 - (isListening ? LunaDraw.width(textRenderer, "키를 누르세요") + 6 : chipW + (cur.isEmpty() ? 0 : 4));
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, b.label(), nameW), sx + 10, rty,
				isListening ? LunaDraw.ACCENT : (hov || onHoverKey ? LunaDraw.TEXT : LunaDraw.TEXT_SUB));
			if (isListening) {
				String w8 = "키를 누르세요";
				LunaDraw.text(ctx, textRenderer, w8, sx + sideW() - 10 - LunaDraw.width(textRenderer, w8), rty, LunaDraw.ACCENT);
			} else if (!cur.isEmpty()) {
				int cx = sx + sideW() - 10 - chipW;
				boolean chipHover = LunaDraw.in(mouseX, mouseY, cx, ry + 2, chipW, ROW_H - 5);
				boolean lt = LunaDraw.soft();
				LunaDraw.roundRectBordered(ctx, cx, ry + 2, chipW, ROW_H - 5, 2,
					lt ? LunaDraw.lerpColor(LunaDraw.CARD, chipHover ? 0xFFE05A50 : LunaDraw.ACCENT, 0.18f) : chipHover ? 0xF02A1413 : 0xF0242830,
					lt ? LunaDraw.lerpColor(LunaDraw.CARD, chipHover ? 0xFFE05A50 : LunaDraw.ACCENT, 0.6f) : chipHover ? 0x99D9655B : 0x66FFFFFF);
				LunaDraw.text(ctx, textRenderer, chipHover ? "✕" : cur,
					chipHover ? cx + (chipW - LunaDraw.width(textRenderer, "✕")) / 2 : cx + 5,
					LunaDraw.textY(ry + 2, ROW_H - 5), LunaDraw.soft()
						? LunaDraw.lerpColor(chipHover ? 0xFFE05A50 : LunaDraw.ACCENT, 0xFF2A2118, 0.45f)
						: chipHover ? 0xFFFF9B93 : 0xFFCDE8A8);
			} else if (hov) {
				String add = "지정";
				LunaDraw.text(ctx, textRenderer, add, sx + sideW() - 10 - LunaDraw.width(textRenderer, add), rty, LunaDraw.TEXT_DIM);
			}
		}
		ctx.disableScissor();
		if (bindables.size() > rows) {
			int trackX = sx + sideW() - 4;
			int th = Math.max(10, Math.round(rows * ROW_H * (rows / (float) bindables.size())));
			int tty = top + Math.round((rows * ROW_H - th) * (listScroll / Math.max(1, bindables.size() - rows)));
			LunaDraw.roundRect(ctx, trackX, tty, 2, th, 1, 0x38FFFFFF);
		}
		keyConfirm.render(ctx, textRenderer, width, height, mouseX, mouseY);
	}

	private void editNotice(String s) {
		editNotice = s;
		editNoticeUntil = System.currentTimeMillis() + 2500;
	}

	// ==================== 입력 ====================

	/** 키보드 그림에서 눌린 키(없으면 -1). */
	private int keyAt(double mouseX, double mouseY) {
		int px = panelX();
		int ky = panelY() + HEAD_H + 6;
		int unit = unit();
		for (List<KeyDef> row : ROWS) {
			int kx = px + 18;
			for (KeyDef k : row) {
				int w = Math.round(k.units() * unit + (k.units() - 1) * GAP);
				if (k.code() >= 0 && LunaDraw.in(mouseX, mouseY, kx, ky, w, keyH())) {
					return k.code();
				}
				kx += w + GAP;
			}
			ky += keyH() + GAP;
		}
		return -1;
	}

	private void assign(int index, int keyCodeWithMods) {
		if (index < 0 || index >= bindables.size()) {
			return;
		}
		Bindable b = bindables.get(index);
		// 49-125차: 서버에서는 공격/사용의 보조 키를 키보드로 못 둔다 - 안내만 띄우고 그대로 둔다.
		String blocked = kr.lunaslight.mod.util.AltKeys.blockedReason(b.setting().getId(), keyCodeWithMods);
		if (blocked != null) {
			listening = -1;
			editNotice(blocked);
			return;
		}
		b.setting().setValue(keyCodeWithMods);
		kr.lunaslight.mod.config.LunaClientConfig.save();
		listening = -1;
		init();
		editNotice(keyCodeWithMods < 0 ? b.setting().getDisplayName() + " 해제" : b.setting().getDisplayName() + " = " + b.setting().getKeyName());
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		if (LunaDraw.in(mouseX, mouseY, sideX(), sideY(), sideW(), sideH())) {
			listScroll = Math.max(0, Math.min(Math.max(0, bindables.size() - listRows()), listScroll - (float) verticalAmount));
			return true;
		}
		return false;
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (keyConfirm.isOpen()) {
			keyConfirm.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		int pw = panelW();
		int ph = panelH();
		int px = panelX();
		int py = panelY();
		if (LunaDraw.in(mouseX, mouseY, px + 12, py + 10, 20, 20) || !LunaDraw.in(mouseX, mouseY, px, py, pw, ph)) {
			if (listening >= 0) {
				listening = -1;
				return true;
			}
			goBack();
			return true;
		}
		// 오른쪽 목록
		int sx = sideX();
		int top = listTop();
		int rows = listRows();
		if (LunaDraw.in(mouseX, mouseY, sx, top, sideW(), rows * ROW_H)) {
			int i = (int) listScroll + (int) ((mouseY - top) / ROW_H);
			if (i >= 0 && i < bindables.size()) {
				Bindable b = bindables.get(i);
				String cur = b.setting().isBound() ? b.setting().getKeyName() : "";
				int chipW = cur.isEmpty() ? 0 : LunaDraw.width(textRenderer, cur) + 10;
				int ry = top + (i - (int) listScroll) * ROW_H;
				boolean onChip = !cur.isEmpty() && LunaDraw.in(mouseX, mouseY, sx + sideW() - 10 - chipW, ry + 2, chipW, ROW_H - 5);
				if (onChip || button == 1) {
					if (b.setting().isBound()) {
						final int idx = i;
						keyConfirm.show(LunaIcons.KEYBOARD, "이 키를 삭제할까요?",
							b.setting().getDisplayName() + " (" + b.setting().getKeyName() + ")", "삭제", () -> assign(idx, -1));
					}
				} else {
					listening = listening == i ? -1 : i;
				}
			}
			return true;
		}
		// 키보드 그림
		int code = keyAt(mouseX, mouseY);
		if (code >= 0) {
			if (listening >= 0) {
				assign(listening, code);   // 49-124차: 조합키 제거 - 누른 키 그대로 지정
			} else {
				// 그 키에 지정된 첫 기능으로 목록 스크롤(어디 있는지 바로 보이게)
				for (int i = 0; i < bindables.size(); i++) {
					Bindable b = bindables.get(i);
					if (b.setting().isBound() && b.setting().getBaseKey() == code) {
						listScroll = Math.max(0, Math.min(Math.max(0, bindables.size() - listRows()), i));
						break;
					}
				}
			}
			return true;
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyConfirm.isOpen()) {
			keyConfirm.keyPressed(keyCode);
			return true;
		}
		if (listening >= 0) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				listening = -1;
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
				final int idx = listening;
				Bindable bd = bindables.get(idx);
				if (bd.setting().isBound()) {
					keyConfirm.show(LunaIcons.KEYBOARD, "이 키를 삭제할까요?",
						bd.setting().getDisplayName() + " (" + bd.setting().getKeyName() + ")", "삭제", () -> assign(idx, -1));
				} else {
					listening = -1;
				}
				return true;
			}
			assign(listening, keyCode);   // 49-124차: 조합키 제거 - 누른 키 그대로 지정
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			goBack();
			return true;
		}
		return false;
	}

	private void goBack() {
		kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
	}

	@Override
	public void close() {
		goBack();
	}
}
