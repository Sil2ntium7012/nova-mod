package kr.lunaslight.mod.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

/**
 * 49-90차(8-18·8-19): 되돌릴 수 없는 일(친구 삭제·차단, 스크린샷 삭제) 앞에 뜨는 <b>확인 창</b>.
 *
 * <p>예전엔 버튼을 "한 번 더" 누르게 했다(3~4초 동안 "정말?"로 바뀜). 사용자: "정말 지울까요?가 글도
 * 배경도 빨강이라 안 보임", "버튼 크기가 바뀜", "삭제·차단은 재차 확인". 버튼 글을 바꾸는 방식은
 * 폭이 달라져 옆 버튼이 밀리고, 빨강 배경에 빨강 글이라 읽히지 않았다. 그래서 설정 화면의 "전체
 * 초기화할까요?" 창과 같은 모양의 작은 창을 띄운다 - 버튼은 그대로, 창이 묻는다.
 *
 * <p>화면 클래스는 {@link #render}를 맨 마지막에, {@link #mouseClicked}·{@link #keyPressed}를 맨 먼저
 * 부른다(열려 있으면 입력을 전부 삼킨다). Enter = 확인, ESC·바깥 클릭 = 취소.
 */
public final class LunaConfirm {

	private static final int W = 250;
	private static final int H0 = 92;
	private int H = H0;

	private String icon;
	private String title;
	private String line;
	private String okLabel;
	private Runnable onOk;
	private boolean open;
	// 49-257차: 묻는 창이 아니라 알리는 창(크림 HUD 안내)용 - 둘째 줄, 취소 글자, 확인 버튼을 빨강이 아니게
	private String line2;
	private String cancelLabel = "취소";
	private boolean danger = true;
	private int iconColor = 0xFFCF7B74;
	private int bx, by;

	/** 창을 연다. icon은 {@link LunaIcons} 글리프(null이면 없음). */
	public void show(String icon, String title, String line, String okLabel, Runnable onOk) {
		this.icon = icon;
		this.title = title;
		this.line = line;
		this.okLabel = okLabel;
		this.onOk = onOk;
		this.open = true;
		this.line2 = null;
		this.cancelLabel = "취소";
		this.danger = true;
		this.iconColor = 0xFFCF7B74;
		this.H = H0;
		LunaDraw.resetAnim("confirm");
	}

	/** 49-257차: 알리는 창 - 두 줄 설명, 취소 자리 글자(예: 나중에), 확인 버튼은 테마색. */
	public void showInfo(String icon, String title, String line, String line2, String okLabel, String cancelLabel, Runnable onOk) {
		show(icon, title, line, okLabel, onOk);
		this.line2 = line2;
		this.cancelLabel = cancelLabel == null ? "닫기" : cancelLabel;
		this.danger = false;
		this.iconColor = LunaDraw.ACCENT | 0xFF000000;
		this.H = line2 == null ? H0 : H0 + 12;
	}

	public boolean isOpen() {
		return open;
	}

	public void close() {
		open = false;
		onOk = null;
	}

	public void render(DrawContext ctx, TextRenderer tr, int screenW, int screenH, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		ctx.fill(0, 0, screenW, screenH, LunaDraw.applyAlpha(0xA6000000));
		float pop = LunaDraw.animFrom("confirm", 0f, 1f, 18f);
		bx = (screenW - W) / 2;
		by = (screenH - H) / 2 + Math.round((1f - pop) * 10f);
		LunaDraw.panel3d(ctx, bx, by, W, H, 8);   // 49-227차: 사진 시안 판
		if (icon != null) {
			LunaIcons.drawCentered(ctx, tr, icon, bx + W / 2, LunaDraw.iconY(by + 6, 14), iconColor, false);
		}
		LunaDraw.textCentered(ctx, tr, title, bx + W / 2, by + 30, LunaDraw.TEXT);
		if (line != null && !line.isEmpty()) {
			LunaDraw.textCentered(ctx, tr, LunaDraw.ellipsize(tr, line, W - 24), bx + W / 2, by + 44, LunaDraw.TEXT_DIM);
		}
		if (line2 != null && !line2.isEmpty()) {
			LunaDraw.textCentered(ctx, tr, LunaDraw.ellipsize(tr, line2, W - 24), bx + W / 2, by + 56, LunaDraw.TEXT_DIM);
		}
		button(ctx, tr, "confirm:no", cancelLabel, bx + W / 2 - 72, by + H - 28, 66, 20, mouseX, mouseY, false);
		if (danger) {
			button(ctx, tr, "confirm:ok", okLabel, bx + W / 2 + 6, by + H - 28, 66, 20, mouseX, mouseY, true);
		} else {
			boolean oh = LunaDraw.in(mouseX, mouseY, bx + W / 2 + 6, by + H - 28, 66, 20);
			LunaDraw.button3d(ctx, tr, bx + W / 2 + 6, by + H - 28, 66, 20, okLabel, LunaDraw.B_PRIMARY,
				LunaDraw.anim("mb:confirm:okp", oh ? 1f : 0f, 18f));
		}
	}

	/** 열려 있으면 클릭을 전부 먹는다(true). */
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!open) {
			return false;
		}
		if (button == 0 && LunaDraw.in(mouseX, mouseY, bx + W / 2 + 6, by + H - 28, 66, 20)) {
			confirm();
		} else if (button == 0 && (LunaDraw.in(mouseX, mouseY, bx + W / 2 - 72, by + H - 28, 66, 20)
				|| !LunaDraw.in(mouseX, mouseY, bx, by, W, H))) {
			close();
		}
		return true;
	}

	/** 열려 있으면 키를 전부 먹는다(true). Enter = 확인, ESC = 취소. */
	public boolean keyPressed(int keyCode) {
		if (!open) {
			return false;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			confirm();
		} else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			close();
		}
		return true;
	}

	private void confirm() {
		Runnable r = onOk;
		close();
		if (r != null) {
			r.run();
		}
	}

	/**
	 * 확인 창 버튼 - 설정 화면 modalButton과 같은 모양. danger는 어두운 바탕에 옅은 빨강 글(빨강 위 빨강 아님).
	 */
	public static void button(DrawContext ctx, TextRenderer tr, String key, String label, int x, int y, int w, int h,
			int mouseX, int mouseY, boolean danger) {
		boolean hovered = LunaDraw.in(mouseX, mouseY, x, y, w, h);
		float hov = LunaDraw.anim("mb:" + key, hovered ? 1f : 0f, 18f);
		// 49-227차(사진 시안): 공용 입체 버튼 - 지우기 같은 위험한 건 빨강
		LunaDraw.button3d(ctx, tr, x, y, w, h, label, danger ? LunaDraw.B_DANGER : LunaDraw.B_NEUTRAL, hov);
	}
}
