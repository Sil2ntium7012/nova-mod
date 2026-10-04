package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 49-53차(3-8): 시야 고정 - <b>속도 때문에 화면이 넓어졌다 좁아졌다 하는 것</b>을 끈다.
 *
 * <p>달리기·신속·구멍 뚫린 길을 지날 때마다 시야각이 출렁이는데, 이게 멀미가 나거나 조준이 흔들린다는
 * 이유로 끄고 싶어 하는 사람이 많다. 마인크래프트는 1.17부터 이걸 바닐라 설정
 * (<code>FOV 효과 크기</code> = {@code fovEffectScale})으로 갖고 있어서, 우리가 화면을 다시 그릴
 * 필요 없이 <b>그 값을 0으로 눌러두기만</b> 하면 된다 - 렌더러를 건드리지 않으니 비용도 0이고
 * 버전마다 달라지는 FOV 계산식에 기대지도 않는다.
 *
 * <p>1.16 이하에는 그 설정 자체가 없어서(시야 출렁임이 코드에 박혀 있다) 기능을 잠근다 -
 * 거기까지 맞추려면 버전별 FOV 계산식에 믹스인을 걸어야 하는데, 그 값어치가 없다고 봤다.
 *
 * <p>끄면 켜기 전 값으로 되돌려 준다(바닐라 설정을 빼앗지 않는다).
 */
public class FovLockModule extends Module {

	private Double saved;

	public FovLockModule() {
		// 49-76차(6-10, 사용자: "그래픽에 시야 고정이 왜 있는 건지 모르겠음"): 이게 3-8 "속도 FOV 왜곡"이다.
		// "시야 고정"이라는 이름이 무엇을 고정하는지 안 보여서 못 알아본 것 - 바닐라 설정 이름(시야각 효과)을 따른다.
		super("fov_lock", "시야각 효과 끄기", ModuleCategory.VIEW, "달리기/물약의 시야각 변화 없음");
		// 49-122차(사용자: "시야각 효과 끄기 > UI로 이동"): [그래픽] → [UI] 설정 페이지.
		settingsPage(kr.lunaslight.mod.module.SettingsPage.UI);
		supportedVersions("1.17", null);
	}

	@Override
	protected void onEnable() {
		if (client == null || client.options == null) {
			return;
		}
		Double now = LunaCompat.getFovEffectScale(client.options);
		if (now != null && now > 0.0001) {
			saved = now;   // 0이 아닐 때만 기억한다(껐다 켰다 반복해도 원래 값을 잃지 않게)
		}
		LunaCompat.setFovEffectScale(client.options, 0.0);
	}

	@Override
	public void onTick() {
		// 바닐라 설정 화면에서 되돌려 놓은 경우를 대비해 계속 0으로 눌러 둔다.
		if (client != null && client.options != null) {
			Double now = LunaCompat.getFovEffectScale(client.options);
			if (now != null && now > 0.0001) {
				saved = now;
				LunaCompat.setFovEffectScale(client.options, 0.0);
			}
		}
	}

	@Override
	protected void onDisable() {
		if (client != null && client.options != null) {
			LunaCompat.setFovEffectScale(client.options, saved == null ? 1.0 : saved);
		}
	}
}
