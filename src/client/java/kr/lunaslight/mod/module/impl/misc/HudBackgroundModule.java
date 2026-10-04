package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.SettingsPage;
import kr.lunaslight.mod.module.setting.EnumSetting;

/**
 * 49-63차(2-1): <b>HUD 배경</b> - 화면에 띄우는 정보 뒤에 깔리는 상자의 모양을 한 번에 정한다.
 *
 * <p>사용자 요청 2-1: "배경 모양: 둥근 / 네모난 / 없음".
 *
 * <p>HUD 상자는 전부 {@code Module.hudBox()} 한 곳을 지나가므로 여기서 값 하나만 바꾸면 좌표·시계·
 * CPS·핑… 전부가 같이 바뀐다. 모듈마다 따로 고르게 하지 않은 이유이기도 하다 - 같은 화면에 둥근 상자와
 * 네모난 상자가 섞이면 그게 제일 안 좋다.
 *
 * <ul>
 *   <li><b>둥근</b>(기본) - 모서리 반지름 4</li>
 *   <li><b>네모난</b> - 반지름 0</li>
 *   <li><b>없음</b> - 배경도 윤곽선도 안 그린다. 글자만 남는다</li>
 * </ul>
 *
 * <p><b>[없음]과 모듈별 [배경] 스위치의 관계</b>: 여기서 없음을 고르면 <b>모듈별 설정보다 세다</b>
 * (전부 안 그린다). 다시 둥근/네모난으로 돌리면 모듈별로 켜 둔 대로 돌아온다 - 모듈 설정을 지우지 않는다.
 *
 * <p>끄고 켜는 개념이 없는 "설정 묶음"이라 {@code alwaysOn}이다(글꼴과 같다).
 */
public class HudBackgroundModule extends Module {

	public enum Shape {
		ROUND("둥근"),
		SQUARE("네모난"),
		CREAM("크림"),   // 49-257차: 크림 UI를 가졌을 때만(없으면 둥근으로 보인다)
		MIDNIGHT("미드나잇"),   // 49-279차: 미드나잇 UI를 가졌을 때만
		NEON("네온"),           // 49-279차: 네온 사이버 UI를 가졌을 때만
		NONE("없음");

		private final String label;

		Shape(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Shape> shape = register(new EnumSetting<>(
			"shape", "모양", "화면 정보 뒤에 깔리는 상자의 모양입니다. 크림은 크림 UI를 가졌을 때 쓸 수 있습니다. 없음이면 글자만 남습니다.",
			Shape.ROUND, Shape.class));
	/** 49-257차: 크림 UI로 처음 들어왔을 때 크림 HUD 안내를 한 번 띄웠는지(숨김, 저장용). */
	private final kr.lunaslight.mod.module.setting.BooleanSetting creamNotice = register(
			new kr.lunaslight.mod.module.setting.BooleanSetting("cream_notice", "크림 HUD 안내", "", false));

	public static HudBackgroundModule instance;

	public HudBackgroundModule() {
		super("hud_background", "HUD 배경", ModuleCategory.HUD, "화면 정보 뒤 상자 모양");
		settingsPage(SettingsPage.UI);   // 사용자 요청 목록 "2. UI 설정"의 2-1
		alwaysOn();
		instance = this;
		creamNotice.hidden();
		shape.onChange(this::apply);
		apply();
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	public void onTick() {
		apply();   // 설정 파일에서 불러온 직후에도 맞도록(값이 같으면 대입 한 번이라 싸다)
	}

	private void apply() {
		switch (shape.get()) {
			case SQUARE -> Module.HUD_BOX_SHAPE = Module.HUD_BOX_SQUARE;
			case NONE -> Module.HUD_BOX_SHAPE = Module.HUD_BOX_NONE;
			case CREAM -> Module.HUD_BOX_SHAPE = Module.HUD_BOX_CREAM;
			case MIDNIGHT -> Module.HUD_BOX_SHAPE = Module.HUD_BOX_MIDNIGHT;
			case NEON -> Module.HUD_BOX_SHAPE = Module.HUD_BOX_NEON;
			default -> Module.HUD_BOX_SHAPE = Module.HUD_BOX_ROUND;
		}
	}

	/** 49-257차: 크림 HUD 안내를 아직 안 띄웠나. */
	public boolean creamNoticePending() {
		return !creamNotice.get();
	}

	public void markCreamNoticeShown() {
		creamNotice.setValue(true);
	}

	public boolean isCream() {
		return shape.get() == Shape.CREAM;
	}

	/** 모든 HUD를 크림 상자로(기능별로 다른 모양을 고른 것은 그대로). */
	public void useCream() {
		shape.setValue(Shape.CREAM);
		apply();
	}
}
