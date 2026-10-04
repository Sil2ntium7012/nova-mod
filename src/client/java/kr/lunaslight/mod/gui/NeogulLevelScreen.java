package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 49-149차(사용자: "너굴마을 경험치 - 기능에 계산기, 따로 켜는 게 아니라 입력 > 결과 방식. 시작 레벨 끝 레벨로 필요한 경험치,
 * 돈 보여주고 위키 정보도 밑에 따로, 레벨별 구매 가능한 기능도"): <b>너굴마을 레벨 계산기</b>.
 *
 * <p>위: [시작 레벨] → [끝 레벨] 입력(숫자 입력, -/+, 휠, 위아래 화살표). 그 아래 결과 상자(생활 경험치, 토벌 경험치,
 * 레벨업 비용, 이 구간에 새로 배울 수 있는 기능 수와 합계 비용), 새로 배울 수 있는 기능 목록, 맨 아래 레벨표(구간마다
 * 생활/토벌 경험치, 레벨업 비용, 그 레벨에서 배울 수 있는 기능). 자료는 mcng.kr/wiki 레벨 & 스킬(사용자가 준 표 기준).
 * 레벨업에는 생활 경험치와 토벌 경험치가 둘 다 차야 한다.
 */
public class NeogulLevelScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
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

	@Override
	protected boolean lunaCharTyped(char chr) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaCharTyped0(chr);
		} finally {
			lunaV.exit(e);
		}
	}

	// ==================== 자료(위키) ====================

	/** EXP[n] = Lv.n → Lv.n+1 에 필요한 생활(= 토벌) 경험치. n = 1..99. */
	private static final long[] EXP = {0,
		2410, 3220, 4420, 6030, 7840, 10050, 12460, 15280, 18690, 19630,
		22190, 25080, 28360, 32060, 36240, 40970, 46320, 52360, 59190, 62150,
		69400, 77480, 86510, 96590, 107800, 120400, 134400, 150100, 167600, 176000,
		187800, 200400, 213800, 228100, 243400, 259700, 277200, 295700, 315600, 331300,
		357500, 385600, 416000, 448800, 484200, 522400, 563600, 608000, 656000, 688800,
		721700, 756300, 792500, 830400, 870100, 911800, 955400, 1001000, 1049000, 1101000,
		1189000, 1283000, 1385000, 1495000, 1614000, 1742000, 1881000, 2030000, 2191000, 2301000,
		2403000, 2509000, 2621000, 2737000, 2859000, 2985000, 3118000, 3256000, 3401000, 5437620,
		5981370, 6579510, 7237470, 7961220, 8757360, 9633120, 10596420, 11656050, 12821640, 14103810,
		15514200, 17065620, 18772170, 20649390, 22714320, 24985770, 27484350, 30232800, 33255000};

	/** COST[n] = Lv.n → Lv.n+1 레벨업 비용(원). */
	private static final long[] COST = {0,
		110000, 120000, 130000, 140000, 150000, 160000, 170000, 180000, 190000, 200000,
		210000, 220000, 230000, 240000, 250000, 260000, 270000, 285000, 300000, 400000,
		500000, 600000, 700000, 800000, 900000, 1000000, 1100000, 1200000, 1300000, 1400000,
		1500000, 1600000, 1700000, 1800000, 1900000, 2000000, 2100000, 2200000, 2500000, 2700000,
		2900000, 3100000, 3300000, 3500000, 3600000, 3700000, 3800000, 4000000, 4250000, 4500000,
		4750000, 5000000, 5250000, 5500000, 5750000, 6000000, 6250000, 6500000, 6750000, 7000000,
		7250000, 7500000, 7750000, 8000000, 8250000, 8500000, 8750000, 9250000, 10000000, 12000000,
		14000000, 16000000, 18000000, 20000000, 22000000, 24000000, 26000000, 28000000, 30000000, 33000000,
		36000000, 39000000, 42000000, 45000000, 48000000, 51000000, 54000000, 57000000, 60000000, 64000000,
		68000000, 72000000, 76000000, 80000000, 84000000, 88000000, 92000000, 96000000, 100000000};

	private static final int MAX_LV = 100;

	/** 레벨 도달 시 살 수 있는 기능(권한 / 공격 / 유틸 스킬). */
	private record Skill(int level, String kind, String name, String effect, long cost) {
	}

	private static final List<Skill> SKILLS = new ArrayList<>();

	private static void s(int lv, String kind, String name, String effect, long man) {
		SKILLS.add(new Skill(lv, kind, name, effect, man * 10000L));
	}

	static {
		// 권한(명령어)
		s(1, "권한", "/창고1", "창고 1", 20);
		s(1, "권한", "/수리", "수리", 10);
		s(3, "권한", "/제작대", "어디서나 제작대", 30);
		s(5, "권한", "/창고2", "창고 2", 50);
		s(8, "권한", "/밥", "허기 회복", 50);
		s(10, "권한", "/창고3", "창고 3", 200);
		s(17, "권한", "/줄기보호", "줄기 보호", 150);
		s(20, "권한", "/야간투시", "야간 투시", 300);
		s(25, "권한", "/창고4", "창고 4", 1000);
		s(30, "권한", "/슈퍼점프", "슈퍼 점프", 750);
		s(35, "권한", "/그림", "그림", 1000);
		s(40, "권한", "/셋홈", "집 지정", 1000);
		s(50, "권한", "/원격상점", "어디서나 상점", 3000);
		s(60, "권한", "/원격요리", "어디서나 요리", 4000);
		s(70, "권한", "/원격무역", "어디서나 무역", 5000);
		// 공격
		s(8, "공격", "1단 베기 I", "공격력 100→105%", 50);
		s(15, "공격", "1단 베기 II", "공격력 105→110%", 100);
		s(21, "공격", "2단 베기", "1→2단 콤보", 300);
		s(28, "공격", "2단 베기 I", "공격력 110→115%", 500);
		s(35, "공격", "2단 베기 II", "공격력 115→120%", 800);
		s(41, "공격", "내려찍기", "1→3단 콤보", 1500);
		s(48, "공격", "내려찍기 I", "공격력 120→125%", 2000);
		s(55, "공격", "내려찍기 II", "공격력 125→130%", 2500);
		s(61, "공격", "마무리 강타", "1→4단 콤보", 4000);
		s(68, "공격", "마무리 강타 I", "공격력 130→135%", 4500);
		s(75, "공격", "마무리 강타 II", "공격력 135→140%", 5000);
		s(11, "공격", "돌진 베기", "앞 6칸 돌진(쿨 8초)", 250);
		s(18, "공격", "돌진 베기 I", "공격력 160→170%", 500);
		s(25, "공격", "돌진 베기 II", "공격력 170→185%", 1000);
		s(31, "공격", "회전 참격", "반경 3.5칸(쿨 12초)", 500);
		s(38, "공격", "회전 참격 I", "공격력 160→170%", 1000);
		s(45, "공격", "회전 참격 II", "공격력 170→185%", 2000);
		s(51, "공격", "검기 발사", "부채꼴(쿨 12초)", 1000);
		s(58, "공격", "검기 발사 I", "공격력 160→170%", 2000);
		s(65, "공격", "검기 발사 II", "공격력 170→185%", 4000);
		// 유틸
		s(3, "농사", "이리 오너라 I", "허수아비 확률 +10%", 30);
		s(23, "농사", "이리 오너라 II", "허수아비 확률 +20%", 500);
		s(43, "농사", "이리 오너라 III", "허수아비 확률 +30%", 1000);
		s(63, "농사", "이리 오너라 IV", "허수아비 확률 +40%", 2000);
		s(83, "농사", "이리 오너라 V", "허수아비 확률 +50%", 5000);
		s(1, "농사", "황금 작물 I", "액기스 확률 0.01%", 30);
		s(21, "농사", "황금 작물 II", "액기스 확률 0.013%", 500);
		s(41, "농사", "황금 작물 III", "액기스 확률 0.016%", 1000);
		s(61, "농사", "황금 작물 IV", "액기스 확률 0.019%", 2000);
		s(81, "농사", "황금 작물 V", "액기스 확률 0.022%", 5000);
		s(5, "낚시", "손 맛이 좋아 I", "미니게임 2회 1%", 30);
		s(25, "낚시", "손 맛이 좋아 II", "미니게임 2회 2%", 500);
		s(45, "낚시", "손 맛이 좋아 III", "미니게임 2회 3%", 1000);
		s(65, "낚시", "손 맛이 좋아 IV", "미니게임 2회 4%", 2000);
		s(85, "낚시", "손 맛이 좋아 V", "미니게임 2회 5%", 5000);
		s(8, "낚시", "고급 미끼 I", "입질 -0.5초", 100);
		s(28, "낚시", "고급 미끼 II", "입질 -1초", 1000);
		s(48, "낚시", "고급 미끼 III", "입질 -1.5초", 2000);
		s(68, "낚시", "고급 미끼 IV", "입질 -2초", 4000);
		s(88, "낚시", "고급 미끼 V", "입질 -2.5초", 7500);
		s(18, "요리", "美슐랭 I", "유통기한 2→3일", 800);
		s(48, "요리", "美슐랭 II", "유통기한 3→4일", 3000);
		s(78, "요리", "美슐랭 III", "유통기한 4→5일", 7500);
		s(2, "광질", "도굴꾼 I", "보석 확률 +3%", 30);
		s(22, "광질", "도굴꾼 II", "보석 확률 +6%", 500);
		s(42, "광질", "도굴꾼 III", "보석 확률 +9%", 1000);
		s(62, "광질", "도굴꾼 IV", "보석 확률 +12%", 2000);
		s(82, "광질", "도굴꾼 V", "보석 확률 +15%", 5000);
		s(20, "광질", "보석의 이해 I", "레어 보석", 1500);
		s(40, "광질", "보석의 이해 II", "에픽 보석", 3000);
		s(60, "광질", "보석의 이해 III", "유니크/희귀 보석", 7500);
		s(10, "광질", "급하다 급해 I", "성급함 5초 0.1%", 100);
		s(30, "광질", "급하다 급해 II", "성급함 5초 0.13%", 1000);
		s(50, "광질", "급하다 급해 III", "성급함 6초 0.15%", 2000);
		s(70, "광질", "급하다 급해 IV", "성급함 6초 0.18%", 4000);
		s(90, "광질", "급하다 급해 V", "성급함 7초 0.2%", 7500);
		s(17, "섬", "신속한 발걸음 I", "이동속도 +5%", 1000);
		s(47, "섬", "신속한 발걸음 II", "이동속도 +10%", 3000);
		s(6, "기타", "체력증진 I", "피로도 상한 +10", 100);
		s(26, "기타", "체력증진 II", "피로도 상한 +20", 1000);
		s(46, "기타", "체력증진 III", "피로도 상한 +30", 2000);
		s(66, "기타", "체력증진 IV", "피로도 상한 +40", 4000);
		s(86, "기타", "체력증진 V", "피로도 상한 +50", 7500);
		SKILLS.sort((a, b) -> a.level() != b.level() ? Integer.compare(a.level(), b.level()) : a.kind().compareTo(b.kind()));
	}

	private static List<Skill> skillsAt(int lv) {
		List<Skill> out = new ArrayList<>();
		for (Skill k : SKILLS) {
			if (k.level() == lv) {
				out.add(k);
			}
		}
		return out;
	}

	// ==================== 상태 ====================

	private final Screen parent;
	private int startLv = 1;
	private int endLv = 10;
	private int focus = -1;          // 0 = 시작, 1 = 끝
	private String buf = "";
	private float scroll;
	private int maxScroll;
	private String tip;

	private static final int TOP_Y = 14;
	private static final int INPUT_Y = 44;
	private static final int BOX_H = 18;
	private static final int FIELD_W = 34;
	private static final int BTN_W = 16;
	private int left, contentW, contentTop;

	public NeogulLevelScreen(Screen parent) {
		super(LunaCompat.textLiteral("너굴마을 레벨 계산기"));
		this.parent = parent;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void layout() {
		contentW = Math.min(width - 32, 560);
		left = (width - contentW) / 2;
		contentTop = INPUT_Y + BOX_H + 12;
	}

	// ==================== 숫자 모양 ====================

	private static String num(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}

	/**
	 * 123450000 → "1억 2,345만원", 5000 → "0.5만원", 12500 → "1.25만원", 300 → "300원".
	 * 49-167차(사용자: "천원단위는 0.5만원 이런식으로"): 만 미만 끝자리는 소수(최대 2자리, 끝 0 제거)로 만 단위에 붙인다.
	 */
	private static String won(long v) {
		if (v < 10_000L) {
			return v >= 1_000L ? manDecimal(v) + "만원" : num(v) + "원";
		}
		long eok = v / 100_000_000L;
		long manRest = v % 100_000_000L;
		StringBuilder sb = new StringBuilder();
		if (eok > 0) {
			sb.append(num(eok)).append("억");
		}
		if (manRest > 0) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(manDecimal(manRest)).append("만");
		}
		return sb.append("원").toString();
	}

	/** 12500 → "1.25", 5000 → "0.5", 20000 → "2", 123450000 % 1억 = 23450000 → "2,345". */
	private static String manDecimal(long v) {
		long man = v / 10_000L;
		long rest = (v % 10_000L) / 100L; // 백 원 단위까지(소수 둘째 자리)
		if (rest == 0) {
			return num(man);
		}
		String d = String.format(Locale.ROOT, "%02d", rest);
		if (d.endsWith("0")) {
			d = d.substring(0, 1);
		}
		return num(man) + "." + d;
	}

	// ==================== 갈무리 글자 ====================

	private Text gObj(String s) {
		// 49-155차(사용자: "기능에 폰트 기본으로 다 되돌려"): 갈무리 고정을 풀고 글꼴 설정(기본 = 마크 글꼴)을 따른다.
		return LunaGfx.text(s);
	}

	private int gWidth(String s) {
		return LunaCompat.textWidth(textRenderer, gObj(s));
	}

	private void gText(DrawContext ctx, String s, int x, int boxY, int boxH, int color) {
		ctx.drawText(textRenderer, gObj(s), x, boxY + Math.round(boxH / 2f - LunaCompat.textVisualCenter()), LunaDraw.applyAlpha(color), false);
	}

	private boolean gBig(DrawContext ctx, String s, int x, int boxY, int boxH, int color, int maxW) {
		if (!LunaCompat.guiTransformSupported(ctx) || gWidth(s) * 2 > maxW) {
			gText(ctx, s, x, boxY, boxH, color);
			return false;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, x, boxY + Math.round(boxH / 2f - LunaCompat.textVisualCenter() * 2f));
			LunaCompat.guiScale(ctx, 2f, 2f);
			ctx.drawText(textRenderer, gObj(s), 0, 0, LunaDraw.applyAlpha(color), false);
		} finally {
			LunaCompat.guiPop(ctx);
		}
		return true;
	}

	private void card(DrawContext ctx, int x, int y, int w, int h) {
		LunaDraw.card3d(ctx, x, y, w, h, 4, LunaClientScreen.themeCard(), LunaClientScreen.themeLine());   // 49-227차
	}

	// ==================== 그리기 ====================

	private void lunaRender0(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaClientScreen.applyScreenTheme();
		try {
			draw(ctx, mouseX, mouseY, delta);
		} finally {
			LunaClientScreen.restoreScreenTheme();
		}
	}

	private void draw(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		layout();
		tip = null;
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF)
			| (LunaClientScreen.themeLight() ? 0xF5000000 : 0xEE000000)));

		// 제목 + 닫기
		gBig(ctx, "레벨 계산기", left, TOP_Y, 24, txt, contentW);
		String note = "너굴마을 위키 기준 | 레벨업 조건: 생활 경험치 + 토벌 경험치";
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, note, contentW - 170), left + 150,
			LunaDraw.textY(TOP_Y + 6, 14), dim);
		boolean ch = LunaDraw.in(mouseX, mouseY, left + contentW - 16, TOP_Y + 4, 16, 16);
		LunaIcons.drawInBox(ctx, textRenderer, LunaIcons.CLOSE, left + contentW - 16, TOP_Y + 4, 16, ch ? acc : sub);

		// 입력 줄
		int x = left;
		gText(ctx, "시작 레벨", x, INPUT_Y, BOX_H, sub);
		x += gWidth("시작 레벨") + 8;
		x = drawStepper(ctx, 0, x, mouseX, mouseY) + 14;
		gText(ctx, "→", x, INPUT_Y, BOX_H, dim);
		x += gWidth("→") + 14;
		gText(ctx, "끝 레벨", x, INPUT_Y, BOX_H, sub);
		x += gWidth("끝 레벨") + 8;
		drawStepper(ctx, 1, x, mouseX, mouseY);
		ctx.fill(left, contentTop - 6, left + contentW, contentTop - 5, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));

		// 본문(스크롤)
		int bottom = height - 8;
		lunaV.scissor(ctx, left - 2, contentTop, left + contentW + 2, bottom);
		int y = contentTop + 2 - Math.round(scroll);
		int y0 = y;
		y = drawResults(ctx, y, mouseX, mouseY) + 12;
		y = drawTable(ctx, y, mouseX, mouseY) + 8;
		ctx.disableScissor();
		int total = y - y0;
		maxScroll = Math.max(0, total - (bottom - contentTop));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		if (maxScroll > 0) {
			int h = bottom - contentTop;
			int thumbH = Math.max(20, h * h / (h + maxScroll));
			int thumbY = contentTop + Math.round((h - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, left + contentW + 4, thumbY, 2, thumbH, 1, LunaClientScreen.ink(0x40));
		}
		if (tip != null) {
			ctx.drawTooltip(textRenderer, LunaCompat.textLiteral(tip), mouseX, mouseY);
		}
	}

	/** [-][ 숫자 ][+] - 반환 = 오른쪽 끝 x. 좌표는 클릭 판정용으로 기억해 둔다. */
	private final int[] stepX = new int[2];

	private int drawStepper(DrawContext ctx, int which, int x, int mouseX, int mouseY) {
		stepX[which] = x;
		int val = which == 0 ? startLv : endLv;
		boolean foc = focus == which;
		int line = LunaClientScreen.themeLine();
		int txt = LunaClientScreen.themeText();
		int acc = LunaClientScreen.themeAccent();
		// -
		boolean mh = LunaDraw.in(mouseX, mouseY, x, INPUT_Y, BTN_W, BOX_H);
		card(ctx, x, INPUT_Y, BTN_W, BOX_H);
		LunaDraw.textCentered(ctx, textRenderer, "-", x + BTN_W / 2, LunaDraw.textY(INPUT_Y, BOX_H), mh ? acc : txt);
		// 칸
		int fx = x + BTN_W + 2;
		LunaDraw.roundRect(ctx, fx, INPUT_Y, FIELD_W, BOX_H, 4, foc ? acc : line);
		LunaDraw.roundRect(ctx, fx + 1, INPUT_Y + 1, FIELD_W - 2, BOX_H - 2, 3, LunaClientScreen.themeCard());
		String shown = foc ? buf : String.valueOf(val);
		int tw = gWidth(shown);
		gText(ctx, shown, fx + (FIELD_W - tw) / 2, INPUT_Y, BOX_H, txt);
		if (foc && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = fx + (FIELD_W + tw) / 2 + 1;
			ctx.fill(cx, INPUT_Y + 4, cx + 1, INPUT_Y + BOX_H - 4, LunaDraw.applyAlpha(txt));
		}
		// +
		int px = fx + FIELD_W + 2;
		boolean ph = LunaDraw.in(mouseX, mouseY, px, INPUT_Y, BTN_W, BOX_H);
		card(ctx, px, INPUT_Y, BTN_W, BOX_H);
		LunaDraw.textCentered(ctx, textRenderer, "+", px + BTN_W / 2, LunaDraw.textY(INPUT_Y, BOX_H), ph ? acc : txt);
		return px + BTN_W;
	}

	/** 결과 상자 4개 + 구간 내 습득 가능 기능. */
	private int drawResults(DrawContext ctx, int y, int mouseX, int mouseY) {
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();
		if (endLv <= startLv) {
			card(ctx, left, y, contentW, 30);
			gText(ctx, "입력 오류: 끝 레벨은 시작 레벨 초과 필요", left + 10, y, 30, sub);
			return y + 30;
		}
		long exp = 0;
		long cost = 0;
		for (int lv = startLv; lv < endLv; lv++) {
			exp += EXP[lv];
			cost += COST[lv];
		}
		List<Skill> fresh = new ArrayList<>();
		long skillCost = 0;
		for (Skill k : SKILLS) {
			if (k.level() > startLv && k.level() <= endLv) {
				fresh.add(k);
				skillCost += k.cost();
			}
		}
		String[][] items = {
			{"생활 경험치", num(exp), "Lv." + startLv + " → Lv." + endLv},
			{"토벌 경험치", num(exp), "Lv." + startLv + " → Lv." + endLv},
			{"레벨업 비용", won(cost), num(cost) + "원"},
			{"습득 가능 기능", fresh.size() + "개", "모두 구매시 " + won(skillCost)}};
		int gap = 6;
		int cols = contentW >= 420 ? 4 : 2;
		int tw = (contentW - gap * (cols - 1)) / cols;
		int th = 52;
		for (int i = 0; i < items.length; i++) {
			int tx = left + (i % cols) * (tw + gap);
			int ty = y + (i / cols) * (th + gap);
			card(ctx, tx, ty, tw, th);
			LunaDraw.text(ctx, textRenderer, items[i][0], tx + 9, ty + 7, sub);
			gBig(ctx, items[i][1], tx + 9, ty + 17, 22, i == 2 ? acc : txt, tw - 16);
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, items[i][2], tw - 16), tx + 9, ty + th - 12, dim);
		}
		y += ((items.length + cols - 1) / cols) * (th + gap) - gap + 10;

		// 새로 배울 수 있는 기능 목록(두 줄씩)
		gText(ctx, "구간 내 습득 가능 기능", left, y, 14, sub);
		y += 18;
		if (fresh.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, "없음", left + 4, y, dim);
			return y + 12;
		}
		int colsL = contentW >= 420 ? 2 : 1;
		int cw = (contentW - (colsL - 1) * 12) / colsL;
		int per = (fresh.size() + colsL - 1) / colsL;
		for (int i = 0; i < fresh.size(); i++) {
			Skill k = fresh.get(i);
			int cx = left + (i / per) * (cw + 12);
			int cy = y + (i % per) * 12;
			String lv = "Lv." + k.level();
			LunaDraw.text(ctx, textRenderer, lv, cx, cy, acc);
			int nx = cx + 34;
			String kind = "[" + k.kind() + "]";
			LunaDraw.text(ctx, textRenderer, kind, nx, cy, dim);
			nx += LunaDraw.width(textRenderer, kind) + 4;
			String price = won(k.cost());
			int pw = LunaDraw.width(textRenderer, price);
			String name = LunaDraw.ellipsize(textRenderer, k.name() + "  " + k.effect(), cx + cw - pw - 8 - nx);
			LunaDraw.text(ctx, textRenderer, name, nx, cy, txt);
			LunaDraw.text(ctx, textRenderer, price, cx + cw - pw, cy, sub);
			if (LunaDraw.in(mouseX, mouseY, cx, cy - 1, cw, 12) && mouseY >= contentTop) {
				tip = k.name() + " | " + k.effect() + " | " + won(k.cost());
			}
		}
		return y + per * 12;
	}

	/** 위키 레벨표: 구간 | 생활 | 토벌 | 레벨업 비용 | 그 레벨에서 배울 수 있는 기능. 고른 구간은 강조. */
	private int drawTable(DrawContext ctx, int y, int mouseX, int mouseY) {
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();
		gText(ctx, "레벨표 (위키)", left, y, 14, sub);
		y += 18;
		int cLv = left + 4;
		int cLife = left + 84;
		int cHunt = left + 150;
		int cCost = left + 216;
		int cSkill = left + 290;
		boolean narrow = contentW < 460;
		if (narrow) {
			cHunt = -1;
			cCost = left + 150;
			cSkill = left + 224;
		}
		LunaDraw.text(ctx, textRenderer, "구간", cLv, y, dim);
		LunaDraw.text(ctx, textRenderer, narrow ? "생활=토벌" : "생활", cLife, y, dim);
		if (cHunt > 0) {
			LunaDraw.text(ctx, textRenderer, "토벌", cHunt, y, dim);
		}
		LunaDraw.text(ctx, textRenderer, "레벨업 비용", cCost, y, dim);
		LunaDraw.text(ctx, textRenderer, "도착 레벨 습득 기능", cSkill, y, dim);
		y += 12;
		ctx.fill(left, y - 2, left + contentW, y - 1, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
		int skillW = left + contentW - cSkill - 4;
		for (int lv = 1; lv < MAX_LV; lv++) {
			List<Skill> ks = skillsAt(lv + 1);
			List<String> lines = new ArrayList<>();
			StringBuilder cur = new StringBuilder();
			for (Skill k : ks) {
				String part = k.name();
				String next = cur.length() == 0 ? part : cur + ", " + part;
				if (cur.length() > 0 && LunaDraw.width(textRenderer, next) > skillW) {
					lines.add(cur.toString());
					cur = new StringBuilder(part);
				} else {
					cur = new StringBuilder(next);
				}
			}
			if (cur.length() > 0) {
				lines.add(cur.toString());
			}
			int rows = Math.max(1, lines.size());
			int rh = rows * 10 + 3;
			boolean in = lv >= startLv && lv < endLv;
			if (in) {
				LunaDraw.roundRect(ctx, left, y - 2, contentW, rh, 2, LunaDraw.withAlpha(acc, 0x22));
			} else if (lv % 2 == 0) {
				LunaDraw.roundRect(ctx, left, y - 2, contentW, rh, 2, LunaClientScreen.ink(0x08));
			}
			int c = in ? txt : sub;
			LunaDraw.text(ctx, textRenderer, lv + " → " + (lv + 1), cLv, y, in ? acc : c);
			LunaDraw.text(ctx, textRenderer, num(EXP[lv]), cLife, y, c);
			if (cHunt > 0) {
				LunaDraw.text(ctx, textRenderer, num(EXP[lv]), cHunt, y, c);
			}
			LunaDraw.text(ctx, textRenderer, won(COST[lv]), cCost, y, c);
			for (int i = 0; i < lines.size(); i++) {
				LunaDraw.text(ctx, textRenderer, lines.get(i), cSkill, y + i * 10, in ? txt : dim);
			}
			if (!ks.isEmpty() && LunaDraw.in(mouseX, mouseY, cSkill, y - 2, skillW, rh) && mouseY >= contentTop) {
				StringBuilder t = new StringBuilder("Lv." + (lv + 1) + ": ");
				for (int i = 0; i < ks.size(); i++) {
					Skill k = ks.get(i);
					t.append(i == 0 ? "" : ",  ").append(k.name()).append(" (").append(k.effect()).append(", ").append(won(k.cost())).append(")");
				}
				tip = t.toString();
			}
			y += rh;
		}
		return y;
	}

	// ==================== 입력 ====================

	private void commit() {
		if (focus < 0) {
			return;
		}
		try {
			int v = Integer.parseInt(buf.trim());
			v = Math.max(1, Math.min(MAX_LV, v));
			if (focus == 0) {
				startLv = v;
			} else {
				endLv = v;
			}
		} catch (NumberFormatException ignored) {
		}
		focus = -1;
		buf = "";
	}

	private void bump(int which, int d) {
		if (focus == which) {
			commit();
		}
		if (which == 0) {
			startLv = Math.max(1, Math.min(MAX_LV, startLv + d));
		} else {
			endLv = Math.max(1, Math.min(MAX_LV, endLv + d));
		}
	}

	/** 0 = -, 1 = 칸, 2 = +, -1 = 없음. */
	private int partAt(int which, double mx, double my) {
		int x = stepX[which];
		if (my < INPUT_Y || my >= INPUT_Y + BOX_H) {
			return -1;
		}
		if (mx >= x && mx < x + BTN_W) {
			return 0;
		}
		int fx = x + BTN_W + 2;
		if (mx >= fx && mx < fx + FIELD_W) {
			return 1;
		}
		int px = fx + FIELD_W + 2;
		if (mx >= px && mx < px + BTN_W) {
			return 2;
		}
		return -1;
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		layout();
		if (LunaDraw.in(mouseX, mouseY, left + contentW - 18, TOP_Y + 2, 20, 20)) {
			close();
			return true;
		}
		for (int w = 0; w < 2; w++) {
			int p = partAt(w, mouseX, mouseY);
			if (p == 0) {
				bump(w, -1);
				return true;
			}
			if (p == 2) {
				bump(w, 1);
				return true;
			}
			if (p == 1) {
				commit();
				focus = w;
				buf = "";
				return true;
			}
		}
		commit();
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double amount) {
		for (int w = 0; w < 2; w++) {
			if (partAt(w, mouseX, mouseY) >= 0) {
				bump(w, amount > 0 ? 1 : -1);
				return true;
			}
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (float) amount * 24f));
		return true;
	}

	private boolean lunaCharTyped0(char chr) {
		if (focus >= 0 && chr >= '0' && chr <= '9' && buf.length() < 3) {
			buf += chr;
			return true;
		}
		return focus >= 0;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (focus >= 0) {
				focus = -1;
				buf = "";
				return true;
			}
			close();
			return true;
		}
		if (focus >= 0) {
			switch (keyCode) {
				case GLFW.GLFW_KEY_BACKSPACE -> {
					if (!buf.isEmpty()) {
						buf = buf.substring(0, buf.length() - 1);
					}
				}
				case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commit();
				case GLFW.GLFW_KEY_TAB -> {
					int next = focus == 0 ? 1 : 0;
					commit();
					focus = next;
				}
				case GLFW.GLFW_KEY_UP -> {
					int w = focus;
					commit();
					bump(w, 1);
				}
				case GLFW.GLFW_KEY_DOWN -> {
					int w = focus;
					commit();
					bump(w, -1);
				}
				default -> {
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void close() {
		LunaCompat.setScreen(parent);
	}
}
