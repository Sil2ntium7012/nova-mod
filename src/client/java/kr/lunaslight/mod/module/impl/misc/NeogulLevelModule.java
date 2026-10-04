package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-149차(사용자: "너굴마을 경험치 계산기 - 따로 켜는 게 아니라 입력 > 결과 방식"): <b>레벨 계산기</b>.
 * 켜고 끄는 기능이 아니다(늘 켜짐, 스위치 없음). 설정 화면에서 상자를 누르면 {@code NeogulLevelScreen}이 열린다.
 */
public class NeogulLevelModule extends Module {

	public NeogulLevelModule() {
		super("neogul_level_calc", "레벨 계산기", ModuleCategory.SERVER, "시작 레벨 → 끝 레벨 경험치와 비용");
		serverGroup("너굴마을");
		alwaysOn();
	}

	@Override
	public boolean openCustomScreen(Object parent) {
		try {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.NeogulLevelScreen((net.minecraft.client.gui.screen.Screen) parent));
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulLevel:open", t);
			return false;
		}
	}
}
