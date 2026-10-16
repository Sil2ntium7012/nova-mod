package kr.lunaslight.mod.util;

import net.minecraft.client.MinecraftClient;

/**
 * 49-349차(사용자: "배그처럼 자유시점도 가능하게 해줘 기본키 ALT로"): 키를 누르는 동안 <b>몸(플레이어 시선)은 그대로 두고 카메라만</b>
 * 마우스로 둘러본다. 마우스 움직임이 Entity.changeLookDirection(플레이어 회전)으로 가기 전에 가로채 카메라 각도 오프셋에 쌓고, 카메라가 플레이어
 * 각도를 읽는 자리(Camera.update의 getYaw/getPitch)에 그 오프셋을 더한다. 떼면 바로 제자리로(49-373차).
 * 상태는 모듈(FreeViewModule)이 켜고 끈다.
 */
public final class FreeView {

	/** 놓은 뒤 카메라가 제자리로 돌아오는 시간(ms). */
	public static final float RETURN_MS = 150f;
	/** 바닐라 Entity.changeLookDirection의 마우스 → 각도 배율. */
	private static final float TURN_SCALE = 0.15f;

	private static volatile boolean active;
	private static float yawOff, pitchOff;
	private static long releasedAt;
	private static float relYaw, relPitch;

	private FreeView() {
	}

	public static boolean active() {
		return active;
	}

	/** 모듈이 켜고 끈다. 끄면 지금 오프셋에서 0으로 돌아오는 애니메이션이 시작된다. */
	public static void setActive(boolean on) {
		if (on == active) {
			return;
		}
		if (on) {
			yawOff = 0f;
			pitchOff = 0f;
			releasedAt = 0L;
		} else {
			// 49-373차(사용자: "자유 시점 화면 돌아갈 때 부드럽게 하지 말고 바로 돌아가게"): 떼면 바로 제자리(되돌아가는 애니메이션 없음)
			relYaw = 0f;
			relPitch = 0f;
			releasedAt = 0L;
			yawOff = 0f;
			pitchOff = 0f;
		}
		active = on;
	}

	/**
	 * Entity.changeLookDirection 가로채기(EntityTurnMixin). 내 플레이어이고 자유 시점이 켜져 있으면 플레이어 대신 카메라 오프셋을 돌리고 true.
	 * playerPitch = 플레이어의 지금 pitch(카메라 pitch가 ±90을 안 넘게 오프셋을 자른다).
	 */
	public static boolean consumeTurn(Object entity, double dx, double dy, float playerPitch) {
		if (!active) {
			return false;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc == null || mc.player == null || mc.player != entity) {
			return false;
		}
		yawOff += (float) (dx * TURN_SCALE);
		float np = pitchOff + (float) (dy * TURN_SCALE);
		pitchOff = Math.max(-90f - playerPitch, Math.min(90f - playerPitch, np));
		return true;
	}

	/** 카메라 yaw에 더할 각도(놓은 뒤엔 0으로 줄어드는 중). */
	public static float yawOffset() {
		if (active) {
			return yawOff;
		}
		float k = returnK();
		return k <= 0f ? 0f : relYaw * k;
	}

	/** 카메라 pitch에 더할 각도. */
	public static float pitchOffset() {
		if (active) {
			return pitchOff;
		}
		float k = returnK();
		return k <= 0f ? 0f : relPitch * k;
	}

	/** 놓은 뒤 남은 비율(1 → 0, ease-out). */
	private static float returnK() {
		if (releasedAt == 0L) {
			return 0f;
		}
		float t = (System.currentTimeMillis() - releasedAt) / RETURN_MS;
		if (t >= 1f) {
			releasedAt = 0L;
			return 0f;
		}
		float u = 1f - t;
		return u * u;
	}

	/** 49-354차(사용자: "자유시점 시점 돌리면 그 방향에 맞게 방향키도 다르게"): 켜져 있으면 WASD를 카메라가 보는 방향 기준으로. 모듈이 매 틱 넣는다. */
	public static volatile boolean moveWithCamera = true;

	/**
	 * 49-354차: 이동 입력(x = 왼쪽, z = 앞)을 돌릴 각도(도). 안 돌리면 0 - 자유 시점이 꺼졌고 제자리로 돌아오는 중도 아니거나,
	 * [카메라 방향으로 이동]이 꺼졌거나, 내 플레이어가 아니면. 바닐라 입력 → 속도 변환이 몸 yaw로 돌리므로, 카메라 yaw = 몸 yaw + 오프셋만큼 더 돌린다.
	 */
	public static float moveYaw(Object entity) {
		if (!moveWithCamera) {
			return 0f;
		}
		float off = yawOffset();
		if (off == 0f) {
			return 0f;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc != null && mc.player != null && mc.player == entity ? off : 0f;
	}

	/** 카메라 pitch(플레이어 pitch + 오프셋)를 ±90 안으로. */
	public static float clampPitch(float pitch) {
		return Math.max(-90f, Math.min(90f, pitch));
	}
}
