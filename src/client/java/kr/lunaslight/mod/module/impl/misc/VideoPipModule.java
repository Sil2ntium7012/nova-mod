package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaPip;
import kr.lunaslight.mod.util.WindowAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/**
 * 49-177차(사용자: "내가 보고 있는 동영상 플랫폼 마크 전체화면 하더라도 보이는 기능"): <b>보고 있는 영상</b>.
 *
 * <p>브라우저(유튜브, SOOP, 치지직, 트위치, 넷플릭스 등)에서 보던 영상 창을 런처가 작게 찍어 보내 주고, 이 기능은
 * 그걸 HUD 구석에 그린다. 마크가 전체 화면이어도 보인다 - 게임 화면 안에 그리는 것이기 때문이다.
 * 소리는 원래 브라우저에서 그대로 난다. 브라우저 창을 <b>최소화하면</b> 윈도우가 그리기를 멈춰서 화면이 멈춘다.
 *
 * <p>49-179차(사용자: "뭘 어떻게 하는지 모르겠어 - 켜 놓은 목록 중에 골라서 트는 느낌으로, 크기는 HUD 설정에서,
 * 비율은 쇼츠 or 일반만"): [영상 고르기]로 지금 열린 창 목록에서 하나를 고른다(고른 창 이름은 저장). 크기 설정은
 * 없애고 HUD 편집기의 크기 조절(배율)을 쓴다 - 배율은 가로세로를 같이 키우므로 비율이 안 바뀐다. 비율은 일반(16:9)과
 * 쇼츠(9:16) 둘뿐이고, 찍어 온 창은 그 비율로 <b>가운데를 잘라</b> 채운다(찌그러지지 않게).
 */
public class VideoPipModule extends Module {

	public enum Ratio {
		NORMAL("일반"),
		SHORTS("쇼츠");

		private final String label;

