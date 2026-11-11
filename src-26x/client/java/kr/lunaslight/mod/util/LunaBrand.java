package kr.lunaslight.mod.util;

import java.util.regex.Pattern;

/**
 * 49-627차(사용자: "곳곳에 너무 노바클 이름이나 흔적이 많아, 너굴라이언트에서는 노바클 말고 너굴라이언트로 해 줘"):
 * 화면에 보이는 클라이언트 이름. 너굴라이언트로 켰으면 글자를 그리는 곳(LunaDraw.text/width, 채팅, 액션바)에서
 * "Nova Client", "Nova", "노바"를 "너굴라이언트"로 바꿔 보여 준다(기록용 로그, 파일 이름, 서명 문자열은 그대로).
 */
public final class LunaBrand {
	private LunaBrand() {
	}

	public static final String NEOGUL = "너굴라이언트";

	private static final Pattern NOVA_CLIENT = Pattern.compile("(?i)nova\\s*client");
	private static final Pattern NOVA_WORD = Pattern.compile("(?<![A-Za-z0-9_])(?:Nova|NOVA)(?![A-Za-z0-9_])");

	private static final java.util.Map<String, String> CACHE = new java.util.LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(java.util.Map.Entry<String, String> e) {
			return size() > 256;
		}
	};

	public static boolean neogul() {
		return LunaSocial.nugulient;
	}

	/** 클라이언트 이름(긴 것). */
	public static String name() {
		return neogul() ? NEOGUL : "Nova Client";
	}

	/** 클라이언트 이름(짧은 것). */
	public static String shortName() {
		return neogul() ? NEOGUL : "Nova";
	}

	/** 보이는 글자의 클라이언트 이름을 지금 런처에 맞게. */
	public static String t(String s) {
		if (s == null || !neogul()) {
			return s;
		}
		if (s.indexOf("Nova") < 0 && s.indexOf("NOVA") < 0 && s.indexOf("nova") < 0 && s.indexOf("노바") < 0) {
			return s;
		}
		synchronized (CACHE) {
			String hit = CACHE.get(s);
			if (hit != null) {
				return hit;
			}
			String r = NOVA_CLIENT.matcher(s).replaceAll(NEOGUL);
			r = NOVA_WORD.matcher(r).replaceAll(NEOGUL);
			r = r.replace("노바", NEOGUL);
			CACHE.put(s, r);
			return r;
		}
	}
}
