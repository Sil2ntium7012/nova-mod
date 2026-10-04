package kr.lunaslight.mod.util;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * 49-69차(2-3 앞 절반): 불 오버레이를 위아래로 옮기는 실제 계산.
 *
 * <p><b>값이 0이면 아무 일도 하지 않는다</b> - push도 pop도 안 한다. 건드리지 않은 사람에게는
 * 예전과 완전히 같은 그림이 나온다는 뜻이고, 이 기능 때문에 생기는 비용도 0이다.
 *
 * <p><b>단위에 대해 솔직히</b>: 불꽃은 화면 픽셀이 아니라 <b>-1~1 정규 좌표</b>에 그려진다. 그래서
 * 설정 값(-50~50)을 그대로 픽셀로 읽으면 안 되고, {@link #SPAN}만큼 나눠 밀어 준다. 몇이 "딱 좋은지"는
 * 화면 비율과 시야각에 따라 달라서, <b>눈으로 맞추는 슬라이더</b>로 두었다 - 내가 숫자를 정해 주는
 * 척하는 것보다 낫다.
 *
 * <p>{@code pushed}로 짝을 맞춘다. 밀지 않았으면 되돌리지도 않는다(행렬 스택이 어긋나면 화면 전체가
 * 깨지므로, 짝이 안 맞는 일이 절대 없어야 한다).
 */
public final class FireOverlayHook {
	private FireOverlayHook() {
	}

	/** -50 ~ 50 한 칸이 정규 좌표로 얼마인지. 50이 화면 반 칸쯤 되도록 잡았다. */
	private static final float SPAN = 100f;

	/** 위로 올리면 양수. 0이면 손대지 않는다. */
	public static volatile int offset;

	private static boolean pushed;

	public static void push(PoseStack matrices) {
		pushed = false;
		int v = offset;
		if (matrices == null || v == 0) {
			return;
		}
		try {
			matrices.pushPose();
			matrices.translate(0.0, v / SPAN, 0.0);
			pushed = true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("fireOverlay:push", t);
			pushed = false;
		}
	}

	public static void pop(PoseStack matrices) {
		if (!pushed || matrices == null) {
			return;
		}
		pushed = false;
		try {
			matrices.popPose();
		} catch (Throwable t) {
			LunaCompat.warnOnce("fireOverlay:pop", t);
		}
	}
}
