package kr.lunaslight.mod.util;

/**
 * Luna's Light 팔레트.
 *
 * 49-29차: 색이 고정이 아니라 **클라이언트에서 장착한 색**을 따라간다(사용자: "클라이언트 색상을 따라 인게임도
 * 회색/화이트/블랙/핑크 + 색상으로"). 런처가 .luna-launch.json에 실어 보낸 치장품 색(cosmetics.accent)을
 * 읽어 쓰고, 없으면 기본 연두. 게임 안에서는 [테마] 기능으로 직접 고를 수도 있다.
 *
 * ACCENT는 상수가 아니라 **갈아끼울 수 있는 값**이라, 색을 바꾸면 GUI(설정·통계·타이틀)와 기본 색을 쓰는
 * HUD가 한 번에 따라온다. 사용자가 직접 바꾼 HUD 색은 그대로 둔다(ColorSetting이 "기본값 그대로인 색"만
 * 테마 색으로 대체).
 */
public final class LunaTheme {
	private LunaTheme() {
	}

	/** 처음부터 있던 Luna 연두 - "기본값 그대로인지" 판단 기준이라 이 값은 바뀌지 않는다. */
	public static final int DEFAULT_ACCENT = 0xFFA9D973;

	public static int ACCENT = DEFAULT_ACCENT;
	/** 강조색 위에 올리는 글자색(밝은 색이면 검정, 어두운 색이면 흰색). */
	public static int ON_ACCENT = 0xFF0B0C0E;
	/** 강조색을 아주 옅게 깐 배경(선택된 카테고리 등). */
	public static int ACCENT_SOFT = 0x1AA9D973;
	/** 켜진 토글의 트랙/노브. 49-85차: 트랙이 너무 어두워 켜짐이 눈에 안 띄던 것 → 강조색이 확실히 도는 초록으로. */
	public static int TOGGLE_TRACK_ON = 0xFF566E42;
	public static int TOGGLE_ON = 0xFFB9E387;

	public static final int ACCENT_SECONDARY = 0xFFD7DBE0; // 라이트 그레이 (보조 강조/그래프)
	public static final int BACKGROUND = 0xE30F1113;     // 패널 배경 (중립 그래파이트)
	public static final int BACKGROUND_SOLID = 0xFF121417;
	public static final int PANEL_BORDER = 0x40FFFFFF;
	public static final int TEXT_PRIMARY = 0xFFECECF1;
	public static final int TEXT_SECONDARY = 0xFFA0A0B0;
	// 18차 추가: 버전 미지원으로 잠긴 기능 표시용 (버전 게이팅 시스템, LunaVersion 참고)
	public static final int TEXT_DISABLED = 0xFF5A5866;
	public static final int TOGGLE_OFF = 0xFF33373B;
	public static final int DANGER = 0xFFEF4444;
	public static final int SUCCESS = 0xFF22C55E;
	public static final int WARNING = 0xFFF59E0B;

	public static final int HUD_TEXT_DEFAULT = 0xFFFFFFFF;
	public static final int HUD_SHADOW = 0x80000000;

	// ==================== 프리셋 ====================

	/** 고를 수 있는 색(런처에서 파는 색과 같은 이름). */
	public enum Preset {
		AUTO("자동", 0),                 // 런처에서 장착한 색(없으면 Luna 연두)
		LUNA("노바 연두", DEFAULT_ACCENT),
		GRAY("회색", 0xFF9AA3AD),
		WHITE("화이트", 0xFFF2F4F6),
		BLACK("블랙", 0xFF3A3F46),
		PINK("핑크", 0xFFFF8FC7),
		CUSTOM("직접 선택", 0);

		private final String label;
		public final int color;

