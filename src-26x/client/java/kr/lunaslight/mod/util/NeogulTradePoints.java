package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 49-572차(사용자: "무역 점수도 너굴 아이템에 넣어 줘"): <b>너굴 무역 포인트</b>.
 *
 * <p>위키(무역 > 무역물품)의 무역 포인트는 매일 00시 요리 가격에 따라 바뀐다 - 위키 페이지가 쓰는 공개 API
 * (https://mcng.kr/api/v1/public/cook/prices, 요리 이름 + price + tenths(0.1% 단위 등락))를 너굴마을에 들어갈 때 한 번, 그 뒤 날짜가 바뀌면 다시 읽는다
 * (다른 스레드). 맛의 기원 (海) 3,800,000P, (地) 2,400,000P는 고정값(위키 11-06). 아이템 설명 맨 아래에 "무역 포인트 15,120P (-4.2%)"를 붙인다.
 */
public final class NeogulTradePoints {
	private NeogulTradePoints() {
	}

	private static final String API = "https://mcng.kr/api/v1/public/cook/prices";
	/**
	 * 49-588차(11/07 패치: 일부 품목 시세 적용 - 유저상점은 시세 범위 안 가격으로만 등록): 위키 시세표가 쓰는 공개 API
	 * {rules: [{key, name, min, max}]}. S급/A급 물고기 규칙은 그 등급 물고기 전부에 건다. 아이템 설명에 "유저상점 시세 a ~ b원".
	 */
	private static final String MARKET_API = "https://mcng.kr/api/v1/public/market/prices";
	private static final Map<String, long[]> MARKET = new ConcurrentHashMap<>();   // 이름(띄어쓰기 없앰) → {min, max}
	/** 49-689차: 시세 규칙 key(fish_coin_a 등) → {min, max}(시세 파괴 알림용). 처음엔 2026-11-21 API 값. */
	public static final Map<String, long[]> RULES = new ConcurrentHashMap<>(Map.ofEntries(
			Map.entry("a_fish", new long[]{105000, 220000}), Map.entry("extract_golden", new long[]{60000, 100000}),
			Map.entry("fish_coin_a", new long[]{630000, 1320000}), Map.entry("fishing_bait_b", new long[]{155000, 330000}),
			Map.entry("potion_dungeon", new long[]{2500000, 5000000}), Map.entry("potion_gather", new long[]{800000, 1500000}),
			Map.entry("potion_meat", new long[]{800000, 1500000}), Map.entry("potion_mud", new long[]{800000, 1500000}),
			Map.entry("potion_sea", new long[]{800000, 1500000}), Map.entry("s_fish", new long[]{1000000, 1500000}),
			Map.entry("vending_machine", new long[]{1000000, 2000000})));
	private static final Map<String, long[]> POINTS = new ConcurrentHashMap<>();   // 이름(띄어쓰기 없앰) → {포인트, tenths}
	private static volatile String date = "";
	private static volatile long fetchedDay = -1;
	private static volatile boolean fetching;
	private static boolean registered;

	static {
		POINTS.put(norm("맛의 기원 (海)"), new long[]{3_800_000L, Long.MIN_VALUE});
		POINTS.put(norm("맛의 기원 (地)"), new long[]{2_400_000L, Long.MIN_VALUE});
	}

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		LunaCompat.registerTooltipCallback(() -> {
			Minecraft mc = Minecraft.getInstance();
			return mc != null && mc.player != null && NeogulData.onNeogul(mc);
		}, NeogulTradePoints::addLine);
		try {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> refreshIfStale());
		} catch (Throwable t) {
			LunaCompat.warnOnce("trade:init", t);
		}
	}

	private static String norm(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || Character.isWhitespace(cp)))
			.forEach(sb::appendCodePoint);
		String t = sb.toString().replaceAll("§.", "");
		return t.startsWith("[요리]") ? t.substring(4) : t;
	}

	/** 날짜(한국 시각)가 바뀌었으면 다시 받는다. */
	public static void refreshIfStale() {
		long day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).toEpochDay();
		if (fetching || day == fetchedDay || !NeogulData.onNeogul(Minecraft.getInstance())) {
			return;
		}
		fetching = true;
		Thread th = new Thread(() -> {
			try {
				java.net.http.HttpClient hc = java.net.http.HttpClient.newBuilder()
					.connectTimeout(java.time.Duration.ofSeconds(8)).build();
				java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(API))
					.timeout(java.time.Duration.ofSeconds(10)).header("User-Agent", "NovaClient").GET().build();
				String body = hc.send(req, java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8)).body();
				JsonObject root = JsonParser.parseString(body).getAsJsonObject();
				JsonObject data = root.getAsJsonObject("data");
				int n = 0;
				for (JsonElement e : data.getAsJsonArray("items")) {
					JsonObject it = e.getAsJsonObject();
					if (!it.has("name") || !it.has("price")) {
						continue;
					}
					long tenths = it.has("tenths") ? it.get("tenths").getAsLong() : Long.MIN_VALUE;
					POINTS.put(norm(it.get("name").getAsString()), new long[]{it.get("price").getAsLong(), tenths});
					n++;
				}
				date = data.has("date") ? data.get("date").getAsString() : "";
				fetchedDay = day;
				try {
					fetchMarket(hc);
				} catch (Throwable t2) {
					LunaCompat.warnOnce("trade:market", t2);
				}
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 너굴 무역 포인트 {}개 받음({})", n, date);
			} catch (Throwable t) {
				LunaCompat.warnOnce("trade:fetch", t);
			} finally {
				fetching = false;
			}
		}, "Nova-NeogulTrade");
		th.setDaemon(true);
		th.start();
	}

	private static void fetchMarket(java.net.http.HttpClient hc) throws Exception {
		java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(MARKET_API))
			.timeout(java.time.Duration.ofSeconds(10)).header("User-Agent", "NovaClient").GET().build();
		String body = hc.send(req, java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8)).body();
		JsonObject data = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("data");
		Map<String, long[]> next = new java.util.HashMap<>();
		for (JsonElement e : data.getAsJsonArray("rules")) {
			JsonObject r = e.getAsJsonObject();
			if (!r.has("min") || !r.has("max")) {
				continue;
			}
			long[] v = {r.get("min").getAsLong(), r.get("max").getAsLong()};
			String key = r.has("key") ? r.get("key").getAsString() : "";
			if (!key.isEmpty()) {
				RULES.put(key, v);
			}
			String grade = "s_fish".equals(key) ? "S급" : "a_fish".equals(key) ? "A급" : null;
			if (grade != null) {
				for (NeogulData.Entry it : NeogulData.items()) {
					if (!"fish".equals(it.cat())) {
						continue;
					}
					for (NeogulData.Source src : it.from()) {
						if (src.note() != null && src.note().startsWith(grade)) {
							next.put(norm(it.name()), v);
						}
					}
				}
			} else if (r.has("name")) {
				next.put(norm(r.get("name").getAsString()), v);
			}
		}
		MARKET.clear();
		MARKET.putAll(next);
		kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 너굴 시세 {}개 받음", next.size());
	}

	/** 이 아이템의 유저상점 시세 {min, max}(시세표에 없으면 null). */
	public static long[] market(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		long[] m = MARKET.get(norm(stack.getHoverName().getString()));
		if (m == null) {
			NeogulData.Entry e = NeogulData.get(NeogulData.keyOf(stack));
			if (e != null) {
				m = MARKET.get(norm(e.name()));
			}
		}
		return m;
	}

	/** 이 아이템의 무역 포인트 {포인트, tenths}(모르면 null). */
	public static long[] of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		return POINTS.get(norm(stack.getHoverName().getString()));
	}

	public static String text(long[] p) {
		String s = String.format(java.util.Locale.ROOT, "무역 포인트 %,dP", p[0]);
		if (p[1] != Long.MIN_VALUE) {
			s += String.format(java.util.Locale.ROOT, " (%s%.1f%%)", p[1] > 0 ? "+" : "", p[1] / 10.0);
		}
		return s;
	}

	private static void addLine(ItemStack stack, List<Component> lines) {
		try {
			refreshIfStale();
			long[] mk = market(stack);
			if (mk != null) {
				lines.add(Component.literal(String.format(java.util.Locale.ROOT, "유저상점 시세 %,d ~ %,d원", mk[0], mk[1])).withColor(0x8FD3FF));
			}
			long[] p = of(stack);
			if (p == null) {
				return;
			}
			int color = p[1] == Long.MIN_VALUE || p[1] == 0 ? 0xFFE3B55A : (p[1] > 0 ? 0xFF7ED957 : 0xFFE0645A);
			lines.add(Component.literal(text(p)).withColor(color & 0xFFFFFF));
		} catch (Throwable t) {
			LunaCompat.warnOnce("trade:tooltip", t);
		}
	}
}
