package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.InfoSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.DiscordVoice;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 49-732차(사용자가 전해 준 부탁: "너굴라이언트 쓸 때 왼쪽에 디스코드 뜨게, 말할 때 초록색 원 뜨는 거"): <b>디스코드 음성</b>.
 * 내가 들어가 있는 디스코드 음성 채널 사람들을 화면 왼쪽에 프로필 사진과 이름으로, 말하는 사람은 사진에 초록 테두리({@link DiscordVoice}).
 * 처음 한 번 [연결]을 누르면 디스코드 창에 허용 창이 뜬다(StreamKit Overlay). 마이크 꺼짐, 헤드셋 꺼짐은 이름 옆에 빨갛게.
 *
 * <p>49-733차(사용자: "디스코드 오버레이가 안 되면 약간 다른 느낌으로 디스코드 심화를 만들어 봐"): 이름을 <b>디스코드 심화</b>로.
 * 음성 채널에 더해 디스코드 알림(DM, 멘션 - 디스코드 앱이 띄우는 알림)을 화면 오른쪽 위에 카드로 6초 띄운다(소리 설정).
 * 디스코드 자체 오버레이는 디스코드가 고른 게임(2만 5천여 개 중 141개)만 저절로 켜지고 마인크래프트는 그 목록에 없어(overlay false) 모드로는 못 켠다.
 */
