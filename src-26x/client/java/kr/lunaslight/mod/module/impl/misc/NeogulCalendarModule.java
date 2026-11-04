package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-529차(사용자: "달력도 없고"): <b>너굴 달력</b>을 너굴마을 기능 목록에 따로 둔다(예전엔 너굴 추천 핫타임 설정 안 [달력 열기]에만 있어 못 찾았다).
 * 켜고 끄는 기능이 아니다(늘 켜짐) - 누르면 {@code NeogulCalendarScreen}. 기록은 NeogulCalendar(퀘스트 화면, 추천, 핫타임, 통계).
 */
public class NeogulCalendarModule extends Module {

	public NeogulCalendarModule() {
		super("neogul_calendar", "너굴 달력", ModuleCategory.SERVER, "일퀘 보상 | 추천 | 핫타임 | 접속 시간");
		serverGroup("너굴마을");
		alwaysOn();
		kr.lunaslight.mod.util.NeogulCalendar.init();
	}

	@Override
	public boolean openCustomScreen(Object parent) {
		try {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.NeogulCalendarScreen((net.minecraft.client.gui.screens.Screen) parent));
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCalendar:open", t);
			return false;
		}
	}
}
