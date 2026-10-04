package kr.lunaslight.mod.util;

import net.minecraft.entity.Entity;

/**
 * 49-59차(3-4 · 1-6의 "유저 캐릭터 가리기"): 이름으로 엔티티를 안 그리게 한다.
 *
 * <p>거는 자리는 {@code EntityRenderDispatcher#shouldRender(Entity, Frustum, double, double, double)} -
 * <b>"이걸 그릴까?"를 묻는 바닐라의 원래 관문</b>이다. 여기서 false를 주면 그 엔티티는 렌더 목록에
 * 들어가지도 않는다(그리기를 중간에 취소하는 것보다 싸고, 반쯤 그려지는 자리가 없다).
 *
 * <p><b>성능</b>: 이 메서드는 로드된 엔티티마다 <b>매 프레임</b> 불린다. 그래서
 * <ol>
 *   <li>목록이 비어 있으면(대부분의 경우) 맨 앞에서 바로 false - 리플렉션도 문자열도 안 만든다.</li>
 *   <li>켜져 있을 때도 <b>{@code getCustomName()} 먼저</b> 본다. 이름표를 안 단 몹은 여기서 null이라
 *       바로 끝난다. {@code getName()}은 몹 종류 이름을 <b>새로 만들어 내는</b> 버전이 있어(번역 텍스트
 *       생성) 매 프레임 부르기엔 비싸다 - 이름표가 없고 플레이어도 아니면 아예 안 부른다.</li>
 * </ol>
 *
 * <p>내 캐릭터는 절대 안 숨긴다 - 3인칭에서 내가 사라지면 그건 고장으로 보인다.
 *
 * <p>49-60차(3-11 · 3-6): 같은 관문에 <b>성능용 두 가지</b>를 더 얹었다 - 겹친 것 줄이기와 거리 제한.
 * 이름으로 가리는 것과 달리 이쪽은 "많아서" 숨기는 것이라 규칙이 다르다:
 * <ul>
 *   <li><b>플레이어는 절대 대상이 아니다.</b> 남이 안 보이는 건 성능 개선이 아니라 게임이 달라지는 것이다
 *       (PVP에서 상대가 사라지는 건 고장이거나 치트다).</li>
 *   <li>겹친 것 판단은 <b>1틱에 한 번</b> 만들어 둔 목록을 보는 것뿐이다 - 매 프레임 세면 엔티티 수의
 *       제곱이 된다. {@link #thinned}는 그 목록이고 EntityCullModule이 틱마다 갈아 끼운다.</li>
 *   <li>거리는 렌더 관문이 이미 넘겨주는 <b>카메라 좌표</b>로 그 자리에서 계산한다 - API를 하나도 안 부른다.</li>
 * </ul>
 *
 * <p>여기서 숨기는 건 <b>그리기뿐</b>이다 - 아이템은 그대로 주울 수 있고 몹도 그대로 때릴 수 있다
 * (히트박스·상호작용은 렌더와 무관하다).
 */
public final class EntityHideHook {
	private EntityHideHook() {
	}

	/** 3-4: 이 이름을 가진 엔티티를 안 그린다(소문자, 없으면 null). */
	public static volatile String[] names;
	/** 1-6: 차단한 사람의 캐릭터를 안 그린다(소문자, 없으면 null). */
	public static volatile String[] blockedPlayers;

	/** 3-11: 한 자리에 너무 많이 겹쳐서 안 그리기로 한 것들(틱마다 갈아 끼움, 없으면 null). */
	public static volatile java.util.Set<Entity> thinned;
	/** 3-6: 이 거리(제곱)보다 먼 엔티티는 안 그린다. 0이면 끔. */
	public static volatile double maxDistanceSq;
	/**
	 * 49-63차(3-9): 자리 비움 동안만 걸리는 거리(제곱). 0이면 끔.
	 *
	 * <p>{@link #maxDistanceSq}와 <b>따로</b> 두는 이유: 두 모듈(엔티티 줄이기 · 자리 비움)이 틱마다
	 * 각자 자기 값을 밀어 넣는데 한 칸을 같이 쓰면 나중에 쓴 쪽이 이겨서 매 틱 값이 튄다.
	 * 칸을 나누고 <b>둘 중 더 가까운 쪽</b>을 쓰면 서로 안 싸운다.
	 */
	public static volatile double afkDistanceSq;

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
	 * 이 엔티티를 아예 안 그릴지. camX/camY/camZ는 렌더 관문이 그대로 넘겨주는 카메라 좌표다.
	 */
	public static boolean shouldHide(Entity entity, double camX, double camY, double camZ) {
		String[] byName = names;
		String[] byPlayer = blockedPlayers;
		java.util.Set<Entity> thin = thinned;
		double maxSq = distanceLimitSq();
		// 49-76차(6-16): 가려짐 판정. 플레이어는 이 아래 어디에서도 대상이 아니지만, 여기서도 먼저 거른다.
		if (OcclusionCull.on && entity != null && !(entity instanceof net.minecraft.entity.player.PlayerEntity)
				&& OcclusionCull.hideEntity(entity)) {
			return true;
		}
		if (entity == null || (byName == null && byPlayer == null && thin == null && maxSq <= 0)) {
			return false;   // 꺼져 있을 때의 비용 = 필드 네 번 읽기
		}
		try {
			net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
			if (mc != null && entity == mc.player) {
				return false;   // 내 캐릭터는 예외 없이 그린다
			}
			boolean player = entity instanceof net.minecraft.entity.player.PlayerEntity;
			if (!player) {
				// 성능용 두 가지 - 플레이어는 아예 대상이 아니다(위 주석 참고).
				if (maxSq > 0) {
					double dx = EntityPos.x(entity) - camX;
					double dy = EntityPos.y(entity) - camY;
					double dz = EntityPos.z(entity) - camZ;
					if (dx * dx + dy * dy + dz * dz > maxSq) {
						return true;
					}
				}
				if (thin != null && thin.contains(entity)) {
					return true;
				}
			}
			if (byName == null && byPlayer == null) {
				return false;   // 이름 규칙이 꺼져 있으면 여기서 끝(이름 읽는 비용 0)
			}
			String label = customNameOf(entity);
			if (label == null && player) {
				label = plainName(entity);   // 플레이어는 이름표가 없어도 닉이 곧 이름
			}
			if (label == null || label.isEmpty()) {
				return false;
			}
			String low = label.toLowerCase(java.util.Locale.ROOT);
			if (byName != null && matches(low, byName)) {
				return true;
			}
			return player && byPlayer != null && matches(low, byPlayer);
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean matches(String low, String[] list) {
		for (String n : list) {
			if (low.contains(n)) {
				return true;
			}
		}
		return false;
	}

	/** 이름표(모루로 붙인 이름). 안 달았으면 null - 여기서 끝나는 게 대부분이다. */
	private static String customNameOf(Entity entity) {
		Object name = LunaCompat.callNoArg(entity, "getCustomName");
		return name instanceof net.minecraft.text.Text t ? safeString(t) : null;
	}

	private static String plainName(Entity entity) {
		Object name = LunaCompat.callNoArg(entity, "getName");
		return name instanceof net.minecraft.text.Text t ? safeString(t) : null;
	}

	private static String safeString(net.minecraft.text.Text text) {
		try {
			return text.getString();
		} catch (Throwable ignored) {
			return null;
		}
	}
}
