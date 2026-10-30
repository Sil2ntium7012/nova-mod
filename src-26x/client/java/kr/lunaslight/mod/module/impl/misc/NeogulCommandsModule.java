package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-431차(사용자: "명령어 목록도 볼 수 있으면 좋을 거 같고 명령어 검색 기능이랑"): <b>명령어</b>. 켜고 끄는 기능이 아니다(늘 켜짐).
 * 설정 화면에서 상자를 누르면 {@code NeogulCommandsScreen}이 열린다.
 */
public class NeogulCommandsModule extends Module {

	public NeogulCommandsModule() {
		super("neogul_commands", "너굴 명령어", ModuleCategory.SERVER, "너굴마을 명령어 목록 | 검색");
		serverGroup("너굴마을");
		alwaysOn();
	}

	@Override
	public boolean openCustomScreen(Object parent) {
		try {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.NeogulCommandsScreen((net.minecraft.client.gui.screens.Screen) parent));
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCommands:open", t);
			return false;
		}
	}

}
