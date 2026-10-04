package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.config.LunaClientConfig;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.SettingsPage;
import kr.lunaslight.mod.module.setting.*;
import kr.lunaslight.mod.util.HarvestLog;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaHangul;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Luna's Light 기능 설정 화면(Right Shift).
 *
 * 49-129차 전면 개편(사용자가 느낌이 완전히 다른 시안 6개 중 "6번(스위스 미니멀) 바탕 + 1번(유리) 상자"를 고름):
 *  ① 창 없이 화면 전체. 게임은 흐리고 어둡게.
 *  ② 왼쪽은 글자만 있는 카테고리 목록(고른 것은 2배 큰 글씨 + 테마색 네모), 가는 세로선.
 *  ③ 오른쪽 위 큰 제목 + 개수, 밑줄 검색칸, 톱니.
 *  ④ 기능은 유리 상자(반투명 흰 채움 + 1px 흰 테두리). 기능 설정은 왼쪽 미리보기 상자 + 오른쪽 설정 상자.
 *  설정 줄·모달·가격표·색 팔레트 등 컨트롤은 예전 그대로.
 */
public class LunaClientScreen extends LunaScreenBase {
	// ---- 레이아웃 상수 ----
	// 49-166차(사용자: "세부 설정 구조 자체가 너무 작아 화면에 더 차야해"): 줄 30, 묶음 머리 24, 스위치 32x14, 버튼 22,
	// 칸 사이 12, 미리보기 최대 280. 상수만 키웠고 배치는 전부 이 값으로 계산된다.
	private static final int MARGIN = 14;
	private static final int MAX_PANEL_W = 700;
	private static final int MAX_PANEL_H = 400;
	private static final int PANEL_RADIUS = 6;     // 49-22차: 둥근 정도 완화(LunaDraw가 최대 4로 통일)
	private static final int PAD = 10;

	private static final int TAB_H = 32;      // 상단 탭 바 높이
	private static final int TAB_BTN = 22;    // 탭 버튼 높이
	// 49-20차: "설명이 너무 보기 어려워 그냥 없애줘" - 설명 문장을 지우고, 이 줄은
	// 뒤로가기/액션 버튼만 남는 얇은 '액션 바'가 됨.
	private static final int DESC_H = 22;     // 액션 바 높이
	private static final int PREVIEW_W = 170; // 미리보기 패널 폭
	private static final int MODS_DETAIL_W = 210; // 모드 목록의 오른쪽 상세 패널 폭
	private static final int MOD_ROW_H = 30;

	private static final int CARD_MIN_W = 94;
	private static final int CARD_TOP_PAD = 4;     // 49-34차: 호버 떠오름(2px)이 잘리지 않게 첫 줄 위 여유
	// 49-76차(6-3, 사용자: "켜짐/꺼짐 사라지면서 생긴 공백에 따라서 위치 조정"): 49-53차(4-27)에 카드 아래
	// "켜짐/꺼짐" 글자를 뺀 뒤로 카드 아래 24px가 비어 있었다. 카드를 80 → 72로 줄이고 아이콘·이름을 가운데로
	// 다시 놓았다. 아이콘은 6-19로 17 → 22px가 되어 그만큼 자리를 더 차지한다.
	private static final int CARD_H = 72;
	private static final int CARD_GAP = 8;

	private static final int GROUP_HEAD_H = 24;
	private static final int ROW_H = 30;
	private static final int GROUP_GAP = 12;
	private static final int COLOR_EDITOR_H = 90;  // 49-126차: 팔레트(네모 + 색상/투명도 막대) + #색코드 + 자주 쓰는 색
	private static final int MASTER_H = 34;        // 49-34차: 설명 줄 제거 → 이름 한 줄(사용자: "기능 설명 이딴 거 쓰지 마")
	private static final int TOGGLE_W = 32;
	private static final int TOGGLE_H = 14;
	private static final int CARD_TOGGLE_W = 20;   // 카드 우상단 켜기/끄기 스위치(49-20차)
	private static final int CARD_TOGGLE_H = 10;
	private static final int NUMBOX_W = 40;
	private static final int CTRL_H = TOGGLE_H + 2;   // 49-166차: 줄 안 값 상자/선택 버튼 높이(스위치와 같은 키)

	private final Screen parent;

	// ---- 상태 ----
	// 49-21차: 마지막으로 보던 카테고리를 기억(사용자: "설정 들어가면 마지막 내 카테고리가 뜨게")
	private static ModuleCategory lastCategory = ModuleCategory.HUD;
	private static boolean lastModsView;
	// 49-56차: 기능과 별개인 설정 페이지([일반]·[UI]·[그래픽]). null이면 기능 쪽을 보고 있는 것.
	private static SettingsPage lastPage;
	private SettingsPage settingsPage = lastPage;
	private ModuleCategory selectedCategory = lastCategory;
	private Module openModule;             // null이면 카드 그리드, 아니면 그 모듈의 설정 페이지
	private double scroll;
	private double scrollTarget;
	private int maxScroll;
	private String search = "";
	private boolean searchFocused;
	// 49-188차(사용자: "검색에서 중간 수정이 안 돼"): 글자 사이 커서(←→, Home/End, Delete, 클릭한 자리)와 길 때 보이는 시작 위치
	private int searchCursor;
	private int searchViewStart;

	private Setting<?> draggingSetting;    // 슬라이더 드래그 중
	private int dragX, dragW;
	private KeybindSetting listeningKeybind;
	private String kbBlockedMsg;           // 49-125차: 키 지정이 막혔을 때 모달에 잠깐 띄우는 이유
	private long kbBlockedUntil;
	private ColorSetting openColor;        // 펼쳐진 색상 편집기
	private int colorDragChannel = -1;     // 49-126차: 10=채도/밝기 네모 11=색상 막대 12=투명도 막대
	private StringSetting editingString;
	private ColorSetting editingHex;       // 49-22차: HEX 색코드 입력 중인 색 설정
	private String hexBuffer = "";
	// 49-110차: 작물 계산기 가격표 등록 위젯 - 아이템 피커 모달 + 가격 숫자 편집.
	private PriceListSetting pickingPrices;   // 아이템 피커가 열려 있으면 그 설정
	private double pickerScroll;
	private PriceListSetting priceEditSetting; // 가격을 고치는 중인 가격표 설정
	private String priceEditId;                // 가격을 고치는 중인 아이템 id
	private String priceEditBuf = "";
	private int pendingKey = -2;           // 49-22차: 마크 키와 겹쳐 확인 대기 중인 키(-2 = 없음)
	private String pendingConflict;
	private int modalModsHeld;             // 49-24차: 키 지정 모달에서 지금 눌려 있는 보조키(MOD_* 비트)
	private boolean modalModOnly;          // 보조키만 눌린 상태(떼면 그 보조키 자체를 지정)
	private final Set<String> collapsed = new HashSet<>();   // "모듈id/그룹" 접힘 상태
	private String hoverTip;               // 이번 프레임 호버 툴팁(설정 설명)

	// 49-19차: 설치된 모드 목록 탭(모드 리스트 모드 없이 기본 내장)
	private boolean modsView = lastModsView;
	// 49-21차: 전체 초기화 확인 팝업(2단 클릭 대신)
	private boolean confirmReset;
	// 49-172차(사용자: "초기화 재차 확인이 없음"): 기능/페이지 초기화도 같은 확인 창을 거친다 - 창이 실행할 동작과 문구
	private Runnable resetAction;
	private String resetTitle = "전체 설정을 초기화할까요?";
	private String resetSub = "모든 기능의 설정/켜짐 상태가 기본값으로";
	// 49-124차(사용자: "키바인드 삭제할 때 팝업 띄워주고 확인차"): 키 삭제 확인 창.
	private final LunaConfirm keyConfirm = new LunaConfirm();
	/** 49-257차: 크림 UI로 처음 들어왔을 때 한 번 - "HUD도 크림으로 바꿀 수 있어요". */
	private final LunaConfirm creamConfirm = new LunaConfirm();
	private boolean creamChecked;
	private boolean showLibraries;
	private int selectedMod;
	private List<ModInfo> modCache;
	private boolean modCacheLibs;

	// 패널/본문 영역(init에서 계산)
	private int px, py, pw, ph;
	private int contentX, contentY, contentW, contentH;
	// 49-169차(사용자: "기능 눌렀을 때 애니메이션 방식 바꿔줘"): 본문 등장 방향. +1 = 기능 열기(오른쪽에서 밀려 들어옴),
	// -1 = 목록으로 돌아가기(왼쪽에서), 0 = 탭 바꾸기(페이드만).
	private int pageDir;
	// 49-172차(사용자: "HUD 선택은 리스트 형식으로 누르면 선택지 나오고 그중에 선택"): 선택지가 많아 알약 하나로 줄어든
	// EnumSetting은 누르면 아래로 목록이 펼쳐지고 그중 하나를 고른다(예전엔 누를 때마다 다음 값으로 넘어갔다).
	private EnumSetting<?> dropdown;
	private int ddX, ddY, ddW;

	// 무지개(빨주노초파남보) 프리셋 + 흰/검
	private static final int[] COLOR_PRESETS = {
		0xFFA9D973, 0xFFFF5252, 0xFFFF9436, 0xFFFFE24A, 0xFF4CD964,
		0xFF3B9CFF, 0xFF4A5AE8, 0xFFB05CFF, 0xFFFFFFFF, 0xFF000000
	};

	// 설정 그룹(레퍼런스의 "스타일" / "CPS"처럼 묶기)
	// 49-21차: "키 지정" 그룹 해체(사용자: "키 지정란을 따로 분리하지 말고 기능 위치에 맞게") - 키는 기능 그룹에 그대로.
	private static final int G_MAIN = 0;
	private static final int G_STYLE = 1;
	private static final String[] GROUP_NAMES = {"기능", "스타일"};

