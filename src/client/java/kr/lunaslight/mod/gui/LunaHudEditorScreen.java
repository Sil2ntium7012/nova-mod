package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.config.LunaClientConfig;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 47차: HUD 편집기. 게임 화면을 그대로 보면서 켜져 있는 HUD 요소(위치 설정이 있는 모든 모듈)를
 * 마우스로 잡아 끌어 원하는 자리에 놓는 화면.
 *
 * 동작 원리: HUD 모듈들은 매 프레임 HudPosition#resolveX/Y로 자기 자리를 계산하는데, 47차부터
 * 거기서 "실제로 그려진 영역"을 기억해 둠. 이 화면은 그 영역 위에 둥근 박스를 덧그리고, 드래그하면
 * HudPosition#moveTo로 offset을 바꿈 → 모듈은 다음 프레임에 새 자리에 그려짐(모듈 코드 무수정).
 * 놓으면 reanchor로 가장 가까운 모서리에 붙여서 해상도가 바뀌어도 자리가 유지됨.
 *
 * 49-22차(사용자 4건):
 *  ① 목록 패널을 머리띠를 잡고 옮길 수 있고, 꺾쇠로 접었다 펼 수 있음(위치/접힘은 화면이 살아 있는 동안 기억).
 *  ② 드래그 "드드득": 요소 폭이 매 프레임 바뀌는 HUD(좌표/시계 등)를 잡으면 오른쪽·가운데 앵커 기준
 *     offset이 폭 변화만큼 흔들렸고, 요소 목록을 드래그 이벤트마다 다시 만들면서 크기가 튀었음.
 *     이제 잡는 순간의 크기/앵커를 고정해 두고 드래그 내내 그 값으로만 옮김 + 목록은 프레임당 1회 계산.
 *  ③ 요소끼리 자동 정렬: 다른 요소의 왼쪽/오른쪽/가운데/위/아래 선에 4px 안으로 오면 붙고 안내선을 그림.
 *  ④ 겹침 경고는 기본 꺼짐(패널 안 스위치로 켤 수 있음).
 *
 *  - 좌클릭 드래그: 이동   - 우클릭: 그 요소 기본 위치로   - 화면 가운데/다른 요소 선: 자동 스냅
 *  - ESC / 바깥 클릭: 저장하고 설정 화면으로 복귀
 */
public class LunaHudEditorScreen extends LunaScreenBase {
	private static final int BOX_PAD = 3;
	private static final int LIST_W = 136;
	/** 49-157차: 키 안내 바가 펼쳐져 있는지(바 위에 마우스가 있는 동안 계속 열어 두려고). */
	private boolean helpShown;
	private static final int LIST_ROW_H = 16;
	private static final int LIST_HEAD_H = 22;
	private static final int SNAP = 5;
	private static final int ALIGN_SNAP = 4;

	private final Screen parent;
	private Module selected;

	// 드래그 상태(잡는 순간 고정)
	private Module dragging;
	private PositionSetting dragSetting;
	private int dragW, dragH;
	private double grabDx, grabDy; // 49-24차: 소수 좌표(부드러운 드래그)

	// 49-24차: 우클릭 메뉴
	private Module menuModule;
	private int menuX, menuY;
	private static final int MENU_W = 96;
	private static final int MENU_ROW = 16;
	private static final String[] MENU_ITEMS = {"설정 열기", "끄기", "기본 위치", "크기 초기화"};
	private int guideX = Integer.MIN_VALUE, guideY = Integer.MIN_VALUE; // 이번 프레임 정렬 안내선
	/** 49-32차: 뭔가 옮겼는지 / ESC 저장 확인 창이 떠 있는지. */
	private boolean movedAnything;
	private boolean askSave;
	/** 49-122차(사용자: "취소 누르면 저장 안 하고 나가져야지"): 편집기를 열 때의 위치 스냅샷 - [취소]가 여기로 되돌린다. */
	private final java.util.Map<PositionSetting, HudPosition> snapshot = new java.util.HashMap<>();

	// 목록 패널(49-22차: 옮기기/접기)
	private static int panelX = -1, panelY = 30;
	private static boolean panelCollapsed;
	private boolean headPressed;
	private boolean headPressMoved;
	private static boolean overlapWarning;   // 겹침 경고(기본 꺼짐)
	/**
	 * 49-69차(4-28 · 4-45): <b>바닐라가 쓰는 자리</b> 보여 주기(기본 <b>켜짐</b>).
	 *
	 * <p>"키스트로크 기본 위치가 이상하다"(4-28)와 "CPS가 채팅에 가려진다"(4-45)는 둘 다 같은 문제다 -
	 * 편집기에는 <b>Luna 요소끼리의 겹침</b>만 보이고, 정작 <b>채팅과 핫바가 어디까지 차지하는지</b>는
	 * 안 보인다. 그래서 비어 보이는 자리에 놨다가 게임에 들어가면 가려진다.
	 * 이제 그 두 자리를 흐린 테두리로 그려 준다 - 피해서 놓으면 된다.
	 */
	private static boolean vanillaGuide = true;
	private boolean panelDragging;
	private int panelGrabDx, panelGrabDy;
	private double listScroll;
	private int listMaxScroll;

	// 프레임 캐시(드래그 이벤트마다 목록을 다시 만들지 않게)
	private List<Module> positionableCache;
	private List<Element> elementsCache;
	private long cacheFrame = -1;
	private long frameSerial;

	// 49-34차(사용자: "HUD 편집기에서 크기 조절도 되게 해줘 끝에 땡기면"): 선택 요소의 오른쪽 아래 모서리 손잡이를
	// 끌면 scale(0.5~2.0)이 바뀐다. 잡는 순간의 왼쪽 위 좌표와 "배율 1일 때 폭"을 고정해 두고, 마우스와의
	// 가로 거리 비율로 새 배율을 만든다(휠 조절과 같은 필드를 건드림).
	// 49-187차(사용자: "크기 조절하는 거 어디 갔어 - 오른쪽 아래로 끄는 게 아니라 한쪽 모서리 땡기면 그냥 커지게"):
	// 손잡이를 네 모서리 모두에 두고(선택했거나 마우스를 올린 요소), 잡은 모서리의 <b>반대쪽 모서리를 고정</b>한 채
	// 끄는 만큼 커지고 작아진다(보통 창 크기 조절과 같은 느낌). 배율 0.5~4.
	private static final int HANDLE = 8;
	private static final float SCALE_MIN = 0.5f, SCALE_MAX = 4f;
	private Module resizing;
	private PositionSetting resizeSetting;
	private int resizeOriginX, resizeOriginY;   // 고정된(반대쪽) 모서리의 화면 좌표
	private boolean resizeLeft, resizeTop;      // 잡은 모서리가 왼쪽/위쪽인지
	private float resizeUnitW, resizeUnitH;   // 배율 1일 때의 폭/높이

	private record Element(Module module, PositionSetting setting, int x, int y, int w, int h) {
	}

