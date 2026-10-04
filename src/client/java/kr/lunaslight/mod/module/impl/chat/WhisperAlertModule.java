package kr.lunaslight.mod.module.impl.chat;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.WindowAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/**
 * 49-67차(5-5): <b>귓속말 알림</b>.
 *
 * <p>49-121차에 화면 카드를 빼고 소리만 냈다. 49-246차(사용자: "귓속말 오면 인게임에서도 접속 알림처럼 귓속말 왔다고 알려 주고,
 * 온 거 다 알려 주는 게 아니라 딱 온 순간 알려 주는 것, 오랫동안 안 오고 다시 왔을 때 그때 또 알림"): 친구 접속 알림과 같은 카드
 * (오른쪽 아래에서 올라와 4초 머묾, 보낸 사람 얼굴 + "OO 님이 귓속말을 보냈어요")를 다시 띄운다.
 *
 * <p><b>언제 알리나</b>: 귓속말이 {@value #QUIET_MS}ms(1분) 넘게 안 오다가 새로 왔을 때 한 번. 대화가 이어지는 동안 오는 귓속말은
 * 조용히 넘긴다(앞 귓속말과 1분 안이면 알림 없음). 오래 쉬었다가 다시 오면 그때 또 한 번.
 *
 * <p><b>무엇을 귓속말로 보나</b>: 바닐라 번역 키 {@code commands.message.display.incoming}이 붙은 줄만
 * ({@link ChatState#observe}). 서버가 직접 꾸민 귓속말은 일반 채팅과 구별할 수 없어 건드리지 않는다.
 */
public class WhisperAlertModule extends Module {

	/** 앞 귓속말과 이만큼 떨어져야 다시 알린다(대화 중에는 조용히). */
	private static final long QUIET_MS = 60_000;
	private static final long SHOW_MS = 4_000L;
	private static final long IN_MS = 260L;
	private static final long OUT_MS = 320L;
	private static final int CARD_H = 26;

	private final BooleanSetting card = register(new BooleanSetting(
			"card", "화면 알림", "귓속말이 오면 화면 오른쪽 아래에 알림 카드를 띄웁니다.", true));
	private final BooleanSetting sound = register(new BooleanSetting(
			"sound", "알림음", "귓속말이 오면 종소리를 냅니다.", true));

	/** 마지막으로 본 귓속말 시각. */
	private long seenAtMs;
	/** 지금 떠 있는 카드(없으면 null). */
	private String cardFrom;
	private long cardAtMs;

	public WhisperAlertModule() {
		super("whisper_alert", "귓속말 알림", ModuleCategory.FEATURE, "귓속말이 오면 화면과 소리로 알림");
	}

	@Override
	protected void onEnable() {
		ChatState.watchWhispers = true;
	}

	@Override
	protected void onDisable() {
		ChatState.watchWhispers = false;
		ChatState.lastWhisperFrom = null;
		cardFrom = null;
	}

	@Override
	public void onTick() {
		ChatState.watchWhispers = isEnabled();
		if (client == null) {
			return;
		}
		long at = ChatState.lastWhisperAtMs;
		if (at > seenAtMs && ChatState.lastWhisperFrom != null) {
			// 대화가 이어지는 동안(앞 귓속말 뒤 1분 안)은 조용히 - 오래 안 오다가 새로 올 때만 한 번.
			boolean fresh = at - seenAtMs > QUIET_MS;
			seenAtMs = at;
			if (fresh && System.currentTimeMillis() - at < 1500) {
				if (card.get()) {
					cardFrom = ChatState.lastWhisperFrom;
					cardAtMs = System.currentTimeMillis();
				}
				if (sound.get()) {
					// 채팅 강조 알림음과 다른 음 - 무엇이 왔는지 소리로 구분되게.
					LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", 1.6f);
				}
			}
		}
	}

	// ==================== 카드 ====================

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (client == null) {
			return;
		}
		int sw = WindowAccess.of(client).getScaledWidth();
		int sh = WindowAccess.of(client).getScaledHeight();
		if (isPreview()) {
			String me = LunaCompat.sessionName(client);
			int w = cardWidth(me);
			int x = isPreviewBoxed() ? previewCenterX() - w / 2 : sw - w - 6;
			int y = isPreviewBoxed() ? previewCenterY() - CARD_H / 2 : sh - CARD_H - 6;
			drawCard(context, x, y, w, me, 1f, true);
			return;
		}
		if (cardFrom == null) {
			return;
		}
		long age = System.currentTimeMillis() - cardAtMs;
		if (age > SHOW_MS + OUT_MS) {
			cardFrom = null;
			return;
		}
		float in = Math.min(1f, age / (float) IN_MS);
		float out = age > SHOW_MS ? Math.min(1f, (age - SHOW_MS) / (float) OUT_MS) : 0f;
		float ease = 1f - (1f - in) * (1f - in) * (1f - in);
		float alpha = Math.min(in * 1.6f, 1f) * (1f - out);
		int w = cardWidth(cardFrom);
		int x = sw - w - 6;
		// 친구 접속 카드(오른쪽 맨 아래)와 안 겹치게 한 칸 위
		int baseY = sh - 6 - CARD_H - (CARD_H + 4);
		float rise = (1f - ease) * (CARD_H + 12) + out * (CARD_H + 12);
		drawCard(context, x, Math.round(baseY + rise), w, cardFrom, alpha, false);
	}

	private static final String TITLE = "귓속말";

	private static String body(String name) {
		return name + " 님이 귓속말을 보냈어요";
	}

	private int cardWidth(String name) {
		int t1 = LunaCompat.getTextWidth(client.textRenderer, TITLE);
		int t2 = LunaCompat.getTextWidth(client.textRenderer, body(name));
		return 6 + 16 + 6 + Math.max(t1, t2) + 8;
	}

	private void drawCard(DrawContext ctx, int x, int y, int w, String name, float alpha, boolean self) {
		int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
		if (a < 10) {
			return;   // 알파가 너무 작으면 옛 버전에서 글자가 불투명으로 그려진다
		}
		drawHudBox(ctx, x, y, w, CARD_H, alpha);
		Identifier skin = self ? LunaCompat.playerSkinTexture(client) : LunaCompat.playerSkinByName(client, name);
		if (skin != null) {
			kr.lunaslight.mod.gui.LunaGfx.drawPlayerFace(ctx, skin, x + 6, y + 5, 16, (a << 24) | 0xFFFFFF);
		}
		int tx = x + 6 + 16 + 6;
		LunaCompat.drawHudText(ctx, client.textRenderer, TITLE, tx, y + 4, (a << 24) | 0xC9A0F5);
		LunaCompat.drawHudText(ctx, client.textRenderer, body(name), tx, y + 14, (a << 24) | 0xFFFFFF);
	}
}
