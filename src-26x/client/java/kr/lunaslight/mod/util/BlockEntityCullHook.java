package kr.lunaslight.mod.util;

/**
 * 49-62차(3-5 · 3-6의 나머지 반쪽): <b>멀리 있는 상자·화로·간판을 안 그린다.</b>
 *
 * <p>사용자 요청 3-5: "화로·상자 등 서버에서 많이 쓰이는 블록 엔티티 렉 줄이기".
 *
 * <p><b>왜 이게 렉이 되나</b>: 상자·간판·배너·머리 같은 것들은 일반 블록처럼 청크 메시에 구워지지
 * 않는다. 하나하나가 <b>매 프레임 따로 그려지는 모델</b>이다(간판은 거기에 글자까지 얹는다).
 * 창고 서버에서 화면에 상자가 수백 개면 그만큼의 모델 렌더가 매 프레임 돈다.
 *
 * <p><b>거는 자리</b>는 바닐라가 "이 블록 엔티티를 그려라"라고 부르는 <b>그 한 줄</b>이다 -
 * 시대에 따라 이름이 둘로 갈린다(믹스인 두 벌, gradle이 버전에 맞는 하나만 컴파일):
 *
 * <table border="1">
 *   <caption>거는 자리</caption>
 *   <tr><th>버전</th><th>클래스</th><th>메서드</th></tr>
 *   <tr><td>1.15.2 ~ 1.21.8</td><td>{@code BlockEntityRenderDispatcher}</td>
 *       <td>{@code render(BlockEntity, float, MatrixStack, VertexConsumerProvider)}</td></tr>
 *   <tr><td>1.21.9 ~ 1.21.11 · 26.x</td><td>{@code BlockEntityRenderManager}</td>
 *       <td>{@code render(BlockEntityRenderState, MatrixStack, OrderedRenderCommandQueue, CameraRenderState)}</td></tr>
 * </table>
 *
 * <p>둘 다 {@code WorldRenderer}가 블록 엔티티 하나마다 부르는 자리임을 상수 풀에서 확인했다
 * (1.20.4 · 1.21.11 실측). 앞쪽 시그니처는 <b>1.15.2부터 1.21.8까지 한 글자도 안 바뀌었다.</b>
 *
 * <p><b>솔직히 적어 둘 것</b>: 상자·간판은 <b>이 모델이 전부</b>다(일반 블록 모델이 따로 없다).
 * 그래서 거리를 넘기면 흐려지는 게 아니라 <b>아예 안 보인다</b>. 그게 이 기능의 값이자 대가라서
 * 기본은 꺼 둔다. 설명 줄에도 그대로 적었다 - 켜 놓고 "상자가 사라졌다"고 놀랄 일이 없게.
 *
 * <p><b>성능</b>: 이 판단은 블록 엔티티마다 매 프레임 돈다. 꺼져 있으면 {@link #maxDistanceSq}
 * 한 번 읽고 끝이고(그게 0), 켜져 있어도 카메라 좌표 세 번 읽고 뺄셈 세 번이 전부다 -
 * 목록도 API 호출도 없다.
 */
public final class BlockEntityCullHook {
	private BlockEntityCullHook() {
	}

	/** 이 거리(제곱)보다 멀면 안 그린다. 0이면 꺼짐. */
	public static volatile double maxDistanceSq;
	/**
	 * 49-63차(3-9): 자리 비움 동안만 걸리는 거리(제곱). 0이면 끔.
	 * 두 모듈이 틱마다 각자 값을 밀어 넣으므로 칸을 나누고 <b>더 가까운 쪽</b>을 쓴다(EntityHideHook과 같은 이유).
	 */
	public static volatile double afkDistanceSq;

	/** 꺼져 있으면 true - 믹스인이 좌표를 꺼내기 전에 싸게 걸러내려고 쓴다. */
	public static boolean isOff() {
		return maxDistanceSq <= 0 && afkDistanceSq <= 0 && !OcclusionCull.on;
	}

	/** 지금 실제로 적용할 거리(제곱). 켜져 있는 것들 중 더 가까운 쪽, 둘 다 꺼져 있으면 0. */
	private static double distanceLimitSq() {
		double a = maxDistanceSq;
		double b = afkDistanceSq;
		if (a <= 0) {
			return b;
		}
		if (b <= 0) {
			return a;
		}
		return Math.min(a, b);
	}

	/**
	 * 이 블록 엔티티를 건너뛸지. 좌표는 블록 칸이라 가운데(+0.5)로 재서 한 칸 차이로 깜빡이지 않게 한다.
	 *
	 * <p>기준점은 <b>카메라가 붙어 있는 엔티티</b>다({@code getCameraEntity} - 관전 중이면 관전 대상).
	 * 실제 카메라는 3인칭에서 최대 4블록쯤 뒤에 있지만, 이 기능의 거리 단위가 수십 블록이라 그 차이는
	 * 의미가 없다. 대신 버전마다 다른 카메라 필드를 건드리지 않아도 된다.
	 */
	public static boolean shouldSkip(int bx, int by, int bz) {
		// 49-76차(6-16): 벽 뒤에 가려진 상자·화로·간판은 건너뛴다(판정은 다른 스레드가 미리 해 둔다)
		if (OcclusionCull.on && OcclusionCull.hideBlock(bx, by, bz)) {
			return true;
		}
		double maxSq = distanceLimitSq();
		if (maxSq <= 0) {
			return false;   // 꺼져 있을 때의 비용 = 필드 한 번 읽기
		}
		try {
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
			net.minecraft.world.entity.Entity cam = mc == null ? null : mc.getCameraEntity();
			if (cam == null) {
				return false;
			}
			double dx = (bx + 0.5) - cam.getX();
			double dy = (by + 0.5) - cam.getY();
			double dz = (bz + 0.5) - cam.getZ();
			return dx * dx + dy * dy + dz * dz > maxSq;
		} catch (Throwable ignored) {
			return false;
		}
	}
}
