package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.impl.waypoint.WaypointModule;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;

/**
 * 49-21차: 웨이포인트 관리창 - 목록(색/이름/좌표/거리), 여기에 추가, 이름 바꾸기(클릭 후 입력),
 * 색 바꾸기(견본 클릭 = 다음 색), 표시 켜기/끄기, 삭제.
 */
public class LunaWaypointScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 456, 316);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			if (lunaV.needsInit()) {
				lunaInit0();
				lunaV.markInit();
			}
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected void init() {
		boolean e = lunaV.enter();
		try {
			lunaInit0();
			lunaV.markInit();
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseClicked0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseScrolled0(mouseX * k, mouseY * k, verticalAmount);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaKeyPressed0(keyCode, scanCode, modifiers);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaCharTyped0(chr);
		} finally {
			lunaV.exit(e);
		}
	}

	private static final int PANEL_W = 440;
	private static final int PANEL_H = 300;
	private static final int ROW_H = 26;
	private static final int PAD = 12;
	private static final int HEAD_H = 40;

	private final Screen parent;
	private int px, py;
	private double scroll;
	private WaypointModule.Waypoint editing;
	private String editBuffer = "";

	public LunaWaypointScreen(Screen parent) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("웨이포인트"));
		this.parent = parent;
		LunaDraw.resetAnim("wp");
	}

	private WaypointModule module() {
		var opt = ModuleManager.get().find("waypoint");
		return opt.isPresent() && opt.get() instanceof WaypointModule wm ? wm : null;
	}

	private void lunaInit0() {
		px = (width - PANEL_W) / 2;
		py = Math.max(8, (height - PANEL_H) / 2);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		commitEdit();
		WaypointModule wm = module();
		if (wm != null) {
			wm.save();
		}
		if (minecraft != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
		}
	}

	private int listTop() {
		return py + HEAD_H;
	}

	private int listH() {
		return PANEL_H - HEAD_H - PAD;
	}

	private void commitEdit() {
		if (editing != null) {
			String n = editBuffer.trim();
			if (!n.isEmpty()) {
				editing.name = n;
			}
			editing = null;
			WaypointModule wm = module();
			if (wm != null) {
				wm.save();
			}
		}
	}

	private void lunaRender0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		float open = LunaDraw.animFrom("wp", 0f, 1f, 16f);
		LunaDraw.setAlpha(open);
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.applyAlpha(LunaDraw.OVERLAY));
		LunaDraw.panel3d(ctx, px, py, PANEL_W, PANEL_H, 10);   // 49-227차: 사진 시안 판

		WaypointModule wm = module();
		List<WaypointModule.Waypoint> list = wm == null ? List.of() : wm.getWaypoints();

		// 헤더: 제목 + 개수 + [여기에 추가] + 닫기
		LunaIcons.draw(ctx, font, LunaIcons.MOVE, px + PAD, LunaDraw.iconY(py + 10, 22), LunaDraw.ACCENT);
		LunaDraw.text(ctx, font, "웨이포인트", px + PAD + 16, LunaDraw.textY(py + 10, 22), LunaDraw.TEXT);
		LunaDraw.text(ctx, font, list.size() + "개", px + PAD + 16 + LunaDraw.width(font, "웨이포인트") + 8,
			LunaDraw.textY(py + 10, 22), LunaDraw.TEXT_DIM);

		int closeX = px + PANEL_W - PAD - 22;
		boolean closeHover = LunaDraw.in(mouseX, mouseY, closeX, py + 10, 22, 22);
		LunaDraw.button3d(ctx, closeX, py + 10, 22, 22, 5, LunaDraw.B_NEUTRAL, closeHover ? 1f : 0f);   // 49-227차
		LunaIcons.draw(ctx, font, LunaIcons.CLOSE, closeX + 6, LunaDraw.iconY(py + 10, 22),
			closeHover ? 0xFFFF8B82 : 0xFFCF7B74);

		int addW = 11 + 6 + LunaDraw.width(font, "여기에 추가") + 16;
		int addX = closeX - 8 - addW;
		boolean addHover = LunaDraw.in(mouseX, mouseY, addX, py + 10, addW, 22);
		// 49-227차: 주 버튼 = 테마색 입체 버튼
		LunaDraw.button3d(ctx, addX, py + 10, addW, 22, 5, LunaDraw.B_PRIMARY, addHover ? 1f : 0f);
		int addFg = LunaDraw.buttonText(LunaDraw.B_PRIMARY, 0f);
		LunaIcons.draw(ctx, font, LunaIcons.EDIT, addX + 8, LunaDraw.iconY(py + 10, 22), addFg);
		LunaDraw.text(ctx, font, "여기에 추가", addX + 8 + 11 + 5, LunaDraw.textY(py + 10, 22), addFg);

		// 49-28차: 통계 화면과 같은 헤더 밑 가는 선
		LunaDraw.fadeLine(ctx, px + PAD, py + HEAD_H - 4, PANEL_W - PAD * 2, LunaDraw.ACCENT);

		// 목록
		int lt = listTop();
		int lh = listH();
		int maxScroll = Math.max(0, list.size() * ROW_H - lh);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		Vec3 p = minecraft.player == null ? null : LunaCompat.getPos(minecraft.player);

		lunaV.scissor(ctx, px + 1, lt, px + PANEL_W - 1, lt + lh);
		if (list.isEmpty()) {
			LunaDraw.textCentered(ctx, font, "아직 웨이포인트가 없습니다 | 오른쪽 위 [여기에 추가]", px + PANEL_W / 2,
				lt + 30, LunaDraw.TEXT_DIM);
		}
		for (int i = 0; i < list.size(); i++) {
			WaypointModule.Waypoint w = list.get(i);
			int ry = lt + i * ROW_H - (int) scroll;
			if (ry + ROW_H < lt || ry > lt + lh) {
				continue;
			}
			int rx = px + PAD;
			int rw = PANEL_W - PAD * 2;
			boolean hovered = LunaDraw.in(mouseX, mouseY, rx, ry, rw, ROW_H - 3) && LunaDraw.in(mouseX, mouseY, px, lt, PANEL_W, lh);
			LunaDraw.roundRectBordered(ctx, rx, ry, rw, ROW_H - 3, 5,
				hovered ? LunaDraw.CARD_HOVER : LunaDraw.CARD, w.visible ? LunaDraw.CARD_BORDER : 0x0AFFFFFF);

			// 색 견본(클릭 = 다음 색)
			LunaDraw.circle(ctx, rx + 7, ry + (ROW_H - 3 - 11) / 2, 11, w.argb());

			// 이름(편집 중이면 입력 상자)
			int nameX = rx + 24;
			int nameW = 130;
			if (editing == w) {
				LunaDraw.roundRectBordered(ctx, nameX - 3, ry + 3, nameW + 6, ROW_H - 9, 4, LunaDraw.TRACK, LunaDraw.ACCENT);
				String shown = LunaDraw.ellipsize(font, editBuffer, nameW - 4);
				LunaDraw.text(ctx, font, shown, nameX, LunaDraw.textY(ry, ROW_H - 3), LunaDraw.TEXT);
				if ((System.currentTimeMillis() / 500) % 2 == 0) {
					int cx = nameX + LunaDraw.width(font, shown) + 1;
					ctx.fill(cx, ry + 6, cx + 1, ry + ROW_H - 9, LunaDraw.applyAlpha(LunaDraw.TEXT));
				}
			} else {
				LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, w.name, nameW), nameX,
					LunaDraw.textY(ry, ROW_H - 3), w.visible ? LunaDraw.TEXT : LunaDraw.TEXT_DIM);
			}

			// 좌표 + 거리
			String coords = (int) Math.floor(w.x) + ", " + (int) Math.floor(w.y) + ", " + (int) Math.floor(w.z);
			if (p != null) {
				double dx = w.x - p.x, dy = w.y - p.y, dz = w.z - p.z;
				coords += "  §7" + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
			}
			LunaDraw.text(ctx, font, coords, nameX + nameW + 10, LunaDraw.textY(ry, ROW_H - 3), LunaDraw.TEXT_SUB);

			// 표시 토글 + 삭제
			int tX = rx + rw - 26 - 8 - 22 - 8;
			LunaDraw.toggle(ctx, tX, ry + (ROW_H - 3 - 12) / 2, 26, 12,
				LunaDraw.anim("wpv:" + i, w.visible ? 1f : 0f, 14f), true);
			int delX = rx + rw - 22 - 6;
			boolean delHover = LunaDraw.in(mouseX, mouseY, delX, ry + 2, 20, ROW_H - 7);
			LunaDraw.button3d(ctx, delX, ry + 2, 20, ROW_H - 7, 4, LunaDraw.B_NEUTRAL, delHover ? 1f : 0f);   // 49-227차
			LunaIcons.draw(ctx, font, LunaIcons.CLOSE, delX + 5, LunaDraw.iconY(ry + 2, ROW_H - 7),
				delHover ? 0xFFFF8B82 : 0xFFCF7B74);
		}
		ctx.disableScissor();

		if (maxScroll > 0) {
			int trackX = px + PANEL_W - 5;
			int thumbH = Math.max(16, lh * lh / (list.size() * ROW_H));
			int thumbY = lt + (int) ((lh - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, trackX, thumbY, 3, thumbH, 1, 0x38FFFFFF);
		}
		LunaDraw.setAlpha(1f);
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		WaypointModule wm = module();
		if (wm == null) {
			onClose();
			return true;
		}
		if (!LunaDraw.in(mouseX, mouseY, px, py, PANEL_W, PANEL_H)) {
			onClose();
			return true;
		}
		int closeX = px + PANEL_W - PAD - 22;
		if (LunaDraw.in(mouseX, mouseY, closeX, py + 10, 22, 22)) {
			onClose();
			return true;
		}
		int addW = 11 + 6 + LunaDraw.width(font, "여기에 추가") + 16;
		int addX = closeX - 8 - addW;
		if (LunaDraw.in(mouseX, mouseY, addX, py + 10, addW, 22)) {
			commitEdit();
			WaypointModule.Waypoint w = wm.addHere(null);
			if (w != null) {
				editing = w;
				editBuffer = w.name;
			}
			return true;
		}
		List<WaypointModule.Waypoint> list = wm.getWaypoints();
		int lt = listTop();
		if (!LunaDraw.in(mouseX, mouseY, px, lt, PANEL_W, listH())) {
			commitEdit();
			return true;
		}
		for (int i = 0; i < list.size(); i++) {
			WaypointModule.Waypoint w = list.get(i);
			int ry = lt + i * ROW_H - (int) scroll;
			int rx = px + PAD;
			int rw = PANEL_W - PAD * 2;
			if (!LunaDraw.in(mouseX, mouseY, rx, ry, rw, ROW_H - 3)) {
				continue;
			}
			int delX = rx + rw - 22 - 6;
			if (LunaDraw.in(mouseX, mouseY, delX, ry + 2, 20, ROW_H - 7)) {
				if (editing == w) {
					editing = null;
				}
				wm.remove(w);
				return true;
			}
			int tX = rx + rw - 26 - 8 - 22 - 8;
			if (LunaDraw.in(mouseX, mouseY, tX - 4, ry, 34, ROW_H - 3)) {
				w.visible = !w.visible;
				wm.save();
				return true;
			}
			if (LunaDraw.in(mouseX, mouseY, rx + 2, ry, 20, ROW_H - 3)) {
				// 다음 프리셋 색
				int idx = 0;
				for (int k = 0; k < WaypointModule.PRESET_COLORS.length; k++) {
					if (WaypointModule.PRESET_COLORS[k] == w.argb()) {
						idx = k + 1;
						break;
					}
				}
				w.color = WaypointModule.PRESET_COLORS[idx % WaypointModule.PRESET_COLORS.length];
				wm.save();
				return true;
			}
			// 이름 클릭 → 편집
			commitEdit();
			editing = w;
			editBuffer = w.name;
			return true;
		}
		commitEdit();
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		scroll -= verticalAmount * ROW_H;
		return true;
	}


	/** 49-221차: 이 칸에 초점이 있는 동안 한글 IME 유지(LunaScreenBase.lunaSyncIme). */
	@Override
	protected boolean lunaWantsText() {
		return editing != null;
	}
	private boolean lunaCharTyped0(char chr) {
		if (editing == null || chr < 32) {
			return false;
		}
		if (editBuffer.length() < 24) {
			editBuffer += chr;
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (editing != null) {
			if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
				commitEdit();
				return true;
			}
			if (keyCode == InputConstants.KEY_ESCAPE) {
				editing = null;
				return true;
			}
			if (keyCode == InputConstants.KEY_BACKSPACE && !editBuffer.isEmpty()) {
				editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
				return true;
			}
			return true;
		}
		if (keyCode == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return false;
	}
}
