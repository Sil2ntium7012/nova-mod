package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.FireOverlayHook;

/**
 * 49-69차(2-3의 앞 절반): <b>불 화면 위치</b> - 불에 탈 때 시야를 덮는 불꽃을 위아래로 옮긴다.
 *
 * <p>사용자 요청 2-3 "방패·불 높이 조절" 중 <b>불</b>. 방패 쪽은 49-75차에 따로 나왔다
 * ({@link ShieldOffsetModule} - "메서드가 거대해서 위험하다"던 49-69차의 걱정은 <b>틀렸다.</b>
 * 안쪽에 끼어들 때만 위험하고, 전체를 감싸는 방식은 안전했다). 카드를 둘로 나눈 건
 * 하나가 두 가지를 하는 것보다 이름이 정직해서다.
 *
 * <p><b>0이면 바닐라와 완전히 같다.</b> 값을 올리면 불꽃이 위로, 내리면 아래로 간다 -
 * 보통은 <b>내려서</b> 앞이 보이게 쓴다. 딱 맞는 숫자는 화면 비율·시야각마다 달라서
 * <b>눈으로 맞추는 슬라이더</b>로 두었다.
 */
public class FireOverlayModule extends Module {

	private final IntSetting offset = register(new IntSetting(
			"offset", "불 높이", "불에 탈 때 화면을 덮는 불꽃을 위아래로 옮깁니다. 0이면 바닐라 그대로입니다.",
			0, -50, 50, 1));

	public FireOverlayModule() {
		super("fire_overlay", "불 화면", ModuleCategory.VIEW, "타는 중 불꽃 높이");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.UI);   // 2-3은 [UI 설정] 항목이다
		offset.onChange(this::apply);
	}

	@Override
	protected void onEnable() {
		apply();
	}

	@Override
	protected void onDisable() {
		FireOverlayHook.offset = 0;
	}

	private void apply() {
		FireOverlayHook.offset = isEnabled() ? offset.get() : 0;
	}
}
