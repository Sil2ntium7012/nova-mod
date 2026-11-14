package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.SettingsPage;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-652차(사용자: "ESC 눌렀을 때랑 메인화면을 마크 기본처럼 쓰되 기능창만 마크 느낌으로 하나 추가된 거 고를 수 있게, 모든 버전"):
 * <b>마크 기본 메뉴</b> - 켜면 메인 화면과 ESC 화면을 노바 화면으로 바꾸지 않고 마크 기본 화면을 그대로 쓴다.
 * 대신 마크 버튼 하나(노바 설정 창 열기)만 얹는다(LunaCompat.addMenuButton). 기본은 꺼짐(노바 화면).
 * [UI] 설정 페이지에 스위치로 나온다. 내 서버 잠금과 상관없이 사용자가 고른 값(isEnabledStored)을 따른다.
 */
public class VanillaMenuModule extends Module {

	public VanillaMenuModule() {
		super("vanilla_menu", "마크 기본 메뉴", ModuleCategory.FEATURE,
			"메인 화면과 ESC 화면을 마크 기본으로 쓰고 Nova 설정 버튼만 하나 얹습니다");
		settingsPage(SettingsPage.UI);
		LunaCompat.vanillaMenus = this::isEnabledStored;
	}
}
