package kr.lunaslight.mod.module.impl.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.InfoSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-657차(사용자: "OO님의 [재료] 요리용 석탄 64개를 115,200원에 구매했습니다 / OO님과의 거래가 완료되었습니다 └ 1원 (-) - 이런 식으로
 * 돈이 빠져나간 걸 디스코드 채널에 올릴 수 있나", "내가 산 거야", "내 돈 쓰는 거 로그 디코"): <b>지출 기록</b>.
 *
 * <ul>
 *   <li>유저 상점에서 산 것: "판매자님의 [재료] 요리용 석탄 64개(을)를 110,000원에 구매했습니다."</li>
 *   <li>거래에서 낸 돈: "상대님과의 거래가 완료되었습니다." 바로 뒤 "└ 10,300,000원 (-)", 같이 온 "└ [특수재료] 잿빛 잉걸불 1개 (+)"는 받은 것.</li>
 * </ul>
 * 디스코드 채널 웹후크 주소(채널 편집 → 연동 → 웹후크 → 새 웹후크 → URL 복사)를 붙여 넣으면 그 채널에 올린다. 연달아 사면 3초 모아 한 번에,
 * 오늘(한국 시간) 쓴 돈 합계를 같이 적는다. 보내지 못한 건 1분 뒤 다시.
 */
public class NeogulSpendLogModule extends Module {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Pattern HOOK = Pattern.compile("https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d{5,25}/[A-Za-z0-9_-]{20,100}");
	private static final Pattern BUY = Pattern.compile("([A-Za-z0-9_]{2,16})님의\\s+(.+?)\\s*([\\d,]+)개(?:\\(을\\)를|을|를)?\\s*([\\d,]+)원에\\s*구매했습니다");
	private static final Pattern TRADE = Pattern.compile("([A-Za-z0-9_]{2,16})님과의\\s*거래가\\s*완료되었습니다");
	private static final Pattern TRADE_MONEY = Pattern.compile("└\\s*([\\d,]+)원\\s*\\(\\s*([+-])\\s*\\)");
	private static final Pattern TRADE_ITEM = Pattern.compile("└\\s*(.+?)\\s*([\\d,]+)개\\s*\\(\\s*([+-])\\s*\\)");
	private static final Pattern PLAYER_CHAT = Pattern.compile("[A-Za-z0-9_]{3,16}\\s*:\\s");
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private final StringSetting hook = register(new StringSetting("webhook", "웹후크", "", ""));
	/** 저장용: {"d":날짜,"t":오늘 쓴 돈,"n":오늘 건수}. */
	private final StringSetting state = register(new StringSetting("state", "상태", "", ""));

	private String day = "";
	private long total;
	private int count;
	private boolean loaded;
	private String note = "";

	/** 보낼 줄(모아서 한 번에). */
	private final List<String> queue = new ArrayList<>();
	private long flushAt;
	private long retryAt;
	private volatile boolean sending;

	/** 거래 완료 줄 뒤에 오는 └ 줄들. */
	private String tradeWith;
	private long tradeAt;
	private long tradePaid;
	private final List<String> tradeGot = new ArrayList<>();
	private final List<String> tradeGave = new ArrayList<>();

	public NeogulSpendLogModule() {
		super("neogul_spend_log", "지출 기록 (디스코드)", ModuleCategory.SERVER, "유저 상점에서 산 것, 거래로 낸 돈을 디스코드 채널에 올림");
		serverGroup("너굴마을");
		hook.hidden();
		state.hidden();
		register(new InfoSetting("status", "상태", this::statusText));
		register(new ActionSetting("paste", "디스코드 웹후크", "올릴 채널의 웹후크 주소를 복사한 뒤 누르세요(채널 편집 → 연동 → 웹후크 → URL 복사).",
				"붙여 넣기", this::pasteHook));
		register(new ActionSetting("test", "시험 보내기", "채널에 시험 글을 하나 올립니다.", "보내기", () -> {
			queue.add("✅ 지출 기록 연결됨");
			flushAt = 0;
		}));
		register(new ActionSetting("unlink", "연결 해제", "이 컴퓨터에서 웹후크 주소를 지웁니다.", "해제", () -> {
			hook.setValue("");
			note = "연결을 해제했어요";
			kr.lunaslight.mod.config.LunaClientConfig.save();
		}));
		ChatState.addLoggedListener(this::onChat);
	}

	// ==================== 상태 ====================

	private void load() {
		String t = LocalDate.now(KST).toString();
		if (!loaded) {
			loaded = true;
			try {
				String v = state.get();
				if (v != null && !v.isEmpty()) {
					@SuppressWarnings("deprecation")
					JsonObject o = new JsonParser().parse(v).getAsJsonObject();
					day = o.get("d").getAsString();
					total = o.get("t").getAsLong();
					count = o.get("n").getAsInt();
				}
			} catch (Throwable ignored) {
			}
		}
		if (!t.equals(day)) {
			day = t;
			total = 0;
			count = 0;
			save();
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		o.addProperty("d", day);
		o.addProperty("t", total);
		o.addProperty("n", count);
		state.setValue(o.toString());
	}

	private boolean linked() {
		String h = hook.get();
		return h != null && HOOK.matcher(h).matches();
	}

	private String statusText() {
		load();
		String s = (linked() ? "연결됨" : "웹후크 없음") + "  |  오늘 " + count + "건 " + won(total);
		return note == null || note.isEmpty() ? s : s + "  |  " + note;
	}

	private void pasteHook() {
		try {
			String clip = kr.lunaslight.mod.util.LunaInput.clipboard(net.minecraft.client.Minecraft.getInstance());
			Matcher m = HOOK.matcher(clip == null ? "" : clip.trim());
			if (!m.find()) {
				note = "클립보드에 웹후크 주소가 없어요";
				return;
			}
			hook.setValue(m.group());
			if (!isEnabledStored()) {
				setEnabled(true);
			}
			note = "연결했어요";
			queue.add("✅ 지출 기록 연결됨");
			flushAt = 0;
			kr.lunaslight.mod.config.LunaClientConfig.save();
		} catch (Throwable t) {
			LunaCompat.warnOnce("spendLog:paste", t);
			note = "붙여 넣지 못했어요";
		}
	}

	private static String won(long v) {
		return String.format("%,d원", v);
	}

	private static long num(String s) {
		try {
			return Long.parseLong(s.replace(",", ""));
		} catch (Throwable t) {
			return 0;
		}
	}

	/** 아이콘 글자(서버 팩 글리프) 빼기. */
	private static String clean(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || (cp >= 0xA000 && cp <= 0xA4CF)))
				.forEach(sb::appendCodePoint);
		return sb.toString().replaceAll("\\s+", " ").trim();
	}

	private static String hhmm() {
		LocalTime t = LocalTime.now(KST);
		return String.format("%02d:%02d", t.getHour(), t.getMinute());
	}

	// ==================== 감지 ====================

	private void onChat(String raw) {
		if (!isEnabled() || raw == null || raw.isEmpty() || !NeogulData.onNeogul(client)) {
			return;
		}
		String line = clean(raw.replaceFirst("^\\s*\\[\\d{1,2}:\\d{2}\\]\\s*", ""));
		if (line.startsWith("<") || PLAYER_CHAT.matcher(line).find()) {
			return;
		}
		load();
		long now = System.currentTimeMillis();
		// 거래: 완료 줄 뒤 3초 안의 └ 줄
		if (tradeWith != null && now - tradeAt < 3000 && line.startsWith("└")) {
			Matcher mm = TRADE_MONEY.matcher(line);
			if (mm.find()) {
				if ("-".equals(mm.group(2))) {
					tradePaid += num(mm.group(1));
				} else {
					tradeGot.add(mm.group(1) + "원");
				}
				return;
			}
			Matcher mi = TRADE_ITEM.matcher(line);
			if (mi.find()) {
				String it = mi.group(1).replaceAll("\\[[^\\]]*\\]", "").trim() + " " + mi.group(2) + "개";
				("+".equals(mi.group(3)) ? tradeGot : tradeGave).add(it);
				return;
			}
		}
		Matcher t = TRADE.matcher(line);
		if (t.find()) {
			endTrade();
			tradeWith = t.group(1);
			tradeAt = now;
			return;
		}
		Matcher b = BUY.matcher(line);
		if (b.find()) {
			long price = num(b.group(4));
			String item = b.group(2).replaceAll("\\[[^\\]]*\\]", "").trim();
			add(price, "🛒 `" + hhmm() + "` **" + item + " " + b.group(3) + "개** · " + b.group(1) + "님 상점 · **-" + won(price) + "**");
		}
	}

	/** 모은 거래 줄을 한 건으로. */
	private void endTrade() {
		if (tradeWith == null) {
			return;
		}
		if (tradePaid > 0) {
			StringBuilder sb = new StringBuilder("🤝 `" + hhmm() + "` " + tradeWith + "님과 거래 · **-" + won(tradePaid) + "**");
			if (!tradeGot.isEmpty()) {
				sb.append("\n　받은 것: ").append(String.join(", ", tradeGot));
			}
			if (!tradeGave.isEmpty()) {
				sb.append("\n　준 것: ").append(String.join(", ", tradeGave));
			}
			add(tradePaid, sb.toString());
		}
		tradeWith = null;
		tradePaid = 0;
		tradeGot.clear();
		tradeGave.clear();
	}

	private void add(long spent, String text) {
		total += spent;
		count++;
		save();
		kr.lunaslight.mod.config.LunaClientConfig.save();
		queue.add(text);
		flushAt = System.currentTimeMillis() + 3000;   // 연달아 사면 모아서 한 번에
	}

	// ==================== 보내기 ====================

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();
		if (tradeWith != null && now - tradeAt >= 3000) {
			endTrade();
		}
		if (queue.isEmpty() || sending || !linked() || now < flushAt || now < retryAt) {
			return;
		}
		load();
		List<String> batch = new ArrayList<>(queue.subList(0, Math.min(20, queue.size())));
		StringBuilder desc = new StringBuilder();
		for (String s : batch) {
			if (desc.length() + s.length() > 3800) {
				break;
			}
			desc.append(s).append('\n');
		}
		JsonObject embed = new JsonObject();
		embed.addProperty("description", desc.toString().trim());
		embed.addProperty("color", 0xF2A65A);
		JsonObject footer = new JsonObject();
		footer.addProperty("text", "오늘 쓴 돈 " + won(total) + " · " + count + "건 · " + LunaCompat.sessionName(client));
		embed.add("footer", footer);
		JsonArray embeds = new JsonArray();
		embeds.add(embed);
		JsonObject body = new JsonObject();
		body.addProperty("username", "지출 기록");
		body.add("embeds", embeds);
		JsonObject am = new JsonObject();
		am.add("parse", new JsonArray());
		body.add("allowed_mentions", am);
		final int n = batch.size();
		sending = true;
		HttpRequest req = HttpRequest.newBuilder(URI.create(hook.get()))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("User-Agent", "NovaMod-SpendLog/1")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, err) -> client.execute(() -> {
			sending = false;
			int st = r == null ? 0 : r.statusCode();
			if (err == null && st >= 200 && st < 300) {
				for (int i = 0; i < n && !queue.isEmpty(); i++) {
					queue.remove(0);
				}
				note = "보냄";
				retryAt = System.currentTimeMillis() + 1500;
			} else if (st == 401 || st == 404) {
				note = "웹후크 주소가 안 맞아요(다시 붙여 넣기)";
				queue.clear();
			} else {
				note = "보내기 실패, 1분 뒤 다시";
				retryAt = System.currentTimeMillis() + 60_000;
			}
		}));
	}
}
