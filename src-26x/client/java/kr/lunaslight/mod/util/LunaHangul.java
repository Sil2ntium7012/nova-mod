package kr.lunaslight.mod.util;

/**
 * 49-170차(사용자: "검색에 영어로 dlsqps 이렇게 써도 인벤 이런식으로 인식하게"): 한/영 키를 안 바꾸고 친 검색어도
 * 맞게 하기 위한 자모 비교.
 *
 * <p>글자를 조립하지 않고 <b>양쪽 다 자모 열로 풀어서</b> 부분 일치를 본다. 검색어의 영문은 두벌식 자판 자리의
 * 자모로 바꾸고(d→ㅇ, l→ㅣ, s→ㄴ ...), 대상 글(기능 이름·설명)의 완성 글자는 초성·중성·종성으로 푼다. 겹받침(ㄳ)과
 * 겹모음(ㅘ)은 자판에서 두 번 쳐서 나오는 것이라 두 자모로 더 푼다. 그래서 "dlsq"(ㅇㅣㄴㅂ)도 "인벤"(ㅇㅣㄴㅂㅔㄴ)의
 * 앞부분과 맞는다. 영문·숫자는 그대로 두므로 "fps" 같은 영문 검색도 그대로 된다 - 다만 영문 검색어는 자모로도
 * 한 번 더 비교하므로 두 경우 중 하나만 맞으면 된다.
 */
public final class LunaHangul {

	private LunaHangul() {
	}

	private static final char[] CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ".toCharArray();
	private static final char[] JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ".toCharArray();
	private static final char[] JONG = "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ".toCharArray();

	/** 두벌식: 영문 키 → 자모. 대문자는 쌍자음/ㅒㅖ 자리. */
	private static final String KEYS = "qwertyuiopasdfghjklzxcvbnmQWERTOPYUIASDFGHJKLZXCVBNM";
	private static final String JAMO = "ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡㅃㅉㄸㄲㅆㅒㅖㅛㅕㅑㅁㄴㅇㄹㅎㅗㅓㅏㅣㅋㅌㅊㅍㅠㅜㅡ";

	/** 겹자모 → 자판에서 치는 두 자모. */
	private static String split(char c) {
		return switch (c) {
			case 'ㄳ' -> "ㄱㅅ";
			case 'ㄵ' -> "ㄴㅈ";
			case 'ㄶ' -> "ㄴㅎ";
			case 'ㄺ' -> "ㄹㄱ";
			case 'ㄻ' -> "ㄹㅁ";
			case 'ㄼ' -> "ㄹㅂ";
			case 'ㄽ' -> "ㄹㅅ";
			case 'ㄾ' -> "ㄹㅌ";
			case 'ㄿ' -> "ㄹㅍ";
			case 'ㅀ' -> "ㄹㅎ";
			case 'ㅄ' -> "ㅂㅅ";
			case 'ㅘ' -> "ㅗㅏ";
			case 'ㅙ' -> "ㅗㅐ";
			case 'ㅚ' -> "ㅗㅣ";
			case 'ㅝ' -> "ㅜㅓ";
			case 'ㅞ' -> "ㅜㅔ";
			case 'ㅟ' -> "ㅜㅣ";
			case 'ㅢ' -> "ㅡㅣ";
			default -> String.valueOf(c);
		};
	}

	/** 대상 글을 자모 열로(완성 글자는 초·중·종으로, 나머지는 소문자 그대로). */
	public static String jamo(String s) {
		if (s == null || s.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder(s.length() * 3);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 0xAC00 && c <= 0xD7A3) {
				int v = c - 0xAC00;
				int cho = v / (21 * 28);
				int jung = (v % (21 * 28)) / 28;
				int jong = v % 28;
				sb.append(split(CHO[cho])).append(split(JUNG[jung]));
				if (jong > 0) {
					sb.append(split(JONG[jong]));
				}
			} else if (c >= 0x3131 && c <= 0x3163) {
				sb.append(split(c));
			} else {
				sb.append(Character.toLowerCase(c));
			}
		}
		return sb.toString();
	}

	/** 검색어를 자모 열로: 영문 키는 두벌식 자리의 자모로 바꾼 뒤 {@link #jamo}. */
	public static String keysToJamo(String q) {
		if (q == null || q.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder(q.length());
		for (int i = 0; i < q.length(); i++) {
			char c = q.charAt(i);
			int k = KEYS.indexOf(c);
			sb.append(k >= 0 ? JAMO.charAt(k) : c);
		}
		return jamo(sb.toString());
	}

	/** 대상(이름·설명)이 검색어와 맞는가: 그대로 포함되거나, 자모 열로 포함되거나, 영타를 한글로 본 자모 열로 포함되거나. */
	public static boolean matches(String target, String query) {
		if (target == null || query == null || query.isEmpty()) {
			return false;
		}
		// 49-188차(사용자: "띄어쓰기 안 해도 인식되게"): 양쪽 다 띄어쓰기를 빼고 비교한다("아이템획득" = "아이템 획득")
		String t = target.toLowerCase().replaceAll("\\s+", "");
		String q = query.toLowerCase().replaceAll("\\s+", "");
		if (q.isEmpty()) {
			return false;
		}
		if (t.contains(q)) {
			return true;
		}
		String tj = jamo(t);
		String qj = jamo(q);
		if (tj.contains(qj)) {
			return true;
		}
		String kj = keysToJamo(q);
		return !kj.isEmpty() && !kj.equals(qj) && tj.contains(kj);
	}
}
