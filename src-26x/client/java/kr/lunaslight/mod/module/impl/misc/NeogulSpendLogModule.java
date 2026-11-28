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
	// 49-801차(사용자: "블록 산 값(값만 뜨게), 모든 거래 품목, 모든 돈 보내기를 통한 금액(송금), 도토리 구매와 관련된 글, 유저 상점 구매들 전부전부 뜨게"):
	//  서버 상점(블록): "매끄러운 석영 블록 64개를 9,600원에 구매하였습니다. (잔고: 8,278,147원)" → 금액과 횟수만 모아 {k:"block"}
	//  송금: "comedu_H님에게 6,220,800원을 송금했습니다. (남은 잔고: …)" → {k:"send"}
	//  도토리 구매 등록: "1,000도토리 구매를 도토리 1개당 1,800원에 등록했습니다. (총 1,800,000원 차감)" → {k:"acorn"}
	//  도토리 상점: "[강화] 행운의 클로버(다이아) 1개를 2,990도토리에 구매하였습니다. (잔고: …)" → {k:"ashop", cost, cur 도토리|캐시}
	// 빼는 것은 이제 모드에서 고르지 않는다(치장, 가구, 장비도 보냄) - 무엇을 올릴지는 봇이 정한다.
	private static final Pattern BLOCK = Pattern.compile("^(.+?)\\s*([\\d,]+)개를\\s*([\\d,]+)원에\\s*구매하였습니다\\.?\\s*\\(잔고");
	private static final Pattern SEND = Pattern.compile("^([A-Za-z0-9_]{2,16})님에게\\s*([\\d,]+)원을\\s*송금했습니다");
	private static final Pattern ACORN = Pattern.compile("^([\\d,]+)도토리\\s*구매를\\s*도토리\\s*1개당\\s*([\\d,]+)원에\\s*등록했습니다\\.?\\s*\\(총\\s*([\\d,]+)원\\s*차감");
	private static final Pattern ASHOP = Pattern.compile("^(\\[.+?\\]\\s*.+?)\\s*([\\d,]+)개를\\s*([\\d,]+)(도토리|캐시)에\\s*구매하였습니다");
	private long blockWon, blockAt, blockFirst;
	private int blockN;

	private static JsonObject miscCard(String k, String t) {
		JsonObject o = new JsonObject();
		o.addProperty("k", k);
		o.addProperty("t", t);
		return o;
	}

	/** 줄이 블록 구매, 송금, 도토리 구매, 도토리 상점 구매면 {종류, 카드}(블록은 카드의 won, n이 한 번치). 아니면 null. */
	private JsonObject miscOf(String line, String t) {
		Matcher m = ASHOP.matcher(line);
		if (m.find()) {
			JsonObject o = miscCard("ashop", t);
			JsonObject th = thing(m.group(1), num(m.group(2)));
			for (var e : th.entrySet()) {
				o.add(e.getKey(), e.getValue());
			}
			o.addProperty("cost", num(m.group(3)));
			o.addProperty("cur", m.group(4));
			return o;
		}
		m = BLOCK.matcher(line);
		if (m.find()) {
			JsonObject o = miscCard("block", t);
			o.addProperty("won", num(m.group(3)));
			o.addProperty("n", 1);
			return o;
		}
		m = SEND.matcher(line);
		if (m.find()) {
			JsonObject o = miscCard("send", t);
			o.addProperty("who", m.group(1));
			o.addProperty("won", num(m.group(2)));
			return o;
		}
		m = ACORN.matcher(line);
		if (m.find()) {
			JsonObject o = miscCard("acorn", t);
			o.addProperty("n", num(m.group(1)));
			o.addProperty("each", num(m.group(2)));
			o.addProperty("won", num(m.group(3)));
			return o;
		}
		return null;
	}

	private void endBlocks() {
		if (blockN <= 0) {
			return;
		}
		java.time.LocalTime bt = java.time.Instant.ofEpochMilli(blockFirst).atZone(KST).toLocalTime();
		JsonObject o = miscCard("block", String.format("%02d:%02d", bt.getHour(), bt.getMinute()));
		o.addProperty("won", blockWon);
		o.addProperty("n", blockN);
		queue.add(o);
		blockWon = 0;
		blockN = 0;
		blockFirst = 0;
	}
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
	// 49-792차(사용자 사진: 같은 상점에서 1분 사이 산 두 가지가 카드 두 장 - "하나로 묶는 시간을 대폭 늘려 줘야"): 5초 → 3분.
	// 마지막 구매 뒤 3분 동안 더 안 사면 한 장으로 올린다. 계속 사도 15분이 되거나 줄이 많아지면(글 길이) 끊고, 서버에서 나가면 바로 올린다.
	private static final long BUY_GAP_MS = 180_000;
	private static final long BUY_MAX_MS = 900_000;

	private static NeogulSpendLogModule instance;

	// ==================== 49-765차: 지난 지출(11/22부터) ====================
	// 사용자: "이전 지출도 다 포함해 줘, 22일 이후 로그 확인해서". 게임 기록(logs/날짜-N.log.gz, 이번 실행의 latest.log는 빼고)에서
	// 구매, 거래 줄을 읽어 하루치 구매는 카드 몇 장으로 묶고 거래는 한 장씩, 루나리스 연동으로 천천히 보낸다(한 번만).
	// 봇은 같은 키를 두 번 세지 않고, 실시간으로 세기 시작한 때 뒤의 것은 버린다.
	private static final java.time.LocalDate HIST_FROM = java.time.LocalDate.of(2026, 10, 31);   // 49-778차(사용자: "10월 31일 이후로 바꿔 줘 기록을")
	private static final Pattern LOG_TIME = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})\\]");
	private static final Pattern HIST_BANK_IN = Pattern.compile("입금액\\s*[:：]\\s*([\\d,]+)\\s*원");
	/** 49-780차(사용자: "누적 후원 금액도 확인 가능해?"): "/후원"을 치면 뜨는 "- 닉네임님의 누적 후원금액은 350,000원입니다." */
	private static final Pattern DONATE = Pattern.compile("([A-Za-z0-9_]{2,16})님의\\s*누적\\s*후원금액은\\s*([\\d,]+)\\s*원");
	private static final Pattern LOG_GZ = Pattern.compile("\\d{4}-\\d{2}-\\d{2}-\\d+\\.log\\.gz");
	private final StringSetting histDone = register(new StringSetting("hist1031e", "지난 지출", "", ""));   // 49-788차: 봇의 빼는 규칙(도구 낀 거래, 섬원끼리 거래)이 바뀌어 한 번 더 읽어 보낸다(id와 키 "h6|")
	private volatile boolean histRunning;
	private volatile java.util.List<Object[]> histPending;   // {card, hd, ts, key}
	private long histAt;

	private void histTick(long now) {
		if (client.player == null || !NeogulData.onNeogul(client) || !LunarisLinkModule.canSpend() || "1".equals(histDone.get())) {
			return;
		}
		if (histAt == 0) {
			histAt = now + 60_000;
			return;
		}
		if (now < histAt) {
			return;
		}
		java.util.List<Object[]> pend = histPending;
		if (pend != null) {
			if (pend.isEmpty()) {
				histDone.setValue("1");
				histPending = null;
				kr.lunaslight.mod.config.LunaClientConfig.save();
				return;
			}
			Object[] o = pend.get(0);
			if (LunarisLinkModule.addSpendHist((JsonObject) o[0], (String) o[1], (Long) o[2], (String) o[3])) {
				pend.remove(0);
			}
			histAt = now + 2000;
			return;
		}
		if (histRunning) {
			return;
		}
		histRunning = true;
		Thread th = new Thread(() -> {
			java.util.List<Object[]> out = new ArrayList<>();
			try {
				out = histScan();
			} catch (Throwable t) {
				LunaCompat.warnOnce("spendhist:scan", t);
			}
			java.util.List<Object[]> res = out;
			client.execute(() -> {
				histPending = res;
				histRunning = false;
			});
		}, "Nova-SpendHistory");
		th.setDaemon(true);
		th.start();
	}

	/** 기록 파일 하나에서 읽은 지출 한 건. */
	private static final class Hit {
		int wrap, sec;
		boolean trade;
		int bank;   // 49-778차: 2 = 섬 은행 출금, 3 = 섬 은행 입금
		String who, raw;
		JsonObject misc;   // 49-801차: 블록, 송금, 도토리 구매, 도토리 상점
		long n, won;
		final JsonArray got = new JsonArray(), gave = new JsonArray();
	}

	private java.util.List<Object[]> histScan() throws java.io.IOException {
		java.util.List<Object[]> out = new ArrayList<>();
		final String me = String.valueOf(LunaCompat.sessionName(client));
		long donateTs = 0, donateWon = -1;
		java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs");
		if (!java.nio.file.Files.isDirectory(dir)) {
			return out;
		}
		ZoneId zone = ZoneId.systemDefault();
		java.util.List<java.nio.file.Path> files = new ArrayList<>();
		try (var ls = java.nio.file.Files.list(dir)) {
			// 49-778차: 이번 실행의 latest.log도 읽는다(봇이 기록을 비운 때보다 앞의 것만 받으므로 실시간 것과 겹치지 않는다)
			ls.filter(f -> LOG_GZ.matcher(f.getFileName().toString()).matches() || f.getFileName().toString().equals("latest.log")).forEach(files::add);
		}
		// 49-812차(사용자: "노바 클라이언트, 너굴라이언트 옮겨 다녀서 지금 너굴라이언트 집계만 된다, 노바 것 너굴로 로그 옮길 수 있나?"):
		// 다른 런처로 한 기록도 같이 읽는다 - %APPDATA%\Nugulient\minecraft\logs, %APPDATA%\NovaClient\profiles\*\logs 중 지금 폴더가 아닌 것.
		// 그쪽 파일은 너굴마을에 접속한 기록("mcng"가 보이는 파일)만 쓴다(다른 서버 프로필이 섞이지 않게).
		final java.util.Set<java.nio.file.Path> foreign = new java.util.HashSet<>();
		try {
			String ad = System.getenv("APPDATA");
			if (ad != null && !ad.isEmpty()) {
				java.util.List<java.nio.file.Path> dirs = new ArrayList<>();
				dirs.add(java.nio.file.Path.of(ad, "Nugulient", "minecraft", "logs"));
				java.nio.file.Path profs = java.nio.file.Path.of(ad, "NovaClient", "profiles");
				if (java.nio.file.Files.isDirectory(profs)) {
					try (var ps = java.nio.file.Files.list(profs)) {
						ps.filter(java.nio.file.Files::isDirectory).forEach(x -> dirs.add(x.resolve("logs")));
					}
				}
				java.nio.file.Path here = dir.toRealPath();
				for (java.nio.file.Path od : dirs) {
					if (!java.nio.file.Files.isDirectory(od) || od.toRealPath().equals(here)) {
						continue;
					}
					try (var ls = java.nio.file.Files.list(od)) {
						ls.filter(f -> LOG_GZ.matcher(f.getFileName().toString()).matches() || f.getFileName().toString().equals("latest.log")).forEach(f -> {
							files.add(f);
							foreign.add(f);
						});
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("spendhist:others", t);
		}
		// 날짜 → 구매 줄, 거래
		java.util.TreeMap<String, java.util.List<Object[]>> days = new java.util.TreeMap<>();   // {ts, Hit}
		for (java.nio.file.Path f : files) {
			try {
				long mtime = java.nio.file.Files.getLastModifiedTime(f).toMillis();
				java.time.LocalDate end = java.time.Instant.ofEpochMilli(mtime - 120_000).atZone(zone).toLocalDate();
				if (end.isBefore(HIST_FROM) || java.nio.file.Files.size(f) > 300L * 1024 * 1024) {
					continue;
				}
				java.util.List<Hit> hits = new ArrayList<>();
				boolean sawNeogul = !foreign.contains(f);
				int wraps = 0, prev = -1, sec = 0;
				Hit trade = null;
				boolean pendOut = false, pendIn = false;
				boolean gz = f.getFileName().toString().endsWith(".gz");
				try (java.io.InputStream in0 = java.nio.file.Files.newInputStream(f);
						java.io.InputStream in = gz ? new java.util.zip.GZIPInputStream(in0) : in0;
						java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
					String line;
					while ((line = r.readLine()) != null) {
						if (!sawNeogul && line.contains("mcng")) {
							sawNeogul = true;
						}
						Matcher tm = LOG_TIME.matcher(line);
						String text;
						if (tm.find()) {
							sec = Integer.parseInt(tm.group(1)) * 3600 + Integer.parseInt(tm.group(2)) * 60 + Integer.parseInt(tm.group(3));
							if (prev >= 0 && sec < prev - 3600) {
								wraps++;
							}
							prev = sec;
							int c = line.indexOf("[CHAT]");
							if (c < 0) {
								trade = null;
								continue;
							}
							text = line.substring(c + 6);
						} else {
							text = line;   // 여러 줄 글의 이어지는 줄
						}
						text = clean(text.replaceFirst("^\\s*\\[\\d{1,2}:\\d{2}\\]\\s*", ""));
						// 49-778차(사용자: "은행 입금, 은행 출금 넣어야 하고"): "섬 은행에서 출금하였습니다." → "└ 출금한 금액: N원",
						// "…입금되었습니다." → "└ 입금액: N원"
						if (text.startsWith("└")) {
							if (pendOut) {
								Matcher bo = BANK_OUT_WON.matcher(text);
								if (bo.find()) {
									pendOut = false;
									Hit h = new Hit();
									h.bank = 2;
									h.wrap = wraps;
									h.sec = sec;
									h.won = num(bo.group(1));
									hits.add(h);
									continue;
								}
							}
							if (pendIn) {
								Matcher bi = HIST_BANK_IN.matcher(text);
								if (bi.find()) {
									pendIn = false;
									Hit h = new Hit();
									h.bank = 3;
									h.wrap = wraps;
									h.sec = sec;
									h.won = num(bi.group(1));
									hits.add(h);
									continue;
								}
							}
						} else {
							pendOut = false;
							pendIn = false;
						}
						if (trade != null && text.startsWith("└")) {
							Matcher mm = TRADE_MONEY.matcher(text);
							if (mm.find()) {
								if ("-".equals(mm.group(2))) {
									trade.won += num(mm.group(1));
								} else {
									JsonObject o = new JsonObject();
									o.addProperty("name", "돈");
									o.addProperty("won", num(mm.group(1)));
									trade.got.add(o);
								}
								continue;
							}
							Matcher mi = TRADE_ITEM.matcher(text);
							if (mi.find()) {
								("+".equals(mi.group(3)) ? trade.got : trade.gave).add(thing(mi.group(1), num(mi.group(2))));
							}
							continue;
						}
						trade = null;
						if (text.startsWith("<") || PLAYER_CHAT.matcher(text).find()) {
							continue;
						}
						Matcher dn = DONATE.matcher(text);
						if (dn.find()) {
							if (dn.group(1).equalsIgnoreCase(me)) {
								Hit h = new Hit();
								h.bank = 4;   // 누적 후원금액(마지막 값만 쓴다)
								h.wrap = wraps;
								h.sec = sec;
								h.won = num(dn.group(2));
								hits.add(h);
							}
							continue;
						}
						if (BANK_OUT.matcher(text).find()) {
							pendOut = true;
							continue;
						}
						if (text.endsWith("입금되었습니다.") || text.endsWith("입금되었습니다")) {
							pendIn = true;
							continue;
						}
						Matcher t = TRADE.matcher(text);
						if (t.find()) {
							trade = new Hit();
							trade.trade = true;
							trade.wrap = wraps;
							trade.sec = sec;
							trade.who = t.group(1);
							hits.add(trade);
							continue;
						}
						Matcher b = BUY.matcher(text);
						if (b.find()) {
							Hit h = new Hit();
							h.wrap = wraps;
							h.sec = sec;
							h.who = b.group(1);
							h.raw = b.group(2);
							h.n = num(b.group(3));
							h.won = num(b.group(4));
							hits.add(h);
							continue;
						}
						JsonObject mc = miscOf(text, hm(sec));
						if (mc != null) {
							Hit h = new Hit();
							h.wrap = wraps;
							h.sec = sec;
							h.misc = mc;
							hits.add(h);
						}
					}
				}
				if (!sawNeogul) {
					continue;   // 다른 서버 프로필의 기록
				}
				java.time.LocalDate start = end.minusDays(wraps);
				for (Hit h : hits) {
					java.time.LocalDate d = start.plusDays(h.wrap);
					boolean barter = h.trade && (!h.got.isEmpty() || !h.gave.isEmpty());   // 49-779차: 물물교환, 49-801차: 받기만 한 거래(돈을 받음)도
					if (d.isBefore(HIST_FROM) || (h.won <= 0 && !barter && h.misc == null)) {
						continue;
					}
					long ts = d.atStartOfDay(zone).toInstant().toEpochMilli() + h.sec * 1000L;
					if (h.bank == 4) {
						if (ts >= donateTs) {
							donateTs = ts;
							donateWon = h.won;
						}
						continue;
					}
					days.computeIfAbsent(d.toString(), k -> new ArrayList<>()).add(new Object[]{ts, h});
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("spendhist:file", t);
			}
		}
		for (var en : days.entrySet()) {
			String day = en.getKey();
			java.util.List<Object[]> list = en.getValue();
			list.sort((a, b) -> Long.compare((Long) a[0], (Long) b[0]));
			JsonArray rows = new JsonArray();
			long firstTs = 0, lastTs = 0;
			int part = 0, tr = 0, bk = 0, mi = 0;
			long blkWon = 0, blkTs = 0;
			int blkN = 0, blkSec = 0;
			for (Object[] o : list) {
				Hit h = (Hit) o[1];
				long ts = (Long) o[0];
				if (h.misc != null) {
					if ("block".equals(h.misc.get("k").getAsString())) {   // 블록은 하루치를 한 줄로(값만)
						if (blkN == 0) {
							blkSec = h.sec;
						}
						blkWon += h.misc.get("won").getAsLong();
						blkN++;
						blkTs = ts;
					} else {
						out.add(new Object[]{h.misc, day, ts, "h6|" + day + "|m" + (mi++)});
					}
					continue;
				}
				if (h.bank != 0) {
					JsonObject c = new JsonObject();
					c.addProperty("k", h.bank == 2 ? "bank" : "bankin");
					c.addProperty("t", hm(h.sec));
					c.addProperty("won", h.won);
					out.add(new Object[]{c, day, ts, "h6|" + day + "|k" + (bk++)});
					continue;
				}
				if (h.trade) {
					JsonObject c = new JsonObject();
					c.addProperty("k", "trade");
					c.addProperty("t", hm(h.sec));
					c.addProperty("who", h.who);
					c.addProperty("won", h.won);
					while (h.got.size() > 6) {
						h.got.remove(h.got.size() - 1);
					}
					while (h.gave.size() > 6) {
						h.gave.remove(h.gave.size() - 1);
					}
					c.add("got", h.got);
					c.add("gave", h.gave);
					out.add(new Object[]{c, day, ts, "h6|" + day + "|t" + (tr++)});
					continue;
				}
				JsonObject row = thing(h.raw, h.n);
				row.addProperty("who", h.who);
				row.addProperty("won", h.won);
				row.addProperty("times", 1);
				boolean merged = false;
				for (var e : rows) {
					JsonObject r0 = e.getAsJsonObject();
					if (r0.get("name").getAsString().equals(row.get("name").getAsString()) && r0.get("who").getAsString().equals(h.who)) {
						r0.addProperty("n", r0.get("n").getAsLong() + h.n);
						r0.addProperty("won", r0.get("won").getAsLong() + h.won);
						r0.addProperty("times", r0.get("times").getAsInt() + 1);
						merged = true;
						break;
					}
				}
				if (!merged) {
					if (rows.isEmpty()) {
						firstTs = ts;
					}
					rows.add(row);
				}
				lastTs = ts;
				if (rows.toString().length() > 1100) {
					out.add(new Object[]{buyCard(rows, firstTs, zone), day, lastTs, "h6|" + day + "|b" + (part++)});
					rows = new JsonArray();
				}
			}
			if (!rows.isEmpty()) {
				out.add(new Object[]{buyCard(rows, firstTs, zone), day, lastTs, "h6|" + day + "|b" + part});
			}
			if (blkN > 0) {
				JsonObject c = miscCard("block", hm(blkSec));
				c.addProperty("won", blkWon);
				c.addProperty("n", blkN);
				out.add(new Object[]{c, day, blkTs, "h6|" + day + "|blk"});
			}
		}
		if (donateWon >= 0) {
			JsonObject c = new JsonObject();
			c.addProperty("k", "donate");
			c.addProperty("won", donateWon);
			out.add(new Object[]{c, LocalDate.now(KST).toString(), donateTs, "h6|donate"});
		}
		return out;
	}

	private static String hm(int sec) {
		return String.format("%02d:%02d", sec / 3600, sec / 60 % 60);
	}

	private static JsonObject buyCard(JsonArray rows, long firstTs, ZoneId zone) {
		JsonObject c = new JsonObject();
		c.addProperty("k", "buy");
		java.time.LocalTime lt = java.time.Instant.ofEpochMilli(firstTs).atZone(zone).toLocalTime();
		c.addProperty("t", String.format("%02d:%02d", lt.getHour(), lt.getMinute()));
		long sum = 0;
		for (var e : rows) {
			sum += e.getAsJsonObject().get("won").getAsLong();
		}
		c.addProperty("won", sum);
		c.add("rows", rows);
		return c;
	}

	/** 49-761차: 웹후크 주소가 없으면(섬원) 루나리스 연동으로 개인 지출을 보낸다. */
	private boolean viaLink() {
		// 49-771차(사용자: "지출 채널 없애고 기능들을 개인지출에 몰빵"): 루나리스 연동이 되어 있으면 주소가 있어도 늘 연동으로(개인지출)
		return LunarisLinkModule.canSpend();
	}

	/** LunarisLinkModule이 틱마다 부른다 - 이 기능이 꺼져 있어도 개인 지출은 모아서 보낸다. */
	public static void linkTick() {
		NeogulSpendLogModule m = instance;
		if (m != null && !m.isEnabled() && m.viaLink()) {   // 켜져 있으면 자기 onTick이 돈다
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
		histDone.hidden();
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
		String s = (LunarisLinkModule.canSpend() ? "개인지출 자동 (루나리스 연동)" : linked() ? "예전 주소로 보냄 (루나리스 /연동 을 하면 개인지출로)" : "연결 안 됨 (루나리스 /연동 을 하십시오)") + "  |  오늘 " + count + "건 " + won(total);
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
		// 49-781차(사용자: "누적후원보상 명령어를 치게 만들던가 메시지 띄워서"): 후원하면 뜨는 줄 뒤에 /후원 을 치라고 알려 준다
		// (서버가 알려 주는 누적 후원금액 줄을 읽어야 정확해서, 받은 캐시로 셈하지 않는다 - 후원 사이트가 얹어 주는 캐시가 섞여 있다)
		if (line.contains("후원해 주셔서 감사합니다") && viaLink()) {
			try {
				client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
						"§a[Nova] §f/누적후원보상 을 한 번 여시면 누적 후원금액이 루나리스 개인지출에 반영됩니다."));
			} catch (Throwable t) {
				LunaCompat.warnOnce("spend:donateHint", t);
			}
		}
		Matcher dn = DONATE.matcher(line);
		if (dn.find() && dn.group(1).equalsIgnoreCase(LunaCompat.sessionName(client))) {
			JsonObject o = new JsonObject();
			o.addProperty("k", "donate");
			o.addProperty("t", hhmm());
			o.addProperty("won", num(dn.group(2)));
			queue.add(o);
			return;
		}
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
		if (!b.find()) {
			// 49-801차: 블록 구매(3분 묶음), 송금, 도토리 구매, 도토리 상점
			JsonObject mc = miscOf(line, hhmm());
			if (mc != null) {
				if ("block".equals(mc.get("k").getAsString())) {
					if (blockN == 0) {
						blockFirst = now;
					}
					blockWon += mc.get("won").getAsLong();
					blockN++;
					blockAt = now;
				} else {
					queue.add(mc);
				}
			}
			return;
		}
		{
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
		// 49-792차: 올리는 때가 아니라 처음 산 때를 적는다(묶는 시간이 길어져서)
		java.time.LocalTime bt = buyFirst > 0 ? java.time.Instant.ofEpochMilli(buyFirst).atZone(KST).toLocalTime() : LocalTime.now(KST);
		card.addProperty("t", String.format("%02d:%02d", bt.getHour(), bt.getMinute()));
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
		// 49-779차(사용자: "거래를 돈 말고 내가 물물교환한 것도"), 49-801차("모든 거래 품목"): 주고받은 것이 있으면 모두 올린다
		if (tradePaid > 0 || !tradeGave.isEmpty() || !tradeGot.isEmpty()) {
			JsonObject o = new JsonObject();
			o.addProperty("k", "trade");
			o.addProperty("t", hhmm());
			o.addProperty("who", tradeWith);
			o.addProperty("won", tradePaid);
			o.add("got", tradeGot.deepCopy());
			o.add("gave", tradeGave.deepCopy());
			if (tradePaid > 0) {
				spent(tradePaid);
			}
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

	// 49-787차(사용자 사진: /누적후원보상 창 "LNR_Sil2ntium님의 누적 후원금액은 500,000원입니다." - "이걸로 확인하면 되겠다"):
	// 그 글은 채팅이 아니라 창 안 글이라, 열린 창의 글(제목 + 글 위젯)을 읽는다. 같은 금액은 한 번만 보낸다.
	private long donateSeen = -1;
	private int donateTick;

	private void donateScreenTick() {
		if (++donateTick % 10 != 0 || !viaLink() || !NeogulData.onNeogul(client)) {
			return;
		}
		try {
			net.minecraft.client.gui.screens.Screen sc = LunaCompat.screenOf(client);
			if (sc == null) {
				return;
			}
			StringBuilder sb = new StringBuilder();
			if (sc.getTitle() != null) {
				sb.append(sc.getTitle().getString()).append('\n');
			}
			donateTexts(sc, 0, new int[1], sb);
			Matcher dn = DONATE.matcher(clean(sb.toString()));
			if (!dn.find() || !dn.group(1).equalsIgnoreCase(String.valueOf(LunaCompat.sessionName(client)))) {
				return;
			}
			long won = num(dn.group(2));
			if (won == donateSeen) {
				return;
			}
			donateSeen = won;
			JsonObject o = new JsonObject();
			o.addProperty("k", "donate");
			o.addProperty("t", hhmm());
			o.addProperty("won", won);
			queue.add(o);
		} catch (Throwable t) {
			LunaCompat.warnOnce("spend:donateScreen", t);
		}
	}

	/** 창 안 글 위젯의 글을 모은다(깊이 6, 300개까지). */
	private static void donateTexts(Object o, int depth, int[] n, StringBuilder sb) {
		if (!(o instanceof net.minecraft.client.gui.components.events.ContainerEventHandler c) || depth > 6) {
			return;
		}
		for (Object child : c.children()) {
			if (++n[0] > 300) {
				return;
			}
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.getMessage() != null) {
				sb.append(w.getMessage().getString()).append('\n');
			}
			donateTexts(child, depth + 1, n, sb);
		}
	}

	private void tick0() {
		long now = System.currentTimeMillis();
		histTick(now);
		donateScreenTick();
		if (tradeWith != null && now - tradeAt >= 3000) {
			endTrade();
		}
		if (buyRows != null && (now - buyAt >= BUY_GAP_MS || now - buyFirst >= BUY_MAX_MS || client.player == null
				|| buyRows.toString().length() > 1100)) {
			endBuys();
		}
		if (blockN > 0 && (now - blockAt >= BUY_GAP_MS || now - blockFirst >= BUY_MAX_MS || client.player == null)) {
			endBlocks();
		}
		if (!queue.isEmpty() && LunarisLinkModule.canSpend()) {
			// 49-761차: 섬원은 연동으로(개인지출). 시험 글과 섬 은행 출금은 개인 지출이 아니라 버린다. 글 길이(2000자)를 넘지 않게 줄을 줄인다.
			JsonObject c0 = queue.remove(0);
			String k0 = c0.has("k") ? c0.get("k").getAsString() : "";
			if ("buy".equals(k0) || "trade".equals(k0) || "bank".equals(k0) || "donate".equals(k0)
					|| "block".equals(k0) || "send".equals(k0) || "acorn".equals(k0) || "ashop".equals(k0)) {   // 49-771차: 섬 은행 출금도 개인지출로
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
