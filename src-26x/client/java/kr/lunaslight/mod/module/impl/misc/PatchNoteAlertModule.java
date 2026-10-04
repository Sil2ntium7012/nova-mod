package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 49-268차: <b>패치노트 알림</b>. 사용자: "인게임에 있을 때 패치노트 올라오면 위쪽 상단 가운데에 알림 보내 줘. 설정 없이 무조건".
 *
 * <p>패치노트 = 런처(Nova Client) GitHub 릴리스(Sil2ntium7012/nova-client, 런처 CHANGELOG로 만드는 본문). 다른 스레드에서
 * 5분마다 최신 릴리스를 물어보고, 게임을 켠 뒤 처음 본 것을 기준으로 삼아 <b>그 뒤에 새로 올라온 것</b>만 알린다(켜자마자 예전 것을
 * 띄우지 않게). 월드에 들어가 있을 때 화면 위 가운데에 카드로 8초 - 제목(버전)과 첫 항목. 메뉴에 있을 때 올라왔으면 월드에 들어갈 때 띄운다.
 *
 * <p>설정이 없다(항상 켜짐, 기능 목록에 카드 없음). GitHub 비로그인 조회 한도(시간당 60번)보다 훨씬 적게(시간당 12번) 묻는다.
 */
public class PatchNoteAlertModule extends Module {

	private static final String API = "https://api.github.com/repos/Sil2ntium7012/nova-client/releases/latest";
	private static final long POLL_MS = 5 * 60_000L;
	private static final long SHOW_MS = 8000, IN_MS = 350, OUT_MS = 450;
	private static final int CARD_H = 34;

	private static volatile String baseTag;
	private static volatile String pendingTitle, pendingLine;
	private static Thread worker;

	private String cardTitle, cardLine;
	private long cardAtMs;

	public PatchNoteAlertModule() {
		super("patch_note_alert", "패치노트 알림", ModuleCategory.FEATURE, "새 패치노트를 화면 위에 알림");
		alwaysOn();
	}

	/** 항상 켜진 알림 - 기능 목록에 카드를 만들지 않는다. */
	@Override
	public boolean hiddenInList() {
		return true;
	}

	@Override
	public void onTick() {
		startWorker();
		if (client == null || client.level == null) {
			return;
		}
		String t = pendingTitle;
		if (t != null && cardTitle == null) {
			cardTitle = t;
			cardLine = pendingLine;
			pendingTitle = null;
			pendingLine = null;
			cardAtMs = System.currentTimeMillis();
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", 1.2f);
		}
	}

	private static synchronized void startWorker() {
		if (worker != null) {
			return;
		}
		worker = new Thread(PatchNoteAlertModule::loop, "Nova-PatchNotes");
		worker.setDaemon(true);
		worker.start();
	}

	private static void loop() {
		try {
			Thread.sleep(15_000L);   // 게임이 켜지는 동안은 조용히
		} catch (InterruptedException e) {
			return;
		}
		while (true) {
			long wait = POLL_MS;
			try {
				check();
			} catch (Throwable t) {
				wait = POLL_MS * 2;   // 연결 실패, 조회 한도 - 천천히 다시
			}
			try {
				Thread.sleep(wait);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	private static void check() throws Exception {
		HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
		c.setConnectTimeout(8000);
		c.setReadTimeout(8000);
		c.setRequestProperty("User-Agent", "NovaClient-Mod");
		c.setRequestProperty("Accept", "application/vnd.github+json");
		int code = c.getResponseCode();
		if (code != 200) {
			c.disconnect();
			throw new IllegalStateException("HTTP " + code);
		}
		String json;
		try (InputStream in = c.getInputStream()) {
			ByteArrayOutputStream bo = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0 && bo.size() < (1 << 20)) {
				bo.write(buf, 0, n);
			}
			json = bo.toString(StandardCharsets.UTF_8.name());
		} finally {
			c.disconnect();
		}
		com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
		String tag = str(o, "tag_name");
		if (tag == null || tag.isEmpty()) {
			return;
		}
		if (baseTag == null) {
			baseTag = tag;   // 처음 본 것 = 이미 올라와 있던 것
			return;
		}
		if (tag.equals(baseTag)) {
			return;
		}
		baseTag = tag;
		List<String> items = items(str(o, "body"));
		String line = items.isEmpty() ? "런처에서 자세한 내용을 볼 수 있어요"
				: clip(items.get(0), 46) + (items.size() > 1 ? "  외 " + (items.size() - 1) + "개" : "");
		pendingLine = line;
		pendingTitle = "Nova Client " + tag + " 패치노트";
	}

	private static String str(com.google.gson.JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null;
	}

	/** 릴리스 본문(마크다운)의 목록 줄("- ", "* ")만. */
	private static List<String> items(String body) {
		List<String> out = new ArrayList<>();
		if (body == null) {
			return out;
		}
		for (String l : body.split("\\r?\\n")) {
			String s = l.trim();
			if (s.startsWith("- ") || s.startsWith("* ")) {
				s = s.substring(2).replace("**", "").replace("`", "").trim();
				if (!s.isEmpty()) {
					out.add(s);
				}
			}
		}
		return out;
	}

	private static String clip(String s, int max) {
		return s.length() <= max ? s : s.substring(0, max - 1) + "…";
	}

	// ==================== 카드 ====================

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (client == null || cardTitle == null) {
			return;
		}
		long age = System.currentTimeMillis() - cardAtMs;
		if (age > SHOW_MS + OUT_MS) {
			cardTitle = null;
			cardLine = null;
			return;
		}
		float in = Math.min(1f, age / (float) IN_MS);
		float out = age > SHOW_MS ? Math.min(1f, (age - SHOW_MS) / (float) OUT_MS) : 0f;
		float ease = 1f - (1f - in) * (1f - in) * (1f - in);
		float alpha = Math.min(in * 1.6f, 1f) * (1f - out);
		int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
		if (a < 10) {
			return;   // 알파가 너무 작으면 옛 버전에서 글자가 불투명으로 그려진다
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int w1 = LunaCompat.getTextWidth(client.font, cardTitle);
		int w2 = cardLine == null ? 0 : LunaCompat.getTextWidth(client.font, cardLine);
		int w = Math.max(w1, w2) + 20;
		int x = (sw - w) / 2;
		int y = Math.round(6 - (1f - ease) * (CARD_H + 10) - out * (CARD_H + 10));
		drawHudBox(context, x, y, w, CARD_H, alpha);
		int accent = kr.lunaslight.mod.gui.LunaDraw.ACCENT & 0xFFFFFF;
		LunaCompat.drawHudText(context, client.font, cardTitle, x + (w - w1) / 2, y + 6, (a << 24) | accent);
		if (cardLine != null) {
			LunaCompat.drawHudText(context, client.font, cardLine, x + (w - w2) / 2, y + 19, (a << 24) | 0xFFFFFF);
		}
	}
}
