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