	public LunaClientScreen(Screen parent) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("Nova Client"));
		this.parent = parent;
		LunaDraw.resetAnim("panel");
		LunaDraw.resetAnim("rail:bar");
		LunaDraw.resetAnim("rail:bar:h");
		pageDir = 0;
	}

	/** 49-24차: HUD 편집기 우클릭 메뉴 [설정 열기] - 해당 모듈 설정 페이지로 바로. */
	public LunaClientScreen(Screen parent, Module open) {
		this(parent);
		if (open != null) {
			modsView = false;
			if (open.getPage() != null) {
				// 49-56차: 설정 페이지에 실린 것은 따로 페이지가 없다 - 그 페이지를 열어 준다(설정이 거기 평평하게 있다).
				settingsPage = open.getPage();
				lastPage = settingsPage;
			} else {
				settingsPage = null;
				lastPage = null;
				selectedCategory = open.getCategory();
				lastCategory = selectedCategory;
				openModule = open;
				returnOnBack = true;   // 49-195차: 다른 화면(HUD 편집기)에서 바로 연 설정 - 나가면 그 화면으로
			}
		}
	}

	/**
	 * 49-195차(사용자: "기능 세부 설정 나가면 바로 전 화면으로 가기"): 기능 설정을 다른 화면(HUD 편집기 우클릭 [설정 열기])에서
	 * 바로 열었으면 뒤로(← / ESC) 갈 때 목록이 아니라 그 화면으로 돌아간다. 목록에서 연 설정은 목록으로 돌아가되,
	 * 열기 전에 보던 스크롤 자리 그대로(예전엔 맨 위로 올라가 방금 보던 기능을 다시 찾아야 했다).
	 */
	private boolean returnOnBack;
	private double listScrollBeforeOpen = -1;

	// =====================================================================
	// 49-129차: 전면 개편(사용자가 시안 6개 중 고름: "6번 베이스에 1번처럼 박스 형태로, 초록 동그라미는 없애고,
	// 기존 방식들 조합해서")
	// =====================================================================
	// 창(패널) 없이 화면 전체를 쓴다. 게임은 흐리고 어둡게 깔고 그 위에:
	//  - 왼쪽: 글자만 있는 카테고리 목록(6번). 고른 것은 2배 큰 글씨 + 테마색 네모 표시. 가는 세로선으로 구분.
	//  - 오른쪽: 큰 제목(2배) + 개수, 오른쪽 위에 밑줄 검색칸과 톱니.
	//  - 기능은 유리 상자(1번): 반투명 흰 채움 + 1px 흰 테두리 + 윗면 하이라이트. 켜진 상자는 조금 더 밝게.
	//    1번에 있던 초록 빛 번짐(동그라미)은 뺐다.
	//  - 기능 설정: 뒤로 + 큰 이름 + 큰 스위치, 왼쪽 미리보기 상자 + 오른쪽 설정 상자들(기존 설정 줄 그대로).
	// 색: 밝기 단계는 흰색 알파(어두운 배경 위라 어떤 테마에서도 읽힘), 강조는 테마색, 켜짐 스위치는 초록 고정.

	// 49-141차: 시안 v4(사용자: "1번에서 과함을 좀 덜고 폰트는 정상화" → "이쪽으로 가되 전에 쓰던 아이콘들은
	// 제대로 잘 보이게 유지해주고 크기도 적당하게"). 평평한 바탕 + 1px 테두리 카드, 글자는 전부 갈무리,
	// 다크/라이트 두 가지. 아래 색은 매 프레임 applyScreenTheme()가 지금 테마에 맞춰 채운다.
	private static int WHITE = 0xFFE9ECEF;     // 본문 글자
	private static int GRAY = 0xFF6C737B;      // 보조 글자
	private static int GRAY_DIM = 0xFF3F454B;  // 흐린 글자
	private static int OFF_TXT = 0xFF5A6168;   // 꺼진 상자의 글자·아이콘
	private static int BG = 0xFF0C0D0F;
	private static int CARD_C = 0xFF141619;
	private static int CARD_HOV = 0xFF191C20;
	private static int LINE = 0xFF212428;      // 카드 테두리·구분선
	private static int LINE_HOV = 0xFF2E3237;
	private static int ACC = 0xFFA9D973;       // 강조(테마색, 라이트는 조금 진하게)
	private static int SW_OFF = 0xFF2A2E33;
	private static int KNOB_OFF = 0xFF5A6168;
	private static int MODAL_BG = 0xF20F1013;
	private static int NUMBOX_BG = 0xF00A0C0E;
	private static int INK = 0xFFFFFF;         // 호버·구분선 같은 반투명 덧칠의 RGB(다크 = 흰색, 라이트 = 남색)
	private static boolean LIGHT;
	private static final int TITLE_H = 24;
	// 49-148차(사용자: "기능 리스트 볼 때 너무 작아서 - 큰 박스, 작은 박스, 자세히 보기(1열) 3개 설정, 큰 박스 기본"):
	// 상자 크기는 [글꼴과 테마] > [기능 보기] 값에 따라 applyTileMode()가 매 프레임(그리고 클릭 전에) 채운다.
	private static final int VIEW_LARGE = 0, VIEW_SMALL = 1, VIEW_LIST = 2;
	private static int VIEW = VIEW_LARGE;
	private static int TILE_H = 74;
	private static int TILE_GAP = 8;
	private static int TILE_MIN_W = 128;
	private static final int TILE_TOP_PAD = 1;
	private static int SW_W = 20;
	private static int SW_H = 10;
	private static final int BIG_SW_W = 44;   // 49-226차: 큰 스위치 → [켜짐] 입체 버튼
	private static final int BIG_SW_H = 18;
	private static final int ICON_INK = 10;       // 아이콘 잉크 폭(12px 칸 안 가운데 10px)
	private static final int BTN_H = 22;

	// 배치(updateLayout)
	private int railX, railW, dividerX, rightX, titleY, railTop;
	// 49-226차: 사진 시안 - 화면 가운데 불투명 판(panX..) + 머리 줄 아래 선(headLineY)
	private int panX, panY, panW, panH, headLineY;

	@Override
	protected void init() {
		updateLayout();
	}

	private void updateLayout() {
		// 49-226차(사용자가 고른 사진 시안: "불투명 판 + 칸마다 테두리 카드 + 아래 두께 있는 입체 버튼"): 화면 전체 대신
		// 가운데 판 하나. 위 머리 줄(로고, 제목, 검색, 톱니) 아래에 선, 왼쪽은 분류 카드, 오른쪽은 본문.
		int mx = Math.max(6, Math.min(40, Math.round(this.width * 0.035f)));
		int my = Math.max(6, Math.min(20, Math.round(this.height * 0.03f)));
		panX = mx;
		panY = my;
		panW = this.width - mx * 2;
		panH = this.height - my * 2;
		titleY = panY + 8;
		headLineY = titleY + TITLE_H + 8;
		railX = panX + 10;
		railW = Math.max(90, Math.min(130, Math.round(this.width * 0.19f)));
		dividerX = railX + railW + 2;
		rightX = panX + panW - 12 - 24;
		railTop = headLineY + 10;
		// px/py/pw/ph는 "오른쪽 본문 영역"(모달·메뉴 위치 계산용)
		px = dividerX + 12;
		py = 0;
		pw = rightX - px;
		ph = this.height;
		contentX = px;
		contentY = headLineY + 10;
		contentW = pw;
		contentH = Math.max(40, panY + panH - 10 - contentY);
	}

	// ------------------------------------------------------------- 유리 상자

	/**
	 * 49-141차: 상자 = 평평한 카드 + 1px 테두리(시안 v4). level 0 = 보통, 0~1 = 호버 정도, 2 = 강조(테두리에 테마색).
	 * 예전 유리(흰 반투명 + 윗면 하이라이트)는 라이트 테마에서 안 보여서 뺐다.
	 */
	private static void glass(DrawContext ctx, int x, int y, int w, int h, float level) {
		glass(ctx, x, y, w, h, level, 0f);
	}

	/**
	 * 49-167차(사용자: "선택한 기능은 테두리가 부드럽지가 않아"): 켜진 상자 테두리를 fill 계단(roundRectOutline) 대신
	 * 여기서 테두리 색을 테마색으로 섞어 그린다 - 바깥/안쪽 둥근 사각형 둘 다 텍스처 원이라 매끈하다. accent = 0~1.
	 */
	private static void glass(DrawContext ctx, int x, int y, int w, int h, float level, float accent) {
		// 49-226차: 사진 시안 카드 - 1px 테두리 + 속 + 아래로 2px 두께(어두운 띠). 켜진(accent) 카드는 테두리와 속이 테마색으로 물든다.
		float hv = Math.max(0f, Math.min(1f, level));
		int edge = level >= 2f ? LunaDraw.lerpColor(LINE, ACC, 0.5f) : LunaDraw.lerpColor(LINE, LINE_HOV, hv);
		int fill = level >= 2f ? CARD_C : LunaDraw.lerpColor(CARD_C, CARD_HOV, hv);
		if (accent > 0.01f) {
			float a = Math.min(1f, accent);
			edge = LunaDraw.lerpColor(edge, LunaTheme.mix(LINE, ACC, 0.6f), a);
			fill = LunaDraw.lerpColor(fill, LunaTheme.mix(fill, ACC, LIGHT ? 0.05f : 0.07f), a);
		}
		card(ctx, x, y, w, h, 4, fill, edge);
	}

	/** 카드 아래 두께 색(판 바탕보다 진하게). */
	private static int edgeColor() {
		return LIGHT ? LunaTheme.mix(LINE, 0xFF000000, 0.12f) : LunaTheme.mix(BG, 0xFF000000, 0.55f);
	}

	/** 입력칸 속(카드보다 한 단계 깊게). */
	private static int fieldColor() {
		return LIGHT ? 0xFFFFFFFF : LunaTheme.mix(BG, 0xFF000000, 0.25f);
	}

	/** 49-226차: 카드 = 아래 두께 2px + 1px 테두리 + 속. */
	private static void card(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int border) {
		if (w <= 2 || h <= 2) {
			return;
		}
		if (LunaDraw.skinCard(ctx, x, y, w, h, r, fill, border)) {
			return;   // 49-279차: 미드나잇 / 네온
		}
		if (LIGHT) {
			// 49-239차: 크림 = 더 둥글게 + 부드러운 그림자(아래 두께 없음)
			r = Math.min(r + 3, Math.min(w, h) / 2);
			LunaDraw.softShadow(ctx, x, y, w, h, r);
			LunaDraw.roundRect(ctx, x, y, w, h, r, border);
			LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
			return;
		}
		LunaDraw.roundRect(ctx, x, y + 2, w, h, r, edgeColor());
		LunaDraw.roundRect(ctx, x, y, w, h, r, border);
		LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
	}

	/** 49-226차: 설정 줄 하나를 감싸는 작은 카드(묶음 카드 안, 한 단계 깊게). */
	/**
	 * 49-275차(사용자: "키 지정 버튼 같은 것들 여전히 좀 낮아"): 줄 안 값 상자/키 버튼/선택 버튼의 위쪽 y.
	 * 줄 카드는 y+1부터 ROW_H-4 높이라 실제 가운데가 줄 가운데보다 1px 위고, 상자는 아래로 2px 두께 띠를 깔아 눈으로 보는 가운데가
	 * 1px 더 내려가 있었다. 그만큼(2px) 올린다. 슬라이더는 원래 자리 그대로.
	 */
	private static int ctrlY(int y) {
		return y + (ROW_H - CTRL_H) / 2 - 2;
	}

	private static void rowCard(DrawContext ctx, int x, int y, int w, int h) {
		if (w <= 4 || h <= 4) {
			return;
		}
		if (LunaDraw.skinRow(ctx, x, y, w, h)) {
			return;   // 49-279차
		}
		if (LIGHT) {
			int r = Math.min(6, h / 2);
			LunaDraw.roundRect(ctx, x, y + 1, w, h, r, 0x145A4630);
			LunaDraw.roundRect(ctx, x, y, w, h, r, LINE);
			LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, r - 1, 0xFFFFFDF7);
			return;
		}
		LunaDraw.roundRect(ctx, x, y + 1, w, h, 3, edgeColor());
		LunaDraw.roundRect(ctx, x, y, w, h, 3, LINE);
		LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, h - 2, 2, LunaTheme.mix(CARD_C, BG, 0.45f));
	}

	// 켜짐 버튼 초록(테마와 무관 - 켜짐 스위치와 같은 규칙)
	private static final int ON_TOP = 0xFF69A845, ON_BOT = 0xFF4F8A2C, ON_EDGE = 0xFF23401A, ON_BORDER = 0xFF3C6A22,
		ON_TEXT = 0xFFF4FFE9;

	/** 49-226차: 입체 버튼(아래 두께 2px). on = 초록 그라데이션, 아니면 회색 카드. hov = 0~1. */
	private void btn3d(DrawContext ctx, int x, int y, int w, int h, String label, boolean on, float hov) {
		if (LunaDraw.skinButton(ctx, x, y, w, h, on, hov)) {
			// 49-279차: 미드나잇 / 네온 - 켜짐 = 주 버튼, 꺼짐 = 일반 버튼
			if (label != null && !label.isEmpty()) {
				String t = LunaDraw.ellipsize(textRenderer, label, w - 6);
				LunaDraw.textCentered(ctx, textRenderer, t, x + w / 2, LunaDraw.textY(y, h), LunaDraw.skinButtonText(on, hov));
			}
			return;
		}
		if (on && LIGHT) {
			// 49-239차: 크림 - 둥근 초록 버튼 + 얇은 두께 + 부드러운 그림자(시안의 [강화하기])
			int r = Math.min(7, h / 2);
			LunaDraw.softShadow(ctx, x, y, w, h, r);
			LunaDraw.roundRect(ctx, x, y + 1, w, h, r, 0xFF3E7424);
			LunaDraw.roundRect(ctx, x, y, w, h, r, 0xFF4A8530);
			LunaDraw.roundRectGradient(ctx, x + 1, y + 1, w - 2, h - 2, r - 1,
				LunaDraw.lighten(0xFF79B556, 0.10f * hov), LunaDraw.lighten(0xFF5A9A3A, 0.10f * hov));
			ctx.fill(x + r, y + 1, x + w - r, y + 2, LunaDraw.applyAlpha(0x40FFFFFF));
		} else if (on) {
			LunaDraw.roundRect(ctx, x, y + 2, w, h, 4, ON_EDGE);
			LunaDraw.roundRect(ctx, x, y, w, h, 4, ON_BORDER);
			LunaDraw.roundRectGradient(ctx, x + 1, y + 1, w - 2, h - 2, 3,
				LunaDraw.lighten(ON_TOP, 0.10f * hov), LunaDraw.lighten(ON_BOT, 0.10f * hov));
			ctx.fill(x + 3, y + 1, x + w - 3, y + 2, LunaDraw.applyAlpha(0x33FFFFFF));
		} else {
			int fill = LunaDraw.lerpColor(CARD_HOV, LunaTheme.mix(CARD_HOV, WHITE, LIGHT ? 0.02f : 0.06f), hov);
			card(ctx, x, y, w, h, 4, fill, LunaDraw.lerpColor(LINE_HOV, LunaTheme.mix(LINE_HOV, WHITE, 0.15f), hov));
		}
		if (label != null && !label.isEmpty()) {
			String t = LunaDraw.ellipsize(textRenderer, label, w - 6);
			LunaDraw.textCentered(ctx, textRenderer, t, x + w / 2, LunaDraw.textY(y, h),
				on ? ON_TEXT : LunaDraw.lerpColor(GRAY, WHITE, Math.max(0.35f, hov)));
		}
	}

	/**
	 * 49-238차(크림 스킨 - 장비 강화 창 시안의 복숭아/하늘/민트 칩): 밝은 화면에서 기능 칸 아이콘 칸을 분류별 파스텔로.
	 * {속, 테두리, 아이콘}. 0이면 예전 색.
	 */
	private static int[] pastel(ModuleCategory c) {
		if (!LIGHT || c == null) {
			return null;
		}
		return switch (c) {
			case HUD -> new int[]{0xFFFBE8D2, 0xFFE7C08E, 0xFFB4742C};
			case VIEW -> new int[]{0xFFE2F0FB, 0xFF9DC9EE, 0xFF2F7DBF};
			case INVENTORY -> new int[]{0xFFE6F3DB, 0xFFA8D18A, 0xFF4E8A2E};
			case FEATURE -> new int[]{0xFFEDE6F8, 0xFFC3B1E6, 0xFF7656B5};
			case COMBAT -> new int[]{0xFFFBE3E3, 0xFFEBA9A9, 0xFFB8484A};
			case SERVER -> new int[]{0xFFFFF3C9, 0xFFE6CD6E, 0xFF9A7B12};
			default -> null;
		};
	}

	/** 다음 iconTile 한 번에 쓸 파스텔(그리고 지운다). */
	private int[] iconTint;

	/** 49-226차: 아이콘 칸(테두리 있는 작은 네모 + 가운데 아이콘). on = 0~1(테마색으로 물듦). */
	private void iconTile(DrawContext ctx, String glyph, int x, int y, int size, float on, int iconColor) {
		int border = LunaDraw.lerpColor(LINE_HOV, LunaTheme.mix(LINE, ACC, 0.6f), on);
		int fill = LunaDraw.lerpColor(fieldColor(), LunaTheme.mix(fieldColor(), ACC, 0.08f), on);
		int[] tint = iconTint;
		iconTint = null;
		if (tint != null) {
			// 크림: 분류 파스텔 칩. 꺼진 기능은 색을 반쯤 뺀다
			float k = 0.45f + 0.55f * on;
			fill = LunaDraw.lerpColor(fieldColor(), tint[0], k);
			border = LunaDraw.lerpColor(LINE_HOV, tint[1], k);
			if (iconColor != GRAY_DIM) {
				iconColor = LunaDraw.lerpColor(LunaDraw.lerpColor(GRAY, tint[2], 0.5f), tint[2], on);
			}
		}
		int ir = LIGHT ? Math.min(7, size / 3) : LunaDraw.neon() ? 1 : LunaDraw.midnight() ? 5 : 4;   // 49-239차: 크림은 더 둥근 칩, 49-279차: 네온 각지게
		LunaDraw.roundRect(ctx, x, y, size, size, ir, border);
		LunaDraw.roundRect(ctx, x + 1, y + 1, size - 2, size - 2, ir - 1, fill);
		if (size >= 18) {
			LunaIcons.drawInBox(ctx, textRenderer, glyph, x, y, size, iconColor);
		} else {
			iconIn(ctx, glyph, x, y, size, size, iconColor);
		}
	}

	/** 반투명 덧칠색(다크 = 흰색, 라이트 = 남색). */
	static int ink(int alpha) {
		return ((alpha & 0xFF) << 24) | INK;
	}

	/** 기존 코드(설정 묶음·페이지 상자·모드 목록 등)가 부르는 카드 = 유리 상자. fill이 호버색이면 한 단계 밝게. */
	private static void surface(DrawContext ctx, int x, int y, int w, int h, int fill) {
		glass(ctx, x, y, w, h, fill == cardHoverSolid() ? 1f : 0f);
	}

	private static int cardSolid() {
		return 0xFF000001;
	}

	private static int cardHoverSolid() {
		return 0xFF000002;
	}

	/** 아이콘을 상자 한가운데에(잉크 10px 기준). */
	private void iconIn(DrawContext ctx, String glyph, int bx, int by, int bw, int bh, int color) {
		LunaIcons.draw(ctx, textRenderer, glyph, bx + (bw - ICON_INK) / 2, LunaDraw.iconY(by, bh), color);
	}

	/**
	 * 켜짐은 사용자 요청대로 테마와 무관한 초록 고정(설정 줄 스위치와 같은 색).
	 * 49-161차(사용자: "너무 네모가 보여 클라이언트처럼 부드럽게"): 네모 노브 → 알약 트랙 + 동그란 노브(부드러운 텍스처 원).
	 */
	private static void pxSwitch(DrawContext ctx, int x, int y, int w, int h, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int in = h >= 12 ? 2 : 1;
		if (LunaDraw.skinSwitch(ctx, x, y, w, h, t)) {
			return;   // 49-279차
		}
		if (LIGHT) {
			// 49-239차: 크림 - 시안의 하늘색 알약 스위치 + 흰 노브
			LunaDraw.pill(ctx, x, y, w, h, LunaDraw.lerpColor(SW_OFF, 0xFF62ACE6, t));
			int kk = h - in * 2;
			LunaDraw.circle(ctx, Math.round(x + in + (w - in * 2 - kk) * t), y + in, kk, 0xFFFFFFFF);
			return;
		}
		LunaDraw.pill(ctx, x, y, w, h, LunaDraw.lerpColor(SW_OFF, 0xFF566E42, t));
		int k = h - in * 2;
		int kx = Math.round(x + in + (w - in * 2 - k) * t);
		LunaDraw.circle(ctx, kx, y + in, k, LunaDraw.lerpColor(KNOB_OFF, 0xFFB9E387, t));
	}

	// ------------------------------------------------------------- 49-141차: 다크/라이트

	/**
	 * 이 화면에서 쓸 색을 정한다. 다크는 런처 배경 테마(LunaTheme 팔레트)에서 뽑고, 라이트는 시안 v4 값.
	 * 라이트일 땐 설정 줄들이 쓰는 LunaDraw 공용 색도 이 프레임 동안만 밝은 값으로 바꿨다가 restoreScreenTheme()로 되돌린다.
	 */
	static void applyScreenTheme() {
		LIGHT = LunaTheme.light();
		if (LIGHT) {
			// 49-238차: 밝은 화면 = 크림 스킨(베이지 판, 크림 카드, 갈색 글자)
			BG = 0xFFF7EEDC;
			CARD_C = 0xFFFFFBF2;
			CARD_HOV = 0xFFFFF6E6;
			LINE = 0xFFE6D6B8;
			LINE_HOV = 0xFFD2BC94;
			WHITE = 0xFF3B2C1D;
			GRAY = 0xFF7D6648;
			GRAY_DIM = 0xFFC2B095;
			OFF_TXT = 0xFFB3A080;
			ACC = LunaTheme.luminance(LunaTheme.ACCENT) > 0.45f ? LunaTheme.mix(LunaTheme.ACCENT, 0xFF000000, 0.3f) : LunaTheme.ACCENT;
			SW_OFF = 0xFFE6D9C0;
			KNOB_OFF = 0xFFFFFFFF;
			MODAL_BG = 0xFAFFFBF2;
			NUMBOX_BG = 0xFFFBF4E6;
			INK = 0x3B2C1D;
			LunaDraw.TEXT = WHITE;
			LunaDraw.TEXT_SUB = 0xFF7D6648;
			LunaDraw.TEXT_DIM = 0xFFB09C7E;
			LunaDraw.CARD = 0xFFFFFBF2;
			LunaDraw.CARD_HOVER = 0xFFFBF1DE;
			LunaDraw.TRACK = 0xFFEADCC2;
			LunaDraw.CARD_BORDER = LINE;
			LunaDraw.PANEL_BORDER = LINE;
			LunaDraw.ACCENT = ACC;
			LunaDraw.ACCENT_SOFT = (ACC & 0x00FFFFFF) | 0x26000000;
			LunaDraw.PANEL = 0xF7F7EEDC;
			LunaDraw.SIDEBAR = 0xFFF2E6CF;
			return;
		}
		int bg0 = 0xFF000000 | LunaTheme.PANEL;
		int bg1 = 0xFF000000 | LunaTheme.CARD;
		int bg2 = 0xFF000000 | LunaTheme.CARD_HOVER;
		int bg3 = 0xFF000000 | LunaTheme.TRACK;
		int text = 0xFF000000 | LunaTheme.TEXT;
		BG = LunaTheme.mix(bg0, bg1, 0.35f);
		CARD_C = LunaTheme.mix(bg1, bg2, 0.5f);
		CARD_HOV = LunaTheme.mix(bg2, text, 0.02f);
		LINE = LunaTheme.mix(bg3, text, 0.03f);
		LINE_HOV = LunaTheme.mix(bg3, text, 0.12f);
		WHITE = text;
		GRAY = LunaTheme.mix(0xFF000000 | LunaTheme.TEXT_SUB, BG, 0.3f);
		GRAY_DIM = LunaTheme.mix(0xFF000000 | LunaTheme.TEXT_DIM, BG, 0.25f);
		OFF_TXT = LunaTheme.mix(GRAY, GRAY_DIM, 0.4f);
		ACC = 0xFF000000 | LunaTheme.ACCENT;
		SW_OFF = LunaTheme.mix(bg3, text, 0.06f);
		KNOB_OFF = OFF_TXT;
		MODAL_BG = 0xF2000000 | (LunaTheme.mix(bg1, bg2, 0.3f) & 0x00FFFFFF);
		NUMBOX_BG = 0xF0000000 | (bg0 & 0x00FFFFFF);
		INK = 0xFFFFFF;
		// 49-279차: 미드나잇 / 네온 - 선과 꺼진 스위치를 스킨 색으로(팔레트 섞기만으로는 회색에 가깝다)
		if (LunaDraw.midnight()) {
			LINE = LunaTheme.mix(BG, LunaDraw.MID_BORDER, 0.30f);
			LINE_HOV = LunaTheme.mix(BG, LunaDraw.MID_BORDER, 0.55f);
			SW_OFF = 0xFF2C2648;
			KNOB_OFF = 0xFF8A82B0;
		} else if (LunaDraw.neon()) {
			LINE = LunaTheme.mix(BG, LunaDraw.NEON_CYAN, 0.28f);
			LINE_HOV = LunaTheme.mix(BG, LunaDraw.NEON_CYAN, 0.6f);
			SW_OFF = 0xFF1A1A26;
			KNOB_OFF = 0xFF55556A;
		}
	}

	/** 공용 색을 테마 원래 값으로(라이트에서 바꿔 둔 것 되돌림). 테마가 프레임 중에 바뀌어도 최신 값으로 돌아간다. */
	static void restoreScreenTheme() {
		LunaDraw.TEXT = LunaTheme.TEXT;
		LunaDraw.TEXT_SUB = LunaTheme.TEXT_SUB;
		LunaDraw.TEXT_DIM = LunaTheme.TEXT_DIM;
		LunaDraw.CARD = LunaTheme.CARD;
		LunaDraw.CARD_HOVER = LunaTheme.CARD_HOVER;
		LunaDraw.TRACK = LunaTheme.TRACK;
		LunaDraw.CARD_BORDER = LunaTheme.CARD_BORDER;
		LunaDraw.PANEL_BORDER = LunaTheme.PANEL_BORDER_LIVE;
		LunaDraw.ACCENT = LunaTheme.ACCENT;
		LunaDraw.ACCENT_SOFT = LunaTheme.ACCENT_SOFT;
		LunaDraw.PANEL = LunaTheme.PANEL;
		LunaDraw.SIDEBAR = LunaTheme.SIDEBAR;
	}

	/** 다른 Luna 화면(통계, 소셜)이 같은 다크/라이트 색을 쓰도록 여는 값들. applyScreenTheme() 뒤에 읽는다. */
	static boolean themeLight() {
		return LIGHT;
	}

	static int themeBg() {
		return BG;
	}

	static int themeCard() {
		return CARD_C;
	}

	static int themeLine() {
		return LINE;
	}

	static int themeText() {
		return WHITE;
	}

	static int themeSub() {
		return GRAY;
	}

	static int themeDim() {
		return GRAY_DIM;
	}

	static int themeAccent() {
		return ACC;
	}

	// ------------------------------------------------------------- 큰 글씨

	/**
	 * 큰 글씨(2배). 49-141차(사용자: "폰트는 정상화"): 글꼴 설정과 상관없이 갈무리11을 그대로 2배 -
	 * 픽셀 글꼴이라 키워도 또렷하고, 왼쪽 목록·상자 글자와 같은 글꼴이 된다. 2D 변환이 안 되는 옛 버전은 굵은 1배.
	 */
	private Text bigTextObj(String s) {
		return railTextObj(s, RAIL_FONT_LG);
	}

	private boolean bigOk = true;

	private int bigWidth(String s) {
		if (!bigOk) {
			return LunaDraw.widthBold(textRenderer, s);
		}
		return LunaCompat.textWidth(textRenderer, bigTextObj(s)) * 2;
	}

	private void bigText(DrawContext ctx, String s, int x, int boxY, int boxH, int color) {
		if (!bigOk) {
			LunaDraw.textBold(ctx, textRenderer, s, x, LunaDraw.textY(boxY, boxH), color);
			return;
		}
		int y = boxY + Math.round(boxH / 2f - railCenter(RAIL_FONT_LG) * 2f);
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, x, y);
			LunaCompat.guiScale(ctx, 2f, 2f);
			ctx.drawText(textRenderer, bigTextObj(s), 0, 0, LunaDraw.applyAlpha(color), false);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	private String bigEllipsize(String s, int maxW) {
		if (bigWidth(s) <= maxW) {
			return s;
		}
		String t = s;
		while (t.length() > 1 && bigWidth(t + "…") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	// ------------------------------------------------------------- 왼쪽 목록(글자만)

	/** 목록 항목: 탭 번호(-1 = 구분선). */
	private List<Integer> railTabs() {
		List<Integer> out = new ArrayList<>();
		for (int i = tabAll(); i < tabModsIndex(); i++) {
			out.add(i);
		}
		out.add(-1);
		for (int i = 0; i < pages().length; i++) {
			out.add(i);
		}
		out.add(tabModsIndex());
		return out;
	}

	private static final int RAIL_SEP_H = 12;

	/** 항목 간격 - 화면이 낮으면 줄여서 목록이 "ESC 닫기" 위에서 끝나게. 49-141차: 고른 항목도 같은 높이. */
	private int railStep() {
		int n = railTabs().size() - 1;   // 구분선 제외
		int avail = (panY + panH - 28) - railTop - RAIL_SEP_H;   // 49-226차: 판 안, 아래 "ESC 닫기" 위에서 끝나게
		return Math.max(14, Math.min(24, avail / Math.max(1, n)));
	}

	private record RailItem(int tab, int y, int h) {
	}

	private List<RailItem> railItems() {
		List<RailItem> out = new ArrayList<>();
		int step = railStep();
		int y = railTop;
		for (int t : railTabs()) {
			if (t < 0) {
				out.add(new RailItem(-1, y, RAIL_SEP_H));
				y += RAIL_SEP_H;
				continue;
			}
			out.add(new RailItem(t, y, step));
			y += step;
		}
		return out;
	}

	// 49-131차(사용자: "왼쪽 카테고리는 마크 기본 글꼴 말고 갈무리로 고정 - 무조건"): 글꼴 설정(모던/마크)과
	// 상관없이 왼쪽 목록은 늘 갈무리(동봉 픽셀 한글, 영문은 마크 글꼴로 이어짐).
	// 49-132차(사용자: "기능들 크기 좀 키워 줘, 큰 거에 비해 너무 작아"): 고르지 않은 항목은 갈무리 7px → 갈무리
	// 11px(동봉 hangul11 - 원래 크기라 배율 없이도 또렷). 고른 항목은 그대로 갈무리 7px의 2배(14px).
	private static final String RAIL_FONT = "mchan";      // 7px - 2배로 키워 고른 항목에
	private static final String RAIL_FONT_LG = "mchan11"; // 11px - 나머지 항목

	private Text railTextObj(String s, String base) {
		// 49-155차(사용자: "기능에 폰트 기본으로 다 되돌려"): 갈무리 고정을 풀고 글꼴 설정(기본 = 마크 글꼴)을 따른다.
		return LunaGfx.text(s);
	}

	private Text railTextObj(String s) {
		return railTextObj(s, RAIL_FONT);
	}

	private int railWidth(String s) {
		return LunaCompat.textWidth(textRenderer, railTextObj(s));
	}

	/** 49-141차: 갈무리11 1배(목록·상자 글자). */
	private int gWidth(String s) {
		return LunaCompat.textWidth(textRenderer, railTextObj(s, RAIL_FONT_LG));
	}

	private void gText(DrawContext ctx, String s, int x, int boxY, int boxH, int color) {
		railText(ctx, s, RAIL_FONT_LG, x, boxY, boxH, color);
	}

	private String gFit(String s, int maxW) {
		if (s == null || gWidth(s) <= maxW) {
			return s == null ? "" : s;
		}
		String t = s;
		while (t.length() > 1 && gWidth(t + "…") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	/** 갈무리 잉크 세로 가운데. */
	private static float railCenter(String base) {
		return LunaCompat.textVisualCenter();   // 49-155차: 글꼴 설정을 따르므로 그 글꼴의 세로 가운데
	}

	private void railText(DrawContext ctx, String s, int x, int boxY, int boxH, int color) {
		railText(ctx, s, RAIL_FONT, x, boxY, boxH, color);
	}

	private void railText(DrawContext ctx, String s, String base, int x, int boxY, int boxH, int color) {
		int y = boxY + Math.round(boxH / 2f - LunaCompat.textVisualCenter(s));   // 49-172차: 한글이면 0.5px 아래 중심
		ctx.drawText(textRenderer, railTextObj(s, base), x, y, LunaDraw.applyAlpha(color), false);
	}


	/**
	 * 49-143차(사용자: "왼쪽 위 루나 글로 말고 로고+글 그거 쓰는 걸로"): 타이틀·일시정지 화면과 같은 로고 + 워드마크.
	 * 워드마크는 흰 그림이라 글자색으로 물들여 라이트 테마에서도 보인다. 그림을 못 그리면 예전 큰 글씨.
	 */
	private void renderBrand(DrawContext ctx) {
		// 49-190차(사용자: "기능에 우리 로고 더 크게"): 로고 20 → 28, 워드마크 최대 14 → 20(왼쪽 줄 폭 안에서)
		int logo = 28;
		int avail = dividerX - 10 - railX;
		int wordH = Math.max(8, Math.min(20, Math.round((avail - logo - 6) * 112f / 492f)));
		int ly = titleY + (TITLE_H - logo) / 2;
		boolean logoDrawn = LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/logo.png"),
			railX, ly, logo, logo, 0, 0, 128, 128, 128, LunaDraw.applyAlpha(0xFFFFFFFF));
		if (!logoDrawn) {
			LunaIcons.drawInBox(ctx, textRenderer, LunaIcons.LOGO, railX, ly, logo, ACC);
		}
		int wx = railX + logo + 6;
		if (LunaGfx.drawWordmark(ctx, wx, titleY + (TITLE_H - wordH) / 2, wordH, LunaDraw.applyAlpha(WHITE)) < 0) {
			gText(ctx, "NOVA", wx, titleY, TITLE_H, WHITE);
		}
	}

	/** 목록 오른쪽 숫자를 붙이는 탭(전체 + 카테고리). 설정 페이지·모드는 숫자 없음. */
	private static boolean railCounted(int tab) {
		return tab >= tabAll() && tab < tabModsIndex();
	}

	private void renderRail(DrawContext ctx, int mouseX, int mouseY) {
		// 49-226차(사진 시안): 분류마다 카드 한 장(아이콘 + 이름). 고른 카드는 테두리와 속이 테마색으로 물든다. 개수 표시는 뺐다
		// (사용자: "켜짐 개수 없애고").
		renderBrand(ctx);
		int cw = dividerX - 8 - railX;
		for (RailItem it : railItems()) {
			if (it.tab() < 0) {
				int ly = it.y() + RAIL_SEP_H / 2 - 1;
				ctx.fill(railX + 4, ly, railX + cw - 4, ly + 1, LunaDraw.applyAlpha(LINE));
				continue;
			}
			String label = tabLabel(it.tab());
			boolean sel = tabSelected(it.tab());
			boolean hovered = LunaDraw.in(mouseX, mouseY, railX - 4, it.y(), dividerX - railX, it.h());
			float hv = sel ? 1f : LunaDraw.anim("rail:" + it.tab(), hovered ? 1f : 0f, 18f);
			int ch = Math.max(10, it.h() - 4);
			glass(ctx, railX, it.y(), cw, ch, sel ? 0f : hv, sel ? 1f : 0f);
			int ic = sel ? ACC : LunaDraw.lerpColor(GRAY, WHITE, hv * 0.7f);
			LunaIcons.draw(ctx, textRenderer, tabIcon(it.tab()), railX + 6, LunaDraw.iconY(it.y(), ch), ic);
			gText(ctx, gFit(label, cw - 24), railX + 20, it.y(), ch, sel ? WHITE : LunaDraw.lerpColor(GRAY, WHITE, hv));
		}
		// 49-243차(사용자: "설정에서 ESC 닫기 글 없애줘"): 왼쪽 아래 "ESC 닫기" 줄 삭제(ESC 키는 그대로 닫힌다)
	}

	/** 49-226차: 판 안 왼쪽 아래 "ESC 닫기" 줄. */
	private int escY() {
		return panY + panH - 16;
	}

	// ------------------------------------------------------------- 오른쪽 위(검색 / 톱니)

	private int gearX() {
		return rightX + 9;
	}

	private int gearY() {
		return titleY + (TITLE_H - 12) / 2;
	}

	private int searchW() {
		return Math.min(140, Math.max(60, pw / 3));
	}

	private int searchX() {
		return rightX - searchW();
	}

	private boolean showSearch() {
		return openModule == null;
	}

	// 49-168차(사용자: "기능 큰 박스 작은 박스 그게 일반 설정이 아니라 검색 옆에 있어야지"): 기능 보기 전환은
	// 설정 항목이 아니라 검색 칸 왼쪽의 작은 3칸 스위치(큰 박스 / 작은 박스 / 상세). 값 저장은 여전히
	// InterfaceStyleModule의 숨은 설정(tile_view) - LunaTheme.setTileView로 넘긴다.
	private static final int VIEW_CELL_W = 16;
	private static final int VIEW_SW_H = 14;
	private static final int VIEW_SW_W = VIEW_CELL_W * 3;

	private int viewSwitchX() {
		return searchX() - 14 - VIEW_SW_W;
	}

	private int viewSwitchY() {
		return titleY + 5;
	}

	private void renderViewSwitch(DrawContext ctx, int mouseX, int mouseY) {
		int x = viewSwitchX();
		int y = viewSwitchY();
		card(ctx, x, y, VIEW_SW_W, VIEW_SW_H, 4, fieldColor(), LINE);   // 49-226차: 카드 칸
		for (int i = 0; i < 3; i++) {
			int cx = x + i * VIEW_CELL_W;
			boolean sel = VIEW == i;
			boolean hov = !sel && LunaDraw.in(mouseX, mouseY, cx, y, VIEW_CELL_W, VIEW_SW_H);
			if (sel) {
				LunaDraw.roundRect(ctx, cx + 1, y + 1, VIEW_CELL_W - 2, VIEW_SW_H - 2, 3, LunaDraw.withAlpha(ACC, 0x50));
			}
			int c = LunaDraw.applyAlpha(sel ? WHITE : hov ? GRAY : GRAY_DIM);
			int gx = cx + (VIEW_CELL_W - 8) / 2;
			int gy = y + (VIEW_SW_H - 8) / 2;
			switch (i) {
				case VIEW_LARGE -> {   // 2x2 큰 칸
					for (int r = 0; r < 2; r++) {
						for (int q = 0; q < 2; q++) {
							ctx.fill(gx + q * 4 + (q == 0 ? 0 : 1), gy + r * 4 + (r == 0 ? 0 : 1), gx + q * 4 + 3 + (q == 0 ? 0 : 1), gy + r * 4 + 3 + (r == 0 ? 0 : 1), c);
						}
					}
				}
				case VIEW_SMALL -> {   // 3x3 작은 칸
					for (int r = 0; r < 3; r++) {
						for (int q = 0; q < 3; q++) {
							ctx.fill(gx + q * 3, gy + r * 3, gx + q * 3 + 2, gy + r * 3 + 2, c);
						}
					}
				}
				default -> {   // 가로 줄 3개
					for (int r = 0; r < 3; r++) {
						ctx.fill(gx, gy + r * 3, gx + 8, gy + r * 3 + 2, c);
					}
				}
			}
		}
		if (LunaDraw.in(mouseX, mouseY, x, y, VIEW_SW_W, VIEW_SW_H)) {
			int i = Math.min(2, (mouseX - x) / VIEW_CELL_W);
			hoverTip = i == VIEW_LARGE ? "큰 박스" : i == VIEW_SMALL ? "작은 박스" : "상세";
		}
	}

	private void renderTopRight(DrawContext ctx, int mouseX, int mouseY) {
		boolean gh = LunaDraw.in(mouseX, mouseY, gearX() - 2, gearY() - 2, 16, 16);
		// 49-226차: 톱니도 네모 칸 버튼(사진 시안의 × 자리)
		card(ctx, gearX() - 4, gearY() - 4, 20, 20, 4,
			LunaDraw.lerpColor(CARD_HOV, LunaTheme.mix(CARD_HOV, WHITE, 0.06f), gh || actionMenuOpen ? 1f : 0f),
			actionMenuOpen ? LunaTheme.mix(LINE, ACC, 0.6f) : LINE_HOV);
		iconIn(ctx, LunaIcons.SETTINGS, gearX(), gearY(), 12, 12, actionMenuOpen ? ACC : gh ? WHITE : GRAY);
		if (!showSearch()) {
			return;
		}
		renderViewSwitch(ctx, mouseX, mouseY);
		int sx = searchX();
		int sw = searchW();
		int sy = titleY + 6;
		boolean hovered = LunaDraw.in(mouseX, mouseY, sx, sy - 2, sw, 16);
		// 49-226차: 밑줄 칸 → 카드 입력칸(속은 한 단계 깊게, 초점이면 테두리 테마색)
		card(ctx, sx - 5, titleY + 2, sw + 9, 20, 4, fieldColor(),
			searchFocused ? LunaTheme.mix(LINE, ACC, 0.7f) : hovered ? LINE_HOV : LINE);
		int fg = searchFocused ? WHITE : hovered ? GRAY : GRAY_DIM;
		LunaIcons.draw(ctx, textRenderer, LunaIcons.SEARCH, sx, LunaDraw.iconY(sy, 12), searchFocused ? ACC : fg);
		int tx = sx + ICON_INK + 5;
		if (search.isEmpty() && !searchFocused) {
			gText(ctx, "검색", tx, sy, 12, fg);
		} else {
			int avail = sw - (tx - sx) - 4;
			int cur = Math.max(0, Math.min(searchCursor, search.length()));
			fitSearchView(cur, avail);
			String shown = searchVisible(avail);
			gText(ctx, shown, tx, sy, 12, WHITE);
			if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = tx + gWidth(search.substring(searchViewStart, cur)) + (cur == search.length() ? 1 : 0);
				ctx.fill(cx, sy, cx + 1, sy + 12, LunaDraw.applyAlpha(WHITE));
			}
		}
	}

	/** 49-188차: 커서가 보이도록 보이는 시작 위치를 옮긴다(글이 칸보다 길 때만). */
	private void fitSearchView(int cur, int avail) {
		if (gWidth(search) <= avail) {
			searchViewStart = 0;
			return;
		}
		searchViewStart = Math.max(0, Math.min(searchViewStart, cur));
		while (searchViewStart < cur && gWidth(search.substring(searchViewStart, cur)) > avail - 2) {
			searchViewStart++;
		}
	}

	/** 보이는 시작 위치부터 칸에 들어가는 만큼. */
	private String searchVisible(int avail) {
		String t = search.substring(Math.min(searchViewStart, search.length()));
		while (t.length() > 0 && gWidth(t) > avail) {
			t = t.substring(0, t.length() - 1);
		}
		return t;
	}

	/** 마우스 x에 가장 가까운 글자 사이 위치. */
	private int searchIndexAt(double mouseX) {
		int tx = searchX() + ICON_INK + 5;
		int start = Math.min(searchViewStart, search.length());
		int best = start;
		double bestD = Double.MAX_VALUE;
		for (int i = start; i <= search.length(); i++) {
			double d = Math.abs(tx + gWidth(search.substring(start, i)) - mouseX);
			if (d < bestD) {
				bestD = d;
				best = i;
			}
		}
		return best;
	}

	private void searchInsert(String text) {
		int cur = Math.max(0, Math.min(searchCursor, search.length()));
		search = search.substring(0, cur) + text + search.substring(cur);
		searchCursor = cur + text.length();
		modsView = false;
		resetScroll();
	}

	/** Ctrl+←/→ 와 Ctrl+Backspace가 건너뛸 단어 경계(띄어쓰기 기준). */
	private int searchWordLeft(int cur) {
		int i = cur;
		while (i > 0 && search.charAt(i - 1) == ' ') {
			i--;
		}
		while (i > 0 && search.charAt(i - 1) != ' ') {
			i--;
		}
		return i;
	}

	private int searchWordRight(int cur) {
		int i = cur, n = search.length();
		while (i < n && search.charAt(i) == ' ') {
			i++;
		}
		while (i < n && search.charAt(i) != ' ') {
			i++;
		}
		return i;
	}

	// ------------------------------------------------------------- 제목 줄

	private record Btn(Action action, int x, int y, int w) {
	}

	private int actionBtnW(Action a) {
		return ICON_INK + 6 + LunaDraw.width(textRenderer, a.label()) + 16;
	}

	private int bigSwitchX() {
		return rightX - BIG_SW_W;
	}

	private int bigSwitchY() {
		return titleY + (TITLE_H - BIG_SW_H) / 2;
	}

	private boolean showBigSwitch() {
		return openModule != null && !openModule.isAlwaysOn() && openModule.isVersionSupported();
	}

	/** 제목 줄 오른쪽 작은 버튼들(오른쪽 끝에서 왼쪽으로). */
	private List<Btn> titleButtons() {
		List<Btn> out = new ArrayList<>();
		int right;
		if (openModule != null) {
			right = showBigSwitch() ? bigSwitchX() - 10 : rightX;
		} else {
			right = viewSwitchX() - 12;
		}
		int y = titleY + (TITLE_H - BTN_H) / 2;
		List<Action> acts = currentActions();
		for (int i = acts.size() - 1; i >= 0; i--) {
			Action a = acts.get(i);
			int w = actionBtnW(a);
			right -= w;
			out.add(0, new Btn(a, right, y, w));
			right -= 6;
		}
		return out;
	}

	private int backX() {
		return px;
	}

	private String countLabel() {
		if (modsView) {
			return installedMods().size() + "개";
		}
		if (openModule == null && pageView()) {
			return ModuleManager.get().byPage(settingsPage).size() + "개";
		}
		List<Module> list = currentModules();
		int on = 0;
		for (Module m : list) {
			if (m.isEnabled() && m.isVersionSupported()) {
				on++;
			}
		}
		return list.size() + "개 중 " + on + "개 켜짐";
	}

	private int titleRightEdge() {
		List<Btn> btns = titleButtons();
		return btns.isEmpty()
			? (openModule != null ? (showBigSwitch() ? bigSwitchX() - 10 : rightX) : searchX() - 12)
			: btns.get(0).x() - 10;
	}

	/** 49-143차: 기능 설정의 [← 이름] - 화살표 16px, 이름은 갈무리 1배. 이름을 눌러도 뒤로. */
	private static final int BACK_ICON = 16;
	/** 49-226차: [←] 네모 칸(20) + 아이콘 칸(20) + 간격 = 이름 앞 자리. */
	private static final int BACK_W = 20 + 5 + 20 + 7;

	private String detailName() {
		int nx = backX() + BACK_W;
		return gFit(openModule.getDisplayName(), titleRightEdge() - nx);
	}

	private int backHitW() {
		return BACK_W + gWidth(detailName()) + 6;
	}

	private void renderTitle(DrawContext ctx, int mouseX, int mouseY) {
		List<Btn> btns = titleButtons();
		int titleRight = titleRightEdge();
		if (openModule != null) {
			Module m = openModule;
			boolean bh = LunaDraw.in(mouseX, mouseY, backX() - 2, titleY, backHitW(), TITLE_H);
			// 49-195차(사용자: "세부설정 화살표가 다시 글이랑 위치가 안 맞음 - 화살표가 더 높음"): 이름은 문자열별 중심
			// (한글은 0.5px 아래)으로 놓이는데 화살표는 상자 정중앙이라 1px 높았다 - 화살표를 1px 내린다.
			// 49-226차(사진 시안): [←] 네모 칸 + 기능 아이콘 칸 + 이름
			int by = titleY + (TITLE_H - 20) / 2;
			card(ctx, backX(), by, 20, 20, 4, LunaDraw.lerpColor(CARD_HOV, LunaTheme.mix(CARD_HOV, WHITE, 0.06f), bh ? 1f : 0f),
				bh ? LunaTheme.mix(LINE, ACC, 0.6f) : LINE_HOV);
			LunaIcons.drawInBox(ctx, textRenderer, LunaIcons.BACK, backX(), by, 20, bh ? ACC : WHITE);   // 49-245차: 칸(20) 정가운데 - 예전 +2/+3은 1px 아래였다
			float mon = LunaDraw.anim("t:" + m.getId(), m.isEnabled() && m.isVersionSupported() ? 1f : 0f, 14f);
			iconTile(ctx, LunaIcons.forModule(m.getId(), m.getCategory()), backX() + 25, by, 20, mon,
				LunaDraw.lerpColor(GRAY, ACC, mon));
			int nx = backX() + BACK_W;
			gText(ctx, detailName(), nx, titleY, TITLE_H, !m.isVersionSupported() ? GRAY : bh ? ACC : WHITE);
			if (showBigSwitch()) {
				int sx = bigSwitchX();
				int sy = bigSwitchY();
				boolean sh = LunaDraw.in(mouseX, mouseY, sx - 3, sy - 3, BIG_SW_W + 6, BIG_SW_H + 6);
				btn3d(ctx, sx, sy, BIG_SW_W, BIG_SW_H, m.isEnabled() ? "켜짐" : "켜기", m.isEnabled(),
					LunaDraw.anim("bigsw", sh ? 1f : 0f, 18f));
			} else if (!m.isVersionSupported()) {
				String need = m.getVersionRequirementLabel();
				String lock = need == null ? "지원 안 함" : need + " 필요";
				LunaDraw.text(ctx, textRenderer, lock, rightX - LunaDraw.width(textRenderer, lock),
					LunaDraw.textY(titleY, TITLE_H), 0xCCF59E0B);
			}
		} else {
			// 49-232차(사용자: "로고 옆에 HUD 이런 거 뜨는 거 없애줘"): 목록 화면 큰 제목 삭제
			// 49-226차(사용자: "켜짐 개수 없애고"): 제목 옆 "n개 중 m개 켜짐" 삭제
		}
		for (Btn b : btns) {
			Action a = b.action();
			boolean hovered = LunaDraw.in(mouseX, mouseY, b.x(), b.y(), b.w(), BTN_H);
			float hv = LunaDraw.anim("act:" + a.label(), hovered ? 1f : 0f, 18f);
			glass(ctx, b.x(), b.y(), b.w(), BTN_H, a.accent() ? 2f : hv);
			int fg = a.accent() ? WHITE : LunaDraw.lerpColor(GRAY, WHITE, hv);
			LunaIcons.draw(ctx, textRenderer, a.icon(), b.x() + 7, LunaDraw.iconY(b.y(), BTN_H), a.accent() ? ACC : fg);
			LunaDraw.text(ctx, textRenderer, a.label(), b.x() + 7 + ICON_INK + 6, LunaDraw.textY(b.y(), BTN_H), fg);
		}
	}

	private void selectTab(int i) {
		// 49-169차(사용자: "같은 카테고리 눌러도 계속 애니메이션 나오는데 방지"): 이미 그 탭의 목록을 보고 있으면 아무것도 안 한다
		if (tabSelected(i) && openModule == null && !searching() && !actionMenuOpen) {
			return;
		}
		pageDir = openModule != null ? -1 : 0;
		modsView = i == tabModsIndex();
		settingsPage = modsView ? null : pageAt(i);
		if (!modsView && settingsPage == null) {
			selectedCategory = categoryAt(i);
			lastCategory = selectedCategory;
		}
		lastPage = settingsPage;
		lastModsView = modsView;
		openModule = null;
		returnOnBack = false;
		listScrollBeforeOpen = -1;
		openColor = null;
		editingHex = null;
		actionMenuOpen = false;
		search = "";
		searchFocused = false;
		resetScroll();
		LunaDraw.resetAnim("page:grid");
		LunaDraw.resetAnim("page:mods");
	}

	private void backToList() {
		if (returnOnBack) {
			returnOnBack = false;
			close();
			return;
		}
		pageDir = -1;
		openModule = null;
		openColor = null;
		editingHex = null;
		resetScroll();
		if (listScrollBeforeOpen > 0) {
			scroll = listScrollBeforeOpen;
			scrollTarget = listScrollBeforeOpen;
		}
		listScrollBeforeOpen = -1;
		LunaDraw.resetAnim("page:grid");
	}

	/** 목록·위쪽·제목 줄 클릭. 처리했으면 true. */
	private boolean handleChromeClick(double mouseX, double mouseY) {
		// 왼쪽 목록
		if (mouseX < dividerX) {
			for (RailItem it : railItems()) {
				if (it.tab() >= 0 && LunaDraw.in(mouseX, mouseY, railX - 4, it.y(), dividerX - railX, it.h())) {
					selectTab(it.tab());
					return true;
				}
			}
			// 49-243차: 왼쪽 아래 "ESC 닫기" 줄을 없애서 누를 곳도 없다
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, gearX() - 2, gearY() - 2, 16, 16)) {
			actionMenuOpen = !actionMenuOpen;
			return true;
		}
		if (mouseY >= contentY - 4) {
			return false;
		}
		if (showSearch() && LunaDraw.in(mouseX, mouseY, searchX() - 2, titleY + 2, searchW() + 2, 18)) {
			modsView = false;
			// 49-188차: 이미 글이 있으면 누른 자리에 커서, 처음 누르면 끝에
			searchCursor = searchFocused ? searchIndexAt(mouseX) : search.length();
			searchFocused = true;
			return true;
		}
		if (showSearch() && LunaDraw.in(mouseX, mouseY, viewSwitchX(), viewSwitchY(), VIEW_SW_W, VIEW_SW_H)) {
			int i = Math.min(2, (int) ((mouseX - viewSwitchX()) / VIEW_CELL_W));
			LunaTheme.setTileView(i);
			applyTileMode();
			return true;
		}
		for (Btn b : titleButtons()) {
			if (LunaDraw.in(mouseX, mouseY, b.x(), b.y(), b.w(), BTN_H)) {
				b.action().run().run();
				return true;
			}
		}
		if (openModule != null) {
			if (LunaDraw.in(mouseX, mouseY, backX() - 2, titleY, backHitW(), TITLE_H)) {
				backToList();
				return true;
			}
			if (showBigSwitch() && LunaDraw.in(mouseX, mouseY, bigSwitchX() - 3, bigSwitchY() - 3, BIG_SW_W + 6, BIG_SW_H + 6)) {
				ModuleManager.get().toggleWithConflictResolution(openModule);
				return true;
			}
		}
		return true;
	}

	@Override
	public void close() {
		LunaClientConfig.save();
		if (this.client != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
		}
	}

	// =====================================================================
	// 데이터
	// =====================================================================

	private boolean searching() {
		return !search.trim().isEmpty();
	}

	private List<Module> currentModules() {
		List<Module> list = new ArrayList<>();
		if (searching()) {
			String q = search.trim().toLowerCase();
			// 49-188차(사용자: "띄어쓰기 안 해도 인식되게"): 띄어쓰기와 밑줄을 빼고 비교(LunaHangul.matches도 띄어쓰기를 무시)
			String qId = q.replaceAll("[\\s_]+", "");
			for (Module m : ModuleManager.get().all()) {
				// 49-61차: 격자에도 설정 페이지에도 안 실리는 모듈은 검색에도 안 나온다
				// ("설정할 수 없어야 하는 것"이 검색으로만 열리면 숨긴 게 아니다).
				if (m.hiddenInList() && m.getPage() == null) {
					continue;
				}
				// 49-170차: 영타(dlsqps)도 한글(인벤)로 본다 - LunaHangul
				if (LunaHangul.matches(m.getDisplayName(), q) || LunaHangul.matches(m.getDescription(), q)
					|| (!qId.isEmpty() && m.getId().toLowerCase().replace("_", "").contains(qId))) {
					list.add(m);
				}
			}
		} else {
			list.addAll(ModuleManager.get().byCategory(selectedCategory));
			if (selectedCategory == null) {
				// 49-167차(사용자: "서버 기능은 전체에서 안 보이게"): [전체]에는 서버 분류 기능이 안 나온다(서버 탭에서만)
				list.removeIf(m -> m.getCategory() == ModuleCategory.SERVER);
			}
			if (serverView()) {
				// 49-142차: 서버 분류는 고른 서버의 기능만
				String g = currentServerGroup();
				list.removeIf(m -> !java.util.Objects.equals(groupName(m), g));
			}
		}
		return list;
	}

	// ------------------------------------------------------------- 49-142차: 서버 분류
	// 사용자: "너굴마을 같은 서버 전용 기능은 따로 서버 기능 분류하고 거기서 또 서버별로 분류해서 하자"
	// 서버 칩(너굴마을 …) → 서버 카드(이름, 주소, 접속 중, 켜진 개수) → 그 서버 기능 상자. 칩과 카드는 상자와 함께 스크롤된다.

	private String serverSel;
	private static final int SCHIP_H = 16;
	private static final int SCARD_H = 46;

	private boolean serverView() {
		return !searching() && !modsView && settingsPage == null && selectedCategory == ModuleCategory.SERVER;
	}

	private static String groupName(Module m) {
		String g = m.serverGroup();
		return g == null || g.isEmpty() ? "기타" : g;
	}

	private List<String> serverGroups() {
		java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
		for (Module m : ModuleManager.get().byCategory(ModuleCategory.SERVER)) {
			out.add(groupName(m));
		}
		return new ArrayList<>(out);
	}

	private String currentServerGroup() {
		List<String> gs = serverGroups();
		if (serverSel != null && gs.contains(serverSel)) {
			return serverSel;
		}
		return gs.isEmpty() ? null : gs.get(0);
	}

	private int serverHeadH() {
		return serverView() && !serverGroups().isEmpty() ? SCHIP_H + 6 + SCARD_H + 10 : 0;
	}

	/** 서버 이름 → {주소, 주소에 들어 있으면 접속 중으로 보는 낱말}. 모르는 서버는 null. */
	private static String[] serverInfo(String group) {
		return switch (group) {
			case "너굴마을" -> new String[]{"mcng.kr", "mcng"};
			default -> null;
		};
	}

	private int serverChipW(String name) {
		return gWidth(name) + 16;
	}

	private void renderServerHead(DrawContext ctx, int mouseX, int mouseY, int top) {
		String cur = currentServerGroup();
		int x = contentX;
		for (String g : serverGroups()) {
			int w = serverChipW(g);
			boolean sel = g.equals(cur);
			boolean hov = LunaDraw.in(mouseX, mouseY, x, top, w, SCHIP_H);
			LunaDraw.roundRect(ctx, x, top, w, SCHIP_H, 4, sel ? ACC : hov ? LINE_HOV : LINE);
			LunaDraw.roundRect(ctx, x + 1, top + 1, w - 2, SCHIP_H - 2, 3, sel ? LunaDraw.lerpColor(CARD_C, ACC, 0.12f) : CARD_C);
			gText(ctx, g, x + 8, top, SCHIP_H, sel ? WHITE : hov ? WHITE : GRAY);
			x += w + 5;
		}
		if (cur == null) {
			return;
		}
		int cy = top + SCHIP_H + 6;
		glass(ctx, contentX, cy, contentW, SCARD_H, 0f);
		LunaIcons.drawInBox(ctx, textRenderer, LunaIcons.WIFI, contentX + 8, cy + 7, 16, ACC);
		bigText(ctx, bigEllipsize(cur, contentW - 140), contentX + 30, cy + 4, 22, WHITE);
		String[] info = serverInfo(cur);
		String addr = LunaCompat.currentServerAddress(this.client);
		boolean here = info != null && addr != null && addr.toLowerCase(java.util.Locale.ROOT).contains(info[1]);
		List<Module> mods = currentModules();
		int on = 0;
		for (Module m : mods) {
			if (m.isEnabled()) {
				on++;
			}
		}
		String line = (info != null ? info[0] + "  |  " : "") + "기능 " + mods.size() + "개 중 " + on + "개 켜짐";
		gText(ctx, gFit(line, contentW - 40), contentX + 30, cy + 29, 12, GRAY);
		String st = here ? "접속 중" : "접속 안 함";
		int sw = gWidth(st);
		int sx = contentX + contentW - 10 - sw;
		gText(ctx, st, sx, cy + 8, 12, here ? ACC : GRAY_DIM);
		int dy = cy + 13;
		ctx.fill(sx - 7, dy, sx - 3, dy + 4, LunaDraw.applyAlpha(here ? ACC : GRAY_DIM));
	}

	private boolean handleServerHeadClick(double mouseX, double mouseY) {
		if (serverHeadH() <= 0) {
			return false;
		}
		int top = contentY - (int) scroll;
		int x = contentX;
		for (String g : serverGroups()) {
			int w = serverChipW(g);
			if (LunaDraw.in(mouseX, mouseY, x, top, w, SCHIP_H)) {
				serverSel = g;
				pageDir = 0;
				LunaDraw.resetAnim("page:grid");
				return true;
			}
			x += w + 5;
		}
		return mouseY < top + serverHeadH();
	}

	/** 설정을 어느 그룹에 넣을지. 스타일(모양) / 나머지 기능(키 포함). */
	private static int groupOf(Setting<?> s) {
		String id = s.getId();
		if (s.isStyle() || s instanceof PositionSetting || s instanceof ColorSetting
			|| id.startsWith("hud_bg") || id.endsWith("_color") || id.equals("scale")) {
			return G_STYLE;
		}
		return G_MAIN;
	}

	private List<Setting<?>> groupSettings(Module m, int group) {
		List<Setting<?>> list = new ArrayList<>();
		for (Setting<?> s : visibleSettings(m)) {
			if (groupOf(s) == group && !isLinkedColor(m, s)) {
				list.add(s);
			}
		}
		return list;
	}

	/** 49-23차: 어떤 스위치 줄에 붙어 그려지는 색 설정인지(그러면 따로 줄을 만들지 않음). */
	private static boolean isLinkedColor(Module m, Setting<?> s) {
		if (!(s instanceof ColorSetting)) {
			return false;
		}
		for (Setting<?> o : visibleSettings(m)) {
			if (o instanceof BooleanSetting b && b.getLinkedColor() == s) {
				return true;
			}
		}
		return false;
	}

	/** 49-23차: 스위치 줄에 붙은 색 견본의 x(스위치 왼쪽). */
	private static int linkedSwatchX(int right) {
		return right - TOGGLE_W - 8 - LINK_SW_W;
	}

	private static final int LINK_SW_W = 30; // 스위치 옆 색 견본 폭

	private boolean isCollapsed(Module m, int group) {
		return collapsed.contains(m.getId() + "/" + group);
	}

	/** 49-124차: 이름 붙은 상세 묶음의 접힘 상태(라벨로 구분 - 묶음 순서가 바뀌어도 안 흔들린다). */
	private boolean isGroupCollapsed(Module m, String label) {
		return collapsed.contains(m.getId() + "#" + label);
	}

	/** 설치된 모드 한 줄 분량의 정보. 49-23차: 라이브러리 여부 + 설정 화면(Mod Menu API) 유무. */
	private record ModInfo(String id, String name, String version, String description, String authors,
			boolean library, boolean configurable) {
	}

	/** FabricLoader에서 설치된 모드를 읽어옴(내용이 안 바뀌므로 캐시). */
	private List<ModInfo> installedMods() {
		if (modCache != null && modCacheLibs == showLibraries) {
			return modCache;
		}
		List<ModInfo> list = new ArrayList<>();
		try {
			for (var container : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
				var meta = container.getMetadata();
				String id = meta.getId();
				// 49-23차: 중첩 jar/Mod Menu 배지/알려진 id/메이븐식 id → 라이브러리로 분류(기본 숨김)
				boolean library = kr.lunaslight.mod.util.ModListSupport.isLibrary(container);
				if (!showLibraries && library) {
					continue;
				}
				String authors;
				try {
					List<String> names = new ArrayList<>();
					meta.getAuthors().forEach(person -> names.add(person.getName()));
					authors = names.isEmpty() ? "-" : String.join(", ", names);
				} catch (Throwable ignored) {
					authors = "-";
				}
				list.add(new ModInfo(id, meta.getName(), meta.getVersion().getFriendlyString(),
					meta.getDescription(), authors, library, kr.lunaslight.mod.util.ModListSupport.hasConfig(id)));
			}
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("modList", t);
		}
		list.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
		modCache = list;
		modCacheLibs = showLibraries;
		if (selectedMod >= list.size()) {
			selectedMod = 0;
		}
		return list;
	}

	private static final int VOPT_H = 24; // 49-47차: 세로 목록형 EnumSetting의 한 항목 높이
	private static final int KENTRY_H = 26; // 49-103차: 키바인드 목록 한 항목 높이(명령어칸 + 키 버튼 + ×)
	private static final int PCHIP_H = 20;  // 49-110차: 가격표 표 칩 줄 높이
	private static final int PENTRY_H = 26; // 49-110차: 가격표 한 항목 높이(아이콘 + 이름 + 가격칸 + ×)

	/** 49-76차: 화면에 그리는 설정만(hidden()은 저장만 되고 안 보인다). */
	private static java.util.List<Setting<?>> visibleSettings(Module m) {
		java.util.List<Setting<?>> out = new java.util.ArrayList<>();
		for (Setting<?> s : m.getSettings()) {
			if (!s.isHidden()) {
				out.add(s);
			}
		}
		return out;
	}

	private int rowHeight(Setting<?> setting) {
		if (setting instanceof EnumSetting<?> es && es.isVertical()) {
			return ROW_H + es.getOptions().length * VOPT_H + 4;
		}
		// 49-76차(6-8): 목록형 글자 설정은 항목마다 한 줄(세로 선택지와 같은 높이)
		if (setting instanceof StringSetting ss && ss.isList()) {
			int n = ss.items().size();
			return ROW_H + (n == 0 ? 0 : n * VOPT_H + 4);
		}
		// 49-103차: 키바인드 목록 - 머리줄(추가하기) + 항목마다 한 줄. 빈 목록도 4px 여백을 둬 반반 배치에 안 끼게 한다.
		if (setting instanceof KeybindListSetting kl) {
			return ROW_H + kl.size() * KENTRY_H + 4;
		}
		// 49-110차: 가격표 등록 - 머리줄(추가하기) + 표 칩 줄 + 항목마다 한 줄.
		// 49-122차(사용자: "아래 따로 박스로 · 글이 튀어나옴"): 항목이 없어도 안내 한 줄 자리를 두고(안 그러면
		// 안내 글이 행 높이 밖으로 삐져나온다) 박스 여백까지 포함한다.
		if (setting instanceof PriceListSetting pl) {
			return ROW_H + PCHIP_H + Math.max(1, pl.entries().size()) * PENTRY_H + 8;
		}
		if (setting instanceof ColorSetting && setting == openColor) {
			return ROW_H + COLOR_EDITOR_H;
		}
		if (setting instanceof BooleanSetting b && b.getLinkedColor() != null && b.getLinkedColor() == openColor) {
			return ROW_H + COLOR_EDITOR_H;
		}
		return ROW_H;
	}

	/** 49-22차: 모듈이 스스로 결정(위치 설정이 없어도 예시를 그릴 수 있게 - Module.hasPreview). */
	private static boolean hasPreview(Module m) {
		return m.hasPreview();
	}

	private static PositionSetting positionOf(Module m) {
		for (Setting<?> s : visibleSettings(m)) {
			if (s instanceof PositionSetting ps) {
				return ps;
			}
		}
		return null;
	}

	// =====================================================================
	// 렌더
	// =====================================================================

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		applyScreenTheme();
		applyTileMode();
		vOk = LunaCompat.guiTransformSupported(ctx);
		vScisPose = LunaCompat.guiScissorFollowsPose(ctx) || kr.lunaslight.mod.util.LunaVersion.isWithin("1.21.4", null);
		vMouseX = mouseX;
		vMouseY = mouseY;
		boolean e = vEnter();
		try {
			if (e) {
				LunaCompat.guiPush(ctx);
				LunaCompat.guiScale(ctx, 1f / vK, 1f / vK);
				LunaGfx.roundPx = vPx;
				try {
					renderFrame(ctx, (int) Math.floor((mouseX + 0.5) * vK), (int) Math.floor((mouseY + 0.5) * vK), delta);
				} finally {
					LunaGfx.roundPx = 0f;
					LunaCompat.guiPop(ctx);
				}
				if (hoverTip != null && !hoverTip.isEmpty()) {
					ctx.drawTooltip(textRenderer, LunaGfx.text(hoverTip), mouseX, mouseY);
				}
			} else {
				renderFrame(ctx, mouseX, mouseY, delta);
			}
		} finally {
			vExit(e);
			restoreScreenTheme();
		}
	}

	// ------------------------------------------------------------ 49-232차: GUI 배율과 무관한 크기
	// 사용자: "GUI 비율 바뀌어도 안 달라지게, 크기는 GUI 2 기준". 화면 픽셀 2칸 = 1단위인 가상 좌표계로 그리고
	// (글자 픽셀이 늘 2배라 GUI 3에서도 선명), 입력 좌표도 같은 배율로 바꿔 넘긴다. 창이 아주 작으면 1단위까지 줄인다.
	private boolean vOn, vOk, vScisPose;
	private float vK = 1f, vPx = 2f;
	private int vRealW, vRealH, vMouseX, vMouseY;

	/** 실제 GUI 배율 / 가상 배율(1이면 바꿀 것 없음). */
	private float vCalcK() {
		if (!vOk) {
			return 1f;
		}
		double g;
		try {
			g = WindowAccess.of(this.client).getScaleFactor();
		} catch (Throwable t) {
			return 1f;
		}
		if (!(g > 0.0)) {
			return 1f;
		}
		// 49-247차(사용자: "마크 창이 작아지면 GUI도 조절"): 640x360단위가 안 들어가면 줄인다(예전 440x250은 창을 꽤 줄여도 그대로라 화면이 비좁아졌다)
		double px = Math.max(1.0, Math.min(2.0, Math.min(vRealW * g / 640.0, vRealH * g / 360.0)));
		vPx = (float) px;
		float k = (float) (g / px);
		return Math.abs(k - 1f) < 0.01f ? 1f : k;
	}

	/** 가상 크기로 들어간다(이미 들어가 있거나 바꿀 게 없으면 false). */
	private boolean vEnter() {
		if (vOn) {
			return false;
		}
		vRealW = width;
		vRealH = height;
		vK = vCalcK();
		if (vK == 1f) {
			return false;
		}
		width = Math.round(vRealW * vK);
		height = Math.round(vRealH * vK);
		vOn = true;
		updateLayout();
		return true;
	}

	private void vExit(boolean entered) {
		if (entered) {
			vOn = false;
			width = vRealW;
			height = vRealH;
		}
	}

	/** 가위 자르기 - 이 버전 가위가 행렬을 안 따르면(1.21.3 이하) 가상 좌표를 실제 좌표로 바꿔 준다. */
	private void scis(DrawContext ctx, int x1, int y1, int x2, int y2) {
		if (vOn && !vScisPose) {
			ctx.enableScissor((int) Math.floor(x1 / vK), (int) Math.floor(y1 / vK), (int) Math.ceil(x2 / vK), (int) Math.ceil(y2 / vK));
		} else {
			ctx.enableScissor(x1, y1, x2, y2);
		}
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = vEnter();
		double k = e ? vK : 1.0;
		try {
			return lunaMouseClicked0(mouseX * k, mouseY * k, button);
		} finally {
			vExit(e);
		}
	}

	@Override
	protected boolean lunaMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		boolean e = vEnter();
		double k = e ? vK : 1.0;
		try {
			return lunaMouseDragged0(mouseX * k, mouseY * k, button, deltaX * k, deltaY * k);
		} finally {
			vExit(e);
		}
	}

	@Override
	protected boolean lunaMouseReleased(double mouseX, double mouseY, int button) {
		boolean e = vEnter();
		double k = e ? vK : 1.0;
		try {
			return lunaMouseReleased0(mouseX * k, mouseY * k, button);
		} finally {
			vExit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		boolean e = vEnter();
		double k = e ? vK : 1.0;
		try {
			return lunaMouseScrolled0(mouseX * k, mouseY * k, verticalAmount);
		} finally {
			vExit(e);
		}
	}

	@Override
	protected boolean lunaKeyReleased(int keyCode, int scanCode, int modifiers) {
		boolean e = vEnter();
		try {
			return lunaKeyReleased0(keyCode, scanCode, modifiers);
		} finally {
			vExit(e);
		}
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = vEnter();
		try {
			return lunaKeyPressed0(keyCode, scanCode, modifiers);
		} finally {
			vExit(e);
		}
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		boolean e = vEnter();
		try {
			return lunaCharTyped0(chr);
		} finally {
			vExit(e);
		}
	}

	private void renderFrame(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderDelta = delta;
		hoverTip = null;
		bigOk = LunaCompat.guiTransformSupported(ctx);
		updateLayout();
		scroll += (scrollTarget - scroll) * Math.min(1f, LunaDraw.dt() * 18f);
		if (Math.abs(scrollTarget - scroll) < 0.3) {
			scroll = scrollTarget;
		}
		float open = LunaDraw.animFrom("panel", 0f, 1f, 16f);
		LunaDraw.setAlpha(open);
		try {
			renderAll(ctx, mouseX, mouseY);
		} finally {
			LunaDraw.setAlpha(1f);
		}
		if (!vOn && hoverTip != null && !hoverTip.isEmpty()) {
			ctx.drawTooltip(textRenderer, LunaGfx.text(hoverTip), mouseX, mouseY);
		}
	}

	private float renderDelta;

	private void renderAll(DrawContext ctx, int mouseX, int mouseY) {
		// 게임을 흐리고(1.21+ 바닐라 블러, 안 되는 버전은 어두운 막) 한 번 더 어둡게 - 글자가 어떤 장면 위에서도 읽히게
		if (vOn) {
			// 49-232차: 배경(블러/어두운 막)은 실제 화면 크기로 - 잠깐 행렬과 크기를 원래대로
			LunaCompat.guiPop(ctx);
			int sw = width, sh = height;
			width = vRealW;
			height = vRealH;
			try {
				LunaGfx.drawScreenBackdrop(this, ctx, vRealW, vRealH, vMouseX, vMouseY, renderDelta, LunaDraw.applyAlpha(LunaDraw.OVERLAY));
			} finally {
				width = sw;
				height = sh;
				LunaCompat.guiPush(ctx);
				LunaCompat.guiScale(ctx, 1f / vK, 1f / vK);
			}
		} else {
			LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, renderDelta, LunaDraw.applyAlpha(LunaDraw.OVERLAY));
		}
		// 49-226차(사진 시안): 판 바깥은 한 번 더 어둡게, 가운데 불투명 판(아래 두께 3px + 테두리) + 머리 줄 아래 선 + 세로 구분선
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((BG & 0x00FFFFFF) | (LIGHT ? 0x73000000 : 0x8C000000)));
		if (LIGHT) {
			// 49-239차: 크림 - 크게 둥근 판 + 옅은 그림자 세 겹(아래 두께 없음)
			LunaDraw.roundRect(ctx, panX - 4, panY - 1, panW + 8, panH + 10, 15, 0x105A4630);
			LunaDraw.roundRect(ctx, panX - 2, panY + 1, panW + 4, panH + 5, 13, 0x165A4630);
			LunaDraw.roundRect(ctx, panX - 1, panY + 1, panW + 2, panH + 2, 12, 0x1E5A4630);
			LunaDraw.roundRect(ctx, panX, panY, panW, panH, 11, 0xFFE3D0AE);
			LunaDraw.roundRect(ctx, panX + 1, panY + 1, panW - 2, panH - 2, 10, BG);
		} else if (LunaDraw.skinPanel(ctx, panX, panY, panW, panH, 7)) {
			// 49-279차: 미드나잇(보라 빛 번짐 + 세로 그라데이션) / 네온(시안 2px 테두리 + 빛 + 잘린 모서리)
		} else {
			LunaDraw.roundRect(ctx, panX - 2, panY, panW + 4, panH + 7, 8, 0x38000000);
			LunaDraw.roundRect(ctx, panX, panY + 3, panW, panH, 7, edgeColor());
			LunaDraw.roundRect(ctx, panX, panY, panW, panH, 7, LINE_HOV);
			LunaDraw.roundRect(ctx, panX + 1, panY + 1, panW - 2, panH - 2, 6, BG);
		}
		ctx.fill(panX + 1, headLineY, panX + panW - 1, headLineY + 1, LunaDraw.applyAlpha(LINE));
		ctx.fill(dividerX, headLineY + 1, dividerX + 1, panY + panH - 1, LunaDraw.applyAlpha(LINE));

		// 왼쪽 목록은 제자리. 49-169차: 기능을 열면 본문이 오른쪽에서 밀려 들어오고(목록으로 돌아가면 왼쪽에서),
		// 탭을 바꾸면 페이드만. 예전(위로 5px 떠오름)은 뺐다.
		renderRail(ctx, mouseX, mouseY);
		float page = LunaDraw.animFrom("page:" + (modsView ? "mods"
			: openModule != null ? openModule.getId()
			: pageView() ? "page:" + settingsPage.name() : "grid"), 0f, 1f, 20f);
		int slide = Math.round((1f - page) * 28f * pageDir);
		float prevAlpha = LunaDraw.alpha();
		LunaDraw.setAlpha(prevAlpha * (0.4f + 0.6f * page));
		renderTopRight(ctx, mouseX, mouseY);
		renderTitle(ctx, mouseX, mouseY);
		contentX += slide;
		try {
			if (modsView) {
				renderModList(ctx, mouseX, mouseY);
			} else if (openModule != null) {
				if (hasPreview(openModule)) {
					renderStage(ctx, mouseX, mouseY);
				}
				renderSettings(ctx, mouseX, mouseY);
			} else if (pageView()) {
				renderPage(ctx, mouseX, mouseY);
			} else {
				renderGrid(ctx, mouseX, mouseY);
			}
		} finally {
			contentX -= slide;
			LunaDraw.setAlpha(prevAlpha);
		}

		renderDropdown(ctx, mouseX, mouseY);
		if (actionMenuOpen) {
			renderActionMenu(ctx, mouseX, mouseY);
		}
		if (pickingPrices != null) {
			renderPricePicker(ctx, mouseX, mouseY);
		} else if (listeningKeybind != null) {
			renderKeybindModal(ctx, mouseX, mouseY);
		} else if (confirmReset) {
			renderResetModal(ctx, mouseX, mouseY);
		}
		// 49-124차: 키 삭제 확인 창은 키 지정 모달 위에 겹쳐 뜬다 - 맨 마지막에 그린다.
		keyConfirm.render(ctx, textRenderer, width, height, mouseX, mouseY);
		maybeCreamNotice();
		creamConfirm.render(ctx, textRenderer, width, height, mouseX, mouseY);
	}

	/**
	 * 49-257차(사용자: "처음 크림 UI로 들어왔을 때 알려주고"): 크림 화면 스킨으로 이 화면을 처음 연 때 한 번만, HUD도 크림 상자로
	 * 바꿀 수 있다고 알린다([바꾸기] = UI 설정 HUD 배경을 크림으로). 띄웠다는 표시는 HUD 배경 기능의 숨은 설정에 저장.
	 */
	private void maybeCreamNotice() {
		if (creamChecked || !LunaDraw.soft()) {
			return;
		}
		creamChecked = true;
		kr.lunaslight.mod.module.impl.misc.HudBackgroundModule hb = kr.lunaslight.mod.module.impl.misc.HudBackgroundModule.instance;
		if (hb == null || !hb.creamNoticePending()) {
			return;
		}
		hb.markCreamNoticeShown();
		LunaClientConfig.save();
		if (hb.isCream()) {
			return;
		}
		creamConfirm.showInfo(LunaIcons.PALETTE, "크림 HUD도 쓸 수 있어요", "화면에 띄우는 정보도 크림 상자로 바뀝니다",
			"UI 설정 > HUD 배경에서 언제든 바꿀 수 있어요", "바꾸기", "나중에", () -> {
				hb.useCream();
				LunaClientConfig.save();
			});
	}

	// ------------------------------------------------------------- 모달(49-21차)

	private static final int KB_W = 224, KB_H = 100;   // 49-172차: 키캡 자리만큼 조금 더 높게
	private static final int RS_W = 250, RS_H = 92;

	private int modalX(int w) {
		return (width - w) / 2;
	}

	private int modalY(int h) {
		return (height - h) / 2;
	}

	/** 모달 안 작은 버튼(호버 보간 포함). accent=true면 연두 강조, danger=true면 빨강. */
	private void modalButton(DrawContext ctx, String key, String label, int x, int y, int w, int h,
			int mouseX, int mouseY, boolean accent, boolean danger) {
		boolean hovered = LunaDraw.in(mouseX, mouseY, x, y, w, h);
		float hov = LunaDraw.anim("mb:" + key, hovered ? 1f : 0f, 18f);
		// 49-227차: 공용 입체 버튼(강조 = 테마색, 위험 = 빨강)
		int kind = danger ? LunaDraw.B_DANGER : accent ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL;
		LunaDraw.button3d(ctx, textRenderer, x, y, w, h, label, kind, hov);
	}

	/**
	 * 키 지정 모달 - 49-21차(사용자: "X 눌러서 닫을 수 있게, ESC는 키 지정 없애기, 초기화 따로"):
	 * 우상단 X = 취소(그대로 둠), ESC/Backspace = 해제, [기본값] = 그 키의 기본값으로.
	 */
	private void renderKeybindModal(DrawContext ctx, int mouseX, int mouseY) {
		ctx.fill(0, 0, width, height, 0xA6000000);
		float pop = LunaDraw.animFrom("modal:kb", 0f, 1f, 18f);
		int bx = modalX(KB_W), by = modalY(KB_H) + Math.round((1f - pop) * 10f);
		if (pendingConflict != null) {
			renderConflictModal(ctx, mouseX, mouseY, bx, by);
			return;
		}
		LunaDraw.panel3d(ctx, bx, by, KB_W, KB_H, 8);   // 49-227차: 사진 시안 판
		// X
		boolean xHover = LunaDraw.in(mouseX, mouseY, bx + KB_W - 24, by + 6, 18, 18);
		LunaDraw.roundRectBordered(ctx, bx + KB_W - 24, by + 6, 18, 18, 4,
			xHover ? 0x33CF7B74 : 0x14CF7B74, xHover ? 0x8CCF7B74 : 0x2ECF7B74);
		LunaIcons.draw(ctx, textRenderer, LunaIcons.CLOSE, bx + KB_W - 24 + 4, LunaDraw.iconY(by + 6, 18),
			xHover ? 0xFFFF8B82 : 0xFFCF7B74);

		LunaIcons.draw(ctx, textRenderer, LunaIcons.KEYBOARD, bx + 12, LunaDraw.iconY(by + 6, 18), LunaDraw.ACCENT);
		LunaDraw.text(ctx, textRenderer, listeningKeybind.getDisplayName(), bx + 12 + LunaIcons.SIZE + 4, LunaDraw.textY(by + 6, 18), LunaDraw.TEXT);
		// 49-124차(사용자: "조합 가능 이딴 거 싹 지우고 한줄에 하나씩"): 조합키 표시/기능 제거.
		// 49-172차(사용자: "키 지정 키보드 키캡처럼 느끼게"): 가운데에 두께가 있는 키캡 하나 - 지금 키 이름이 새겨져 있고
		// 기다리는 동안 테마색 테두리가 숨 쉬듯 깜빡인다. 그 아래 안내 한 줄.
		String keyName = listeningKeybind.getKeyName();
		boolean unbound = keyName == null || keyName.isEmpty() || "없음".equals(keyName);
		String cap = unbound ? "…" : keyName;
		int capW = Math.max(64, LunaDraw.width(textRenderer, cap) + 28);
		int capH = 30, depth = 4;
		int kx = bx + (KB_W - capW) / 2, ky = by + 30;
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
		int face = LunaDraw.lerpColor(0xFF2B3038, LunaDraw.withAlpha(LunaDraw.ACCENT, 0xFF), 0.10f + 0.12f * pulse);
		int side = LunaDraw.lerpColor(face, 0xFF000000, 0.5f);
		LunaDraw.roundRect(ctx, kx, ky + depth, capW, capH - depth, 5, side);
		LunaDraw.roundRect(ctx, kx, ky, capW, capH - depth, 5, face);
		LunaDraw.roundRectBorderedFlat(ctx, kx, ky, capW, capH - depth, 5, 0x00000000, LunaDraw.withAlpha(LunaDraw.ACCENT, Math.round(0x50 + 0x80 * pulse)));
		ctx.fill(kx + 4, ky + 1, kx + capW - 4, ky + 2, 0x50FFFFFF);
		LunaDraw.textCentered(ctx, textRenderer, cap, kx + capW / 2, LunaDraw.textY(ky, capH - depth), 0xFFF2F4F6);
		if (kbBlockedMsg != null && System.currentTimeMillis() < kbBlockedUntil) {
			LunaDraw.textCentered(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, kbBlockedMsg, KB_W - 20),
				bx + KB_W / 2, by + KB_H - 22, 0xFFFF8B82);
		} else {
			LunaDraw.textCentered(ctx, textRenderer, "설정할 키를 누르세요  §8|  §7삭제: ESC", bx + KB_W / 2, by + KB_H - 22, LunaDraw.TEXT_DIM);
		}
	}

	/** 49-22차: 마크(또는 다른 모드) 키와 겹칠 때 확인 - [그래도 지정] / [다른 키]. */
	private void renderConflictModal(DrawContext ctx, int mouseX, int mouseY, int bx, int by) {
		LunaDraw.panel3d(ctx, bx, by, KB_W, KB_H, 8);   // 49-227차: 사진 시안 판
		LunaIcons.draw(ctx, textRenderer, LunaIcons.KEYBOARD, bx + 12, LunaDraw.iconY(by + 6, 18), 0xFFF59E0B);
		LunaDraw.text(ctx, textRenderer, "키가 겹침", bx + 12 + LunaIcons.SIZE + 4, LunaDraw.textY(by + 6, 18), LunaDraw.TEXT);
		String keyName = LunaCompat.keyDisplayName(pendingKey);
		LunaDraw.textCentered(ctx, textRenderer, LunaDraw.ellipsize(textRenderer,
			keyName + " = 마크 '" + pendingConflict + "'", KB_W - 20), bx + KB_W / 2, by + 34, LunaDraw.TEXT);
		LunaDraw.textCentered(ctx, textRenderer, "그래도 이 키로 지정할까요?", bx + KB_W / 2, by + 48, LunaDraw.TEXT_DIM);
		modalButton(ctx, "kbforce", "그래도 지정", bx + KB_W / 2 - 74, by + KB_H - 26, 70, 18, mouseX, mouseY, true, false);
		modalButton(ctx, "kbother", "다른 키", bx + KB_W / 2 + 4, by + KB_H - 26, 70, 18, mouseX, mouseY, false, false);
	}

	/** 키(또는 마우스 버튼 코드)를 지정 - 마크 키와 겹치면 확인부터. */
	private void bindKey(int code) {
		modalModsHeld = 0;
		modalModOnly = false;
		// 49-125차: 서버에서는 공격/사용의 보조 키를 키보드로 못 둔다 - 모달 안에 이유를 띄우고 계속 듣는다.
		String blocked = kr.lunaslight.mod.util.AltKeys.blockedReason(listeningKeybind.getId(), code);
		if (blocked != null) {
			kbBlockedMsg = blocked;
			kbBlockedUntil = System.currentTimeMillis() + 2500;
			return;
		}
		String conflict = LunaCompat.keyModifiers(code) == 0 ? LunaCompat.vanillaKeyLabelFor(this.client, code) : null;
		if (conflict != null) {
			pendingKey = code;
			pendingConflict = conflict;
			return;
		}
		listeningKeybind.setValue(code);
		listeningKeybind = null;
		pendingKey = -2;
		pendingConflict = null;
	}

	/** 49-124차: 키 삭제 전 확인 창. 지정된 키가 없으면 물어볼 것도 없이 그냥 실행. */
	private void askDeleteKeybind(KeybindSetting key, Runnable onDelete) {
		if (key == null || !key.isBound()) {
			onDelete.run();
			return;
		}
		keyConfirm.show(LunaIcons.KEYBOARD, "이 키를 삭제할까요?", "현재 키: " + key.getKeyName(), "삭제", onDelete);
	}

	/** 전체 초기화 확인 팝업(사용자: "한 번 더 누르는 거 말고 O/X 창으로"). */
	private void renderResetModal(DrawContext ctx, int mouseX, int mouseY) {
		ctx.fill(0, 0, width, height, 0xA6000000);
		float pop = LunaDraw.animFrom("modal:rs", 0f, 1f, 18f);
		int bx = modalX(RS_W), by = modalY(RS_H) + Math.round((1f - pop) * 10f);
		LunaDraw.panel3d(ctx, bx, by, RS_W, RS_H, 8);   // 49-227차: 사진 시안 판
		LunaIcons.drawCentered(ctx, textRenderer, LunaIcons.RESET, bx + RS_W / 2, LunaDraw.iconY(by + 6, 14), 0xFFCF7B74, false);
		LunaDraw.textCentered(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, resetTitle, RS_W - 16), bx + RS_W / 2, by + 30, LunaDraw.TEXT);
		LunaDraw.textCentered(ctx, textRenderer, resetSub, bx + RS_W / 2, by + 44, LunaDraw.TEXT_DIM);
		modalButton(ctx, "rsno", "취소", bx + RS_W / 2 - 72, by + RS_H - 28, 66, 20, mouseX, mouseY, false, false);
		modalButton(ctx, "rsyes", "초기화", bx + RS_W / 2 + 6, by + RS_H - 28, 66, 20, mouseX, mouseY, false, true);
	}

	/** 모달이 떠 있을 때의 클릭 처리. 모달이 없으면 false. */
	private boolean handleModalClick(double mouseX, double mouseY, int button) {
		if (pickingPrices != null) {
			PriceListSetting pl = pickingPrices;
			int bx = modalX(PICK_W);
			int by = modalY(PICK_H);
			if (LunaDraw.in(mouseX, mouseY, bx + PICK_W - 24, by + 6, 18, 18)
					|| !LunaDraw.in(mouseX, mouseY, bx, by, PICK_W, PICK_H)) {
				pickingPrices = null;
				return true;
			}
			int gx = bx + 10;
			int gy = by + PICK_HEAD;
			int gw = PICK_W - 20;
			int gh = PICK_H - PICK_HEAD - 8;
			if (LunaDraw.in(mouseX, mouseY, gx, gy, gw, gh)) {
				int cellW = gw / PICK_COLS;
				List<String> ids = pl.pickList();
				int c = (int) ((mouseX - gx) / cellW);
				int r = (int) ((mouseY - gy + pickerScroll) / PICK_CELL);
				int idx = r * PICK_COLS + c;
				if (c >= 0 && c < PICK_COLS && idx >= 0 && idx < ids.size()) {
					String id = ids.get(idx);
					Double existing = pl.entries().get(id);
					if (existing == null) {
						pl.setPrice(id, 0);
					}
					commitPriceEdit();
					priceEditSetting = pl;
					priceEditId = id;
					priceEditBuf = existing == null ? "" : HarvestLog.trim(existing);
					pickingPrices = null;
				}
			}
			return true;
		}
		if (listeningKeybind != null) {
			int bx = modalX(KB_W), by = modalY(KB_H);
			if (pendingConflict != null) {
				if (LunaDraw.in(mouseX, mouseY, bx + KB_W / 2 - 74, by + KB_H - 26, 70, 18)) {
					listeningKeybind.setValue(pendingKey);
					listeningKeybind = null;
				}
				// [다른 키] 또는 바깥/그 외 = 다시 듣기
				pendingKey = -2;
				pendingConflict = null;
				return true;
			}
			// 49-22차: 마우스 옆버튼(휠 클릭 포함, 버튼 2 이상)도 키로 지정(49-124차: 조합키 제거 - 버튼만)
			if (button >= 2) {
				bindKey(LunaCompat.mouseKeyCode(button));
				return true;
			}
			if (LunaDraw.in(mouseX, mouseY, bx + KB_W - 24, by + 6, 18, 18)
					|| !LunaDraw.in(mouseX, mouseY, bx, by, KB_W, KB_H)) {
				listeningKeybind = null; // X 또는 바깥 클릭 = 취소
			}
			return true;
		}
		if (confirmReset) {
			int bx = modalX(RS_W), by = modalY(RS_H);
			if (LunaDraw.in(mouseX, mouseY, bx + RS_W / 2 + 6, by + RS_H - 28, 66, 20)) {
				runReset();
			} else if (LunaDraw.in(mouseX, mouseY, bx + RS_W / 2 - 72, by + RS_H - 28, 66, 20)
					|| !LunaDraw.in(mouseX, mouseY, bx, by, RS_W, RS_H)) {
				confirmReset = false;
			}
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- 탭 바

	// 49-56차 탭 배치(사용자: "그래픽·UI·일반 설정은 기능이랑 별개다, 위쪽에 새로 만들어서"):
	//   0 ~ 2  설정 페이지 [일반][UI][그래픽]   ← 기능과 별개. 여기부터 시작한다.
	//   3      [전체]
	//   4 ~    기능 카테고리
	//   맨 뒤   [모드](설치된 모드 목록 - 카테고리가 아니라 딸림 화면이라 끝으로 뺐다)
	/**
	 * 49-61차: 탭 줄에 <b>내용이 있는 설정 페이지만</b> 싣는다. 빈 페이지 탭은 눌러 봐야 빈 화면이라
	 * 그 자체가 군더더기고, 무엇보다 "있는데 아무것도 없다"는 게 제일 나쁜 상태다.
	 *
	 * <p>모듈의 페이지 배정은 생성자에서 한 번 정해지고 실행 중에 바뀌지 않으므로 한 번만 계산해 둔다
	 * (탭 줄은 프레임마다 좌표를 다시 재므로 매번 모듈을 훑으면 그냥 낭비다).
	 */
	private static SettingsPage[] pagesCache;

	private static SettingsPage[] pages() {
		if (pagesCache == null) {
			List<SettingsPage> list = new ArrayList<>();
			for (SettingsPage p : SettingsPage.values()) {
				if (!ModuleManager.get().byPage(p).isEmpty()) {
					list.add(p);
				}
			}
			pagesCache = list.toArray(new SettingsPage[0]);
		}
		return pagesCache;
	}

	/** [전체] 탭. 카테고리 값이 아니라 selectedCategory == null 로 다룬다. */
	private static int tabAll() {
		return pages().length;
	}

	private static int tabCat0() {
		return tabAll() + 1;
	}

	private static int tabModsIndex() {
		return tabCat0() + ModuleCategory.values().length;
	}

	private static int tabCount() {
		return tabModsIndex() + 1;
	}

	/** 탭 번호 → 설정 페이지(기능 쪽 탭이면 null). */
	private static SettingsPage pageAt(int index) {
		SettingsPage[] ps = pages();
		return index >= 0 && index < ps.length ? ps[index] : null;
	}

	/** 설정 페이지 → 탭 번호(탭 줄에 없으면 -1). */
	private static int tabIndexOf(SettingsPage page) {
		SettingsPage[] ps = pages();
		for (int i = 0; i < ps.length; i++) {
			if (ps[i] == page) {
				return i;
			}
		}
		return -1;
	}

	/** 탭 번호 → 카테고리. 설정 페이지·[전체]·[모드]는 카테고리가 없으므로 null. */
	private static ModuleCategory categoryAt(int index) {
		return index < tabCat0() || index >= tabModsIndex() ? null
			: ModuleCategory.values()[index - tabCat0()];
	}

	/** 카테고리 → 탭 번호. null(전체)이면 [전체] 탭. */
	private static int tabIndexOf(ModuleCategory category) {
		return category == null ? tabAll() : category.ordinal() + tabCat0();
	}

	/** 지금 보고 있는 탭 번호. */
	private int currentTab() {
		if (modsView) {
			return tabModsIndex();
		}
		if (settingsPage != null) {
			int i = tabIndexOf(settingsPage);
			if (i >= 0) {
				return i;
			}
		}
		return tabIndexOf(selectedCategory);
	}

	private String tabLabel(int index) {
		if (index == tabModsIndex()) {
			return "모드";
		}
		SettingsPage p = pageAt(index);
		if (p != null) {
			return p.getDisplayName();
		}
		return index == tabAll() ? "전체" : categoryAt(index).getDisplayName();
	}

	private String tabIcon(int index) {
		if (index == tabModsIndex()) {
			return LunaIcons.PACKAGE;
		}
		SettingsPage p = pageAt(index);
		if (p != null) {
			return LunaIcons.forPage(p);
		}
		return index == tabAll() ? LunaIcons.ALL : LunaIcons.forCategory(categoryAt(index));
	}

	/** 그 탭이 몇 줄/몇 개를 담고 있는지(설명 줄 오른쪽 숫자). */
	private int tabCount(int index) {
		if (index == tabModsIndex()) {
			return installedMods().size();
		}
		SettingsPage p = pageAt(index);
		return p != null ? ModuleManager.get().byPage(p).size()
			: ModuleManager.get().byCategory(categoryAt(index)).size();
	}

	private boolean tabSelected(int index) {
		if (index == tabModsIndex()) {
			return modsView;
		}
		if (modsView || searching()) {
			return false;
		}
		SettingsPage p = pageAt(index);
		if (p != null) {
			return settingsPage == p;
		}
		return settingsPage == null && categoryAt(index) == selectedCategory;
	}

	// =============================================================== 탭 줄 치수
	// 49-56차: 탭이 8개 → 11개가 되면서 GUI 배율을 크게 쓰면(패널이 292px까지 좁아짐) 탭이
	// 검색칸을 밀고 나갈 수 있다. 그래서 폭이 모자라면 순서대로 줄인다:
	//   ① 묶음 사이 간격 → ② 탭 사이 간격 → ③ 검색칸(아이콘만) → ④ 탭 버튼 자체
	// 넷 다 줄여도 안 들어가는 크기는 마인크래프트가 만들지 않는다(최소 가로 320 → 패널 292).

	// ------------------------------------------------------------- 설명 바

	private record Action(String icon, String label, boolean accent, Runnable run) {
	}

	/**
	 * 알약 줄 오른쪽(또는 탭이 없으면 왼쪽부터)에 놓이는 작은 버튼들. 49-127차: 톱니는 첫 줄로 옮겼다.
	 */
	private List<Action> currentActions() {
		List<Action> list = new ArrayList<>();
		if (modsView) {
			list.add(new Action(LunaIcons.LAYOUT, "라이브러리 포함", showLibraries, () -> {
				showLibraries = !showLibraries;
				selectedMod = 0;
				resetScroll();
			}));
			return list;
		}
		if (openModule == null && pageView()) {
			// 49-56차: 설정 페이지는 "이 페이지 전부 기본값으로" 하나만.
			list.add(new Action(LunaIcons.RESET, "초기화", false, () -> askReset("이 페이지 설정을 초기화할까요?",
				"이 페이지의 모든 설정이 기본값으로", () -> {
					for (Module m : ModuleManager.get().byPage(settingsPage)) {
						for (Setting<?> s : visibleSettings(m)) {
							s.resetToDefault();
						}
					}
					openColor = null;
				})));
			return list;
		}
		if (openModule != null) {
			if (positionOf(openModule) != null) {
				list.add(new Action(LunaIcons.MOVE, "위치 편집", false, () -> openHudEditor(openModule)));
			}
			if ("waypoint".equals(openModule.getId())) {
				list.add(new Action(LunaIcons.LAYOUT, "관리창", true,
					() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaWaypointScreen(this))));
			}
			Module target = openModule;
			list.add(new Action(LunaIcons.RESET, "초기화", false, () -> askReset(target.getDisplayName() + " 설정을 초기화할까요?",
				"이 기능의 모든 설정이 기본값으로", () -> {
					for (Setting<?> s : visibleSettings(target)) {
						s.resetToDefault();
					}
					openColor = null;
				})));
		}
		return list;
	}

	/** 49-172차: 초기화 확인 창 열기(전체/페이지/기능 공용). */
	private void askReset(String title, String sub, Runnable action) {
		resetTitle = title;
		resetSub = sub;
		resetAction = action;
		confirmReset = true;
		LunaDraw.resetAnim("modal:rs");
	}

	private void runReset() {
		confirmReset = false;
		Runnable r = resetAction;
		resetAction = null;
		if (r != null) {
			r.run();
		} else {
			resetAllModules();
		}
	}

	/** 49-41차: 톱니 메뉴 항목(기능 목록 페이지). */
	private boolean actionMenuOpen;
	private static final int MENU_ROW_H = 18;

	private List<Action> menuActions() {
		List<Action> list = new ArrayList<>();
		list.add(new Action(LunaIcons.KEYBOARD, "키 지정", false,
			() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaKeybindsScreen(this))));
		list.add(new Action(LunaIcons.MOVE, "HUD 편집기", false, () -> openHudEditor(null)));
		// 49-32차에 여기 있던 [글꼴]은 49-61차에 뺐다 - 49-56차부터 [UI] 설정 페이지에 있어서
		// 같은 것이 두 군데에 있었다.
		// 49-48차의 [작물 계산기] 항목은 49-210차에 작물 계산기 기능 설정의 [가격 등록]/[수확 기록] 버튼으로 옮겼다.
		list.add(new Action(LunaIcons.RESET, "전체 초기화", false, () -> askReset("전체 설정을 초기화할까요?",
			"모든 기능의 설정/켜짐 상태가 기본값으로", this::resetAllModules)));
		return list;
	}

	private int menuWidth(List<Action> items) {
		int w = 0;
		for (Action a : items) {
			w = Math.max(w, LunaDraw.width(textRenderer, a.label()));
		}
		return 8 + LunaIcons.SIZE + 6 + w + 10;
	}

	private int menuX(List<Action> items) {
		return gearX() + 12 - menuWidth(items);
	}

	private int menuY() {
		return gearY() + 16;
	}

	private void renderActionMenu(DrawContext ctx, int mouseX, int mouseY) {
		List<Action> items = menuActions();
		int mw = menuWidth(items);
		int mx = menuX(items);
		int my = menuY();
		int mh = items.size() * MENU_ROW_H + 6;
		LunaDraw.shadow(ctx, mx, my, mw, mh, 4);
		LunaDraw.roundRect(ctx, mx, my, mw, mh, 4, LINE_HOV);
		LunaDraw.roundRect(ctx, mx + 1, my + 1, mw - 2, mh - 2, 3, MODAL_BG | 0xFF000000);
		int ry = my + 3;
		for (Action a : items) {
			boolean hovered = LunaDraw.in(mouseX, mouseY, mx + 3, ry, mw - 6, MENU_ROW_H);
			float hov = LunaDraw.anim("menu:" + a.label(), hovered ? 1f : 0f, 18f);
			if (hov > 0.01f) {
				LunaDraw.roundRect(ctx, mx + 3, ry, mw - 6, MENU_ROW_H, 3, ink(Math.round(0x12 * hov)));
			}
			int fg = LunaDraw.lerpColor(LunaDraw.TEXT_SUB, LunaDraw.TEXT, hov);
			LunaIcons.draw(ctx, textRenderer, a.icon(), mx + 8, LunaDraw.iconY(ry, MENU_ROW_H), hovered ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
			LunaDraw.text(ctx, textRenderer, a.label(), mx + 8 + LunaIcons.SIZE + 6, LunaDraw.textY(ry, MENU_ROW_H), fg);
			ry += MENU_ROW_H;
		}
	}

	/** 메뉴가 열려 있을 때의 클릭: 항목이면 실행, 어디든 누르면 닫힘(톱니 자체는 액션 바 쪽이 토글). */
	private boolean handleActionMenuClick(double mouseX, double mouseY) {
		if (!actionMenuOpen) {
			return false;
		}
		List<Action> items = menuActions();
		int mw = menuWidth(items);
		int mx = menuX(items);
		int my = menuY();
		int ry = my + 3;
		for (Action a : items) {
			if (LunaDraw.in(mouseX, mouseY, mx + 3, ry, mw - 6, MENU_ROW_H)) {
				actionMenuOpen = false;
				a.run().run();
				return true;
			}
			ry += MENU_ROW_H;
		}
		actionMenuOpen = false;
		return true;   // 메뉴 밖(톱니 포함)을 누르면 닫는 것으로 끝(다시 열리지 않게)
	}


	// =====================================================================
	// 49-56차: 설정 페이지([일반]·[UI]·[그래픽]) - 기능 격자와 다른 화면
	// =====================================================================
	//
	// 사용자: "그래픽, UI, 일반 설정은 기능이랑은 별개인 거야. 따로 둬야 하는 거야, 위쪽에 새로 만들어서."
	//
	// 기능 격자는 "카드를 눌러 들어가면 그 기능의 설정"이지만, 여기는 <b>한 페이지에 다 펼쳐</b> 둔다.
	// 클라이언트 설정을 보러 와서 카드를 한 번씩 눌러 들어갔다 나오게 만들 이유가 없다.
	//
	// 한 모듈 = 한 묶음. 묶음 머리줄에 [이름 + 스위치]가 있고, 설정이 있으면 그 아래에 줄로 붙는다.
	// 설정이 하나도 없는 것(예: 신호기 빛 끄기)은 머리줄 하나로 끝난다 - 그게 곧 그 설정이다.

	private static final int PAGE_HEAD_H = 30;

	/** 지금 설정 페이지를 보고 있는지(검색 중에는 기능 격자가 결과를 보여주므로 아니다). */
	/**
	 * 49-61차: 탭 줄에 없는 페이지(내용이 비어 사라진 페이지)를 마지막으로 보고 있었다면 기능 격자로
	 * 되돌린다 - 안 그러면 "아무 탭도 선택 안 된 빈 화면"이 뜬다.
	 */
	private boolean pageView() {
		if (settingsPage != null && tabIndexOf(settingsPage) < 0) {
			settingsPage = null;
			lastPage = null;
		}
		return settingsPage != null && !searching();
	}

	/** 설정 줄을 그리는 동안의 주인 모듈 - 애니메이션 키가 모듈마다 따로 놀게. */
	private Module rowOwner;

	private String rowOwnerId() {
		Module m = rowOwner != null ? rowOwner : openModule;
		return m == null ? "" : m.getId();
	}

	/** 이 모듈이 설정 페이지에서 보여줄 설정 줄들(스위치에 붙어 그려지는 색은 뺀다). */
	private List<Setting<?>> pageSettings(Module m) {
		List<Setting<?>> list = new ArrayList<>();
		for (Setting<?> s : visibleSettings(m)) {
			if (!isLinkedColor(m, s)) {
				list.add(s);
			}
		}
		return list;
	}

	private int sectionHeight(Module m) {
		int h = PAGE_HEAD_H;
		List<List<Cell>> rows = pageRows(m, contentW);
		if (!rows.isEmpty()) {
			for (List<Cell> row : rows) {
				h += row.get(0).h();
			}
			h += 6;
		}
		return h;
	}

	// ============================================================================
	// 49-88차(8-7, 사용자: "일반·UI·그래픽 설정창 한 줄에 한 설정 말고 한 줄에 2개 반반"):
	// 페이지 설정을 "칸" 단위로 배치한다. 한 줄짜리 단순 설정(스위치·숫자·가로 선택·키·위치·색·글자)은 둘씩
	// 짝지어 반반, 키가 큰 것(세로 선택지·목록·펼친 색 편집기)은 한 줄을 통째로 쓴다. 높이·그리기·클릭이
	// 같은 배치를 보게 pageRows() 하나로 모았다.
	// ============================================================================
	private record Cell(Setting<?> s, int dx, int w, int h) {
	}

	private static final int CELL_GAP = 12;
	private static final int MIN_HALF_W = 250;   // 이보다 좁으면 반반이 아니라 한 줄 하나로

	private List<List<Cell>> pageRows(Module m, int w) {
		return pairRows(pageSettings(m), w);
	}

	/**
	 * 49-88차/49-124차: 설정 목록을 "칸" 줄로 묶는다. 한 줄짜리 단순 설정(스위치/숫자/가로 선택/키/위치/색/글자)은
	 * 둘씩 짝지어 반반, 키가 큰 것(세로 선택지/목록/펼친 색 편집기)은 한 줄을 통째로 쓴다. 페이지 설정과 기능
	 * 상세 설정이 같은 배치를 보게 하나로 모았다.
	 */
	private List<List<Cell>> pairRows(List<Setting<?>> list, int w) {
		List<List<Cell>> rows = new ArrayList<>();
		boolean halves = w >= MIN_HALF_W * 2 + CELL_GAP;
		int halfW = (w - CELL_GAP) / 2;
		int i = 0;
		while (i < list.size()) {
			Setting<?> a = list.get(i);
			int ha = rowHeight(a);
			if (halves && ha == ROW_H && i + 1 < list.size() && rowHeight(list.get(i + 1)) == ROW_H) {
				Setting<?> b = list.get(i + 1);
				rows.add(List.of(new Cell(a, 0, halfW, ROW_H), new Cell(b, halfW + CELL_GAP, w - halfW - CELL_GAP, ROW_H)));
				i += 2;
			} else {
				rows.add(List.of(new Cell(a, 0, w, ha)));
				i += 1;
			}
		}
		return rows;
	}

	/** 기능 상세 설정의 한 묶음(박스). label = 묶음 제목, settings = 그 묶음의 설정들(선언 순서). */
	private record GroupBox(String label, List<Setting<?>> settings) {
	}

	/**
	 * 49-124차(사용자: "채팅이나 이런 거 지금 한 박스 안에 너무 많아 좀 나눠야 해"): 기능 상세 설정을
	 * 이름 붙은 묶음들로 나눈다. 스타일 설정은 늘 맨 끝 "스타일" 묶음으로, 나머지는 각 설정의 group()
	 * 이름(없으면 "기능")별로 선언 순서대로 묶는다.
	 */
	private List<GroupBox> detailGroups(Module m) {
		java.util.LinkedHashMap<String, List<Setting<?>>> main = new java.util.LinkedHashMap<>();
		List<Setting<?>> style = new ArrayList<>();
		for (Setting<?> s : visibleSettings(m)) {
			if (isLinkedColor(m, s)) {
				continue;
			}
			if (groupOf(s) == G_STYLE) {
				style.add(s);
			} else {
				String label = s.getGroupName() != null ? s.getGroupName() : GROUP_NAMES[G_MAIN];
				main.computeIfAbsent(label, k -> new ArrayList<>()).add(s);
			}
		}
		List<GroupBox> out = new ArrayList<>();
		for (var e : main.entrySet()) {
			out.add(new GroupBox(e.getKey(), e.getValue()));
		}
		if (!style.isEmpty()) {
			out.add(new GroupBox(GROUP_NAMES[G_STYLE], style));
		}
		return out;
	}

	private int pageTotalHeight(List<Module> modules) {
		int total = 0;
		for (Module m : modules) {
			total += sectionHeight(m) + GROUP_GAP;
		}
		return total;
	}

	private void renderPage(DrawContext ctx, int mouseX, int mouseY) {
		List<Module> modules = ModuleManager.get().byPage(settingsPage);
		int w = contentW;
		boolean inContent = LunaDraw.in(mouseX, mouseY, contentX, contentY, w, contentH);
		clampScroll(pageTotalHeight(modules));
		scis(ctx, contentX, contentY, contentX + w, contentY + contentH);

		int y = contentY - (int) scroll;
		for (Module m : modules) {
			List<Setting<?>> list = pageSettings(m);
			int boxH = sectionHeight(m);
			surface(ctx, contentX, y, w, boxH, cardSolid());

			boolean headHover = inContent && LunaDraw.in(mouseX, mouseY, contentX, y, w, PAGE_HEAD_H);
			boolean on = m.isEnabled();
			// 49-226차(사진 시안): 머리 줄 = 아이콘 칸 + 이름, 설정 줄마다 작은 카드
			int its = Math.min(18, PAGE_HEAD_H - 6);
			float pon = LunaDraw.anim("pt:" + m.getId(), on ? 1f : 0f, 14f);
			// 49-232차(사용자: "일반, UI, 그래픽 같은 설정은 아이콘에 배경 만들지 마"): 칸 없이 아이콘만
			iconIn(ctx, LunaIcons.forModule(m.getId(), m.getCategory()), contentX + 6, y + (PAGE_HEAD_H - its) / 2, its, its,
				LunaDraw.lerpColor(LunaDraw.TEXT_DIM, LunaDraw.ACCENT, pon));
			LunaDraw.text(ctx, textRenderer, m.getDisplayName(), contentX + 6 + its + 6, LunaDraw.textY(y, PAGE_HEAD_H),
				headHover ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);

			// 오른쪽 끝에 무엇이 오는지 - 49-56차: 끌 수 없는 것(테마 등)은 스위치를 아예 안 그린다
			// (눌러도 안 꺼지는 스위치는 거짓말이다), 못 쓰는 버전이면 스위치 대신 이유를 적는다.
			int gRight;
			if (m.isAlwaysOn()) {
				gRight = contentX + w - 12;
			} else if (!m.isVersionSupported()) {
				String need = m.getVersionRequirementLabel();
				String lock = need == null ? "지원 안 함" : need + " 필요";
				int lw = LunaDraw.width(textRenderer, lock);
				LunaDraw.text(ctx, textRenderer, lock, contentX + w - 10 - lw,
					LunaDraw.textY(y, PAGE_HEAD_H), LunaDraw.TEXT_DIM);
				gRight = contentX + w - 18 - lw;
			} else {
				LunaDraw.toggle(ctx, contentX + w - TOGGLE_W - 9, y + (PAGE_HEAD_H - TOGGLE_H) / 2,
					TOGGLE_W, TOGGLE_H, LunaDraw.anim("pt:" + m.getId(), on ? 1f : 0f, 14f), true);
				gRight = contentX + w - TOGGLE_W - 17;
			}
			// 49-226차: 머리줄 오른쪽 가는 선은 뺐다(카드가 나눈다)

			int ry = y + PAGE_HEAD_H;
			rowOwner = m;
			try {
				for (List<Cell> row : pageRows(m, w)) {
					for (Cell c : row) {
						rowCard(ctx, contentX + c.dx() + 5, ry + 1, c.w() - 10, row.get(0).h() - 4);
						renderSettingRow(ctx, c.s(), contentX + c.dx(), ry, c.w(), mouseX, mouseY, inContent);
					}
					ry += row.get(0).h();
				}
			} finally {
				rowOwner = null;
			}
			y += boxH + GROUP_GAP;
		}
		if (modules.isEmpty()) {
			LunaDraw.textCentered(ctx, textRenderer, "이 페이지에 설정이 없음",
				contentX + w / 2, contentY + contentH / 2 - 4, LunaDraw.TEXT_DIM);
		}
		ctx.disableScissor();
		renderScrollbar(ctx);
	}

	private boolean handlePageClick(double mouseX, double mouseY, int button) {
		if (!LunaDraw.in(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
			return true;
		}
		int w = contentW;
		int y = contentY - (int) scroll;
		for (Module m : ModuleManager.get().byPage(settingsPage)) {
			if (LunaDraw.in(mouseX, mouseY, contentX, y, w, PAGE_HEAD_H)) {
				if (m.isVersionSupported() && !m.isAlwaysOn()) {
					ModuleManager.get().toggleWithConflictResolution(m);
				}
				return true;
			}
			int ry = y + PAGE_HEAD_H;
			rowOwner = m;
			try {
				for (List<Cell> row : pageRows(m, w)) {
					int h = row.get(0).h();
					for (Cell c : row) {
						if (LunaDraw.in(mouseX, mouseY, contentX + c.dx(), ry, c.w(), h)) {
							handleSettingClick(c.s(), contentX + c.dx(), ry, c.w(), mouseX, mouseY, button);
							return true;
						}
					}
					ry += h;
				}
			} finally {
				rowOwner = null;
			}
			y += sectionHeight(m) + GROUP_GAP;
		}
		return true;
	}

	// ------------------------------------------------------------ 카드 그리드

	// ------------------------------------------------------------ 기능 상자(유리)

	private int tileCols() {
		return Math.max(1, (contentW + TILE_GAP) / (TILE_MIN_W + TILE_GAP));
	}

	/** i번째 상자 {x, y(스크롤 전, contentY 기준), w}. 나눗셈 나머지는 마지막 열이 먹는다. */
	private int[] tileRect(int i) {
		int cols = tileCols();
		int cw = (contentW - TILE_GAP * (cols - 1)) / cols;
		int col = i % cols;
		int row = i / cols;
		int x = contentX + col * (cw + TILE_GAP);
		int w = col == cols - 1 ? contentX + contentW - x : cw;
		return new int[]{x, TILE_TOP_PAD + serverHeadH() + row * (TILE_H + TILE_GAP), w};
	}

	private void renderGrid(DrawContext ctx, int mouseX, int mouseY) {
		List<Module> modules = currentModules();
		boolean inContent = LunaDraw.in(mouseX, mouseY, contentX, contentY, contentW, contentH);
		int rows = (modules.size() + tileCols() - 1) / tileCols();
		clampScroll(Math.max(0, TILE_TOP_PAD + serverHeadH() + rows * (TILE_H + TILE_GAP) - TILE_GAP + 2));

		scis(ctx, contentX - 2, contentY, contentX + contentW + 2, contentY + contentH);
		if (serverHeadH() > 0) {
			renderServerHead(ctx, mouseX, mouseY, contentY - (int) scroll);
		}
		for (int i = 0; i < modules.size(); i++) {
			int[] r = tileRect(i);
			int y = contentY + r[1] - (int) scroll;
			if (y + TILE_H < contentY || y > contentY + contentH) {
				continue;
			}
			boolean hovered = inContent && LunaDraw.in(mouseX, mouseY, r[0], y, r[2], TILE_H);
			renderTile(ctx, modules.get(i), r[0], y, r[2], mouseX, mouseY, hovered);
		}
		ctx.disableScissor();
		renderScrollbar(ctx);

		if (modules.isEmpty()) {
			LunaIcons.drawCentered(ctx, textRenderer, LunaIcons.SEARCH, contentX + contentW / 2,
				LunaDraw.iconLgY(contentY + 24, 16), GRAY, true);
			LunaDraw.textCentered(ctx, textRenderer, searching() ? "검색 결과 없음" : "이 카테고리에 기능이 없음",
				contentX + contentW / 2, contentY + 58, GRAY);
		}
	}

	/**
	 * 49-141차(시안 v4) 기능 상자 한 칸: 왼쪽 위 아이콘(예전 아이콘 그대로, 중간 크기 16px), 오른쪽 위 작은 네모 스위치,
	 * 가운데 줄 = 지금 값(그 기능 HUD가 방금 그린 글자, 없으면 단축키), 아래 = 이름. 꺼진 상자는 글자·아이콘이 흐리다.
	 * 본문 = 설정 열기, 스위치 = 켜기/끄기. 호버는 테두리·바탕만 한 단계 밝게(움직이지 않음).
	 */
	private void renderTile(DrawContext ctx, Module m, int x, int y, int w, int mouseX, int mouseY, boolean hovered) {
		// 49-226차(사진 시안): 카드(아래 두께) + 왼쪽 위 아이콘 칸 + 이름/지금 값 + 아래 [켜짐/켜기] 입체 버튼(큰 박스는 옆에 톱니 칸).
		// 켜진 카드는 테두리와 속이 테마색으로 물든다(예전 바깥 빛 번짐은 뺐다). 버튼 = 켜기/끄기, 나머지 = 설정 열기.
		boolean supported = m.isVersionSupported();
		// 49-256차: 지금 들어간 서버에서 안 되는 기능(밝기 등)은 회색 + 이유
		String here = supported ? m.unavailableHere() : null;
		boolean dim = !supported || here != null;
		float hov = LunaDraw.anim("ch:" + m.getId(), hovered ? 1f : 0f, 18f);
		float on = LunaDraw.anim("t:" + m.getId(), m.isEnabled() && !dim ? 1f : 0f, 14f);
		glass(ctx, x, y, w, TILE_H, hov, supported ? on : 0f);

		int iconColor = dim ? GRAY_DIM : LunaDraw.lerpColor(LunaDraw.lerpColor(OFF_TXT, GRAY, hov), ACC, on);
		String glyph = LunaIcons.forModule(m.getId(), m.getCategory());
		String value = "";
		int valueColor = GRAY;
		if (!supported) {
			String req = m.getVersionRequirementLabel();
			value = req == null ? "지원 안 함"
				: req.endsWith(" 모드 설치") ? req.substring(0, req.length() - " 모드 설치".length()) + " 필요" : req + " 필요";
			valueColor = 0xCCF59E0B;
		} else if (here != null) {
			value = here;
			valueColor = GRAY;
		}
		// 49-232차(사용자: "기능 켜면 기능에도 표시되는 거 없애고, FPS 같은 거"): 지금 값(liveValue) 표시 삭제
		int nameColor = dim ? GRAY_DIM : LunaDraw.lerpColor(LunaDraw.lerpColor(GRAY, WHITE, 0.6f + 0.4f * hov), WHITE, on);

		if (VIEW == VIEW_LIST) {
			// 자세히 보기: [아이콘 칸] 이름 ........ 지금 값  [스위치]
			int ts = TILE_H - 8;
			iconTint = !dim ? pastel(m.getCategory()) : null;
			iconTile(ctx, glyph, x + 4, y + 4, ts, !dim ? on : 0f, iconColor);
			int nameX = x + 4 + ts + 8;
			int right = tileToggleX(x, w) - 12;
			String name = gFit(m.getDisplayName(), Math.max(20, (right - nameX) / 2));
			gText(ctx, name, nameX, y, TILE_H, nameColor);
			if (!value.isEmpty()) {
				int vx0 = nameX + gWidth(name) + 16;
				String v = LunaDraw.ellipsize(textRenderer, value, Math.max(10, right - vx0));
				LunaDraw.text(ctx, textRenderer, v, right - LunaDraw.width(textRenderer, v), LunaDraw.textY(y, TILE_H), valueColor);
			}
			if (supported && !m.isAlwaysOn()) {
				int sx = tileToggleX(x, w);
				int sy = tileToggleY(y);
				if (LunaDraw.in(mouseX, mouseY, sx - 4, sy - 4, SW_W + 8, SW_H + 8)) {
					LunaDraw.pill(ctx, sx - 3, sy - 3, SW_W + 6, SW_H + 6, ink(0x14));
				}
				pxSwitch(ctx, sx, sy, SW_W, SW_H, on);
			}
			return;
		}

		boolean large = VIEW == VIEW_LARGE;
		int pad = large ? 9 : 7;
		int ts = large ? 26 : 20;
		iconTint = !dim ? pastel(m.getCategory()) : null;
		iconTile(ctx, glyph, x + pad, y + pad, ts, !dim ? on : 0f, iconColor);
		int tx = x + pad + ts + 6;
		int tw = Math.max(10, x + w - pad - tx);
		String name = LunaDraw.ellipsize(textRenderer, m.getDisplayName(), tw);
		if (value.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, name, tx, LunaDraw.textY(y + pad, ts), nameColor);
		} else {
			LunaDraw.text(ctx, textRenderer, name, tx, y + pad + (large ? 3 : 1), nameColor);
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, value, tw), tx, y + pad + ts - 9, valueColor);
		}
		int[] b = toggleRect(x, y, w);
		if (supported && !m.isAlwaysOn()) {
			boolean bh = LunaDraw.in(mouseX, mouseY, b[0], b[1], b[2], b[3]);
			btn3d(ctx, b[0], b[1], b[2], b[3], m.isEnabled() ? "켜짐" : "켜기", m.isEnabled() && here == null,
				LunaDraw.anim("tb:" + m.getId(), bh ? 1f : 0f, 18f));
		} else if (supported) {
			btn3d(ctx, b[0], b[1], b[2], b[3], "열기", false, hov);   // 늘 켜진 기능(계산기 등) - 누르면 전용 화면/설정
		}
		if (large) {
			int gx = x + w - pad - b[3];
			boolean gh = hovered && LunaDraw.in(mouseX, mouseY, gx, b[1], b[3], b[3]);
			card(ctx, gx, b[1], b[3], b[3], 4, LunaDraw.lerpColor(CARD_HOV, LunaTheme.mix(CARD_HOV, WHITE, 0.06f), gh ? 1f : 0f),
				LINE_HOV);
			iconIn(ctx, LunaIcons.SETTINGS, gx, b[1], b[3], b[3], gh ? WHITE : GRAY);
		}
	}

	/** 49-226차: 카드 아래 켜기 버튼 자리 {x, y, w, h}(자세히 보기는 오른쪽 스위치 자리). 그리기와 누르기가 같은 수를 쓴다. */
	private static int[] toggleRect(int x, int y, int w) {
		if (VIEW == VIEW_LIST) {
			return new int[]{tileToggleX(x, w) - 4, tileToggleY(y) - 4, SW_W + 8, SW_H + 8};
		}
		boolean large = VIEW == VIEW_LARGE;
		int pad = large ? 9 : 7;
		int h = large ? 18 : 16;
		int by = y + TILE_H - pad - h - 1;
		int bw = large ? w - pad * 2 - h - 4 : w - pad * 2;
		return new int[]{x + pad, by, bw, h};
	}

	private static int tileToggleX(int x, int w) {
		return x + w - (VIEW == VIEW_SMALL ? 8 : 10) - SW_W;
	}

	private static int tileToggleY(int y) {
		return VIEW == VIEW_LIST ? y + (TILE_H - SW_H) / 2 : VIEW == VIEW_LARGE ? y + 12 : y + 11;
	}

	/** 기능 보기 모드에 맞춰 상자 치수를 정한다. */
	private static void applyTileMode() {
		VIEW = Math.max(0, Math.min(2, LunaTheme.tileView()));
		switch (VIEW) {
			case VIEW_SMALL -> {
				TILE_H = 60;
				TILE_GAP = 8;
				TILE_MIN_W = 100;
				SW_W = 16;
				SW_H = 8;
			}
			case VIEW_LIST -> {
				TILE_H = 30;
				TILE_GAP = 4;
				TILE_MIN_W = 100000;   // 한 줄에 하나
				SW_W = 20;
				SW_H = 10;
			}
			default -> {
				// 49-232차(사용자: "박스 크기 키워줘, GUI 2 기준 한 줄에 4개 정도"): 1920x1080 GUI 2에서 본문 폭 702 → 4칸
				TILE_H = 76;
				TILE_GAP = 10;
				TILE_MIN_W = 150;
				SW_W = 20;
				SW_H = 10;
			}
		}
	}

	/** 미리보기 칸: 실제 게임 사진(preview.png) 위에 그 기능을 그린다. 칸 비율에 맞게 사진을 잘라 쓴다. */
	private void renderPreviewBox(DrawContext ctx, Module m, int x, int y, int w, int h) {
		int regionH = Math.max(24, Math.min(512, Math.round(277f * h / Math.max(1, w))));
		int v = Math.max(0, Math.min(512 - regionH, 250 - regionH / 2));
		if (!LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/preview.png"), x, y, w, h, 0, v, 277, regionH, 512, 0xFFFFFFFF)) {
			int horizon = y + h * 45 / 100;
			ctx.fillGradient(x, y, x + w, horizon, 0xFF8FC2E8, 0xFFB7D9EF);
			ctx.fillGradient(x, horizon, x + w, y + h, 0xFF7FA04E, 0xFF5E7D3A);
		}
		scis(ctx, x, y, x + w, y + h);
		try {
			m.renderPreview(ctx, x, y, w, h);
		} catch (Throwable t) {
			LunaCompat.warnOnce("preview:" + m.getId(), t);
		} finally {
			ctx.disableScissor();
		}
	}

	private void renderScrollbar(DrawContext ctx) {
		if (maxScroll <= 0) {
			return;
		}
		int trackX = contentX + contentW + 6;
		int thumbH = Math.max(24, contentH * contentH / (contentH + maxScroll));
		int thumbY = contentY + (int) ((contentH - thumbH) * (scroll / maxScroll));
		LunaDraw.roundRect(ctx, trackX, thumbY, 2, thumbH, 1, ink(0x40));
	}

	// --------------------------------------------------------- 설정 페이지

	// ------------------------------------------------------------ 기능 설정: 왼쪽 미리보기 상자 + 오른쪽 설정 상자

	private int stageW() {
		return openModule != null && hasPreview(openModule)
			? Math.max(110, Math.min(280, Math.round(contentW * 0.42f))) : 0;
	}

	private int stageH() {
		return Math.min(contentH, Math.round(stageW() * 0.8f));
	}

	private int settingsX() {
		int s = stageW();
		return s > 0 ? contentX + s + 10 : contentX;
	}

	private int settingsW() {
		int s = stageW();
		return s > 0 ? contentW - s - 10 : contentW;
	}

	/** 왼쪽 미리보기 상자. 설정을 바꾸면 바로 반영. 위치가 있는 기능은 누르면 HUD 편집기로. */
	private void renderStage(DrawContext ctx, int mouseX, int mouseY) {
		int x = contentX;
		int y = stageY();
		if (y + stageH() <= contentY) {
			return;
		}
		scis(ctx, contentX, contentY, contentX + contentW, contentY + contentH);
		try {
			renderStageBody(ctx, x, y, mouseX, mouseY);
		} finally {
			ctx.disableScissor();
		}
	}

	/**
	 * 49-176차(사용자: "미리보기 아래쪽도 기능 설정 놔도 됨"): 미리보기 상자도 설정과 같이 스크롤되고, 미리보기 아래로
	 * 내려간 묶음은 화면 폭을 다 쓴다. 미리보기 옆 묶음만 오른쪽 칸 폭.
	 */
	private int stageY() {
		return contentY - (int) scroll;
	}

	/** 묶음 하나의 자리(내용 맨 위 기준 relY, 칸 x와 폭). */
	private record GroupSlot(GroupBox box, int relY, int x, int w) {
	}

	private int groupBlockHeight(Module m, GroupBox box, int w) {
		int h = GROUP_HEAD_H;
		if (!isGroupCollapsed(m, box.label())) {
			for (List<Cell> row : pairRows(box.settings(), w)) {
				h += row.get(0).h();
			}
			h += 6;
		}
		return h + GROUP_GAP;
	}

	private List<GroupSlot> groupSlots(Module m) {
		List<GroupSlot> out = new ArrayList<>();
		int s = stageW();
		int below = s > 0 ? stageH() + 8 : 0;
		int rel = 0;   // 49-226차: 묶음이 카드라 카드 윗선 = 미리보기 윗선
		for (GroupBox box : detailGroups(m)) {
			boolean wide = s <= 0 || rel >= below;
			int gx = wide ? contentX : settingsX();
			int gw = wide ? contentW : settingsW();
			out.add(new GroupSlot(box, rel, gx, gw));
			rel += groupBlockHeight(m, box, gw);
		}
		return out;
	}

	/** 묶음들이 끝난 자리(relY) - 안내표가 여기서 시작. */
	private int groupsEndRel(Module m, List<GroupSlot> slots) {
		if (slots.isEmpty()) {
			return 0;
		}
		GroupSlot last = slots.get(slots.size() - 1);
		return last.relY() + groupBlockHeight(m, last.box(), last.w());
	}

	private void renderStageBody(DrawContext ctx, int x, int y, int mouseX, int mouseY) {
		int w = stageW();
		int h = stageH();
		boolean movable = positionOf(openModule) != null;
		boolean hovered = movable && LunaDraw.in(mouseX, mouseY, x, y, w, h);
		glass(ctx, x, y, w, h, hovered ? 1f : 0f);
		// 49-234차(사용자: "미리보기가 뒤 박스보다 앞으로 나와서 이상해"): 그림이 카드 둥근 테두리를 덮지 않게 액자처럼 안쪽으로
		renderPreviewBox(ctx, openModule, x + 5, y + 5, w - 10, h - 10);
		if (movable) {
			String hint = "눌러서 위치 편집";
			int hw = LunaDraw.width(textRenderer, hint) + 12;
			int hy = y + h - 6 - 14;
			LunaDraw.roundRect(ctx, x + 6, hy, hw, 14, 2, LunaDraw.withAlpha(0x000000, hovered ? 0xC0 : 0x96));
			LunaDraw.text(ctx, textRenderer, hint, x + 12, LunaDraw.textY(hy, 14), hovered ? WHITE : 0xFFDCDEE4);
		}
	}

	private boolean handleStageClick(double mouseX, double mouseY) {
		if (openModule == null || stageW() <= 0 || positionOf(openModule) == null) {
			return false;
		}
		if (!LunaDraw.in(mouseX, mouseY, contentX, contentY, contentW, contentH)
				|| !LunaDraw.in(mouseX, mouseY, contentX, stageY(), stageW(), stageH())) {
			return false;
		}
		openHudEditor(openModule);
		return true;
	}

	private void renderSettings(DrawContext ctx, int mouseX, int mouseY) {
		Module m = openModule;
		boolean inContent = LunaDraw.in(mouseX, mouseY, contentX, contentY, contentW, contentH);
		clampScroll(settingsTotalHeight(m));
		scis(ctx, contentX, contentY, contentX + contentW, contentY + contentH);
		// 49-172차(사용자: "미리보기랑 설정들이랑 위쪽 위치가 안 맞음"): 첫 묶음 제목의 글자 윗선이 미리보기 상자 윗선과
		// 같은 높이가 되게 머리 여백만큼 올려 시작한다(잘리는 건 빈 여백뿐). 49-176차: 자리는 groupSlots가 정한다.
		int top = contentY - (int) scroll;
		List<GroupSlot> slots = groupSlots(m);
		for (GroupSlot g : slots) {
			renderGroup(ctx, m, g.box().label(), g.box().settings(), g.x(), top + g.relY(), g.w(), mouseX, mouseY, inContent);
		}
		int endRel = groupsEndRel(m, slots);
		boolean helpWide = stageW() <= 0 || endRel >= stageH() + 8;
		renderHelpTable(ctx, m, helpWide ? contentX : settingsX(), top + endRel, helpWide ? contentW : settingsW());
		ctx.disableScissor();
		renderScrollbar(ctx);
	}

	// 49-32차: 설정 아래 안내표(스크린샷 파일 이름 표기 등)
	private static final int HELP_ROW_H = 14;
	private static final int HELP_HEAD_H = 20;

	private int helpTableHeight(Module m) {
		String[][] rows = m.helpTable();
		return rows == null || rows.length == 0 ? 0 : HELP_HEAD_H + rows.length * HELP_ROW_H + 12;
	}

	private void renderHelpTable(DrawContext ctx, Module m, int x, int y, int w) {
		String[][] rows = m.helpTable();
		if (rows == null || rows.length == 0) {
			return;
		}
		int h = HELP_HEAD_H + rows.length * HELP_ROW_H + 8;
		surface(ctx, x, y, w, h, cardSolid());
		LunaDraw.text(ctx, textRenderer, m.helpTableTitle(), x + 10, y + 7, LunaDraw.TEXT_SUB);
		int keyW = 0;
		for (String[] r : rows) {
			keyW = Math.max(keyW, LunaDraw.width(textRenderer, r[0]));
		}
		int ry = y + HELP_HEAD_H;
		for (String[] r : rows) {
			LunaDraw.text(ctx, textRenderer, r[0], x + 10, ry, LunaDraw.ACCENT);
			LunaDraw.text(ctx, textRenderer,
				LunaDraw.ellipsize(textRenderer, r[1], w - 20 - keyW - 8), x + 10 + keyW + 8, ry, LunaDraw.TEXT_DIM);
			ry += HELP_ROW_H;
		}
	}

	private int settingsTotalHeight(Module m) {
		int end = groupsEndRel(m, groupSlots(m)) + helpTableHeight(m);
		return Math.max(end, stageW() > 0 ? stageH() : 0);
	}

	private GroupSlot slotOf(Module m, String label) {
		for (GroupSlot g : groupSlots(m)) {
			if (g.box().label().equals(label)) {
				return g;
			}
		}
		return null;
	}

	private int groupHeadY(Module m, String label) {
		GroupSlot g = slotOf(m, label);
		List<GroupSlot> all = groupSlots(m);
		int rel = g != null ? g.relY() : groupsEndRel(m, all);
		return contentY - (int) scroll + rel;   // 49-172차: renderSettings와 같은 시작점
	}

	private int renderGroup(DrawContext ctx, Module m, String label, List<Setting<?>> list,
			int x, int y, int w, int mouseX, int mouseY, boolean inContent) {
		boolean folded = isGroupCollapsed(m, label);
		List<List<Cell>> rows = folded ? List.of() : pairRows(list, w);
		int bodyH = 0;
		if (!folded) {
			for (List<Cell> row : rows) {
				bodyH += row.get(0).h();
			}
			bodyH += 6;
		}
		int boxH = GROUP_HEAD_H + bodyH;
		// 49-143차(사용자: "세부 설정이 박스에 있어서 빈 공간이 많아 보여, 분리만 잘 해서 박스 없애고 깔끔하게"):
		// 묶음 상자를 없애고 [제목 + 끝까지 가는 1px 선]으로만 나눈다.

		boolean headHover = inContent && LunaDraw.in(mouseX, mouseY, x, y, w, GROUP_HEAD_H);
		// 49-226차(사진 시안): 묶음 = 카드 한 장, 그 안 설정 줄마다 작은 카드. 개수 표시는 뺐다.
		glass(ctx, x, y, w, boxH, headHover ? 0.5f : 0f);
		LunaDraw.chevron(ctx, x + 8, y + (GROUP_HEAD_H - 5) / 2, !folded,
			headHover ? LunaDraw.TEXT : LunaDraw.TEXT_DIM);
		LunaDraw.text(ctx, textRenderer, label, x + 18, LunaDraw.textY(y, GROUP_HEAD_H),
			headHover ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);

		if (folded) {
			return y + boxH + GROUP_GAP;
		}

		// 49-53차(4-19): 설정 줄 사이에 긋던 가는 선을 없앴다 - 묶음 상자 테두리와 줄 간격만으로 충분히 갈린다.
		// 49-124차: 반반 두 열 배치(단순 설정은 둘씩, 큰 설정은 한 줄 통째로).
		int ry = y + GROUP_HEAD_H;
		for (List<Cell> row : rows) {
			for (Cell c : row) {
				rowCard(ctx, x + c.dx() + 5, ry + 1, c.w() - 10, row.get(0).h() - 4);
				renderSettingRow(ctx, c.s(), x + c.dx(), ry, c.w(), mouseX, mouseY, inContent);
			}
			ry += row.get(0).h();
		}
		return y + boxH + GROUP_GAP;
	}

	/** 이 기능에 지정된 첫 키 이름(없으면 null). */
	private String firstKeyName(Module m) {
		for (Setting<?> s : visibleSettings(m)) {
			if (s instanceof KeybindSetting kb && kb.isBound()) {
				return kb.getKeyName();
			}
		}
		return null;
	}

	/** 오른쪽 조작부의 대략적인 폭(연결선을 어디까지 그릴지). */
	/** 49-87차(8-2): 초기화 ↺ 자리 - 조작부 바로 왼쪽. */
	private static final int RESET_W = LunaIcons.SIZE + 6;

	private int resetIconX(int right, Setting<?> s) {
		return right - controlWidth(s) - 8 - RESET_W + 3;
	}

	private int controlWidth(Setting<?> s) {
		if (s instanceof kr.lunaslight.mod.module.setting.InfoSetting inf) {
			return LunaDraw.width(textRenderer, inf.text()) + 2;   // 49-271차: 보기만 하는 줄
		}
		if (s instanceof ActionSetting as) {
			return LunaDraw.width(textRenderer, as.getButtonLabel()) + 18;
		}
		if (s instanceof KeybindListSetting kl) {
			return LunaDraw.width(textRenderer, kl.getButtonLabel()) + 18;
		}
		if (s instanceof PriceListSetting pl) {
			return LunaDraw.width(textRenderer, pl.getButtonLabel()) + 18;
		}
		if (s instanceof BooleanSetting b) {
			return TOGGLE_W + (b.getLinkedColor() != null ? LINK_SW_W + 6 : 0);
		}
		if (s instanceof ColorSetting) {
			return 40;
		}
		if (s instanceof KeybindSetting kb) {
			return LunaDraw.width(textRenderer, kb.getKeyName()) + 16;
		}
		if (s instanceof IntSetting || s instanceof FloatSetting) {
			return 96 + NUMBOX_W + 10;
		}
		if (s instanceof PositionSetting) {
			return 62;   // 49-122차: 좌표 숫자 제거 - [위치 편집] 버튼 폭만. ↺가 버튼 바로 옆에 붙는다.
		}
		if (s instanceof EnumSetting<?> es) {
			// 49-124차(사용자: "선택지 있는 것들 초기화 위치가 이상하잖아"): 예전엔 여기서 70을 돌려줘
			// ↺가 실제 선택지(세그먼트) 폭과 안 맞아 선택지 위에 겹쳤다. 세로 목록은 행 아래에 그려지니
			// 행 오른쪽은 비어 있어 ↺가 맨 오른쪽(폭 0), 가로 세그먼트는 실제 세그먼트 폭만큼 비켜난다.
			return es.isVertical() ? 0 : enumSegmentsWidth(es);
		}
		return 70;
	}

	/** 가로 세그먼트(또는 알약)로 그린 EnumSetting의 오른쪽 조작부 실제 폭 - renderSegments/handleSegmentClick와 같은 계산. */
	private int enumSegmentsWidth(EnumSetting<?> es) {
		Enum<?>[] options = es.getOptions();
		int total = 0;
		for (Enum<?> o : options) {
			total += LunaDraw.width(textRenderer, enumLabel(o)) + 12 + 3;
		}
		total -= 3;
		if (options.length > 4 || total > curRowW - 120) {
			return LunaDraw.width(textRenderer, enumLabel(es.get())) + 16 + 9;   // 자리가 부족하면 현재 값만 알약으로(+ ▾)
		}
		return total;
	}

	/**
	 * 49-93차(사용자: "기능을 끄면 회색으로 어둡게 해서 설정 자체가 안 되게"): 설정 페이지에서 묶음(=모듈) 머리의
	 * 스위치가 꺼져 있으면 그 아래 설정들은 지금 아무 효과가 없으니 흐리게 그리고 클릭도 막는다. 항상 켜진 기능은
	 * 스위치가 없으니 제외. 기능 상세 패널(카드 → 설정, openModule)에서는 켜기 전에 미리 맞춰 둘 수 있게 안 막는다.
	 */
	private boolean ownerDisabled() {
		return rowOwner != null && !rowOwner.isAlwaysOn() && !rowOwner.isEnabled();
	}

	private void renderSettingRow(DrawContext ctx, Setting<?> s, int x, int y, int w, int mouseX, int mouseY,
			boolean inContent) {
		// 49-76차(6-6): 지금 의미 없는 설정(예: 직접 시간을 쓰는 동안의 시간대)은 흐리게 그리고 마우스도 안 받는다
		// 49-93차: 기능(묶음)이 꺼져 있으면 그 설정도 전부 흐리게 + 클릭 차단
		boolean disabled = s.isDisabled() || ownerDisabled();
		if (disabled) {
			float prev = LunaDraw.alpha();
			LunaDraw.setAlpha(prev * 0.35f);
			try {
				renderSettingRowInner(ctx, s, x, y, w, -1, -1, false);
			} finally {
				LunaDraw.setAlpha(prev);
			}
			return;
		}
		renderSettingRowInner(ctx, s, x, y, w, mouseX, mouseY, inContent);
	}

	/** 49-88차(8-7): 지금 그리거나 누르는 설정 행의 폭 - 반반 배치에서 가로 선택지가 칸 밖으로 넘치지 않게 본다. */
	private int curRowW;

	private void renderSettingRowInner(DrawContext ctx, Setting<?> s, int x, int y, int w, int mouseX, int mouseY,
			boolean inContent) {
		curRowW = w;
		boolean hovered = inContent && LunaDraw.in(mouseX, mouseY, x, y, w, ROW_H);
		if (hovered) {
			LunaDraw.roundRect(ctx, x + 3, y + 1, w - 6, ROW_H - 2, 4, ink(0x0F));
			// 49-34차: 설정 설명 툴팁 제거(사용자: "설명 이딴 거 따로 쓰지 말라니까")
		}
		int labelX = x + 12;
		int ty = LunaDraw.textY(y, ROW_H);
		int cy = y + (ROW_H - TOGGLE_H) / 2;
		int right = x + w - 10;

		LunaDraw.text(ctx, textRenderer, s.getDisplayName(), labelX, ty,
			hovered ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
		// 49-29차: 이름과 조작부 사이를 가는 선으로 이어 시선이 끊기지 않게(통계·진단 화면과 같은 결)
		int linkFrom = labelX + LunaDraw.width(textRenderer, s.getDisplayName()) + 8;
		int linkTo = right - controlWidth(s) - 8;
		// 49-87차(8-2): 기본값이 아닌 설정에 초기화 ↺(누르면 기본값). 우클릭 초기화는 예전부터 있었으나 안 보였다.
		// 49-122차(사용자: "기본값일 때 초기화 안뜨게 + 마우스 안 대도 항상 뜨게"): hover 조건 제거 - 기본값이 아니면 늘 뜬다.
		boolean showReset = !s.isDefault() && linkTo - linkFrom > 12 + RESET_W;
		if (showReset) {
			linkTo -= RESET_W;
			int rx = resetIconX(right, s);
			boolean rh = LunaDraw.in(mouseX, mouseY, rx - 2, y, RESET_W + 2, ROW_H);
			LunaIcons.draw(ctx, textRenderer, LunaIcons.RESET, rx, LunaDraw.iconY(y, ROW_H),
				rh ? LunaDraw.ACCENT : LunaDraw.TEXT_SUB);
			if (rh) {
				hoverTip = "기본값으로";
			}
		}
		if (linkTo - linkFrom > 12) {
			LunaDraw.linkTail(ctx, linkFrom, linkTo, ty + 4);
		}

		if (s instanceof BooleanSetting b) {
			LunaDraw.toggle(ctx, right - TOGGLE_W, cy, TOGGLE_W, TOGGLE_H,
				LunaDraw.anim("st:" + rowOwnerId() + ":" + s.getId(), b.get() ? 1f : 0f, 14f), true);
			ColorSetting link = b.getLinkedColor();
			if (link != null) {
				// 49-23차: 색 설정을 스위치 옆 견본 하나로(클릭 = 색 편집기 펼침). 꺼져 있으면 반투명.
				int sx = linkedSwatchX(right);
				boolean open = openColor == link;
				boolean swHover = hovered && LunaDraw.in(mouseX, mouseY, sx, cy, LINK_SW_W, TOGGLE_H + 2);
				float prevA = LunaDraw.alpha();
				if (!b.get()) {
					LunaDraw.setAlpha(prevA * 0.45f);
				}
				LunaDraw.roundRect(ctx, sx, cy, LINK_SW_W, TOGGLE_H + 2, 4,
					open ? LunaDraw.ACCENT : (swHover ? ink(0x70) : ink(0x40)));
				LunaDraw.roundRect(ctx, sx + 1, cy + 1, LINK_SW_W - 2, TOGGLE_H, 3, 0xFF33333D);
				LunaDraw.roundRect(ctx, sx + 1, cy + 1, LINK_SW_W - 2, TOGGLE_H, 3, link.getArgb());
				LunaDraw.setAlpha(prevA);
				if (swHover) {
					hoverTip = link.getDisplayName();
				}
				if (open) {
					renderColorEditor(ctx, link, x, y + ROW_H, w, mouseX, mouseY, inContent);
				}
			}
		} else if (s instanceof IntSetting is) {
			// 49-76차(6-17): 숫자 뒤에 단위(초·블록·틱·px·%)
			renderNumber(ctx, right, y, is.get() + is.getUnit(), ratio(is.get(), is.getMin(), is.getMax()), hovered);
		} else if (s instanceof FloatSetting fs) {
			renderNumber(ctx, right, y, trimFloat(fs.get()) + fs.getUnit(), ratio(fs.get(), fs.getMin(), fs.getMax()), hovered);
		} else if (s instanceof EnumSetting<?> es) {
			if (es.isVertical()) {
				renderVerticalOptions(ctx, es, x, y + ROW_H, w, mouseX, mouseY, inContent);
			} else {
				renderSegments(ctx, es, right, y, mouseX, mouseY);
			}
		} else if (s instanceof kr.lunaslight.mod.module.setting.InfoSetting inf) {
			// 49-271차: 보기만 하는 줄 - 오른쪽에 글자(없으면 흐리게)
			String tv = inf.text();
			LunaDraw.text(ctx, textRenderer, tv, right - LunaDraw.width(textRenderer, tv), ty, inf.empty() ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
		} else if (s instanceof ActionSetting as) {
			// 49-89차: 버튼 설정 - 오른쪽 알약 하나
			String lb = as.getButtonLabel();
			int bw = LunaDraw.width(textRenderer, lb) + 18;
			boolean bh = hovered && LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2);
			LunaDraw.pillButton(ctx, textRenderer, right - bw, cy, bw, TOGGLE_H + 2, lb, bh, true);
		} else if (s instanceof KeybindListSetting kl) {
			// 49-103차: 머리줄 오른쪽에 [추가하기], 아래에 항목마다 [명령어칸 · 키 버튼 · ×] 한 줄.
			String lb = kl.getButtonLabel();
			int bw = LunaDraw.width(textRenderer, lb) + 18;
			boolean addable = kl.size() < kl.capacity();
			boolean bh = addable && hovered && LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2);
			LunaDraw.roundRect(ctx, right - bw, cy, bw, TOGGLE_H + 2, 4,
				!addable ? LunaDraw.TRACK : (bh ? 0xFFC4E796 : LunaDraw.ACCENT));
			LunaDraw.text(ctx, textRenderer, lb, right - bw + 9, LunaDraw.textY(cy, TOGGLE_H + 2),
				addable ? 0xFF0A0F0C : LunaDraw.TEXT_DIM);
			int ox = x + 10;
			int ow = w - 20;
			for (int i = 0; i < kl.size(); i++) {
				renderKeybindEntry(ctx, kl, i, ox, y + ROW_H + i * KENTRY_H, ow, mouseX, mouseY, inContent);
			}
		} else if (s instanceof PriceListSetting pl) {
			renderPriceList(ctx, pl, x, y, w, mouseX, mouseY, inContent, hovered, right, cy);
		} else if (s instanceof KeybindSetting ks) {
			boolean listening = listeningKeybind == ks;
			String label = listening ? "..." : ks.getKeyName();
			int kw = Math.max(48, LunaDraw.width(textRenderer, label) + 20);
			// 49-245차(사용자: "키 설정 버튼 위치가 이상해"): 값 상자와 같은 높이, 같은 세로 자리(줄 가운데)
			int ky = ctrlY(y);
			LunaDraw.roundRectBordered(ctx, right - kw, ky, kw, CTRL_H, 4,
				hovered ? LunaDraw.CARD_HOVER : NUMBOX_BG,
				listening ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
			LunaDraw.text(ctx, textRenderer, label, right - kw + (kw - LunaDraw.width(textRenderer, label)) / 2,
				LunaDraw.textY(ky, CTRL_H), LunaDraw.TEXT);
		} else if (s instanceof PositionSetting) {
			// 49-122차(사용자: "위치 편집 버튼 옆 좌표 숫자 없애"): 좌표 텍스트 제거, [위치 편집] 버튼만.
			int bw = 62;
			int bx = right - bw;
			boolean bh = hovered && LunaDraw.in(mouseX, mouseY, bx, cy, bw, TOGGLE_H + 2);
			LunaDraw.roundRect(ctx, bx, cy, bw, TOGGLE_H + 2, 4, bh ? 0xFFC4E796 : LunaDraw.ACCENT);
			LunaDraw.text(ctx, textRenderer, "위치 편집", bx + (bw - LunaDraw.width(textRenderer, "위치 편집")) / 2,
				LunaDraw.textY(cy, TOGGLE_H + 2), 0xFF0A0F0C);
		} else if (s instanceof StringSetting ss) {
			boolean editing = editingString == ss;
			if (ss.isList()) {
				// 49-76차(6-8): 목록형 - 오른쪽에 [입력 칸][추가], 아래에 항목마다 한 줄 + [×]
				int bw = LunaDraw.width(textRenderer, "추가") + 14;
				int fw = 110;
				int fx = right - bw - 4 - fw;
				LunaDraw.roundRectBordered(ctx, fx, cy, fw, TOGGLE_H + 2, 4, LunaDraw.TRACK,
					editing ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
				String d = ss.draft();
				String shown = d.isEmpty() && !editing ? "이름 입력" : d;
				while (LunaDraw.width(textRenderer, shown) > fw - 10 && shown.length() > 1) {
					shown = shown.substring(1);
				}
				LunaDraw.text(ctx, textRenderer, shown, fx + 5, LunaDraw.textY(cy, TOGGLE_H + 2),
					d.isEmpty() ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
				if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
					int caret = fx + 5 + LunaDraw.width(textRenderer, shown);
					ctx.fill(caret, cy + 2, caret + 1, cy + TOGGLE_H, LunaDraw.applyAlpha(LunaDraw.TEXT));
				}
				boolean can = !d.trim().isEmpty();
				boolean bh = can && hovered && LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2);
				LunaDraw.roundRect(ctx, right - bw, cy, bw, TOGGLE_H + 2, 4,
					!can ? LunaDraw.TRACK : (bh ? 0xFFC4E796 : LunaDraw.ACCENT));
				LunaDraw.text(ctx, textRenderer, "추가", right - bw + 7, LunaDraw.textY(cy, TOGGLE_H + 2),
					can ? 0xFF0A0F0C : LunaDraw.TEXT_DIM);
				java.util.List<String> items = ss.items();
				int ox = x + 10;
				int ow = w - 20;
				for (int i = 0; i < items.size(); i++) {
					int oy = y + ROW_H + i * VOPT_H;
					boolean xh = inContent && LunaDraw.in(mouseX, mouseY, ox + ow - 18, oy, 18, VOPT_H - 2);
					surface(ctx, ox, oy, ow, VOPT_H - 2, cardSolid());
					LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, items.get(i), ow - 34), ox + 8,
						LunaDraw.textY(oy, VOPT_H - 2), LunaDraw.TEXT);
					LunaIcons.draw(ctx, textRenderer, LunaIcons.CLOSE, ox + ow - 14, LunaDraw.iconY(oy, VOPT_H - 2),
						xh ? 0xFFE07A6E : LunaDraw.TEXT_DIM);
				}
			} else {
				int fw = 120;
				LunaDraw.roundRectBordered(ctx, right - fw, cy, fw, TOGGLE_H + 2, 4, LunaDraw.TRACK,
					editing ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
				String v = ss.get() == null ? "" : ss.get();
				String shown = LunaDraw.ellipsize(textRenderer, v, fw - 10);
				LunaDraw.text(ctx, textRenderer, shown, right - fw + 5, LunaDraw.textY(cy, TOGGLE_H + 2), LunaDraw.TEXT);
				if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
					int caret = right - fw + 5 + LunaDraw.width(textRenderer, shown);
					ctx.fill(caret, cy + 2, caret + 1, cy + TOGGLE_H, LunaDraw.applyAlpha(LunaDraw.TEXT));
				}
			}
		} else if (s instanceof ColorSetting cs) {
			int argb = cs.getArgb();
			int swW = 34;
			int sx = right - swW;
			LunaDraw.roundRect(ctx, sx, cy, swW, TOGGLE_H + 2, 4, openColor == cs ? LunaDraw.ACCENT : ink(0x40));
			LunaDraw.roundRect(ctx, sx + 1, cy + 1, swW - 2, TOGGLE_H, 3, 0xFF33333D);
			LunaDraw.roundRect(ctx, sx + 1, cy + 1, swW - 2, TOGGLE_H, 3, argb);
			if (openColor == cs) {
				renderColorEditor(ctx, cs, x, y + ROW_H, w, mouseX, mouseY, inContent);
			}
		}
	}

	/**
	 * 49-103차: 키바인드 항목 한 줄 - [N번] [명령어/글 입력칸] [키 버튼] [×]. 명령어칸은 editingString,
	 * 키 버튼은 listeningKeybind 기존 상태를 그대로 쓰므로 입력·캡처 로직은 재사용된다.
	 */
	private void renderKeybindEntry(DrawContext ctx, KeybindListSetting kl, int i, int ox, int oy, int ow,
			int mouseX, int mouseY, boolean inContent) {
		int h = KENTRY_H - 2;
		surface(ctx, ox, oy, ow, h, cardSolid());
		int ih = 14;
		int iy = oy + (h - ih) / 2;
		String num = (i + 1) + "번";
		LunaDraw.text(ctx, textRenderer, num, ox + 8, LunaDraw.textY(oy, h), LunaDraw.TEXT_SUB);
		int numW = LunaDraw.width(textRenderer, num);

		int delX = ox + ow - 16;
		boolean xh = inContent && LunaDraw.in(mouseX, mouseY, delX - 2, oy, 18, h);

		KeybindSetting key = kl.keyAt(i);
		boolean listening = listeningKeybind == key;
		String kb = listening ? "..." : key.getKeyName();
		int kbw = Math.max(46, LunaDraw.width(textRenderer, kb) + 14);
		int keyX = delX - 8 - kbw;

		StringSetting text = kl.textAt(i);
		boolean editing = editingString == text;
		int fx = ox + 8 + numW + 8;
		int fw = Math.max(24, keyX - 8 - fx);

		// 명령어/글 입력칸
		LunaDraw.roundRectBordered(ctx, fx, iy, fw, ih, 4, LunaDraw.TRACK, editing ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		String v = text.get() == null ? "" : text.get();
		String shown = v.isEmpty() && !editing ? "명령어 / 글" : v;
		while (LunaDraw.width(textRenderer, shown) > fw - 10 && shown.length() > 1) {
			shown = shown.substring(1);   // 길면 뒤(캐럿 쪽)를 보이게 앞을 줄인다
		}
		LunaDraw.text(ctx, textRenderer, shown, fx + 5, LunaDraw.textY(iy, ih),
			v.isEmpty() && !editing ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
		if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caret = fx + 5 + LunaDraw.width(textRenderer, shown);
			ctx.fill(caret, iy + 2, caret + 1, iy + ih - 2, LunaDraw.applyAlpha(LunaDraw.TEXT));
		}

		// 키 버튼
		boolean kh = inContent && LunaDraw.in(mouseX, mouseY, keyX, iy, kbw, ih);
		LunaDraw.roundRectBordered(ctx, keyX, iy, kbw, ih, 4,
			kh ? LunaDraw.CARD_HOVER : LunaDraw.TRACK, listening ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		LunaDraw.text(ctx, textRenderer, kb, keyX + (kbw - LunaDraw.width(textRenderer, kb)) / 2,
			LunaDraw.textY(iy, ih), LunaDraw.TEXT);

		// 삭제 ×
		LunaIcons.draw(ctx, textRenderer, LunaIcons.CLOSE, delX, LunaDraw.iconY(oy, h),
			xh ? 0xFFE07A6E : LunaDraw.TEXT_DIM);
	}

	// ==================== 49-110차: 가격표 등록 위젯 ====================

	private void renderPriceList(DrawContext ctx, PriceListSetting pl, int x, int y, int w,
			int mouseX, int mouseY, boolean inContent, boolean hovered, int right, int cy) {
		// 머리줄 오른쪽 [추가하기]
		String lb = pl.getButtonLabel();
		int bw = LunaDraw.width(textRenderer, lb) + 18;
		boolean bh = hovered && LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2);
		LunaDraw.roundRect(ctx, right - bw, cy, bw, TOGGLE_H + 2, 4, bh ? 0xFFC4E796 : LunaDraw.ACCENT);
		LunaDraw.text(ctx, textRenderer, lb, right - bw + 9, LunaDraw.textY(cy, TOGGLE_H + 2), 0xFF0A0F0C);

		// 49-122차(사용자: "추가하는 부분 아래 따로 박스로"): 표 칩 + 항목(또는 안내)을 한 박스로 감싼다.
		int rows = Math.max(1, pl.entries().size());
		LunaDraw.roundRectBordered(ctx, x + 6, y + ROW_H, w - 12, PCHIP_H + rows * PENTRY_H + 4, 6,
			LunaDraw.CARD, LunaDraw.CARD_BORDER);

		int ox = x + 10;
		int ow = w - 20;
		// 표 칩 줄(작물 / 광물 …)
		int chipY = y + ROW_H + 2;
		String active = pl.activeTable();
		int cxp = ox;
		for (String name : pl.tableNames()) {
			int cw = LunaDraw.width(textRenderer, name) + 16;
			boolean on = name.equals(active);
			boolean chov = inContent && LunaDraw.in(mouseX, mouseY, cxp, chipY, cw, 16);
			LunaDraw.card3d(ctx, cxp, chipY, cw, 16, chov ? 1f : 0f, on);   // 49-227차
			LunaDraw.text(ctx, textRenderer, name, cxp + 8, LunaDraw.textY(chipY, 16), on ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
			cxp += cw + 4;
		}
		// 항목들
		int i = 0;
		for (Map.Entry<String, Double> e : pl.entries().entrySet()) {
			renderPriceEntry(ctx, pl, e.getKey(), e.getValue(), ox, y + ROW_H + PCHIP_H + i * PENTRY_H, ow,
				mouseX, mouseY, inContent);
			i++;
		}
		if (i == 0) {
			LunaDraw.text(ctx, textRenderer, "[추가하기]로 작물/광물을 골라 가격을 정하세요", ox + 2,
				LunaDraw.textY(y + ROW_H + PCHIP_H, PENTRY_H - 2), LunaDraw.TEXT_DIM);
		}
	}

	private void renderPriceEntry(DrawContext ctx, PriceListSetting pl, String id, double price, int ox, int oy, int ow,
			int mouseX, int mouseY, boolean inContent) {
		int h = PENTRY_H - 2;
		surface(ctx, ox, oy, ow, h, cardSolid());
		int ih = 14;
		int iy = oy + (h - ih) / 2;
		ItemStack st = priceItemStack(id);
		if (st != null && !st.isEmpty()) {
			ctx.drawItem(st, ox + 4, oy + (h - 16) / 2);
		}
		int delX = ox + ow - 16;
		boolean xh = inContent && LunaDraw.in(mouseX, mouseY, delX - 2, oy, 18, h);
		// 가격 입력칸(× 앞)
		boolean editing = priceEditSetting == pl && id.equals(priceEditId);
		int fw = 58;
		int fx = delX - 8 - fw;
		LunaDraw.roundRectBordered(ctx, fx, iy, fw, ih, 4, LunaDraw.TRACK, editing ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		String disp = editing ? priceEditBuf : HarvestLog.trim(price);
		if (disp.isEmpty() && !editing) {
			disp = "0";
		}
		LunaDraw.text(ctx, textRenderer, disp, fx + 5, LunaDraw.textY(iy, ih), LunaDraw.TEXT);
		if (editing && (System.currentTimeMillis() / 500) % 2 == 0) {
			int caret = fx + 5 + LunaDraw.width(textRenderer, disp);
			ctx.fill(caret, iy + 2, caret + 1, iy + ih - 2, LunaDraw.applyAlpha(LunaDraw.TEXT));
		}
		// 이름(아이콘 오른쪽 ~ 가격칸 왼쪽)
		int nameX = ox + 4 + 18;
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, priceItemName(id), fx - 6 - nameX),
			nameX, LunaDraw.textY(oy, h), LunaDraw.TEXT);
		// 삭제 ×
		LunaIcons.draw(ctx, textRenderer, LunaIcons.CLOSE, delX, LunaDraw.iconY(oy, h),
			xh ? 0xFFE07A6E : LunaDraw.TEXT_DIM);
	}

	private static ItemStack priceItemStack(String id) {
		Item it = LunaCompat.itemById(id);
		return it == null ? ItemStack.EMPTY : new ItemStack(it);
	}

	private static String priceItemName(String id) {
		Item it = LunaCompat.itemById(id);
		if (it == null) {
			return id.startsWith("minecraft:") ? id.substring(10) : id;
		}
		try {
			return new ItemStack(it).getName().getString();
		} catch (Throwable t) {
			return id;
		}
	}

	/** 편집 중이던 가격을 확정해서 저장한다. */
	private void commitPriceEdit() {
		if (priceEditSetting == null || priceEditId == null) {
			return;
		}
		try {
			String b = priceEditBuf.trim();
			double v = b.isEmpty() ? 0 : Double.parseDouble(b);
			priceEditSetting.setPrice(priceEditId, Math.max(0, v));
		} catch (Throwable ignored) {
		}
		priceEditSetting = null;
		priceEditId = null;
		priceEditBuf = "";
	}

	// ---- 아이템 피커 모달 ----
	private static final int PICK_W = 306, PICK_H = 226, PICK_COLS = 8, PICK_CELL = 34, PICK_HEAD = 26;

	private void renderPricePicker(DrawContext ctx, int mouseX, int mouseY) {
		PriceListSetting pl = pickingPrices;
		if (pl == null) {
			return;
		}
		ctx.fill(0, 0, width, height, 0x99000000);
		int bx = modalX(PICK_W);
		int by = modalY(PICK_H);
		LunaDraw.roundRectBordered(ctx, bx, by, PICK_W, PICK_H, 6, MODAL_BG, LunaDraw.CARD_BORDER);
		LunaDraw.text(ctx, textRenderer, "아이템 고르기 | " + pl.activeTable(), bx + 12, by + 8, LunaDraw.TEXT);
		boolean cx = LunaDraw.in(mouseX, mouseY, bx + PICK_W - 24, by + 6, 18, 18);
		LunaIcons.draw(ctx, textRenderer, LunaIcons.CLOSE, bx + PICK_W - 22, LunaDraw.iconY(by + 6, 18),
			cx ? LunaDraw.TEXT : LunaDraw.TEXT_DIM);

		int gx = bx + 10;
		int gy = by + PICK_HEAD;
		int gw = PICK_W - 20;
		int gh = PICK_H - PICK_HEAD - 8;
		List<String> ids = pl.pickList();
		int rows = (ids.size() + PICK_COLS - 1) / PICK_COLS;
		int contentH = rows * PICK_CELL;
		double maxScroll = Math.max(0, contentH - gh);
		if (pickerScroll < 0) {
			pickerScroll = 0;
		}
		if (pickerScroll > maxScroll) {
			pickerScroll = maxScroll;
		}
		scis(ctx, gx, gy, gx + gw, gy + gh);
		int cellW = gw / PICK_COLS;
		for (int i = 0; i < ids.size(); i++) {
			int r = i / PICK_COLS;
			int c = i % PICK_COLS;
			int cellX = gx + c * cellW;
			int cellY = gy + r * PICK_CELL - (int) pickerScroll;
			if (cellY + PICK_CELL < gy || cellY > gy + gh) {
				continue;
			}
			boolean hov = LunaDraw.in(mouseX, mouseY, cellX, cellY, cellW, PICK_CELL)
				&& mouseY >= gy && mouseY < gy + gh;
			if (hov) {
				LunaDraw.roundRect(ctx, cellX + 1, cellY + 1, cellW - 2, PICK_CELL - 2, 4, ink(0x22));
			}
			ItemStack st = priceItemStack(ids.get(i));
			if (st != null && !st.isEmpty()) {
				ctx.drawItem(st, cellX + (cellW - 16) / 2, cellY + (PICK_CELL - 16) / 2);
			}
			if (hov) {
				hoverTip = priceItemName(ids.get(i));
			}
		}
		ctx.disableScissor();
	}

	private void handlePriceListClick(PriceListSetting pl, int x, int y, int w, double mouseX, double mouseY, int button) {
		int right = x + w - 10;
		int cy = y + (ROW_H - TOGGLE_H) / 2;
		if (mouseY < y + ROW_H) {
			int bw = LunaDraw.width(textRenderer, pl.getButtonLabel()) + 18;
			if (LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2)) {
				commitPriceEdit();
				pickingPrices = pl;
				pickerScroll = 0;
			}
			return;
		}
		int ox = x + 10;
		int ow = w - 20;
		int chipY = y + ROW_H + 2;
		if (mouseY >= chipY && mouseY < chipY + 16) {
			int cxp = ox;
			for (String name : pl.tableNames()) {
				int cw = LunaDraw.width(textRenderer, name) + 16;
				if (LunaDraw.in(mouseX, mouseY, cxp, chipY, cw, 16)) {
					commitPriceEdit();
					pl.setActiveTable(name);
					return;
				}
				cxp += cw + 4;
			}
			return;
		}
		int i = 0;
		for (Map.Entry<String, Double> e : pl.entries().entrySet()) {
			int oy = y + ROW_H + PCHIP_H + i * PENTRY_H;
			i++;
			if (mouseY < oy || mouseY >= oy + PENTRY_H) {
				continue;
			}
			int h = PENTRY_H - 2;
			int ih = 14;
			int iy = oy + (h - ih) / 2;
			int delX = ox + ow - 16;
			if (LunaDraw.in(mouseX, mouseY, delX - 2, oy, 18, h)) {
				commitPriceEdit();
				pl.remove(e.getKey());
				return;
			}
			int fw = 58;
			int fx = delX - 8 - fw;
			if (LunaDraw.in(mouseX, mouseY, fx, iy, fw, ih)) {
				commitPriceEdit();
				priceEditSetting = pl;
				priceEditId = e.getKey();
				priceEditBuf = HarvestLog.trim(e.getValue());
				return;
			}
			return;
		}
	}

	/** 값 상자 + 슬라이더(레퍼런스와 동일한 배치). */
	private void renderNumber(DrawContext ctx, int right, int y, String value, float ratio, boolean hovered) {
		int sliderW = 96;
		int sx = right - sliderW;
		// 49-76차(6-17): 단위가 붙어 "24000틱"처럼 길어질 수 있어 상자가 글자에 맞춰 늘어난다
		int boxW = Math.max(NUMBOX_W, LunaDraw.width(textRenderer, value) + 10);
		int bx = sx - 6 - boxW;
		int by = ctrlY(y);
		LunaDraw.roundRectBordered(ctx, bx, by, boxW, CTRL_H, 4, NUMBOX_BG, LunaDraw.CARD_BORDER);
		LunaDraw.text(ctx, textRenderer, value, bx + (boxW - LunaDraw.width(textRenderer, value)) / 2,
			LunaDraw.textY(by, CTRL_H), LunaDraw.TEXT);
		LunaDraw.slider(ctx, sx, y + (ROW_H - CTRL_H) / 2, sliderW, ratio, hovered);
	}

	/** 선택지 = 세그먼트 버튼(레퍼런스의 [배경][윤곽][텍스트만]). 너무 넓으면 값만 표시. */
	/**
	 * 49-47차: 가로 세그먼트 대신 <b>세로 목록</b>. 항목마다 한 줄 카드(왼쪽에 선택 표시 막대 + 이름)라
	 * 이름이 길어도 안 뭉개지고, 지금 무엇이 골라져 있는지 한눈에 보인다.
	 */
	private void renderVerticalOptions(DrawContext ctx, EnumSetting<?> es, int x, int y, int w,
			int mouseX, int mouseY, boolean inContent) {
		Enum<?>[] options = es.getOptions();
		int ox = x + 10;
		int ow = w - 20;
		for (int i = 0; i < options.length; i++) {
			int oy = y + i * VOPT_H;
			boolean on = options[i] == es.get();
			boolean hov = inContent && LunaDraw.in(mouseX, mouseY, ox, oy, ow, VOPT_H - 2);
			// 49-84차(사용자: "선택된 거 흰색으로 돼서 글이 안 보여 너무 밝아"): 고른 항목 배경을 밝은 강조
			// 틴트(ACCENT_SOFT - 밝은 강조색을 쓰면 흰 글씨가 묻힌다)에서 어두운 칩(TRACK)으로. 강조는 테두리로.
			LunaDraw.roundRectBordered(ctx, ox, oy, ow, VOPT_H - 2, 4,
				on ? LunaDraw.TRACK : (hov ? LunaDraw.CARD_HOVER : LunaDraw.CARD),
				on ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
			// 49-78차(사용자: "막대기 너무 많다"): 왼쪽 강조 바 삭제 - 고른 항목은 배경·테두리 색으로 보인다.
			// 49-76차(6-14, 사용자: "글꼴 미리보기는 해당 글꼴로 표시 무조건"): 글꼴 목록은 항목마다 그 글꼴로 그린다
			net.minecraft.text.Text styled = fontSampleText(options[i], enumLabel(options[i]));
			if (styled != null) {
				ctx.drawText(textRenderer, styled, ox + 8, LunaDraw.textY(oy, VOPT_H - 2),
					LunaDraw.applyAlpha(on ? LunaDraw.TEXT : LunaDraw.TEXT_SUB), false);
			} else {
				LunaDraw.text(ctx, textRenderer, enumLabel(options[i]), ox + 8, LunaDraw.textY(oy, VOPT_H - 2),
					on ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
			}
		}
	}

	/**
	 * 글꼴 선택지는 <b>그 글꼴로</b> 보여 준다. 마크 기본은 바닐라 글꼴 그대로(스타일 없음), 갈무리는 mchan,
	 * 모던은 ui. 1.20 미만에서 갈무리는 어차피 바닐라로 떨어지므로 그때는 바닐라로 보인다 - 실제와 같다.
	 */
	private net.minecraft.text.Text fontSampleText(Enum<?> option, String label) {
		// 49-80차(5-2): 갈무리 크기 목록도 항목마다 그 크기로 - 7/9/11이 실제로 어떻게 보이는지 고르기 전에 보인다
		if (option instanceof kr.lunaslight.mod.module.impl.misc.InterfaceStyleModule.PixelSize ps) {
			try {
				String base = ps.ink == 9 ? "mchan9" : (ps.ink == 11 ? "mchan11" : "mchan");
				Object st = kr.lunaslight.mod.util.LunaVersion.isWithin("1.20", null) ? LunaCompat.fontStyle(base) : null;
				net.minecraft.text.Text t = st == null ? null : LunaCompat.styledText(label, st);
				return t != null ? t : LunaCompat.textLiteral(label);
			} catch (Throwable ignored) {
				return null;
			}
		}
		if (!(option instanceof kr.lunaslight.mod.module.impl.misc.InterfaceStyleModule.FontStyle fs)) {
			return null;
		}
		try {
			switch (fs) {
				case MC:
					return LunaCompat.textLiteral(label);
				case MC_HANGUL: {
					Object st = kr.lunaslight.mod.util.LunaVersion.isWithin("1.20", null) ? LunaCompat.fontStyle(LunaCompat.pixelBase()) : null;
					net.minecraft.text.Text t = st == null ? null : LunaCompat.styledText(label, st);
					return t != null ? t : LunaCompat.textLiteral(label);
				}
				default: {
					net.minecraft.text.Text t = LunaCompat.styledText(label, LunaCompat.fontStyle("ui"));
					return t != null ? t : LunaCompat.textLiteral(label);
				}
			}
		} catch (Throwable ignored) {
			return null;
		}
	}

	private void renderSegments(DrawContext ctx, EnumSetting<?> es, int right, int y, int mouseX, int mouseY) {
		Enum<?>[] options = es.getOptions();
		int[] widths = new int[options.length];
		int total = 0;
		for (int i = 0; i < options.length; i++) {
			widths[i] = LunaDraw.width(textRenderer, enumLabel(options[i])) + 12;
			total += widths[i] + 3;
		}
		total -= 3;
		int maxW = curRowW - 120;
		int cy = ctrlY(y);
		if (options.length > 4 || total > maxW) {
			// 자리가 부족하면 현재 값만 알약으로(클릭 = 다음 값)
			String v = enumLabel(es.get());
			int vw = LunaDraw.width(textRenderer, v) + 16 + 9;
			boolean hovered = LunaDraw.in(mouseX, mouseY, right - vw, cy, vw, CTRL_H) || dropdown == es;
			LunaDraw.roundRectBordered(ctx, right - vw, cy, vw, CTRL_H, 4,
				hovered ? LunaDraw.CARD_HOVER : LunaDraw.TRACK,
				LunaDraw.withAlpha(LunaDraw.ACCENT, hovered ? 0x8C : 0x4D));
			LunaDraw.text(ctx, textRenderer, v, right - vw + 8, LunaDraw.textY(cy, CTRL_H), LunaDraw.ACCENT);
			// 49-172차: 펼침 표시(작은 ▾)
			int chx = right - 10, chy = cy + CTRL_H / 2 - 1;
			ctx.fill(chx - 2, chy - 1, chx + 3, chy, LunaDraw.applyAlpha(LunaDraw.TEXT_DIM));
			ctx.fill(chx - 1, chy, chx + 2, chy + 1, LunaDraw.applyAlpha(LunaDraw.TEXT_DIM));
			ctx.fill(chx, chy + 1, chx + 1, chy + 2, LunaDraw.applyAlpha(LunaDraw.TEXT_DIM));
			if (dropdown == es) {
				// 목록 자리 기억(그리기는 renderAll 끝에서 맨 위에)
				ddX = right - vw;
				ddY = cy + CTRL_H + 2;
				ddW = vw;
				for (Enum<?> o : options) {
					ddW = Math.max(ddW, LunaDraw.width(textRenderer, enumLabel(o)) + 20);
				}
				ddX = Math.min(ddX, right - ddW);
			}
			return;
		}
		int sx = right - total;
		// 49-22차: 선택 강조가 옵션 사이를 미끄러져 이동하는 애니메이션
		int selX = sx, selW = widths[0];
		int px2 = sx;
		for (int i = 0; i < options.length; i++) {
			if (options[i] == es.get()) {
				selX = px2;
				selW = widths[i];
			}
			px2 += widths[i] + 3;
		}
		// 스크롤로 행이 움직여도 흔들리지 않게 그룹 시작점 기준 상대 좌표로 보간
		String key = "seg:" + rowOwnerId() + ":" + es.getId();
		int ax = sx + Math.round(LunaDraw.anim(key + ":x", selX - sx, 22f));
		int aw = Math.round(LunaDraw.anim(key + ":w", selW, 22f));
		for (int i = 0; i < options.length; i++) {
			boolean hovered = LunaDraw.in(mouseX, mouseY, sx, cy, widths[i], CTRL_H);
			LunaDraw.roundRectBordered(ctx, sx, cy, widths[i], CTRL_H, 4,
				hovered ? LunaDraw.CARD_HOVER : LunaDraw.TRACK, LunaDraw.CARD_BORDER);
			sx += widths[i] + 3;
		}
		LunaDraw.roundRectBordered(ctx, ax, cy, aw, CTRL_H, 4,
			LunaDraw.withAlpha(LunaDraw.ACCENT, 0x1A), LunaDraw.withAlpha(LunaDraw.ACCENT, 0x6B));
		sx = right - total;
		for (int i = 0; i < options.length; i++) {
			boolean sel = options[i] == es.get();
			boolean hovered = LunaDraw.in(mouseX, mouseY, sx, cy, widths[i], CTRL_H);
			LunaDraw.text(ctx, textRenderer, enumLabel(options[i]), sx + 6, LunaDraw.textY(cy, CTRL_H),
				sel ? LunaDraw.ACCENT : (hovered ? LunaDraw.TEXT : LunaDraw.TEXT_DIM));
			sx += widths[i] + 3;
		}
	}

	// ==================== 49-126차: 색 고르기(팔레트) ====================
	// 사용자: "색 결정하는 거 하나하나 수치 입력 말고 #~~~ 이거랑 팔레트 펼쳐서 하는 거로". R/G/B/A 숫자 슬라이더를
	// 없애고 그림판식 팔레트로 바꿨다: 왼쪽 큰 네모 = 채도(가로)/밝기(세로), 그 옆 세로 막대 = 색상(무지개),
	// 그 옆 = 투명도. 오른쪽에 지금 색 미리보기, #색코드 입력, 자주 쓰는 색. 전부 fill/fillGradient만 쓴다.

	/** 팔레트 네모의 한 변(편집기 폭에 맞춰 40~78). */
	private static int paletteSize(int w) {
		return Math.max(40, Math.min(COLOR_EDITOR_H - 12, w - 14 - 40 - 110));
	}

	/** 팔레트 요소 위치 {네모 x, 네모 y, 한 변, 색상 막대 x, 투명도 막대 x, 오른쪽 칸 x}. y = 편집기 위쪽. */
	private static int[] paletteLayout(int x, int y, int w) {
		int sq = paletteSize(w);
		int px = x + 14;
		int py = y + 6;
		int hueX = px + sq + 6;
		int alphaX = hueX + PAL_BAR_W + 5;
		int rightX = alphaX + PAL_BAR_W + 12;
		return new int[]{px, py, sq, hueX, alphaX, rightX};
	}

	private static final int PAL_BAR_W = 9;

	/** 편집 중인 색의 HSV(0~1) - 회색(채도 0)으로 가도 고르던 색상을 잃지 않게 따로 들고 있는다. */
	private float palH, palS, palV;
	private int palSyncedArgb = 0x12345678;
	private Object palOwner;

	private void syncPalette(ColorSetting cs, int argb) {
		if (palOwner == cs && palSyncedArgb == argb) {
			return;
		}
		float[] hsv = rgbToHsv(argb);
		if (palOwner != cs || hsv[1] > 0.001f) {
			palH = hsv[0];   // 회색이면 색상은 고르던 값 유지
		}
		if (palOwner != cs || hsv[2] > 0.001f) {
			palS = hsv[1];   // 검정이면 채도도 유지
		}
		palV = hsv[2];
		palOwner = cs;
		palSyncedArgb = argb;
	}

	private void applyPalette(ColorSetting cs, int alpha) {
		int argb = (hsvToRgb(palH, palS, palV) & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
		palSyncedArgb = argb;
		palOwner = cs;
		cs.setValue(argb);
	}

	static int hsvToRgb(float h, float s, float v) {
		h = (h % 1f + 1f) % 1f;
		float c = v * s;
		float hp = h * 6f;
		float x = c * (1 - Math.abs(hp % 2 - 1));
		float r = 0, g = 0, b = 0;
		switch ((int) hp) {
			case 0 -> { r = c; g = x; }
			case 1 -> { r = x; g = c; }
			case 2 -> { g = c; b = x; }
			case 3 -> { g = x; b = c; }
			case 4 -> { r = x; b = c; }
			default -> { r = c; b = x; }
		}
		float m = v - c;
		int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
		return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
	}

	static float[] rgbToHsv(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h = 0;
		if (d > 0) {
			if (max == r) {
				h = ((g - b) / d) % 6f;
			} else if (max == g) {
				h = (b - r) / d + 2f;
			} else {
				h = (r - g) / d + 4f;
			}
			h /= 6f;
			if (h < 0) {
				h += 1f;
			}
		}
		return new float[]{h, max == 0 ? 0 : d / max, max};
	}

	/** 투명도가 보이게 까는 체크무늬. */
	private static void checker(DrawContext ctx, int x, int y, int w, int h) {
		int c = 4;
		for (int yy = 0; yy < h; yy += c) {
			for (int xx = 0; xx < w; xx += c) {
				boolean dark = ((xx / c) + (yy / c)) % 2 == 0;
				ctx.fill(x + xx, y + yy, x + Math.min(w, xx + c), y + Math.min(h, yy + c),
					LunaDraw.applyAlpha(dark ? 0xFF3A3A44 : 0xFF6A6A76));
			}
		}
	}

	private static void frame(DrawContext ctx, int x, int y, int w, int h, int color) {
		int c = LunaDraw.applyAlpha(color);
		ctx.fill(x - 1, y - 1, x + w + 1, y, c);
		ctx.fill(x - 1, y + h, x + w + 1, y + h + 1, c);
		ctx.fill(x - 1, y, x, y + h, c);
		ctx.fill(x + w, y, x + w + 1, y + h, c);
	}

	private void renderColorEditor(DrawContext ctx, ColorSetting cs, int x, int y, int w, int mouseX, int mouseY,
			boolean inContent) {
		int argb = cs.getArgb();
		syncPalette(cs, argb);
		int alpha = (argb >>> 24) & 0xFF;
		int[] L = paletteLayout(x, y, w);
		int px = L[0], py = L[1], sq = L[2], hueX = L[3], alphaX = L[4], rx = L[5];

		// 1) 채도/밝기 네모: 세로 줄마다 위(그 채도·밝기 최대) → 아래(검정) 그라데이션
		for (int i = 0; i < sq; i++) {
			float s = i / (float) (sq - 1);
			int top = hsvToRgb(palH, s, 1f);
			ctx.fillGradient(px + i, py, px + i + 1, py + sq, LunaDraw.applyAlpha(top), LunaDraw.applyAlpha(0xFF000000));
		}
		frame(ctx, px, py, sq, sq, colorDragChannel == 10 ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		int mx = px + Math.round(palS * (sq - 1));
		int my = py + Math.round((1 - palV) * (sq - 1));
		frame(ctx, mx - 2, my - 2, 5, 5, 0xFF000000);
		frame(ctx, mx - 1, my - 1, 3, 3, 0xFFFFFFFF);

		// 2) 색상 막대(무지개, 위→아래)
		for (int k = 0; k < 6; k++) {
			int y0 = py + sq * k / 6;
			int y1 = py + sq * (k + 1) / 6;
			ctx.fillGradient(hueX, y0, hueX + PAL_BAR_W, y1,
				LunaDraw.applyAlpha(hsvToRgb(k / 6f, 1, 1)), LunaDraw.applyAlpha(hsvToRgb((k + 1) / 6f, 1, 1)));
		}
		frame(ctx, hueX, py, PAL_BAR_W, sq, colorDragChannel == 11 ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		int hy = py + Math.round(palH * (sq - 1));
		ctx.fill(hueX - 2, hy - 1, hueX + PAL_BAR_W + 2, hy + 2, LunaDraw.applyAlpha(0xFF000000));
		ctx.fill(hueX - 1, hy, hueX + PAL_BAR_W + 1, hy + 1, LunaDraw.applyAlpha(0xFFFFFFFF));

		// 3) 투명도 막대(위 = 불투명 → 아래 = 투명)
		checker(ctx, alphaX, py, PAL_BAR_W, sq);
		int rgb = argb & 0x00FFFFFF;
		ctx.fillGradient(alphaX, py, alphaX + PAL_BAR_W, py + sq,
			LunaDraw.applyAlpha(0xFF000000 | rgb), LunaDraw.applyAlpha(rgb));
		frame(ctx, alphaX, py, PAL_BAR_W, sq, colorDragChannel == 12 ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		int ay = py + Math.round((1 - alpha / 255f) * (sq - 1));
		ctx.fill(alphaX - 2, ay - 1, alphaX + PAL_BAR_W + 2, ay + 2, LunaDraw.applyAlpha(0xFF000000));
		ctx.fill(alphaX - 1, ay, alphaX + PAL_BAR_W + 1, ay + 1, LunaDraw.applyAlpha(0xFFFFFFFF));

		// 4) 오른쪽: 미리보기 + #색코드 + 자주 쓰는 색
		int rw = Math.max(60, x + w - 14 - rx);
		int pvW = Math.min(rw, 96);
		checker(ctx, rx, py, pvW, 18);
		ctx.fill(rx, py, rx + pvW, py + 18, LunaDraw.applyAlpha(argb));
		frame(ctx, rx, py, pvW, 18, LunaDraw.CARD_BORDER);
		// 49-195차(사용자: "색 초기화도 만들기"): 미리보기 옆 [초기화] - 누르면 이 색만 기본값으로(스위치 옆 색도 같은 편집기라 함께 된다)
		int[] rb = colorResetBox(rx, py, pvW);
		boolean isDef = cs.isDefault();
		boolean rbh = inContent && !isDef && LunaDraw.in(mouseX, mouseY, rb[0], rb[1], rb[2], rb[3]);
		LunaDraw.roundRectBordered(ctx, rb[0], rb[1], rb[2], rb[3], 4, rbh ? ink(0x24) : LunaDraw.TRACK,
			rbh ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		LunaIcons.draw(ctx, textRenderer, LunaIcons.RESET, rb[0] + 4, LunaDraw.iconY(rb[1], rb[3]),
			isDef ? LunaDraw.TEXT_DIM : (rbh ? LunaDraw.ACCENT : LunaDraw.TEXT_SUB));
		LunaDraw.text(ctx, textRenderer, "초기화", rb[0] + 4 + LunaIcons.SIZE + 2, LunaDraw.textY(rb[1], rb[3]),
			isDef ? LunaDraw.TEXT_DIM : (rbh ? LunaDraw.TEXT : LunaDraw.TEXT_SUB));
		String pct = Math.round(alpha * 100 / 255f) + "%";
		if (alpha < 255) {
			LunaDraw.text(ctx, textRenderer, "투명도 " + pct, rb[0] + rb[2] + 6, LunaDraw.textY(py, 18), LunaDraw.TEXT_DIM);
		}

		int hexY = py + 24;
		boolean editing = editingHex == cs;
		int hw = Math.min(rw, 96);
		LunaDraw.roundRectBordered(ctx, rx, hexY, hw, 14, 4, LunaDraw.TRACK,
			editing ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		String shown;
		int tc;
		if (editing) {
			shown = hexBuffer.isEmpty() || "#".equals(hexBuffer) ? "#" : hexBuffer;
			tc = LunaDraw.TEXT;
		} else {
			shown = hexOf(argb);
			tc = LunaDraw.TEXT_SUB;
		}
		LunaDraw.text(ctx, textRenderer, shown, rx + 5, LunaDraw.textY(hexY, 14), tc);
		if (editing) {
			int caret = rx + 5 + LunaDraw.width(textRenderer, shown);
			if ("#".equals(shown)) {
				LunaDraw.text(ctx, textRenderer, hexOf(argb).substring(1), caret + 1, LunaDraw.textY(hexY, 14),
					LunaDraw.withAlpha(LunaDraw.TEXT_DIM, 0x80));
			}
			if ((System.currentTimeMillis() / 500) % 2 == 0) {
				ctx.fill(caret, hexY + 2, caret + 1, hexY + 12, LunaDraw.applyAlpha(LunaDraw.TEXT));
			}
		}
		LunaDraw.text(ctx, textRenderer, editing ? "Enter 확정 | Ctrl+V" : "눌러서 입력", rx + hw + 6,
			LunaDraw.textY(hexY, 14), LunaDraw.TEXT_DIM);

		int prY = hexY + 20;
		int per = Math.max(1, Math.min(COLOR_PRESETS.length, (rw + 4) / 16));
		for (int i = 0; i < COLOR_PRESETS.length; i++) {
			int sx = rx + (i % per) * 16;
			int sy = prY + (i / per) * 14;
			int c = (COLOR_PRESETS[i] & 0x00FFFFFF) | 0xFF000000;
			boolean same = (COLOR_PRESETS[i] & 0x00FFFFFF) == rgb;
			boolean hov = inContent && LunaDraw.in(mouseX, mouseY, sx, sy, 13, 11);
			LunaDraw.roundRect(ctx, sx, sy, 13, 11, 3, same ? LunaDraw.ACCENT : (hov ? ink(0x80) : ink(0x40)));
			LunaDraw.roundRect(ctx, sx + 1, sy + 1, 11, 9, 2, c);
		}
	}

	/** 49-195차: 색 편집기 [초기화] 버튼 자리 {x, y, w, h} - 미리보기 오른쪽, 같은 줄. */
	private int[] colorResetBox(int rx, int py, int pvW) {
		int w = 4 + LunaIcons.SIZE + 2 + LunaDraw.width(textRenderer, "초기화") + 6;
		return new int[]{rx + pvW + 6, py + 2, w, 14};
	}

	/** 0xAARRGGBB → "#RRGGBB"(불투명) / "#AARRGGBB". */
	static String hexOf(int argb) {
		int a = (argb >>> 24) & 0xFF;
		if (a == 0xFF) {
			return String.format("#%06X", argb & 0x00FFFFFF);
		}
		return String.format("#%08X", argb);
	}

	/** "#RGB" / "#RRGGBB" / "#AARRGGBB"(# 생략 가능) → ARGB, 형식이 아니면 null. */
	static Integer parseHex(String text) {
		if (text == null) {
			return null;
		}
		String t = text.trim();
		if (t.startsWith("#")) {
			t = t.substring(1);
		}
		try {
			if (t.length() == 3) {
				int r = Integer.parseInt(t.substring(0, 1), 16) * 17;
				int g = Integer.parseInt(t.substring(1, 2), 16) * 17;
				int b = Integer.parseInt(t.substring(2, 3), 16) * 17;
				return 0xFF000000 | (r << 16) | (g << 8) | b;
			}
			if (t.length() == 6) {
				return 0xFF000000 | Integer.parseInt(t, 16);
			}
			if (t.length() == 8) {
				return (int) Long.parseLong(t, 16);
			}
		} catch (NumberFormatException ignored) {
		}
		return null;
	}

	// -------------------------------------------------- 설치된 모드 목록

	/**
	 * 49-19차: "내가 설치한 모드 리스트가 보이게" - 모드 리스트 전용 모드를 따로 안 깔아도 되게 내장.
	 * FabricLoader가 알려주는 로드된 모드를 이름순으로 보여주고, 오른쪽에 상세(아이디/버전/제작자/설명).
	 */
	private void renderModList(DrawContext ctx, int mouseX, int mouseY) {
		List<ModInfo> mods = installedMods();
		int listW = contentW - MODS_DETAIL_W - PAD;
		boolean inList = LunaDraw.in(mouseX, mouseY, contentX, contentY, listW, contentH);

		clampScroll(mods.size() * MOD_ROW_H);
		scis(ctx, contentX, contentY, contentX + listW, contentY + contentH);
		int y = contentY - (int) scroll;
		for (int i = 0; i < mods.size(); i++) {
			if (y + MOD_ROW_H >= contentY && y <= contentY + contentH) {
				ModInfo mod = mods.get(i);
				boolean sel = i == selectedMod;
				boolean hovered = inList && LunaDraw.in(mouseX, mouseY, contentX, y, listW, MOD_ROW_H);
				LunaDraw.roundRectBordered(ctx, contentX, y, listW, MOD_ROW_H - 2, 5,
					sel ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x14) : (hovered ? LunaDraw.CARD_HOVER : LunaDraw.CARD),
					sel ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x6B) : LunaDraw.CARD_BORDER);
				LunaIcons.draw(ctx, textRenderer, LunaIcons.PACKAGE, contentX + 8,
					LunaDraw.iconY(y, MOD_ROW_H - 2), sel ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
				String ver = mod.version();
				int vw = LunaDraw.width(textRenderer, ver);
				int right = contentX + listW - 8;
				LunaDraw.text(ctx, textRenderer, ver, right - vw, LunaDraw.textY(y, MOD_ROW_H - 2), LunaDraw.TEXT_DIM);
				right -= vw + 6;
				// 49-23차: 설정 화면이 있는 모드는 톱니 표시, 라이브러리는 회색 꼬리표
				if (mod.configurable()) {
					LunaIcons.draw(ctx, textRenderer, LunaIcons.SETTINGS, right - 11, LunaDraw.iconY(y, MOD_ROW_H - 2),
						sel ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
					right -= 15;
				}
				if (mod.library()) {
					String tag = "라이브러리";
					int tw = LunaDraw.width(textRenderer, tag) + 8;
					LunaDraw.roundRect(ctx, right - tw, y + (MOD_ROW_H - 2 - 12) / 2, tw, 12, 3, ink(0x14));
					LunaDraw.text(ctx, textRenderer, tag, right - tw + 4, LunaDraw.textY(y + (MOD_ROW_H - 2 - 12) / 2, 12), LunaDraw.TEXT_DIM);
					right -= tw + 6;
				}
				String name = LunaDraw.ellipsize(textRenderer, mod.name(), right - (contentX + 23) - 4);
				LunaDraw.text(ctx, textRenderer, name, contentX + 23, LunaDraw.textY(y, MOD_ROW_H - 2),
					sel ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
			}
			y += MOD_ROW_H;
		}
		ctx.disableScissor();

		if (maxScroll > 0) {
			int thumbH = Math.max(24, contentH * contentH / (contentH + maxScroll));
			int thumbY = contentY + (int) ((contentH - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, contentX + listW + 2, thumbY, 3, thumbH, 1, ink(0x38));
		}

		// 오른쪽 상세
		int dx = contentX + listW + PAD;
		surface(ctx, dx, contentY, MODS_DETAIL_W, contentH, cardSolid());
		if (mods.isEmpty()) {
			LunaDraw.textCentered(ctx, textRenderer, "모드를 찾지 못했습니다", dx + MODS_DETAIL_W / 2,
				contentY + 20, LunaDraw.TEXT_DIM);
			return;
		}
		ModInfo mod = mods.get(Math.min(selectedMod, mods.size() - 1));
		int tx = dx + 12;
		int ty = contentY + 12;
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, mod.name(), MODS_DETAIL_W - 24),
			tx, ty, LunaDraw.TEXT);
		ty += 14;
		LunaDraw.text(ctx, textRenderer, mod.id() + "  |  " + mod.version(), tx, ty, LunaDraw.ACCENT);
		ty += 16;
		LunaDraw.text(ctx, textRenderer, "제작", tx, ty, LunaDraw.TEXT_DIM);
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, mod.authors(), MODS_DETAIL_W - 24 - 30),
			tx + 30, ty, LunaDraw.TEXT_SUB);
		ty += 18;
		ctx.fill(tx, ty, dx + MODS_DETAIL_W - 12, ty + 1, LunaDraw.applyAlpha(ink(0x12)));
		ty += 8;
		int descBottom = contentY + contentH - 14 - 24;
		for (String line : wrapText(mod.description(), MODS_DETAIL_W - 24, 12)) {
			if (ty > descBottom) {
				break;
			}
			LunaDraw.text(ctx, textRenderer, line, tx, ty, LunaDraw.TEXT_SUB);
			ty += 11;
		}
		// 49-23차: 모드 설정 열기(Mod Menu API 팩토리가 있을 때만 활성)
		int bx = dx + 12;
		int by = contentY + contentH - 12 - 18;
		int bw = MODS_DETAIL_W - 24;
		boolean can = mod.configurable();
		boolean bHover = can && LunaDraw.in(mouseX, mouseY, bx, by, bw, 18);
		float bh = LunaDraw.anim("mod:cfg", bHover ? 1f : 0f, 18f);
		LunaDraw.roundRectBordered(ctx, bx, by, bw, 18, 4,
			can ? LunaDraw.lerpColor(LunaDraw.withAlpha(LunaDraw.ACCENT, 0x1A), LunaDraw.withAlpha(LunaDraw.ACCENT, 0x33), bh) : LunaDraw.TRACK,
			can ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x6B) : LunaDraw.CARD_BORDER);
		String label = can ? "모드 설정 열기" : "설정 화면 없음";
		int lw = LunaDraw.width(textRenderer, label) + 15;
		int lx = bx + (bw - lw) / 2;
		LunaIcons.draw(ctx, textRenderer, LunaIcons.SETTINGS, lx, LunaDraw.iconY(by, 18), can ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
		LunaDraw.text(ctx, textRenderer, label, lx + 15, LunaDraw.textY(by, 18), can ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
	}

	/** 49-23차: 상세 패널의 "모드 설정 열기" 버튼 클릭. */
	private boolean handleModConfigClick(double mouseX, double mouseY) {
		List<ModInfo> mods = installedMods();
		if (mods.isEmpty()) {
			return false;
		}
		int listW = contentW - MODS_DETAIL_W - PAD;
		int dx = contentX + listW + PAD;
		int bx = dx + 12;
		int by = contentY + contentH - 12 - 18;
		int bw = MODS_DETAIL_W - 24;
		if (!LunaDraw.in(mouseX, mouseY, bx, by, bw, 18)) {
			return false;
		}
		ModInfo mod = mods.get(Math.min(selectedMod, mods.size() - 1));
		if (mod.configurable()) {
			kr.lunaslight.mod.util.ModListSupport.openConfig(client, mod.id(), this);
		}
		return true;
	}

	/** 폭에 맞춰 단어 단위로 줄바꿈(최대 maxLines줄). */
	private List<String> wrapText(String text, int maxWidth, int maxLines) {
		List<String> lines = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return lines;
		}
		StringBuilder cur = new StringBuilder();
		for (String word : text.replace("\n", " ").split(" ")) {
			String candidate = cur.isEmpty() ? word : cur + " " + word;
			if (LunaDraw.width(textRenderer, candidate) > maxWidth && !cur.isEmpty()) {
				lines.add(cur.toString());
				cur = new StringBuilder(word);
				if (lines.size() >= maxLines) {
					return lines;
				}
			} else {
				cur = new StringBuilder(candidate);
			}
		}
		if (!cur.isEmpty()) {
			lines.add(cur.toString());
		}
		return lines;
	}

	// ----------------------------------------------------------- 미리보기

	private void clampScroll(int totalHeight) {
		maxScroll = Math.max(0, totalHeight - contentH);
		scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
	}

	// =====================================================================
	// 입력
	// =====================================================================

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		applyTileMode();
		updateLayout();
		if (creamConfirm.isOpen()) {
			creamConfirm.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		if (keyConfirm.isOpen()) {
			keyConfirm.mouseClicked(mouseX, mouseY, button);
			return true;
		}
		if (handleModalClick(mouseX, mouseY, button)) {
			return true;
		}
		if (handleActionMenuClick(mouseX, mouseY)) {
			return true;
		}
		if (handleDropdownClick(mouseX, mouseY)) {
			return true;
		}
		commitPriceEdit();   // 49-110차: 가격 편집 중 다른 곳을 누르면 확정(아래 handleSettingClick이 필요하면 다시 연다)
		editingString = null;
		editingHex = null;
		searchFocused = false;

		if (handleChromeClick(mouseX, mouseY)) {
			return true;
		}
		if (modsView) {
			return handleModListClick(mouseX, mouseY);
		}
		if (openModule == null && pageView()) {
			return handlePageClick(mouseX, mouseY, button);
		}
		if (openModule == null) {
			return handleGridClick(mouseX, mouseY, button);
		}
		if (handleStageClick(mouseX, mouseY)) {
			return true;
		}
		return handleSettingsClick(mouseX, mouseY, button);
	}

	private boolean handleGridClick(double mouseX, double mouseY, int button) {
		if (!LunaDraw.in(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
			return true;
		}
		if (handleServerHeadClick(mouseX, mouseY)) {
			return true;
		}
		List<Module> modules = currentModules();
		for (int i = 0; i < modules.size(); i++) {
			int[] r = tileRect(i);
			int y = contentY + r[1] - (int) scroll;
			if (!LunaDraw.in(mouseX, mouseY, r[0], y, r[2], TILE_H)) {
				continue;
			}
			Module m = modules.get(i);
			// 49-20차: 스위치만 on/off, 상자 본문은 설정 열기.
			// 49-149차: 늘 켜진 기능(계산기)은 스위치가 없고, 전용 화면이 있으면 그걸 연다.
			if (m.isAlwaysOn() && m.openCustomScreen(this)) {
				return true;
			}
			int[] tb = toggleRect(r[0], y, r[2]);   // 49-226차: 카드 아래 [켜짐] 버튼
			if (m.isVersionSupported() && !m.isAlwaysOn() && LunaDraw.in(mouseX, mouseY, tb[0], tb[1], tb[2], tb[3])) {
				boolean wasOn = m.isEnabled();
				ModuleManager.get().toggleWithConflictResolution(m);
				// 49-21차: 켜는 순간 설정으로(사용자: "원하는 기능을 켜면 무조건 설정창이 뜨게")
				if (!wasOn && m.isEnabled() && !visibleSettings(m).isEmpty()) {
					openSettings(m);
				}
				return true;
			}
			if (!visibleSettings(m).isEmpty()) {
				openSettings(m);
			} else if (m.isVersionSupported()) {
				ModuleManager.get().toggleWithConflictResolution(m);
			}
			return true;
		}
		return true;
	}

	/** 49-12차: 모든 기능의 설정 + 켜짐 상태를 기본값으로. */
	private void resetAllModules() {
		for (Module m : ModuleManager.get().all()) {
			for (Setting<?> s : visibleSettings(m)) {
				s.resetToDefault();
			}
			m.setEnabled(m.isEnabledByDefault());
		}
		openColor = null;
		try {
			LunaClientConfig.save();
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("resetAll", t);
		}
	}

	private boolean handleModListClick(double mouseX, double mouseY) {
		int listW = contentW - MODS_DETAIL_W - PAD;
		if (!LunaDraw.in(mouseX, mouseY, contentX, contentY, listW, contentH)) {
			handleModConfigClick(mouseX, mouseY);
			return true;
		}
		List<ModInfo> mods = installedMods();
		int y = contentY - (int) scroll;
		for (int i = 0; i < mods.size(); i++) {
			if (LunaDraw.in(mouseX, mouseY, contentX, y, listW, MOD_ROW_H)) {
				selectedMod = i;
				return true;
			}
			y += MOD_ROW_H;
		}
		return true;
	}

	private void openSettings(Module m) {
		if (m.getPage() != null) {
			// 49-234차(사용자: 검색에 뜬 "HUD 배경" 상자를 보고 "이런 건 UI에 있어야지"): [일반]/[UI]/[그래픽]에 실린 설정은
			// 따로 기능 화면을 열지 않고 그 설정 페이지로 데려간다(검색은 지운다).
			search = "";
			searchFocused = false;
			modsView = false;
			openModule = null;
			settingsPage = m.getPage();
			lastPage = settingsPage;
			lastModsView = false;
			pageDir = 1;
			resetScroll();
			return;
		}
		pageDir = 1;
		if (openModule == null) {
			listScrollBeforeOpen = scrollTarget;   // 49-195차: 목록에서 열 때 보던 자리를 기억
		}
		openModule = m;
		openColor = null;
		editingHex = null;
		resetScroll();
		LunaDraw.resetAnim("page:" + m.getId());
	}

	private void openHudEditor(Module highlight) {
		LunaClientConfig.save();
		if (this.client != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaHudEditorScreen(this, highlight));
		}
	}

	private boolean handleSettingsClick(double mouseX, double mouseY, int button) {
		Module m = openModule;
		if (!LunaDraw.in(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
			return true;
		}
		for (GroupSlot slot : groupSlots(m)) {
			GroupBox box = slot.box();
			int w = slot.w(), sx = slot.x();
			int gy = contentY - (int) scroll + slot.relY();
			if (LunaDraw.in(mouseX, mouseY, sx, gy, w, GROUP_HEAD_H)) {
				String key = m.getId() + "#" + box.label();
				if (!collapsed.remove(key)) {
					collapsed.add(key);
				}
				return true;
			}
			if (isGroupCollapsed(m, box.label())) {
				continue;
			}
			int ry = gy + GROUP_HEAD_H;
			for (List<Cell> row : pairRows(box.settings(), w)) {
				int h = row.get(0).h();
				for (Cell c : row) {
					if (LunaDraw.in(mouseX, mouseY, sx + c.dx(), ry, c.w(), h)) {
						handleSettingClick(c.s(), sx + c.dx(), ry, c.w(), mouseX, mouseY, button);
						return true;
					}
				}
				ry += h;
			}
		}
		return true;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void handleSettingClick(Setting<?> s, int x, int y, int w, double mouseX, double mouseY, int button) {
		if (s.isDisabled() || ownerDisabled()) {
			return;   // 49-76차(6-6)·49-93차: 흐리게 그린 설정(기능이 꺼진 묶음 포함)은 눌러도 아무 일도 없다
		}
		int right = x + w - 10;
		curRowW = w;
		// 49-87차(8-2): ↺ 초기화 버튼(기본값이 아닐 때만 그려진다) - 어떤 종류의 설정이든 먼저 본다
		if (button == 0 && !s.isDefault() && mouseY < y + ROW_H) {
			int rx = resetIconX(right, s);
			if (LunaDraw.in(mouseX, mouseY, rx - 2, y, RESET_W + 2, ROW_H)) {
				s.resetToDefault();
				return;
			}
		}
		int cy = y + (ROW_H - TOGGLE_H) / 2;

		if (s instanceof BooleanSetting b) {
			ColorSetting link = b.getLinkedColor();
			if (link != null) {
				if (mouseY >= y + ROW_H) {
					if (openColor == link) {
						handleColorEditorClick(link, x, y, w, mouseX, mouseY);
					}
					return;
				}
				if (LunaDraw.in(mouseX, mouseY, linkedSwatchX(right), cy, LINK_SW_W, TOGGLE_H + 2)) {
					openColor = openColor == link ? null : link;
					editingHex = null;
					return;
				}
				if (button == 1) {
					link.resetToDefault();
					return;
				}
			}
			b.setValue(!b.get());
		} else if (s instanceof IntSetting || s instanceof FloatSetting) {
			int sliderW = 96;
			int sx = right - sliderW;
			if (button == 1) {
				s.resetToDefault();
				return;
			}
			if (mouseX >= sx - 4 && mouseY < y + ROW_H) {
				draggingSetting = s;
				dragX = sx;
				dragW = sliderW;
				applySliderDrag((Setting) s, mouseX);
			}
		} else if (s instanceof EnumSetting<?> es) {
			if (es.isVertical()) {
				int oy = y + ROW_H;
				Enum<?>[] options = es.getOptions();
				for (int i = 0; i < options.length; i++) {
					if (mouseY >= oy + i * VOPT_H && mouseY < oy + (i + 1) * VOPT_H) {
						es.setIndex(i);
						return;
					}
				}
			} else {
				handleSegmentClick(es, right, y, mouseX, mouseY, button);
			}
		} else if (s instanceof ActionSetting as) {
			int bw = LunaDraw.width(textRenderer, as.getButtonLabel()) + 18;
			if (LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2)) {
				as.run();
			}
		} else if (s instanceof KeybindListSetting kl) {
			// 머리줄: [추가하기]
			if (mouseY < y + ROW_H) {
				int bw = LunaDraw.width(textRenderer, kl.getButtonLabel()) + 18;
				if (kl.size() < kl.capacity() && LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2)) {
					kl.add();
				}
				return;
			}
			int ox = x + 10;
			int ow = w - 20;
			for (int i = 0; i < kl.size(); i++) {
				int oy = y + ROW_H + i * KENTRY_H;
				if (mouseY < oy || mouseY >= oy + KENTRY_H) {
					continue;
				}
				int h = KENTRY_H - 2;
				int ih = 14;
				int iy = oy + (h - ih) / 2;
				int delX = ox + ow - 16;
				// 삭제 × (49-124차: 확인 팝업 먼저)
				if (LunaDraw.in(mouseX, mouseY, delX - 2, oy, 18, h)) {
					final int idx = i;
					editingString = null;
					listeningKeybind = null;
					keyConfirm.show(LunaIcons.KEYBOARD, "이 키바인드를 삭제할까요?",
						(idx + 1) + "번 키바인드", "삭제", () -> kl.delete(idx));
					return;
				}
				KeybindSetting key = kl.keyAt(i);
				int kbw = Math.max(46, LunaDraw.width(textRenderer, key.getKeyName()) + 14);
				int keyX = delX - 8 - kbw;
				// 키 버튼: 좌클릭 = 다시 지정, 우클릭 = 해제
				if (LunaDraw.in(mouseX, mouseY, keyX, iy, kbw, ih)) {
					if (button == 1) {
						key.setValue(-1);
					} else {
						listeningKeybind = key;
						modalModsHeld = 0;
						modalModOnly = false;
						LunaDraw.resetAnim("modal:kb");
					}
					return;
				}
				// 명령어/글 입력칸
				String num = (i + 1) + "번";
				int fx = ox + 8 + LunaDraw.width(textRenderer, num) + 8;
				if (LunaDraw.in(mouseX, mouseY, fx, iy, Math.max(24, keyX - 8 - fx), ih)) {
					editingString = kl.textAt(i);
				}
				return;
			}
		} else if (s instanceof PriceListSetting pl) {
			handlePriceListClick(pl, x, y, w, mouseX, mouseY, button);
		} else if (s instanceof KeybindSetting ks) {
			if (button == 1) {
				ks.setValue(-1);
			} else {
				listeningKeybind = ks;
				modalModsHeld = 0;
				modalModOnly = false;
				LunaDraw.resetAnim("modal:kb");
			}
		} else if (s instanceof PositionSetting) {
			int bw = 62;
			if (LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2)) {
				openHudEditor(openModule);
			} else if (button == 1) {
				s.resetToDefault();
			}
		} else if (s instanceof StringSetting ss) {
			if (ss.isList()) {
				int bw = LunaDraw.width(textRenderer, "추가") + 14;
				if (mouseY < y + ROW_H) {
					if (LunaDraw.in(mouseX, mouseY, right - bw, cy, bw, TOGGLE_H + 2)) {
						ss.commitDraft();
					} else {
						editingString = ss;
					}
					return;
				}
				java.util.List<String> items = ss.items();
				int ox = x + 10;
				int ow = w - 20;
				for (int i = 0; i < items.size(); i++) {
					int oy = y + ROW_H + i * VOPT_H;
					if (LunaDraw.in(mouseX, mouseY, ox + ow - 18, oy, 18, VOPT_H - 2)) {
						ss.removeItem(items.get(i));
						return;
					}
				}
				return;
			}
			editingString = ss;
		} else if (s instanceof ColorSetting cs) {
			if (mouseY < y + ROW_H) {
				openColor = openColor == cs ? null : cs;
				return;
			}
			handleColorEditorClick(cs, x, y, w, mouseX, mouseY);
		}
	}

	/** 색 편집기(행 아래 펼쳐진 영역) 클릭 - 팔레트 네모/색상/투명도 막대, 자주 쓰는 색, #색코드. y는 행의 위쪽. */
	private void handleColorEditorClick(ColorSetting cs, int x, int y, int w, double mouseX, double mouseY) {
		int ey = y + ROW_H;
		int[] L = paletteLayout(x, ey, w);
		int px = L[0], py = L[1], sq = L[2], hueX = L[3], alphaX = L[4], rx = L[5];
		syncPalette(cs, cs.getArgb());
		if (LunaDraw.in(mouseX, mouseY, px - 2, py - 2, sq + 4, sq + 4)) {
			colorDragChannel = 10;
		} else if (LunaDraw.in(mouseX, mouseY, hueX - 3, py - 2, PAL_BAR_W + 6, sq + 4)) {
			colorDragChannel = 11;
		} else if (LunaDraw.in(mouseX, mouseY, alphaX - 3, py - 2, PAL_BAR_W + 6, sq + 4)) {
			colorDragChannel = 12;
		}
		if (colorDragChannel >= 10) {
			palDragX = px;
			palDragY = py;
			palDragSize = sq;
			applyColorDrag(cs, mouseX, mouseY);
			return;
		}
		int rw = Math.max(60, x + w - 14 - rx);
		int[] rb = colorResetBox(rx, py, Math.min(rw, 96));
		if (LunaDraw.in(mouseX, mouseY, rb[0], rb[1], rb[2], rb[3])) {
			if (!cs.isDefault()) {
				cs.resetToDefault();
				editingHex = null;
				syncPalette(cs, cs.getArgb());
			}
			return;
		}
		int hexY = py + 24;
		int hw = Math.min(rw, 96);
		if (LunaDraw.in(mouseX, mouseY, rx, hexY, hw, 14)) {
			editingHex = cs;
			hexBuffer = "#";   // 새로 쓴다(지금 값은 흐리게 보여 줌)
			return;
		}
		int prY = hexY + 20;
		int per = Math.max(1, Math.min(COLOR_PRESETS.length, (rw + 4) / 16));
		for (int i = 0; i < COLOR_PRESETS.length; i++) {
			int sx = rx + (i % per) * 16;
			int sy = prY + (i / per) * 14;
			if (LunaDraw.in(mouseX, mouseY, sx, sy, 13, 11)) {
				cs.setValue((COLOR_PRESETS[i] & 0x00FFFFFF) | (cs.getArgb() & 0xFF000000));
				return;
			}
		}
	}

	private int palDragX, palDragY, palDragSize;

	private void handleSegmentClick(EnumSetting<?> es, int right, int y, double mouseX, double mouseY, int button) {
		Enum<?>[] options = es.getOptions();
		int[] widths = new int[options.length];
		int total = 0;
		for (int i = 0; i < options.length; i++) {
			widths[i] = LunaDraw.width(textRenderer, enumLabel(options[i])) + 12;
			total += widths[i] + 3;
		}
		total -= 3;
		int cy = ctrlY(y);
		if (options.length > 4 || total > curRowW - 120) {
			if (button == 1) {
				es.cycle(-1);
				return;
			}
			dropdown = dropdown == es ? null : es;   // 49-172차: 목록 펼치기
			return;
		}
		int sx = right - total;
		for (int i = 0; i < options.length; i++) {
			if (LunaDraw.in(mouseX, mouseY, sx, cy, widths[i], CTRL_H)) {
				es.setIndex(i);
				return;
			}
			sx += widths[i] + 3;
		}
	}

	/** 49-172차: 펼친 선택 목록(맨 위에 그린다). */
	private void renderDropdown(DrawContext ctx, int mouseX, int mouseY) {
		if (dropdown == null) {
			return;
		}
		Enum<?>[] options = dropdown.getOptions();
		int h = options.length * VOPT_H + 6;
		int y = ddY;
		if (y + h > height - 4) {
			y = Math.max(4, ddY - CTRL_H - 4 - h);   // 아래 자리가 없으면 위로
		}
		LunaDraw.roundRectBordered(ctx, ddX, y, ddW, h, 5, LunaDraw.PANEL, LunaDraw.withAlpha(LunaDraw.ACCENT, 0x60));
		int oy = y + 3;
		for (Enum<?> o : options) {
			boolean sel = o == dropdown.get();
			boolean hov = LunaDraw.in(mouseX, mouseY, ddX + 3, oy, ddW - 6, VOPT_H);
			if (sel || hov) {
				LunaDraw.roundRect(ctx, ddX + 3, oy, ddW - 6, VOPT_H, 3, sel ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x2A) : ink(0x18));
			}
			LunaDraw.text(ctx, textRenderer, enumLabel(o), ddX + 9, LunaDraw.textY(oy, VOPT_H), sel ? LunaDraw.ACCENT : hov ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
			oy += VOPT_H;
		}
	}

	private boolean handleDropdownClick(double mouseX, double mouseY) {
		if (dropdown == null) {
			return false;
		}
		Enum<?>[] options = dropdown.getOptions();
		int h = options.length * VOPT_H + 6;
		int y = ddY;
		if (y + h > height - 4) {
			y = Math.max(4, ddY - CTRL_H - 4 - h);
		}
		EnumSetting<?> es = dropdown;
		dropdown = null;
		if (LunaDraw.in(mouseX, mouseY, ddX, y, ddW, h)) {
			int i = (int) ((mouseY - (y + 3)) / VOPT_H);
			if (i >= 0 && i < options.length) {
				es.setIndex(i);
			}
			return true;
		}
		return false;   // 밖을 누르면 닫고, 그 클릭은 평소대로
	}

	@SuppressWarnings({"rawtypes"})
	private void applySliderDrag(Setting setting, double mouseX) {
		float ratio = (float) Math.max(0, Math.min(1, (mouseX - dragX) / (double) dragW));
		if (setting instanceof IntSetting is) {
			int range = is.getMax() - is.getMin();
			int step = Math.max(1, is.getStep());
			int v = is.getMin() + Math.round(ratio * range / step) * step;
			is.setValue(v);
		} else if (setting instanceof FloatSetting fs) {
			float range = fs.getMax() - fs.getMin();
			float v = fs.getMin() + ratio * range;
			if (fs.getStep() > 0) {
				v = fs.getMin() + Math.round((v - fs.getMin()) / fs.getStep()) * fs.getStep();
			}
			fs.setValue(v);
		}
	}

	private void applyColorDrag(ColorSetting cs, double mouseX, double mouseY) {
		int sq = Math.max(2, palDragSize);
		float fx = (float) Math.max(0, Math.min(1, (mouseX - palDragX) / (sq - 1)));
		float fy = (float) Math.max(0, Math.min(1, (mouseY - palDragY) / (sq - 1)));
		int alpha = (cs.getArgb() >>> 24) & 0xFF;
		switch (colorDragChannel) {
			case 10 -> {
				palS = fx;
				palV = 1 - fy;
			}
			case 11 -> palH = Math.min(0.9999f, fy);
			case 12 -> alpha = Math.round((1 - fy) * 255);
			default -> {
				return;
			}
		}
		applyPalette(cs, alpha);
	}

	@SuppressWarnings({"rawtypes"})
	private boolean lunaMouseDragged0(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingSetting != null) {
			applySliderDrag((Setting) draggingSetting, mouseX);
			return true;
		}
		if (colorDragChannel >= 0 && openColor != null) {
			applyColorDrag(openColor, mouseX, mouseY);
			return true;
		}
		return false;
	}

	private boolean lunaMouseReleased0(double mouseX, double mouseY, int button) {
		draggingSetting = null;
		colorDragChannel = -1;
		return false;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		dropdown = null;
		if (pickingPrices != null) {
			pickerScroll -= verticalAmount * 28;   // 클램프는 렌더에서
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, px, contentY, pw, contentH)) {
			scrollTarget -= verticalAmount * 32;
			scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
			return true;
		}
		return false;
	}

	private boolean lunaKeyReleased0(int keyCode, int scanCode, int modifiers) {
		return false;   // 49-124차: 조합키 제거 - 키를 떼는 순간 지정하던 동작 없음.
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (creamConfirm.isOpen()) {
			creamConfirm.keyPressed(keyCode);
			return true;
		}
		if (keyConfirm.isOpen()) {
			keyConfirm.keyPressed(keyCode);
			return true;
		}
		if (pickingPrices != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				pickingPrices = null;
			}
			return true;   // 피커가 열려 있으면 그동안 다른 키는 막는다
		}
		if (priceEditSetting != null) {
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				commitPriceEdit();
			} else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				priceEditSetting = null;
				priceEditId = null;
				priceEditBuf = "";
			} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !priceEditBuf.isEmpty()) {
				priceEditBuf = priceEditBuf.substring(0, priceEditBuf.length() - 1);
			}
			return true;
		}
		if (listeningKeybind != null) {
			if (pendingConflict != null) {
				// 겹침 확인 중: Enter = 그래도 지정, 그 외 = 다시 듣기
				if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
					listeningKeybind.setValue(pendingKey);
					listeningKeybind = null;
				}
				pendingKey = -2;
				pendingConflict = null;
				return true;
			}
			// 49-124차(사용자: "삭제: ESC ... 키바인드 삭제할 때 팝업 띄워주고 확인차"):
			// ESC/Backspace/Delete = 이 키 삭제(먼저 확인 팝업). 그 밖의 키는 조합 없이 바로 지정.
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
				askDeleteKeybind(listeningKeybind, () -> {
					if (listeningKeybind != null) {
						listeningKeybind.setValue(-1);
						listeningKeybind = null;
					}
				});
			} else {
				bindKey(keyCode);
			}
			return true;
		}
		if (editingHex != null) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				Integer v = parseHex(hexBuffer);
				if (v != null) {
					editingHex.setValue(v);
				}
				editingHex = null;
			} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !hexBuffer.isEmpty()) {
				hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
			} else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
				// 49-126차: #색코드 붙여넣기(인터넷에서 복사한 "#ff8800" 같은 값)
				try {
					String clip = GLFW.glfwGetClipboardString(WindowAccess.of(this.client).getHandle());
					String t = clip == null ? "" : clip.trim().replace("#", "");
					if (t.matches("[0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
						hexBuffer = "#" + t.toUpperCase(java.util.Locale.ROOT);
						Integer v = parseHex(hexBuffer);
						if (v != null) {
							editingHex.setValue(v);
						}
					}
				} catch (Throwable ignored) {
				}
			}
			return true;
		}
		if (confirmReset) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				confirmReset = false;
			} else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				runReset();
			}
			return true;
		}
		if (editingString != null) {
			if (editingString.isList()) {
				// 49-76차(6-8): 목록형 - 엔터는 [추가], ESC는 입력만 접음, 백스페이스는 입력 글자만 지움
				if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
					editingString.commitDraft();
				} else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
					editingString.setDraft("");
					editingString = null;
				} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
					String d = editingString.draft();
					if (!d.isEmpty()) {
						editingString.setDraft(d.substring(0, d.length() - 1));
					}
				}
				return true;
			}
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				editingString = null;
			} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
				String v = editingString.get() == null ? "" : editingString.get();
				if (!v.isEmpty()) {
					editingString.setValue(v.substring(0, v.length() - 1));
				}
			}
			return true;
		}
		if (searchFocused) {
			boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
			int cur = Math.max(0, Math.min(searchCursor, search.length()));
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				searchFocused = false;
			} else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && cur > 0) {
				// 49-188차: 커서 앞 글자(Ctrl이면 앞 단어)를 지운다
				int from = ctrl ? searchWordLeft(cur) : cur - 1;
				search = search.substring(0, from) + search.substring(cur);
				searchCursor = from;
				resetScroll();
			} else if (keyCode == GLFW.GLFW_KEY_DELETE && cur < search.length()) {
				int to = ctrl ? searchWordRight(cur) : cur + 1;
				search = search.substring(0, cur) + search.substring(to);
				searchCursor = cur;
				resetScroll();
			} else if (keyCode == GLFW.GLFW_KEY_LEFT) {
				searchCursor = ctrl ? searchWordLeft(cur) : Math.max(0, cur - 1);
			} else if (keyCode == GLFW.GLFW_KEY_RIGHT) {
				searchCursor = ctrl ? searchWordRight(cur) : Math.min(search.length(), cur + 1);
			} else if (keyCode == GLFW.GLFW_KEY_HOME) {
				searchCursor = 0;
			} else if (keyCode == GLFW.GLFW_KEY_END) {
				searchCursor = search.length();
			} else if (keyCode == GLFW.GLFW_KEY_V && ctrl) {
				try {
					String clip = GLFW.glfwGetClipboardString(WindowAccess.of(this.client).getHandle());
					if (clip != null) {
						String t = clip.replaceAll("[\\r\\n\\t]+", " ").trim();
						if (!t.isEmpty()) {
							searchInsert(t.length() > 64 ? t.substring(0, 64) : t);
						}
					}
				} catch (Throwable ignored) {
				}
			} else if (keyCode == GLFW.GLFW_KEY_A && ctrl) {
				// 전체 지우기 대신: 커서를 끝으로(선택 기능은 없음)
				searchCursor = search.length();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (openModule == null && searching()) {
				search = "";
				resetScroll();
				return true;
			}
			if (openModule != null) {
				backToList();   // 49-195차: ← 와 같은 동작(바로 연 설정이면 전 화면, 아니면 보던 목록 자리)
			} else {
				close();
			}
			return true;
		}
		return false;
	}

	private boolean lunaCharTyped0(char chr) {
		if (listeningKeybind != null) {
			return true;
		}
		if (pickingPrices != null) {
			return true;
		}
		if (priceEditSetting != null) {
			// 49-110차: 가격은 숫자 + 소수점 하나만
			if ((chr >= '0' && chr <= '9') || (chr == '.' && !priceEditBuf.contains("."))) {
				if (priceEditBuf.length() < 9) {
					priceEditBuf += chr;
				}
			}
			return true;
		}
		if (chr < 32) {
			return false;
		}
		if (editingHex != null) {
			boolean hex = (chr >= '0' && chr <= '9') || (chr >= 'a' && chr <= 'f') || (chr >= 'A' && chr <= 'F');
			if (chr == '#' && (hexBuffer.isEmpty() || "#".equals(hexBuffer))) {
				hexBuffer = "#";
			} else if (hex && hexBuffer.replace("#", "").length() < 8) {
				hexBuffer += Character.toUpperCase(chr);
				Integer v = parseHex(hexBuffer);
				if (v != null) {
					editingHex.setValue(v); // 6/8자리가 되는 순간 바로 반영
				}
			}
			return true;
		}
		if (editingString != null) {
			if (editingString.isList()) {
				if (chr != ',' && editingString.draft().length() < 40) {
					editingString.setDraft(editingString.draft() + chr);
				}
				return true;
			}
			String v = editingString.get() == null ? "" : editingString.get();
			editingString.setValue(v + chr);
			return true;
		}
		if (searchFocused) {
			searchInsert(String.valueOf(chr));   // 49-188차: 커서 자리에 끼워 넣는다
			return true;
		}
		return false;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	// =====================================================================
	// 유틸
	// =====================================================================

	private void resetScroll() {
		scroll = 0;
		scrollTarget = 0;
		dropdown = null;
	}

	private static float ratio(float v, float min, float max) {
		if (max <= min) {
			return 0;
		}
		return (v - min) / (max - min);
	}

	private static String trimFloat(float v) {
		String s = String.format("%.2f", v);
		while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	private static String enumLabel(Enum<?> e) {
		// toString()을 오버라이드해 한국어 이름을 주는 enum은 그대로 표시.
		String custom = e.toString();
		if (custom != null && !custom.equals(e.name())) {
			return custom;
		}
		String n = e.name().toLowerCase().replace('_', ' ');
		return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	static String anchorLabel(HudPosition.Anchor anchor) {
		return switch (anchor) {
			case TOP_LEFT -> "왼쪽 위";
			case TOP_RIGHT -> "오른쪽 위";
			case BOTTOM_LEFT -> "왼쪽 아래";
			case BOTTOM_RIGHT -> "오른쪽 아래";
			case TOP_CENTER -> "위 가운데";
			case BOTTOM_CENTER -> "아래 가운데";
			case LEFT_CENTER -> "왼쪽 가운데";
			case RIGHT_CENTER -> "오른쪽 가운데";
		};
	}
}
