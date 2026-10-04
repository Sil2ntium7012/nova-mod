package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.OcclusionCull;

/**
 * 49-76차(6-16): <b>가려진 것 안 그리기</b> - 벽 뒤의 엔티티·상자·화로·간판을 건너뛴다.
 *
 * <p>사용자: "상자 화로 줄이기는 왜 있는 건지 모르겠음. 나는 렉을 줄여 달라 한 거지 줄여 달라고는 안 함.
 * 대신 내가 바라보고 있지 않은 엔티티·블록 엔티티는 렉을 줄여 주는 시스템 필요."
 * 거리로 안 그리던 모듈(49-62차)은 지웠다. 이건 <b>거리와 상관없이, 안 보이는 것만</b> 건너뛴다.
 *
 * <p>설정이 없다. 켜면 되는 기능이고, "몇 블록"처럼 고를 값이 없다. 어떻게 판정하는지·무엇을 절대
 * 안 숨기는지는 {@link OcclusionCull} 주석에 있다(플레이어는 절대 대상이 아니다).
 *
 * <p><b>성능</b>: 광선 계산은 전부 다른 스레드에서 돈다. 렌더 스레드는 "집합에 있나"만 본다.
 */
public class OcclusionCullModule extends Module {

	public OcclusionCullModule() {
		super("occlusion_cull", "가려진 것 안 그리기", ModuleCategory.FEATURE, "벽 뒤 엔티티 | 상자 | 화로 생략");
		// 49-85차(8-13, 사용자: "가려진 것 안 그리기 이딴 설정 두지 말 것, 기본으로 켜져 있어야 하는 것"):
		// 고를 값이 없는 순수 성능 기능이라 토글·카드를 없애고 항상 켠다(플레이어는 절대 안 숨긴다 - OcclusionCull 참고).
		alwaysOn();
	}

	/** 항상 켜진 성능 기능 - 기능 목록·설정에 카드를 만들지 않는다. */
	@Override
	public boolean hiddenInList() {
		return true;
	}

	@Override
	protected void onEnable() {
		OcclusionCull.on = true;
	}

	@Override
	protected void onDisable() {
		OcclusionCull.on = false;
		OcclusionCull.clear();
	}

	@Override
	public void onTick() {
		OcclusionCull.on = isEnabled();
		OcclusionCull.tick(client);
	}
}
