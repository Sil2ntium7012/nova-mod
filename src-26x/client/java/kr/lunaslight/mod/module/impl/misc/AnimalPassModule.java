package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.AnimalPass;

/** 49-456차 → 49-472차: <b>몹 조준 보정</b> - 작물 칸 위에 서 있거나 블록보다 앞에 있는 몹, 동물을 블록 대신 때린다({@link AnimalPass}). 잔디 관통은 뺐다. 기본 켜짐. */
public class AnimalPassModule extends Module {

	public AnimalPassModule() {
		super("animal_pass", "몹 조준 보정", ModuleCategory.FEATURE, "발밑 작물을 보고 때려도 몹이 맞기");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		AnimalPass.on = true;
	}

	@Override
	protected void onDisable() {
		AnimalPass.on = false;
	}

	@Override
	public void onTick() {
		AnimalPass.on = isEnabled();
	}
}
