package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.impl.waypoint.BlueprintModule;
import kr.lunaslight.mod.util.Blueprint;
import kr.lunaslight.mod.util.LunaCompat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-253차: 설계도 창(O). 탭 세 개:
 * <ul>
 *   <li>저장: 지점 1·2 좌표와 크기, 기준 위치(지점 1 / 지점 2 / 내 위치), 이름(비우면 Nova-blueprintN), [빌드].</li>
 *   <li>목록: config/lunaslight/blueprints 안의 설계도. [불러오기](지금 서 있는 칸이 기준 위치), [삭제](두 번 눌러 확인).</li>
 *   <li>불러온 설계도: 크기, 진행률, 층 범위(최소·최대), [여기로 옮기기], [해제], 필요한 블록 목록(전체 / 남음 / 보유).</li>
 * </ul>
 */
public class BlueprintScreen extends LunaScreenBase {

	// ==================== GUI 배율과 무관한 크기(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 540, 340);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
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
		return lunaKeyPressed0(keyCode);
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		return lunaCharTyped0(chr);
	}

	// ==================== 배치 ====================

	private static final int PANEL_W = 520;
	private static final int PANEL_H = 366;   // 49-280차: 돌리기/뒤집기 두 줄만큼 키움(320 → 366)
	private static final int PAD = 14;
	private static final int ROW = 22;
	private static final String[] TABS = {"저장", "목록", "불러온 설계도"};
	// 49-280차: [불러온 설계도] 돌리기/뒤집기 버튼(위에서 볼 때 기준)
	private static final String[] XF_LABELS = {"돌리기", "뒤집기"};
	private static final String[][] XF_NAMES = {{"왼쪽 90", "오른쪽 90", "반 바퀴"}, {"좌우", "앞뒤", "위아래"}};
	private static final char[][] XF_OPS = {{Blueprint.ROT_CCW, Blueprint.ROT_CW, 'H'}, {Blueprint.MIRROR_X, Blueprint.MIRROR_Z, Blueprint.FLIP_Y}};
	private static final int XF_W = 50;

	private final Screen parent;
	private int tab;
	private String nameBuf = "";
	private boolean nameFocus;
	private String message = "";
	private long messageAt;
	private double listScroll, matScroll;
	private String confirmDelete;
	private long confirmAt;
	private List<String> files = new ArrayList<>();
	private final Map<String, Integer> invCache = new HashMap<>();
	private long invAt;
	/** 이름을 비웠을 때 저장될 이름(Nova-blueprintN). */
	private String autoName = "";

	public BlueprintScreen(Screen parent) {
		super(LunaCompat.textLiteral("설계도"));
		this.parent = parent;
		BlueprintModule m = BlueprintModule.instance;
		if (m != null && m.bp != null) {
			tab = 2;
		}
		refreshFiles();
		LunaDraw.resetAnim("bp");
	}

	private BlueprintModule mod() {
		return BlueprintModule.instance;
	}

	private void refreshFiles() {
		files = Blueprint.list();
		autoName = Blueprint.nextAutoName();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		LunaCompat.setScreen(parent);
	}

	private int px() {
		return (width - PANEL_W) / 2;
	}

	private int py() {
		return Math.max(6, (height - PANEL_H) / 2);
	}

	private void say(String s) {
		message = s == null ? "" : s;
		messageAt = System.currentTimeMillis();
	}

	// ==================== 그리기 ====================

	private void lunaRender0(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
		LunaDraw.beginFrame();
		float open = LunaDraw.animFrom("bp", 0f, 1f, 16f);
		LunaDraw.setAlpha(open);
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mx, my, delta, LunaDraw.applyAlpha(LunaDraw.OVERLAY));
		int px = px(), py = py();
		LunaDraw.panel3d(ctx, px, py, PANEL_W, PANEL_H, 10);
		LunaIcons.draw(ctx, font, LunaIcons.LAYOUT, px + PAD, LunaDraw.iconY(py + 10, 22), LunaDraw.ACCENT);
		LunaDraw.text(ctx, font, "설계도", px + PAD + 16, LunaDraw.textY(py + 10, 22), LunaDraw.TEXT);
		int closeX = px + PANEL_W - PAD - 22;
		boolean ch = LunaDraw.in(mx, my, closeX, py + 10, 22, 22);
		LunaDraw.button3d(ctx, closeX, py + 10, 22, 22, 5, LunaDraw.B_NEUTRAL, ch ? 1f : 0f);
		LunaIcons.draw(ctx, font, LunaIcons.CLOSE, closeX + 6, LunaDraw.iconY(py + 10, 22), ch ? 0xFFFF8B82 : 0xFFCF7B74);
		// 탭
		int tx = px + PAD + 70;
		for (int i = 0; i < TABS.length; i++) {
			int tw = LunaDraw.width(font, TABS[i]) + 20;
			boolean th = LunaDraw.in(mx, my, tx, py + 10, tw, 22);
			LunaDraw.button3d(ctx, font, tx, py + 10, tw, 22, TABS[i], i == tab ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL, th ? 1f : 0f);
			tx += tw + 6;
		}
		LunaDraw.fadeLine(ctx, px + PAD, py + 40, PANEL_W - PAD * 2, LunaDraw.ACCENT);
		BlueprintModule m = mod();
		if (m == null) {
			LunaDraw.text(ctx, font, "설계도 기능을 찾을 수 없습니다", px + PAD, py + 56, LunaDraw.TEXT_DIM);
			LunaDraw.setAlpha(1f);
			return;
		}
		int top = py + 50;
		if (tab == 0) {
			renderSave(ctx, m, px, top, mx, my);
		} else if (tab == 1) {
			renderList(ctx, m, px, top, mx, my);
		} else {
			renderLoaded(ctx, m, px, top, mx, my);
		}
		if (!message.isEmpty() && System.currentTimeMillis() - messageAt < 6000) {
			LunaDraw.text(ctx, font, LunaDraw.ellipsizeFormatted(font, message, PANEL_W - PAD * 2), px + PAD,
					py + PANEL_H - 18, LunaDraw.TEXT);
		}
		LunaDraw.setAlpha(1f);
	}

	private void label(GuiGraphicsExtractor ctx, String k, String v, int x, int y) {
		LunaDraw.text(ctx, font, k, x, LunaDraw.textY(y, ROW), LunaDraw.TEXT_SUB);
		LunaDraw.text(ctx, font, v, x + 70, LunaDraw.textY(y, ROW), LunaDraw.TEXT);
	}

	// ---- 저장 탭 ----

	private static final String[] REF_NAMES = {"지점 1", "지점 2", "내 위치"};

	private void renderSave(GuiGraphicsExtractor ctx, BlueprintModule m, int px, int top, int mx, int my) {
		int x = px + PAD, y = top;
		LunaDraw.text(ctx, font, "도구(" + toolName(m) + ")를 들고 좌클릭 = 지점 1, 우클릭 = 지점 2", x, LunaDraw.textY(y, ROW), LunaDraw.TEXT_DIM);
		y += ROW;
		label(ctx, "지점 1", BlueprintModule.fmt(m.pos1), x, y);
		y += ROW;
		label(ctx, "지점 2", BlueprintModule.fmt(m.pos2), x, y);
		y += ROW;
		String size = m.pos1 != null && m.pos2 != null
				? m.spanX() + " × " + m.spanY() + " × " + m.spanZ() + "  (" + ((long) m.spanX() * m.spanY() * m.spanZ()) + "칸)" : "-";
		label(ctx, "크기", size, x, y);
		y += ROW + 4;
		LunaDraw.text(ctx, font, "기준 위치", x, LunaDraw.textY(y, ROW), LunaDraw.TEXT_SUB);
		int bx = x + 70;
		for (int i = 0; i < 3; i++) {
			int bw = LunaDraw.width(font, REF_NAMES[i]) + 18;
			boolean h = LunaDraw.in(mx, my, bx, y, bw, ROW - 2);
			LunaDraw.button3d(ctx, font, bx, y, bw, ROW - 2, REF_NAMES[i], m.refMode == i ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL, h ? 1f : 0f);
			bx += bw + 6;
		}
		LunaDraw.text(ctx, font, BlueprintModule.fmt(m.refPos()), bx + 6, LunaDraw.textY(y, ROW), LunaDraw.TEXT);
		y += ROW + 2;
		LunaDraw.text(ctx, font, "불러올 때 내가 서 있는 칸이 이 자리가 됩니다", x + 70, LunaDraw.textY(y, ROW), LunaDraw.TEXT_DIM);
		y += ROW + 6;
		// 이름
		LunaDraw.text(ctx, font, "이름", x, LunaDraw.textY(y, ROW), LunaDraw.TEXT_SUB);
		int fw = 240;
		LunaDraw.roundRectBordered(ctx, x + 70, y, fw, ROW - 2, 4, LunaDraw.TRACK, nameFocus ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
		String shown = nameBuf.isEmpty() ? autoName : nameBuf;   // 비었으면 저장될 이름(회색)
		shown = LunaDraw.ellipsize(font, shown, fw - 12);
		LunaDraw.text(ctx, font, shown, x + 76, LunaDraw.textY(y, ROW - 2), nameBuf.isEmpty() ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
		if (nameFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = x + 76 + (nameBuf.isEmpty() ? 0 : LunaDraw.width(font, shown)) + 1;
			ctx.fill(cx, y + 4, cx + 1, y + ROW - 6, LunaDraw.applyAlpha(LunaDraw.TEXT));
		}
		int bw = 80;
		boolean bh = LunaDraw.in(mx, my, x + 70 + fw + 8, y, bw, ROW - 2);
		LunaDraw.button3d(ctx, font, x + 70 + fw + 8, y, bw, ROW - 2, "빌드", LunaDraw.B_PRIMARY, bh ? 1f : 0f);
	}

	private String toolName(BlueprintModule m) {
		return m.toolLabel();
	}

	// ---- 목록 탭 ----

	private void renderList(GuiGraphicsExtractor ctx, BlueprintModule m, int px, int top, int mx, int my) {
		int x = px + PAD, w = PANEL_W - PAD * 2;
		LunaDraw.text(ctx, font, "불러오면 지금 서 있는 칸이 설계도의 기준 위치가 됩니다", x, LunaDraw.textY(top, ROW), LunaDraw.TEXT_DIM);
		int lt = top + ROW, lh = PANEL_H - (lt - py()) - 26;
		int maxScroll = Math.max(0, files.size() * ROW - lh);
		listScroll = Math.max(0, Math.min(listScroll, maxScroll));
		lunaV.scissor(ctx, x, lt, x + w, lt + lh);
		if (files.isEmpty()) {
			LunaDraw.text(ctx, font, "저장된 설계도가 없습니다", x + 4, lt + 8, LunaDraw.TEXT_DIM);
		}
		for (int i = 0; i < files.size(); i++) {
			int ry = lt + i * ROW - (int) listScroll;
			if (ry + ROW < lt || ry > lt + lh) {
				continue;
			}
			String f = files.get(i);
			boolean loaded = m.bp != null && f.equals(m.bp.name);
			LunaDraw.roundRectBordered(ctx, x, ry, w, ROW - 3, 4, LunaDraw.CARD, loaded ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, f, w - 170), x + 8, LunaDraw.textY(ry, ROW - 3), LunaDraw.TEXT);
			int delX = x + w - 64, loadX = delX - 82;
			boolean lh2 = LunaDraw.in(mx, my, loadX, ry + 1, 76, ROW - 5);
			LunaDraw.button3d(ctx, font, loadX, ry + 1, 76, ROW - 5, loaded ? "다시 불러오기" : "불러오기", LunaDraw.B_PRIMARY, lh2 ? 1f : 0f);
			boolean dh = LunaDraw.in(mx, my, delX, ry + 1, 58, ROW - 5);
			boolean confirm = f.equals(confirmDelete) && System.currentTimeMillis() - confirmAt < 3000;
			LunaDraw.button3d(ctx, font, delX, ry + 1, 58, ROW - 5, confirm ? "정말?" : "삭제", LunaDraw.B_DANGER, dh ? 1f : 0f);
		}
		ctx.disableScissor();
	}

	// ---- 불러온 설계도 탭 ----

	private void renderLoaded(GuiGraphicsExtractor ctx, BlueprintModule m, int px, int top, int mx, int my) {
		int x = px + PAD;
		if (m.bp == null) {
			LunaDraw.text(ctx, font, "불러온 설계도가 없습니다 | [목록]에서 불러오세요", x, LunaDraw.textY(top, ROW), LunaDraw.TEXT_DIM);
			return;
		}
		Blueprint bp = m.bp;
		int colW = 230;
		int y = top;
		label(ctx, "이름", bp.name, x, y);
		y += ROW;
		label(ctx, "크기", bp.w + " × " + bp.h + " × " + bp.l, x, y);
		y += ROW;
		int done = bp.solid - m.totalLeft;
		label(ctx, "블록", bp.solid + "개 | 남음 " + m.totalLeft + (m.scanned() ? "" : " (확인 중)"), x, y);
		y += ROW;
		label(ctx, "진행률", bp.solid == 0 ? "-" : (int) Math.floor(Math.max(0, done) * 100.0 / bp.solid) + "%", x, y);
		y += ROW;
		label(ctx, "기준 위치", BlueprintModule.fmt(new BlockPos(m.origin.getX() + bp.rx, m.origin.getY() + bp.ry, m.origin.getZ() + bp.rz)), x, y);
		y += ROW + 4;
		// 층
		y = layerRow(ctx, "최소 층", m.layerMin, x, y, mx, my);
		y = layerRow(ctx, "최대 층", m.layerMax, x, y, mx, my);
		label(ctx, "높이", "Y " + (m.origin.getY() + m.layerMin - 1) + "  ~  Y " + (m.origin.getY() + m.layerMax - 1), x, y);
		y += ROW;
		label(ctx, "층 남음", m.layerLeft + "개" + (m.scanned() ? "" : "  (확인 중)"), x, y);
		y += ROW + 4;
		String[] acts = {"전체 층", "여기로 옮기기", "해제"};
		int bx = x;
		for (String a : acts) {
			int bw = LunaDraw.width(font, a) + 18;
			boolean h = LunaDraw.in(mx, my, bx, y, bw, ROW - 2);
			LunaDraw.button3d(ctx, font, bx, y, bw, ROW - 2, a, "해제".equals(a) ? LunaDraw.B_DANGER : LunaDraw.B_NEUTRAL, h ? 1f : 0f);
			bx += bw + 6;
		}
		// 49-280차: 돌리기 / 뒤집기
		y += ROW;
		for (int row = 0; row < 2; row++) {
			LunaDraw.text(ctx, font, XF_LABELS[row], x, LunaDraw.textY(y, ROW), LunaDraw.TEXT_SUB);
			int tx = x + 70;
			for (int i = 0; i < 3; i++) {
				String a = XF_NAMES[row][i];
				boolean h = LunaDraw.in(mx, my, tx, y, XF_W, ROW - 2);
				LunaDraw.button3d(ctx, font, tx, y, XF_W, ROW - 2, a, LunaDraw.B_NEUTRAL, h ? 1f : 0f);
				tx += XF_W + 4;
			}
			y += ROW;
		}
		// 오른쪽: 필요한 블록
		int rx = px + PAD + colW + 16, rw = PANEL_W - (rx - px) - PAD;
		LunaDraw.text(ctx, font, "필요한 블록", rx, LunaDraw.textY(top, ROW), LunaDraw.TEXT_SUB);
		// 49-255차: 숫자와 머리글을 같은 칸에 오른쪽 맞춤(예전엔 한 줄 글자로 붙여 써서 자릿수가 다르면 어긋났다)
		int numCol = Math.max(LunaDraw.width(font, "00000"), LunaDraw.width(font, "보유")) + 12;
		int c3 = rx + rw, c2 = c3 - numCol, c1 = c2 - numCol;
		String[] heads = {"전체", "남음", "보유"};
		int[] cols = {c1, c2, c3};
		for (int hi = 0; hi < 3; hi++) {
			LunaDraw.text(ctx, font, heads[hi], cols[hi] - LunaDraw.width(font, heads[hi]), LunaDraw.textY(top, ROW), LunaDraw.TEXT_DIM);
		}
		Map<String, String> names = new HashMap<>();
		LinkedHashMap<String, int[]> mats = m.materials(names);
		refreshInv(mats);
		int lt = top + ROW, lh = PANEL_H - (lt - py()) - 26;
		int maxScroll = Math.max(0, mats.size() * 16 - lh);
		matScroll = Math.max(0, Math.min(matScroll, maxScroll));
		lunaV.scissor(ctx, rx, lt, rx + rw, lt + lh);
		int i = 0;
		for (Map.Entry<String, int[]> en : mats.entrySet()) {
			int ry = lt + i * 16 - (int) matScroll;
			i++;
			if (ry + 16 < lt || ry > lt + lh) {
				continue;
			}
			int total = en.getValue()[0], left = en.getValue()[1];
			int have = invCache.getOrDefault(en.getKey(), 0);
			String nm = names.getOrDefault(en.getKey(), en.getKey());
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, nm, c1 - numCol - rx - 6), rx, ry + 4, left == 0 ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
			int col = left == 0 ? 0xFF7CE08A : have >= left ? LunaDraw.TEXT_SUB : 0xFFFF8B82;
			String[] nums = {String.valueOf(total), String.valueOf(left), String.valueOf(have)};
			for (int ci = 0; ci < 3; ci++) {
				LunaDraw.text(ctx, font, nums[ci], cols[ci] - LunaDraw.width(font, nums[ci]), ry + 4,
						ci == 0 ? LunaDraw.TEXT_DIM : col);
			}
		}
		ctx.disableScissor();
	}

	private int layerRow(GuiGraphicsExtractor ctx, String name, int v, int x, int y, int mx, int my) {
		LunaDraw.text(ctx, font, name, x, LunaDraw.textY(y, ROW), LunaDraw.TEXT_SUB);
		int bx = x + 70;
		boolean h1 = LunaDraw.in(mx, my, bx, y, 22, ROW - 2);
		LunaDraw.button3d(ctx, font, bx, y, 22, ROW - 2, "-", LunaDraw.B_NEUTRAL, h1 ? 1f : 0f);
		String s = String.valueOf(v);
		LunaDraw.text(ctx, font, s, bx + 22 + (36 - LunaDraw.width(font, s)) / 2, LunaDraw.textY(y, ROW), LunaDraw.TEXT);
		boolean h2 = LunaDraw.in(mx, my, bx + 58, y, 22, ROW - 2);
		LunaDraw.button3d(ctx, font, bx + 58, y, 22, ROW - 2, "+", LunaDraw.B_NEUTRAL, h2 ? 1f : 0f);
		return y + ROW;
	}

	private void refreshInv(Map<String, int[]> mats) {
		long now = System.currentTimeMillis();
		if (now - invAt < 1000 || minecraft == null || minecraft.player == null) {
			return;
		}
		invAt = now;
		invCache.clear();
		try {
			Inventory inv = LunaCompat.getPlayerInventory(minecraft.player);
			for (int i = 0; i < 36; i++) {
				ItemStack st = LunaCompat.invGetStack(inv, i);
				if (st == null || st.isEmpty()) {
					continue;
				}
				Object id = LunaCompat.getItemId(st.getItem());
				if (id != null) {
					invCache.merge(id.toString(), st.getCount(), Integer::sum);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("blueprint:inv", t);
		}
	}

	// ==================== 입력 ====================

	private boolean lunaMouseClicked0(double mx, double my, int button) {
		int px = px(), py = py();
		if (!LunaDraw.in(mx, my, px, py, PANEL_W, PANEL_H)) {
			onClose();
			return true;
		}
		int closeX = px + PANEL_W - PAD - 22;
		if (LunaDraw.in(mx, my, closeX, py + 10, 22, 22)) {
			onClose();
			return true;
		}
		int tx = px + PAD + 70;
		for (int i = 0; i < TABS.length; i++) {
			int tw = LunaDraw.width(font, TABS[i]) + 20;
			if (LunaDraw.in(mx, my, tx, py + 10, tw, 22)) {
				tab = i;
				nameFocus = false;
				if (i == 1) {
					refreshFiles();
				}
				return true;
			}
			tx += tw + 6;
		}
		BlueprintModule m = mod();
		if (m == null) {
			return true;
		}
		int top = py + 50;
		if (tab == 0) {
			return clickSave(m, px, top, mx, my);
		}
		if (tab == 1) {
			return clickList(m, px, top, mx, my);
		}
		return clickLoaded(m, px, top, mx, my);
	}

	private boolean clickSave(BlueprintModule m, int px, int top, double mx, double my) {
		int x = px + PAD;
		int y = top + ROW * 4 + 4;
		int bx = x + 70;
		for (int i = 0; i < 3; i++) {
			int bw = LunaDraw.width(font, REF_NAMES[i]) + 18;
			if (LunaDraw.in(mx, my, bx, y, bw, ROW - 2)) {
				m.refMode = i;
				if (i == 2 && minecraft.player != null) {
					m.refCustom = m.playerBlock();
				}
				m.saveState();   // 49-260차
				return true;
			}
			bx += bw + 6;
		}
		y += ROW + 2 + ROW + 6;
		int fw = 240;
		if (LunaDraw.in(mx, my, x + 70, y, fw, ROW - 2)) {
			nameFocus = true;
			return true;
		}
		if (LunaDraw.in(mx, my, x + 70 + fw + 8, y, 80, ROW - 2)) {
			doBuild(m);
			return true;
		}
		nameFocus = false;
		return true;
	}

	private void doBuild(BlueprintModule m) {
		say(m.build(nameBuf));
		if (message.startsWith("§a")) {
			nameBuf = "";
			nameFocus = false;
			refreshFiles();
		}
	}

	private boolean clickList(BlueprintModule m, int px, int top, double mx, double my) {
		int x = px + PAD, w = PANEL_W - PAD * 2;
		int lt = top + ROW, lh = PANEL_H - (lt - py()) - 26;
		if (!LunaDraw.in(mx, my, x, lt, w, lh)) {
			return true;
		}
		for (int i = 0; i < files.size(); i++) {
			int ry = lt + i * ROW - (int) listScroll;
			String f = files.get(i);
			int delX = x + w - 64, loadX = delX - 82;
			if (LunaDraw.in(mx, my, loadX, ry + 1, 76, ROW - 5)) {
				say(m.load(f));
				if (m.bp != null) {
					tab = 2;
				}
				return true;
			}
			if (LunaDraw.in(mx, my, delX, ry + 1, 58, ROW - 5)) {
				if (f.equals(confirmDelete) && System.currentTimeMillis() - confirmAt < 3000) {
					say(Blueprint.delete(f) ? "§7삭제했습니다: " + f : "§c삭제하지 못했습니다");
					confirmDelete = null;
					refreshFiles();
				} else {
					confirmDelete = f;
					confirmAt = System.currentTimeMillis();
				}
				return true;
			}
		}
		return true;
	}

	private boolean clickLoaded(BlueprintModule m, int px, int top, double mx, double my) {
		if (m.bp == null) {
			return true;
		}
		int x = px + PAD;
		int y = top + ROW * 5 + 4;
		int bx = x + 70;
		// 최소 층
		if (LunaDraw.in(mx, my, bx, y, 22, ROW - 2)) {
			m.setLayers(m.layerMin - 1, m.layerMax);
			return true;
		}
		if (LunaDraw.in(mx, my, bx + 58, y, 22, ROW - 2)) {
			m.setLayers(m.layerMin + 1, Math.max(m.layerMax, m.layerMin + 1));
			return true;
		}
		y += ROW;
		if (LunaDraw.in(mx, my, bx, y, 22, ROW - 2)) {
			m.setLayers(Math.min(m.layerMin, m.layerMax - 1), m.layerMax - 1);
			return true;
		}
		if (LunaDraw.in(mx, my, bx + 58, y, 22, ROW - 2)) {
			m.setLayers(m.layerMin, m.layerMax + 1);
			return true;
		}
		y += ROW + ROW + ROW + 4;
		String[] acts = {"전체 층", "여기로 옮기기", "해제"};
		int ax = x;
		for (String a : acts) {
			int bw = LunaDraw.width(font, a) + 18;
			if (LunaDraw.in(mx, my, ax, y, bw, ROW - 2)) {
				if ("전체 층".equals(a)) {
					m.setLayers(1, m.bp.h);
				} else if ("여기로 옮기기".equals(a)) {
					m.moveHere();
					say("§a지금 서 있는 칸으로 옮겼습니다");
				} else {
					m.unload();
					say("§7설계도를 해제했습니다");
				}
				return true;
			}
			ax += bw + 6;
		}
		// 49-280차: 돌리기 / 뒤집기
		y += ROW;
		for (int row = 0; row < 2; row++) {
			int tx = x + 70;
			for (int i = 0; i < 3; i++) {
				if (LunaDraw.in(mx, my, tx, y, XF_W, ROW - 2)) {
					say(m.transform(XF_OPS[row][i]));
					return true;
				}
				tx += XF_W + 4;
			}
			y += ROW;
		}
		return true;
	}

	/** 이름 칸에 초점이 있는 동안 한글 IME 유지(LunaScreenBase.lunaSyncIme). */
	@Override
	protected boolean lunaWantsText() {
		return nameFocus;
	}

	private boolean lunaMouseScrolled0(double mx, double my, double amount) {
		if (tab == 1) {
			listScroll -= amount * ROW;
		} else if (tab == 2) {
			matScroll -= amount * 16;
		}
		return true;
	}

	private boolean lunaCharTyped0(char chr) {
		if (!nameFocus || chr < 32) {
			return false;
		}
		if (nameBuf.length() < 40) {
			nameBuf += chr;
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode) {
		if (nameFocus) {
			if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
				BlueprintModule m = mod();
				if (m != null) {
					doBuild(m);
				}
				return true;
			}
			if (keyCode == InputConstants.KEY_ESCAPE) {
				nameFocus = false;
				return true;
			}
			if (keyCode == InputConstants.KEY_BACKSPACE && !nameBuf.isEmpty()) {
				nameBuf = nameBuf.substring(0, nameBuf.length() - 1);
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
