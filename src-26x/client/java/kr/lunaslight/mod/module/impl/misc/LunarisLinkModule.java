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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-653차(사용자: "피로도 최대치가 100인 사람은 200, 125인 사람은 250을 쓰면 피로도 통과, 일퀘 보상 메시지가 뜨면 일퀘 통과 -
 * 루나리스 봇에 신호를 보내서 자동 일퀘 확인"): <b>루나리스 자동 일퀘</b>.
 *
 * <ul>
 *   <li>일퀘: 서버 알림 "퀘스트 완료 보상을 획득"(플레이어 채팅은 빼고) → 오늘 일퀘 완료.</li>
 *   <li>피로도: 액션바 "… 피로도 ( N/M )"(토벌, 던전은 빼고)의 N이 오른 만큼 오늘 쓴 양에 더한다. 월드(섬)가 바뀌면 처음 본 값은
 *       기준으로만 쓴다(섬마다 N이 달라서). 줄어들면(회복제) 기준만 바꾼다. M = 최대치(100 / 125).</li>
 *   <li>보내기: 루나리스 봇이 /연동 으로 준 코드(LNR1.웹후크id.웹후크토큰.디스코드id.세대.서명)로 숨은 채널 웹후크에
 *       {"lnr":1,…} 한 줄을 올린다. 봇이 서명을 확인하고 그 사람 게시글에 일퀘 완료, 피로도 횟수(쓴 양 / 최대치)를 넣는다.
 *       바뀔 때만 보내고, 실패하면 1분 뒤 다시.</li>
 * </ul>
 * 날짜는 한국 시간. 코드는 남에게 보이지 않게 숨긴 설정에만 둔다(클립보드에서 붙여 넣기).
 */
public class LunarisLinkModule extends Module {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Pattern CODE = Pattern.compile("LNR1\\.(\\d{5,25})\\.([A-Za-z0-9_-]{20,100})\\.(\\d{5,25})\\.(\\d{1,6})\\.([A-Za-z0-9_-]{10,60})");
	private static final Pattern BAR = Pattern.compile("([가-힣A-Za-z\\[\\]]*)\\s*피로도\\s*\\(\\s*([\\d,]+)\\s*/\\s*([\\d,]+)\\s*\\)");
	private static final Pattern PLAYER_CHAT = Pattern.compile("[A-Za-z0-9_]{3,16}\\s*:\\s");

	private final StringSetting code = register(new StringSetting("code", "연동 코드", "", ""));
	/** 저장용: {"d":날짜,"u":쓴 양,"m":최대치,"q":일퀘,"su":보낸 쓴 양,"sq":보낸 일퀘}. */
	private final StringSetting state = register(new StringSetting("state", "상태", "", ""));

	private String day;
	private int used;
	private int max;
	private boolean quest;
	private int sentUsed = -1;
	private boolean sentQuest;
	private boolean loaded;

	private Object lastBar;
	private Object lastLevel;
	private int barBase = -1;
	private String barKey = "";
	private boolean dirty;
	private long nextSend;
	private volatile boolean sending;
	private String note = "";

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	public LunarisLinkModule() {
		super("lunaris_link", "루나리스 자동 일퀘", ModuleCategory.SERVER, "일퀘 보상, 피로도를 루나리스 봇에 자동으로 알림");
		serverGroup("너굴마을");
		code.hidden();
		state.hidden();
		register(new InfoSetting("status", "상태", this::statusText));
		register(new ActionSetting("paste", "연동 코드", "디스코드에서 /연동 으로 받은 코드를 복사한 뒤 누르세요.", "붙여 넣기", this::pasteCode));
		register(new ActionSetting("unlink", "연동 해제", "이 컴퓨터에서 코드를 지웁니다.", "해제", () -> {
			code.setValue("");
			note = "연동을 해제했어요";
			kr.lunaslight.mod.config.LunaClientConfig.save();
		}));
		register(new ActionSetting("resend", "지금 보내기", "오늘 기록을 봇에 다시 보냅니다.", "보내기", () -> {
			load();
			dirty = true;
			sentUsed = -1;
			sentQuest = false;
			nextSend = 0;
		}));
		ChatState.addLoggedListener(this::onChat);
	}

	// ==================== 상태 ====================

	private String today() {
		return LocalDate.now(KST).toString();
	}

	private void load() {
		String t = today();
		if (!loaded) {
			loaded = true;
			try {
				String v = state.get();
				if (v != null && !v.isEmpty()) {
					@SuppressWarnings("deprecation")
					JsonObject o = new JsonParser().parse(v).getAsJsonObject();
					day = o.get("d").getAsString();
					used = o.get("u").getAsInt();
					max = o.get("m").getAsInt();
					quest = o.get("q").getAsBoolean();
					sentUsed = o.get("su").getAsInt();
					sentQuest = o.get("sq").getAsBoolean();
				}
			} catch (Throwable ignored) {
			}
		}
		if (!t.equals(day)) {
			day = t;
			used = 0;
			quest = false;
			sentUsed = -1;
			sentQuest = false;
			save();
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		o.addProperty("d", day);
		o.addProperty("u", used);
		o.addProperty("m", max);
		o.addProperty("q", quest);
		o.addProperty("su", sentUsed);
		o.addProperty("sq", sentQuest);
		state.setValue(o.toString());
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
		if (max > 0) {
			sb.append("  |  피로도 ").append(String.format("%,d", used)).append(" / ").append(String.format("%,d", max * 2));
		} else {
			sb.append("  |  피로도 아직 못 봄");
		}
		if (note != null && !note.isEmpty()) {
			sb.append("  |  ").append(note);
		}
		return sb.toString();
	}

	private void pasteCode() {
		try {
			String clip = kr.lunaslight.mod.util.LunaInput.clipboard(net.minecraft.client.Minecraft.getInstance());
			Matcher m = CODE.matcher(clip == null ? "" : clip);
			if (!m.find()) {
				note = "클립보드에 코드가 없어요";
				return;
			}
			code.setValue(m.group());
			if (!isEnabledStored()) {
				setEnabled(true);
			}
			note = "연동했어요";
			load();
			dirty = true;
			sentUsed = -1;
			sentQuest = false;
			nextSend = 0;
			kr.lunaslight.mod.config.LunaClientConfig.save();
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaris:paste", t);
			note = "붙여 넣지 못했어요";
		}
	}

	// ==================== 감지 ====================

	private void onChat(String line) {
		if (!isEnabled() || line == null || line.isEmpty() || !NeogulData.onNeogul(client)) {
			return;
		}
		line = line.replaceFirst("^\\s*\\[\\d{1,2}:\\d{2}\\]\\s*", "");
		if (line.startsWith("<") || PLAYER_CHAT.matcher(line).find()) {
			return;   // 플레이어 채팅(누가 같은 글을 쳐도 안 걸리게)
		}
		if (line.replace(" ", "").contains("퀘스트완료보상을획득")) {
			load();
			if (!quest) {
				quest = true;
				save();
				dirty = true;
				nextSend = 0;
				kr.lunaslight.mod.config.LunaClientConfig.save();
			}
		}
	}

	@Override
	public void onTick() {
		if (client == null) {
			return;
		}
		load();
		readBar();
		if (dirty && linked() && !sending && System.currentTimeMillis() >= nextSend) {
			send();
		}
	}

	private void readBar() {
		try {
			Object level = client.level;
			if (level != lastLevel) {
				lastLevel = level;
				barBase = -1;   // 섬(월드)이 바뀌면 처음 본 값은 기준으로만
			}
			if (level == null || !NeogulData.onNeogul(client)) {
				return;
			}
			Object v = LunaCompat.getFieldValue(client.gui, "overlayMessageString");
			if (v == null || v == lastBar) {
				return;
			}
			lastBar = v;
			String s = v instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(v);
			Matcher m = BAR.matcher(s);
			if (!m.find()) {
				return;
			}
			String key = m.group(1) == null ? "" : m.group(1);
			if (key.contains("토벌") || key.contains("던전")) {
				return;
			}
			int n = Integer.parseInt(m.group(2).replace(",", ""));
			int mx = Integer.parseInt(m.group(3).replace(",", ""));
			if (mx <= 0 || mx > 1000) {
				return;
			}
			if (!key.equals(barKey)) {
				barKey = key;
				barBase = -1;
			}
			if (mx != max) {
				max = mx;
				dirty = true;
			}
			if (barBase >= 0 && n > barBase && n - barBase <= mx) {
				used += n - barBase;
				dirty = true;
			}
			barBase = n;
			if (dirty) {
				save();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaris:bar", t);
		}
	}

	// ==================== 보내기 ====================

	private void send() {
		int units = max > 0 ? used / max : 0;
		int sentUnits = max > 0 && sentUsed >= 0 ? sentUsed / max : -1;
		boolean questNew = quest && !sentQuest;
		boolean unitsNew = units > 0 && units != sentUnits;
		if (!questNew && !unitsNew) {
			dirty = false;
			return;
		}
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
		p.addProperty("u", used);
		p.addProperty("m", max);
		JsonObject body = new JsonObject();
		body.addProperty("content", p.toString());
		body.addProperty("username", "Nova 자동 일퀘");
		final boolean q = quest;
		final int u = used;
		final String d = day;
		sending = true;
		HttpRequest req = HttpRequest.newBuilder(URI.create("https://discord.com/api/webhooks/" + c.group(1) + "/" + c.group(2)))
				.timeout(Duration.ofSeconds(10))
				.header("Content-Type", "application/json")
				.header("User-Agent", "NovaMod-Lunaris/1")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString()))
				.build();
		HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, err) -> client.execute(() -> {
			sending = false;
			int st = r == null ? 0 : r.statusCode();
			if (err == null && st >= 200 && st < 300) {
				if (d.equals(day)) {
					sentQuest = sentQuest || q;
					sentUsed = Math.max(sentUsed, u);
					save();
					kr.lunaslight.mod.config.LunaClientConfig.save();
				}
				note = "보냄";
				nextSend = System.currentTimeMillis() + 5_000;
			} else if (st == 401 || st == 404) {
				note = "코드가 더 이상 안 맞아요(/연동 다시)";
				dirty = false;
			} else {
				note = "보내기 실패, 1분 뒤 다시";
				nextSend = System.currentTimeMillis() + 60_000;
			}
		}));
	}
}
