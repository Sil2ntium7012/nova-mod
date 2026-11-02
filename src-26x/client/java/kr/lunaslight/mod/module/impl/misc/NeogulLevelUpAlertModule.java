package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.ChatState;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-496차(사용자 사진: "토벌 레벨업 조건을 채웠습니다! /레벨 에서 레벨을 올릴 수 있습니다." - "기본 설정으로 켜져 있게 하고, 이런 식으로
 * 레벨업하면 저렇게 메시지 뜨는데 5가지 레벨 종류 모두 저거 뜨는 순간 소리와 함께 타이틀을 띄워 줘"): <b>레벨업 알림</b>.
 *
 * <p>채팅 한 줄에 "○○ 레벨업 조건을 채웠습니다"가 있으면 레벨업 소리 + 화면 가운데 큰 제목("○○ 레벨업 가능!").
 * 분야 이름은 메시지에서 그대로 읽어서 분야가 몇 개든(농사, 토벌, 채광, 낚시 …) 다 된다. 몇 초 안에 여러 분야가 오면
 * 제목에 같이 적는다. 제목은 허수아비 알림처럼 우리 HUD로 그린다(26.2부터 Gui.setTitle이 없다).
 */
public class NeogulLevelUpAlertModule extends Module {

	private static final Pattern READY = Pattern.compile("([가-힣A-Za-z0-9]+)\\s*레벨\\s*업\\s*조건을\\s*채웠");
	private static final long SHOW_MS = 4000L;
	/** 이 시간 안에 온 다른 분야는 같은 제목에 붙인다. */
	private static final long MERGE_MS = 1500L;

	private final BooleanSetting sound = register(new BooleanSetting(
			"sound", "알림음", "레벨업 조건을 채우면 소리를 냅니다.", true));
	private final BooleanSetting title = register(new BooleanSetting(
			"title", "타이틀", "레벨업 조건을 채우면 화면 가운데에 큰 글씨를 띄웁니다.", true));

	private final List<String> fields = new ArrayList<>();
	private volatile long shownAt;
	private int pendingDing;

	public NeogulLevelUpAlertModule() {
		super("neogul_levelup_alert", "레벨업 알림", ModuleCategory.SERVER, "레벨업 조건을 채우면 소리 + 타이틀");
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
		// 서버 아이콘 글자(사용자 영역 U+E000~) 빼고 본다
		StringBuilder sb = new StringBuilder(line.length());
		line.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000)).forEach(sb::appendCodePoint);
		Matcher m = READY.matcher(sb);
		if (!m.find()) {
			return;
		}
		String field = m.group(1);
		long now = System.currentTimeMillis();
		synchronized (fields) {
			if (now - shownAt > MERGE_MS) {
				fields.clear();
			}
			if (!fields.contains(field)) {
				fields.add(field);
			}
		}
		shownAt = now;
		if (sound.get()) {
			LunaCompat.playUiSound(client, "ENTITY_PLAYER_LEVELUP", 1.0f);
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_PLING", 1.5f);
			pendingDing = 2;
		}
	}

	@Override
	public void onTick() {
		if (pendingDing > 0 && client != null) {
			pendingDing--;
			LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_PLING", pendingDing == 1 ? 1.78f : 2.0f);
		}
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
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
		String names;
		int color;
		synchronized (fields) {
			names = preview || fields.isEmpty() ? "토벌" : String.join(", ", fields);
			color = preview || fields.size() != 1 ? 0xFFD54A : colorOf(fields.get(0));
		}
		int alpha = Math.round(255 * a) << 24;
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int cy = sh / 2 - 40;
		drawScaled(ctx, names + " 레벨업 가능!", sw / 2, cy, 3f, alpha | color);
		drawScaled(ctx, "/레벨 에서 레벨을 올릴 수 있어요", sw / 2, cy + 32, 1.5f, alpha | 0xFFFFFF);
	}

	private static int colorOf(String field) {
		return switch (field) {
			case "농사" -> 0x7CDB5A;
			case "토벌" -> 0xFF6B5A;
			case "채광" -> 0x8FD8FF;
			case "낚시" -> 0x5AA8FF;
			default -> 0xFFD54A;
		};
	}

	/** 가운데 정렬 + 배율 글씨(2D 변환이 안 되는 옛 버전은 1배). */
	private void drawScaled(GuiGraphicsExtractor ctx, String s, int cx, int y, float scale, int argb) {
		int w = LunaCompat.getTextWidth(client.font, s);
		if (!LunaCompat.guiTransformSupported(ctx)) {
			LunaCompat.drawHudText(ctx, client.font, s, cx - w / 2, y, argb);
			return;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, cx - w * scale / 2f, y);
			LunaCompat.guiScale(ctx, scale, scale);
			LunaCompat.drawHudText(ctx, client.font, s, 0, 0, argb);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}
}