		Preset(String label, int color) {
			this.label = label;
			this.color = color;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	// ==================== 49-40차: 배경 테마(런처 "테마" 카테고리 = 배경·글자까지 바뀌는 완전 테마) ====================
	// 사용자: "클라이언트를 블랙 테마에 보라색으로 하고 갔는데 전혀 적용이 안 됐어" - 지금까지 모드는 포인트색(accent)만
	// 따라갔고 배경(런처 equippedThemeMode: pure-black/cute/aqua/sky/minecraft/metal)은 아예 안 받았다.
	// 값은 런처 style.css의 body[data-color-theme=…] 변수(--bg-0~3/--border-soft/--text-0~2/--accent)를 그대로 옮김.
	// 핑크(cute)만 런처에선 밝은 파스텔인데 게임 화면은 흰 글자·흰 테두리를 곳곳에 박아 둔 어두운 UI라
	// 어두운 핑크 톤으로 옮겼다(글자는 밝게 유지).

	/** 배경 프리셋(런처 "테마" 상품 이름 그대로). */
	public enum Base {
		AUTO("자동", null),
		NEUTRAL("기본", new Palette(0x090A0C, 0x0F1114, 0x171A1E, 0x1D2024, 0, 0xECECF1, 0xA0A0B0, 0x5A5866, DEFAULT_ACCENT)),
		PURE_BLACK("블랙 & 화이트", new Palette(0x000000, 0x0C0C0C, 0x1C1C1C, 0x272727, 0, 0xF4F5F6, 0xA5A8B0, 0x6D7078, 0xFFE6E6E6)),
		CUTE("핑크", new Palette(0x23101A, 0x2E1622, 0x3A1C2B, 0x48243A, 0xF0A6C7, 0xFFE9F2, 0xE3B6CB, 0xB0728E, 0xFFEF4F90)),
		AQUA("아쿠아", new Palette(0x062430, 0x0A3446, 0x0F4459, 0x145670, 0x2D7F96, 0xEAF9FB, 0xA9D8DE, 0x74A8B1, 0xFF2FB8EC)),
		SKY("스카이", new Palette(0x100C2E, 0x1A1440, 0x241C54, 0x302668, 0x675CB0, 0xF1EDFF, 0xC3B9EC, 0x8D81BD, 0xFFA78BFA)),
		MINECRAFT("마인크래프트", new Palette(0x1A1D22, 0x23272E, 0x2E333B, 0x3C424C, 0x454C57, 0xF2F4F6, 0xC6CDD6, 0x8E97A3, 0xFF5EB83A)),
		METAL("메탈", new Palette(0x191D22, 0x222932, 0x2B333D, 0x39424D, 0x3B434C, 0xF0F4F8, 0xBCC7D2, 0x838F9B, 0xFFB4C4D4));

		private final String label;
		public final Palette palette;

		Base(String label, Palette palette) {
			this.label = label;
			this.palette = palette;
		}

		@Override
		public String toString() {
			return label;
		}

		/** 런처 mode 문자열("pure-black" 등) → 프리셋. 모르면 null. */
		public static Base fromLauncherMode(String mode) {
			if (mode == null) {
				return null;
			}
			return switch (mode.trim().toLowerCase(java.util.Locale.ROOT)) {
				case "pure-black", "pure_black", "black", "theme-pure-black" -> PURE_BLACK;
				case "cute", "pink", "theme-cute" -> CUTE;
				case "aqua", "theme-aqua" -> AQUA;
				case "sky", "theme-sky" -> SKY;
				case "minecraft", "theme-minecraft" -> MINECRAFT;
				case "metal", "theme-metal" -> METAL;
				case "", "none", "default", "dark", "neutral" -> NEUTRAL;
				default -> null;
			};
		}
	}

	/**
	 * 배경 팔레트(RGB, 알파 없음). borderSoft가 0이면 테두리는 흰색 반투명(기본·블랙&화이트처럼 무채색 테마).
	 * accent는 이 테마의 기본 포인트색 - 런처에서 색상 상품을 따로 장착했으면 그쪽이 우선.
	 */
	public record Palette(int bg0, int bg1, int bg2, int bg3, int borderSoft, int text0, int text1, int text2, int accent) {
	}

	// 지금 적용된 배경 값들(LunaDraw가 사본을 들고 있고 refresh()가 같이 갱신)
	public static int PANEL = 0xF0090A0C;
	public static int SIDEBAR = 0xF007080A;
	public static int CARD = 0xF00F1114;
	public static int CARD_HOVER = 0xF6171A1E;
	public static int TRACK = 0xF01D2024;
	public static int PANEL_BORDER_LIVE = 0x1AFFFFFF;
	public static int CARD_BORDER = 0x16FFFFFF;
	public static int TEXT = TEXT_PRIMARY;
	public static int TEXT_SUB = TEXT_SECONDARY;
	public static int TEXT_DIM = TEXT_DISABLED;
	/** 타이틀·일시정지의 반투명 버튼(뒤가 비침) - 패널색에 알파만 다르게. */
	public static int BTN_BG = 0x9E090A0C;
	public static int BTN_BG_HOVER = 0xC716191D;

	private static Base base = Base.AUTO;
	private static Base launcherBase;

	/** 런처(.luna-launch.json cosmetics.theme.mode)에서 읽은 배경 테마를 등록. */
	public static void setLauncherBase(Base b) {
		launcherBase = b;
		refresh();
	}

	public static void setBase(Base b) {
		base = b == null ? Base.AUTO : b;
		refresh();
	}

	public static Base base() {
		return base;
	}

	private static Base effectiveBase() {
		if (base == Base.AUTO) {
			return launcherBase != null ? launcherBase : Base.NEUTRAL;
		}
		return base;
	}

	/** 런처가 알려 준 장착 색(0이면 없음). */
	private static int launcherAccent;
	/** HUD 기본색도 테마를 따라갈지(테마 기능 설정). */
	private static boolean hudFollows = true;

	public static void setHudFollows(boolean follows) {
		hudFollows = follows;
	}
	private static Preset preset = Preset.AUTO;
	private static int customColor = DEFAULT_ACCENT;

	/** 런처(.luna-launch.json)에서 읽은 색을 등록. */
	public static void setLauncherAccent(int argb) {
		launcherAccent = argb;
		refresh();
	}

	public static boolean hasLauncherAccent() {
		return launcherAccent != 0;
	}

	public static void setPreset(Preset p, int custom) {
		preset = p == null ? Preset.AUTO : p;
		customColor = custom;
		refresh();
	}

	public static Preset preset() {
		return preset;
	}

	// ==================== 49-238차: 화면 스킨(클라이언트에서 사서 쓰는 별도 UI 색 묶음) ====================
	// 사용자: "이런 느낌의 색상 UI도 만들 건데, 클라이언트에서 아이템을 구매하면 쓸 수 있게 - 인게임 UI가 기본 테마랑 따로".
	// 스킨은 배경 테마(Base, 런처 테마 상품)와 따로 Nova 화면 전체의 판/카드/글자/버튼 색을 통째로 바꾼다.
	// 크림 = 밝은 베이지 판 + 흰 크림 카드 + 짙은 갈색 글자 + 초록 주 버튼(장비 강화 창 시안).
	// 구매 연동은 나중 - 지금은 setSkinOwnership으로 막을 자리만 두고 모두 열어 둔다.

	public enum Skin {
		DEFAULT("기본"),
		CREAM("크림");

		private final String label;

		Skin(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** 크림 스킨 팔레트(bg0 판, bg1 카드, bg2 카드 호버, bg3 트랙, 테두리, 글자 셋, 주 버튼 초록). */
	public static final Palette CREAM_PALETTE = new Palette(0xF7EEDC, 0xFFFBF2, 0xFBF1DE, 0xEADCC2, 0xD9C6A2,
		0x3B2C1D, 0x7D6648, 0xB09C7E, 0xFF5B9B3C);

	private static Skin skin = Skin.DEFAULT;
	private static java.util.function.Predicate<Skin> skinOwned = s -> true;

	/** 고른 스킨(가졌는지와 무관하게 설정값 그대로). */
	public static Skin chosenSkin() {
		return skin;
	}

	public static void setSkin(Skin s) {
		Skin n = s == null ? Skin.DEFAULT : s;
		if (n != skin) {
			skin = n;
			refresh();
		}
	}

	/** 구매 연동이 생기면 런처가 알려 준 보유 목록으로 바꿔 끼운다(가지지 않은 스킨은 기본으로 보인다). */
	public static void setSkinOwnership(java.util.function.Predicate<Skin> owned) {
		skinOwned = owned == null ? s -> true : owned;
		refresh();
	}

	public static boolean skinOwned(Skin s) {
		try {
			return s == Skin.DEFAULT || skinOwned.test(s);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 지금 실제로 쓰는 스킨. */
	public static Skin skin() {
		return skin != Skin.DEFAULT && skinOwned(skin) ? skin : Skin.DEFAULT;
	}

	/** 지금 쓸 색·배경을 정해서 팔레트 전체에 반영. */
	public static void refresh() {
		Base b = effectiveBase();
		boolean cream = skin() == Skin.CREAM;
		Palette pal = cream ? CREAM_PALETTE : b.palette;
		int accent = switch (preset) {
			case AUTO -> cream ? CREAM_PALETTE.accent() : launcherAccent != 0 ? launcherAccent
				: (launcherBase != null && launcherBase != Base.NEUTRAL ? pal.accent() : DEFAULT_ACCENT);
			case CUSTOM -> customColor;
			default -> preset.color;
		};
		applyBase(pal);
		apply(accent);
	}

	private static void applyBase(Palette p) {
		PANEL = 0xF0000000 | p.bg0();
		SIDEBAR = 0xF0000000 | (mix(0xFF000000 | p.bg0(), 0xFF000000, 0.22f) & 0x00FFFFFF);
		CARD = 0xF0000000 | p.bg1();
		CARD_HOVER = 0xF6000000 | p.bg2();
		TRACK = 0xF0000000 | p.bg3();
		if (p.borderSoft() == 0) {
			PANEL_BORDER_LIVE = 0x1AFFFFFF;
			CARD_BORDER = 0x16FFFFFF;
		} else {
			PANEL_BORDER_LIVE = 0x8C000000 | p.borderSoft();
			CARD_BORDER = 0x66000000 | p.borderSoft();
		}
		TEXT = 0xFF000000 | p.text0();
		TEXT_SUB = 0xFF000000 | p.text1();
		TEXT_DIM = 0xFF000000 | p.text2();
		BTN_BG = 0x9E000000 | p.bg0();
		BTN_BG_HOVER = 0xC7000000 | p.bg2();
	}

	private static void apply(int accent) {
		ACCENT = 0xFF000000 | (accent & 0x00FFFFFF);
		ACCENT_SOFT = (ACCENT & 0x00FFFFFF) | 0x1A000000;
		ON_ACCENT = luminance(ACCENT) > 0.55f ? 0xFF0B0C0E : 0xFFF2F4F6;
		TOGGLE_TRACK_ON = mix(ACCENT, 0xFF101216, 0.5f);   // 49-85차: 0.72(거의 검정) → 0.5, 켜짐이 확실히 초록으로 보이게
		TOGGLE_ON = mix(ACCENT, 0xFFFFFFFF, 0.28f);
		// GUI 헬퍼가 들고 있는 사본도 같이 갱신(호출부는 그대로 LunaDraw.ACCENT 등을 씀)
		try {
			Class<?> draw = Class.forName("kr.lunaslight.mod.gui.LunaDraw");
			draw.getField("ACCENT").setInt(null, ACCENT);
			draw.getField("ACCENT_SOFT").setInt(null, ACCENT_SOFT);
			draw.getField("PANEL").setInt(null, PANEL);
			draw.getField("SIDEBAR").setInt(null, SIDEBAR);
			draw.getField("CARD").setInt(null, CARD);
			draw.getField("CARD_HOVER").setInt(null, CARD_HOVER);
			draw.getField("TRACK").setInt(null, TRACK);
			draw.getField("PANEL_BORDER").setInt(null, PANEL_BORDER_LIVE);
			draw.getField("CARD_BORDER").setInt(null, CARD_BORDER);
			draw.getField("TEXT").setInt(null, TEXT);
			draw.getField("TEXT_SUB").setInt(null, TEXT_SUB);
			draw.getField("TEXT_DIM").setInt(null, TEXT_DIM);
			draw.getField("BTN_BG").setInt(null, BTN_BG);
			draw.getField("BTN_BG_HOVER").setInt(null, BTN_BG_HOVER);
		} catch (Throwable ignored) {
			// gui 패키지가 없는 서브프로젝트(구버전) - 무시
		}
	}

	// 49-141차: 설정 화면 다크/라이트(사용자: "라이트랑 다크 둘 다, 설정에서 고르게"). 값은 InterfaceStyleModule의
	// [화면 테마] 설정이 공급자로 걸어 준다(이 클래스는 모듈을 모르는 util이라 공급자로 받는다).
	private static java.util.function.BooleanSupplier lightSupplier = () -> false;

	public static void setLightSupplier(java.util.function.BooleanSupplier supplier) {
		lightSupplier = supplier == null ? () -> false : supplier;
	}

	// 49-148차: 설정 화면 기능 보기(0 = 큰 박스, 1 = 작은 박스, 2 = 자세히 보기). 공급자는 InterfaceStyleModule.
	private static java.util.function.IntSupplier tileViewSupplier = () -> 0;

	public static void setTileViewSupplier(java.util.function.IntSupplier supplier) {
		tileViewSupplier = supplier == null ? () -> 0 : supplier;
	}

	public static int tileView() {
		try {
			return tileViewSupplier.getAsInt();
		} catch (Throwable t) {
			return 0;
		}
	}

	// 49-168차: 설정 화면 검색 칸 옆 스위치가 값을 바꾼다(저장은 InterfaceStyleModule의 숨은 설정).
	private static java.util.function.IntConsumer tileViewSetter = v -> {};

	public static void setTileViewSetter(java.util.function.IntConsumer setter) {
		tileViewSetter = setter == null ? v -> {} : setter;
	}

	public static void setTileView(int view) {
		try {
			tileViewSetter.accept(Math.max(0, Math.min(2, view)));
		} catch (Throwable ignored) {
			// 공급자 없음
		}
	}

	/** 설정 화면을 밝게(라이트) 그릴지. */
	public static boolean light() {
		try {
			return skin() == Skin.CREAM || lightSupplier.getAsBoolean();   // 49-238차: 크림 스킨 = 밝은 화면
		} catch (Throwable t) {
			return false;
		}
	}

	/** 0~1 밝기(대비 판단용). */
	public static float luminance(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f;
		float g = ((argb >> 8) & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		return 0.2126f * r + 0.7152f * g + 0.0722f * b;
	}

	/** a와 b를 t만큼 섞음(알파는 a 유지). */
	public static int mix(int a, int b, float t) {
		int aa = (a >>> 24) & 0xFF;
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (aa << 24) | (r << 16) | (g << 8) | bl;
	}

	/**
	 * 49-29차: "기본 색 그대로인" 설정값을 지금 테마 색으로 바꿔 준다(사용자가 직접 고른 색은 그대로).
	 * 기준은 처음 기본값이던 Luna 연두 - 알파는 원래 값을 유지한다.
	 */
	public static int themed(int color) {
		if (!hudFollows || (color & 0x00FFFFFF) != (DEFAULT_ACCENT & 0x00FFFFFF)) {
			return color;
		}
		return (color & 0xFF000000) | (ACCENT & 0x00FFFFFF);
	}
}
