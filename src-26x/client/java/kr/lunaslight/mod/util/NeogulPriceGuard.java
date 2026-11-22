package kr.lunaslight.mod.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-689차(사용자 사진: "[확성기] pang_cheetah: A급낚시코인 140에 사요" → 가이드가 "[전체] A급 낚시코인 최대 거래가격은 132만원입니다.
 * 위키 시세 확인 부탁드립니다." - "가이드 기능에 이런 시세 파괴도 넣어 줘"): 확성기, 전체 채팅의 사고팔기 글에서
 * 시세표(유저상점 시세 규정, {@link NeogulTradePoints#RULES})를 벗어난 값을 찾는다.
 *
 * <ul>
 *   <li>사요/삽니다/팝니다/구매/판매 같은 사고팔기 말이 있는 줄만.</li>
 *   <li>"/", ",", 줄바꿈으로 나눈 조각마다 아이템 이름(줄임말 포함) 뒤의 첫 값. "3개" 같은 개수는 건너뜀.</li>
 *   <li>값: "132만", "1.5억", "15만5천", "1,320,000원". 단위 없이 1만보다 작으면 만 단위로 본다("140" = 140만).</li>
 *   <li>요리용 석탄은 묶음(셋, 박스) 값으로 말하는 일이 많아 뺐다.</li>
 * </ul>
 */
public final class NeogulPriceGuard {
	private NeogulPriceGuard() {
	}

	/** 시세를 벗어난 글 하나. */
	public record Hit(String who, String item, long price, long limit, boolean over, String line) {
		/** 가이드가 붙여 넣을 안내 글. */
		public String reply() {
			return item + (over ? " 최대" : " 최소") + " 거래가격은 " + won(limit) + "입니다. 위키 시세 확인 부탁드립니다.";
		}
	}

	/** {규칙 key, 보이는 이름, 줄임말들(띄어쓰기 없이 소문자)}. 긴 이름이 먼저 걸리게 찾는다. */
	private static final String[][] ITEMS = {
		{"fish_coin_a", "A급 낚시코인", "a급낚시코인", "a급낚시코인", "a낚시코인", "낚시코인(a)", "낚시코인a", "a급낚코", "a낚코", "a급코인", "a코인", "에이코인"},
		{"vending_machine", "피로회복제 자판기", "피로회복제자판기", "피회자판기", "자판기"},
		{"potion_dungeon", "던전 피로회복제", "던전피로회복제", "던전피회", "던전회복제", "던피회", "던피"},
		{"potion_gather", "수확섬 피로회복제", "수확섬피로회복제", "수확피로회복제", "수확섬피회", "수확피회"},
		{"potion_meat", "정육섬 피로회복제", "정육섬피로회복제", "정육피로회복제", "정육섬피회", "정육피회"},
		{"potion_mud", "갯벌섬 피로회복제", "갯벌섬피로회복제", "갯벌피로회복제", "갯벌섬피회", "갯벌피회"},
		{"potion_sea", "해양섬 피로회복제", "해양섬피로회복제", "해양피로회복제", "해양섬피회", "해양피회"},
		{"potion_gather", "피로회복제", "피로회복제", "피회"},   // 섬 피로회복제 네 가지는 값이 같다
		{"fishing_bait_b", "고급 미끼 B형", "고급미끼b형", "고급미끼b", "b형미끼", "b미끼", "고급미끼"},
		{"s_fish", "S급 물고기", "s급물고기", "s급생선", "범고래", "상어", "소라고동"},
		{"a_fish", "A급 물고기", "a급물고기", "a급생선", "연어", "장어", "해파리", "랍스터", "참치"},
		{"extract_golden", "황금가루", "황금가루", "황금 가루"},
	};

	private static final Pattern TRADE = Pattern.compile("사요|삽니다|사겠|구매|구합니다|구해요|구함|매입|팝니다|팔아요|팔게요|팔아용|판매|파요|팝|삼|팜|ㅅㅅ|ㅍㅍ");
	private static final Pattern NUM = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)(억|만|천)?(원)?");
	private static final String NOT_PRICE = "개장명셋세쎗%분시배급번칸렙레강단일초층등위px";

	/** 채팅 한 줄(서버 그림 글자는 뺀 것)에서 시세를 벗어난 첫 곳. 없으면 null. */
	public static Hit check(String plain) {
		if (plain == null) {
			return null;
		}
		boolean loud = plain.contains("[확성기]") || plain.contains("[전체]");
		if (!loud) {
			return null;
		}
		int colon = plain.indexOf(": ");
		if (colon < 0) {
			return null;
		}
		String head = plain.substring(0, colon);
		String msg = plain.substring(colon + 2).trim();
		if (!TRADE.matcher(msg).find() || msg.contains("거래가격") || msg.contains("시세 확인")) {
			return null;
		}
		String[] words = head.trim().split("\\s+");
		String who = words.length == 0 ? "" : words[words.length - 1];
		for (String seg : msg.split("[/，|\\n]+|,(?!\\d)")) {
			Hit h = checkSegment(who, seg, plain);
			if (h != null) {
				return h;
			}
		}
		return null;
	}

	private static Hit checkSegment(String who, String seg, String line) {
		String t = seg.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
		if (t.isEmpty()) {
			return null;
		}
		boolean[] used = new boolean[t.length()];
		// 긴 줄임말부터
		List<String[]> found = new ArrayList<>();   // {key, 이름, 끝 자리}
		List<String[]> all = new ArrayList<>();
		for (String[] it : ITEMS) {
			for (int k = 2; k < it.length; k++) {
				all.add(new String[]{it[0], it[1], it[k].replace(" ", "")});
			}
		}
		all.sort((a, b) -> b[2].length() - a[2].length());
		for (String[] a : all) {
			for (int i = t.indexOf(a[2]); i >= 0; i = t.indexOf(a[2], i + 1)) {
				int e = i + a[2].length();
				boolean free = true;
				for (int j = i; j < e; j++) {
					if (used[j]) {
						free = false;
						break;
					}
				}
				if (!free) {
					continue;
				}
				for (int j = i; j < e; j++) {
					used[j] = true;
				}
				found.add(new String[]{a[0], a[1], Integer.toString(i), Integer.toString(e)});
			}
		}
		found.sort((a, b) -> Integer.parseInt(a[2]) - Integer.parseInt(b[2]));
		for (int f = 0; f < found.size(); f++) {
			String[] it = found.get(f);
			int from = Integer.parseInt(it[3]);
			int to = f + 1 < found.size() ? Integer.parseInt(found.get(f + 1)[2]) : t.length();
			long price = priceIn(t.substring(from, Math.max(from, Math.min(to, from + 24))));
			if (price <= 0) {
				continue;
			}
			long[] r = NeogulTradePoints.RULES.get(it[0]);
			if (r == null) {
				continue;
			}
			if (price > r[1] && price <= r[1] * 20) {
				return new Hit(who, it[1], price, r[1], true, line);
			}
			if (price < r[0] && price * 20 >= r[0]) {
				return new Hit(who, it[1], price, r[0], false, line);
			}
		}
		return null;
	}

	/** 이름 뒤 글에서 첫 값(원). 개수 같은 것은 건너뛴다. 없으면 0. */
	static long priceIn(String s) {
		Matcher m = NUM.matcher(s);
		while (m.find()) {
			int e = m.end();
			if (m.group(2) == null && m.group(3) == null && e < s.length() && NOT_PRICE.indexOf(Character.toLowerCase(s.charAt(e))) >= 0) {
				continue;   // 3개, 2셋, 10% …
			}
			if (m.start() > 0 && "x×*".indexOf(s.charAt(m.start() - 1)) >= 0) {
				continue;
			}
			double n;
			try {
				n = Double.parseDouble(m.group(1).replace(",", ""));
			} catch (NumberFormatException ex) {
				continue;
			}
			String unit = m.group(2);
			long v;
			if ("억".equals(unit)) {
				v = Math.round(n * 100_000_000L);
				Matcher m2 = Pattern.compile("^(\\d+)만").matcher(s.substring(e));
				if (m2.find()) {
					v += Long.parseLong(m2.group(1)) * 10_000L;
				}
			} else if ("만".equals(unit)) {
				v = Math.round(n * 10_000L);
				Matcher m2 = Pattern.compile("^(\\d+)천").matcher(s.substring(e));
				if (m2.find()) {
					v += Long.parseLong(m2.group(1)) * 1_000L;
				}
			} else if ("천".equals(unit)) {
				v = Math.round(n * 1_000L);
			} else {
				v = Math.round(n);
				if (m.group(3) == null && v < 10_000L) {
					v *= 10_000L;   // "140에 사요" = 140만
				}
			}
			return v;
		}
		return 0;
	}

	/** 1,320,000 → "132만원", 155,000 → "15만 5천원". */
	public static String won(long v) {
		if (v >= 10_000L && v % 10_000L == 0) {
			return String.format(Locale.ROOT, "%,d만원", v / 10_000L);
		}
		if (v >= 10_000L && v % 1_000L == 0) {
			return String.format(Locale.ROOT, "%,d만 %d천원", v / 10_000L, (v % 10_000L) / 1_000L);
		}
		return String.format(Locale.ROOT, "%,d원", v);
	}
}
