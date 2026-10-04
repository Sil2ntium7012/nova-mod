package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * 밝기(감마).
 *
 * 49-21차 재작성(사용자: "감마는 완전히 밝게 가능하게, 야간투시 낀 급으로 다양하게"): 바닐라 슬라이더는
 * 0~100%(감마 0~1)까지지만, 라이트맵은 감마 생값을 그대로 쓰므로 1을 훨씬 넘기면 어두운 곳까지 다
 * 밝아진다(options.txt gamma 해킹과 같은 원리). SimpleOption#setValue가 0~1로 잘라내기 때문에
 * LunaCompat.setGammaRaw로 내부 값에 직접 쓴다. 100% = 바닐라 최대 밝기, 1000% = 풀브라이트.
 * 끄면 켜기 전 값으로 되돌림.
 *
 * <p>49-125차(사용자: "밝기(감마 기능), 서버에서는 작동 안되게"): <b>멀티플레이 서버에 있는 동안은 적용하지 않는다</b>
 * (바닐라 값으로 되돌려 둠). 싱글(통합 서버, LAN 포함)에서만 동작. 서버에서 나오면 다시 적용.
 */
public class GammaControlModule extends Module {

	private final IntSetting brightness = register(new IntSetting(
			"brightness", "밝기", "100%가 마인크래프트 최대이며, 그 이상은 야간투시처럼 밝아집니다. 서버에서는 작동하지 않습니다(싱글 전용).", 300, 0, 1000, 10));

	private Double savedGamma;
	private int ticks;
	private int lastApplied = Integer.MIN_VALUE;
	/** 49-125차: 지금 멀티플레이 서버에 있는지 - 서버에서는 적용하지 않는다. */
	private boolean onServer;

	public GammaControlModule() {
		super("gamma_control", "밝기", ModuleCategory.VIEW, "기본 한계를 넘는 밝기 | 싱글 전용");
		singleOnly();   // 49-256차: 서버에 있으면 목록에서 회색
		brightness.onChange(this::apply);
	}

	@Override
	protected void onEnable() {
		savedGamma = LunaCompat.getGamma(client.options);
		lastApplied = Integer.MIN_VALUE;
		apply();
	}

	@Override
	protected void onDisable() {
		if (savedGamma != null) {
			LunaCompat.setGamma(client.options, Math.max(0, Math.min(1, savedGamma)));
		}
		lastApplied = Integer.MIN_VALUE;
	}

	private void apply() {
		if (!isEnabled() || client.options == null || onServer) {
			return;
		}
		LunaCompat.setGammaRaw(client.options, brightness.get() / 100.0);
		lastApplied = brightness.get();
	}

	/** 서버에 있는 동안은 바닐라 범위(0~1)의 값으로 되돌려 둔다. */
	private void restoreVanilla() {
		if (client.options == null) {
			return;
		}
		double base = savedGamma == null ? 1.0 : savedGamma;
		LunaCompat.setGamma(client.options, Math.max(0, Math.min(1, base)));
		lastApplied = Integer.MIN_VALUE;
	}

	@Override
	public void onTick() {
		// 49-125차: 서버(통합 서버가 아닌 월드)에 들어가면 끄고, 나오면 다시 켠다.
		boolean server = client.level != null && !LunaCompat.isSinglePlayer(client);
		if (server != onServer) {
			onServer = server;
			if (server) {
				restoreVanilla();
			} else {
				lastApplied = Integer.MIN_VALUE;
			}
		}
		if (onServer) {
			return;
		}
		// 바닐라 설정 화면 등이 값을 되돌렸을 수 있으니 2초마다 한 번 다시 맞춤
		if (++ticks % 40 == 0 || lastApplied != brightness.get()) {
			apply();
		}
	}
}
