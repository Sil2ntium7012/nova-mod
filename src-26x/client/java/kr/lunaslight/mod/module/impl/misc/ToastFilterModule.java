package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.ToastHook;

/**
 * 49-54차(1-3): 토스트 끄기 - 화면 오른쪽 위로 튀어나오는 알림 상자를 종류별로 막는다.
 *
 * <p>[시스템 알림]만 <b>기본 꺼짐</b>이다. 여기엔 "세계 저장 실패" · "리소스팩을 못 읽음" 같은
 * 진짜 문제 알림이 섞여 있어서, 기본으로 막아 두면 고장을 조용히 숨기는 꼴이 된다.
 */
public class ToastFilterModule extends Module {

	private final BooleanSetting advancement = register(new BooleanSetting(
			"advancement", "발전 과제", "발전 과제를 달성했다는 알림 상자를 막습니다.", true));
	private final BooleanSetting recipe = register(new BooleanSetting(
			"recipe", "제작법", "새 제작법을 배웠다는 알림 상자를 막습니다.", true));
	private final BooleanSetting tutorial = register(new BooleanSetting(
			"tutorial", "튜토리얼", "\"이동하려면 WASD\" 같은 안내 상자를 막습니다.", true));
	private final BooleanSetting system = register(new BooleanSetting(
			"system", "시스템 알림", "저장 실패/리소스팩 오류 같은 알림까지 막습니다. 문제를 놓칠 수 있습니다.", false));

	public ToastFilterModule() {
		super("toast_filter", "토스트 끄기", ModuleCategory.FEATURE, "오른쪽 위 알림 상자 막기");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);   // 49-56차: 기능이 아니라 [일반] 설정
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	public void onTick() {
		apply();
	}

	@Override
	protected void onDisable() {
		ToastHook.active = false;
	}

	private void apply() {
		ToastHook.active = isEnabled();
		ToastHook.hideAdvancement = advancement.get();
		ToastHook.hideRecipe = recipe.get();
		ToastHook.hideTutorial = tutorial.get();
		ToastHook.hideSystem = system.get();
	}
}
