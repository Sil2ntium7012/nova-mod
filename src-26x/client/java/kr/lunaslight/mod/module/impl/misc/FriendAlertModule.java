package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaSocial;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 49-74차(1-4) 친구 접속 메시지 → 49-160차 다시 만듦.
 *
 * <p>사용자(49-160): "같은 서버 접속만 되는 거고 알림이 어딘가 띠링 하고 올라오는 느낌이여야 해 소리도 나고.
 * 지금은 소리도 안나고 뭐도 안돼". 예전 판은 런처 접속(오프라인 ↔ 온라인)만 봐서, 게임 중에는 거의 바뀌지 않아
 * 아무것도 안 떴고, 알림음은 기본 꺼짐이었다.
 *
 * <h3>"같은 서버에 들어왔다"를 어떻게 아나</h3>
 * <ol>
 *   <li><b>탭 목록</b>: 친구의 마크 닉네임(런처가 site_presence에 올린 mc_name, 오프라인이어도 마지막 값이 남음)이
 *       지금 서버 탭 목록에 새로 나타나면 들어온 것, 사라지면 나간 것. 거의 바로 안다(0.5초마다 봄).</li>
 *   <li>닉네임을 모르는 친구만 <b>접속 정보</b>(site_presence의 서버 주소가 내 서버와 같은지)로 판단. 10초마다 읽는데,
 *       친구 게임 → 런처 → 서버를 거쳐 올라오므로 수십 초 늦을 수 있다.</li>
 * </ol>
 * 서버에 막 들어온 뒤(6초, 그리고 첫 친구 목록 조회 전)는 조용히 지금 상태만 적어 둔다 - 이미 있던 친구로 도배하지 않게.
 * 서버를 옮기면 다시 조용히 시작한다.
 *
 * <h3>알림</h3>
 * 화면 오른쪽 아래에서 카드가 올라와 4초 머문 뒤 내려간다(친구 얼굴 + "OO 님이 들어왔어요"). 소리는 기본 "띠링"
 * (노트 블록 두 음). 배경은 이 기능의 [배경] 설정을 따른다. 채팅 한 줄은 선택.
 */
public class FriendAlertModule extends Module {

	/** 친구 목록 다시 읽는 간격. 닉네임을 아는 친구는 탭 목록으로 바로 알기 때문에 이건 보조다. */
	private static final long POLL_MS = 10_000L;
	/** 서버에 들어온 직후 조용히 있는 시간(탭 목록이 다 차는 동안). */
	private static final long SETTLE_MS = 6_000L;
	private static final long SHOW_MS = 4_000L;
	private static final long IN_MS = 260L;
	private static final long OUT_MS = 320L;
	private static final int MAX_CARDS = 3;
	private static final int CARD_H = 26;

	public enum Tone {
		DING("띠링"),
		BELL("종"),
		XP("경험치"),
		PLING("핑");

		private final String label;

		Tone(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final BooleanSetting join = register(new BooleanSetting(
			"join", "접속 알림", "친구가 같은 서버에 들어오면 알립니다.", true));
	private final BooleanSetting leave = register(new BooleanSetting(
			"leave", "퇴장 알림", "친구가 같은 서버에서 나가면 알립니다.", false));
	// 49-160차: 예전 "sound"(기본 끔)는 저장된 값이 꺼짐으로 남아 있어서 새 키로 바꿨다(기본 켬).
	private final BooleanSetting sound = register(new BooleanSetting(
			"sound_v2", "알림음", "알릴 때 소리를 냅니다.", true));
	private final EnumSetting<Tone> tone = register(new EnumSetting<>(
			"tone", "소리", "알림 소리입니다.", Tone.DING, Tone.class));
	private final BooleanSetting chat = register(new BooleanSetting(
			"chat", "채팅 표시", "카드와 함께 채팅에 한 줄 남깁니다.", false));

	private record Card(String name, String mcName, boolean joined, long startMs) {
	}

	private final List<Card> cards = new ArrayList<>();

	private final Map<String, LunaSocial.Friend> friends = new HashMap<>();   // 런처 계정 id → 친구
	private final Map<String, String> mcNames = new HashMap<>();             // 런처 계정 id → 마지막 마크 닉네임
	private Set<String> present = new HashSet<>();                           // 지금 같은 서버에 있다고 본 친구
	private String serverKey = "";
	private long joinedAtMs;
	private boolean fetchedSinceJoin;
	private long lastPollMs;
	private boolean polling;
	private int tickCount;
	private long secondNoteAtMs;

	public FriendAlertModule() {
		super("friend_alert", "친구 접속 알림", ModuleCategory.FEATURE,
				"같은 서버에 친구가 들어오면 알림");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);   // 1-4는 [일반 설정] 항목이다
		defaultEnabled(true);   // 49-121차(사용자): 기본 활성화
		enableHudStyle(0xD80E1014, false, 0xB40B0C0E, Module.HudShape.FOLLOW);
	}

