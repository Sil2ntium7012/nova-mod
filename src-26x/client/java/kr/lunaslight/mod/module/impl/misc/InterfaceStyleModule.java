package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-13차: Luna 화면(타이틀/ESC/설정)과 HUD가 쓰는 글꼴을 고르는 기능.
 *
 * 사용자 선택으로 기본값은 **마크 기본 폰트**. 마크 기본 폰트는 숫자·영문이 100% 바닐라 그대로라
 * 게임 화면과 완벽히 붙지만, 한글 글리프가 바닐라에 없어서 유니폰트(네모난 옛날 글꼴)로 떨어진다.
 * 그래서 "마크 + 한글 픽셀" 모드를 같이 넣었다 - 라틴/숫자는 바닐라 비트맵 그대로 쓰고 한글만
 * 갈무리7(8px 픽셀 글꼴, OFL) 서브셋으로 그려서 높이·굵기가 바닐라와 정확히 맞는다
 * (실측: 갈무리7 size 8 한글 밴드 0..7 = 바닐라 대문자와 동일).
 *
 * 폰트 해석은 LunaCompat.uiStyle()이 담당하고, 이 모듈은 설정값을 그쪽에 연결만 한다(모듈을
 * 49-32차: 끄면 마인크래프트 기본 글꼴로 돌아간다(사용자 요청).
 */
public class InterfaceStyleModule extends Module {

	public enum FontStyle {
		MC("마크 폰트"),   // 49-120차(사용자): "마크 기본 폰트" → "마크 폰트"로 이름 변경
		MC_HANGUL("갈무리 폰트"),
		MODERN("모던 (Pretendard)");

		private final String label;

		FontStyle(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/**
	 * 49-80차(5-2 "갈무리 폰트 크기 조절"): 픽셀 글꼴을 갈무리 7 / 9 / 11 중에서 고른다.
	 * 49-84차 정리(사용자: "갈무리7로 그냥 고정하되 크기 조정으로, 9·11은 크기만 커지고 안예뻐 - 9랑 11이 똑같더라"):
	 * <b>보통 / 크게</b> 둘로 줄였다.
	 *   · <b>보통</b> = 갈무리7(8px). 한글만 갈무리, 라틴·숫자는 마크 기본 비트맵 그대로 - 게임 화면과 딱 붙는다.
	 *   · <b>크게</b> = 갈무리9(10px). 한글만 키우면 8px 마크 라틴과 어긋나므로 라틴·숫자도 갈무리 것으로 함께 커진다
	 *     (전체가 고르게 커짐). 갈무리9의 원래 크기(ppem 10)로 그려 선명하다. 예전 "11"은 9와 사실상 같아 없앴다.
	 * 글꼴 파일은 OFL(quiple/galmuri)에서 한글 2350자 + 라틴 + 기호만 추린 서브셋(hangul9.ttf).
	 */
	public enum PixelSize {
		NORMAL("보통", 7),
		LARGE("크게", 9);

		private final String label;
		public final int ink;   // 한글 잉크 높이(px) - LunaCompat.textBandBottom이 세로 정렬에 쓴다

		PixelSize(String label, int ink) {
			this.label = label;
			this.ink = ink;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** 49-148차: 설정 화면 기능 목록 보기. */
	public enum TileView {
		LARGE("큰 박스"),
		SMALL("작은 박스"),
		LIST("상세");

		private final String label;

		TileView(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<FontStyle> font;
	private final EnumSetting<TileView> tileView;
	private final EnumSetting<PixelSize> pixelSize;
	private final EnumSetting<kr.lunaslight.mod.util.LunaTheme.Skin> skin;

	public InterfaceStyleModule() {
		super("interface_style", "글꼴과 테마", ModuleCategory.FEATURE, "Nova 화면 글꼴과 밝기");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.UI);   // 49-56차: 기능이 아니라 [UI] 설정
		// 49-36차: 기본값을 [마크 + 한글 픽셀]로. 마크 기본은 한글 글리프가 없어 유니폰트(가늘고 긴 옛 글꼴)로
		// 떨어지는데, 새 프로필/다른 버전에서 처음 켤 때마다 그게 먼저 보여 "구석기로 돌아갔다"는 인상을 줬다.
		// 1.20 미만은 reference 프로바이더가 없어 LunaCompat이 자동으로 마크 기본으로 강등한다.
		// 49-47차(사용자: "글꼴 순서 배열을 가로 말고 세로로, 기능 이런 거에 넣지 말고 그냥 나열해줘"):
		// 가로 세그먼트 버튼 대신 세로 목록으로 - 이름이 길어 가로로는 글자가 뭉개졌다.
		// 49-120차(사용자: "기본 폰트 마크 폰트로 해주라"): 기본값을 갈무리 → 마크 폰트로.
		// 49-151차(사용자: "폰트 관련 설정 다 기본으로 되돌려"): 저장 이름에 _v2를 붙여 예전에 바꿔 둔 값이 무시되고
		// 기본값(마크 폰트 / 보통 / 리소스팩 글꼴 우선 켬)으로 새로 시작한다.
		font = register(new EnumSetting<>("font_v2", "글꼴", "마크, 갈무리, 모던 중에서 고릅니다.",
			FontStyle.MC, FontStyle.class).vertical());
		// 49-80차(5-2) → 49-84차: 갈무리를 골랐을 때만 뜻이 있는 설정 - 다른 글꼴이면 어둡게 잠근다(disabledWhen)
		pixelSize = register(new EnumSetting<>("pixel_size_v2", "글씨 크기",
			"갈무리 글꼴 크기입니다. '크게'는 영문/숫자도 갈무리로 함께 커집니다.", PixelSize.NORMAL, PixelSize.class).vertical());
		pixelSize.disabledWhen(() -> font.get() != FontStyle.MC_HANGUL);
		// 49-34차(사용자: "루나 글꼴 키고 끄는 기능은 왜 있는 거야 그냥 고르게만 하면 되지"):
		// 켜기/끄기 없이 글꼴만 고른다. 마크 기본 글꼴은 목록의 [마크 기본]으로 고르면 된다.
		// 49-47차의 [리소스팩 글꼴 우선] 설정은 49-168차(사용자: "그 설정 없애자, 마크 기본 폰트로 하면 알아서 되니까")에 뺐다.
		// 글꼴 리소스팩은 [마크 기본]을 고르면 그대로 보인다(LunaCompat.fontMode는 이제 고른 글꼴만 따른다).
		// 49-76차(6-15, 사용자: "메인화면 크기 조절 설정은 다 필요 없음 삭제"): 49-69차 크기 · 49-75차 여백·좌우
		// 바꾸기를 전부 뺐다. 메인 화면은 고정 배치다(LunaTitleScreen 상수).
		// 49-141차의 [화면 테마](다크/라이트)는 49-170차(사용자: "화면 테마 블랙/화이트 없애줘, 어차피 클라이언트에 다 있어")에
		// 뺐다 - 설정 화면은 항상 다크(LunaTheme.light()는 기본 false).
		// 49-148차(사용자: "큰 박스, 작은 박스, 자세히 보기(1열) 3개 설정, 큰 박스 기본")
		// 49-168차(사용자: "그게 일반 설정이 아니라 검색 옆에 있어야지"): 설정 목록에서는 숨기고(저장용) 값은
		// 설정 화면 검색 칸 옆 스위치가 LunaTheme.setTileView로 바꾼다.
		tileView = register(new EnumSetting<>("tile_view", "기능 보기", "설정 화면 기능 목록을 큰 박스, 작은 박스, 한 줄씩 자세히 중에서 고릅니다.",
			TileView.LARGE, TileView.class));
		tileView.hidden();
		// 49-238차: 화면 스킨(기본 = 어두운 Nova 화면, 크림 = 밝은 베이지). 클라이언트 구매 연동은 나중.
		skin = register(new EnumSetting<>("ui_skin", "화면 스킨",
			"Nova 화면 전체의 색 묶음입니다. 크림은 밝은 베이지 판에 갈색 글자로 바뀝니다.",
			kr.lunaslight.mod.util.LunaTheme.Skin.DEFAULT, kr.lunaslight.mod.util.LunaTheme.Skin.class).vertical());
		skin.onChange(() -> kr.lunaslight.mod.util.LunaTheme.setSkin(skin.get()));
		// 49-271차(사용자: "교체는 클라이언트에서 할 거니까 따로 교체는 못 하게"): 게임 안에서는 고를 수 없다 - 런처가 정한 값(ShopUnlocks)만.
		// 지금 무엇을 꼈는지는 [코스메틱] 페이지에서 본다.
		skin.hidden();
		kr.lunaslight.mod.util.LunaTheme.setTileViewSupplier(() -> tileView.get().ordinal());
		kr.lunaslight.mod.util.LunaTheme.setTileViewSetter(tileView::setIndex);
		alwaysOn();
		LunaCompat.setFontModeSupplier(() -> font.get().ordinal());
		LunaCompat.setPixelSizeSupplier(() -> pixelSize.get().ink);
	}

	@Override
	public void onTick() {
		// 설정 파일에서 불러온 값도 맞춘다(같으면 아무 일 없음)
		kr.lunaslight.mod.util.LunaTheme.setSkin(skin.get());
	}

	/** 49-32차: 인게임 기능이 아니라 클라이언트 설정 - 기능 목록에서 빼고 설정 화면 아래 [글꼴] 버튼으로만 연다. */
	@Override
	public boolean hiddenInList() {
		return true;
	}
}
