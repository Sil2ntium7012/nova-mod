package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;

/**
 * 49-351차(사용자: "토끼가방으로 치장에 추가해줘"): <b>가방</b> - 런처 상점에서 산 노바 가방(첫 번째 = 토끼 가방)을 등에 그린다.
 * 날개(WingsModule)와 같은 구조: 내 것은 런처 서명(kind "bag")이 맞을 때만, 남의 것은 Supabase nova_player_tiers.cosmetic_bag.
 * 모델은 Blockbench 파일(면마다 UV) 그대로 암호화 묶음(util/NovaWings.parseBlockbench).
 */
public class BagsModule extends Module {

	private static BagsModule instance;

	private final BooleanSetting others = register(new BooleanSetting(
			"others_bags", "다른 사람 가방", "다른 Nova 유저가 낀 노바 가방도 보여줍니다.", true));

	public BagsModule() {
		super("bags", "가방", ModuleCategory.VIEW, "런처에서 산 노바 가방");
		defaultEnabled(true);
		instance = this;
	}

	public static boolean shown() {
		BagsModule m = instance;
		return m != null && m.isEnabled();
	}

	public static boolean othersShown() {
		BagsModule m = instance;
		return m == null || m.others.get();
	}

	@Override
	public void onWorldRender(Object context) {
		kr.lunaslight.mod.util.NovaWingsRender.draw(context, others.get(), false, kr.lunaslight.mod.util.NovaWings.BAG);
	}
}
