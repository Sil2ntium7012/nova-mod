package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.AnimalPass;

/** 49-456차 → 49-459차: <b>잔디 관통</b>(49-460차 사용자가 이름 정함) - 풀이나 작물에 가린 동물, 몹을 풀을 안 베고 바로 때린다({@link AnimalPass}). 기본 켜짐. */
public class AnimalPassModule extends Module {

	private final BooleanSetting sneak = register(new BooleanSetting(
			"sneak_to_hit", "웅크리면 블록 선택", "웅크린 동안은 원래대로 풀이나 작물 블록을 고릅니다. 괭이를 들었을 때도 블록입니다.", true));

	public AnimalPassModule() {
		super("animal_pass", "잔디 관통", ModuleCategory.FEATURE, "풀이나 작물에 가린 동물, 몹을 바로 때리기");
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
