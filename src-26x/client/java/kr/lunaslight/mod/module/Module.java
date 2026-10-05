package kr.lunaslight.mod.module;

import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.Setting;
import kr.lunaslight.mod.util.LunaVersion;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;

/**
 * 기능 하나(예: "CPS 표시", "좌표/바이옴 HUD")의 공통 부모.
 *
 * 요청사항 반영:
 *  - 전부 개별로 껐다 켤 수 있어야 함 -> {@link #enabled} + {@link #toggle()}
 *  - 위치/테두리/색 등 세부 커스텀 -> {@link #settings}에 등록해두면 설정 GUI가 자동으로 그려줌
 *  - "겹치는 기능은 자동으로 꺼주기" -> {@link #conflictsWith}에 등록해두면
 *    ModuleManager가 이 모듈을 켤 때 그 목록에 있는 모듈들을 자동으로 끔
 *    (단, 사용자가 그 모듈을 "직접" 켠 상태라면 그대로 둠 - ModuleManager 참고)
 */
public abstract class Module {
	protected final Minecraft client = Minecraft.getInstance();

	private final String id;
	private final String displayName;
	private final ModuleCategory category;
	private final String description;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final List<String> conflictsWith = new ArrayList<>();

	private boolean enabled;
	private boolean enabledByDefault;

	// 18차 추가 - 멀티버전 지원(버전 게이팅). 둘 다 null이면 "모든 버전에서 사용 가능"(기존
	// 모듈 46개는 전부 이 기본값 그대로라 이번 변경으로 동작이 바뀌지 않음). 실제로 특정
	// 마인크래프트 버전부터/까지만 되는 기능을 새로 추가할 때만 supportedVersions()를 호출.
	private String minVersion;
	private String maxVersion;

	// 49-56차: 기능 격자가 아니라 [일반]·[UI]·[그래픽] 설정 페이지에 실리는 것. null이면 기능.
	private SettingsPage page;

	/**
	 * 49-56차: 이 모듈을 <b>기능이 아니라 클라이언트 설정</b>으로 취급한다({@link SettingsPage} 주석 참고).
	 * 기능 격자와 [전체] 탭에서 빠지고, 설정 화면 앞쪽의 해당 페이지에 평평한 줄로 나온다.
	 */
	protected Module settingsPage(SettingsPage value) {
		this.page = value;
		return this;
	}

	/** 이 모듈이 실리는 설정 페이지(기능이면 null). */
	public SettingsPage getPage() {
		return page;
	}

