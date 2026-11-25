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
 * 루나리스 봇 /지출연동(섬장만)이 만든 채널 "지출기록"의 웹후크 주소를 붙여 넣으면 그 채널에 올린다. 관리자 계정에서만 보인다. 연달아 사면 3초 모아 한 번에,
 * 오늘(한국 시간) 쓴 돈 합계를 같이 적는다. 보내지 못한 건 1분 뒤 다시.
 */
public class NeogulSpendLogModule extends Module {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Pattern HOOK = Pattern.compile("https://(?:canary\\.|ptb\\.)?discord(?:app)?\\.com/api/webhooks/\\d{5,25}/[A-Za-z0-9_-]{20,100}");
	private static final Pattern BUY = Pattern.compile("([A-Za-z0-9_]{2,16})님의\\s+(.+?)\\s*([\\d,]+)개(?:\\(을\\)를|을|를)?\\s*([\\d,]+)원에\\s*구매했습니다");
	private static final Pattern TRADE = Pattern.compile("([A-Za-z0-9_]{2,16})님과의\\s*거래가\\s*완료되었습니다");
	private static final Pattern TRADE_MONEY = Pattern.compile("└\\s*([\\d,]+)원\\s*\\(\\s*([+-])\\s*\\)");
	private static final Pattern TRADE_ITEM = Pattern.compile("└\\s*(.+?)\\s*([\\d,]+)개\\s*\\(\\s*([+-])\\s*\\)");
	/** 49-739차: 섬 은행 출금 - "섬 은행에서 출금하였습니다." 다음 줄 "└ 출금한 금액: 40,000,000원". */
	private static final Pattern BANK_OUT = Pattern.compile("섬\\s*은행에서\\s*출금하였습니다");
	private static final Pattern BANK_OUT_WON = Pattern.compile("출금한\\s*금액\\s*[:：]\\s*([\\d,]+)\\s*원");
	private long bankOutAt;
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
	private final List<JsonObject> queue = new ArrayList<>();
	private long flushAt;
	private long retryAt;
	private volatile boolean sending;

	/** 거래 완료 줄 뒤에 오는 └ 줄들. */
	private String tradeWith;
	private long tradeAt;
	private long tradePaid;
	private final JsonArray tradeGot = new JsonArray();
	private final JsonArray tradeGave = new JsonArray();
	/** 49-660차(사용자: "단시간 안에 여러 개 사면 중첩시키고 보내기"): 5초 안에 이어서 산 것은 한 장에(같은 물건, 판매자는 합침). */
	private JsonArray buyRows;
	private long buyAt;
	private long buyFirst;
	private static final long BUY_GAP_MS = 5000;

	private static NeogulSpendLogModule instance;

	/** 49-761차: 웹후크 주소가 없으면(섬원) 루나리스 연동으로 개인 지출을 보낸다. */
	private boolean viaLink() {
		return !linked() && LunarisLinkModule.canSpend();
	}

	/** LunarisLinkModule이 틱마다 부른다 - 이 기능이 꺼져 있어도 개인 지출은 모아서 보낸다. */
	public static void linkTick() {
		NeogulSpendLogModule m = instance;
		if (m != null && !m.isEnabled() && m.viaLink()) {
			m.tick0();
		}
	}

	/**
	 * 49-761차(사용자: "치장, 도구처럼 사진 같은 거 제외하고 뜨게, 우리 지출 채널도 마찬가지로" - 사진: [치장], [가구], [장비], [치장도구]):
	 * 꾸미기와 장비는 지출로 안 센다.
	 */
	private static boolean skipCat(String cat) {
		return cat != null && (cat.contains("치장") || cat.contains("가구") || cat.contains("장비"));
	}

	public NeogulSpendLogModule() {
		super("neogul_spend_log", "지출 기록 (디스코드)", ModuleCategory.SERVER, "유저 상점에서 산 것, 거래로 낸 돈을 디스코드에 올림 (섬원: /연동 하면 개인지출에 자동, 관리진: /지출연동 주소)");
		serverGroup("너굴마을");
		instance = this;
		hook.hidden();
		state.hidden();
		register(new InfoSetting("status", "상태", this::statusText));
		register(new ActionSetting("paste", "디스코드 웹후크", "디스코드에서 /지출연동 으로 받은 주소를 복사한 뒤 누르십시오.",
				"붙여 넣기", this::pasteHook));
		register(new ActionSetting("test", "시험 보내기", "채널에 시험 글을 하나 올립니다.", "보내기", () -> {
			queue.add(hello());
			flushAt = 0;
		}));
		register(new ActionSetting("unlink", "연결 해제", "이 컴퓨터에서 웹후크 주소를 지웁니다.", "해제", () -> {
			hook.setValue("");
			note = "연결을 해제했습니다";
			kr.lunaslight.mod.config.LunaClientConfig.save();
		}));
		ChatState.addLoggedListener(this::onChat);
	}

