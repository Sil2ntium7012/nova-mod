package kr.lunaslight.mod.util;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 지금 실행 중인 마인크래프트 버전을 감지하고, 각 모듈이 선언한 지원 버전 범위와 비교하는 유틸.
 * 멀티버전 지원(런처가 지원하는 버전이면 뭐든 이 모드도 설치돼서 켜져야 하되, 그 버전에서
 * 실제로 안 되는 기능만 개별적으로 잠기는 구조) 요청 반영.
 *
 * ⚠️ 컴파일 확인 필요: FabricLoader.getInstance().getModContainer("minecraft") →
 * getMetadata().getVersion().getFriendlyString() 체인은 Fabric Loader 공식 API로 널리 쓰이는
 * "현재 실행 중인 마인크래프트 버전 조회" 방법이지만, 이 세션은 실제로 컴파일해볼 수 없었으므로
 * 정확한 메서드 시그니처는 본인 PC에서 한 번 확인 필요합니다(문제가 있다면 IDE 자동완성으로
 * FabricLoader.getInstance().getModContainer(...) 반환 타입을 따라가면 바로 고칠 수 있을 정도로
 * 사소한 지점입니다).
 *
 * 버전 문자열 비교는 Fabric/Mojang API에 의존하지 않고 자체 구현했습니다(불확실한 외부 API에
 * 기대지 않기 위해) - "1.21.11", "1.20.1" 처럼 점으로 구분된 숫자 3개(메이저.마이너.패치)만
 * 비교하고, 그 이후에 붙는 문자열(스냅샷 표기 등)은 무시합니다.
 */
public final class LunaVersion {
	private static String cachedCurrent;

	private LunaVersion() {
	}

	/** 예: "1.21.11". 감지 실패 시 "0.0.0"을 돌려주고(항상 버전 제한에 안 걸리게), 로그로 남김. */
	public static String current() {
		if (cachedCurrent == null) {
			try {
				cachedCurrent = FabricLoader.getInstance()
					.getModContainer("minecraft")
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("0.0.0");
			} catch (Exception e) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.error("[Nova] 마인크래프트 버전 감지 실패 - 버전 제한 기능이 전부 무제한으로 동작합니다.", e);
				cachedCurrent = "0.0.0";
			}
		}
		return cachedCurrent;
	}

	private static String cachedMod;

	/**
	 * 49-64차(5-15): <b>Luna's Light 자체 버전</b>(예: "1.0.7"). 위 {@link #current()}는 마인크래프트
	 * 버전이라 둘은 완전히 다른 값이다 - 타이틀 화면 아래에 둘 다 보여 주려고 나눠 뒀다.
	 *
	 * <p>jar에 박히는 버전 문자열은 {@code "<모드버전>+mc<마크버전>"} 꼴이라(loom-common.gradle) 화면에
	 * 그대로 쓰면 길고 중복된다 - <b>{@code +} 앞만</b> 잘라 쓴다. 못 읽으면 "?"를 돌려준다
	 * (없는 버전을 지어내지 않는다 - 예전에 프로필 카드가 "0.1.0"으로 박혀 있던 게 딱 그 실수였다).
	 */
	public static String mod() {
		if (cachedMod == null) {
			String v = "?";
			try {
				v = FabricLoader.getInstance()
					.getModContainer(kr.lunaslight.mod.LunaClientMod.MOD_ID)
					.map(container -> container.getMetadata().getVersion().getFriendlyString())
					.orElse("?");
			} catch (Exception e) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.warn("[Nova] 모드 버전 조회 실패", e);
			}
			int plus = v.indexOf('+');
			cachedMod = plus > 0 ? v.substring(0, plus) : v;
		}
		return cachedMod;
	}

	/**
	 * 49-143차(사용자: "메인화면 루나클 버전이 0.1.0로 나와, 1.0.11인데"): 화면에 보이는 <b>Luna's Light 버전</b> =
	 * 런처 버전(.luna-launch.json launcherVersion, 런처가 실행할 때 실어 보냄). 옛 런처면 모드 버전.
	 */
	public static String client() {
		try {
			LunaSocial.load();
			String v = LunaSocial.launcherVersion();
			if (v != null && !v.isEmpty()) {
				return v;
			}
		} catch (Throwable ignored) {
		}
		return mod();
	}

	/** "1.21.11" -> [1, 21, 11]. 파싱 안 되는 부분(숫자가 아닌 접미사 등)은 거기서 멈추고 0으로 채움. */
	private static int[] parse(String version) {
		int[] result = new int[3];
		if (version == null) {
			return result;
		}
		String[] parts = version.trim().split("[.\\-+]");
		int count = 0;
		for (String part : parts) {
			if (count >= 3) {
				break;
			}
			if (!part.isEmpty() && part.chars().allMatch(Character::isDigit)) {
				try {
					result[count++] = Integer.parseInt(part);
				} catch (NumberFormatException ignored) {
					break;
				}
			} else {
				break;
			}
		}
		return result;
	}

	private static int compare(String a, String b) {
		int[] pa = parse(a);
		int[] pb = parse(b);
		for (int i = 0; i < 3; i++) {
			int diff = Integer.compare(pa[i], pb[i]);
			if (diff != 0) {
				return diff;
			}
		}
		return 0;
	}

	/**
	 * 현재 버전이 [min, max] 범위 안인지(둘 다 포함). min/max 둘 다 null이면 항상 true(무제한).
	 * "1.20" 처럼 패치 번호를 생략해도 됨(생략된 자리는 0 취급 - 예: min="1.20"은 1.20.0부터).
	 */
	public static boolean isWithin(String min, String max) {
		String cur = current();
		if (min != null && compare(cur, min) < 0) {
			return false;
		}
		if (max != null && compare(cur, max) > 0) {
			return false;
		}
		return true;
	}
}