	public LunaHudEditorScreen(Screen parent, Module highlight) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("Nova HUD Editor"));
		this.parent = parent;
		this.selected = highlight;
		// 49-122차: 연 순간의 위치를 복사해 둔다([취소]로 되돌리기 위해).
		for (Module m : positionable()) {
			PositionSetting ps = positionOf(m);
			if (ps != null && ps.get() != null) {
				snapshot.put(ps, ps.get().copy());
			}
		}
	}

	@Override
	protected void init() {
		if (panelX < 0 || panelX + LIST_W > width) {
			panelX = width - LIST_W - 8;
		}
		panelY = Math.max(0, Math.min(height - LIST_HEAD_H, panelY));
	}

	@Override
	public void close() {
		kr.lunaslight.mod.util.HudEditorState.active = false;
		LunaClientConfig.save();
		if (this.client != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
		}
	}

	/**
	 * 49-122차(사용자: "취소 누르면 저장 안 하고 나가져야지"): 이번에 옮긴 것을 열 때 스냅샷으로 <b>되돌리고</b>
	 * 저장 없이 나간다(close()와 달리 LunaClientConfig.save()를 부르지 않는다 - removed()도 저장하지 않는다).
	 */
	private void discardAndClose() {
		for (java.util.Map.Entry<PositionSetting, HudPosition> e : snapshot.entrySet()) {
			if (e.getKey() != null && e.getValue() != null) {
				e.getKey().setValue(e.getValue().copy());
			}
		}
		kr.lunaslight.mod.util.HudEditorState.active = false;
		if (this.client != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
		}
	}

	@Override
	public void removed() {
		kr.lunaslight.mod.util.HudEditorState.active = false;
		super.removed();
	}

	// ---- 데이터 ----

	static PositionSetting positionOf(Module module) {
		for (Setting<?> s : module.getSettings()) {
			if (s instanceof PositionSetting p) {
				return p;
			}
		}
		return null;
	}

	private List<Module> positionable() {
		if (positionableCache != null && cacheFrame == frameSerial) {
			return positionableCache;
		}
		List<Module> list = new ArrayList<>();
		for (Module m : ModuleManager.get().all()) {
			if (positionOf(m) != null) {
				list.add(m);
			}
		}
		// HUD 카테고리 먼저, 그다음 이름순
		list.sort((a, b) -> {
			boolean ah = a.getCategory() == ModuleCategory.HUD;
			boolean bh = b.getCategory() == ModuleCategory.HUD;
			if (ah != bh) {
				return ah ? -1 : 1;
			}
			return a.getDisplayName().compareTo(b.getDisplayName());
		});
		positionableCache = list;
		return list;
	}

	/** 지금 화면에 표시 중(켜짐)인 요소들의 박스(프레임당 1회 계산). */
	private List<Element> elements() {
		if (elementsCache != null && cacheFrame == frameSerial) {
			return elementsCache;
		}
		List<Element> out = new ArrayList<>();
		for (Module m : positionable()) {
			if (!m.isEnabled() || !m.isVersionSupported()) {
				continue;
			}
			PositionSetting ps = positionOf(m);
			HudPosition p = ps.get();
			int x, y, w, h;
			if (p.hasRecentBounds()) {
				x = p.getLastX();
				y = p.getLastY();
				w = p.getLastWidth();
				h = p.getLastHeight();
			} else {
				// 아직 안 그려진(조건부 표시) 요소는 이름 크기만큼의 가상 박스
				w = LunaDraw.width(textRenderer, m.getDisplayName()) + 8;
				h = textRenderer.fontHeight + 2;
				x = p.resolveX(width, w);
				y = p.resolveY(height, h);
			}
			out.add(new Element(m, ps, x, y, w, h));
		}
		elementsCache = out;
		cacheFrame = frameSerial;
		return out;
	}

	private void invalidateCaches() {
		elementsCache = null;
		positionableCache = null;
	}

	private int listX() {
		return panelX;
	}

	private int listY() {
		return panelY;
	}

	private int listH() {
		if (panelCollapsed) {
			return LIST_HEAD_H;
		}
		// 49-32차(사용자: "너무 위아래로 길어 - 절반으로 고정하고 휠로 내리게"):
		// 목록 칸은 화면 높이의 절반을 넘지 않는다(넘치는 만큼은 휠로 스크롤).
		int natural = LIST_HEAD_H + 6 + positionable().size() * LIST_ROW_H + 6 + 26 + 40;
		return Math.min(Math.min(height - panelY - 8, height / 2), natural);
	}

	/**
	 * 49-32차(사용자: "진짜 마크 화면처럼 보여야지 핫바는 흐리게 하지 말고"):
	 * 이 화면에서는 게임 화면을 어둡게/흐리게 하지 않는다. 버전마다 시그니처가 달라(1.20.1은 1인자,
	 * 1.20.2+는 4인자) @Override 없이 둘 다 정의해 둔다 - 그 버전에 있는 쪽이 실제로 불린다.
	 */
	public void renderBackground(DrawContext ctx) {
	}

	public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
	}

	// ---- 렌더 ----

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		frameSerial++;
		invalidateCaches();

		// 48-3차: 요소를 "실물 그대로" 보여주면서 편집 - 편집기가 켜진 모듈의 HUD를 직접 그림(ModuleManager는 이때 쉼).
		// 49-24차: 실제 상태에서 아무것도 안 그리는 모듈(조준 대상/효과/획득 아이템 없음 등)은 **예시 데이터로 실물 크기**
		// 샘플을 그 자리에 그리고, 드래그 중인 요소는 소수 좌표만큼 살짝 옮겨 그려 1칸 단위 '드드득'을 없앰.
		kr.lunaslight.mod.util.HudEditorState.active = true;
		for (Module m : positionable()) {
			if (!m.isEnabled() || !m.isVersionSupported()) {
				continue;
			}
			PositionSetting ps = positionOf(m);
			boolean drag = m == dragging && dragSetting != null;
			float fx = 0f, fy = 0f;
			if (drag) {
				HudPosition p = dragSetting.get();
				fx = p.fracX(width, dragW);
				fy = p.fracY(height, dragH);
				kr.lunaslight.mod.util.LunaCompat.guiPush(ctx);
				kr.lunaslight.mod.util.LunaCompat.guiTranslate(ctx, fx, fy);
			}
			try {
				long before = System.nanoTime();
				try {
					m.renderHud(ctx, null);
				} catch (Throwable ignored) {
				}
				if (ps != null && ps.get().getLastResolveNanos() < before) {
					m.renderEditorSample(ctx, width, height);
				}
			} finally {
				if (drag) {
					kr.lunaslight.mod.util.LunaCompat.guiPop(ctx);
				}
			}
		}

		if (vanillaGuide) {
			drawVanillaAreas(ctx);
		}

		// 스냅 안내선은 드래그 중에만
		if (dragging != null) {
			// 49-34차: 가운데 안내선은 바닐라 십자의 가운데 픽셀에(짝수 폭에서 w/2는 1px 어긋남)
			int ccx = kr.lunaslight.mod.util.LunaCompat.crosshairCenterX(client);
			int ccy = kr.lunaslight.mod.util.LunaCompat.crosshairCenterY(client);
			ctx.fill(ccx, 0, ccx + 1, height, 0x3CFFFFFF);
			ctx.fill(0, ccy, width, ccy + 1, 0x3CFFFFFF);
			if (guideX != Integer.MIN_VALUE) {
				ctx.fill(guideX, 0, guideX + 1, height, LunaDraw.withAlpha(LunaDraw.ACCENT, 0xA0));
			}
			if (guideY != Integer.MIN_VALUE) {
				ctx.fill(0, guideY, width, guideY + 1, LunaDraw.withAlpha(LunaDraw.ACCENT, 0xA0));
			}
		}

		List<Element> all = elements();
		Element hover = elementAt(mouseX, mouseY);
		// 49-9차: 겹침 감지 - 49-22차: 기본 꺼짐(패널 스위치)
		java.util.Set<Module> overlapping = new java.util.HashSet<>();
		if (overlapWarning) {
			for (int i = 0; i < all.size(); i++) {
				Element a = all.get(i);
				for (int j = i + 1; j < all.size(); j++) {
					Element b = all.get(j);
					if (a.x() < b.x() + b.w() && b.x() < a.x() + a.w()
							&& a.y() < b.y() + b.h() && b.y() < a.y() + a.h()) {
						overlapping.add(a.module());
						overlapping.add(b.module());
					}
				}
			}
		}
		for (Element e : all) {
			boolean isSel = e.module() == selected;
			boolean isHover = (hover != null && hover.module() == e.module()) || e.module() == dragging;
			boolean isOverlap = overlapping.contains(e.module());
			int bx = e.x() - BOX_PAD;
			int by = e.y() - BOX_PAD;
			int bw = e.w() + BOX_PAD * 2;
			int bh = e.h() + BOX_PAD * 2;
			float hov = LunaDraw.anim("edh:" + e.module().getId(), isSel || isHover ? 1f : 0f, 18f);
			int line = isOverlap
				? LunaDraw.lerpColor(0x8CFF6B5E, 0xFFFF8B7E, hov)
				: LunaDraw.lerpColor(0x46FFFFFF, LunaDraw.ACCENT, hov);
			LunaDraw.roundRectOutline(ctx, bx, by, bw, bh, 3, line);
			if ((isSel || isHover || resizing == e.module()) && dragging == null) {
				// 49-187차: 네 모서리 크기 조절 손잡이
				for (int c = 0; c < 4; c++) {
					int hx = (c % 2 == 0 ? bx : bx + bw) - HANDLE / 2;
					int hy = (c < 2 ? by : by + bh) - HANDLE / 2;
					boolean hh = LunaDraw.in(mouseX, mouseY, hx - 3, hy - 3, HANDLE + 6, HANDLE + 6) || resizing == e.module();
					// 49-232차(사용자: "꼭지점 동그라미가 너무 이상해, 부드럽게"): 네모 칸 + 테두리 대신 매끈한 작은 점
					// (그림자 한 겹 + 밝은 점, 올리면 조금 커지며 테마색). 잡는 자리(HANDLE + 6)는 그대로.
					float ha = LunaDraw.anim("edc:" + e.module().getId() + c, hh ? 1f : 0f, 18f);
					int d = 6 + Math.round(2f * ha);
					int cx = hx + HANDLE / 2;
					int cy = hy + HANDLE / 2;
					LunaDraw.circle(ctx, cx - d / 2 - 1, cy - d / 2, d + 2, 0x55000000);
					LunaDraw.circle(ctx, cx - d / 2, cy - d / 2, d, LunaDraw.lerpColor(0xFFE9ECEF, LunaDraw.ACCENT, ha));
				}
			}
			if (e.module() == dragging) {
				// 잡고 있는 요소는 은은한 글로우(바깥 테두리 한 겹 더)
				LunaDraw.roundRectOutline(ctx, bx - 2, by - 2, bw + 4, bh + 4, 4, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x50));
			}
			// 이름표는 마우스를 올리거나 잡고 있을 때만(요소 자체가 잘 보이게)
			if (hov > 0.5f) {
				String name = e.module().getDisplayName();
				int nw = LunaDraw.width(textRenderer, name) + 12;
				int nh = 14;
				int ny = by - nh - 3 >= 0 ? by - nh - 3 : by + bh + 3;
				int nx = Math.max(0, Math.min(width - nw, bx + (bw - nw) / 2));
				LunaDraw.roundRect(ctx, nx, ny, nw, nh, 4, 0xE60D0F12);
				// 49-78차(사용자: "막대기 너무 많다"): 왼쪽 강조 바 삭제. 글자는 세로 중앙
				LunaDraw.text(ctx, textRenderer, name, nx + 6, LunaDraw.textY(ny, nh), LunaDraw.TEXT);
			}
		}

		// 안내 바 - 49-41차(사용자: "글=글 점 식이라 조잡, 중앙도 안 맞음"): 키는 키캡 칩, 뜻은 옆에 옅은 글자,
		// 쌍 사이는 넉넉히. 글자·칩 전부 세로 중앙(LunaDraw.textY). 겹침 경고가 있으면 경고 한 줄로 교체.
		int hy = 6;
		int barH = 20;
		if (!overlapping.isEmpty()) {
			String warn = "⚠ " + overlapping.size() + "개 요소가 겹쳐 있습니다 - 빨간 상자를 옮기거나 '전체 기본 위치' 사용";
			int hw = LunaDraw.width(textRenderer, warn) + 24;
			int hx = (width - hw) / 2;
			LunaDraw.roundRect(ctx, hx, hy, hw, barH, 4, 0xE0301410);
			LunaDraw.text(ctx, textRenderer, warn, hx + 12, LunaDraw.textY(hy, barH), 0xFFFFB4AC);
		} else {
			// 49-47차(사용자: "키 가이드 너무 가려"): 평소엔 위 가운데 작은 [?] 칩 하나만 두고,
			// 마우스를 올리면 그때 키 안내 바가 펼쳐진다. 끌고 있는 동안은 칩도 숨긴다 - 이제
			// 화면 위쪽 HUD 요소를 가리는 게 없다(따로 켜고 끄는 설정도 필요 없음).
			// 49-157차(사용자: "HUD 설정에 키 가이드 오른쪽 아래로"): [?] 칩과 펼친 안내 바를 화면 오른쪽 아래로.
			// 바는 오른쪽 끝에 붙어 왼쪽으로 펼쳐지고, 마우스가 칩이나 펼친 바 위에 있는 동안 열려 있다.
			String[][] keys = {{"드래그", "이동"}, {"모서리", "크기"}, {"휠", "크기"}, {"우클릭", "메뉴"}, {"방향키", "미세 이동"}, {"ESC", "나가기"}};
			int capPad = 5, gapIn = 5, gapOut = 12;
			int total = 0;
			for (String[] k : keys) {
				total += LunaDraw.width(textRenderer, k[0]) + capPad * 2 + gapIn + LunaDraw.width(textRenderer, k[1]) + gapOut;
			}
			total -= gapOut;
			int hw = total + 24;
			int chipW = 20;
			int gy = height - barH - 6;
			int chipX = width - chipW - 6;
			int barX = width - hw - 6;
			boolean overChip = LunaDraw.in(mouseX, mouseY, chipX - 6, gy - 4, chipW + 12, barH + 10);
			boolean overBar = helpShown && LunaDraw.in(mouseX, mouseY, barX, gy - 2, hw, barH + 4);
			boolean helpOpen = dragging == null && (overChip || overBar);
			helpShown = helpOpen;
			if (dragging != null) {
				// 끌고 있을 땐 아무것도 안 그림
			} else if (!helpOpen) {
				LunaDraw.roundRectBordered(ctx, chipX, gy, chipW, barH, 4, 0x990C120E, 0x26FFFFFF);
				String q = "?";
				LunaDraw.text(ctx, textRenderer, q, chipX + (chipW - LunaDraw.width(textRenderer, q)) / 2,
					LunaDraw.textY(gy, barH), LunaDraw.TEXT_SUB);
			} else {
				int hx = barX;
				LunaDraw.roundRect(ctx, hx, gy, hw, barH, 4, 0xD90C120E);
				int x = hx + 12;
				int capH = 14;
				int capY = gy + (barH - capH) / 2;
				for (String[] k : keys) {
					int kw = LunaDraw.width(textRenderer, k[0]) + capPad * 2;
					LunaDraw.roundRectBordered(ctx, x, capY, kw, capH, 3, 0x2EFFFFFF, 0x30FFFFFF);
					LunaDraw.text(ctx, textRenderer, k[0], x + capPad, LunaDraw.textY(capY, capH), LunaDraw.TEXT);
					x += kw + gapIn;
					LunaDraw.text(ctx, textRenderer, k[1], x, LunaDraw.textY(gy, barH), LunaDraw.TEXT_SUB);
					x += LunaDraw.width(textRenderer, k[1]) + gapOut;
				}
			}
		}

		renderList(ctx, mouseX, mouseY);

		if (askSave) {
			renderSaveAsk(ctx, mouseX, mouseY);
		}

		if (dragging != null && dragSetting != null) {
			HudPosition p = dragSetting.get();
			String pos = LunaClientScreen.anchorLabel(p.anchor) + "  " + Math.round(p.offsetX) + ", " + Math.round(p.offsetY)
				+ (Math.abs(p.scale - 1f) > 0.001f ? "  ×" + trimScale(p.scale) : "");
			int pw = LunaDraw.width(textRenderer, pos) + 12;
			int pxx = Math.min(mouseX + 10, width - pw);
			LunaDraw.roundRect(ctx, pxx, mouseY + 12, pw, 12, 4, 0xD0000000);
			LunaDraw.text(ctx, textRenderer, pos, pxx + 6, mouseY + 14, LunaDraw.TEXT);
		}
		// 49-24차: 휠로 크기 조절 중 표시(선택 요소 위)
		Element selEl = selected != null ? find(selected) : null;
		if (selEl != null && dragging == null && Math.abs(selEl.setting().get().scale - 1f) > 0.001f) {
			String sc = "×" + trimScale(selEl.setting().get().scale);
			int sw2 = LunaDraw.width(textRenderer, sc) + 8;
			LunaDraw.roundRect(ctx, selEl.x() + selEl.w() + BOX_PAD - sw2, selEl.y() - BOX_PAD - 12, sw2, 11, 3, 0xD0000000);
			LunaDraw.text(ctx, textRenderer, sc, selEl.x() + selEl.w() + BOX_PAD - sw2 + 4, selEl.y() - BOX_PAD - 10, LunaDraw.ACCENT);
		}
		renderMenu(ctx, mouseX, mouseY);
	}

	private static String trimScale(float s) {
		String t = String.format(java.util.Locale.ROOT, "%.2f", s);
		if (t.endsWith("0")) {
			t = t.substring(0, t.length() - 1);
		}
		return t;
	}

	/** 49-24차: 요소 우클릭 메뉴(설정 열기 / 끄기 / 기본 위치 / 크기 초기화). */
	/**
	 * 49-69차(4-28 · 4-45): <b>바닐라가 차지하는 자리</b>를 흐린 테두리로 그린다.
	 *
	 * <p>지금 그리는 둘:
	 * <ul>
	 *   <li><b>채팅</b> - 왼쪽 아래. 크기는 {@code ChatHud}에게 직접 물어본다(폭·줄 수·채팅 배율이
	 *       설정에 따라 다르므로 상수로 박으면 틀린 자리를 알려 주게 된다). <b>못 물어보면 아예 안 그린다</b> -
	 *       엉뚱한 사각형을 그려 주는 것보다 없는 편이 낫다.</li>
	 *   <li><b>핫바</b> - 아래 가운데 182×22. 이 크기는 1.15.2부터 지금까지 같다.</li>
	 * </ul>
	 *
	 * <p>채팅은 <b>열었을 때</b> 기준으로 그린다({@code getHeight()}가 열린 높이를 준다) - 닫혀 있을 때만
	 * 보고 자리를 잡으면 채팅을 여는 순간 가려진다.
	 */
	private void drawVanillaAreas(DrawContext ctx) {
		// 핫바(아래 가운데) - 폭 182 · 높이 22는 전 버전 공통
		int hbW = 182;
		int hbH = 22;
		int hbX = (width - hbW) / 2;
		int hbY = height - hbH;
		guideBox(ctx, hbX, hbY, hbW, hbH, "핫바");

		int[] chat = chatArea();
		if (chat != null) {
			guideBox(ctx, chat[0], chat[1], chat[2], chat[3], "채팅(열었을 때)");
		}
	}

	/** 채팅 칸 {x, y, w, h}. 크기를 못 읽으면 null. */
	private int[] chatArea() {
		try {
			Object hud = kr.lunaslight.mod.util.LunaCompat.callNoArg(client, "getInGameHud");
			Object chatHud = kr.lunaslight.mod.util.LunaCompat.callNoArg(hud, "getChatHud");
			if (chatHud == null) {
				return null;
			}
			// 1.21.11부터 셋 다 private이라 findAnyMethod(선언 메서드까지)를 쓴다
			java.lang.reflect.Method mw = kr.lunaslight.mod.util.LunaCompat.findAnyMethod(chatHud.getClass(), "getWidth");
			java.lang.reflect.Method mh = kr.lunaslight.mod.util.LunaCompat.findAnyMethod(chatHud.getClass(), "getHeight");
			java.lang.reflect.Method ms = kr.lunaslight.mod.util.LunaCompat.findAnyMethod(chatHud.getClass(), "getChatScale");
			if (mw == null || mh == null) {
				return null;
			}
			int cw = (int) mw.invoke(chatHud);
			int ch = (int) mh.invoke(chatHud);
			double scale = ms == null ? 1.0 : ((Number) ms.invoke(chatHud)).doubleValue();
			if (cw <= 0 || ch <= 0 || scale <= 0) {
				return null;
			}
			int w = (int) Math.round(cw * scale);
			int h = (int) Math.round(ch * scale);
			// 채팅은 왼쪽 아래에서 위로 쌓인다(입력창 자리만큼 띄워져 있다)
			return new int[]{2, height - 40 - h, w, h};
		} catch (Throwable ignored) {
			return null;
		}
	}

	/** 점선 느낌의 흐린 테두리 + 구석 이름표. 편집 대상과 헷갈리지 않게 아주 옅게 그린다. */
	private void guideBox(DrawContext ctx, int x, int y, int w, int h, String label) {
		int line = 0x33FFFFFF;
		for (int i = x; i < x + w; i += 6) {
			int end = Math.min(x + w, i + 3);
			ctx.fill(i, y, end, y + 1, line);
			ctx.fill(i, y + h - 1, end, y + h, line);
		}
		for (int i = y; i < y + h; i += 6) {
			int end = Math.min(y + h, i + 3);
			ctx.fill(x, i, x + 1, end, line);
			ctx.fill(x + w - 1, i, x + w, end, line);
		}
		LunaDraw.text(ctx, textRenderer, label, x + 3, y + 2, 0x55FFFFFF);
	}

	private void renderMenu(DrawContext ctx, int mouseX, int mouseY) {
		if (menuModule == null) {
			return;
		}
		int h = MENU_ROW * MENU_ITEMS.length + 6 + 14;
		LunaDraw.panel3d(ctx, menuX, menuY, MENU_W, h, 5);   // 49-227차: 사진 시안 판
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, menuModule.getDisplayName(), MENU_W - 12), menuX + 6, LunaDraw.textY(menuY, 14), LunaDraw.TEXT_DIM);
		ctx.fill(menuX + 4, menuY + 14, menuX + MENU_W - 4, menuY + 15, 0x14FFFFFF);
		for (int i = 0; i < MENU_ITEMS.length; i++) {
			int iy = menuY + 17 + i * MENU_ROW;
			boolean hov = LunaDraw.in(mouseX, mouseY, menuX + 3, iy, MENU_W - 6, MENU_ROW);
			if (hov) {
				LunaDraw.roundRect(ctx, menuX + 3, iy, MENU_W - 6, MENU_ROW, 3, 0x14FFFFFF);
			}
			String label = MENU_ITEMS[i];
			if (i == 1) {
				label = menuModule.isEnabled() ? "끄기" : "켜기";
			}
			LunaDraw.text(ctx, textRenderer, label, menuX + 8, LunaDraw.textY(iy, MENU_ROW), i == 1 && menuModule.isEnabled() ? 0xFFFF8B82 : LunaDraw.TEXT);
		}
	}

	private boolean handleMenuClick(double mouseX, double mouseY) {
		if (menuModule == null) {
			return false;
		}
		int h = MENU_ROW * MENU_ITEMS.length + 6 + 14;
		Module m = menuModule;
		if (!LunaDraw.in(mouseX, mouseY, menuX, menuY, MENU_W, h)) {
			menuModule = null;
			return false; // 바깥 클릭은 메뉴만 닫고 클릭은 계속 처리
		}
		for (int i = 0; i < MENU_ITEMS.length; i++) {
			int iy = menuY + 17 + i * MENU_ROW;
			if (LunaDraw.in(mouseX, mouseY, menuX + 3, iy, MENU_W - 6, MENU_ROW)) {
				menuModule = null;
				PositionSetting ps = positionOf(m);
				switch (i) {
					case 0 -> {
						LunaClientConfig.save();
						kr.lunaslight.mod.util.HudEditorState.active = false;
						// 49-195차(사용자: "기능 세부 설정 나가면 바로 전 화면으로"): 설정에서 뒤로 가면 이 편집기로 돌아온다
						kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaClientScreen(this, m));
					}
					case 1 -> {
						if (m.isVersionSupported()) {
							ModuleManager.get().toggleWithConflictResolution(m);
						}
					}
					case 2 -> {
						if (ps != null) {
							ps.resetToDefault();
						}
					}
					case 3 -> {
						if (ps != null) {
							HudPosition p = ps.get();
							p.scale = 1f;
							ps.setValue(p);
						}
					}
					default -> {
					}
				}
				return true;
			}
		}
		menuModule = null;
		return true;
	}

	private void renderList(DrawContext ctx, int mouseX, int mouseY) {
		int lx = listX(), ly = listY(), lh = listH();
		float fold = LunaDraw.anim("edlist", panelCollapsed ? 0f : 1f, 16f);
		LunaDraw.panel3d(ctx, lx, ly, LIST_W, lh, 6);   // 49-227차: 사진 시안 판
		// 머리띠: 잡고 옮기기 + 꺾쇠(접기)
		boolean headHover = LunaDraw.in(mouseX, mouseY, lx, ly, LIST_W, LIST_HEAD_H);
		if (headHover || panelDragging) {
			LunaDraw.roundRect(ctx, lx + 1, ly + 1, LIST_W - 2, LIST_HEAD_H - 1, 5, 0x10FFFFFF);
		}
		LunaIcons.draw(ctx, textRenderer, LunaIcons.MOVE, lx + 9, LunaDraw.iconY(ly, LIST_HEAD_H), headHover ? LunaDraw.TEXT_SUB : LunaDraw.TEXT_DIM);
		LunaDraw.text(ctx, textRenderer, "HUD 요소", lx + 24, LunaDraw.textY(ly, LIST_HEAD_H), LunaDraw.TEXT);
		LunaDraw.chevron(ctx, lx + LIST_W - 16, ly + (LIST_HEAD_H - 5) / 2, !panelCollapsed,
			LunaDraw.in(mouseX, mouseY, lx + LIST_W - 24, ly, 24, LIST_HEAD_H) ? LunaDraw.TEXT : LunaDraw.TEXT_DIM);
		if (panelCollapsed || fold < 0.05f) {
			return;
		}
		ctx.fill(lx + 8, ly + LIST_HEAD_H, lx + LIST_W - 8, ly + LIST_HEAD_H + 1, 0x14FFFFFF);

		int top = ly + LIST_HEAD_H + 4;
		// 49-130차(사용자: 목록 끝 줄이 '바닐라 자리'와 겹침): 49-69차에 '바닐라 자리' 줄을 더하면서 목록 끝을
		// 안 올려서 마지막 항목이 스위치 줄 위로 그려졌다. 아래 스위치 두 줄 + 버튼 자리를 전부 비운다.
		int bottom = ly + lh - 26 - 40;
		List<Module> mods = positionable();
		listMaxScroll = Math.max(0, mods.size() * LIST_ROW_H - (bottom - top));
		if (listScroll > listMaxScroll) {
			listScroll = listMaxScroll;
		}
		boolean inList = LunaDraw.in(mouseX, mouseY, lx, top, LIST_W, bottom - top);

		ctx.enableScissor(lx, top, lx + LIST_W, bottom);
		int y = top - (int) listScroll;
		for (Module m : mods) {
			if (y + LIST_ROW_H >= top && y <= bottom) {
				boolean sel = m == selected;
				boolean hov = inList && LunaDraw.in(mouseX, mouseY, lx + 4, y, LIST_W - 8, LIST_ROW_H);
				if (sel || hov) {
					LunaDraw.roundRect(ctx, lx + 4, y, LIST_W - 8, LIST_ROW_H, 3, sel ? LunaDraw.ACCENT_SOFT : 0x12FFFFFF);
				}
				boolean supported = m.isVersionSupported();
				int nameColor = supported ? (m.isEnabled() ? LunaDraw.TEXT : LunaDraw.TEXT_SUB) : LunaDraw.TEXT_DIM;
				LunaIcons.draw(ctx, textRenderer, LunaIcons.forModule(m.getId(), m.getCategory()), lx + 9, LunaDraw.iconY(y, LIST_ROW_H),
					m.isEnabled() && supported ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
				String name = LunaDraw.ellipsize(textRenderer, m.getDisplayName(), LIST_W - 8 - 12 - 30 - 12);
				LunaDraw.text(ctx, textRenderer, name, lx + 23, LunaDraw.textY(y, LIST_ROW_H), nameColor);
				LunaDraw.toggle(ctx, lx + LIST_W - 8 - 24, y + 3, 22, 10, LunaDraw.anim("t:" + m.getId(), m.isEnabled() ? 1f : 0f, 14f), supported);
			}
			y += LIST_ROW_H;
		}
		ctx.disableScissor();
		// 목록과 아래 스위치 사이 가는 선
		ctx.fill(lx + 8, ly + lh - 26 - 37, lx + LIST_W - 8, ly + lh - 26 - 36, 0x14FFFFFF);

		// 겹침 경고 스위치(49-22차) + 바닐라 자리 스위치(49-69차)
		int vgY = ly + lh - 26 - 34;
		LunaDraw.text(ctx, textRenderer, "바닐라 자리", lx + 12, LunaDraw.textY(vgY, 14), LunaDraw.TEXT_SUB);
		LunaDraw.toggle(ctx, lx + LIST_W - 8 - 24, vgY + 2, 22, 10, LunaDraw.anim("edvan", vanillaGuide ? 1f : 0f, 14f), true);
		int owY = ly + lh - 26 - 18;
		LunaDraw.text(ctx, textRenderer, "겹침 경고", lx + 12, LunaDraw.textY(owY, 14), LunaDraw.TEXT_SUB);
		LunaDraw.toggle(ctx, lx + LIST_W - 8 - 24, owY + 2, 22, 10, LunaDraw.anim("edovl", overlapWarning ? 1f : 0f, 14f), true);

		// 49-9차: 전체 기본 위치 버튼 - 모든 HUD 위치를 새 기본 배치(겹침 없음)로 한 번에
		int rbY = ly + lh - 26;
		boolean rbHover = LunaDraw.in(mouseX, mouseY, lx + 8, rbY, LIST_W - 16, 20);
		LunaDraw.roundRectBordered(ctx, lx + 8, rbY, LIST_W - 16, 20, 4,
			rbHover ? LunaDraw.CARD_HOVER : LunaDraw.CARD,
			rbHover ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x8C) : LunaDraw.CARD_BORDER);
		String rbLabel = "전체 기본 위치로";
		int rbW = LunaDraw.width(textRenderer, rbLabel) + 15;
		int rbX = lx + (LIST_W - rbW) / 2;
		LunaIcons.draw(ctx, textRenderer, LunaIcons.RESET, rbX, LunaDraw.iconBesideText(rbY + 7),
			rbHover ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
		LunaDraw.text(ctx, textRenderer, rbLabel, rbX + 15, rbY + 7,
			rbHover ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
	}

	// ---- 입력 ----

	private Element elementAt(double mx, double my) {
		List<Element> list = elements();
		// 나중에 그려진(위에 있는) 것부터
		for (int i = list.size() - 1; i >= 0; i--) {
			Element e = list.get(i);
			if (LunaDraw.in(mx, my, e.x() - BOX_PAD, e.y() - BOX_PAD, e.w() + BOX_PAD * 2, e.h() + BOX_PAD * 2)) {
				return e;
			}
		}
		return null;
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		if (askSave) {
			return handleSaveAskClick(mouseX, mouseY);
		}
		if (handleMenuClick(mouseX, mouseY)) {
			return true;
		}
		// 오른쪽 목록
		int lx = listX(), ly = listY(), lh = listH();
		if (LunaDraw.in(mouseX, mouseY, lx, ly, LIST_W, lh)) {
			// 머리띠: 꺾쇠 = 접기/펴기, 나머지 = 패널 옮기기 시작
			if (mouseY < ly + LIST_HEAD_H) {
				// 49-32차(사용자: "화살표 말고 버튼 자체를 눌러도 되게"):
				// 꺾쇠뿐 아니라 머리띠 아무 데나 눌러도 접히고 펴진다. 끌면(움직이면) 그때부터
				// 패널 옮기기로 바뀌고, 그 경우엔 놓을 때 접지 않는다(headPressMoved).
				headPressed = true;
				headPressMoved = false;
				panelDragging = true;
				panelGrabDx = (int) mouseX - lx;
				panelGrabDy = (int) mouseY - ly;
				return true;
			}
			if (panelCollapsed) {
				return true;
			}
			// 하단 '전체 기본 위치' 버튼
			if (LunaDraw.in(mouseX, mouseY, lx + 8, ly + lh - 26, LIST_W - 16, 20)) {
				for (Module m : positionable()) {
					PositionSetting ps = positionOf(m);
					if (ps != null) {
						ps.resetToDefault();
					}
				}
				return true;
			}
			// 바닐라 자리 스위치(49-69차) - 겹침 경고보다 한 줄 위
			if (LunaDraw.in(mouseX, mouseY, lx, ly + lh - 26 - 34, LIST_W, 16)) {
				vanillaGuide = !vanillaGuide;
				return true;
			}
			// 겹침 경고 스위치
			if (LunaDraw.in(mouseX, mouseY, lx, ly + lh - 26 - 18, LIST_W, 16)) {
				overlapWarning = !overlapWarning;
				return true;
			}
			int top = ly + LIST_HEAD_H + 4;
			int bottom = ly + lh - 26 - 40;
			int y = top - (int) listScroll;
			for (Module m : positionable()) {
				if (mouseY >= top && mouseY < bottom && LunaDraw.in(mouseX, mouseY, lx + 4, y, LIST_W - 8, LIST_ROW_H)) {
					if (mouseX >= lx + LIST_W - 8 - 28) {
						if (m.isVersionSupported()) {
							ModuleManager.get().toggleWithConflictResolution(m);
						}
					} else {
						selected = m;
					}
					return true;
				}
				y += LIST_ROW_H;
			}
			return true;
		}

		// 49-187차: 네 모서리 크기 조절 손잡이(선택한 요소 먼저, 그다음 마우스 아래 요소)
		if (button == 0) {
			for (int pass = 0; pass < 2; pass++) {
				for (Element se : elements()) {
					// 첫 바퀴는 선택한 요소, 둘째 바퀴는 나머지(마우스를 올리면 손잡이가 보이는 요소들)
					if ((pass == 0) != (se.module() == selected)) {
						continue;
					}
					int bx = se.x() - BOX_PAD, by = se.y() - BOX_PAD;
					int bw = se.w() + BOX_PAD * 2, bh = se.h() + BOX_PAD * 2;
					for (int c = 0; c < 4; c++) {
						int hx = (c % 2 == 0 ? bx : bx + bw) - HANDLE / 2;
						int hy = (c < 2 ? by : by + bh) - HANDLE / 2;
						if (!LunaDraw.in(mouseX, mouseY, hx - 3, hy - 3, HANDLE + 6, HANDLE + 6)) {
							continue;
						}
						resizing = se.module();
						selected = se.module();
						resizeSetting = se.setting();
						resizeLeft = c % 2 == 0;
						resizeTop = c < 2;
						resizeOriginX = resizeLeft ? se.x() + se.w() : se.x();
						resizeOriginY = resizeTop ? se.y() + se.h() : se.y();
						float sc = Math.max(0.05f, se.setting().get().scale);
						resizeUnitW = Math.max(1f, se.w() / sc);
						resizeUnitH = Math.max(1f, se.h() / sc);
						movedAnything = true;
						return true;
					}
				}
			}
		}
		Element e = elementAt(mouseX, mouseY);
		if (e == null) {
			// 빈 곳 클릭: 선택 해제(닫지는 않음 - 실수로 나가는 것 방지, ESC로 나감)
			selected = null;
			return true;
		}
		selected = e.module();
		if (button == 1) {
			// 49-24차: 우클릭 = 메뉴(설정 열기/끄기/기본 위치/크기 초기화)
			menuModule = e.module();
			int h = MENU_ROW * MENU_ITEMS.length + 6 + 14;
			menuX = (int) Math.min(mouseX, width - MENU_W - 2);
			menuY = (int) Math.min(mouseY, height - h - 2);
			return true;
		}
		// 잡는 순간의 크기를 고정(폭이 매 프레임 바뀌는 HUD도 드래그 내내 안정)
		dragging = e.module();
		movedAnything = true;   // 49-32차: ESC에 저장 여부를 물어보기 위해
		dragSetting = e.setting();
		grabDx = mouseX - e.x();
		grabDy = mouseY - e.y();
		dragW = e.w();
		dragH = e.h();
		return true;
	}

	/**
	 * 49-65차(5-6 "HUD 설정에서 화면 끝으로 밀면 조금 가려지는 현상"): 화면 끝 클램프.
	 *
	 * <p>원인은 <b>편집기가 글자 크기만 보고 끝에 딱 붙인 것</b>이었다. 실제로 그려지는 건 글자 뒤의
	 * 배경 상자고, 그 상자는 사방으로 {@link Module#HUD_BOX_PAD}px 더 넓다 - 그만큼이 화면 밖으로
	 * 나가서 "조금" 잘려 보였다. 그래서 그 여백만큼 안쪽에서 멈춘다.
	 *
	 * <p>자동 정렬(스냅) 뒤에도 다시 조인다 - 스냅은 다른 요소 선에 맞추느라 클램프를 넘어설 수 있다.
	 */
	private float clampX(float x) {
		int pad = kr.lunaslight.mod.module.Module.HUD_BOX_PAD;
		return Math.max(pad, Math.min(width - dragW - pad, x));
	}

	private float clampY(float y) {
		int pad = kr.lunaslight.mod.module.Module.HUD_BOX_PAD;
		return Math.max(pad, Math.min(height - dragH - pad, y));
	}

	@Override
	protected boolean lunaMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (panelDragging) {
			panelX = Math.max(0, Math.min(width - LIST_W, (int) mouseX - panelGrabDx));
			panelY = Math.max(0, Math.min(height - LIST_HEAD_H, (int) mouseY - panelGrabDy));
			headPressMoved = true;
			return true;
		}
		if (resizing != null && resizeSetting != null) {
			// 49-187차: 반대쪽 모서리를 고정하고, 거기서 마우스까지 가로/세로 중 큰 쪽 비율로(비율은 안 바뀜). 0.05 단위.
			float sx = (float) Math.abs(mouseX - resizeOriginX) / resizeUnitW;
			float sy = (float) Math.abs(mouseY - resizeOriginY) / resizeUnitH;
			// 화면 밖으로는 못 커지게 - 고정 모서리에서 화면 끝까지가 한계
			float roomX = (resizeLeft ? resizeOriginX : width - resizeOriginX) / resizeUnitW;
			float roomY = (resizeTop ? resizeOriginY : height - resizeOriginY) / resizeUnitH;
			float ns = Math.max(sx, sy);
			ns = Math.min(ns, Math.min(roomX, roomY));
			ns = (float) Math.floor(ns * 20f) / 20f;
			ns = Math.max(SCALE_MIN, Math.min(SCALE_MAX, ns));
			HudPosition p = resizeSetting.get();
			if (Math.abs(p.scale - ns) > 0.001f) {
				int nw = Math.max(1, Math.round(resizeUnitW * ns));
				int nh = Math.max(1, Math.round(resizeUnitH * ns));
				int nx = resizeLeft ? resizeOriginX - nw : resizeOriginX;
				int ny = resizeTop ? resizeOriginY - nh : resizeOriginY;
				p.scale = ns;
				p.moveTo(nx, ny, width, height, nw, nh);
				resizeSetting.setValue(p);
			}
			return true;
		}
		if (dragging == null || dragSetting == null) {
			return false;
		}
		// 49-24차: 소수 좌표 유지(놓을 때 반올림) - 스냅/정렬 판정은 반올림값으로
		float fxPos = (float) (mouseX - grabDx);
		float fyPos = (float) (mouseY - grabDy);
		fxPos = clampX(fxPos);
		fyPos = clampY(fyPos);
		int nx = Math.round(fxPos);
		int ny = Math.round(fyPos);
		boolean snapped = false;
		guideX = Integer.MIN_VALUE;
		guideY = Integer.MIN_VALUE;
		// 가운데 스냅(49-34차: 바닐라 십자 픽셀 기준)
		int ccx = kr.lunaslight.mod.util.LunaCompat.crosshairCenterX(client);
		int ccy = kr.lunaslight.mod.util.LunaCompat.crosshairCenterY(client);
		int cx = nx + dragW / 2;
		if (Math.abs(cx - ccx) <= SNAP) {
			nx = ccx - dragW / 2;
			snapped = true;
		}
		int cy = ny + dragH / 2;
		if (Math.abs(cy - ccy) <= SNAP) {
			ny = ccy - dragH / 2;
			snapped = true;
		}
		// 49-22차: 다른 요소의 선(왼/오른/가운데, 위/아래/가운데)에 자동 정렬
		int bestDx = Integer.MAX_VALUE, bestDy = Integer.MAX_VALUE;
		int snapX = nx, snapY = ny, gx = Integer.MIN_VALUE, gy = Integer.MIN_VALUE;
		for (Element o : elements()) {
			if (o.module() == dragging) {
				continue;
			}
			int[] mineX = {nx, nx + dragW, nx + dragW / 2};
			int[] theirX = {o.x(), o.x() + o.w(), o.x() + o.w() / 2};
			for (int a = 0; a < 3; a++) {
				for (int b = 0; b < 3; b++) {
					int d = theirX[b] - mineX[a];
					if (Math.abs(d) <= ALIGN_SNAP && Math.abs(d) < Math.abs(bestDx)) {
						bestDx = d;
						snapX = nx + d;
						gx = theirX[b];
					}
				}
			}
			int[] mineY = {ny, ny + dragH, ny + dragH / 2};
			int[] theirY = {o.y(), o.y() + o.h(), o.y() + o.h() / 2};
			for (int a = 0; a < 3; a++) {
				for (int b = 0; b < 3; b++) {
					int d = theirY[b] - mineY[a];
					if (Math.abs(d) <= ALIGN_SNAP && Math.abs(d) < Math.abs(bestDy)) {
						bestDy = d;
						snapY = ny + d;
						gy = theirY[b];
					}
				}
			}
		}
		if (bestDx != Integer.MAX_VALUE) {
			nx = Math.round(clampX(snapX));
			guideX = gx;
			snapped = true;
		}
		if (bestDy != Integer.MAX_VALUE) {
			ny = Math.round(clampY(snapY));
			guideY = gy;
			snapped = true;
		}
		HudPosition p = dragSetting.get();
		if (snapped) {
			p.moveTo(nx, ny, width, height, dragW, dragH);
		} else {
			p.moveTo(fxPos, fyPos, width, height, dragW, dragH);
		}
		return true;
	}

	@Override
	protected boolean lunaMouseReleased(double mouseX, double mouseY, int button) {
		if (panelDragging) {
			panelDragging = false;
			if (headPressed && !headPressMoved) {
				panelCollapsed = !panelCollapsed;   // 머리띠를 그냥 눌렀다 뗀 것 = 접기/펴기
			}
			headPressed = false;
			return true;
		}
		if (resizing != null) {
			if (resizeSetting != null) {
				// 크기를 바꾼 뒤 자리에 맞는 모서리로 다시 붙인다(창 크기가 바뀌어도 그 자리를 따라가게)
				HudPosition p = resizeSetting.get();
				int nw = Math.max(1, Math.round(resizeUnitW * p.scale));
				int nh = Math.max(1, Math.round(resizeUnitH * p.scale));
				p.reanchor(width, height, nw, nh);
				p.offsetX = Math.round(p.offsetX);
				p.offsetY = Math.round(p.offsetY);
				resizeSetting.setValue(p);
			}
			resizing = null;
			resizeSetting = null;
			return true;
		}
		if (dragging != null) {
			if (dragSetting != null) {
				HudPosition p = dragSetting.get();
				p.reanchor(width, height, dragW, dragH);
				p.offsetX = Math.round(p.offsetX);
				p.offsetY = Math.round(p.offsetY);
				dragSetting.setValue(p); // onChange 콜백(캐시 갱신 등)용
			}
			dragging = null;
			dragSetting = null;
			guideX = Integer.MIN_VALUE;
			guideY = Integer.MIN_VALUE;
			return true;
		}
		return false;
	}

	private Element find(Module m) {
		for (Element e : elements()) {
			if (e.module() == m) {
				return e;
			}
		}
		return null;
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		if (!panelCollapsed && LunaDraw.in(mouseX, mouseY, listX(), listY(), LIST_W, listH())) {
			listScroll = Math.max(0, Math.min(listMaxScroll, listScroll - verticalAmount * 16));
			return true;
		}
		// 49-24차: 요소 위에서 휠 = 크기(0.5~2.0, 0.05 단위). 마우스 아래 요소가 없으면 선택된 요소.
		Element e = elementAt(mouseX, mouseY);
		if (e == null && selected != null) {
			e = find(selected);
		}
		if (e != null && verticalAmount != 0) {
			HudPosition p = e.setting().get();
			float ns = p.scale + (verticalAmount > 0 ? 0.05f : -0.05f);
			ns = Math.round(ns * 20f) / 20f;
			p.scale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, ns));
			e.setting().setValue(p);
			selected = e.module();
			return true;
		}
		return false;
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			// 49-32차: 바로 나가지 않고 한 번 물어본다(실수로 옮겨 놓고 나가는 것 방지).
			if (askSave) {
				askSave = false;
				return true;
			}
			if (movedAnything) {
				askSave = true;
				return true;
			}
			close();
			return true;
		}
		// 방향키 미세 이동
		if (selected != null) {
			int dx = 0, dy = 0;
			if (keyCode == GLFW.GLFW_KEY_LEFT) {
				dx = -1;
			} else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
				dx = 1;
			} else if (keyCode == GLFW.GLFW_KEY_UP) {
				dy = -1;
			} else if (keyCode == GLFW.GLFW_KEY_DOWN) {
				dy = 1;
			}
			if (dx != 0 || dy != 0) {
				Element e = find(selected);
				if (e != null) {
					HudPosition p = e.setting().get();
					p.moveTo(e.x() + dx, e.y() + dy, width, height, e.w(), e.h());
					e.setting().setValue(p);
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// ==================== 49-32차: ESC 저장 확인 ====================

	private int askW() {
		return 220;
	}

	private int askH() {
		return 74;
	}

	private int askX() {
		return (width - askW()) / 2;
	}

	private int askY() {
		return (height - askH()) / 2;
	}

	private void renderSaveAsk(DrawContext ctx, int mouseX, int mouseY) {
		ctx.fill(0, 0, width, height, 0x8C000000);
		int x = askX(), y = askY(), w = askW(), h = askH();
		LunaDraw.panel3d(ctx, x, y, w, h, 6);   // 49-227차: 사진 시안 판
		LunaDraw.textBold(ctx, textRenderer, "바뀐 위치를 저장할까요?", x + 14, y + 12, LunaDraw.TEXT);
		LunaDraw.text(ctx, textRenderer, "저장하지 않으면 이번에 옮긴 것은 사라집니다.", x + 14, y + 27, LunaDraw.TEXT_SUB);
		int bw = (w - 14 * 2 - 8) / 2;
		int by = y + h - 14 - 20;
		LunaDraw.pillButton(ctx, textRenderer, x + 14, by, bw, 20, "저장",
			LunaDraw.in(mouseX, mouseY, x + 14, by, bw, 20), true);
		LunaDraw.pillButton(ctx, textRenderer, x + 14 + bw + 8, by, bw, 20, "취소",
			LunaDraw.in(mouseX, mouseY, x + 14 + bw + 8, by, bw, 20), false);
	}

	/** 확인 창이 떠 있을 때의 클릭. 처리했으면 true. */
	private boolean handleSaveAskClick(double mouseX, double mouseY) {
		int x = askX(), y = askY(), w = askW(), h = askH();
		int bw = (w - 14 * 2 - 8) / 2;
		int by = y + h - 14 - 20;
		if (LunaDraw.in(mouseX, mouseY, x + 14, by, bw, 20)) {
			askSave = false;
			close();                      // close()가 설정을 저장한다
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, x + 14 + bw + 8, by, bw, 20)) {
			// 49-122차: [취소] = 저장 안 하고 되돌린 뒤 나간다(예전엔 "계속 편집"이라 안 나가졌다).
			askSave = false;
			discardAndClose();
			return true;
		}
		return true;                      // 창 밖 클릭은 그냥 삼킴
	}
}
