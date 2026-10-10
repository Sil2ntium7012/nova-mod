package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaRecorder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.glfw.GLFW;

/**
 * 49-76차(6-5): <b>녹화</b> - 키 하나로 게임 창을 영상(mp4)으로 남긴다. 실제 촬영은 런처의 ffmpeg가 한다.
 *
 * <p>사용자: "녹화도 소리랑 같이 [일반]으로 이동 / 크기·초당 장수 설정할 필요 X, 무조건 60fps에 720 or 1080,
 * 최대 길이 없음, 대신 12시간마다 끊고 새로 시작". 그래서 설정은 <b>키 · 화질(720p/1080p) · 표시</b>뿐이다.
 * 60fps와 12시간 분할은 고정이라 설정에 없다. (49-77차: 표시 위치는 다른 HUD처럼 옮길 수 있다 - 왼쪽 위 고정이
 * FPS 표시(왼쪽 위 4,4)와 겹쳤다. 기본은 왼쪽 아래, 노래 표시 바로 위.)
 *
 * <p>49-72차의 GIF는 이 요청을 물리적으로 못 채워서 버렸다(1080p60 GIF는 1분에 수 GB). 어떻게 바꿨는지,
 * 왜 게임이 안 끊기는지는 {@link LunaRecorder} 주석에 있다.
 *
 * <p><b>런처가 필요하다.</b> 런처 없이 켠 게임에서는 키를 눌러도 "런처로 실행해야 한다"고만 나온다 -
 * 안 되는 걸 되는 척하지 않는다. 처음 한 번은 런처가 ffmpeg(약 30MB)를 받는 동안 기다려야 한다.
 * <b>소리는 안 담긴다.</b>
 */
public class RecorderModule extends Module {

	public enum Quality {
		P720("720p", 720),
		P1080("1080p", 1080);

		private final String label;
		final int height;

		Quality(String label, int height) {
			this.label = label;
			this.height = height;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final KeybindSetting key = register(new KeybindSetting(
			"key", "녹화 키", "누르면 녹화를 시작하고, 다시 누르면 끝냅니다.", GLFW.GLFW_KEY_UNKNOWN));
	private final EnumSetting<Quality> quality = register(new EnumSetting<>(
			"quality", "화질", "세로 크기입니다. 창이 이보다 작으면 창 크기 그대로 담깁니다. 60fps 고정.", Quality.P1080, Quality.class));
	private final BooleanSetting indicator = register(new BooleanSetting(
			"indicator", "녹화 표시", "녹화 중에 빨간 점과 지난 시간을 띄웁니다.", true));
	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "녹화 표시가 뜨는 자리입니다.", HudPosition.of(HudPosition.Anchor.BOTTOM_LEFT, 4, 24)));

	private boolean wasDown;
	private String notice;
	private long noticeUntil;

	public RecorderModule() {
		super("recorder", "녹화", ModuleCategory.FEATURE, "키 하나로 영상 남기기 (60fps | 12시간마다 새 파일)");
		settingsPage(kr.lunaslight.mod.module.SettingsPage.GENERAL);   // 6-5: 소리와 같이 [일반]으로
		quality.onChange(() -> LunaRecorder.height = quality.get().height);
		LunaRecorder.height = quality.get().height;
	}

	@Override
	protected void onDisable() {
		wasDown = false;
		if (LunaRecorder.recording && client != null) {
			LunaRecorder.toggle(client);   // 기능을 끄면 녹화도 끝낸다
		}
	}

	@Override
	public void onTick() {
		if (client == null || WindowAccess.of(client) == null) {
			return;
		}
		if (!key.isBound()) {
			wasDown = false;
			return;
		}
		// 화면(채팅·설정)이 떠 있으면 키를 가로채지 않는다
		boolean down = client.currentScreen == null && key.isDown(client);
		if (down && !wasDown) {
			String why = LunaRecorder.toggle(client);
			if (why != null) {
				notice = why;
				noticeUntil = System.currentTimeMillis() + 4000;
			}
		}
		wasDown = down;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			if (indicator.get()) {   // 49-88차(8-12): 표시를 껐으면 미리보기도 빈다
				drawDot(context, previewCenterX() - 18, previewCenterY() - 4, "0:03");
			}
			return;
		}
		LunaRecorder.poll();
		String text;
		boolean dot = false;
		if (notice != null && System.currentTimeMillis() < noticeUntil) {
			text = "§7" + notice;
		} else if (indicator.get() && LunaRecorder.showRecording()) {
			int s = (int) LunaRecorder.elapsed();
			text = (s / 3600 > 0 ? (s / 3600) + ":" : "")
				+ String.format(java.util.Locale.ROOT, "%02d:%02d", (s / 60) % 60, s % 60);
			dot = true;
		} else if (LunaRecorder.saving()) {
			text = "§7녹화 저장하는 중…";   // 49-322차: 끄기를 누르자마자
		} else if (LunaRecorder.recentSaved() != null) {
			String f = LunaRecorder.recentSaved();   // 49-322차: 런처가 파일을 닫은 뒤 5초
			text = f.isEmpty() ? "§a녹화 저장됨 §7(clips 폴더)" : "§a녹화 저장됨 §7clips/" + f;
		} else if (LunaRecorder.recentError() != null) {
			text = "§c" + LunaRecorder.recentError();
		} else {
			return;
		}
		int w = (dot ? 11 : 0) + LunaCompat.getTextWidth(client.textRenderer, text);
		int h = 16;
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), w);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), h);
		int ty = y + Math.round(h / 2f - LunaCompat.textVisualCenter());
		if (dot) {
			drawDot(context, x, ty, text);
		} else {
			LunaCompat.drawHudText(context, client.textRenderer, text, x, ty, 0xFFFFFFFF);
		}
	}

	/** 깜빡이는 빨간 점 + 지난 시간. */
	private void drawDot(DrawContext ctx, int x, int y, String time) {
		boolean on = isPreview() || (System.currentTimeMillis() / 600) % 2 == 0;
		LunaDraw.circle(ctx, x + 3, y + 4, 6, on ? 0xFFEF4444 : 0x66EF4444);
		LunaCompat.drawHudText(ctx, client.textRenderer, "§c" + time, x + 11, y, 0xFFFFFFFF);
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
