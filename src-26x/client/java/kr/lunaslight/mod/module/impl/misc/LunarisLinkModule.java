package kr.lunaslight.mod.module.impl.misc;

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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-653차(사용자: "피로도 최대치가 100인 사람은 200, 125인 사람은 250을 쓰면 피로도 통과, 일퀘 보상 메시지가 뜨면 일퀘 통과 -
 * 루나리스 봇에 신호를 보내서 자동 일퀘 확인"): <b>루나리스 자동 일퀘</b>.
 *
 * <ul>
 *   <li>일퀘: 서버 알림 "퀘스트 완료 보상을 획득"(플레이어 채팅은 빼고) → 오늘 일퀘 완료.</li>
 *   <li>피로도(49-654차 다시 짬 - 사용자: "피로회복제 쓰면 뜨는 메시지, 그 뒤 또 채우면 그것도 1회", 쓴 양을 더하는 방식은
 *       액션바를 놓치면 모자랐다): <b>섬 하나가 가득 찬 것(N = 최대치)을 1회</b>로 센다.
 *     <ul>
 *       <li>섬 이름이 보이는 줄("해양섬 피로도 ( 125/125 )" - /피로도 결과, 액션바)은 섬마다 "센 적 있음" 표시를 저장해
 *           같은 가득 참을 두 번 세지 않는다. 회복제 메시지("해양섬 피로도가 회복되었습니다. (0/125)")나 덜 찬 값을 보면
 *           표시를 지워 다시 채우면 또 1회.</li>
 *       <li>섬 이름이 없는 액션바("피로도 ( N/M )")는 같은 월드 안에서 덜 찬 값 → 가득 찬 값으로 오르는 것을 볼 때만 1회
 *           (어느 섬인지 모르니 "이름 모르는 1회"로 두고, 뒤에 /피로도에서 가득 찬 섬이 처음 보이면 그걸로 맞춘다).</li>
 *       <li>토벌, 던전 피로도는 뺀다.</li>
 *     </ul></li>
 *   <li>보내기: 루나리스 봇이 /연동 으로 준 코드(LNR1.웹후크id.웹후크토큰.디스코드id.세대.서명)로 숨은 채널 웹후크에
 *       {"lnr":1,…,"f":가득 찬 횟수,"c":지금 N,"m":최대치} 한 줄을 올린다. 일퀘, 횟수가 바뀌면 바로, 지금 N만 바뀌면 3분에 한 번.</li>
 * </ul>
 * 날짜는 한국 시간. 코드는 남에게 보이지 않게 숨긴 설정에만 둔다(클립보드에서 붙여 넣기).
 */
public class LunarisLinkModule extends Module {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Pattern CODE = Pattern.compile("LNR1\\.(\\d{5,25})\\.([A-Za-z0-9_-]{20,100})\\.(\\d{5,25})\\.(\\d{1,6})\\.([A-Za-z0-9_-]{10,60})");
	private static final Pattern BAR = Pattern.compile("([가-힣A-Za-z\\[\\]]*)\\s*피로도\\s*\\(\\s*([\\d,]+)\\s*/\\s*([\\d,]+)\\s*\\)");
	private static final Pattern RECOVER = Pattern.compile("([가-힣A-Za-z\\[\\]]*)\\s*피로도가\\s*회복");
	private static final Pattern PLAYER_CHAT = Pattern.compile("[A-Za-z0-9_]{3,16}\\s*:\\s");
	private static final long PROGRESS_MS = 180_000;

	private final StringSetting code = register(new StringSetting("code", "연동 코드", "", ""));
	/** 저장용(JSON): 날짜, 일퀘, 오늘 가득 찬 횟수, 최대치, 지금 N, 보낸 값들, 섬마다 센 적 있음, 이름 모르는 1회. */
	private final StringSetting state = register(new StringSetting("state", "상태", "", ""));

	private String day;
	private boolean quest;
	private int fills;
	private int max;
	private int cur;
	private boolean sentQuest;
	private int sentFills = -1;
	private int sentCur = -1;
	private int sentPotions;
	private long sentMoney = -1, sentAcorn = -1;
	private final Map<String, Boolean> full = new HashMap<>();
	private int unnamed;
	private int potions;   // 49-667차: 오늘 쓴 피로회복제(회복 메시지 수)
	private boolean barArmed;   // 49-667차: 이름 없는 액션바 - 덜 찬 섬을 보고 있음(가득 차면 1회)
	private boolean loaded;

	private Object lastBar;
	private Object lastLevel;
	private int barPrev = -1;   // 이름 없는 액션바: 이 월드에서 마지막으로 본 N
	private int barRise;   // 49-684차: 1회를 셀 수 있게 된 뒤 같은 섬에서 조금씩 오른 횟수
	private int barMisses;        // 49-686차: 오늘 가득 찼는데 못 센 횟수(봇 기록용, 저장 안 함)
	private String barMissNote;   // 마지막으로 못 센 때와 까닭
	// 49-699차(사용자 사진: 헌디 2번 채웠는데 1회, 카드 마지막 값 124/125): 섬은 피로도가 가득 차면 바로 내보내서
	// 마지막 125/125 액션바를 못 보거나(내보내기와 같은 틱에 와서 월드가 바뀐 뒤 처음 값으로 읽힘) 아예 못 받는 일이 있다.
	private int leftPrev = -1;      // 월드가 바뀌기 직전 이름 없는 액션바 값
	private boolean leftArmed;
	private int leftRise;
	private long leftAt;
	private int nearN = -1;         // 거의 가득에서 나가서 1회로 센 값(같은 섬에 다시 들어와 채우면 두 번 안 셈)
	private boolean dirty;
	private long nextSend;
	private long lastProgress;
	private volatile boolean sending;
	private String note = "";
	private int calTick;
	private int walletTick;
	private String logScanDay = "";
	private volatile boolean scanning;
	private static final Pattern LOG_TIME = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})\\]");
	private static final Pattern LOG_GZ = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-\\d+\\.log\\.gz");

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	public LunarisLinkModule() {
		super("lunaris_link", "루나리스 자동 일퀘", ModuleCategory.SERVER, "일퀘 보상, 피로도를 루나리스 봇에 자동으로 알림");
		serverGroup("너굴마을");
		code.hidden();
		state.hidden();
		register(new InfoSetting("status", "상태", this::statusText));
		register(new ActionSetting("paste", "연동 코드", "디스코드에서 /연동 으로 받은 코드를 복사한 뒤 누르십시오.", "붙여 넣기", this::pasteCode));
		register(new ActionSetting("unlink", "연동 해제", "이 컴퓨터에서 코드를 지웁니다.", "해제", () -> {
			code.setValue("");
			note = "연동을 해제했습니다";
			kr.lunaslight.mod.config.LunaClientConfig.save();
		}));
		register(new ActionSetting("resend", "지금 보내기", "오늘 기록을 봇에 다시 보냅니다.", "보내기", this::resendAll));
		ChatState.addLoggedListener(this::onChat);
	}

	// ==================== 상태 ====================

	private String today() {
		return LocalDate.now(KST).toString();
	}

	private static int num(JsonObject o, String k, int def) {
		return o.has(k) ? o.get(k).getAsInt() : def;
	}

	private void load() {
		if (!loaded) {
			loaded = true;
			try {
				String v = state.get();
				if (v != null && !v.isEmpty()) {
					@SuppressWarnings("deprecation")
					JsonObject o = new JsonParser().parse(v).getAsJsonObject();
					day = o.get("d").getAsString();
					quest = o.has("q") && o.get("q").getAsBoolean();
					fills = num(o, "f", 0);
					max = num(o, "m", 0);
					cur = num(o, "c", 0);
					sentQuest = o.has("sq") && o.get("sq").getAsBoolean();
					sentFills = num(o, "sf", -1);
					sentCur = num(o, "sc", -1);
					unnamed = num(o, "un", 0);
					potions = num(o, "r", 0);
					// 49-667차: 예전 판은 섬을 오가거나 피로도가 조금 내려갔다 오르면 또 세서 8회까지 부풀었다 - 오늘 값은 섬 4개로 줄인다
					if (num(o, "v", 1) < 2 && fills > ISLANDS) {
						fills = ISLANDS;
					}
					if (o.has("full")) {
						for (var e : o.getAsJsonObject("full").entrySet()) {
							full.put(e.getKey(), e.getValue().getAsBoolean());
						}
					}
				}
			} catch (Throwable ignored) {
			}
		}
		String t = today();
		if (!t.equals(day)) {
			day = t;
			quest = false;
			fills = 0;
			unnamed = 0;
			nearN = -1;
			potions = 0;
			barMisses = 0;
			barMissNote = null;
			cur = 0;   // 49-679차(규격 10장): 자정에 지금 값도 0
			sentPotions = 0;
			sentQuest = false;
			sentFills = -1;
			sentCur = -1;
			save();   // 섬마다 "센 적 있음"은 남긴다 - 어제 가득 찬 채로 둔 섬을 오늘 또 세지 않게
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		o.addProperty("d", day);
		o.addProperty("q", quest);
		o.addProperty("f", fills);
		o.addProperty("m", max);
		o.addProperty("c", cur);
		o.addProperty("sq", sentQuest);
		o.addProperty("sf", sentFills);
		o.addProperty("sc", sentCur);
		o.addProperty("un", unnamed);
		o.addProperty("r", potions);
		o.addProperty("v", 2);
		JsonObject f = new JsonObject();
		full.forEach(f::addProperty);
		o.add("full", f);
		state.setValue(o.toString());
	}

	private void changed() {
		save();
		dirty = true;
	}

	private boolean linked() {
		String c = code.get();
		return c != null && CODE.matcher(c).matches();
	}

	private String statusText() {
		load();
		StringBuilder sb = new StringBuilder();
		sb.append(linked() ? "연동됨" : "코드 없음");
		sb.append("  |  일퀘 ").append(quest ? "완료" : "아직");
		sb.append("  |  피로도 ").append(fills).append("회");
		if (potions > 0) {
			sb.append("  |  피로회복제 ").append(potions).append("개");
		}
		if (max > 0) {
			sb.append(" (지금 ").append(cur).append("/").append(max).append(")");
		}
		if (note != null && !note.isEmpty()) {
			sb.append("  |  ").append(note);
		}
		return sb.toString();
	}

	private void resendAll() {
		load();
		scanLogs();
		sentQuest = false;
		sentFills = -1;
		sentCur = -1;
		sentPotions = -1;
		lastProgress = 0;
		nextSend = 0;
		changed();
	}

	private void pasteCode() {
		try {
			String clip = kr.lunaslight.mod.util.LunaInput.clipboard(net.minecraft.client.Minecraft.getInstance());
			Matcher m = CODE.matcher(clip == null ? "" : clip);
			if (!m.find()) {
				note = "클립보드에 코드가 없습니다";
				return;
			}
			code.setValue(m.group());
			if (!isEnabledStored()) {
				setEnabled(true);
			}
			note = "연동했습니다";
			kr.lunaslight.mod.util.NeogulGear.relink();   // 49-679차: 장비 신호 처음부터
			kr.lunaslight.mod.util.NeogulPotHistory.relink();
			mboxId = null;   // 49-696차: 새 코드면 우편함도 새로
			resendAll();
			kr.lunaslight.mod.config.LunaClientConfig.save();
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaris:paste", t);
			note = "붙여 넣지 못했습니다";
		}
	}

	// ==================== 감지 ====================

	private static boolean skipKey(String key) {
		return key.contains("토벌") || key.contains("던전");
	}

	private void onChat(String line) {
		if (!isEnabled() || line == null || line.isEmpty() || !NeogulData.onNeogul(client)) {
			return;
		}
		line = line.replaceFirst("^\\s*\\[\\d{1,2}:\\d{2}\\]\\s*", "");
		if (line.startsWith("<") || PLAYER_CHAT.matcher(line).find()) {
			return;   // 플레이어 채팅(누가 같은 글을 쳐도 안 걸리게)
		}
		load();
		if (line.replace(" ", "").contains("퀘스트완료보상을획득") && !quest) {
			quest = true;
			nextSend = 0;
			changed();
			kr.lunaslight.mod.config.LunaClientConfig.save();
		}
		if (mineChat(line) || bankChat(line)) {
			return;
		}
		// 회복제: "해양섬 피로도가 회복되었습니다. (0/125)" → 그 섬은 다시 채우면 또 1회
		Matcher r = RECOVER.matcher(line);
		// 49-679차(규격 10장: 피로도 채운 횟수와 회복제 수가 안 맞음): 회복제 줄은 "(0/최대)"가 붙은 시스템 줄만 - 남이 쓴 채팅은 안 셈
		boolean chatLine = line.contains("[전체]") || line.contains("[귓속말]") || line.contains("[확성기]") || line.contains("명)");
		if (!chatLine && r.find() && line.matches(".*\\(\\s*0\\s*/\\s*[\\d,]+\\s*\\).*")) {
			String key = r.group(1) == null ? "" : r.group(1);
			if (!skipKey(key)) {
				if (!key.isEmpty()) {
					full.put(key, false);
				}
				potions++;
				// 49-704차(사용자: 이브 - 티켓 2번, 피로회복제 10개인데 피로도 10회. 로그: 회복제만 있고 그 앞 가득이 안 세진 때가 2번,
				// 같은 섬에서 계속 캐며 회복제를 마심): 가득을 못 본 채(125/125 액션바가 안 왔거나 거의 다 찼을 때 마심) 회복제를 쓰면
				// 지금 채우던 섬은 다 쓴 것 - 절반 넘게 찼으면 1회로 센다. 이미 센 섬(무장 안 됨)은 안 셈.
				if (barArmed && barPrev >= 0 && max > 0 && max < 500 && barPrev < max && max - barPrev <= max / 2 && fills < cap()) {
					fills++;
					unnamed++;
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 피로도 가득을 못 보고 피로회복제를 씀 - 1회로 셈 (앞 " + barPrev + "/" + max + ")");
				}
				barPrev = 0;
				barArmed = true;
				barRise = 0;
				nearN = -1;
				changed();
			}
			return;
		}
		// /피로도 결과("└ 해양섬 피로도 ( 125/125 )", 여러 줄이 한 번에 올 수 있음)
		Matcher m = BAR.matcher(line);
		while (m.find()) {
			observe(m.group(1), m.group(2), m.group(3), false);
		}
	}

	@Override
	public void onTick() {
		if (client == null) {
			return;
		}
		load();
		// 49-654차(사용자: "보내기에서 일퀘가 안 간다"): 일퀘 보상 줄을 놓쳤어도 너굴 달력에 오늘 받았다고 있으면 완료
		if (!quest && client.level != null && ++calTick % 40 == 0) {
			try {
				kr.lunaslight.mod.util.NeogulCalendar.Day cd = kr.lunaslight.mod.util.NeogulCalendar.peek(LocalDate.now().toString());
				if (cd != null && cd.quest == kr.lunaslight.mod.util.NeogulCalendar.Quest.CLAIMED) {
					quest = true;
					nextSend = 0;
					changed();
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("lunaris:calendar", t);
			}
		}
		if (!quest && client.level != null && !day.equals(logScanDay) && NeogulData.onNeogul(client)) {
			logScanDay = day;
			scanLogs();
		}
		readBar();
		if (linked() && NeogulData.onNeogul(client)) {
			kr.lunaslight.mod.util.NeogulGear.tick(client, this::queueGear);   // 49-679차: 장비 신호
			// 49-682차: 지난 피로회복제(게임 기록에서 센 날짜별 개수)를 코드마다 한 번
			Matcher cm = CODE.matcher(code.get());
			if (client.player != null && cm.matches()) {
				kr.lunaslight.mod.util.NeogulPotHistory.maybeSend(cm.group(3) + "." + cm.group(4), events::add);
			}
		}
		if (++walletTick % 20 == 0) {
			kr.lunaslight.mod.util.NeogulWallet.read(client);   // 49-670차: 소지금, 도토리(보스바)
			if (kr.lunaslight.mod.util.NeogulWallet.money >= 0 && (kr.lunaslight.mod.util.NeogulWallet.money != sentMoney
					|| kr.lunaslight.mod.util.NeogulWallet.acorn != sentAcorn)) {
				dirty = true;   // 보내기는 3분에 한 번으로 묶인다
			}
		}
		mineTick();
		mailboxTick();   // 49-696차
		if (!events.isEmpty() && linked() && !sending && System.currentTimeMillis() >= nextSend) {
			sendEvent();
			return;
		}
		if (dirty && linked() && !sending && System.currentTimeMillis() >= nextSend) {
			send();
		}
	}

	/** 섬 4개 + 오늘 쓴 피로회복제 수보다 많이 셀 수는 없다(안전장치). */
	private static final int ISLANDS = 4;

	private int cap() {
		int named = 0;
		for (String k : full.keySet()) {
			if (!skipKey(k)) {
				named++;
			}
		}
		return Math.max(ISLANDS, named) + potions;
	}

	private void readBar() {
		try {
			Object level = client.level;
			if (level != lastLevel) {
				lastLevel = level;
				leftPrev = barPrev;
				leftArmed = barArmed;
				leftRise = barRise;
				leftAt = System.currentTimeMillis();
				// 거의 가득(125면 122 이상)에서 섬을 나감 = 가득 차서 내보내진 것 → 1회
				if (barArmed && barPrev >= 0 && max > 0 && max < 500 && barPrev < max
						&& max - barPrev <= Math.max(1, max / 40) && fills < cap()) {
					fills++;
					unnamed++;
					nearN = barPrev;
					leftArmed = false;
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 피로도 거의 가득에서 섬을 나감 - 1회로 셈 (앞 " + barPrev + "/" + max + ")");
					cur = max;
					changed();
					kr.lunaslight.mod.config.LunaClientConfig.save();
				}
				barPrev = -1;   // 섬(월드)이 바뀌면 이름 없는 액션바는 처음부터
				barArmed = false;
				barRise = 0;
			}
			if (level == null || !isEnabled() || !NeogulData.onNeogul(client)) {
				return;
			}
			Object v = LunaCompat.getFieldValue(client.gui, "overlayMessageString");
			if (v == null || v == lastBar) {
				return;
			}
			lastBar = v;
			String s = v instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(v);
			Matcher m = BAR.matcher(s);
			if (m.find()) {
				observe(m.group(1), m.group(2), m.group(3), true);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaris:bar", t);
		}
	}

	/** 피로도 한 줄. bar = 액션바(지금 섬), 아니면 /피로도 결과. */
	private void observe(String rawKey, String nStr, String mStr, boolean bar) {
		String key = rawKey == null ? "" : rawKey;
		if (skipKey(key)) {
			return;
		}
		int n = Integer.parseInt(nStr.replace(",", ""));
		int mx = Integer.parseInt(mStr.replace(",", ""));
		if (mx <= 0 || mx > 1000) {
			return;
		}
		boolean now = false;
		if (mx != max) {
			max = mx;
			now = true;
		}
		if (bar && n != cur) {
			cur = n;
			now = true;
		}
		boolean isFull = n >= mx;
		int low = Math.max(1, mx / 10);
		// 49-667차(사용자 사진: 밍하 8회, "제대로 안 돼"): 예전엔 덜 찬 값을 한 번만 봐도 다시 셌다.
		// 섬을 오가며 다른 섬 값이 보이거나 피로도가 조금 내려갔다 오르면 또 1회가 돼서 부풀었다.
		// 이제는 "새로 채울 수 있게 됨" = 피로회복제 메시지, 거의 빈 값(최대치의 10% 이하), 처음 보는 덜 찬 섬일 때만.
		if (key.isEmpty()) {
			// 49-699차: 월드가 바뀐 바로 뒤 처음 본 값이 가득이면 바뀌기 전 섬 값으로 판단(내보내기와 같은 틱에 온 125/125)
			if (barPrev < 0 && isFull && leftArmed && leftPrev >= 0 && System.currentTimeMillis() - leftAt < 5_000) {
				barPrev = leftPrev;
				barArmed = true;
				barRise = leftRise;
				leftArmed = false;
			}
			// 거의 가득에서 나가 이미 센 섬에 다시 들어와 그 값을 보면 이 섬은 안 셈
			if (barPrev < 0 && !isFull && nearN >= 0 && n == nearN) {
				barPrev = n;
				barArmed = false;
				nearN = -1;
			}
			if (!isFull) {
				// 다른 섬으로 넘어감(값이 크게 바뀜), 처음 봄, 거의 빔 → 이 섬은 채우면 1회
				if (barPrev < 0 || n <= low || Math.abs(n - barPrev) > low) {
					if (!barArmed) {
						barRise = 0;
					}
					barArmed = true;
				} else if (barArmed && n > barPrev) {
					barRise++;
				}
			} else if (barArmed && barPrev >= 0 && (mx - barPrev <= mx / 2 || barRise >= 3) && fills < cap()) {
				// 같은 섬을 캐서 올라 가득 참.
				// 49-684차(사용자 사진: 망고 피로회복제 43개인데 피로도 35회): 예전엔 가득 차기 바로 전 값이 최대치의 80% 이상일 때만 셌다.
				// 액션바가 띄엄띄엄 오면(다른 알림이 덮음) 96 → 125처럼 건너뛰어 놓쳤다. 이제 절반 이상이었거나, 조금씩 3번 이상 올랐으면 센다.
				fills++;
				unnamed++;
				barArmed = false;
				now = true;
			} else if (barPrev >= 0 && barPrev < mx) {
				// 덜 찬 값에서 가득으로 넘어갔는데 못 셈 - 까닭을 기록(49-686차: 봇에도 보냄)
				String why = (barArmed ? "" : "무장 안 됨 ") + "앞 " + barPrev + "/" + mx + " 오름 " + barRise + " 오늘 " + fills + "회 한도 " + cap();
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 피로도 가득 - 안 셈 (" + why + ")");
				barMisses++;
				barMissNote = java.time.LocalTime.now(KST).withNano(0) + " " + why;
			}
			barPrev = n;
		} else {
			Boolean counted = full.get(key);
			if (isFull) {
				if ((counted == null || !counted) && fills < cap()) {
					full.put(key, true);
					if (unnamed > 0 && !bar) {
						unnamed--;   // 아까 이름 모르게 센 1회가 이 섬이었다 - 두 번 안 셈
					} else {
						fills++;
					}
					now = true;
				}
			} else if (counted == null || (counted && n <= low)) {
				full.put(key, false);
				now = true;
			}
			if (bar) {
				barPrev = n;
				if (!isFull) {
					barArmed = !Boolean.TRUE.equals(full.get(key));
				}
			}
		}
		if (now) {
			changed();
			if (isFull) {
				kr.lunaslight.mod.config.LunaClientConfig.save();
			}
		}
	}

	// ==================== 49-655차: 그날 게임 기록에서 일퀘 찾기 ====================
	// 사용자: "보내기에서 일퀘가 안 간다" → "로그 뒤지면 되잖아 그날의". 모드를 켜기 전이나 연동 전에 받은 일퀘 보상은
	// 채팅 감지가 못 봤다. 게임 기록(logs/latest.log, 오늘 날짜로 끝난 logs/날짜-N.log.gz)의 [CHAT] 줄에서 오늘 받은
	// "퀘스트 완료보상을 획득"을 찾는다. 기록 줄엔 시각만 있어서 파일이 끝난 날(latest.log = 마지막 수정일, gz = 이름의 날짜)부터
	// 거꾸로 읽으며 시각이 거꾸로 가면 하루 앞으로 센다. 피로도는 다시 읽으면 두 번 셀 수 있어 일퀘만 본다.

	private void scanLogs() {
		if (scanning || quest) {
			return;
		}
		scanning = true;
		final String want = day;
		Thread th = new Thread(() -> {
			boolean found = false;
			try {
				found = questInLogs(LocalDate.parse(want));
			} catch (Throwable t) {
				LunaCompat.warnOnce("lunaris:logs", t);
			}
			final boolean f = found;
			client.execute(() -> {
				scanning = false;
				if (f && want.equals(day) && !quest) {
					quest = true;
					nextSend = 0;
					note = "게임 기록에서 오늘 일퀘 찾음";
					changed();
					kr.lunaslight.mod.config.LunaClientConfig.save();
				}
			});
		}, "Nova-LunarisLogScan");
		th.setDaemon(true);
		th.start();
	}

	private static boolean questInLogs(LocalDate want) throws java.io.IOException {
		java.nio.file.Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("logs");
		if (!java.nio.file.Files.isDirectory(dir)) {
			return false;
		}
		java.util.List<java.nio.file.Path> files = new java.util.ArrayList<>();
		try (var ls = java.nio.file.Files.list(dir)) {
			ls.forEach(files::add);
		}
		for (java.nio.file.Path f : files) {
			String name = f.getFileName().toString();
			LocalDate end;
			boolean gz = false;
			if (name.equals("latest.log")) {
				end = java.time.Instant.ofEpochMilli(java.nio.file.Files.getLastModifiedTime(f).toMillis()).atZone(ZoneId.systemDefault()).toLocalDate();
			} else {
				Matcher g = LOG_GZ.matcher(name);
				if (!g.matches()) {
					continue;
				}
				end = LocalDate.parse(g.group(1));
				gz = true;
			}
			if (end.isBefore(want) || java.nio.file.Files.size(f) > 200L * 1024 * 1024) {
				continue;   // 오늘보다 앞에 끝난 기록엔 오늘 줄이 없다
			}
			if (questInLog(f, gz, end, want)) {
				return true;
			}
		}
		return false;
	}

	private static boolean questInLog(java.nio.file.Path f, boolean gz, LocalDate end, LocalDate want) throws java.io.IOException {
		java.util.List<String> lines = new java.util.ArrayList<>();
		try (java.io.InputStream in0 = java.nio.file.Files.newInputStream(f);
				java.io.InputStream in = gz ? new java.util.zip.GZIPInputStream(in0) : in0;
				java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
			String l;
			while ((l = r.readLine()) != null) {
				if (l.startsWith("[")) {
					lines.add(l);
				}
			}
		}
		LocalDate date = end;
		int next = -1;   // 거꾸로 읽을 때 바로 뒤 줄의 시각(초), -1 = 아직 없음
		for (int i = lines.size() - 1; i >= 0; i--) {
			String l = lines.get(i);
			Matcher t = LOG_TIME.matcher(l);
			if (!t.find()) {
				continue;
			}
			int sec = Integer.parseInt(t.group(1)) * 3600 + Integer.parseInt(t.group(2)) * 60 + Integer.parseInt(t.group(3));
			if (next >= 0 && sec > next + 60) {
				date = date.minusDays(1);   // 시각이 거꾸로 감 = 자정을 넘긴 앞날
			}
			next = sec;
			if (date.isBefore(want)) {
				return false;
			}
			if (!date.equals(want)) {
				continue;
			}
			int c = l.indexOf("[CHAT]");
			if (c < 0) {
				continue;
			}
			String msg = l.substring(c + 6).trim();
			if (msg.startsWith("<") || PLAYER_CHAT.matcher(msg).find()) {
				continue;
			}
			if (msg.replace(" ", "").contains("퀘스트완료보상을획득")) {
				return true;
			}
		}
		return false;
	}

	// ==================== 49-663차: 잠광(섬 광물창고) 알림 ====================
	// 사용자: "섬 광물 창고의 사암 보관한도가 가득 차서 캘 수 없습니다 - 이 메시지가 뜨면 잠광 꽉 찼다고 디코에 알림, 정리하면 누가 정리했습니다,
	// 연동 안 한 사람이 정리하면 누군가 정리한 걸로", "저 메시지가 갑자기 멈췄는데 우리 중 금액이 없으면 누군가 판 거라고 생각하면 될 듯".
	//   꽉 참: 그 줄 → {"ev":"mine_full"} (10분에 한 번)
	//   정리(판매 줄은 판 사람에게만 뜸): "섬 광물창고의 광물 7종 277,401개를 판매해 섬 은행에 7,986,084원을 입금했습니다." → {"ev":"mine_clear", 종류, 개수, 금액}
	//   멈춤: 꽉 참 줄이 오던 같은 월드에서 90초 넘게 안 오면(계속 캐는 중인데 이제 캐짐 = 누가 비움) → {"ev":"mine_resume"}
	//   봇이 정리 줄이 없으면 "누군가 정리"로 올린다.
	private static final Pattern MINE_FULL = Pattern.compile("섬\\s*광물\\s*창고의\\s*(.*?)\\s*보관\\s*한도가\\s*가득\\s*차서\\s*캘\\s*수\\s*없");
	private static final Pattern MINE_SOLD = Pattern.compile("섬\\s*광물\\s*창고의\\s*광물\\s*([\\d,]+)\\s*종\\s*([\\d,]+)\\s*개를\\s*판매해\\s*섬\\s*은행에\\s*([\\d,]+)\\s*원");
	private long mineFullAt;
	private long mineFullSentAt;
	private Object mineFullLevel;
	private final java.util.List<JsonObject> events = new java.util.ArrayList<>();

	private static String plain(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || (cp >= 0xA000 && cp <= 0xA4CF)))
				.forEach(sb::appendCodePoint);
		return sb.toString().replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\s+", " ").trim();
	}

	private static long lnum(String s) {
		try {
			return Long.parseLong(s.replace(",", ""));
		} catch (Throwable t) {
			return 0;
		}
	}

	/** 잠광 줄이면 true. */
	// 49-671차(사용자: "벌금은 은행에 입금하는 걸로 자동 인식하면 돼, 금액을"): 섬 은행 입금
	//   "입금 되었습니다." 다음 줄 "└ 입금액: 16,000,000원" → {"ev":"bank_dep","won":16000000,"k":시각}
	private static final Pattern BANK_AMOUNT = Pattern.compile("└\\s*입금액\\s*:\\s*([\\d,]+)\\s*원");
	private long bankDepAt;

	private boolean bankChat(String line) {
		if (line.contains("[전체]") || line.contains("[귓속말]") || line.contains("[확성기]") || line.contains("명)")) {
			return false;   // 남이 쓴 글
		}
		String t = line.replace(" ", "");
		if (t.endsWith("입금되었습니다.") || t.endsWith("입금되었습니다")) {
			bankDepAt = System.currentTimeMillis();
			return true;
		}
		Matcher m = BANK_AMOUNT.matcher(line);
		if (m.find() && System.currentTimeMillis() - bankDepAt < 5000) {
			bankDepAt = 0;
			long won = lnum(m.group(1));
			if (won > 0) {
				JsonObject e = new JsonObject();
				e.addProperty("ev", "bank_dep");
				e.addProperty("won", won);
				e.addProperty("k", System.currentTimeMillis());
				events.add(e);
			}
			return true;
		}
		return false;
	}

	/**
	 * 49-679차(장비 신호 규격 6장): 장비는 최신 상태만 뜻이 있으니, 아직 안 나간 장비 신호 중 같은 보관함(같은 id, 또는 둘 다 없음)은
	 * 새 것으로 바꿔치기한다(지금 보내는 중인 맨 앞 것은 그대로).
	 */
	private void queueGear(java.util.List<JsonObject> pages, String boxKey) {
		for (int i = events.size() - 1; i >= 0; i--) {
			if (i == 0 && sending) {
				continue;
			}
			JsonObject e = events.get(i);
			if (e.has("ev") && "gear".equals(e.get("ev").getAsString()) && e.has("_bk") && boxKey.equals(e.get("_bk").getAsString())) {
				events.remove(i);
			}
		}
		for (JsonObject p : pages) {
			p.addProperty("_bk", boxKey);
			events.add(p);
		}
		while (events.size() > 40) {
			events.remove(sending ? 1 : 0);   // 너무 밀리면 오래된 것부터
		}
	}

	/** 49-679차(규격 10장): 꽉 참 줄은 30초 안에 3번 이상 떠야 진짜(한 번 스친 알림은 무시). */
	private final java.util.ArrayDeque<Long> mineFullHits = new java.util.ArrayDeque<>();

	private boolean mineChat(String line) {
		Matcher f = MINE_FULL.matcher(line);
		if (f.find()) {
			long now = System.currentTimeMillis();
			if (line.contains("[전체]") || line.contains("[귓속말]") || line.contains("[확성기]") || line.contains("명)")) {
				return false;   // 남이 쓴 글
			}
			mineFullHits.addLast(now);
			while (!mineFullHits.isEmpty() && now - mineFullHits.peekFirst() > 30_000) {
				mineFullHits.pollFirst();
			}
			mineFullAt = now;
			mineFullLevel = client.level;
			// 우리 섬이 아니라고 아는 곳(섬 방문 등)에서 뜬 줄은 안 보낸다
			boolean notOurs = kr.lunaslight.mod.util.NeogulGear.island == kr.lunaslight.mod.util.NeogulGear.Island.NO;
			if (mineFullHits.size() >= 3 && !notOurs && now - mineFullSentAt > 10 * 60_000) {
				mineFullSentAt = now;
				JsonObject e = new JsonObject();
				e.addProperty("ev", "mine_full");
				String item = plain(f.group(1));
				e.addProperty("item", item);
				try {
					NeogulData.Entry ne = NeogulData.byName(item);
					if (ne != null && ne.model() != null && !ne.model().isEmpty()) {
						e.addProperty("model", ne.model());
					} else if (ne != null && ne.icons() != null && !ne.icons().isEmpty()) {
						e.addProperty("model", ne.icons().get(0));
					}
				} catch (Throwable ignored) {
				}
				events.add(e);
			}
			return true;
		}
		Matcher s2 = MINE_SOLD.matcher(line);
		if (s2.find()) {
			JsonObject e = new JsonObject();
			e.addProperty("ev", "mine_clear");
			e.addProperty("kinds", lnum(s2.group(1)));
			e.addProperty("cnt", lnum(s2.group(2)));
			e.addProperty("won", lnum(s2.group(3)));
			events.add(e);
			mineFullAt = 0;
			mineFullSentAt = 0;
			return true;
		}
		return false;
	}

	private void mineTick() {
		if (mineFullAt > 0 && System.currentTimeMillis() - mineFullAt > 90_000) {
			if (client.level != null && client.level == mineFullLevel && NeogulData.onNeogul(client)) {
				JsonObject e = new JsonObject();
				e.addProperty("ev", "mine_resume");
				events.add(e);
			}
			mineFullAt = 0;
			mineFullSentAt = 0;
		}
	}

	// ==================== 49-696차: 우편함(루나리스 → 내 게임 화면) ====================
	// 사용자: "잠광 정리했을 때 연동된 사람들에게 채팅으로 됐다고 보내기 되나?" → 봇 토큰 없이, 이미 가진 웹후크로.
	// 접속하면 숨은 연동 채널에 웹후크 글 하나를 올리고(ev "mbox", ?wait=true로 글 번호를 받음) 30초마다 그 글을 읽는다.
	// 봇이 그 글을 {"mb":1,"n":번호,"e":[…]}로 고쳐 두면, 처음 읽은 번호보다 큰 일만 내 화면에 띄운다(알림 + 채팅 줄, 나만 보임).
	private String mboxId;
	private long mboxNext;
	private int mboxSeq = -1;
	private boolean mboxBusy;

	private void mailboxTick() {
		if (mboxBusy || !linked() || client.player == null || !NeogulData.onNeogul(client)) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < mboxNext) {
			return;
		}
		Matcher c = CODE.matcher(code.get());
		if (!c.matches()) {
			return;
		}
		String base = "https://discord.com/api/webhooks/" + c.group(1) + "/" + c.group(2);
		mboxBusy = true;
		mboxNext = now + 30_000;
		if (mboxId == null) {
			JsonObject p = new JsonObject();
			p.addProperty("ev", "mbox");
			p.addProperty("mv", 2);   // 49-701차: 같이 쓰는 우편함 글 하나를 읽음(봇이 그 글 번호를 알려 줌)
			p.addProperty("lnr", 1);
			p.addProperty("uid", c.group(3));
			p.addProperty("g", Integer.parseInt(c.group(4)));
			p.addProperty("s", c.group(5));
			p.addProperty("mc", LunaCompat.sessionName(client));
			p.addProperty("d", day);
			JsonObject body = new JsonObject();
			body.addProperty("content", p.toString());
			body.addProperty("username", "Nova 자동 일퀘");
			HttpRequest req = HttpRequest.newBuilder(URI.create(base + "?wait=true"))
					.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json").header("User-Agent", "NovaMod-Lunaris/2")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
			HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((r, err) -> client.execute(() -> {
				mboxBusy = false;
				try {
					if (err == null && r.statusCode() >= 200 && r.statusCode() < 300) {
						mboxId = JsonParser.parseString(r.body()).getAsJsonObject().get("id").getAsString();
						mboxSeq = -1;
						mboxNext = System.currentTimeMillis() + 5_000;   // 봇이 우편함으로 고칠 시간
					} else {
						mboxNext = System.currentTimeMillis() + 5 * 60_000;
					}
				} catch (Throwable t) {
					mboxNext = System.currentTimeMillis() + 5 * 60_000;
				}
			}));
			return;
		}
		HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/messages/" + mboxId))
				.timeout(Duration.ofSeconds(10)).header("User-Agent", "NovaMod-Lunaris/2").GET().build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((r, err) -> client.execute(() -> {
			mboxBusy = false;
			try {
				if (err != null || r == null) {
					return;
				}
				int st = r.statusCode();
				if (st == 404) {
					mboxId = null;   // 봇이 지웠음(하루 지남 등) - 다시 연다
					mboxNext = System.currentTimeMillis() + 60_000;
					return;
				}
				if (st == 429) {
					mboxNext = System.currentTimeMillis() + 90_000;
					return;
				}
				if (st < 200 || st >= 300) {
					return;
				}
				String content = JsonParser.parseString(r.body()).getAsJsonObject().get("content").getAsString();
				// 49-701차: 같이 쓰는 우편함 글은 "📬 머리 줄 … ||{"mb":…}||" - 값은 가림 안의 {"mb": … }
				int a = content.indexOf("{\"mb\":");
				int b = content.lastIndexOf('}');
				if (a < 0 || b < a) {
					return;   // 봇이 아직 우편함으로 안 고침
				}
				JsonObject mb = JsonParser.parseString(content.substring(a, b + 1)).getAsJsonObject();
				if (mb.has("ref")) {
					mboxId = mb.get("ref").getAsString();   // 이제부터 같이 쓰는 우편함 글을 읽음(내가 올린 글은 봇이 곧 지움)
					mboxSeq = -1;
					mboxNext = System.currentTimeMillis() + 1_000;
					return;
				}
				int n = mb.has("n") ? mb.get("n").getAsInt() : 0;
				if (mboxSeq < 0) {
					mboxSeq = n;   // 처음 읽은 것 = 기준(지난 일은 안 띄움)
					return;
				}
				if (n <= mboxSeq) {
					return;
				}
				String me = c.group(3);
				if (mb.has("e")) {
					for (var e : mb.getAsJsonArray("e")) {
						JsonObject o = e.getAsJsonObject();
						if (o.has("n") && o.get("n").getAsInt() > mboxSeq) {
							showMail(o, me);
						}
					}
				}
				mboxSeq = n;
			} catch (Throwable t) {
				LunaCompat.warnOnce("lunaris:mbox", t);
			}
		}));
	}

	/** 우편함 일 하나를 내 화면에(알림 + 채팅 줄). 내가 한 일이면 안 띄운다. */
	private void showMail(JsonObject o, String me) {
		String k = o.has("k") ? o.get("k").getAsString() : "";
		if (!"mine_clear".equals(k) || (o.has("u") && me.equals(o.get("u").getAsString()))) {
			return;
		}
		String who = o.has("who") ? o.get("who").getAsString() : "";
		long kinds = o.has("kinds") ? o.get("kinds").getAsLong() : 0;
		long cnt = o.has("cnt") ? o.get("cnt").getAsLong() : 0;
		long won = o.has("won") ? o.get("won").getAsLong() : 0;
		String head = who.isEmpty() ? "누군가 섬 광물창고를 정리했습니다." : who + "님이 섬 광물창고를 정리했습니다.";
		String tail = cnt > 0 ? String.format(java.util.Locale.ROOT, " %,d종 %,d개, %,d원", kinds, cnt, won) : "";
		try {
			client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[Nova] §f" + head + "§7" + tail));
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaris:mboxChat", t);
		}
		try {
			Object tm = LunaCompat.callNoArg(client, "getToastManager");
			if (tm == null) {
				tm = LunaCompat.callNoArg(client.gui, "toastManager");
			}
			if (tm instanceof net.minecraft.client.gui.components.toasts.ToastManager m) {
				net.minecraft.client.gui.components.toasts.SystemToast.add(m, net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
						net.minecraft.network.chat.Component.literal("잠광 정리됨"), net.minecraft.network.chat.Component.literal(head + tail));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaris:mboxToast", t);
		}
		LunaCompat.playUiSound(client, "EXPERIENCE_ORB_PICKUP", 1.4f);
	}

	/** 잠광 신호 하나 보내기(일퀘 신호와 같은 웹후크, 같은 서명). */
	private void sendEvent() {
		Matcher c = CODE.matcher(code.get());
		if (!c.matches() || events.isEmpty()) {
			return;
		}
		JsonObject p = events.get(0).deepCopy();
		p.remove("_bk");   // 49-679차: 큐 안에서만 쓰는 표시
		p.addProperty("lnr", 1);
		p.addProperty("uid", c.group(3));
		p.addProperty("g", Integer.parseInt(c.group(4)));
		p.addProperty("s", c.group(5));
		p.addProperty("mc", LunaCompat.sessionName(client));
		p.addProperty("d", day);
		JsonObject body = new JsonObject();
		body.addProperty("content", p.toString());
		body.addProperty("username", "Nova 자동 일퀘");
		final JsonObject first = events.get(0);
		sending = true;
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://discord.com/api/webhooks/" + c.group(1) + "/" + c.group(2)))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("User-Agent", "NovaMod-Lunaris/2")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, err) -> client.execute(() -> {
			sending = false;
			int st = r == null ? 0 : r.statusCode();
			if (err == null && st >= 200 && st < 300) {
				events.remove(first);
				nextSend = System.currentTimeMillis() + 1500;
			} else if (st == 401 || st == 404) {
				events.clear();
			} else {
				nextSend = System.currentTimeMillis() + 60_000;
			}
		}));
	}

	// ==================== 보내기 ====================

	private void send() {
		boolean questNew = quest && !sentQuest;
		boolean fillsNew = (fills > 0 && fills != sentFills) || potions != sentPotions;
		// 49-670차: 소지금, 도토리가 바뀐 것도 진행처럼 3분에 한 번
		long wNow = kr.lunaslight.mod.util.NeogulWallet.money, aNow = kr.lunaslight.mod.util.NeogulWallet.acorn;
		boolean curNew = (max > 0 && cur != sentCur) || (wNow >= 0 && (wNow != sentMoney || aNow != sentAcorn));
		long t = System.currentTimeMillis();
		if (!questNew && !fillsNew) {
			if (!curNew) {
				dirty = false;
				return;
			}
			if (t - lastProgress < PROGRESS_MS) {
				nextSend = lastProgress + PROGRESS_MS;   // 지금 N만 바뀐 건 3분에 한 번
				return;
			}
		}
		lastProgress = t;
		Matcher c = CODE.matcher(code.get());
		if (!c.matches()) {
			return;
		}
		JsonObject p = new JsonObject();
		p.addProperty("lnr", 1);
		p.addProperty("uid", c.group(3));
		p.addProperty("g", Integer.parseInt(c.group(4)));
		p.addProperty("s", c.group(5));
		p.addProperty("mc", LunaCompat.sessionName(client));
		p.addProperty("d", day);
		p.addProperty("q", quest);
		p.addProperty("f", fills);
		p.addProperty("r", potions);
		if (wNow >= 0) {
			p.addProperty("w", wNow);
		}
		if (aNow >= 0) {
			p.addProperty("a", aNow);
		}
		p.addProperty("c", cur);
		p.addProperty("m", max);
		p.addProperty("u", fills * max + (cur < max ? cur : 0));   // 예전 봇용
		// 49-686차(사용자: "Yume_o 다 했는데 1회만 감"): 봇 기록에 남길 모드 판과 못 센 까닭
		p.addProperty("nv", kr.lunaslight.mod.util.LunaVersion.mod());
		if (barMisses > 0 && barMissNote != null) {
			p.addProperty("fx", barMisses + "번, 마지막 " + barMissNote);
		}
		JsonObject body = new JsonObject();
		body.addProperty("content", p.toString());
		body.addProperty("username", "Nova 자동 일퀘");
		final boolean q = quest;
		final int f = fills;
		final int rr = potions;
		final int cc = cur;
		final String d = day;
		sending = true;
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://discord.com/api/webhooks/" + c.group(1) + "/" + c.group(2)))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("User-Agent", "NovaMod-Lunaris/2")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, err) -> client.execute(() -> {
			sending = false;
			int st = r == null ? 0 : r.statusCode();
			if (err == null && st >= 200 && st < 300) {
				if (d.equals(day)) {
					sentQuest = sentQuest || q;
					sentFills = f;
					sentPotions = rr;
					sentMoney = wNow;
					sentAcorn = aNow;
					sentCur = cc;
					save();
					kr.lunaslight.mod.config.LunaClientConfig.save();
				}
				note = "보냄";
				nextSend = System.currentTimeMillis() + 5_000;
			} else if (st == 401 || st == 404) {
				note = "코드가 더 이상 안 맞습니다(/연동 다시)";
				dirty = false;
			} else {
				note = "보내기 실패, 1분 뒤 다시";
				nextSend = System.currentTimeMillis() + 60_000;
			}
		}));
	}
}
