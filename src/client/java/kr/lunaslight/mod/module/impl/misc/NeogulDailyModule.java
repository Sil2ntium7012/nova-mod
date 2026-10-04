package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 49-133차(사용자: "너굴 추천 및 핫타임 - 너굴마을 추천하면 체크 표시가 뜨는 UI + 핫타임까지 남은 시간과 받았다는 표시",
 * 감지 = 서버 채팅 메시지): <b>너굴 추천 핫타임</b> HUD. 49-134차: 핫타임은 위키 기준 매일 저녁 8시(20시).
 *
 * <pre>
 *  추천    [v]
 *  핫타임  [ ] 02:13:05
 * </pre>
 * 추천/핫타임 보상 메시지가 채팅에 오면({@link ChatState#addPlainListener}) 그날 칸에 체크가 들어가고, 자정에 풀린다.
 * 감지 낱말은 아래 REC_WORDS / HOT_WORDS(코드)에서 고친다. 잘못 잡히면 [체크 바꾸기]로 직접 고친다.
 * 핫타임 시각이 되면 알림음 한 번.
 */
public class NeogulDailyModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 130)));

	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));

	// 49-167차(사용자: "핫타임 시간은 고정, 너굴마을에서만 설정은 없애, 앞의 에메랄드/시계 없애"): 핫타임 20시 고정(위키),
	// 너굴마을(mcng.kr) 접속 중에만 보임 - 서버 기능이라 설정으로 두지 않는다. 아이템 아이콘 없이 글자와 체크 칸만.
	private static final int HOT_HOUR = 20;

	private final BooleanSetting alarm = register(new BooleanSetting(
			"alarm", "핫타임 알림음", "핫타임 시각이 되면 소리를 한 번 냅니다(아직 안 받았을 때만).", true));

	// 49-143차(사용자: "핫타임/추천 감지 글은 내가 설정할 거야 개인 설정 없애"): 감지 낱말은 설정이 아니라 여기서 고친다.
	// 채팅 한 줄에서 띄어쓰기를 빼고 이 낱말(띄어쓰기 빼고)이 들어 있으면 체크된다.
	/** 추천 완료로 보는 채팅 낱말. */
	private static final List<String> REC_WORDS = List.of("추천 보상", "추천해 주셔서", "추천 감사");
	/** 핫타임 받음으로 보는 채팅 낱말. */
	private static final List<String> HOT_WORDS = List.of("핫타임 보상", "핫타임 지급");
	/** 핫타임 시각부터 이 분 동안은 "지금"(받을 수 있는 때). 그 뒤에도 못 받았으면 그날은 X. */
	private static final int HOT_WINDOW_MIN = 10;

	/** 저장용: 날짜|추천|핫타임|알림 울림. */
	private final StringSetting state = register(new StringSetting("state", "상태", "", ""));

	public NeogulDailyModule() {
		super("neogul_daily", "너굴 추천 핫타임", ModuleCategory.SERVER, "추천 체크 | 핫타임까지 남은 시간");
		serverGroup("너굴마을");
		state.hidden();
		register(new ActionSetting("toggle_rec", "추천 체크", "오늘 추천 체크를 직접 켜거나 끕니다.", "체크 전환",
				() -> set(1, !flag(1))));
		register(new ActionSetting("toggle_hot", "핫타임 체크", "오늘 핫타임 받음 체크를 직접 켜거나 끕니다.", "체크 전환",
				() -> set(2, !flag(2))));
		ChatState.addPlainListener(this::onChat);
	}

	// ==================== 상태 ====================

	private String[] parts() {
		String today = LocalDate.now().toString();
		String v = state.get();
		String[] p = v == null ? new String[0] : v.split("\\|");
		if (p.length < 4 || !today.equals(p[0])) {
			p = new String[]{today, "0", "0", "0"};
			state.setValue(String.join("|", p));
		}
		return p;
	}

	private boolean flag(int i) {
		return "1".equals(parts()[i]);
	}

	private void set(int i, boolean on) {
		String[] p = parts();
		String nv = on ? "1" : "0";
		if (!nv.equals(p[i])) {
			p[i] = nv;
			state.setValue(String.join("|", p));
		}
	}

	private boolean here() {
		return NeogulData.onNeogul(client);
	}

	private void onChat(String line) {
		if (!isEnabled() || line == null || line.isEmpty() || !here()) {
			return;
		}
		if (line.startsWith("<")) {
			return;   // 바닐라 형식 플레이어 채팅
		}
		String l = line.replace(" ", "");
		if (!flag(1) && matches(l, REC_WORDS)) {
			set(1, true);
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_CHIME", 1.4f);
		}
		if (!flag(2) && matches(l, HOT_WORDS)) {
			set(2, true);
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_CHIME", 1.6f);
		}
	}

	private static boolean matches(String compact, List<String> words) {
		for (String w : words) {
			String c = w.replace(" ", "");
			if (!c.isEmpty() && compact.contains(c)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null || !here()) {
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		if (alarm.get() && now.getHour() >= HOT_HOUR && !flag(2) && !flag(3)) {
			set(3, true);
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", 1.2f);
		}
	}

	/** 오늘 핫타임을 놓쳤는지(시각 + 10분이 지났는데 못 받음). 자정에 풀린다. */
	private boolean missed(boolean received) {
		return !received && !LocalDateTime.now().isBefore(LocalDate.now().atTime(HOT_HOUR, 0).plusMinutes(HOT_WINDOW_MIN));
	}

	/**
	 * 핫타임 칸 오른쪽 글자: 남은 시간, 시작하고 10분 동안 아직 안 받았으면 "지금".
	 * 49-143차(사용자: "그날에 못받으면 그날동안 X, 하루 지나면 없어지면서 시간으로"): 놓치면 글자 없이 X만.
	 */
	private String hotText(boolean received) {
		LocalDateTime now = LocalDateTime.now();
		LocalDateTime today = LocalDate.now().atTime(HOT_HOUR, 0);
		boolean passed = !now.isBefore(today);
		if (missed(received)) {
			return "";
		}
		if (!received && passed) {
			return "지금";
		}
		LocalDateTime target = received || passed ? today.plusDays(1) : today;
		long s = Math.max(0, Duration.between(now, target).getSeconds());
		return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
	}

	// ==================== 그리기 ====================

	@Override
	public void onHudRender(DrawContext ctx, RenderTickCounter tickCounter) {
		if (!isPreview() && (client.player == null || !here())) {
			return;
		}
		boolean rec = isPreview() || flag(1);
		boolean hot = !isPreview() && flag(2);
		String recLabel = "추천";
		String hotLabel = "핫타임";
		String time = isPreview() ? "02:13:05" : hotText(hot);
		int labelW = Math.max(LunaCompat.getTextWidth(client.textRenderer, recLabel),
				LunaCompat.getTextWidth(client.textRenderer, hotLabel));
		int timeW = LunaCompat.getTextWidth(client.textRenderer, time);
		int boxX = 4 + labelW + 6;
		int w = boxX + 9 + 5 + timeW;
		int h = 16 * 2 + 2;
		int sw = WindowAccess.of(client).getScaledWidth();
		int sh = WindowAccess.of(client).getScaledHeight();
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		drawHudPanel(ctx, x, y, w, h);

		int color = textColor.getArgb();
		LunaCompat.drawHudText(ctx, client.textRenderer, recLabel, x + 4, y + 4, color);
		checkbox(ctx, x + boxX, y + 3, rec);

		int y2 = y + 18;
		LunaCompat.drawHudText(ctx, client.textRenderer, hotLabel, x + 4, y2 + 4, color);
		if (!isPreview() && missed(hot)) {
			crossbox(ctx, x + boxX, y2 + 3);
		} else {
			checkbox(ctx, x + boxX, y2 + 3, hot);
		}
		boolean now = "지금".equals(time);
		if (!time.isEmpty() && (!now || (System.currentTimeMillis() / 500) % 2 == 0)) {
			LunaCompat.drawHudText(ctx, client.textRenderer, time, x + boxX + 14, y2 + 4, now ? 0xFFFFD84A : color);
		}
	}

	/** 놓친 날: 같은 칸에 빨간 X. */
	private static void crossbox(DrawContext ctx, int x, int y) {
		ctx.fill(x, y, x + 9, y + 9, 0xFF8B8B8B);
		ctx.fill(x + 1, y + 1, x + 8, y + 8, 0xFF1E1E1E);
		for (int i = 0; i < 5; i++) {
			ctx.fill(x + 2 + i, y + 2 + i, x + 3 + i, y + 3 + i, 0xFFFF5555);
			ctx.fill(x + 6 - i, y + 2 + i, x + 7 - i, y + 3 + i, 0xFFFF5555);
		}
	}

	/** 바닐라 느낌 체크 칸(9×9): 회색 테두리 + 어두운 속 + 초록 체크. */
	private static void checkbox(DrawContext ctx, int x, int y, boolean on) {
		ctx.fill(x, y, x + 9, y + 9, 0xFF8B8B8B);
		ctx.fill(x + 1, y + 1, x + 8, y + 8, 0xFF1E1E1E);
		if (!on) {
			return;
		}
		int c = 0xFF55FF55;
		// ✔ 모양: 왼쪽 짧은 획 + 오른쪽 긴 획, 2px 굵기
		int[][] px = {{1, 4}, {2, 5}, {3, 6}, {4, 5}, {5, 4}, {6, 3}, {7, 2}};
		for (int[] p : px) {
			ctx.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 2, c);
		}
	}
}