	protected Module(String id, String displayName, ModuleCategory category, String description) {
		this.id = id;
		this.displayName = displayName;
		this.category = category;
		this.description = description;
		// 49-21차: 49-7차에 넣었던 "모든 모듈 자동 토글 키"(toggle_key)는 사용자 요청("쓸데없는
		// 기능")으로 제거. 저장 파일에 남은 toggle_key 값은 대응 설정이 없으니 그냥 무시된다.
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public ModuleCategory getCategory() {
		return category;
	}

	public String getDescription() {
		return description;
	}

	public List<Setting<?>> getSettings() {
		return settings;
	}

	public List<String> getConflicts() {
		return conflictsWith;
	}

	public boolean isEnabled() {
		return alwaysOn || enabled;
	}

	/**
	 * 49-34차(사용자: "루나 글꼴 키고 끄는 기능은 왜 있는 거야 그냥 고르게만 하면 되지"):
	 * 켜고 끄는 개념이 없는 "설정 묶음"(글꼴 등). 항상 켜져 있고, 설정 화면에도 켜기 행이 안 뜬다.
	 */
	private boolean alwaysOn;

	protected Module alwaysOn() {
		this.alwaysOn = true;
		this.enabled = true;
		this.enabledByDefault = true;
		return this;
	}

	/**
	 * 49-149차: 설정 화면에서 상자를 누르면 설정 대신 이 기능만의 화면을 연다(레벨 계산기처럼 입력 > 결과 방식).
	 * parent = 돌아갈 화면. 열었으면 true.
	 */
	public boolean openCustomScreen(Object parent) {
		return false;
	}

	public boolean isAlwaysOn() {
		return alwaysOn;
	}

	public boolean isEnabledByDefault() {
		return enabledByDefault;
	}

	/** true를 넘기면 config에 저장된 값이 없을 때 기본으로 켜진 채 시작. */
	// ==================== 49-138차: 서버 전용 기능의 서버 이름 ====================
	private String serverGroup;

	/** 서버 전용 기능이면 그 서버 이름(설정 화면 [서버] 분류에서 서버별로 묶는다). 아니면 null. */
	public String serverGroup() {
		return serverGroup;
	}

	protected Module serverGroup(String name) {
		this.serverGroup = name;
		return this;
	}

	// ==================== 49-256차: 지금 서버에서 안 되는 기능 ====================
	// 사용자: "서버에서 작동 안 하는 것들은 서버에 들어갔을 때 기능들이 회색으로". 목록 카드가 이 값을 보고 회색 + 이유를 띄운다.
	private boolean singleOnly;

	/** 싱글(LAN 포함)에서만 되는 기능 - 멀티 서버에 들어가 있으면 목록에서 회색. */
	protected Module singleOnly() {
		this.singleOnly = true;
		return this;
	}

	/** 지금 멀티 서버(내 컴퓨터의 통합 서버가 아닌 곳)에 들어가 있나. */
	protected boolean onRemoteServer() {
		try {
			return client != null && client.level != null && !kr.lunaslight.mod.util.LunaCompat.isSinglePlayer(client);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 지금 있는 곳에서 이 기능이 안 되면 그 이유(짧게), 되면 null. 기능마다 덮어써서 서버별 제한을 더할 수 있다. */
	public String unavailableHere() {
		return singleOnly && onRemoteServer() ? "서버에서 안 됨" : null;
	}

	protected Module defaultEnabled(boolean value) {
		this.enabledByDefault = value;
		this.enabled = value;
		return this;
	}

	/** 이 모듈이 켜질 때 자동으로 꺼줄, 서로 겹치는 기능의 id들 (예: 여러 크로스헤어 모드끼리). */
	protected Module conflicts(String... otherModuleIds) {
		for (String otherId : otherModuleIds) {
			conflictsWith.add(otherId);
		}
		return this;
	}

	// 버그 수정(19차): 원래 <T> Setting<T> register(Setting<T>)였는데, 이러면 리턴 타입이
	// 항상 Setting<T>라서 `BooleanSetting x = register(new BooleanSetting(...))`처럼
	// 구체 서브클래스 변수에 대입하는 모든 곳에서 "incompatible types" 컴파일 에러가 남
	// (46개 모듈 거의 전부에서 나던 에러 중 절대다수가 이거 하나 때문이었음). 넘긴 타입 그대로
	// 돌려주도록 고침.
	protected <S extends Setting<?>> S register(S setting) {
		settings.add(setting);
		return setting;
	}

	// ==================== 49-6차: HUD 공통 배경 스타일 ====================
	// "HUD들이 기본적으로 백그라운드/윤곽선/색을 설정할 수 있게" - 텍스트 HUD 모듈이
	// 생성자에서 enableHudStyle()을 부르면 4개 설정이 자동 등록되고, drawHudLine()이
	// 배경(패딩 3px)+윤곽선+텍스트를 한 번에 그려줌. gui 패키지 의존 없음(fill만 사용).
	private BooleanSetting hudBg;
	private ColorSetting hudBgColor;
	private BooleanSetting hudBgOutline;
	private ColorSetting hudBgOutlineColor;
	/** 49-156차(사용자: "모든 설정들 배경 설정 따로"): 기능마다 배경 상자 모양. [HUD 배경 따름]이면 UI 설정의 모양. */
	private kr.lunaslight.mod.module.setting.EnumSetting<HudShape> hudBgShape;
	// 49-170차(사용자: "기능들 배경을 직접 픽셀로 그릴 수 있게"): 픽셀 그림 문자열(숨김) + [배경 그리기] 버튼.
	private kr.lunaslight.mod.module.setting.StringSetting hudBgPixels;
	private kr.lunaslight.mod.util.PixelArt hudPixelArt;
	private String hudPixelArtSrc;

	public enum HudShape {
		FOLLOW("HUD 배경 따름"),
		ROUND("둥근"),
		SQUARE("네모난"),
		CREAM("크림"),        // 49-257차: 크림 UI를 가졌으면 HUD도 크림 상자로(없으면 둥근으로 보인다)
		MIDNIGHT("미드나잇"), // 49-279차: 미드나잇 UI를 가졌을 때(없으면 둥근)
		NEON("네온"),         // 49-279차: 네온 사이버 UI를 가졌을 때(없으면 둥근)
		PIXEL("직접 그림"),   // 49-170차: 픽셀 편집기로 그린 배경(9분할 반복)
		NONE("없음");

		private final String label;

		HudShape(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	protected void enableHudStyle() {
		enableHudStyle(0x66090A0C, false, 0xB40B0C0E, HudShape.FOLLOW);
	}

	/**
	 * 49-156차: 자기 상자를 그리던 기능(달리기, 듣고 있는 노래, 블록 정보 …)도 같은 [배경] 설정을 갖게 - 그 기능이
	 * 원래 쓰던 색과 모양을 기본값으로 넘긴다. 상자는 {@link #drawHudBox}로 그린다.
	 */
	protected void enableHudStyle(int defBg, boolean defOutline, int defOutlineColor, HudShape defShape) {
		hudBg = register(new BooleanSetting("hud_bg", "배경", "글자 뒤에 배경 상자를 깝니다.", true));
		hudBgColor = register(new ColorSetting("hud_bg_color", "배경 색", "배경 상자의 색입니다. 투명도를 포함합니다.", defBg));
		hudBgOutline = register(new BooleanSetting("hud_bg_outline", "윤곽선", "배경 상자에 테두리를 두릅니다.", defOutline));
		hudBgOutlineColor = register(new ColorSetting("hud_bg_outline_color", "윤곽선 색", "테두리의 색입니다.", defOutlineColor));
		hudBgShape = register(new kr.lunaslight.mod.module.setting.EnumSetting<>("hud_bg_shape", "배경 모양",
			"이 기능의 배경 상자 모양입니다. [HUD 배경 따름]이면 UI 설정의 [HUD 배경] 모양을 씁니다.", defShape, HudShape.class));
		// 49-23차: 색 설정은 스위치 옆 견본으로(설정 화면 1줄 통합)
		hudBg.withColor(hudBgColor);
		hudBgOutline.withColor(hudBgOutlineColor);
		hudBgPixels = register(new kr.lunaslight.mod.module.setting.StringSetting("hud_bg_pixels", "배경 그림", "", ""));
		hudBgPixels.hidden();
		register(new kr.lunaslight.mod.module.setting.ActionSetting("hud_bg_draw", "배경 그리기",
			"이 기능의 배경을 픽셀 단위로 직접 그립니다. 그리면 [배경 모양]이 [직접 그림]으로 바뀝니다.", "그리기", () -> {
				if (client != null) {
					kr.lunaslight.mod.util.LunaCompat.setScreen(new kr.lunaslight.mod.gui.LunaPixelEditorScreen((net.minecraft.client.gui.screens.Screen) kr.lunaslight.mod.util.LunaCompat.screenOf(client), this));
				}
			}).style());
	}

	/** 49-170차: 그려 둔 배경 그림(없으면 null). 설정 문자열이 바뀌면 다시 푼다. */
	public kr.lunaslight.mod.util.PixelArt hudPixelArt() {
		if (hudBgPixels == null) {
			return null;
		}
		String src = hudBgPixels.get();
		if (src == null || src.isEmpty()) {
			hudPixelArt = null;
			hudPixelArtSrc = src;
			return null;
		}
		if (!src.equals(hudPixelArtSrc)) {
			hudPixelArt = kr.lunaslight.mod.util.PixelArt.decode(src);
			hudPixelArtSrc = src;
		}
		return hudPixelArt;
	}

	/** 편집기가 저장할 때: 그림을 넣고 모양을 [직접 그림]으로. */
	public void setHudPixelArt(kr.lunaslight.mod.util.PixelArt art) {
		if (hudBgPixels == null) {
			return;
		}
		hudBgPixels.setValue(art == null ? "" : art.encode());
		if (art != null && hudBgShape != null) {
			hudBgShape.setValue(HudShape.PIXEL);
		}
	}

	public boolean hasHudStyle() {
		return hudBg != null;
	}

	// 49-172차: 픽셀 편집기 연동(LunaPixelEditorScreen). 편집 중인 기능/그림과, 그 기능이 마지막으로 그린 상자 자리.
	public static Module pixelEditing;
	public static kr.lunaslight.mod.util.PixelArt pixelEditingArt;
	public static final int[] pixelEditBox = new int[4];
	public static boolean pixelEditBoxSeen;

	/**
	 * 49-176차: 자기 모양을 직접 그리는 HUD(아이템 획득의 화살 막대 등)가 "지금은 직접 그린 배경을 써야 하는지".
	 * 편집기가 이 기능을 편집 중이거나, 모양이 [직접 그림]이고 그림이 있으면 true - 그땐 제 모양 대신 drawHudBox를 부른다.
	 */
	protected boolean hudPixelActive() {
		return this == pixelEditing || (hudBgShape != null && hudBgShape.get() == HudShape.PIXEL && hudPixelArt() != null);
	}

	/** 편집기의 기본 그림(둥근 상자)용 - 이 기능의 배경 색. */
	public int hudBgColorForEditor() {
		return hudBgColorArgb();
	}


	// 49-122차: 자기만의 카드를 직접 그리는 HUD(작물 계산기 고퀄 등)가 [배경]·[윤곽선] 스타일 설정을
	// 그대로 따를 수 있게 값을 노출한다. enableHudStyle()을 안 불렀으면 기본값을 돌려준다.
	protected boolean hudBgEnabled() {
		return hudBg == null || hudBg.get();
	}

	protected int hudBgColorArgb() {
		return hudBgColor == null ? 0x66090A0C : hudBgColor.getArgb();
	}

	protected boolean hudBgOutlineEnabled() {
		return hudBgOutline != null && hudBgOutline.get();
	}

	protected int hudBgOutlineColorArgb() {
		return hudBgOutlineColor == null ? 0xB40B0C0E : hudBgOutlineColor.getArgb();
	}

	/**
	 * 49-65차(5-6): HUD 상자가 글자보다 사방으로 더 차지하는 여백. 편집기가 화면 끝 클램프에 이 값을
	 * 그대로 쓴다 - 예전엔 편집기가 <b>글자 크기만</b> 보고 끝에 딱 붙여서, 상자의 이 4px이 화면 밖으로
	 * 나가 "조금 잘려" 보였다. 한 곳에 두고 같이 쓰면 다시 어긋날 수 없다.
	 */
	public static final int HUD_BOX_PAD = 4;

	/** 배경 스타일 설정이 켜져 있으면 (x,y,w,h) 영역 뒤에 배경/윤곽선을 그림. */
	protected void drawHudPanel(GuiGraphicsExtractor context, int x, int y, int w, int h) {

		hudBox(context, x - HUD_BOX_PAD, y - HUD_BOX_PAD, w + HUD_BOX_PAD * 2, h + HUD_BOX_PAD * 2);
	}

	/**
	 * 49-12차: HUD 배경 상자 - 각진 fill 대신 둥근 모서리(r4) + 옵션 윤곽선.
	 * 예전 상자는 글리프 실측 밴드(-3..+8.5)를 무시하고 폰트 높이 9 기준으로 그려서
	 * 글자가 상자 위 모서리에 붙어 보였음(아래는 4px 남는데 위는 0px) - 세련되지 않던 원인.
	 */
	private void hudBox(GuiGraphicsExtractor context, int bx, int by, int bw, int bh) {
		hudBox(context, bx, by, bw, bh, 1f);
	}

	private void hudBox(GuiGraphicsExtractor context, int bx, int by, int bw, int bh, float alpha) {
		if (hudBg == null) {
			return;
		}
		// 49-172차: 픽셀 편집기가 이 기능을 편집 중이면 상자 자리를 적어 두고 편집 중인 그림을 그 자리에 그린다
		// (편집기는 이 자리를 캔버스로 쓴다 - 그래서 HUD가 실제로 어떻게 생겼는지 위에 겹쳐 보인다).
		if (this == pixelEditing) {
			// 49-176차: 줄마다 상자를 따로 그리는 기능(아이템 획득 등)은 첫 상자를 캔버스로 쓴다(마지막 줄로 덮어쓰면 캔버스가 튄다)
			if (!pixelEditBoxSeen) {
				pixelEditBox[0] = bx;
				pixelEditBox[1] = by;
				pixelEditBox[2] = bw;
				pixelEditBox[3] = bh;
				pixelEditBoxSeen = true;
			}
			if (pixelEditingArt != null) {
				pixelEditingArt.draw(context, bx, by, bw, bh, 1f);
			}
			return;
		}
		// 49-63차(2-1): 모양은 [UI] 설정 [HUD 배경] 하나로 정했다. 49-156차: 기능마다 따로 고를 수 있다
		// ([HUD 배경 따름]이면 예전처럼 UI 설정을 따른다). 없음이면 배경도 윤곽선도 안 그린다.
		int shape = hudShapeNow();
		if (shape == HUD_BOX_NONE || alpha <= 0.01f) {
			return;
		}
		if (shape == HUD_BOX_CREAM) {
			if (hudBg.get()) {
				creamBox(context, bx, by, bw, bh, alpha);
			}
			return;
		}
		if (shape == HUD_BOX_MIDNIGHT || shape == HUD_BOX_NEON) {
			if (hudBg.get()) {
				skinHudBox(context, bx, by, bw, bh, alpha, shape == HUD_BOX_NEON, (getId().hashCode() & 1) == 1);
			}
			return;   // 49-279차
		}
		if (shape == HUD_BOX_PIXEL) {
			// 49-170차: 직접 그린 배경(9분할 반복). 그림이 없으면 둥근 상자로.
			kr.lunaslight.mod.util.PixelArt art = hudPixelArt();
			if (art != null) {
				art.draw(context, bx, by, bw, bh, alpha);
				return;
			}
			shape = HUD_BOX_ROUND;
		}
		int radius = shape == HUD_BOX_SQUARE ? 0 : 4;
		// 49-179차: 기능이 윤곽선 색을 잠깐 바꿔 줄 수 있다(듣고 있는 노래의 플랫폼 색). 배경도 윤곽선도 꺼져 있으면 안 그린다.
		int override = hudOutlineOverride;
		boolean outline = hudBgOutline.get() || (override != 0 && hudBg.get());
		if (hudBg.get()) {
			// 49-180차: 윤곽선이 있으면 배경도 윤곽선과 같은 계단 모서리로(부드러운 모서리는 윤곽선 밖으로 삐져나옴)
			if (outline) {
				kr.lunaslight.mod.gui.LunaDraw.roundRectPixel(context, bx, by, bw, bh, radius, mulAlpha(hudBgColor.getArgb(), alpha));
			} else {
				kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx, by, bw, bh, radius, mulAlpha(hudBgColor.getArgb(), alpha));
			}
		}
		if (outline) {
			int oc = override != 0 ? override : hudBgOutlineColor.getArgb();
			kr.lunaslight.mod.gui.LunaDraw.roundRectOutline(context, bx, by, bw, bh, radius, mulAlpha(oc, alpha));
		}
	}

	/**
	 * 49-184차(사용자: "노래 표시에도 그림자"): 이 기능의 배경 상자 아래 부드러운 그림자. 배경이 꺼져 있거나 모양이
	 * [없음]이면 안 그린다. 둥글기는 상자와 같게(각진 모양이면 0). drawHudBox 바로 앞에 부른다.
	 */
	protected void drawHudBoxShadow(GuiGraphicsExtractor context, int x, int y, int w, int h) {
		if (hudBg == null || !hudBg.get() || this == pixelEditing) {
			return;
		}
		int shape = hudShapeNow();
		if (shape == HUD_BOX_NONE || shape == HUD_BOX_CREAM || shape == HUD_BOX_MIDNIGHT || shape == HUD_BOX_NEON) {
			return;   // 크림 상자는 제 그림자를 같이 그린다(49-279차: 미드나잇, 네온도 제 빛/테두리)
		}
		kr.lunaslight.mod.gui.LunaDraw.shadow(context, x, y, w, h, shape == HUD_BOX_SQUARE ? 0 : 4);
	}

	// ==================== 49-257차: 크림 HUD ====================
	// 사용자: "모든 HUD 띄워서 하는 것들, 크림 UI가 있으면 크림 버전도 따로 착용할 수 있게". 크림 화면 스킨을 가진(런처 상점) 사람만
	// [UI] > [HUD 배경] 모양이나 기능별 [배경 모양]에서 [크림]을 고를 수 있다(안 가졌으면 둥근 상자로 보인다). 크림 상자는 밝은 베이지라
	// 그 기능이 그리는 동안(renderHud) LunaCompat.hudCream을 켜서 HUD 글자를 짙은 갈색(밝은 색 코드는 짙은 짝)으로, 그림자 없이 그린다.

	/** 지금 쓸 상자 모양(기능별 설정 → UI 설정, 크림을 안 가졌으면 둥근). */
	private int hudShapeNow() {
		int shape = HUD_BOX_SHAPE;
		if (hudBgShape != null) {
			switch (hudBgShape.get()) {
				case ROUND -> shape = HUD_BOX_ROUND;
				case SQUARE -> shape = HUD_BOX_SQUARE;
				case NONE -> shape = HUD_BOX_NONE;
				case PIXEL -> shape = HUD_BOX_PIXEL;
				case CREAM -> shape = HUD_BOX_CREAM;
				case MIDNIGHT -> shape = HUD_BOX_MIDNIGHT;
				case NEON -> shape = HUD_BOX_NEON;
				default -> {
				}
			}
		}
		if (shape == HUD_BOX_CREAM && !creamOwned()) {
			shape = HUD_BOX_ROUND;
		}
		if (shape == HUD_BOX_MIDNIGHT && !skinOwned(kr.lunaslight.mod.util.LunaTheme.Skin.MIDNIGHT)
				|| shape == HUD_BOX_NEON && !skinOwned(kr.lunaslight.mod.util.LunaTheme.Skin.NEON)) {
			shape = HUD_BOX_ROUND;   // 49-279차
		}
		return shape;
	}

	/** 49-289차: UI 테마가 바뀌었을 때 - 이 기능이 테마 상자(크림/미드나잇/네온)를 골랐으면 [HUD 배경 따름]으로. 바꿨으면 true. */
	public boolean hudShapeFollowTheme() {
		if (hudBgShape == null) {
			return false;
		}
		HudShape s = hudBgShape.get();
		if (s == HudShape.CREAM || s == HudShape.MIDNIGHT || s == HudShape.NEON) {
			hudBgShape.setValue(HudShape.FOLLOW);
			return true;
		}
		return false;
	}

	/** 49-279차: 그 화면 스킨을 가졌나(런처 상점). */
	public static boolean skinOwned(kr.lunaslight.mod.util.LunaTheme.Skin s) {
		try {
			return kr.lunaslight.mod.util.LunaTheme.skinOwned(s);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 크림 화면 스킨을 가졌나(런처 상점). */
	public static boolean creamOwned() {
		try {
			return kr.lunaslight.mod.util.LunaTheme.skinOwned(kr.lunaslight.mod.util.LunaTheme.Skin.CREAM);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 이 기능이 지금 크림 상자로 그려지나(배경 켜짐 + 모양 크림 + 가짐). */
	public boolean usesCreamHud() {
		return hudBg != null && hudBg.get() && this != pixelEditing && hudShapeNow() == HUD_BOX_CREAM;
	}

	/** 크림 상자: 갈색 기운 그림자 + 베이지 테두리 + 크림 속(설정 화면 크림 카드와 같은 색). */
	private static void creamBox(GuiGraphicsExtractor context, int bx, int by, int bw, int bh, float alpha) {
		int r = 5;
		kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx - 1, by + 1, bw + 2, bh + 2, r + 1, mulAlpha(0x145A4630, alpha));
		kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx, by + 1, bw, bh, r, mulAlpha(0x2E5A4630, alpha));
		kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx, by, bw, bh, r, mulAlpha(0xF5DCC8A4, alpha));
		kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx + 1, by + 1, bw - 2, bh - 2, r - 1, mulAlpha(0xF2FFFAF0, alpha));
	}

	/**
	 * 49-279차: 미드나잇 HUD = 짙은 남보라 90% + 보라 테두리 40%(둥근 5), 네온 HUD = 검정 85% + 시안(기능마다 하나 걸러 분홍)
	 * 1px 테두리 + 빛(각진 1). 글자는 밝은 색 그대로.
	 */
	private static void skinHudBox(GuiGraphicsExtractor context, int bx, int by, int bw, int bh, float alpha, boolean neon, boolean pink) {
		if (!neon) {
			kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx, by + 1, bw, bh, 5, mulAlpha(0x26000000, alpha));
			kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx, by, bw, bh, 5, mulAlpha(0x669670FF, alpha));
			kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx + 1, by + 1, bw - 2, bh - 2, 4, mulAlpha(0xE6100C22, alpha));
			return;
		}
		int rgb = pink ? 0xFF3CC8 : 0x20F0FF;
		for (int i = 3; i >= 1; i--) {
			float k = 1f - (i - 0.5f) / 3f;
			kr.lunaslight.mod.gui.LunaDraw.roundRectOutline(context, bx - i, by - i, bw + 2 * i, bh + 2 * i, 1 + i,
				mulAlpha((Math.round(0x50 * k * k) << 24) | rgb, alpha));
		}
		kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx, by, bw, bh, 1, mulAlpha(0xFF000000 | rgb, alpha));
		kr.lunaslight.mod.gui.LunaDraw.roundRect(context, bx + 1, by + 1, bw - 2, bh - 2, 0, mulAlpha(0xD9000000, alpha));
	}

