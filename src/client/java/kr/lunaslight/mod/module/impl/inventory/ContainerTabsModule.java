package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.ContainerIndex;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 49-125차(사용자: "인벤토리 탭 기능 - 상자를 열었을 때 오른쪽에 사거리 안의 모든 상자를 바로 열 수 있게, 버튼은
 * 블록 모양, 마우스 올리고 Shift면 안의 내용물 미리보기"): <b>인벤토리 탭</b>.
 *
 * <p>상자류 화면이나 내 인벤토리가 열려 있으면 GUI 옆에 <b>손이 닿는 거리 안의 보관함 블록</b>을 늘어놓는다(가까운 순).
 * 클릭하면 지금 화면을 닫고 그 블록을 바로 연다(서버에는 평범한 "블록 우클릭"만 간다).
 *
 * <p>49-133차(사용자: "상자 위치 뜰 필요 X 이름도 X 그냥 내용물만 쉬프트 누르면 보이게, 아래로만 너무 길쭉해서
 * 정렬되게, 마크 인벤토리 슬롯 느낌으로 최대 30개까지 오른쪽에, 왼쪽에도 가능, 제작대에서 뜰 때 겹치지 않게 밀리기"):
 * <ul>
 *   <li>둥근 타일 세로 한 줄 → <b>바닐라 GUI 판 + 슬롯 격자</b>. GUI 높이를 넘으면 옆 열로 넘어가 네모나게 정렬된다.</li>
 *   <li>최대 30개. 위치는 오른쪽(기본)/왼쪽. 자리가 없으면 반대쪽으로.</li>
 *   <li>마우스를 올려도 이름/거리 툴팁은 없다. <b>Shift</b>를 누르면 마지막으로 본 내용물만 슬롯 격자로(글자 없음).
 *       한 번도 안 열어 본 상자는 방벽 아이콘 한 칸.</li>
 *   <li>작업대에서 제작 도우미 패널이 떠 있으면 그 오른쪽으로 밀려난다.</li>
 * </ul>
 * 입력은 새 믹스인 없이 화면 렌더 콜백에서 GLFW 버튼 상태를 직접 읽는다({@code MouseTweaksModule}과 같은 방식).
 */
public class ContainerTabsModule extends Module {

	private static final int SLOT = 18;
	private static final int BORDER = 7;
	private static final int PREVIEW_COLS = 9;
	private static final int PREVIEW_ROWS = 6;
	private static final int MAX = 30;

	public enum Side {
		RIGHT("오른쪽"),
		LEFT("왼쪽"),
		TOP("위"),       // 49-237차(사용자: "인벤 위, 오른쪽, 왼쪽, 아래")
		BOTTOM("아래");

		private final String label;

		Side(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final IntSetting maxTabs = register(new IntSetting(
			"max_tabs", "최대 개수", "한 번에 보여줄 상자 칸 수입니다.", MAX, 1, MAX, 1));

	private final EnumSetting<Side> side = register(new EnumSetting<>(
			"side", "위치", "상자 칸을 GUI 오른쪽, 왼쪽, 위, 아래 중 어디에 둘지 정합니다. 상자 화면에서 판 테두리를 끌어 옮길 수도 있습니다(우클릭 = 제자리).", Side.RIGHT, Side.class));

	// 49-237차(사용자: "상자 연 화면에서 위치 조정 가능하게"): 판 테두리를 끌어 옮긴 거리(위치 기준점에서). 설정 화면엔 안 보인다.
	private final IntSetting offX = register(new IntSetting("offset_x", "가로 이동", "", 0, -3000, 3000, 1));
	private final IntSetting offY = register(new IntSetting("offset_y", "세로 이동", "", 0, -3000, 3000, 1));

	{
		offX.hidden();
		offY.hidden();
	}

	// 49-144차(사용자: "화로나 기타 등등 껐다 켰다 가능하게"): 보관함 종류별로 칸에 보일지.
	private final BooleanSetting showChest = register(new BooleanSetting("show_chest", "상자", "상자, 덫 상자, 구리 상자", true));
	private final BooleanSetting showBarrel = register(new BooleanSetting("show_barrel", "통", "통", true));
	private final BooleanSetting showShulker = register(new BooleanSetting("show_shulker", "셜커 상자", "셜커 상자", true));
	private final BooleanSetting showEnder = register(new BooleanSetting("show_ender", "엔더 상자", "엔더 상자", true));
	private final BooleanSetting showFurnace = register(new BooleanSetting("show_furnace", "화로", "화로, 용광로, 훈연기", true));
	private final BooleanSetting showHopper = register(new BooleanSetting("show_hopper", "깔때기", "깔때기", true));
	private final BooleanSetting showDispenser = register(new BooleanSetting("show_dispenser", "발사기", "발사기, 공급기, 제작기", true));
	private final BooleanSetting showBrewing = register(new BooleanSetting("show_brewing", "양조기", "양조기", true));
	private final BooleanSetting showOther = register(new BooleanSetting("show_other", "기타", "주크박스, 조각된 책장, 장식 단지 같은 나머지", false));

	private final BooleanSetting preview = register(new BooleanSetting(
			"preview", "Shift 미리보기", "칸에 마우스를 올리고 Shift를 누르면 마지막으로 본 내용물을 보여줍니다.", true));

	private final BooleanSetting inInventory = register(new BooleanSetting(
			"in_inventory", "내 인벤토리 포함", "상자뿐 아니라 내 인벤토리 화면에서도 근처 상자 칸을 보여줍니다.", true));

	/** 버튼 하나 = 근처 보관함 블록 하나. */
	private record Tab(BlockPos pos, ItemStack icon, String name, double dist) {
	}

	private final List<Tab> tabs = new ArrayList<>();
	private Object lastScreen;
	private int scanTicks;
	private boolean clickHeld;
	/** 지금 열려 있는 보관함의 위치(알 수 있을 때). */
	private BlockPos current;
	/** 화면이 없을 때 마지막으로 바라본 블록 - 화면이 뜨면 그게 열린 블록. */
	private BlockPos lookedNoScreen;
	/** 탭으로 열려는 블록(열리기 전까지 기억). */
	private BlockPos pendingOpen;
	/** 49-157차(사용자: "인벤토리 탭 누르면 마우스가 가운데로 가지 않게"): 탭을 누를 때의 마우스 자리(창 픽셀). */
	private double[] savedCursor;
	private long savedCursorAt;
	private long pendingSince;

	public ContainerTabsModule() {
		super("container_tabs", "인벤토리 탭", ModuleCategory.INVENTORY, "상자 화면에서 근처 상자를 바로 열기");
		defaultEnabled(true);
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
		kr.lunaslight.mod.util.SlotDrawHook.register(this::beforeSlot);
	}

	// ==================== 상태 ====================

	@Override
	public void onTick() {
		if (client.player == null || client.world == null) {
			tabs.clear();
			lastScreen = null;
			return;
		}
		Object screen = client.currentScreen;
		if (screen == null) {
			BlockPos looked = LunaCompat.targetedBlock(client);
			if (looked != null) {
				lookedNoScreen = looked;
			}
			if (pendingOpen != null && System.currentTimeMillis() - pendingSince > 2000) {
				pendingOpen = null;   // 열리지 않았다(멀어졌거나 서버가 거부) - 잊는다
			}
		}
		if (screen != lastScreen) {
			lastScreen = screen;
			if (screen instanceof HandledScreen<?>) {
				// 49-157차: 새 창이 열리면 바닐라가 마우스를 가운데로 옮긴다 - 탭을 누르던 자리로 되돌린다.
				if (savedCursor != null && pendingOpen != null && System.currentTimeMillis() - savedCursorAt < 3000) {
					restoreCursor(savedCursor);
				}
				savedCursor = null;
				// 49-244차(사용자: "방금 연 상자 표시"): 내 인벤토리(E)를 열 땐 "방금 연 상자"를 바꾸지 않는다 - 마지막으로 연 상자가 계속 표시된다.
				if (!(screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) && !(screen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen)) {
					current = pendingOpen != null ? pendingOpen : lookedNoScreen;
				}
				pendingOpen = null;
				rescan();
			} else {
				tabs.clear();
			}
		} else if (screen instanceof HandledScreen<?> && ++scanTicks % 10 == 0) {
			rescan();
		}
	}

	private final List<BlockPos> order = new ArrayList<>();
	private Object orderWorld;
	private double orderX, orderY, orderZ;

	private static boolean byPosAll(List<Tab> list, BlockPos p) {
		for (Tab t : list) {
			if (t.pos().equals(p)) {
				return true;
			}
		}
		return false;
	}

	/** 손이 닿는 거리 안의 보관함 블록을 모은다(49-244차: 한 번 세운 순서 유지). */
	private void rescan() {
		tabs.clear();
		if (!isEnabled()) {
			return;
		}
		try {
			Vec3d eye = LunaCompat.getEyePos(client.player);
			if (eye == null) {
				return;
			}
			double reach = reachDistance();
			int r = (int) Math.ceil(reach);
			int bx = (int) Math.floor(eye.x);
			int by = (int) Math.floor(eye.y);
			int bz = (int) Math.floor(eye.z);
			List<Tab> found = new ArrayList<>();
			for (int dx = -r; dx <= r; dx++) {
				for (int dy = -r; dy <= r; dy++) {
					for (int dz = -r; dz <= r; dz++) {
						BlockPos pos = new BlockPos(bx + dx, by + dy, bz + dz);
						double cx = pos.getX() + 0.5 - eye.x;
						double cy = pos.getY() + 0.5 - eye.y;
						double cz = pos.getZ() + 0.5 - eye.z;
						double dist = Math.sqrt(cx * cx + cy * cy + cz * cz);
						if (dist > reach) {
							continue;
						}
						Object be = client.world.getBlockEntity(pos);
						if (be == null) {
							continue;
						}
						BlockState state = client.world.getBlockState(pos);
						String kind = kindOf(state);
						if (!(be instanceof Inventory) && !"ender".equals(kind)) {
							continue;
						}
						if (!kindShown(kind)) {
							continue;
						}
						String desc = String.valueOf(state);
						if (desc.contains("type=right")) {
							continue;   // 큰 상자의 오른쪽 반쪽 - 왼쪽 반쪽 하나로 보인다
						}
						ItemStack icon = new ItemStack(state.getBlock());
						String name;
						try {
							name = icon.isEmpty() ? "보관함" : icon.getName().getString();
						} catch (Throwable t) {
							name = "보관함";
						}
						found.add(new Tab(pos, icon, name, dist));
					}
				}
			}
			found.sort((a, b) -> Double.compare(a.dist(), b.dist()));
			// 49-244차(사용자: "순서 안 바뀌게, 상자 옮겨도"): 예전엔 열 때마다(그리고 0.5초마다) 지금 눈 위치에서 가까운 순으로
			// 다시 줄을 세워서, 탭으로 다른 상자를 열거나 조금 움직이면 칸 순서가 바뀌었다. 한 번 세운 순서를 기억해 두고
			// 새로 생긴 상자만 뒤에 붙인다. 다른 월드로 가거나 처음 줄 세운 곳에서 8블록 넘게 벗어나면 새로 세운다.
			Object w = client.world;
			double ox = eye.x - orderX, oy = eye.y - orderY, oz = eye.z - orderZ;
			if (w != orderWorld || ox * ox + oy * oy + oz * oz > 64.0) {
				order.clear();
				orderWorld = w;
				orderX = eye.x;
				orderY = eye.y;
				orderZ = eye.z;
			}
			java.util.Map<BlockPos, Tab> byPos = new java.util.HashMap<>();
			for (Tab t : found) {
				byPos.put(t.pos(), t);
			}
			List<Tab> ordered = new ArrayList<>();
			for (BlockPos bp : order) {
				Tab t = byPos.remove(bp);
				if (t != null) {
					ordered.add(t);
				}
			}
			for (Tab t : found) {
				if (byPos.containsKey(t.pos())) {
					ordered.add(t);
					order.add(t.pos());
				}
			}
			// "방금 연 상자"가 큰 상자의 오른쪽 반쪽이면(칸은 왼쪽 반쪽 하나) 옆 칸으로 맞춘다
			if (current != null && !byPosAll(found, current)) {
				for (Tab t : found) {
					BlockPos q = t.pos();
					int dx = Math.abs(q.getX() - current.getX()), dz = Math.abs(q.getZ() - current.getZ());
					if (q.getY() == current.getY() && dx + dz == 1 && kindOf(client.world.getBlockState(q)).equals("chest")
							&& kindOf(client.world.getBlockState(current)).equals("chest")) {
						current = q;
						break;
					}
				}
			}
			int n = Math.min(ordered.size(), maxTabs.get());
			for (int i = 0; i < n; i++) {
				tabs.add(ordered.get(i));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerTabs:scan", t);
		}
	}

	/**
	 * 블록 종류(블록 id로 - 클래스 이름은 실제 게임에서 class_1234처럼 바뀌어 못 쓴다).
	 * chest / barrel / shulker / ender / furnace / hopper / dispenser / brewing / other.
	 */
	private static String kindOf(BlockState state) {
		String id = "";
		try {
			Object ident = LunaCompat.getItemId(new ItemStack(state.getBlock()).getItem());
			id = ident == null ? "" : String.valueOf(ident);
			int c = id.indexOf(':');
			id = c >= 0 ? id.substring(c + 1) : id;
		} catch (Throwable ignored) {
		}
		if (id.isEmpty()) {
			id = String.valueOf(state).toLowerCase(java.util.Locale.ROOT);
		}
		if (id.contains("ender_chest")) {
			return "ender";
		}
		if (id.contains("shulker_box")) {
			return "shulker";
		}
		if (id.contains("chest")) {
			return "chest";
		}
		if (id.contains("barrel")) {
			return "barrel";
		}
		if (id.contains("furnace") || id.contains("smoker")) {
			return "furnace";
		}
		if (id.contains("hopper")) {
			return "hopper";
		}
		if (id.contains("dispenser") || id.contains("dropper") || id.contains("crafter")) {
			return "dispenser";
		}
		if (id.contains("brewing_stand")) {
			return "brewing";
		}
		return "other";
	}

	private boolean kindShown(String kind) {
		return switch (kind) {
			case "chest" -> showChest.get();
			case "barrel" -> showBarrel.get();
			case "shulker" -> showShulker.get();
			case "ender" -> showEnder.get();
			case "furnace" -> showFurnace.get();
			case "hopper" -> showHopper.get();
			case "dispenser" -> showDispenser.get();
			case "brewing" -> showBrewing.get();
			default -> showOther.get();
		};
	}

	/** 블록에 손이 닿는 거리 - 1.20.5+ getBlockInteractionRange, 그 전엔 interactionManager.getReachDistance, 없으면 4.5. */
	private double reachDistance() {
		try {
			Object v = LunaCompat.callNoArg(client.player, "getBlockInteractionRange");
			if (v instanceof Number num && num.doubleValue() > 0) {
				return num.doubleValue();
			}
		} catch (Throwable ignored) {
		}
		try {
			Object v = LunaCompat.callNoArg(client.interactionManager, "getReachDistance");
			if (v instanceof Number num && num.doubleValue() > 0) {
				return num.doubleValue();
			}
		} catch (Throwable ignored) {
		}
		return 4.5;
	}

	// ==================== 그리기 + 클릭 ====================

	// 49-237차(사용자: "인벤토리 탭이 셜커 상자 미리보기랑 겹쳐서 셜커가 안 보여, 셜커를 위로"): 예전엔 화면을 다 그린 뒤
	// (툴팁까지 그린 뒤) 판을 그려서 셜커 미리보기 툴팁을 판이 덮었다. 이제 판은 <b>칸을 그리는 순간</b>(첫 칸 직전, 툴팁보다 먼저)에
	// 그리고, 클릭/끌기/Shift 내용물은 화면 뒤 콜백에서 처리한다. 칸 훅이 없는 옛 버전은 예전처럼 뒤에서 그린다.

	/** 판 자리와 격자. */
	private record Layout(int px, int py, int pw, int ph, int cols, int n, boolean onRight) {
	}

	private int lastMouseX = -1000, lastMouseY = -1000;
	private long slotDrawnAt;
	private boolean dragging;
	private double dragMx, dragMy;
	private int dragOx, dragOy;
	private boolean rightHeld;

	private boolean showsOn(Object screen) {
		if (!isEnabled() || client.player == null || tabs.isEmpty() || !(screen instanceof HandledScreen<?>)) {
			return false;
		}
		// 49-244차: 클래스 이름 글자 비교는 실제 게임(중간 이름 class_490 등)에선 안 맞았다 - 타입으로 본다
		if (screen instanceof net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen) {
			return false;
		}
		return inInventory.get() || !(screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen);
	}

	/** 위치 설정 + 끌어 옮긴 거리로 판 자리를 정한다(화면 밖으로는 안 나간다). */
	private Layout layout(Object screen, HandledScreen<?> hs) {
		int[] box = guiBox(hs);
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		int n = Math.min(tabs.size(), Math.min(MAX, maxTabs.get()));
		Side sd = side.get();
		int cols;
		int rows;
		if (sd == Side.TOP || sd == Side.BOTTOM) {
			// 위/아래: GUI 폭 안에 들어가는 열 수만큼 가로로 채우고 넘치면 다음 줄
			int maxCols = Math.max(1, (box[2] - BORDER * 2) / SLOT);
			rows = Math.max(1, (n + maxCols - 1) / maxCols);
			cols = Math.max(1, (n + rows - 1) / rows);
		} else {
			int availH = Math.max(SLOT + BORDER * 2, Math.min(box[3], sh - box[1] - 2));
			int maxRows = Math.max(1, (availH - BORDER * 2) / SLOT);
			cols = Math.max(1, (n + maxRows - 1) / maxRows);
			rows = Math.max(1, (n + cols - 1) / cols);
		}
		int pw = cols * SLOT + BORDER * 2;
		int ph = rows * SLOT + BORDER * 2;
		int rightEdge = box[0] + box[2];
		int helper = kr.lunaslight.mod.util.SidePanels.rightEdge(screen);
		if (helper > rightEdge) {
			rightEdge = helper;
		}
		int px;
		int py;
		switch (sd) {
			case LEFT -> {
				int leftX = box[0] - pw - 2;
				px = leftX >= 2 || rightEdge + 2 + pw > sw - 2 ? leftX : rightEdge + 2;
				py = box[1];
			}
			case TOP -> {
				px = box[0];
				int topY = box[1] - ph - 2;
				py = topY >= 2 || box[1] + box[3] + 2 + ph > sh - 2 ? topY : box[1] + box[3] + 2;
			}
			case BOTTOM -> {
				px = box[0];
				int botY = box[1] + box[3] + 2;
				py = botY + ph <= sh - 2 || box[1] - ph - 2 < 2 ? botY : box[1] - ph - 2;
			}
			default -> {
				int rightX = rightEdge + 2;
				px = rightX + pw <= sw - 2 || box[0] - pw - 2 < 2 ? rightX : box[0] - pw - 2;
				py = box[1];
			}
		}
		px += offX.get();
		py += offY.get();
		px = Math.max(2, Math.min(sw - pw - 2, px));
		py = Math.max(2, Math.min(sh - ph - 2, py));
		return new Layout(px, py, pw, ph, cols, n, px + pw / 2 >= box[0] + box[2] / 2);
	}

	/** 판 + 칸 + 아이콘(호버 칸 강조). 마우스가 올라간 탭을 돌려준다. */
	private Tab drawPanel(DrawContext ctx, Layout L, int mouseX, int mouseY) {
		LunaDraw.mcPanel(ctx, L.px(), L.py(), L.pw(), L.ph());
		Tab hover = null;
		for (int i = 0; i < L.n(); i++) {
			Tab t = tabs.get(i);
			int sx = L.px() + BORDER + (i % L.cols()) * SLOT;
			int sy = L.py() + BORDER + (i / L.cols()) * SLOT;
			LunaDraw.mcSlot(ctx, sx, sy);
			try {
				ctx.drawItem(t.icon(), sx + 1, sy + 1);
			} catch (Throwable ignored) {
			}
			if (current != null && current.equals(t.pos())) {
				// 지금 열려 있는 상자: 핫바 선택처럼 흰 테두리
				int c = LunaDraw.applyAlpha(0xFFFFFFFF);
				ctx.fill(sx, sy, sx + SLOT, sy + 1, c);
				ctx.fill(sx, sy + SLOT - 1, sx + SLOT, sy + SLOT, c);
				ctx.fill(sx, sy, sx + 1, sy + SLOT, c);
				ctx.fill(sx + SLOT - 1, sy, sx + SLOT, sy + SLOT, c);
			}
			if (!dragging && LunaDraw.in(mouseX, mouseY, sx + 1, sy + 1, 16, 16)) {
				ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x80FFFFFF);   // 바닐라 슬롯 호버
				hover = t;
			}
		}
		return hover;
	}

	/** 마우스 아래 탭(없으면 null)과 그 칸의 y. */
	private Tab hoverTab(Layout L, int mouseX, int mouseY, int[] outY) {
		for (int i = 0; i < L.n(); i++) {
			int sx = L.px() + BORDER + (i % L.cols()) * SLOT;
			int sy = L.py() + BORDER + (i / L.cols()) * SLOT;
			if (LunaDraw.in(mouseX, mouseY, sx + 1, sy + 1, 16, 16)) {
				outY[0] = sy;
				return tabs.get(i);
			}
		}
		return null;
	}

	/** 칸 그리기 훅: 첫 칸 직전에 판을 그린다(행렬이 GUI 원점으로 옮겨져 있어 되돌려 그린다). */
	private void beforeSlot(DrawContext ctx, net.minecraft.screen.slot.Slot slot) {
		Object screen = client.currentScreen;
		if (!showsOn(screen)) {
			return;
		}
		HandledScreen<?> hs = (HandledScreen<?>) screen;
		try {
			if (hs.getScreenHandler().slots.isEmpty() || hs.getScreenHandler().slots.get(0) != slot) {
				return;
			}
		} catch (Throwable t) {
			return;
		}
		slotDrawnAt = System.currentTimeMillis();
		int[] box = guiBox(hs);
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, -box[0], -box[1]);
		try {
			drawPanel(ctx, layout(screen, hs), lastMouseX, lastMouseY);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	private void onScreenFrame(Object screen, DrawContext ctx, int mouseX, int mouseY) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (!showsOn(screen)) {
			dragging = false;
			return;
		}
		HandledScreen<?> hs = (HandledScreen<?>) screen;
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		Layout L = layout(screen, hs);
		if (System.currentTimeMillis() - slotDrawnAt > 250) {
			drawPanel(ctx, L, mouseX, mouseY);   // 칸 훅이 없는 버전 - 예전처럼 맨 위에
		}
		int[] hy = new int[1];
		Tab hover = dragging ? null : hoverTab(L, mouseX, mouseY, hy);
		boolean onPanel = LunaDraw.in(mouseX, mouseY, L.px(), L.py(), L.pw(), L.ph());

		boolean shift = LunaCompat.isKeyPressed(client, GLFW.GLFW_KEY_LEFT_SHIFT)
				|| LunaCompat.isKeyPressed(client, GLFW.GLFW_KEY_RIGHT_SHIFT);
		if (hover != null && shift && preview.get()) {
			drawPreview(ctx, hover, L.onRight() ? L.px() + L.pw() + 2 : L.px() - 2, hy[0], L.onRight(), sw, sh);
		}

		// ---- 클릭(눌린 순간 한 번): 칸 = 열기, 판 테두리 = 끌어 옮기기 ----
		boolean down = client.getWindow() != null
			&& GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		if (down && !clickHeld) {
			clickHeld = true;
			if (hover != null && !holdingStack()) {
				open(hover);
			} else if (hover == null && onPanel && !holdingStack()) {
				dragging = true;
				dragMx = mouseX;
				dragMy = mouseY;
				dragOx = offX.get();
				dragOy = offY.get();
			}
		} else if (down && dragging) {
			offX.setValue(dragOx + (int) Math.round(mouseX - dragMx));
			offY.setValue(dragOy + (int) Math.round(mouseY - dragMy));
		} else if (!down) {
			if (dragging) {
				dragging = false;
				// 화면 밖으로 끌려 잘린 만큼은 버린다(다음에 열 때 판이 엉뚱한 데서 시작하지 않게)
				Layout now = layout(screen, hs);
				offX.setValue(offX.get() + (now.px() - (L.px())));
				offY.setValue(offY.get() + (now.py() - (L.py())));
				try {
					kr.lunaslight.mod.config.LunaClientConfig.save();
				} catch (Throwable ignored) {
				}
			}
			clickHeld = false;
		}
		// 판 테두리 우클릭 = 끈 거리 없애기(위치 설정 기준 자리로)
		boolean rdown = client.getWindow() != null && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
		if (rdown && !rightHeld && onPanel && hover == null && (offX.get() != 0 || offY.get() != 0)) {
			offX.setValue(0);
			offY.setValue(0);
			try {
				kr.lunaslight.mod.config.LunaClientConfig.save();
			} catch (Throwable ignored) {
			}
		}
		rightHeld = rdown;
	}

	/**
	 * Shift: 마지막으로 열어 봤을 때의 내용물(아이템 찾기 색인)을 바닐라 슬롯 격자로. 글자는 없다.
	 * anchorX는 오른쪽에 붙일 땐 판의 왼쪽 끝, 왼쪽에 붙일 땐 판의 오른쪽 끝.
	 */
	private void drawPreview(DrawContext ctx, Tab t, int anchorX, int y, boolean toRight, int sw, int sh) {
		ContainerIndex.Entry e = ContainerIndex.get(client, t.pos());
		List<ItemStack> stacks = new ArrayList<>();
		if (e != null) {
			List<Map.Entry<String, Integer>> items = new ArrayList<>(e.items.entrySet());
			items.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
			for (Map.Entry<String, Integer> it : items) {
				net.minecraft.item.Item item = LunaCompat.itemById(it.getKey());
				if (item == null) {
					continue;
				}
				stacks.add(new ItemStack(item, Math.max(1, it.getValue())));
				if (stacks.size() >= PREVIEW_COLS * PREVIEW_ROWS) {
					break;
				}
			}
		}
		boolean unknown = e == null;
		int cols = unknown ? 1 : PREVIEW_COLS;
		int rows = unknown ? 1 : Math.max(1, Math.min(PREVIEW_ROWS, (stacks.size() + cols - 1) / cols));
		int w = cols * SLOT + BORDER * 2;
		int h = rows * SLOT + BORDER * 2;
		int x = toRight ? anchorX : anchorX - w;
		if (x + w > sw - 2) {
			x = sw - 2 - w;
		}
		if (x < 2) {
			x = 2;
		}
		int py = Math.max(2, Math.min(y - BORDER, sh - 2 - h));
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslateZ(ctx, 400);
		LunaDraw.mcPanel(ctx, x, py, w, h);
		for (int i = 0; i < rows * cols; i++) {
			int sx = x + BORDER + (i % cols) * SLOT;
			int sy = py + BORDER + (i / cols) * SLOT;
			LunaDraw.mcSlot(ctx, sx, sy);
			ItemStack s = unknown ? (i == 0 ? new ItemStack(Items.BARRIER) : ItemStack.EMPTY)
				: (i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
			if (s.isEmpty()) {
				continue;
			}
			try {
				ctx.drawItem(s, sx + 1, sy + 1);
				if (!unknown) {
					LunaCompat.drawItemOverlay(ctx, client.textRenderer, s, sx + 1, sy + 1);
				}
			} catch (Throwable ignored) {
			}
		}
		LunaCompat.guiPop(ctx);
	}

	/** 지금 화면을 닫고 그 블록을 연다(블록 우클릭 한 번). */
	private void open(Tab t) {
		if (client.player == null) {
			return;
		}
		pendingOpen = t.pos();
		pendingSince = System.currentTimeMillis();
		ItemFinderModule.noteOpen(t.pos());   // 아이템 찾기 색인이 이 상자 위치로 기록하게
		savedCursor = cursorPos();
		savedCursorAt = System.currentTimeMillis();
		LunaCompat.setScreen(null);
		if (!LunaCompat.interactBlock(client, t.pos())) {
			pendingOpen = null;
			LunaCompat.sendActionBar(client, "§7이 버전에서는 바로 열 수 없습니다");
		}
	}

	/** 지금 마우스 자리(창 픽셀). 못 읽으면 null. */
	private double[] cursorPos() {
		try {
			long h = client.getWindow().getHandle();
			double[] x = new double[1];
			double[] y = new double[1];
			GLFW.glfwGetCursorPos(h, x, y);
			return new double[]{x[0], y[0]};
		} catch (Throwable t) {
			return null;
		}
	}

	/** 마우스를 그 자리로 옮기고, 게임이 들고 있는 마우스 좌표도 같이 맞춘다(안 맞추면 다음 움직임 전까지 어긋남). */
	private void restoreCursor(double[] pos) {
		try {
			long h = client.getWindow().getHandle();
			GLFW.glfwSetCursorPos(h, pos[0], pos[1]);
			Object mouse = client.mouse;
			String[] names = new String[]{"x", "y"};
			for (int i = 0; i < 2; i++) {
				try {
					java.lang.reflect.Field f = LunaCompat.getFieldCompat(mouse.getClass(), names[i]);
					f.setAccessible(true);
					f.setDouble(mouse, pos[i]);
				} catch (Throwable ignored) {
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerTabs:cursor", t);
		}
	}

	private boolean holdingStack() {
		try {
			ItemStack cursor = LunaCompat.cursorStack(client.player);
			return cursor != null && !cursor.isEmpty();
		} catch (Throwable t) {
			return false;
		}
	}

	/** HandledScreen의 GUI 사각형 {x, y, w, h}. */
	private int[] guiBox(HandledScreen<?> hs) {
		int ox = 0, oy = 0, bw = 176, bh = 166;
		try {
			java.lang.reflect.Field fx = LunaCompat.getFieldCompat(HandledScreen.class, "x");
			java.lang.reflect.Field fy = LunaCompat.getFieldCompat(HandledScreen.class, "y");
			java.lang.reflect.Field fw = LunaCompat.getFieldCompat(HandledScreen.class, "backgroundWidth");
			java.lang.reflect.Field fh = LunaCompat.getFieldCompat(HandledScreen.class, "backgroundHeight");
			fx.setAccessible(true);
			fy.setAccessible(true);
			fw.setAccessible(true);
			fh.setAccessible(true);
			ox = fx.getInt(hs);
			oy = fy.getInt(hs);
			bw = fw.getInt(hs);
			bh = fh.getInt(hs);
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerTabs:box", t);
		}
		return new int[]{ox, oy, bw, bh};
	}
}
