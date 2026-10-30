package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-431차(사용자: "치장 창도 따로 있으면 좋을 듯, 미리 입어보기랑"): <b>치장</b>. 켜고 끄는 기능이 아니다(늘 켜짐).
 * 설정 화면에서 상자를 누르면 {@code NeogulCosmeticsScreen}이 열린다.
 */
public class NeogulCosmeticsModule extends Module {

	public NeogulCosmeticsModule() {
		super("neogul_cosmetics", "너굴 치장", ModuleCategory.SERVER, "너굴마을 치장 목록 | 미리 입어보기");
		serverGroup("너굴마을");
		alwaysOn();
	}

	@Override
	public boolean openCustomScreen(Object parent) {
		try {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.NeogulCosmeticsScreen((net.minecraft.client.gui.screens.Screen) parent));
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCosmetics:open", t);
			return false;
		}
	}

}