	/** 49-179차: 0이 아니면 다음 drawHudBox의 윤곽선을 이 색으로(모양과 둥글기는 이 기능의 배경 설정 그대로). */
	protected int hudOutlineOverride;

	private static int mulAlpha(int argb, float alpha) {
		if (alpha >= 0.999f) {
			return argb;
		}
		int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	/**
	 * 49-156차: 자기 크기를 직접 정하는 상자(여백 없이 이 사각형 그대로)를 이 기능의 [배경] 설정대로 그린다.
	 * alpha는 흐리게(꺼진 상태, 사라지는 중) 그릴 때 곱하는 값.
	 */
	protected void drawHudBox(GuiGraphicsExtractor context, int x, int y, int w, int h) {
		hudBox(context, x, y, w, h, 1f);
	}

	protected void drawHudBox(GuiGraphicsExtractor context, int x, int y, int w, int h, float alpha) {
		hudBox(context, x, y, w, h, alpha);
	}

	// 49-63차(2-1): HUD 배경 상자의 모양. HudBackgroundModule([UI] 설정)이 갈아 끼운다.
	public static final int HUD_BOX_PIXEL = 3;   // 49-170차
	public static final int HUD_BOX_ROUND = 0;
	public static final int HUD_BOX_SQUARE = 1;
	public static final int HUD_BOX_NONE = 2;
	public static final int HUD_BOX_CREAM = 4;   // 49-257차
	public static final int HUD_BOX_MIDNIGHT = 5, HUD_BOX_NEON = 6;   // 49-279차
	/** 지금 쓰는 모양. 매 프레임 여러 번 읽히는 값이라 필드 하나로 둔다(설정 조회 비용 0). */
	public static volatile int HUD_BOX_SHAPE = HUD_BOX_ROUND;

	// 49-141차: 설정 화면 기능 상자 가운데 줄에 "지금 값"(좌표 120 64 -881, FPS 241 …)을 보여 주려고
	// HUD가 마지막으로 그린 글자를 기억한다. 미리보기·편집기 샘플(예시 데이터)은 기억하지 않는다.
	private volatile String lastHudText;
	private volatile long lastHudAt;

	private void noteHud(String text) {
		if (preview || text == null) {
			return;
		}
		lastHudText = text;
		lastHudAt = System.currentTimeMillis();
	}

	/** 켜져 있고 HUD가 최근 2초 안에 그린 글자(색 코드 뺀 것). 없으면 null. */
	public String liveValue() {
		String t = lastHudText;
		if (t == null || !isEnabled() || System.currentTimeMillis() - lastHudAt > 2000L) {
			return null;
		}
		return t.replaceAll("\u00A7.", "").trim();
	}

	/** 49-34차: 여러 줄을 상자 하나에(좌표/바이옴처럼 위아래로 쌓는 HUD). 줄 간격 10px. */
	protected void drawHudLines(GuiGraphicsExtractor context, java.util.List<String> lines, int x, int y, int color) {
		if (lines == null || lines.isEmpty()) {
			return;
		}
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, kr.lunaslight.mod.util.LunaCompat.getTextWidth(client.font, l));
		}
		float top = kr.lunaslight.mod.util.LunaCompat.textBandTop();
		float bottom = kr.lunaslight.mod.util.LunaCompat.textBandBottom();
		int by = y + Math.round(top) - 3;
		int bh = Math.round(bottom - top) + 6 + (lines.size() - 1) * hudLineH();
		hudBox(context, x - 4, by, w + 8, bh);
		noteHud(lines.get(0));
		for (int i = 0; i < lines.size(); i++) {
			kr.lunaslight.mod.util.LunaCompat.drawHudText(context, client.font, lines.get(i), x, y + i * hudLineH(), color);
		}
	}

	/**
	 * 여러 줄 HUD의 줄 간격. 49-80차(5-2): 갈무리 9·11을 고르면 잉크가 9·11px라 10px 간격으로는 줄이 붙는다 →
	 * 잉크 높이 + 2로 벌린다(7이면 예전 그대로 10). 상수처럼 쓰던 자리는 전부 이 메서드로.
	 */
	protected static int hudLineH() {
		return Math.max(10, Math.round(kr.lunaslight.mod.util.LunaCompat.textBandBottom()) + 2);
	}

	/** 여러 줄 HUD가 차지하는 폭/높이(위치 계산용). */
	protected int hudLinesWidth(java.util.List<String> lines) {
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, kr.lunaslight.mod.util.LunaCompat.getTextWidth(client.font, l));
		}
		return w;
	}

	protected int hudLinesHeight(java.util.List<String> lines) {
		return client.font.lineHeight + (Math.max(1, lines.size()) - 1) * hudLineH();
	}

	/** 배경 스타일 + 텍스트 한 줄을 그림(단일 줄 HUD 모듈 공용). 상자는 글리프 실측 밴드 기준 균형 패딩. */
	protected void drawHudLine(GuiGraphicsExtractor context, String text, int x, int y, int color) {
		int w = kr.lunaslight.mod.util.LunaCompat.getTextWidth(client.font, text);
		// 49-13차: 폰트 모드별 실측 밴드 위아래 3px - 어떤 폰트를 골라도 글자가 상자 정중앙에 옴.
		float top = kr.lunaslight.mod.util.LunaCompat.textBandTop();
		float bottom = kr.lunaslight.mod.util.LunaCompat.textBandBottom();
		int by = y + Math.round(top) - 3;
		int bh = Math.round(bottom - top) + 6;
		hudBox(context, x - 4, by, w + 8, bh);
		noteHud(text);
		kr.lunaslight.mod.util.LunaCompat.drawHudText(context, client.font, text, x, y, color);
	}

	/**
	 * 이 기능이 실제로 동작하는 마인크래프트 버전 범위를 선언합니다(둘 다 포함하는 범위).
	 * 부르지 않으면(기본값) 모든 버전에서 사용 가능한 걸로 취급합니다 - 최근에 추가된
	 * 마인크래프트/Fabric API에 기대는 기능이라 구버전에서는 아예 못 켜야 할 때만 쓰세요.
	 * 예: {@code supportedVersions("1.20.5", null)}는 1.20.5 이상에서만, null 대신 상한도
	 * 넣으면 그 사이에서만. 페더 클라이언트처럼 "이 버전에서는 지원 안 함" 잠금 표시가 여기서
	 * 나옵니다(LunaClientScreen, LunaVersion 참고).
	 */
	protected Module supportedVersions(String minVersionInclusive, String maxVersionInclusive) {
		this.minVersion = minVersionInclusive;
		this.maxVersion = maxVersionInclusive;
		return this;
	}

	// ==================== 49-8차: 외부 모드 의존성 ====================
	// "필요한 모드가 있어야 기능이 켜지고, 없으면 설치하라고 뜨게" - requiresMod()를 부른 모듈은
	// 그 모드가 로드돼 있지 않으면 카드에 "<이름> 모드 설치 필요"가 뜨고 켤 수 없음.
	private String requiredModId;
	private String requiredModName;

	protected Module requiresMod(String modId, String displayName) {
		this.requiredModId = modId;
		this.requiredModName = displayName;
		return this;
	}

	public boolean isModDependencyMet() {
		if (requiredModId == null) {
			return true;
		}
		try {
			return net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(requiredModId);
		} catch (Throwable ignored) {
			return true; // 판별 실패 시 막지 않음
		}
	}

	/**
	 * 지금 환경에서 이 기능을 켤 수 있는지(마크 버전 범위 + 외부 모드 의존성 모두 충족).
	 * 이름은 기존 UI 연동(카드 잠금/토글 차단) 호환을 위해 유지.
	 */
	public boolean isVersionSupported() {
		return LunaVersion.isWithin(minVersion, maxVersion) && isModDependencyMet();
	}

	/** GUI에 "1.20.5+ 필요"/"OO 모드 설치 필요" 안내를 보여줄 때 씀(뒤에 " 필요"가 붙음). */
	public String getVersionRequirementLabel() {
		if (!isModDependencyMet()) {
			return requiredModName + " 모드 설치";
		}
		if (minVersion == null && maxVersion == null) {
			return null;
		}
		if (maxVersion == null) {
			return minVersion + "+";
		}
		if (minVersion == null) {
			return "~" + maxVersion;
		}
		return minVersion + " ~ " + maxVersion;
	}

	/** ModuleManager 전용 - GUI 토글이나 config 로드가 아닌 코드 내부에서 강제로 상태를 맞출 때. */
	public void setEnabledSilently(boolean value) {
		this.enabled = (alwaysOn || value) && isVersionSupported();
	}

	public final void setEnabled(boolean value) {
		boolean target = (alwaysOn || value) && isVersionSupported();
		if (this.enabled == target) {
			return;
		}
		this.enabled = target;
		if (target) {
			onEnable();
		} else {
			onDisable();
		}
	}

	/**
	 * 49-156차: 설정 파일에서 "켜짐"으로 불러온 기능은 setEnabledSilently로 켜져서 onEnable이 한 번도 안 불렸다 -
	 * onEnable에서 준비하던 기능(탭리스트, 리소스팩 기억, 귓속말 알림 …)이 게임을 다시 켜면 켜진 것처럼 보이면서
	 * 실제로는 안 돌았다. 첫 틱에 켜진 기능마다 한 번 불러 준다(LunaClientMod).
	 */
	public final void resumeAfterLoad() {
		if (!enabled) {
			return;
		}
		try {
			onEnable();
		} catch (Throwable t) {
			kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 켜 둔 기능 다시 준비 실패: " + id, t);
		}
	}

	public final void toggle() {
		setEnabled(!enabled);
	}

	// ---- 모듈이 오버라이드하는 훅들 (전부 선택적) ----

	/** 켜질 때 1회. */
	protected void onEnable() {
	}

	/** 꺼질 때 1회. */
	protected void onDisable() {
	}

	/** enabled인 동안 매 클라이언트 틱마다 (20tps). */
	public void onTick() {
	}

	/** enabled인 동안 매 프레임 HUD 오버레이 위에. */
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
	}

	/**
	 * 49-23차: HUD 그리기 순서(클수록 나중에 = 위에 덮임). 기본 0. 엔티티 정보처럼 다른 HUD(나침반)
	 * 위에 덮여야 하는 모듈이 양수를 돌려준다. ModuleManager가 등록 순서 대신 이 값으로 정렬해 그린다.
	 */
	public int renderOrder() {
		return 0;
	}

	/**
	 * 49-22차: F3(디버그 화면)이 켜져 있을 때도 그리는 모듈인지. 기본 false(F3에서는 우리 HUD 전부
	 * 숨김 - 49-21차 요청). F3 꾸미기 모듈만 true.
	 */
	public boolean rendersOnDebugHud() {
		return false;
	}

	// ==================== 49-22차: 설정 화면 미리보기 ====================
	// 사용자: "미리보기에서 잘 안 보이는 기능들 전부 고쳐". 예전엔 위치 설정이 있는 모듈만 onHudRender를
	// 미리보기 칸으로 옮겨 그렸고, 게임 상태(조준 대상/체력/효과/획득 아이템…)에 기대는 모듈은 아무것도
	// 안 보였다. 이제 모듈이 isPreview()로 "미리보기 중"을 알 수 있어 예시 데이터를 그리고, 위치 설정이
	// 없는 모듈도 hasPreview()/renderPreview()를 오버라이드해 칸 안에 예시를 그린다.
	private boolean preview;
	private boolean previewBoxed;
	private int previewX, previewY, previewW, previewH;
	private int previewLastW, previewLastH; // 미리보기가 직전 프레임에 그린 실제 크기

	/** 지금 예시 데이터로 그리는 중인지(설정 화면 미리보기 칸 또는 HUD 편집기의 샘플). */
	protected boolean isPreview() {
		return preview;
	}

	/**
	 * 49-24차: 설정 화면의 미리보기 칸(previewX..H 안에 가운데 맞춰 그려야 함)인지. false면 HUD 편집기 샘플 -
	 * 예시 데이터는 쓰되 자기 위치 설정 자리에 그려야 한다(편집기가 그 자리를 잡아 끌게).
	 */
	protected boolean isPreviewBoxed() {
		return preview && previewBoxed;
	}

	/** 위치 설정(있으면). */
	public kr.lunaslight.mod.module.setting.PositionSetting positionSetting() {
		for (Setting<?> s : settings) {
			if (s instanceof kr.lunaslight.mod.module.setting.PositionSetting p) {
				return p;
			}
		}
		return null;
	}

	/** HUD 편집기에서 정한 크기 배율(위치 설정이 없으면 1). */
	/** 49-303차: 마지막으로 그린 HUD 자리(배율 포함)가 사각형 r(x0, y0, x1, y1)과 겹치나. 자리를 모르면 false. */
	public boolean hudOverlaps(int[] r) {
		try {
			kr.lunaslight.mod.module.setting.PositionSetting ps = positionSetting();
			if (ps == null || r == null) {
				return false;
			}
			kr.lunaslight.mod.module.setting.HudPosition p = ps.get();
			int w = p.getLastWidth(), h = p.getLastHeight();
			if (w <= 0 || h <= 0) {
				return false;
			}
			float s = hudScale();
			return kr.lunaslight.mod.util.TabArea.overlaps(r, p.getLastX(), p.getLastY(), Math.round(w * s), Math.round(h * s));
		} catch (Throwable t) {
			return false;
		}
	}

	public float hudScale() {
		kr.lunaslight.mod.module.setting.PositionSetting ps = positionSetting();
		return ps == null ? 1f : Math.max(0.25f, Math.min(4f, ps.get().scale));
	}

	/**
	 * 49-24차: 배율이 적용된 HUD 그리기(ModuleManager·편집기가 onHudRender 대신 부름). 직전 프레임에 기록된
	 * 요소 왼쪽 위(lastX/lastY)를 기준으로 행렬 배율을 걸어 그린다 - resolveX/Y가 이미 배율만큼 커진 폭으로
	 * 앵커를 계산하므로 오른쪽/아래 앵커도 제자리에 붙는다. 변환을 못 쓰는 버전은 그냥 1배.
	 */
	public void renderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		// 49-161차: HUD(와 그 미리보기, 편집기 샘플)는 설정 화면의 "더 둥글게"를 받지 않는다 - 게임 HUD와 같은 모양
		kr.lunaslight.mod.gui.LunaDraw.beginHud();
		boolean prevCream = kr.lunaslight.mod.util.LunaCompat.hudCream;
		kr.lunaslight.mod.util.LunaCompat.hudCream = usesCreamHud();   // 49-257차: 크림 상자면 글자를 짙게
		try {
			renderHud0(context, tickCounter);
		} finally {
			kr.lunaslight.mod.util.LunaCompat.hudCream = prevCream;
			kr.lunaslight.mod.gui.LunaDraw.endHud();
		}
	}

	private void renderHud0(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		float s = hudScale();
		kr.lunaslight.mod.module.setting.PositionSetting ps = positionSetting();
		if (ps == null || Math.abs(s - 1f) < 0.001f || ps.get().getLastWidth() <= 0
				|| !kr.lunaslight.mod.util.LunaCompat.guiTransformSupported(context)) {
			onHudRender(context, tickCounter);
			return;
		}
		kr.lunaslight.mod.module.setting.HudPosition p = ps.get();
		int ox = p.getLastX();
		int oy = p.getLastY();
		kr.lunaslight.mod.util.LunaCompat.guiPush(context);
		kr.lunaslight.mod.util.LunaCompat.guiTranslate(context, ox, oy);
		kr.lunaslight.mod.util.LunaCompat.guiScale(context, s, s);
		kr.lunaslight.mod.util.LunaCompat.guiTranslate(context, -ox, -oy);
		try {
			onHudRender(context, tickCounter);
		} finally {
			kr.lunaslight.mod.util.LunaCompat.guiPop(context);
		}
	}

	/**
	 * 49-24차: HUD 편집기용 샘플 - 예시 데이터로 자기 위치에 그린다(조준 대상/효과/획득 아이템이 없어도 실물 크기로
	 * 보이게). 위치가 끝내 계산되지 않은 모듈(예시 없음)은 이름을 HUD 한 줄처럼 그 자리에 그려 상자 크기를 만든다.
	 */
	public void renderEditorSample(GuiGraphicsExtractor context, int screenW, int screenH) {
		// 49-161차: HUD(와 그 미리보기, 편집기 샘플)는 설정 화면의 "더 둥글게"를 받지 않는다 - 게임 HUD와 같은 모양
		kr.lunaslight.mod.gui.LunaDraw.beginHud();
		try {
			renderEditorSample0(context, screenW, screenH);
		} finally {
			kr.lunaslight.mod.gui.LunaDraw.endHud();
		}
	}

	private void renderEditorSample0(GuiGraphicsExtractor context, int screenW, int screenH) {
		kr.lunaslight.mod.module.setting.PositionSetting ps = positionSetting();
		long before = System.nanoTime();
		preview = true;
		previewBoxed = false;
		previewX = 0;
		previewY = 0;
		previewW = screenW;
		previewH = screenH;
		try {
			renderHud(context, null);
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("editorSample:" + id, t);
		} finally {
			preview = false;
		}
		if (ps != null && ps.get().getLastResolveNanos() < before) {
			String name = getDisplayName();
			int w = kr.lunaslight.mod.util.LunaCompat.getTextWidth(client.font, name);
			int x = ps.get().resolveX(screenW, w);
			int y = ps.get().resolveY(screenH, client.font.lineHeight);
			drawHudLine(context, name, x, y, 0xFFB8C0C8);
		}
	}

	protected int previewX() {
		return previewX;
	}

	protected int previewY() {
		return previewY;
	}

	protected int previewW() {
		return previewW;
	}

	protected int previewH() {
		return previewH;
	}

	/**
	 * 49-32차: 설정 페이지 맨 아래에 붙일 안내표(왼쪽 = 표기, 오른쪽 = 뜻).
	 * 스크린샷 파일 이름 표기처럼 "설정 옆에 다 못 적는 목록"을 보여 줄 때 쓴다. 없으면 null.
	 */
	public String[][] helpTable() {
		return null;
	}

	/** 안내표 제목. helpTable()이 있을 때만 쓰인다. */
	public String helpTableTitle() {
		return "쓸 수 있는 표기";
	}

	/**
	 * 49-32차: 기능 목록(카테고리 격자)에 안 띄울지. 켜고 끄는 "인게임 기능"이 아니라
	 * 클라이언트 자체 설정인 것(글꼴 등)에만 쓴다 - 설정 화면 아래 버튼으로만 연다.
	 */
	public boolean hiddenInList() {
		return false;
	}

	/** 설정 화면에 미리보기 칸을 보여줄지. 기본: 위치 설정이 있으면. */
	public boolean hasPreview() {
		for (Setting<?> s : settings) {
			if (s instanceof kr.lunaslight.mod.module.setting.PositionSetting) {
				return true;
			}
		}
		return false;
	}

	/** 미리보기 칸(x,y,w,h) 안에 그림. 기본: 위치 설정을 칸 가운데로 잠깐 옮겨 onHudRender를 부름. */
	public void renderPreview(GuiGraphicsExtractor context, int x, int y, int w, int h) {
		// 49-161차: HUD(와 그 미리보기, 편집기 샘플)는 설정 화면의 "더 둥글게"를 받지 않는다 - 게임 HUD와 같은 모양
		kr.lunaslight.mod.gui.LunaDraw.beginHud();
		try {
			renderPreview0(context, x, y, w, h);
		} finally {
			kr.lunaslight.mod.gui.LunaDraw.endHud();
		}
	}

	private void renderPreview0(GuiGraphicsExtractor context, int x, int y, int w, int h) {
		preview = true;
		previewBoxed = true;
		previewX = x;
		previewY = y;
		previewW = w;
		previewH = h;
		kr.lunaslight.mod.module.setting.PositionSetting ps = null;
		for (Setting<?> s : settings) {
			if (s instanceof kr.lunaslight.mod.module.setting.PositionSetting p) {
				ps = p;
				break;
			}
		}
		try {
			if (ps == null) {
				onHudRender(context, null);
				return;
			}
			kr.lunaslight.mod.module.setting.HudPosition original = ps.get();
			// 49-23차: 꺼져 있거나 월드 밖이라 실제 HUD가 안 그려지는 모듈은 original의 마지막 크기가 0이라
			// 항상 60×12로 가정했고 → 긴 줄이 오른쪽으로 잘렸음. 미리보기 자체가 그린 크기(임시 위치 객체에
			// 기록됨)를 다음 프레임에 쓴다.
			int lw = previewLastW > 0 ? previewLastW : (original.getLastWidth() > 0 ? original.getLastWidth() : 60);
			int lh = previewLastH > 0 ? previewLastH : (original.getLastHeight() > 0 ? original.getLastHeight() : 12);
			// 49-23차: "좌표같이 긴 거는 짤린다" - 칸보다 크면 칸 안에 들어가도록 통째로 축소해서 그림
			// (DrawContext 행렬 배율, 미리보기 좌표계도 같이 축소). 변환을 못 쓰는 버전은 예전처럼 그대로.
			float fit = 1f;
			// 49-47차: 여백 6 → 12. 6px였을 땐 긴 줄이 칸 가장자리(scissor)에 닿아 글자 끝이 잘려 보였다.
			int margin = 12;
			if ((lw > w - margin || lh > h - margin) && kr.lunaslight.mod.util.LunaCompat.guiTransformSupported(context)) {
				fit = Math.min((w - margin) / (float) lw, (h - margin) / (float) lh);
				fit = Math.max(0.2f, Math.min(1f, fit));   // 49-47차: 아주 넓은 요소도 잘리느니 더 줄인다
			}
			boolean scaled = fit < 0.999f;
			int targetX, targetY;
			if (scaled) {
				kr.lunaslight.mod.util.LunaCompat.guiPush(context);
				kr.lunaslight.mod.util.LunaCompat.guiTranslate(context, x + w / 2f, y + h / 2f);
				kr.lunaslight.mod.util.LunaCompat.guiScale(context, fit, fit);
				previewW = Math.round(w / fit);
				previewH = Math.round(h / fit);
				previewX = -previewW / 2;
				previewY = -previewH / 2;
				targetX = -lw / 2;
				targetY = -lh / 2;
			} else {
				targetX = x + (w - lw) / 2;
				targetY = y + (h - lh) / 2;
			}
			kr.lunaslight.mod.module.setting.HudPosition temp = kr.lunaslight.mod.module.setting.HudPosition.of(
					kr.lunaslight.mod.module.setting.HudPosition.Anchor.TOP_LEFT, targetX, targetY);
			long before = System.nanoTime();
			try {
				ps.setValue(temp);
				onHudRender(context, null);
				// 49-26차: 아무것도 안 그려졌으면(조건이 안 맞아 그냥 반환) 빈 칸 대신 안내 한 줄
				if (temp.getLastResolveNanos() < before) {
					String msg = previewEmptyMessage();
					if (msg != null && !msg.isEmpty()) {
						int mw = kr.lunaslight.mod.util.LunaCompat.getTextWidth(client.font, msg);
						kr.lunaslight.mod.util.LunaCompat.drawHudText(context, client.font, msg,
								previewCenterX() - mw / 2, previewCenterY() - 4, 0xFF9AA3AD);
					}
				}
			} finally {
				ps.setValue(original);
				if (temp.getLastWidth() > 0) {
					previewLastW = temp.getLastWidth();
					previewLastH = temp.getLastHeight();
				}
				if (scaled) {
					kr.lunaslight.mod.util.LunaCompat.guiPop(context);
				}
			}
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("preview:" + id, t);
		} finally {
			preview = false;
		}
	}

	/** 49-26차: 미리보기에서 아무것도 안 그려질 때 칸 가운데에 띄울 한 줄. */
	protected String previewEmptyMessage() {
		return "";   // 49-34차: 안내 문구 없이 빈 칸으로(사용자: "이딴건 왜 써져있는 거야")
	}

	/** 미리보기 칸의 가운데 x. */
	protected int previewCenterX() {
		return previewX + previewW / 2;
	}

	/** 미리보기 칸의 가운데 y. */
	protected int previewCenterY() {
		return previewY + previewH / 2;
	}

	// 19차: net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext가 1.21.9+ 포팅에서
	// 업스트림 자체에서 통째로 제거됨(Fabric API GitHub 이슈 #4902, 2026-08 기준 미재구현).
	// 이 클래스 하나가 공유 소스에 직접 등장하면 그 타입이 없는 버전은 컴파일이 깨지므로,
	// 파라미터를 Object로 받고 실제 WorldRenderContext 처리는 리플렉션 기반
	// kr.lunaslight.mod.util.LunaCompat을 쓰는 개별 모듈(예: CrosshairOutlineModule)에 맡김.
	// ModuleManager가 WorldRenderEvents 클래스 존재 여부를 먼저 확인하므로, 이 타입 자체가 없는
	// 1.21.11에서는 onWorldRender가 아예 호출되지 않는다(월드 렌더 의존 모듈은 그 버전에서 비활성).
	/** enabled인 동안 매 프레임 3D 월드 위(엔티티 위 체력바, 크로스헤어 아웃라인 등). */
	public void onWorldRender(Object context) {
	}
}