public class DiscordVoiceModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 5, 90)));
	private final BooleanSetting onlySpeaking = register(new BooleanSetting(
			"only_speaking", "말하는 사람만", "켜면 지금 말하는 사람만 보여 줍니다.", false));
	private final BooleanSetting showChannel = register(new BooleanSetting(
			"show_channel", "채널 이름", "맨 위에 음성 채널 이름을 보여 줍니다.", true));
	private final BooleanSetting background = register(new BooleanSetting(
			"background", "배경", "목록 뒤에 어두운 판을 깝니다.", false));
	private final BooleanSetting showVoice = register(new BooleanSetting(
			"show_voice", "음성 채널", "들어가 있는 음성 채널 사람들을 보여 줍니다.", true));
	private final BooleanSetting notes = register(new BooleanSetting(
			"notes", "디스코드 알림", "DM과 멘션 알림을 화면 오른쪽 위에 카드로 띄웁니다.", true));
	private final BooleanSetting noteSound = register(new BooleanSetting(
			"note_sound", "알림 소리", "디스코드 알림이 오면 작은 소리를 냅니다.", true));
	private final PositionSetting notePos = register(new PositionSetting(
			"note_position", "알림 위치", "디스코드 알림 카드 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 5, 40)));

	private static final int ROW = 18, AV = 14;
	private static final int GREEN = 0xFF23A55A;

	/** 사람 id → 프로필 사진 텍스처(받는 중이면 없음). */
	private final Map<String, Identifier> tex = new ConcurrentHashMap<>();
	private final Map<String, String> texHash = new ConcurrentHashMap<>();
	private final java.util.Set<String> loading = ConcurrentHashMap.newKeySet();
	private final ConcurrentLinkedQueue<Object[]> ready = new ConcurrentLinkedQueue<>();
	private static final java.net.http.HttpClient HTTP = java.net.http.HttpClient.newBuilder()
			.connectTimeout(java.time.Duration.ofSeconds(8)).followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
	private int seq;

	public DiscordVoiceModule() {
		super("discord_voice", "디스코드 심화", ModuleCategory.FEATURE, "디스코드 음성 채널(말하는 사람 초록 테두리)과 DM, 멘션 알림을 게임 화면에");
		newFeature("49-732");
		register(new InfoSetting("status", "상태", () -> DiscordVoice.status));
		register(new ActionSetting("connect", "디스코드 연결", "누르면 디스코드 창에 허용 창이 뜹니다(StreamKit Overlay). 처음 한 번만 하면 됩니다.", "연결",
				() -> {
					if (!isEnabled()) {
						setEnabled(true);
					}
					DiscordVoice.start(true);
				}));
		register(new ActionSetting("forget", "연결 끊기", "이 컴퓨터에 저장한 디스코드 허용을 지웁니다.", "끊기", DiscordVoice::forget));
	}

	@Override
	protected void onEnable() {
		DiscordVoice.start(false);
	}

	@Override
	protected void onDisable() {
		DiscordVoice.stop();
	}

	@Override
	public void onTick() {
		if (isEnabled()) {
			DiscordVoice.start(false);   // 이미 돌고 있으면 아무것도 안 함
		}
	}

	private static final class Row {
		String id, name, avatar;
		boolean speaking, muted, deafened;
	}

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		if (isPreview()) {
			String[][] s = {{"망고", "1"}, {"유신", "0"}, {"서린", "0"}, {"하로", "1"}};
			for (int i = 0; i < s.length; i++) {
				Row r = new Row();
				r.id = "p" + i;
				r.name = s[i][0];
				r.speaking = "1".equals(s[i][1]);
				r.muted = i == 2;
				out.add(r);
			}
			return out;
		}
		for (DiscordVoice.Member m : DiscordVoice.members()) {
			if (onlySpeaking.get() && !m.speaking) {
				continue;
			}
			Row r = new Row();
			r.id = m.id;
			r.name = m.name == null ? "?" : m.name;
			r.avatar = m.avatar;
			r.speaking = m.speaking;
			r.muted = m.muted;
			r.deafened = m.deafened;
			out.add(r);
		}
		return out;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		uploadReady();
		DiscordVoice.wantNotes = notes.get();
		if (notes.get()) {
			drawNotes(ctx);
		}
		if (!showVoice.get()) {
			return;
		}
		List<Row> rows = rows();
		String title = isPreview() ? "루나 음성" : DiscordVoice.channelName;
		boolean head = showChannel.get() && title != null && !rows.isEmpty();
		if (rows.isEmpty()) {
			return;
		}
		int w = head ? LunaDraw.width(client.font, title) : 0;
		for (Row r : rows) {
			w = Math.max(w, AV + 5 + LunaDraw.width(client.font, r.name) + (r.deafened ? 4 + LunaDraw.width(client.font, "헤드셋 끔")
					: r.muted ? 4 + LunaDraw.width(client.font, "마이크 끔") : 0));
		}
		w += 6;
		int h = (head ? 12 : 0) + rows.size() * ROW;
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		int x = position.get().resolveX(sw, w), y = position.get().resolveY(sh, h);
		if (background.get()) {
			drawHudPanel(ctx, x - 3, y - 3, w + 6, h + 4);
		}
		int yy = y;
		if (head) {
			LunaCompat.drawHudText(ctx, client.font, title, x, yy, 0xFFB5BAC1);
			yy += 12;
		}
		for (Row r : rows) {
			int ax = x + 1, ay = yy + 1;
			if (r.speaking) {
				LunaDraw.circle(ctx, ax - 2, ay - 2, AV + 4, GREEN);   // 말하는 중: 초록 테두리
				LunaDraw.circle(ctx, ax - 1, ay - 1, AV + 2, 0xFF1E1F22);
			}
			Identifier t = r.id == null ? null : avatarTex(r.id, r.avatar);
			if (t == null || !LunaGfx.drawImage(ctx, t, ax, ay, AV, AV, 64, 64, 0xFFFFFFFF)) {
				LunaDraw.circle(ctx, ax, ay, AV, 0xFF5865F2);   // 사진이 없으면 디스코드 색 원 + 첫 글자
				String c = r.name.isEmpty() ? "?" : r.name.substring(0, 1);
				LunaCompat.drawHudText(ctx, client.font, c, ax + (AV - LunaDraw.width(client.font, c)) / 2, ay + 3, 0xFFFFFFFF);
			}
			int tx = x + AV + 6, ty = yy + 4;
			LunaCompat.drawHudText(ctx, client.font, r.name, tx, ty, r.speaking ? 0xFFFFFFFF : 0xFFDBDEE1);
			if (r.deafened || r.muted) {
				String m = r.deafened ? "헤드셋 끔" : "마이크 끔";
				LunaCompat.drawHudText(ctx, client.font, m, tx + LunaDraw.width(client.font, r.name) + 4, ty, 0xFFF23F43);
			}
			yy += ROW;
		}
	}

	// ==================== 49-733차: 디스코드 알림 카드 ====================

	private static final long NOTE_MS = 6000;
	private final List<DiscordVoice.Note> shown = new ArrayList<>();

	private void drawNotes(GuiGraphicsExtractor ctx) {
		long now = System.currentTimeMillis();
		DiscordVoice.Note n;
		while ((n = DiscordVoice.NOTES.poll()) != null) {
			shown.add(new DiscordVoice.Note(n.title(), n.body(), n.icon(), now));
			if (noteSound.get()) {
				LunaCompat.playUiSound(client, "EXPERIENCE_ORB_PICKUP", 1.6f);
			}
		}
		shown.removeIf(x -> now - x.at() > NOTE_MS);
		List<DiscordVoice.Note> list = new ArrayList<>(shown);
		if (isPreview() && list.isEmpty()) {
			list.add(new DiscordVoice.Note("유신 (#잠광, 루나)", "@망고 광물창고 다 찼습니다 정리 부탁드립니다", null, now));
		}
		while (list.size() > 3) {
			list.remove(0);
		}
		if (list.isEmpty()) {
			return;
		}
		int w = 190, lineH = 10;
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		int total = 0;
		List<List<String>> bodies = new ArrayList<>();
		for (DiscordVoice.Note x : list) {
			List<String> lines = wrap(x.body(), w - 30, 2);
			bodies.add(lines);
			total += 14 + lines.size() * lineH + 6;
		}
		int x0 = notePos.get().resolveX(sw, w), y = notePos.get().resolveY(sh, total);
		for (int i = list.size() - 1; i >= 0; i--) {   // 새 알림이 위
			DiscordVoice.Note x = list.get(i);
			List<String> lines = bodies.get(i);
			int h = 14 + lines.size() * lineH;
			long age = now - x.at();
			float a = isPreview() ? 1f : age < 250 ? age / 250f : age > NOTE_MS - 500 ? Math.max(0f, (NOTE_MS - age) / 500f) : 1f;
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * a);
			try {
				LunaDraw.roundRect(ctx, x0, y, w, h + 4, 5, 0xE6232428);
				LunaDraw.roundRect(ctx, x0, y, 3, h + 4, 1, 0xFF5865F2);   // 디스코드 색 띠
				int ax = x0 + 7, ay = y + 4;
				Identifier t = x.icon() == null ? null : urlTex(x.icon(), x.icon());
				if (t == null || !LunaGfx.drawImage(ctx, t, ax, ay, 16, 16, 64, 64, 0xFFFFFFFF)) {
					LunaDraw.circle(ctx, ax, ay, 16, 0xFF5865F2);
				}
				int tx = x0 + 28;
				LunaCompat.drawHudText(ctx, client.font, cut(x.title(), w - 32), tx, y + 4, 0xFFFFFFFF);
				for (int k = 0; k < lines.size(); k++) {
					LunaCompat.drawHudText(ctx, client.font, lines.get(k), tx, y + 15 + k * lineH, 0xFFDBDEE1);
				}
			} finally {
				LunaDraw.setAlpha(base);
			}
			y += h + 4 + 2;
		}
	}

	private String cut(String s, int maxW) {
		if (LunaDraw.width(client.font, s) <= maxW) {
			return s;
		}
		String t = s;
		while (!t.isEmpty() && LunaDraw.width(client.font, t + "…") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	/** 글자 폭으로 줄 나눔(최대 n줄, 넘치면 마지막 줄 끝에 …). */
	private List<String> wrap(String s, int maxW, int n) {
		List<String> out = new ArrayList<>();
		String rest = s.replace('\n', ' ').trim();
		while (!rest.isEmpty() && out.size() < n) {
			int k = rest.length();
			while (k > 1 && LunaDraw.width(client.font, rest.substring(0, k)) > maxW) {
				k--;
			}
			if (out.size() == n - 1 && k < rest.length()) {
				out.add(cut(rest, maxW));
				return out;
			}
			out.add(rest.substring(0, k));
			rest = rest.substring(k).trim();
		}
		return out;
	}

	// ==================== 프로필 사진 ====================

	private Identifier avatarTex(String id, String hash) {
		String url0 = hash != null && !hash.isEmpty() ? "https://cdn.discordapp.com/avatars/" + id + "/" + hash + ".png?size=64" : null;
		if (url0 == null) {
			long n;
			try {
				n = (Long.parseUnsignedLong(id) >>> 22) % 6;
			} catch (Throwable e) {
				n = 0;
			}
			url0 = "https://cdn.discordapp.com/embed/avatars/" + n + ".png";
		}
		return urlTex(id, url0);
	}

	/** 주소의 사진을 동그랗게(받는 중이면 null). key마다 하나, 주소가 바뀌면 다시 받음. */
	private Identifier urlTex(String id, String url0) {
		String key = url0;
		Identifier t = tex.get(id);
		if (t != null && key.equals(texHash.get(id))) {
			return t;
		}
		if (loading.add(id + "|" + key)) {
			String url = url0;
			java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).timeout(java.time.Duration.ofSeconds(10)).GET().build();
			HTTP.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray()).thenAccept(r -> {
				try {
					if (r.statusCode() / 100 != 2) {
						return;
					}
					Object img = round(r.body());
					if (img != null) {
						ready.add(new Object[]{id, key, img});
					}
				} catch (Throwable e) {
					LunaCompat.warnOnce("discordVoice:avatar", e);
				}
			});
		}
		return t;
	}

	/** 사진을 64x64 동그라미로 잘라 NativeImage로(그림 풀기는 받는 스레드에서). */
	private static Object round(byte[] png) throws Exception {
		java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
		if (src == null) {
			return null;
		}
		int n = 64;
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, n, n));
		g.drawImage(src, 0, 0, n, n, null);
		g.dispose();
		java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(out, "png", bo);
		return com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bo.toByteArray()));
	}

	/** 받은 사진을 렌더 스레드에서 텍스처로 올림. */
	private void uploadReady() {
		Object[] r;
		while ((r = ready.poll()) != null) {
			String id = (String) r[0];
			Identifier old = tex.remove(id);
			Identifier t = LunaCompat.registerImageTexture(client, "discord_av/" + (seq++), (com.mojang.blaze3d.platform.NativeImage) r[2]);
			if (t != null) {
				tex.put(id, t);
				texHash.put(id, (String) r[1]);
			}
			if (old != null) {
				try {
					client.getTextureManager().release(old);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