	@Override
	protected void onDisable() {
		resetServer("");
		cards.clear();
		secondNoteAtMs = 0L;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	private void resetServer(String key) {
		serverKey = key;
		present = new HashSet<>();
		joinedAtMs = System.currentTimeMillis();
		fetchedSinceJoin = false;
		lastPollMs = 0L;
	}

	@Override
	public void onTick() {
		pumpSound();
		if (client == null || client.level == null) {
			if (!serverKey.isEmpty()) {
				resetServer("");
			}
			return;
		}
		String key = normalize(LunaSocial.serverAddress(client));
		if (key.isEmpty()) {
			if (!serverKey.isEmpty()) {
				resetServer("");   // 싱글 - 친구가 같이 있을 수 없다
			}
			return;
		}
		if (!key.equals(serverKey)) {
			resetServer(key);
		}
		poll();
		if (++tickCount % 10 == 0) {
			evaluate();
		}
	}

	private void poll() {
		if (polling || !LunaSocial.signedIn()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastPollMs < POLL_MS) {
			return;
		}
		lastPollMs = now;
		polling = true;
		LunaSocial.fetchFriends().whenComplete((list, t) -> {
			if (client == null) {
				polling = false;
				return;
			}
			client.execute(() -> {
				polling = false;
				if (t != null || list == null) {
					return;   // 인터넷이 잠깐 끊긴 것 - 다음 차례에 다시 본다
				}
				friends.clear();
				for (LunaSocial.Friend f : list) {
					friends.put(f.accountId(), f);
					if (f.mcName() != null && !f.mcName().isEmpty()) {
						mcNames.put(f.accountId(), f.mcName());
					}
				}
				fetchedSinceJoin = true;
				evaluate();
			});
		});
	}

	/** 지금 같은 서버에 있는 친구를 다시 세고, 달라진 사람만 알린다. */
	private void evaluate() {
		if (client == null || friends.isEmpty() && present.isEmpty()) {
			return;
		}
		Set<String> tab = new HashSet<>();
		for (String n : LunaCompat.playerListNames(client)) {
			if (n != null) {
				tab.add(n.toLowerCase(Locale.ROOT));
			}
		}
		Set<String> now = new HashSet<>();
		for (LunaSocial.Friend f : friends.values()) {
			String mc = mcNames.get(f.accountId());
			boolean here;
			if (mc != null && !mc.isEmpty()) {
				here = tab.contains(mc.toLowerCase(Locale.ROOT));
			} else {
				here = !"offline".equals(f.presence()) && serverKey.equals(normalize(f.server()));
			}
			if (here) {
				now.add(f.accountId());
			}
		}
		boolean quiet = !fetchedSinceJoin || System.currentTimeMillis() - joinedAtMs < SETTLE_MS;
		if (!quiet) {
			boolean rang = false;
			for (String id : now) {
				if (!present.contains(id) && join.get()) {
					announce(friends.get(id), true, !rang);
					rang = true;
				}
			}
			for (String id : present) {
				if (!now.contains(id) && leave.get()) {
					LunaSocial.Friend f = friends.get(id);
					if (f != null) {
						announce(f, false, false);   // 나갈 때는 조용히 카드만
					}
				}
			}
		}
		present = now;
	}

	private void announce(LunaSocial.Friend f, boolean joined, boolean ring) {
		if (f == null) {
			return;
		}
		String mc = mcNames.get(f.accountId());
		cards.add(new Card(f.name(), mc, joined, System.currentTimeMillis()));
		while (cards.size() > MAX_CARDS) {
			cards.remove(0);
		}
		if (chat.get()) {
			LunaCompat.printLocalMessage(client, joined
					? "§a◆ §f" + f.name() + " §7님이 같은 서버에 들어왔습니다"
					: "§8◇ §7" + f.name() + " 님이 서버에서 나갔습니다");
		}
		if (ring && sound.get()) {
			playTone(tone.get());
		}
	}

	private void playTone(Tone t) {
		switch (t) {
			case DING -> {
				LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_PLING", 1.5f);
				secondNoteAtMs = System.currentTimeMillis() + 120L;   // "띠-링" 두 번째 음
			}
			case BELL -> LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", 1.4f);
			case XP -> LunaCompat.playUiSound(client, "ENTITY_EXPERIENCE_ORB_PICKUP", 1.0f);
			case PLING -> LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_PLING", 1.8f);
		}
	}

	private void pumpSound() {
		if (secondNoteAtMs != 0L && System.currentTimeMillis() >= secondNoteAtMs) {
			secondNoteAtMs = 0L;
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_PLING", 2.0f);
		}
	}

	/** 서버 주소 비교용: 소문자, 기본 포트와 끝 점 뺌. */
	private static String normalize(String addr) {
		if (addr == null) {
			return "";
		}
		String s = addr.trim().toLowerCase(Locale.ROOT);
		if (s.endsWith(":25565")) {
			s = s.substring(0, s.length() - 6);
		}
		while (s.endsWith(".")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	// ==================== 카드 그리기 ====================

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		pumpSound();
		if (client == null) {
			return;
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		if (isPreview()) {
			String me = LunaCompat.sessionName(client);
			int w = cardWidth(me, true);
			int x = isPreviewBoxed() ? previewCenterX() - w / 2 : sw - w - 6;
			int y = isPreviewBoxed() ? previewCenterY() - CARD_H / 2 : sh - CARD_H - 6;
			drawCard(context, x, y, w, me, null, true, 1f, true);
			return;
		}
		if (cards.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		cards.removeIf(c -> now - c.startMs() > SHOW_MS + OUT_MS);
		float stack = 0f;   // 아래에서부터 쌓인 높이(나가는 카드는 덜 차지)
		for (int i = cards.size() - 1; i >= 0; i--) {
			Card c = cards.get(i);
			long age = now - c.startMs();
			float in = Math.min(1f, age / (float) IN_MS);
			float out = age > SHOW_MS ? Math.min(1f, (age - SHOW_MS) / (float) OUT_MS) : 0f;
			float ease = 1f - (1f - in) * (1f - in) * (1f - in);   // 올라올 때 끝에서 부드럽게
			float alpha = Math.min(in * 1.6f, 1f) * (1f - out);
			int w = cardWidth(c.name(), c.joined());
			int x = sw - w - 6;
			float rise = (1f - ease) * (CARD_H + 12) + out * (CARD_H + 12);
			int y = Math.round(sh - 6 - CARD_H - stack + rise);
			drawCard(context, x, y, w, c.name(), c.mcName(), c.joined(), alpha, false);
			stack += (CARD_H + 4) * ease * (1f - out);
		}
	}

	private int cardWidth(String name, boolean joined) {
		int t1 = LunaCompat.getTextWidth(client.font, title(joined));
		int t2 = LunaCompat.getTextWidth(client.font, body(name, joined));
		return 6 + 16 + 6 + Math.max(t1, t2) + 8;
	}

	private static String title(boolean joined) {
		return joined ? "친구 접속" : "친구 퇴장";
	}

	private static String body(String name, boolean joined) {
		return name + (joined ? " 님이 들어왔어요" : " 님이 나갔어요");
	}

	private void drawCard(GuiGraphicsExtractor ctx, int x, int y, int w, String name, String mcName, boolean joined,
			float alpha, boolean self) {
		int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
		if (a < 10) {
			return;   // 알파가 너무 작으면 옛 버전에서 글자가 불투명으로 그려진다
		}
		drawHudBox(ctx, x, y, w, CARD_H, alpha);
		Identifier skin = self ? LunaCompat.playerSkinTexture(client)
				: mcName == null ? null : LunaCompat.playerSkinByName(client, mcName);
		if (skin != null) {
			kr.lunaslight.mod.gui.LunaGfx.drawPlayerFace(ctx, skin, x + 6, y + 5, 16, (a << 24) | 0xFFFFFF);
		}
		int tx = x + 6 + 16 + 6;
		int accent = joined ? 0x7CE08A : 0xA0A4AA;
		LunaCompat.drawHudText(ctx, client.font, title(joined), tx, y + 4, (a << 24) | accent);
		LunaCompat.drawHudText(ctx, client.font, body(name, joined), tx, y + 14, (a << 24) | 0xFFFFFF);
	}
}
