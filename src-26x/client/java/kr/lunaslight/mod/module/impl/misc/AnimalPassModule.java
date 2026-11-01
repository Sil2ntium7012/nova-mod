package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.AnimalPass;

/** 49-456차: <b>동물 통과</b> - 밭과 풀 위에 선 동물을 지나 작물을 캐고 심는다({@link AnimalPass}). 기본 켜짐. */
public class AnimalPassModule extends Module {

	private final BooleanSetting sneak = register(new BooleanSetting(
			"sneak_to_hit", "웅크리면 동물 선택", "웅크린 동안은 원래대로 동물을 고릅니다(먹이 주기, 잡기).", true));

	public AnimalPassModule() {
		super("animal_pass", "동물 통과", ModuleCategory.FEATURE, "밭이나 풀 위의 동물을 지나 작물을 캐고 심기");
		defaultEnabled(true);
		sneak.onChange(() -> AnimalPass.sneakToHit = sneak.get());
	}

	@Override
	protected void onEnable() {
		AnimalPass.on = true;
		AnimalPass.sneakToHit = sneak.get();
	}

	@Override
	protected void onDisable() {
		AnimalPass.on = false;
	}

	@Override
	public void onTick() {
		AnimalPass.on = isEnabled();
		AnimalPass.sneakToHit = sneak.get();
	}
}
