package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;

/**
 * 49-463차(사용자: "사탕무 히트박스 키워 줘"): <b>사탕무 크게 선택</b>. 사탕무(너굴마을은 이 작물 그림을 바꿔 자기 작물로 씀)는 다 자라도
 * 반 칸 높이라 조준이 잘 안 걸린다. 켜면 고르는 칸(외곽선)을 한 칸 전체로 키운다(BeetrootShapeMixin). 몸은 그대로 통과한다. 기본 켜짐.
 */
public class BeetHitboxModule extends Module {
	public static volatile boolean on;

	public BeetHitboxModule() {
		super("beet_hitbox", "사탕무 크게 선택", ModuleCategory.FEATURE, "사탕무(벼)를 고르는 칸을 밀과 같게");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		on = true;
	}

	@Override
	protected void onDisable() {
		on = false;
	}

	@Override
	public void onTick() {
		on = isEnabled();
	}
}