		Ratio(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	/** 배율 1일 때의 크기(HUD 편집기에서 키우면 이 비율 그대로 커진다). */
	private static final int NORMAL_W = 160, NORMAL_H = 90;
	private static final int SHORTS_W = 72, SHORTS_H = 128;

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "영상 창이 뜨는 자리입니다. 크기는 HUD 편집기에서 조절합니다.", HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 6, 6)));
	private final EnumSetting<Ratio> ratio = register(new EnumSetting<>(
			"ratio", "비율", "일반 영상(16:9) 또는 쇼츠(9:16)입니다.", Ratio.NORMAL, Ratio.class).style());
	// 49-187차(사용자: "프레임도 60으로"): 최대 60, 기본 60(예전 값 15/30이 남지 않게 설정 이름을 새로)
	private final IntSetting fps = register(new IntSetting(
			"frame_rate", "프레임", "1초에 몇 장을 받아 올지입니다. 높을수록 부드럽고 컴퓨터가 조금 더 일합니다.", 60, 10, 60, 5));
	// 49-187차(사용자: "화면만 딱 보여주고"): 브라우저 창 전체가 아니라 영상이 나오는 부분만 잘라 온다(움직이는 곳을 찾아서)
	private final BooleanSetting cropVideo = register(new BooleanSetting(
			"crop", "영상 부분만", "브라우저의 주소창, 채팅, 글자는 빼고 영상이 나오는 부분만 보여 줍니다. 영상이 멈춰 있으면 마지막 자리를 씁니다.", true));
	private final IntSetting opacity = register(new IntSetting(
			"opacity", "불투명도", "100이면 또렷하고, 낮출수록 게임 화면이 비쳐 보입니다.", 100, 30, 100, 5).unit("%"));
	private final BooleanSetting showName = register(new BooleanSetting(
			"show_name", "창 이름", "영상 아래에 찍고 있는 창의 이름을 보여 줍니다.", false));
	/** 고른 창 이름(빈 값 = 자동: 영상 사이트 창 중 하나). */
	private final StringSetting window = register(new StringSetting("window", "고른 창", "", "")).hidden();
	/**
	 * 49-189차(사용자: "화면 초점도 이상한 곳에 있어"): 직접 고른 영상 자리("x,y,w,h", 창 전체를 0~1로 본 값). 비어 있으면
	 * 자동(움직이는 곳 찾기). [영상 고르기] 화면의 [영역 지정]에서 끌어서 정한다. 창을 바꾸면 자동으로 돌아간다.
	 */
	private final StringSetting area = register(new StringSetting("area", "영상 자리", "", "")).hidden();

	private boolean requested;
	private int sentFps = -1, sentW = -1;
	private boolean sentCrop;
	private Ratio sentRatio;
	private String sentArea = "";
	/** 49-189차: [영역 지정] 화면이 열려 있는 동안 - 창 전체를 받아 온다. */
	private boolean areaPicking;
	private boolean sentPicking;
	/** 49-189차: 게임 커서를 숨기고 대신 그리고 있는지. */
	private boolean cursorHidden;
	private String sentWindow = null;

	private Identifier texId;
	private int texW, texH;
	private int flip;

	public VideoPipModule() {
		super("video_pip", "보고 있는 영상", ModuleCategory.HUD, "열어 둔 영상 창 중 하나를 골라 게임 화면 구석에");
		register(new ActionSetting("pick_window", "영상 고르기", "지금 열려 있는 영상/브라우저 창 목록에서 볼 창을 고릅니다.", "고르기", () ->
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.LunaVideoPickScreen(client.currentScreen, this))));
		// 49-187차(사용자: "그 회색 테두리 없애고 네모로 딱 끊어줘"): 배경/윤곽선 설정 없음 - 영상만 네모로 그린다.
		// 49-189차(사용자: "영상 틀었을 때 게임 안 마우스가 안 보여"): 창을 찍는 동안 윈도우가 마우스를 소프트웨어로 그리게 바뀌어
		// 전체 화면 마크에선 커서가 안 보인다. 찍는 동안에만 게임 화면(메뉴, 인벤토리) 위에 커서를 직접 그린다.
		LunaCompat.registerScreenAfterRender(this::drawSoftCursor);
	}

	/** 목록 화면에서 부른다. 빈 문자열이면 자동. */
	public void chooseWindow(String name) {
		String n = name == null ? "" : name;
		if (!n.equals(window.get())) {
			area.setValue("");   // 다른 창이면 직접 고른 자리는 맞지 않는다 - 자동으로
		}
		window.setValue(n);
		kr.lunaslight.mod.config.LunaClientConfig.save();
		if (isEnabled()) {
			sendStart();
		}
	}

	public String chosenWindow() {
		return window.get();
	}

	private int baseW() {
		return ratio.get() == Ratio.SHORTS ? SHORTS_W : NORMAL_W;
	}

	private int baseH() {
		return ratio.get() == Ratio.SHORTS ? SHORTS_H : NORMAL_H;
	}

	/**
	 * 찍어 올 폭 = 화면에 그려질 <b>실제 픽셀 폭 그대로</b>(49-187차: "화질 좋게"). 런처가 영상 부분만 잘라 이 폭으로 줄여
	 * 보내므로 한 픽셀이 한 픽셀로 그려진다(더 크게 받아 줄이면 마크의 텍스처는 계단지고, 작게 받으면 뭉개진다).
	 */
	private int captureWidth() {
		float px = baseW() * Math.max(1, LunaCompat.currentGuiScale()) * hudScale();
		return Math.max(160, Math.min(1920, Math.round(px / 16f) * 16));
	}

	private void sendStart() {
		sentFps = fps.get();
		sentW = captureWidth();
		sentWindow = window.get();
		sentCrop = cropVideo.get();
		sentRatio = ratio.get();
		sentArea = area.get();
		sentPicking = areaPicking;
		if (areaPicking) {
			// 영역 지정 중: 창 전체를 넉넉한 크기로(자르지 않음)
			LunaPip.request(true, Math.min(30, sentFps), 960, sentWindow, false, "normal", "");
		} else {
			LunaPip.request(true, sentFps, sentW, sentWindow, sentCrop, sentRatio == Ratio.SHORTS ? "shorts" : "normal",
					sentCrop ? sentArea : "");
		}
		requested = true;
	}

	// ==================== 49-189차: 영역 지정 화면이 쓰는 것 ====================

	public void setAreaPicking(boolean on) {
		if (areaPicking != on) {
			areaPicking = on;
			if (isEnabled()) {
				sendStart();
			}
		}
	}

	/** "x,y,w,h"(0~1) 또는 빈 값(자동). */
	public String area() {
		return area.get();
	}

	public void setArea(String value) {
		area.setValue(value == null ? "" : value);
		kr.lunaslight.mod.config.LunaClientConfig.save();
	}

	public boolean shortsRatio() {
		return ratio.get() == Ratio.SHORTS;
	}

	/** 새 그림을 받아 텍스처로(HUD가 안 그려지는 화면에서도 영역 지정 화면이 부른다). */
	public void pumpFrames() {
		LunaPip.poll();
		swapInFrame();
	}

	public Identifier frameTexture() {
		return System.currentTimeMillis() - LunaPip.lastFrameMs < 3000 ? texId : null;
	}

	public int frameWidth() {
		return texW;
	}

	public int frameHeight() {
		return texH;
	}

	@Override
	protected void onEnable() {
		sendStart();
	}

	@Override
	protected void onDisable() {
		LunaPip.request(false, 0, 0, window.get());
		requested = false;
		dropTexture();
		restoreCursor();
		dropCursorTexture();
	}

	/** 49-222차: 고른 창을 런처가 못 찾기 시작한 때(0이면 정상). */
	private long lostSince;

	@Override
	public void onTick() {
		// 49-222차: 게임을 다시 켜거나 영상이 바뀌면 탭 제목이 달라져 고른 창을 못 찾고 멈춘다("고른 창이 닫혔거나 이름이 바뀜").
		// 그 이름이 창 목록에도 없는 채로 3초가 지나면 [자동](영상 사이트 창 중에서)으로 돌린다.
		String err = LunaPip.error;
		boolean lost = LunaPip.launcherPresent && !LunaPip.running && !window.get().isEmpty()
				&& err != null && err.contains("고른 창") && !LunaPip.windows.contains(window.get());
		if (!lost) {
			lostSince = 0;
		} else if (lostSince == 0) {
			lostSince = System.currentTimeMillis();
		} else if (System.currentTimeMillis() - lostSince > 3000) {
			lostSince = 0;
			chooseWindow("");
		}
		// 영상이 바뀌면 탭 제목(창 이름)도 바뀐다 - 고른 창을 지금 찍고 있는 창 이름으로 따라가 둔다(다시 부탁은 안 함)
		String live = LunaPip.window;
		if (LunaPip.running && !window.get().isEmpty() && live != null && !live.isEmpty() && !live.equals(window.get())
				&& window.get().equals(sentWindow)) {
			window.setValue(live);
			sentWindow = live;
		}
		// 설정 파일에서 켜진 채로 시작했거나 프레임/크기/고른 창이 바뀌었으면 다시 부탁
		if (!requested || sentFps != fps.get() || sentW != captureWidth() || !window.get().equals(sentWindow)
				|| sentCrop != cropVideo.get() || sentRatio != ratio.get() || !area.get().equals(sentArea)
				|| sentPicking != areaPicking) {
			sendStart();
		}
		// 화면이 닫혔거나(게임으로 돌아감) 더는 찍지 않으면 숨겨 둔 커서를 돌려놓는다
		if (cursorHidden && (client.currentScreen == null || !softCursorWanted())) {
			restoreCursor();
		}
	}

	// ==================== 49-189차: 찍는 동안 게임 커서를 직접 그린다 ====================

	/** 윈도우 기본 화살표 모양(B = 검은 테두리, W = 흰색). */
	private static final String[] ARROW = {
		"B", "BB", "BWB", "BWWB", "BWWWB", "BWWWWB", "BWWWWWB", "BWWWWWWB", "BWWWWWWWB", "BWWWWWWWWB",
		"BWWWWWWWWWB", "BWWWWWWBBBBB", "BWWWBWWB", "BWWBBWWB", "BWB  BWWB", "BB   BWWB", "B     BWWB", "      BWWB", "       BB",
	};

	// 49-195차(사용자: "pip 했을 때 마우스 포인터가 윈도우 기본으로 뜸 - 커스텀 마우스 포인터 쓰는데 사라짐"): 위 모양 대신
	// 지금 윈도우에 설정된 화살표 포인터 그림(SystemCursor)을 텍스처로 올려 그 크기 그대로 그린다. 못 읽으면 위 모양.
	private boolean cursorTried;
	private Identifier cursorTex;
	private int cursorW, cursorH, cursorHotX, cursorHotY;

	private void loadCursorTexture() {
		if (cursorTried) {
			return;
		}
		cursorTried = true;
		kr.lunaslight.mod.util.SystemCursor.Image ci = kr.lunaslight.mod.util.SystemCursor.arrow();
		if (ci == null) {
			return;
		}
		NativeImage img = LunaPip.imageFromRgba(ci.rgba, 0, ci.width, ci.height);
		if (img == null) {
			return;
		}
		Identifier id = LunaCompat.registerImageTexture(client, "pip/cursor", img);
		if (id == null) {
			return;
		}
		cursorTex = id;
		cursorW = ci.width;
		cursorH = ci.height;
		cursorHotX = ci.hotX;
		cursorHotY = ci.hotY;
	}

	private void dropCursorTexture() {
		if (cursorTex != null) {
			LunaCompat.unregisterImageTexture(client, cursorTex, null);
			cursorTex = null;
		}
		cursorTried = false;   // 다음에 켤 때 포인터 테마를 다시 읽는다
	}

	private boolean softCursorWanted() {
		// 49-235차(사용자: "마우스 포인터가 그냥 계속 안 보여"): 예전엔 "3초 안에 새 그림이 왔을 때"만 그렸다. 런처는 찍는 창 화면이
		// 안 바뀌면(멈춘 영상, 최소화, 찍기 실패 - 런처 로그의 ProcessFrame failed) 새 장을 안 보내는데, 찍는 동안 윈도우는
		// 커서를 계속 숨긴다 - 그래서 포인터가 아예 안 보였다. 런처가 찍고 있는 동안은 늘 그린다.
		return isEnabled() && LunaPip.running;
	}

	private void drawSoftCursor(Object screen, DrawContext ctx, int mouseX, int mouseY) {
		if (!softCursorWanted() || screen == null) {
			return;
		}
		long handle;
		try {
			handle = WindowAccess.of(client).getHandle();
			if (org.lwjgl.glfw.GLFW.glfwGetInputMode(handle, org.lwjgl.glfw.GLFW.GLFW_CURSOR) == org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL) {
				org.lwjgl.glfw.GLFW.glfwSetInputMode(handle, org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN);
			}
			cursorHidden = true;
		} catch (Throwable t) {
			return;
		}
		// 실제 픽셀 단위로(GUI 배율에 따라 커지지 않게), 1080p 기준 한 칸 = 1픽셀
		int gs = Math.max(1, LunaCompat.currentGuiScale());
		double[] mx = new double[1], my = new double[1];
		org.lwjgl.glfw.GLFW.glfwGetCursorPos(handle, mx, my);
		int winH = Math.max(1, WindowAccess.of(client).getScaledHeight() * gs);
		int unit = Math.max(1, Math.round(winH / 1080f));
		boolean scaled = LunaCompat.guiTransformSupported(ctx);
		if (scaled) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiScale(ctx, 1f / gs, 1f / gs);
		}
		try {
			int px = scaled ? (int) Math.round(mx[0] * framebufferRatio()) : mouseX;
			int py = scaled ? (int) Math.round(my[0] * framebufferRatio()) : mouseY;
			int u = scaled ? unit : 1;
			loadCursorTexture();
			if (cursorTex != null && scaled
					&& LunaGfx.drawImageRegion(ctx, cursorTex, px - cursorHotX, py - cursorHotY, cursorW, cursorH,
						0, 0, cursorW, cursorH, cursorW, cursorH, 0xFFFFFFFF)) {
				return;   // 사용자 포인터 그림 그대로(실제 픽셀 크기)
			}
			for (int r = 0; r < ARROW.length; r++) {
				String row = ARROW[r];
				for (int c = 0; c < row.length(); c++) {
					char ch = row.charAt(c);
					if (ch == ' ') {
						continue;
					}
					ctx.fill(px + c * u, py + r * u, px + (c + 1) * u, py + (r + 1) * u, ch == 'B' ? 0xFF000000 : 0xFFFFFFFF);
				}
			}
		} finally {
			if (scaled) {
				LunaCompat.guiPop(ctx);
			}
		}
	}

	/** 창 좌표(glfwGetCursorPos) → 프레임버퍼 픽셀(윈도우 배율 150% 등이면 다름). */
	private double framebufferRatio() {
		try {
			long h = WindowAccess.of(client).getHandle();
			int[] ww = new int[1], wh = new int[1], fw = new int[1], fh = new int[1];
			org.lwjgl.glfw.GLFW.glfwGetWindowSize(h, ww, wh);
			org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(h, fw, fh);
			return ww[0] > 0 ? fw[0] / (double) ww[0] : 1.0;
		} catch (Throwable t) {
			return 1.0;
		}
	}

	private void restoreCursor() {
		if (!cursorHidden) {
			return;
		}
		cursorHidden = false;
		try {
			long handle = WindowAccess.of(client).getHandle();
			if (org.lwjgl.glfw.GLFW.glfwGetInputMode(handle, org.lwjgl.glfw.GLFW.GLFW_CURSOR) == org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN) {
				org.lwjgl.glfw.GLFW.glfwSetInputMode(handle, org.lwjgl.glfw.GLFW.GLFW_CURSOR, org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
			}
		} catch (Throwable ignored) {
		}
	}

	private void dropTexture() {
		if (texId != null) {
			LunaCompat.unregisterImageTexture(client, texId, null);
			texId = null;
		}
	}

	/** 새 그림이 왔으면 텍스처를 바꾼다(두 이름을 번갈아 써서 그리는 중인 텍스처를 지우지 않게). */
	private void swapInFrame() {
		NativeImage img = LunaPip.takeFrame();
		if (img == null) {
			return;
		}
		int w = img.getWidth(), h = img.getHeight();
		flip ^= 1;
		Identifier id = LunaCompat.registerImageTexture(client, "pip/frame" + flip, img);
		if (id == null) {
			return;
		}
		if (texId != null && !texId.equals(id)) {
			LunaCompat.unregisterImageTexture(client, texId, null);
		}
		texId = id;
		texW = w;
		texH = h;
	}

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		int sw = WindowAccess.of(client).getScaledWidth();
		int sh = WindowAccess.of(client).getScaledHeight();
		int w = baseW(), h = baseH();
		boolean live = false;
		if (!isPreview()) {
			LunaPip.poll();
			swapInFrame();
			live = texId != null && texW > 0 && texH > 0 && System.currentTimeMillis() - LunaPip.lastFrameMs < 3000;
		}
		int nameH = showName.get() && live ? 12 : 0;
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h + nameH);
		if (live) {
			// 고른 비율로 가운데를 잘라 채운다(찌그러지지 않게)
			float target = w / (float) h;
			int u = 0, v = 0, cw = texW, ch = texH;
			if (texW / (float) texH > target) {
				cw = Math.max(1, Math.round(texH * target));
				u = (texW - cw) / 2;
			} else {
				ch = Math.max(1, Math.round(texW / target));
				v = (texH - ch) / 2;
			}
			int a = Math.max(0, Math.min(255, Math.round(opacity.get() * 2.55f)));
			LunaGfx.drawImageRegion(context, texId, x, y, w, h, u, v, cw, ch, texW, texH, (a << 24) | 0xFFFFFF);
			if (nameH > 0) {
				String name = LunaDraw.ellipsize(client.textRenderer, LunaPip.window, w - 4);
				LunaCompat.drawHudText(context, client.textRenderer, name, x + 2, y + h + 2, 0xFFB8BEC6);
			}
			return;
		}
		// 아직 그림이 없다(또는 미리보기) - 빈 화면 + 무엇을 하면 되는지 한 줄
		context.fill(x, y, x + w, y + h, 0xC015181D);
		String msg;
		if (isPreview()) {
			msg = ratio.get() == Ratio.SHORTS ? "쇼츠" : "영상";
		} else if (!LunaPip.launcherPresent) {
			msg = "런처로 실행 필요";
		} else if (LunaPip.error != null && !LunaPip.error.isEmpty()) {
			msg = LunaPip.error;
		} else if (LunaPip.windows.isEmpty()) {
			msg = "열린 영상 창 없음";
		} else if (LunaPip.running) {
			msg = "영상 받는 중";
		} else {
			msg = "[영상 고르기]로 선택";
		}
		drawPlay(context, x + w / 2, y + h / 2 - 6, 0x80FFFFFF);
		String shown = LunaDraw.ellipsize(client.textRenderer, msg, w - 6);
		int tw = LunaCompat.getTextWidth(client.textRenderer, shown);
		LunaCompat.drawHudText(context, client.textRenderer, shown, x + (w - tw) / 2, y + h / 2 + 6, 0xFFB8BEC6);
	}

	/** 가운데 재생 삼각형(글꼴 없이 줄로). */
	private static void drawPlay(DrawContext ctx, int cx, int cy, int color) {
		for (int i = 0; i < 9; i++) {
			int half = (8 - i) / 2 + 1;   // 왼쪽이 높고 오른쪽 끝이 뾰족
			ctx.fill(cx - 3 + i, cy - half, cx - 2 + i, cy + half, color);
		}
	}
}
