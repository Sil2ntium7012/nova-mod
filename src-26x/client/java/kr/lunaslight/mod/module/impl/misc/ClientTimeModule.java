package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.IntSetting;

/**
 * 시간 고정 - 내 화면의 하늘/밝기 시간만 고정(서버 시간은 그대로, 아무것도 전송 안 함).
 * 49-8차부터 매 틱 클라 월드의 하루 시간을 덮어씀(LunaCompat.setClientTimeOfDay).
 * 49-24차: 프리셋(아침/낮/오후/노을/밤/자정/새벽) + 직접 입력. 별도 '오버라이드 사용' 스위치는 삭제(기능 켜짐 = 적용).
 */
public class ClientTimeModule extends Module {

	public enum Preset {
		MORNING("아침", 1000),
		NOON("낮", 6000),
		AFTERNOON("오후", 9000),
		SUNSET("노을", 12500),
		NIGHT("밤", 15000),
		MIDNIGHT("자정", 18000),
		DAWN("새벽", 23000);
		// 49-76차(6-6): 목록의 "직접" 항목은 뺐다 - 아래 [직접 시간 쓰기] 스위치가 그 역할이고,
		// 스위치가 켜지면 이 목록 전체가 흐려진다(disabledWhen). 저장돼 있던 CUSTOM은 기본값(낮)으로 떨어진다.

		private final String label;
		private final int time;

		Preset(String label, int time) {
			this.label = label;
			this.time = time;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static volatile boolean overrideEnabled = false;
	private static volatile int overrideTime = 6000;

	private final EnumSetting<Preset> preset = register(new EnumSetting<>(
			"preset", "시간대", "고정할 시간대입니다.", Preset.NOON, Preset.class));
	// 49-32차(사용자: "직접 시간대 이용을 할 거면 따로 켜는 걸 만들어줘야지 지정을 섞지 말고")
	private final kr.lunaslight.mod.module.setting.BooleanSetting useCustom =
			register(new kr.lunaslight.mod.module.setting.BooleanSetting(
				"use_custom", "직접 시간 쓰기", "켜면 위 시간대 대신 아래에서 고른 시간을 씁니다.", false));

	private final IntSetting customTime = register(new IntSetting(
			"override_time", "직접 시간", "[직접 시간 쓰기]를 켰을 때 적용할 시간(틱)입니다. 6000이 정오입니다.", 6000, 0, 24000, 100).unit("틱"));

	public ClientTimeModule() {
		super("client_time", "시간 고정", ModuleCategory.VIEW, "내 화면의 하늘 시간 고정");
		// 49-76차(6-6): 둘 중 하나만 살아 있게 - 직접 시간을 켜면 시간대가 흐려지고, 끄면 직접 시간이 흐려진다
		preset.disabledWhen(useCustom::get);
		customTime.disabledWhen(() -> !useCustom.get());
	}

	private int effectiveTime() {
		if (useCustom.get()) {
			return customTime.get();
		}
		return preset.get().time;
	}

	@Override
	public void onTick() {
		overrideEnabled = true;
		overrideTime = effectiveTime();
		if (client.level != null) {
			kr.lunaslight.mod.util.LunaCompat.setClientTimeOfDay(client.level, overrideTime);
		}
	}

	@Override
	protected void onDisable() {
		overrideEnabled = false;
	}

	public static boolean isOverrideActive() {
		return overrideEnabled;
	}

	public static int getOverrideTime() {
		return overrideTime;
	}
}