	// 49-739차(사용자: "지출목록에 관리진 전체 거래목록 뜨게 하고 은행 출금까지"): 섬장만 보이던 것을 모두에게 보인다.
	// 주소(웹후크)는 루나리스 /지출연동 을 섬장과 관리진만 받을 수 있어서, 주소를 받은 사람만 올릴 수 있다. 봇이 마크 이름으로 누가 보냈는지 찾는다.

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
		String s = (linked() ? "지출기록 채널 연결됨" : LunarisLinkModule.canSpend() ? "개인지출 자동 (루나리스 연동)" : "연결 안 됨") + "  |  오늘 " + count + "건 " + won(total);
		return note == null || note.isEmpty() ? s : s + "  |  " + note;
	}

	private void pasteHook() {
		try {
			String clip = kr.lunaslight.mod.util.LunaInput.clipboard(net.minecraft.client.Minecraft.getInstance());
			Matcher m = HOOK.matcher(clip == null ? "" : clip.trim());
			if (!m.find()) {
				note = "클립보드에 웹후크 주소가 없습니다";
				return;
			}
			hook.setValue(m.group());
			if (!isEnabledStored()) {
				setEnabled(true);
			}
			note = "연결했습니다";
			queue.add(hello());
			flushAt = 0;
			kr.lunaslight.mod.config.LunaClientConfig.save();
		} catch (Throwable t) {
			LunaCompat.warnOnce("spendLog:paste", t);
			note = "붙여 넣지 못했습니다";
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

	/**
	 * 49-660차(사용자: "좀 더 자세하고 깔끔하게, 너굴마을 리소스팩 이용해서 아이템에 적용"): 아이템 모양 id. 너굴 자료(이름 → nexo 모델)
	 * 먼저, 없으면 인벤토리에서 같은 이름 아이템의 모델(item_model 컴포넌트), 그것도 없으면 바닐라 id. 봇이 서버 팩/게임 jar에서 그림을 찾는다.
	 */
	private String modelOf(String name) {
		try {
			NeogulData.Entry e = NeogulData.byName(name);
			if (e != null) {
				if (e.model() != null && !e.model().isEmpty()) {
					return e.model();
				}
				if (e.icons() != null && !e.icons().isEmpty()) {
					return e.icons().get(0);
				}
			}
			String want = NeogulData.normalize(name);
			var inv = client.player == null ? null : client.player.getInventory();
			if (inv != null) {
				for (int i = 0; i < inv.getContainerSize(); i++) {
					net.minecraft.world.item.ItemStack st = inv.getItem(i);
					if (st.isEmpty() || !want.equals(NeogulData.normalize(st.getHoverName().getString()))) {
						continue;
					}
					Object m = st.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
					if (m != null) {
						return m.toString();
					}
					return LunaCompat.registryIdOf("ITEM", st.getItem());
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("spendLog:model", t);
		}
		return null;
	}

	private static String catOf(String raw) {
		Matcher m = Pattern.compile("\\[([^\\]]+)\\]").matcher(raw);
		return m.find() ? m.group(1) : "";
	}

	private JsonObject thing(String raw, long n) {
		JsonObject o = new JsonObject();
		String name = raw.replaceAll("\\[[^\\]]*\\]", "").trim();
		o.addProperty("name", name);
		o.addProperty("cat", catOf(raw));
		o.addProperty("n", n);
		String model = modelOf(name);
		if (model != null) {
			o.addProperty("model", model);
		}
		return o;
	}

	private void onChat(String raw) {
		if ((!isEnabled() && !viaLink()) || raw == null || raw.isEmpty() || !NeogulData.onNeogul(client)) {
			return;
		}
		String line = clean(raw.replaceFirst("^\\s*\\[\\d{1,2}:\\d{2}\\]\\s*", ""));
		if (line.startsWith("<") || PLAYER_CHAT.matcher(line).find()) {
			return;
		}
		load();
		long now = System.currentTimeMillis();
		// 49-739차: 섬 은행 출금(내 돈이 아니라 섬 돈이라 오늘 쓴 돈에는 안 더한다)
		if (BANK_OUT.matcher(line).find()) {
			bankOutAt = now;
			return;
		}
		if (bankOutAt > 0 && now - bankOutAt < 3000) {
			Matcher bo = BANK_OUT_WON.matcher(line);
			if (bo.find()) {
				bankOutAt = 0;
				JsonObject o = new JsonObject();
				o.addProperty("k", "bank");
				o.addProperty("t", hhmm());
				o.addProperty("won", num(bo.group(1)));
				queue.add(o);
				return;
			}
		}
		// 거래: 완료 줄 뒤 3초 안의 └ 줄
		if (tradeWith != null && now - tradeAt < 3000 && line.startsWith("└")) {
			Matcher mm = TRADE_MONEY.matcher(line);
			if (mm.find()) {
				if ("-".equals(mm.group(2))) {
					tradePaid += num(mm.group(1));
				} else {
					JsonObject o = new JsonObject();
					o.addProperty("name", "돈");
					o.addProperty("won", num(mm.group(1)));
					tradeGot.add(o);
				}
				return;
			}
			Matcher mi = TRADE_ITEM.matcher(line);
			if (mi.find()) {
				("+".equals(mi.group(3)) ? tradeGot : tradeGave).add(thing(mi.group(1), num(mi.group(2))));
				return;
			}
		}
		Matcher t = TRADE.matcher(line);
		if (t.find()) {
			endBuys();
			endTrade();
			tradeWith = t.group(1);
			tradeAt = now;
			return;
		}
		Matcher b = BUY.matcher(line);
		if (b.find()) {
			if (skipCat(catOf(b.group(2)))) {
				return;   // 49-761차: 치장, 가구, 장비는 지출로 안 센다
			}
			long price = num(b.group(4));
			long n = num(b.group(3));
			JsonObject row = thing(b.group(2), n);
			row.addProperty("who", b.group(1));
			row.addProperty("won", price);
			row.addProperty("times", 1);
			if (buyRows == null) {
				buyRows = new JsonArray();
				buyFirst = now;
			}
			// 같은 물건을 같은 판매자에게서 또 샀으면 한 줄로 합침
			boolean merged = false;
			for (var e : buyRows) {
				JsonObject r = e.getAsJsonObject();
				if (r.get("name").getAsString().equals(row.get("name").getAsString()) && r.get("who").getAsString().equals(b.group(1))) {
					r.addProperty("n", r.get("n").getAsLong() + n);
					r.addProperty("won", r.get("won").getAsLong() + price);
					r.addProperty("times", r.get("times").getAsInt() + 1);
					merged = true;
					break;
				}
			}
			if (!merged) {
				buyRows.add(row);
			}
			buyAt = now;
			spent(price);
		}
	}

	private void spent(long won) {
		total += won;
		count++;
		save();
		kr.lunaslight.mod.config.LunaClientConfig.save();
	}

	/** 모아 둔 구매를 카드 한 장으로. */
	private void endBuys() {
		if (buyRows == null || buyRows.isEmpty()) {
			buyRows = null;
			return;
		}
		JsonObject card = new JsonObject();
		card.addProperty("k", "buy");
		card.addProperty("t", hhmm());
		long sum = 0;
		for (var e : buyRows) {
			sum += e.getAsJsonObject().get("won").getAsLong();
		}
		card.addProperty("won", sum);
		card.add("rows", buyRows);
		queue.add(card);
		buyRows = null;
	}

	/** 모은 거래 줄을 카드 한 장으로(거래 하나 = 한 장). */
	private void endTrade() {
		if (tradeWith == null) {
			return;
		}
		boolean deco = !tradeGot.isEmpty();
		for (var g : tradeGot) {
			JsonObject go = g.getAsJsonObject();
			if (!go.has("cat") || !skipCat(go.get("cat").getAsString())) {
				deco = false;
			}
		}
		if (tradePaid > 0 && !deco) {
			JsonObject o = new JsonObject();
			o.addProperty("k", "trade");
			o.addProperty("t", hhmm());
			o.addProperty("who", tradeWith);
			o.addProperty("won", tradePaid);
			o.add("got", tradeGot.deepCopy());
			o.add("gave", tradeGave.deepCopy());
			spent(tradePaid);
			queue.add(o);
		}
		tradeWith = null;
		tradePaid = 0;
		while (!tradeGot.isEmpty()) {
			tradeGot.remove(0);
		}
		while (!tradeGave.isEmpty()) {
			tradeGave.remove(0);
		}
	}

	private static JsonObject hello() {
		JsonObject o = new JsonObject();
		o.addProperty("k", "hello");
		o.addProperty("t", hhmm());
		return o;
	}

	// ==================== 보내기 ====================

	@Override
	public void onTick() {
		tick0();
	}

	private void tick0() {
		long now = System.currentTimeMillis();
		if (tradeWith != null && now - tradeAt >= 3000) {
			endTrade();
		}
		if (buyRows != null && (now - buyAt >= BUY_GAP_MS || now - buyFirst >= 60_000)) {
			endBuys();
		}
		if (!queue.isEmpty() && !linked() && LunarisLinkModule.canSpend()) {
			// 49-761차: 섬원은 연동으로(개인지출). 시험 글과 섬 은행 출금은 개인 지출이 아니라 버린다. 글 길이(2000자)를 넘지 않게 줄을 줄인다.
			JsonObject c0 = queue.remove(0);
			String k0 = c0.has("k") ? c0.get("k").getAsString() : "";
			if ("buy".equals(k0) || "trade".equals(k0)) {
				for (String key : new String[]{"rows", "got", "gave"}) {
					JsonArray arr = c0.has(key) ? c0.getAsJsonArray(key) : null;
					while (arr != null && arr.size() > 1 && c0.toString().length() > 1500) {
						arr.remove(arr.size() - 1);
						c0.addProperty("more", (c0.has("more") ? c0.get("more").getAsInt() : 0) + 1);
					}
				}
				if (c0.toString().length() <= 1700) {
					LunarisLinkModule.addSpend(c0);
				}
			}
			return;
		}
		if (queue.isEmpty() || sending || !linked() || now < flushAt || now < retryAt) {
			return;
		}
		load();
		// 49-660차(사용자: "한 거래마다 한 사진에"): 카드 한 장 = 웹후크 글 하나. 봇이 사진으로 바꿈.
		JsonObject card = queue.get(0);
		JsonObject pay = new JsonObject();
		pay.addProperty("spend", 2);
		pay.addProperty("mc", LunaCompat.sessionName(client));
		pay.addProperty("d", day);
		pay.addProperty("total", total);
		pay.addProperty("cnt", count);
		pay.add("card", card);
		String content = pay.toString();
		if (content.length() > 1990) {
			// 너무 길면(아주 여러 가지를 한꺼번에 산 경우) 줄을 줄여서
			JsonArray rows = card.has("rows") ? card.getAsJsonArray("rows") : null;
			while (rows != null && rows.size() > 1 && pay.toString().length() > 1990) {
				rows.remove(rows.size() - 1);
				card.addProperty("more", (card.has("more") ? card.get("more").getAsInt() : 0) + 1);
			}
			content = pay.toString();
		}
		JsonObject body = new JsonObject();
		body.addProperty("username", "지출 기록");
		body.addProperty("content", content);
		JsonObject am = new JsonObject();
		am.add("parse", new JsonArray());
		body.add("allowed_mentions", am);
		sending = true;
		HttpRequest req = HttpRequest.newBuilder(URI.create(hook.get()))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("User-Agent", "NovaMod-SpendLog/2")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, err) -> client.execute(() -> {
			sending = false;
			int st = r == null ? 0 : r.statusCode();
			if (err == null && st >= 200 && st < 300) {
				if (!queue.isEmpty() && queue.get(0) == card) {
					queue.remove(0);
				}
				note = "보냄";
				retryAt = System.currentTimeMillis() + 1500;
			} else if (st == 401 || st == 404) {
				note = "웹후크 주소가 안 맞습니다(다시 붙여 넣기)";
				queue.clear();
			} else {
				note = "보내기 실패, 1분 뒤 다시";
				retryAt = System.currentTimeMillis() + 60_000;
			}
		}));
	}
}
