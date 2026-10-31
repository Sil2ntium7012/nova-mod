package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-443차(사용자: "던전 몬스터들 정보도 보는 란도 하나 만들어줘"): <b>던전 몬스터</b>. 켜고 끄는 기능이 아니다(늘 켜짐).
 * 설정 화면에서 상자를 누르면 {@code NeogulMobsScreen}이 열린다.
 */
public class NeogulMobsModule extends Module {

	public NeogulMobsModule() {
		super("neogul_mobs", "너굴 던전 몬스터", ModuleCategory.SERVER, "던전별 몬스터 체력 | 공격력 | 경험치 | 전리품");
		serverGroup("너굴마을");
		alwaysOn();
	}

	@Override
	public boolean openCustomScreen(Object parent) {
		try {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.NeogulMobsScreen((net.minecraft.client.gui.screens.Screen) parent));
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulMobs:open", t);
			return false;
		}
	}

}
