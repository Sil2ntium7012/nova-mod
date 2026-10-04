package kr.lunaslight.mod.util;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 49-53차(3-2 · 3-7): 특정 입자를 아예 만들지 않게 막는 길목.
 *
 * <p><b>왜 여기 하나로 되는가.</b> 마인크래프트에서 입자는 결국
 * {@code ParticleManager#addParticle(ParticleEffect, double×6)} 한 곳을 지나간다. 이 시그니처는
 * <b>1.15.2부터 1.21.11까지 한 글자도 바뀌지 않았다</b>(javap 실측). 그래서 믹스인 한 개로 전 버전을
 * 덮을 수 있고, 만들기 전에 막으므로 "그려 놓고 감추는" 것보다 실제로 가볍다.
 *
 * <p><b>무엇을 막을지는 종류(ParticleType) 동일성으로 판단한다.</b> {@code ParticleTypes}의 static
 * 필드를 한 번만 반사로 모아 두고(버전마다 없는 것은 조용히 건너뜀), 그 뒤로는 {@code ==} 비교뿐이라
 * 입자 하나당 비용이 사실상 없다 - 입자는 초당 수백 개가 지나가는 길목이라 이게 중요하다.
 *
 * <p><b>49-64차(3-1): 블록 파괴 입자는 여기로 안 온다</b> - {@code BlockDustParticle}을 직접 만들어
 * 넣는 다른 길이라 {@code addParticle}을 지나가지 않는다. 그래서 그 길목을 따로 막는다.
 * 실측해 보니 <b>메서드는 그대로인데 들고 있는 클래스만 옮겨졌다</b>:
 *
 * <table border="1">
 *   <caption>addBlockBreakParticles(BlockPos, BlockState)가 있는 곳</caption>
 *   <tr><th>버전</th><th>클래스</th></tr>
 *   <tr><td>1.15.2 ~ 1.21.8</td><td>{@code ParticleManager}</td></tr>
 *   <tr><td>1.18 ~ 1.21.11 · 26.x</td><td>{@code ClientWorld}</td></tr>
 * </table>
 *
 * <p>두 구간이 <b>1.18~1.21.8에서 겹친다.</b> 그래서 믹스인 두 벌을 두되 gradle 분기를 안 했다 -
 * 두 클래스는 모든 버전에 있고(메서드만 없을 뿐) {@code require = 0}이라, 각 버전에서 <b>맞는 쪽만</b>
 * 붙고 나머지는 조용히 넘어간다. 겹치는 구간에서 둘 다 붙어도 탈이 없다(먼저 걸리는 쪽에서 끝난다).
 */
public final class ParticleHook {

	private ParticleHook() {
	}

	/** 포션 효과의 뽀글뽀글. */
	public static volatile boolean hidePotion;
	/** 물·용암·꿀 등이 뚝뚝 떨어지는 방울. */
	public static volatile boolean hideDrips;
	/** 49-64차(3-1): 블록이 깨질 때 튀는 조각. 위 둘과 달리 addParticle을 안 지나간다(클래스 주석 참고). */
	public static volatile boolean hideBlockBreak;

	private static Set<Object> potionTypes;
	private static Set<Object> dripTypes;

	private static final String[] POTION_FIELDS = {"ENTITY_EFFECT", "AMBIENT_ENTITY_EFFECT"};
	private static final String[] DRIP_FIELDS = {
		"DRIPPING_WATER", "FALLING_WATER",
		"DRIPPING_LAVA", "FALLING_LAVA", "LANDING_LAVA",
		"DRIPPING_HONEY", "FALLING_HONEY", "LANDING_HONEY",
		"DRIPPING_OBSIDIAN_TEAR", "FALLING_OBSIDIAN_TEAR", "LANDING_OBSIDIAN_TEAR",
		"DRIPPING_DRIPSTONE_WATER", "FALLING_DRIPSTONE_WATER",
		"DRIPPING_DRIPSTONE_LAVA", "FALLING_DRIPSTONE_LAVA",
		"DRIPPING_NECTAR", "FALLING_NECTAR",
	};

	private static Set<Object> collect(String[] fieldNames) {
		Set<Object> out = Collections.newSetFromMap(new IdentityHashMap<>());
		try {
			Class<?> types = LunaCompat.classForName("net.minecraft.core.particles.ParticleTypes");
			for (java.lang.reflect.Field f : types.getFields()) {
				for (String want : fieldNames) {
					// 필드 이름은 난독화되지 않는 대문자 상수라 그대로 비교해도 되지만,
					// 혹시 모를 매핑 차이를 위해 LunaCompat의 이름 대조를 함께 쓴다.
					if (want.equals(f.getName()) || LunaCompat.nameMatches(types, want, f.getName())) {
						Object v = f.get(null);
						if (v != null) {
							out.add(v);
						}
						break;
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("particle:types", t);
		}
		return out;
	}

	/** 이 종류의 입자를 지금 막아야 하는지. 믹스인이 입자 하나마다 부른다 - 가벼워야 한다. */
	public static boolean shouldHide(Object particleType) {
		if (particleType == null) {
			return false;
		}
		if (hidePotion) {
			Set<Object> s = potionTypes;
			if (s == null) {
				s = potionTypes = collect(POTION_FIELDS);
			}
			if (s.contains(particleType)) {
				return true;
			}
		}
		if (hideDrips) {
			Set<Object> s = dripTypes;
			if (s == null) {
				s = dripTypes = collect(DRIP_FIELDS);
			}
			if (s.contains(particleType)) {
				return true;
			}
		}
		return false;
	}

	/** 아무것도 막지 않는 상태면 믹스인이 종류를 물어보지도 않게(가장 흔한 경우를 가장 싸게). */
	public static boolean active() {
		return hidePotion || hideDrips;
	}
}
