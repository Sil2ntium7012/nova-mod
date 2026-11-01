package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;

/**
 * 49-483차(사용자 사진: 좌표, 서버 주소, 너굴 추천 핫타임, 듣는 노래 HUD - "이런 거 인벤에서 안 보이게"): <b>인벤토리에서 HUD 숨기기</b>.
 * 인벤토리, 상자 같은 창이 열려 있는 동안 노바 HUD를 전부 안 그린다(ModuleManager HUD 그리기에서 본다). 기본 켜짐.
 */
public class InventoryHudHideModule extends Module {
	public static volatile boolean on = true;

	public InventoryHudHideModule() {
		super("inventory_hud_hide", "인벤토리에서 HUD 숨기기", ModuleCategory.HUD, "인벤토리나 상자를 열면 HUD를 안 보이게");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		on = true;
	}

	@Override
	protected void onDisable() {
		on = false;
	}

	@Override
	public void onTick() {
		on = isEnabled();
	}
}
