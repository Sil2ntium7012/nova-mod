package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-148차(사용자: "너굴마을 기능에 허수아비 체크 기능 - 채팅에 '허수아비가 나타났습니다! 30초안에 처치해주세요'가 뜨면
 * 감지해서 소리랑 타이틀"): <b>허수아비 알림</b>.
 *
 * <p>채팅 한 줄(색 코드 뺀 글자)에 "허수아비"와 "나타났"이 같이 있으면 알림음을 울리고 화면 가운데에 큰 제목을 띄운다.
 * 제목 아래 줄은 메시지의 "N초"에서 거꾸로 세는 남은 시간(없으면 30초). 제목은 바닐라 타이틀 대신 우리 HUD로 그린다
 * (타이틀 메서드 이름이 40개 버전마다 달라서).
 */
public class NeogulScarecrowModule extends Module {

	private static final Pattern SECONDS = Pattern.compile("(\\d+)\\s*초");
	/** 제목이 떠 있는 시간(ms). 앞 0.2초 나타나기, 뒤 0.6초 사라지기. */
	private static final long SHOW_MS = 4000L;

	private final BooleanSetting sound = register(new BooleanSetting(
			"sound", "알림음", "허수아비가 나타나면 소리를 냅니다.", true));
	private final BooleanSetting title = register(new BooleanSetting(
			"title", "타이틀", "허수아비가 나타나면 화면 가운데에 큰 글씨를 띄웁니다.", true));
	// 49-167차: 너굴마을(mcng.kr) 접속 중에만 - 서버 기능이라 설정으로 두지 않는다.

	private long shownAt;
	private long deadline;
	private int pendingDing;

	public NeogulScarecrowModule() {
		super("neogul_scarecrow", "허수아비 알림", ModuleCategory.SERVER, "허수아비가 나타나면 소리 + 타이틀");
		serverGroup("너굴마을");
		defaultEnabled(true);
		ChatState.addPlainListener(this::onChat);
	}

	private void onChat(String line) {
		if (!isEnabled() || line == null || line.isEmpty() || line.startsWith("<")) {
			return;
		}
		if (!NeogulData.onNeogul(client)) {
			return;
		}
		String l = line.replace(" ", "");
		if (!l.contains("허수아비") || !l.contains("나타났")) {
			return;
		}
		int secs = 30;
		Matcher m = SECONDS.matcher(line);
		if (m.find()) {
			try {
				secs = Math.max(1, Math.min(600, Integer.parseInt(m.group(1))));
			} catch (NumberFormatException ignored) {
			}
		}
		long now = System.currentTimeMillis();
		shownAt = now;
		deadline = now + secs * 1000L;
		if (sound.get()) {
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", 1.0f);
			pendingDing = 2;   // 틱마다 한 번씩 두 번 더(딩-딩-딩)
		}
	}

	@Override
	public void onTick() {
		if (pendingDing > 0 && client != null) {
			pendingDing--;
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", pendingDing == 1 ? 1.26f : 1.5f);
		}
	}

	@Override
	public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
		long now = System.currentTimeMillis();
		boolean preview = isPreview();
		if (!preview && (!title.get() || shownAt == 0 || now - shownAt > SHOW_MS)) {
			return;
		}
		long age = preview ? 1000 : now - shownAt;
		float a = age < 200 ? age / 200f : age > SHOW_MS - 600 ? Math.max(0f, (SHOW_MS - age) / 600f) : 1f;
		if (a <= 0.02f) {
			return;
		}
		int alpha = Math.round(255 * a) << 24;
		long left = preview ? 30 : Math.max(0, (deadline - now + 999) / 1000);
		String big = "허수아비 등장!";
		String small = left > 0 ? left + "초 안에 처치하세요" : "시간 종료";
		int sw = WindowAccess.of(client).getScaledWidth();
		int sh = WindowAccess.of(client).getScaledHeight();
		int cy = sh / 2 - 40;
		drawScaled(ctx, big, sw / 2, cy, 3f, alpha | 0xFFAA00);
		drawScaled(ctx, small, sw / 2, cy + 32, 1.5f, alpha | 0xFFFFFF);
	}

	/** 가운데 정렬 + 배율 글씨(2D 변환이 안 되는 옛 버전은 1배). */
	private void drawScaled(DrawContext ctx, String s, int cx, int y, float scale, int argb) {
		int w = LunaCompat.getTextWidth(client.textRenderer, s);
		if (!LunaCompat.guiTransformSupported(ctx)) {
			LunaCompat.drawHudText(ctx, client.textRenderer, s, cx - w / 2, y, argb);
			return;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, cx - w * scale / 2f, y);
			LunaCompat.guiScale(ctx, scale, scale);
			LunaCompat.drawHudText(ctx, client.textRenderer, s, 0, 0, argb);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}
}
