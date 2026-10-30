package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;

/**
 * 49-353차(사용자: "토끼모자 추가좀 해줘"): <b>모자</b> - 런처 상점에서 산 노바 모자(첫 번째 = 토끼 모자)를 머리에 씌운다.
 * 가방(BagsModule)과 같은 구조지만 머리에 붙는다: 머리 방향과 숙임을 따르고 웅크리면 같이 내려간다(NovaWings.Slot.head).
 * 내 것은 런처 서명(kind "hat")이 맞을 때만, 남의 것은 Supabase nova_player_tiers.cosmetic_hat. 1인칭에선 내 모자를 안 그린다.
 */
public class HatsModule extends Module {

	private static HatsModule instance;

	private final BooleanSetting others = register(new BooleanSetting(
			"others_hats", "다른 사람 모자", "다른 Nova 유저가 쓴 노바 모자도 보여줍니다.", true));

	public HatsModule() {
		super("hats", "모자", ModuleCategory.VIEW, "런처에서 산 노바 모자");
		alwaysOn();
		instance = this;
	}

	public static boolean shown() {
		HatsModule m = instance;
		return m != null && m.isEnabled();
	}

	public static boolean othersShown() {
		HatsModule m = instance;
		return m == null || m.others.get();
	}

	@Override
	public void onWorldRender(Object context) {
		kr.lunaslight.mod.util.NovaWingsRender.draw(context, others.get(), false, kr.lunaslight.mod.util.NovaWings.HAT);
	}

	/** 49-430차(사용자: "기능 설정에 날개 이런 게 왜 있어 - 기본이니까 껐다 켰다 안 했으면"): 늘 켜짐, 기능 목록에 안 띄움. */
	@Override
	public boolean hiddenInList() {
		return true;
	}
}
