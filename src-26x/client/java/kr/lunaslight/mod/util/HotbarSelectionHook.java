package kr.lunaslight.mod.util;

/**
 * 49-41차(사용자: "부드러운 휠 하면 핫바 선택 테두리도 부드럽게 움직여 달라"): 핫바의 선택 테두리 x를
 * 실제 선택 칸으로 지수 보간해 미끄러지게 한다. HotbarSelectionMixin이 renderHotbar 안 선택 테두리
 * 그리기 호출(두 번째 그리기, 1.20.1·1.20.4·1.21.1·1.21.10 javap 실측)의 x 인자를 여기로 돌린다.
 * 8 → 0처럼 끝에서 끝으로 넘어갈 땐(차이 ≥ 5칸) 바로 점프(핫바를 가로질러 날아가면 이상함).
 */
public final class HotbarSelectionHook {
	private HotbarSelectionHook() {
	}

	public static volatile boolean enabled = true;
	/** 초당 따라붙는 속도(지수 보간 계수). */
	public static volatile float speed = 18f;

	private static float pos = -1f;
	private static long lastNanos;

	/** 바닐라가 넘긴 x(= 왼쪽 끝 − 1 + 선택 칸 × 20)를 보간된 위치로. */
	public static int adjust(int x) {
		try {
			if (!enabled) {
				pos = -1f;
				return x;
			}
			net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
			if (client.player == null) {
				return x;
			}
			int sel = LunaCompat.selectedSlot(client.player);
			if (sel < 0) {
				return x;
			}
			long now = System.nanoTime();
			if (pos < 0 || Math.abs(sel - pos) >= 5f) {
				pos = sel;           // 첫 프레임 / 끝에서 끝으로 넘어감 → 점프
			} else {
				float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
				float k = 1f - (float) Math.exp(-speed * dt);
				pos += (sel - pos) * k;
				if (Math.abs(sel - pos) < 0.01f) {
					pos = sel;
				}
			}
			lastNanos = now;
			return x + Math.round((pos - sel) * 20f);
		} catch (Throwable t) {
			LunaCompat.warnOnce("hotbarSel", t);
			return x;
		}
	}
}
