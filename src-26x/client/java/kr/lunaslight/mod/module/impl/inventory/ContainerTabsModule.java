package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.ContainerIndex;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;

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
	/** 49-522차: 칸 모양 = 버튼 하나(BTN) + 간격 2. */
	private static final int BTN = 20;
	private static final int CELL = BTN + 2;
	private static final int PAD = 2;
	private static final net.minecraft.resources.Identifier BTN_SPRITE = net.minecraft.resources.Identifier.withDefaultNamespace("widget/button");
	private static final net.minecraft.resources.Identifier BTN_HI = net.minecraft.resources.Identifier.withDefaultNamespace("widget/button_highlighted");
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

	/** 49-483차(사용자: "인벤토리 탭 이렇게 말고 상자 왼쪽이랑 오른쪽에 룰렛처럼 안 열린 상자가 조금 어둡게 쪽 있고, 클릭 드래그로 넘기면 그 상자로"). */
	public enum Style {
		ROULETTE("돌림판"),
		TABS("칸");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Style> style = register(new EnumSetting<>(
			"style", "모양", "칸: 상자마다 버튼 하나(리소스팩 버튼 모양)를 GUI 옆에 늘어놓습니다. 돌림판: 지금 상자 왼쪽과 오른쪽에 옆 상자가 어둡게 살짝 보이고, 끌어서 넘기거나 눌러서 엽니다.",
			Style.TABS, Style.class));   // 49-522차: 기본 = 칸(버튼)

	private final IntSetting sideBright = register(new IntSetting(
			"side_bright", "옆 상자 밝기", "돌림판에서 양옆 상자를 얼마나 밝게 보일지(%)입니다.", 45, 10, 100, 5));

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
		super("container_tabs", "인벤토리 탭", ModuleCategory.INVENTORY, "상자 양옆 돌림판으로 근처 상자를 바로 열기");
		// 49-502차(사용자: "기본 비활성화로 해 달라고 한 거지? 켠 사람들도 비활성화시켜 전부"): 기본 끔 + 설정 불러올 때 한 번 끔(LunaClientConfig 6)
		defaultEnabled(true);   // 49-530차(사용자: "창고 미리보기 모두 이번 업뎃 때 켜 주고 인벤토리 탭이랑 같이")
		inInventory.hidden();
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
		// 49-494차: 휠로 돌림판 넘기기
		try {
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (screen instanceof AbstractContainerScreen<?>) {
					net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen)
						.register((s, mx, my, ha, va) -> !carouselScroll(s, mx, my, va));
				}
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerTabs:scroll", t);
		}
		kr.lunaslight.mod.util.SlotDrawHook.register(this::beforeSlot);
	}

	// ==================== 상태 ====================

	@Override
	public void onTick() {
		if (client.player == null || client.level == null) {
			tabs.clear();
			lastScreen = null;
			return;
		}
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
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
			if (screen instanceof AbstractContainerScreen<?>) {
				// 49-157차: 새 창이 열리면 바닐라가 마우스를 가운데로 옮긴다 - 탭을 누르던 자리로 되돌린다.
				if (savedCursor != null && pendingOpen != null && System.currentTimeMillis() - savedCursorAt < 3000) {
					restoreCursor(savedCursor);
				}
				savedCursor = null;
				// 49-244차(사용자: "방금 연 상자 표시"): 내 인벤토리(E)를 열 땐 "방금 연 상자"를 바꾸지 않는다 - 마지막으로 연 상자가 계속 표시된다.
				if (!(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) && !(screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen)) {
					current = pendingOpen != null ? pendingOpen : lookedNoScreen;
				}
				pendingOpen = null;
				rescan();
			} else {
				tabs.clear();
			}
		} else if (screen instanceof AbstractContainerScreen<?> && ++scanTicks % 10 == 0) {
			rescan();
		}
		// 49-494차: 돌림판 옆 판이 "마지막으로 열었을 때 그대로" 보이게 지금 상자 칸을 복사해 둔다
		if (isEnabled() && screen instanceof AbstractContainerScreen<?> hs && !customGui(hs)) {
			snapshot(hs);
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
			Vec3 eye = LunaCompat.getEyePos(client.player);
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
						Object be = client.level.getBlockEntity(pos);
						if (be == null) {
							continue;
						}
						BlockState state = client.level.getBlockState(pos);
						String kind = kindOf(state);
						if (!(be instanceof Container) && !"ender".equals(kind)) {
							continue;
						}
						if (!kindShown(kind)) {
							continue;
						}
						String desc = String.valueOf(state);
						if (desc.contains("type=right")) {
							continue;   // 큰 상자의 오른쪽 반쪽 - 왼쪽 반쪽 하나로 보인다
						}
						// 49-302차(사용자: "상자끼리 있을 때 블록 너머에 있는 상자는 안 보이게"): 눈에서 그 칸(큰 상자면 옆 반쪽까지)의
						// 가운데나 면 가운데 중 하나라도 막힘 없이 보여야 칸에 넣는다. 막는 것 = 꽉 찬 불투명 블록과 다른 보관함.
						if (!seenFromEye(eye, pos, partnerOf(state, pos))) {
							continue;
						}
						ItemStack icon = new ItemStack(state.getBlock());
						String name;
						try {
							name = icon.isEmpty() ? "보관함" : icon.getHoverName().getString();
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
			Object w = client.level;
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
					if (q.getY() == current.getY() && dx + dz == 1 && kindOf(client.level.getBlockState(q)).equals("chest")
							&& kindOf(client.level.getBlockState(current)).equals("chest")) {
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
	/** 49-302차: 큰 상자의 다른 반쪽(왼쪽 반쪽 칸 기준 오른쪽). 아니면 null. */
	private BlockPos partnerOf(BlockState state, BlockPos pos) {
		String d = String.valueOf(state);
		if (!d.contains("type=left")) {
			return null;
		}
		// 상자를 앞(facing)에서 볼 때 왼쪽 반쪽 - 오른쪽 반쪽은 facing을 시계 방향으로 돈 쪽
		if (d.contains("facing=north")) {
			return new BlockPos(pos.getX() + 1, pos.getY(), pos.getZ());
		}
		if (d.contains("facing=south")) {
			return new BlockPos(pos.getX() - 1, pos.getY(), pos.getZ());
		}
		if (d.contains("facing=west")) {
			return new BlockPos(pos.getX(), pos.getY(), pos.getZ() - 1);
		}
		if (d.contains("facing=east")) {
			return new BlockPos(pos.getX(), pos.getY(), pos.getZ() + 1);
		}
		return null;
	}

	/** 49-302차: 눈에서 이 보관함(과 큰 상자 반쪽)의 가운데/면 가운데 중 하나라도 막힘 없이 보이나. */
	private boolean seenFromEye(Vec3 eye, BlockPos pos, BlockPos partner) {
		BlockPos[] cells = partner == null ? new BlockPos[]{pos} : new BlockPos[]{pos, partner};
		for (BlockPos c : cells) {
			double cx = c.getX() + 0.5, cy = c.getY() + 0.5, cz = c.getZ() + 0.5;
			if (clearTo(eye, cx, cy, cz, pos, partner)) {
				return true;
			}
			for (int f = 0; f < 6; f++) {
				double fx = cx + (f == 0 ? 0.45 : f == 1 ? -0.45 : 0);
				double fy = cy + (f == 2 ? 0.45 : f == 3 ? -0.45 : 0);
				double fz = cz + (f == 4 ? 0.45 : f == 5 ? -0.45 : 0);
				if (clearTo(eye, fx, fy, fz, pos, partner)) {
					return true;
				}
			}
		}
		return false;
	}

	private final BlockPos.MutableBlockPos rayCell = new BlockPos.MutableBlockPos();

	/** 눈에서 그 점까지(격자 따라가기) 지나는 칸에 막는 블록이 없나. 보관함 자신과 그 반쪽 칸은 안 막는다. */
	private boolean clearTo(Vec3 eye, double ex, double ey, double ez, BlockPos self, BlockPos partner) {
		double sx = eye.x, sy = eye.y, sz = eye.z;
		double dx = ex - sx, dy = ey - sy, dz = ez - sz;
		int x = (int) Math.floor(sx), y = (int) Math.floor(sy), z = (int) Math.floor(sz);
		int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
		double adx = Math.abs(dx), ady = Math.abs(dy), adz = Math.abs(dz);
		double tMaxX = adx < 1e-9 ? Double.MAX_VALUE : (dx > 0 ? x + 1 - sx : sx - x) / adx;
		double tMaxY = ady < 1e-9 ? Double.MAX_VALUE : (dy > 0 ? y + 1 - sy : sy - y) / ady;
		double tMaxZ = adz < 1e-9 ? Double.MAX_VALUE : (dz > 0 ? z + 1 - sz : sz - z) / adz;
		double tdX = adx < 1e-9 ? Double.MAX_VALUE : 1 / adx, tdY = ady < 1e-9 ? Double.MAX_VALUE : 1 / ady, tdZ = adz < 1e-9 ? Double.MAX_VALUE : 1 / adz;
		for (int guard = 0; guard < 64; guard++) {
			if (tMaxX < tMaxY && tMaxX < tMaxZ) {
				if (tMaxX > 1) {
					return true;
				}
				x += stepX;
				tMaxX += tdX;
			} else if (tMaxY < tMaxZ) {
				if (tMaxY > 1) {
					return true;
				}
				y += stepY;
				tMaxY += tdY;
			} else {
				if (tMaxZ > 1) {
					return true;
				}
				z += stepZ;
				tMaxZ += tdZ;
			}
			if (x == self.getX() && y == self.getY() && z == self.getZ()) {
				return true;
			}
			if (partner != null && x == partner.getX() && y == partner.getY() && z == partner.getZ()) {
				continue;
			}
			try {
				rayCell.set(x, y, z);
				BlockState st = client.level.getBlockState(rayCell);
				if (st != null && st.canOcclude()) {
					return false;
				}
				Object rbe = client.level.getBlockEntity(rayCell);
				if (rbe instanceof Container) {
					return false;   // 다른 보관함(상자 뒤 상자)
				}
			} catch (Throwable ignored) {
			}
		}
		return true;
	}

	private double reachDistance() {
		try {
			Object v = LunaCompat.callNoArg(client.player, "getBlockInteractionRange");
			if (v instanceof Number num && num.doubleValue() > 0) {
				return num.doubleValue();
			}
		} catch (Throwable ignored) {
		}
		try {
			Object v = LunaCompat.callNoArg(client.gameMode, "getReachDistance");
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
		if (!isEnabled() || client.player == null || tabs.isEmpty() || !(screen instanceof AbstractContainerScreen<?>)) {
			return false;
		}
		// 49-244차: 클래스 이름 글자 비교는 실제 게임(중간 이름 class_490 등)에선 안 맞았다 - 타입으로 본다
		if (screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) {
			return false;
		}
		// 49-486차(사용자 사진: 너굴마을 "업그레이드" 창 - "너굴 리팩 들어간 이런 거는 안 뜨게"): 서버가 리소스팩 글꼴(사용자 영역 글자 U+E000~)로
		// 창 그림을 그리는 메뉴는 상자가 아니라 서버 화면이다 - 제목에 그런 글자가 있으면 칸을 안 띄운다.
		if (customGui((AbstractContainerScreen<?>) screen)) {
			return false;
		}
		// 49-502차(사용자 사진: 내 인벤토리 양옆에 상자 판 - "인벤에서 안 보여 주게"): 내 인벤토리에선 안 띄운다
		return !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen);
	}

	private Object customFor;
	private boolean customWas;

	private boolean customGui(AbstractContainerScreen<?> hs) {
		if (hs == customFor) {
			return customWas;
		}
		customFor = hs;
		customWas = false;
		try {
			String t = hs.getTitle().getString();
			for (int i = 0; i < t.length(); ) {
				int cp = t.codePointAt(i);
				if ((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000) {
					customWas = true;
					break;
				}
				i += Character.charCount(cp);
			}
		} catch (Throwable ignored) {
		}
		return customWas;
	}

	/** 위치 설정 + 끌어 옮긴 거리로 판 자리를 정한다(화면 밖으로는 안 나간다). */
	private Layout layout(Object screen, AbstractContainerScreen<?> hs) {
		int[] box = guiBox(hs);
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int n = Math.min(tabs.size(), Math.min(MAX, maxTabs.get()));
		Side sd = side.get();
		int cols;
		int rows;
		if (sd == Side.TOP || sd == Side.BOTTOM) {
			// 위/아래: GUI 폭 안에 들어가는 열 수만큼 가로로 채우고 넘치면 다음 줄
			int maxCols = Math.max(1, (box[2] - PAD * 2) / CELL);
			rows = Math.max(1, (n + maxCols - 1) / maxCols);
			cols = Math.max(1, (n + rows - 1) / rows);
		} else {
			int availH = Math.max(CELL + PAD * 2, Math.min(box[3], sh - box[1] - 2));
			int maxRows = Math.max(1, (availH - PAD * 2) / CELL);
			cols = Math.max(1, (n + maxRows - 1) / maxRows);
			rows = Math.max(1, (n + cols - 1) / cols);
		}
		int pw = cols * CELL + PAD * 2;
		int ph = rows * CELL + PAD * 2;
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

	/**
	 * 49-522차(사용자: "인벤토리 탭 예전처럼 바꾸되 리소스팩 적용된 것처럼, 예전 건 인벤에 상자가 있는 느낌이었는데 지금은 하나의 멋진 버튼으로"):
	 * 판(상자 GUI) + 슬롯 격자 대신 상자마다 버튼 하나. 버튼은 마크 버튼 그림(widget/button) 그대로라 서버 리소스팩 버튼 모양을 따른다.
	 * 지금 열린 상자 = 강조 버튼 + 테마색 테두리, 마우스를 올리면 강조 버튼.
	 */
	private Tab drawPanel(GuiGraphicsExtractor ctx, Layout L, int mouseX, int mouseY) {
		Tab hover = null;
		for (int i = 0; i < L.n(); i++) {
			Tab t = tabs.get(i);
			int sx = L.px() + PAD + (i % L.cols()) * CELL;
			int sy = L.py() + PAD + (i / L.cols()) * CELL;
			boolean cur = current != null && current.equals(t.pos());
			boolean hov = !dragging && LunaDraw.in(mouseX, mouseY, sx, sy, BTN, BTN);
			if (hov) {
				hover = t;
			}
			try {
				ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, (hov || cur) ? BTN_HI : BTN_SPRITE, sx, sy, BTN, BTN);
			} catch (Throwable e) {
				LunaDraw.mcSlot(ctx, sx, sy);   // 그림이 없으면 예전 칸
			}
			int lift = hov ? -1 : 0;
			// 49-526차(사용자: "그 상자에 뭐가 있는지는 명확하게 떠야 해 - 가장 많은 아이템 띄우면 되는데"): 버튼 가운데 = 그 상자에 가장 많은
			// 아이템(크게), 상자 종류는 왼쪽 위에 작게. 한 번도 안 열어 본 상자는 상자 그림만.
			ItemStack top = topItem(t.pos());
			try {
				if (top.isEmpty()) {
					ctx.item(t.icon(), sx + (BTN - 16) / 2, sy + (BTN - 16) / 2 + lift);
				} else {
					ctx.item(top, sx + (BTN - 16) / 2, sy + (BTN - 16) / 2 + lift);
					LunaCompat.guiPush(ctx);
					try {
						LunaCompat.guiTranslateZ(ctx, 200);
						LunaCompat.guiTranslate(ctx, sx + 1, sy + 1 + lift);
						LunaCompat.guiScale(ctx, 0.5f, 0.5f);
						ctx.item(t.icon(), 0, 0);
					} finally {
						LunaCompat.guiPop(ctx);
					}
				}
			} catch (Throwable ignored) {
			}
			if (cur) {
				// 49-531차(사용자: "골라진 부분 초록 선이 안 예뻐 다른 식으로"): 테두리 대신 GUI 쪽 가장자리에 작은 흰 막대(책갈피처럼)
				int bx = L.onRight() ? sx - 3 : sx + BTN + 1;
				LunaDraw.roundRect(ctx, bx, sy + 4, 2, BTN - 8, 1, LunaDraw.applyAlpha(0xE6FFFFFF));
			}
		}
		return hover;
	}

	// 49-302차(사용자: "그 상자에 가장 많이 있는 아이템을 상자 오른쪽 아래 아이콘처럼"): 아이템 찾기 색인(ContainerIndex)에 적힌
	// 내용물 중 가장 많은 것을 칸 오른쪽 아래에 반 크기로. 한 번도 안 열어 본 상자(색인 없음)는 안 그린다.
	private final java.util.Map<BlockPos, Object[]> topCache = new java.util.HashMap<>();

	private ItemStack topItem(BlockPos pos) {
		ContainerIndex.Entry e = ContainerIndex.get(client, pos);
		if (e == null || e.items == null || e.items.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Object[] c = topCache.get(pos);
		if (c != null && c[0] == e && c[2] instanceof Integer h && h == e.looks.hashCode() + 31 * e.items.hashCode()) {
			return (ItemStack) c[1];
		}
		String best = null;
		int bestN = 0;
		// 49-466차: 겉모습(스킨)별 기록이 있으면 그걸로 - 가장 많은 것을 그 모델로
		Map<String, Integer> src = e.looks.isEmpty() ? e.items : e.looks;
		for (Map.Entry<String, Integer> it : src.entrySet()) {
			if (it.getValue() != null && it.getValue() > bestN) {
				bestN = it.getValue();
				best = it.getKey();
			}
		}
		ItemStack stack = best == null ? ItemStack.EMPTY : ContainerIndex.stackOfLook(best, 1);
		if (topCache.size() > 256) {
			topCache.clear();
		}
		topCache.put(pos, new Object[]{e, stack, e.looks.hashCode() + 31 * e.items.hashCode()});
		return stack;
	}

	private void drawTopItem(GuiGraphicsExtractor ctx, Tab t, int sx, int sy) {
		ItemStack top = topItem(t.pos());
		if (top.isEmpty()) {
			return;
		}
		try {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslateZ(ctx, 200);
			LunaCompat.guiTranslate(ctx, sx + 9, sy + 9);
			LunaCompat.guiScale(ctx, 0.5f, 0.5f);
			ctx.item(top, 0, 0);
			LunaCompat.guiPop(ctx);
		} catch (Throwable ignored) {
			try {
				LunaCompat.guiPop(ctx);
			} catch (Throwable ignored2) {
			}
		}
	}

	/** 마우스 아래 탭(없으면 null)과 그 칸의 y. */
	private Tab hoverTab(Layout L, int mouseX, int mouseY, int[] outY) {
		for (int i = 0; i < L.n(); i++) {
			int sx = L.px() + PAD + (i % L.cols()) * CELL;
			int sy = L.py() + PAD + (i / L.cols()) * CELL;
			if (LunaDraw.in(mouseX, mouseY, sx, sy, BTN, BTN)) {
				outY[0] = sy;
				return tabs.get(i);
			}
		}
		return null;
	}

	/** 칸 그리기 훅: 첫 칸 직전에 판을 그린다(행렬이 GUI 원점으로 옮겨져 있어 되돌려 그린다). */
	private void beforeSlot(GuiGraphicsExtractor ctx, net.minecraft.world.inventory.Slot slot) {
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (!showsOn(screen)) {
			return;
		}
		AbstractContainerScreen<?> hs = (AbstractContainerScreen<?>) screen;
		try {
			if (hs.getMenu().slots.isEmpty() || hs.getMenu().slots.get(0) != slot) {
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
			if (style.get() == Style.ROULETTE) {
				drawCarousel(ctx, hs, lastMouseX, lastMouseY);
			} else {
				drawPanel(ctx, layout(screen, hs), lastMouseX, lastMouseY);
			}
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	private void onScreenFrame(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		if (!showsOn(screen)) {
			dragging = false;
			shiftX = 0;
			carArmed = false;
			carDragging = false;
			return;
		}
		AbstractContainerScreen<?> hs = (AbstractContainerScreen<?>) screen;
		if (style.get() == Style.ROULETTE) {
			if (System.currentTimeMillis() - slotDrawnAt > 250) {
				// 칸 훅이 없는 버전 - 화면 렌더 밖이라 끄는 거리만큼 직접 옮긴다
				LunaCompat.guiPush(ctx);
				LunaCompat.guiTranslate(ctx, shiftX, 0);
				try {
					drawCarousel(ctx, hs, mouseX, mouseY);
				} finally {
					LunaCompat.guiPop(ctx);
				}
			}
			carouselInput(hs, mouseX, mouseY);
			return;
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		Layout L = layout(screen, hs);
		if (System.currentTimeMillis() - slotDrawnAt > 250) {
			drawPanel(ctx, L, mouseX, mouseY);   // 칸 훅이 없는 버전 - 예전처럼 맨 위에
		}
		int[] hy = new int[1];
		Tab hover = dragging ? null : hoverTab(L, mouseX, mouseY, hy);
		boolean onPanel = LunaDraw.in(mouseX, mouseY, L.px(), L.py(), L.pw(), L.ph());

		boolean shift = LunaCompat.isKeyPressed(client, InputConstants.KEY_LSHIFT)
				|| LunaCompat.isKeyPressed(client, InputConstants.KEY_RSHIFT);
		if (hover != null && shift && preview.get()) {
			drawPreview(ctx, hover, L.onRight() ? L.px() + L.pw() + 2 : L.px() - 2, hy[0], L.onRight(), sw, sh);
		}

		// ---- 클릭(눌린 순간 한 번): 칸 = 열기, 판 테두리 = 끌어 옮기기 ----
		boolean down = client.getWindow() != null
			&& kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 0);
		if (down && !clickHeld) {
			clickHeld = true;
			if (hover != null && !holdingStack()) {
				open(hover);
			} else if (false && hover == null && onPanel && !holdingStack()) {
				// 49-536차(사용자 사진: "드래그하다 이동하더니 밑으로 내려와"): 버튼 사이 틈을 누르면 판이 끌려 옮겨졌다 - 끌어 옮기기 없앰(자리는 [위치] 설정)
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
		boolean rdown = client.getWindow() != null && kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 1);
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
	private void drawPreview(GuiGraphicsExtractor ctx, Tab t, int anchorX, int y, boolean toRight, int sw, int sh) {
		ContainerIndex.Entry e = ContainerIndex.get(client, t.pos());
		List<ItemStack> stacks = new ArrayList<>();
		if (e != null) {
			// 49-466차: 겉모습(스킨)별 기록이 있으면 그 모델로 그린다
			List<Map.Entry<String, Integer>> items = new ArrayList<>((e.looks.isEmpty() ? e.items : e.looks).entrySet());
			items.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
			for (Map.Entry<String, Integer> it : items) {
				ItemStack look = ContainerIndex.stackOfLook(it.getKey(), Math.max(1, it.getValue()));
				if (look.isEmpty()) {
					continue;
				}
				look.setCount(Math.max(1, it.getValue()));
				stacks.add(look);
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
				ctx.item(s, sx + 1, sy + 1);
				if (!unknown) {
					LunaCompat.drawItemOverlay(ctx, client.font, s, sx + 1, sy + 1);
				}
			} catch (Throwable ignored) {
			}
		}
		LunaCompat.guiPop(ctx);
	}

	// ==================== 49-483차: 돌림판 ====================
	// 지금 연 상자(가운데 GUI) 왼쪽에 이전 상자, 오른쪽에 다음 상자가 어둡게 보인다. 옆 판을 누르면 그 상자를,
	// 끌면(옆 판이든 가운데 상자 빈 곳이든) 화면 전체가 같이 움직이고 놓으면 그쪽 상자를 연다. 휠로도 넘긴다. 끝에서 처음으로 돈다.
	//
	// 49-494차(사용자: "창이 이상하잖아 가운데 상자랑 같아야지, 아래 왜 상자 모양이 있어, 왜 이렇게 짧아, 리소스팩도 적용이 안 되고,
	// 앞에 화면이랑 같게, 클릭 휠로 옮기는 거 가운데 상자도 돼야지 다 같이 움직이고"):
	//  - 옆 판 = 가운데와 같은 바닐라 상자 그림(textures/gui/container/generic_54.png, 셜커는 shulker_box.png - 리소스팩 그대로)에
	//    줄 수(3/6)도 같게. 위 칸은 그 상자를 마지막으로 열었을 때 칸 그대로(아이템 그대로 복사 - 리소스팩 모델도 그대로),
	//    아래는 지금 내 인벤토리와 핫바(가운데와 같은 것), 제목과 "인벤토리" 글자도.
	//  - 판은 다 보이게(자리가 모자라면 화면 끝에서 잘림), 가운데 상자와 세로 가운데를 맞춘다.
	//  - 끄는 동안 ContainerShiftMixin이 화면 전체(가운데 상자 + 옆 판 + 칸 + 들고 있는 것)를 같이 옮긴다.

	private static final int GAP = 6;
	private static final int SWIPE = 28;
	private static final int DRAG_START = 6;

	/** 끄는 동안 화면 전체를 옮길 가로 거리(ContainerShiftMixin이 읽는다). 0이면 안 옮긴다. */
	public static volatile int shiftX;

	private boolean carDown;
	private boolean carDragging;
	private boolean carArmed;    // 눌렀지만 아직 덜 움직였다(가운데에서 누른 경우)
	private int carPress;        // 누른 곳: -1 왼쪽 판, 1 오른쪽 판, 2 가운데 상자, 0 없음
	private double carStartX;
	private int carDx;
	private long lastWheelAt;

	/** 상자를 마지막으로 열었을 때 위 칸(상자 몫) 그대로. 이번 실행 동안만 기억한다. */
	private final java.util.Map<BlockPos, List<ItemStack>> snaps = new java.util.LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<BlockPos, List<ItemStack>> e) {
			return size() > 128;
		}
	};
	private Object snapWorld;

	/** 가운데 상자의 칸 번호(내 인벤토리 등 모르면 -1). */
	private int centerIndex(AbstractContainerScreen<?> hs) {
		if (hs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen || current == null) {
			return -1;
		}
		BlockPos other = otherHalf(current);
		for (int i = 0; i < tabs.size(); i++) {
			BlockPos p = tabs.get(i).pos();
			if (p.equals(current) || p.equals(other)) {
				return i;
			}
		}
		return -1;
	}

	/** {왼쪽 상자, 오른쪽 상자} (없으면 null). */
	private Tab[] neighbours(AbstractContainerScreen<?> hs) {
		int n = Math.min(tabs.size(), Math.min(MAX, maxTabs.get()));
		if (n == 0) {
			return new Tab[]{null, null};
		}
		int c = centerIndex(hs);
		if (c < 0) {
			return new Tab[]{n > 1 ? tabs.get(n - 1) : null, tabs.get(0)};
		}
		if (n == 1) {
			return new Tab[]{null, null};
		}
		Tab right = tabs.get((c + 1) % n);
		Tab left = tabs.get((c - 1 + n) % n);
		return new Tab[]{left == right ? null : left, right};
	}

	/** 큰 상자의 다른 반쪽(왼쪽/오른쪽 어느 쪽이든). 아니면 null. */
	private BlockPos otherHalf(BlockPos pos) {
		if (pos == null || client.level == null) {
			return null;
		}
		BlockState st = client.level.getBlockState(pos);
		BlockPos p = partnerOf(st, pos);
		if (p != null) {
			return p;
		}
		String d = String.valueOf(st);
		if (!d.contains("type=right")) {
			return null;
		}
		if (d.contains("facing=north")) {
			return new BlockPos(pos.getX() - 1, pos.getY(), pos.getZ());
		}
		if (d.contains("facing=south")) {
			return new BlockPos(pos.getX() + 1, pos.getY(), pos.getZ());
		}
		if (d.contains("facing=west")) {
			return new BlockPos(pos.getX(), pos.getY(), pos.getZ() + 1);
		}
		if (d.contains("facing=east")) {
			return new BlockPos(pos.getX(), pos.getY(), pos.getZ() - 1);
		}
		return null;
	}

	/** 열린 상자 창의 위 칸(상자 몫)을 복사해 둔다(onTick에서). 상자 칸이 9의 배수(1~6줄)인 창만. */
	private void snapshot(AbstractContainerScreen<?> hs) {
		if (current == null || client.level == null || hs instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
			return;
		}
		if (snapWorld != client.level) {
			snapWorld = client.level;
			snaps.clear();
		}
		try {
			var slots = hs.getMenu().slots;
			int n = slots.size() - 36;
			if (n < 9 || n > 54 || n % 9 != 0) {
				return;
			}
			List<ItemStack> list = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				list.add(slots.get(i).getItem().copy());
			}
			snaps.put(current, list);
			BlockPos o = otherHalf(current);
			if (o != null) {
				snaps.put(o, list);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("containerTabs:snap", t);
		}
	}

	/** 판 하나 크기 {w, h, 줄 수(0 = 상자 그림이 아닌 블록)}. */
	private int[] cardSize(Tab t, int fallbackH) {
		String kind = client.level == null ? "other" : kindOf(client.level.getBlockState(t.pos()));
		boolean chestLike = kind.equals("chest") || kind.equals("barrel") || kind.equals("ender") || kind.equals("shulker");
		if (!chestLike) {
			return new int[]{176, fallbackH, 0};
		}
		List<ItemStack> snap = snaps.get(t.pos());
		int rows;
		if (snap != null) {
			rows = Math.max(1, Math.min(6, snap.size() / 9));
		} else {
			rows = kind.equals("chest") && otherHalf(t.pos()) != null ? 6 : 3;
		}
		if (kind.equals("shulker")) {
			rows = 3;
		}
		return new int[]{176, rows * SLOT + 17 + 7, rows};   // 49-502차: 상자 칸만(아래 인벤 부분 없음)
	}

	/** {왼쪽 판 x, 오른쪽 판 x, 왼쪽 판 w, 오른쪽 판 w, 왼쪽 판 y, 오른쪽 판 y, 왼쪽 h, 오른쪽 h, 왼쪽 줄, 오른쪽 줄}. */
	private int[] cardRects(AbstractContainerScreen<?> hs, Tab[] nb) {
		int[] box = guiBox(hs);
		int sh = client.getWindow().getGuiScaledHeight();
		int rightEdge = box[0] + box[2];
		int helper = kr.lunaslight.mod.util.SidePanels.rightEdge(hs);
		if (helper > rightEdge) {
			rightEdge = helper;
		}
		int[] out = new int[10];
		for (int s = 0; s < 2; s++) {
			Tab t = nb[s];
			int[] sz = t == null ? new int[]{176, box[3], 0} : cardSize(t, box[3]);
			int w = sz[0];
			int h = sz[1];
			int x = s == 0 ? box[0] - GAP - w : rightEdge + GAP;
			int y = box[1];   // 49-502차: 가운데 상자와 위를 맞춘다(상자 칸끼리 나란히)
			y = Math.max(0, Math.min(sh - h, y));
			out[s] = x;
			out[2 + s] = w;
			out[4 + s] = y;
			out[6 + s] = h;
			out[8 + s] = sz[2];
		}
		return out;
	}

	/** 다음 층(stratum)에서 덮을 어두운 칸 {x, y, w, h, alpha}. 아이템까지 같이 어둡게 하려고 마지막에 한 번에. */
	private final List<int[]> dimRects = new ArrayList<>();

	private void drawCarousel(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> hs, int mouseX, int mouseY) {
		dimRects.clear();
		try {
			drawCarousel0(ctx, hs, mouseX, mouseY);
		} finally {
			if (!dimRects.isEmpty()) {
				try {
					ctx.nextStratum();
				} catch (Throwable ignored) {
				}
				for (int[] d : dimRects) {
					ctx.fill(d[0], d[1], d[0] + d[2], d[1] + d[3], d[4] << 24);
				}
				dimRects.clear();
			}
		}
	}

	private void drawCarousel0(GuiGraphicsExtractor ctx, AbstractContainerScreen<?> hs, int mouseX, int mouseY) {
		Tab[] nb = neighbours(hs);
		int[] r = cardRects(hs, nb);
		int sw = client.getWindow().getGuiScaledWidth();
		int dx = carDragging ? carDx : 0;
		for (int s = 0; s < 2; s++) {
			Tab t = nb[s];
			if (t == null) {
				continue;
			}
			int x = r[s], w = r[2 + s], y = r[4 + s], h = r[6 + s];
			if (x + w <= 0 || x >= sw) {
				continue;   // 자리가 아예 없다
			}
			boolean hov = !carDragging && sideAt(hs, nb, mouseX, mouseY) == (s == 0 ? -1 : 1);
			boolean lit = hov || (s == 0 ? dx > SWIPE : dx < -SWIPE);
			if (r[8 + s] > 0) {
				drawChestCard(ctx, t, x, y, r[8 + s], lit);
			} else {
				drawCard(ctx, t, x, y, w, h, lit);
			}
			int ax = s == 0 ? Math.max(2, x) + 4 : Math.min(sw - 2, x + w) - 16;
			LunaIcons2.arrow(ctx, client, ax, y + h / 2 - 6, s == 0);
		}
	}

	private static final net.minecraft.resources.Identifier TEX_CHEST = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final net.minecraft.resources.Identifier TEX_SHULKER = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");

	/** 밝기(0.1~1). 마우스를 올리거나 끄는 중이면 조금 밝게. */
	private float brightness(boolean lit) {
		int bright = Math.max(10, Math.min(100, sideBright.get()));
		if (lit) {
			bright = Math.min(100, bright + (100 - bright) / 2);
		}
		return bright / 100f;
	}

	/**
	 * 가운데와 똑같은 상자 창(상자 칸만): 바닐라 그림(리소스팩) + 마지막으로 본 상자 칸 + 제목.
	 * 49-502차(사용자: "인벤 부분은 안 보이게, 상자 뒤에 이상한 검정 배경"): 아래 인벤 부분을 빼고 상자 칸 아래 테두리(7px)로 닫는다. 어둡게는
	 * 판 전체를 검게 덮던 것(둥근 모서리 밖까지 검은 네모) 대신 그림 자체를 어두운 색으로 칠하고, 칸 영역만 다음 층에서 덮어 아이템도 같이 어둡게.
	 */
	private void drawChestCard(GuiGraphicsExtractor ctx, Tab t, int x, int y, int rows, boolean lit) {
		int w = 176;
		int gridH = rows * SLOT;
		int h = gridH + 17 + 7;
		float b = brightness(lit);
		int g = Math.round(255 * b);
		int tint = 0xFF000000 | (g << 16) | (g << 8) | g;
		String kind = client.level == null ? "" : kindOf(client.level.getBlockState(t.pos()));
		boolean shulker = kind.equals("shulker");
		net.minecraft.resources.Identifier tex = shulker ? TEX_SHULKER : TEX_CHEST;
		int bottomV = shulker ? 159 : 215;
		try {
			ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, w, gridH + 17, 256, 256, tint);
			ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, x, y + gridH + 17, 0f, (float) bottomV, w, 7, 256, 256, tint);
			if (b < 0.999f) {
				// 칸 영역은 원래 밝기로 깔고 다음 층에서 아이템과 같이 덮는다(겹쳐 두 번 어두워지지 않게)
				ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex, x + 7, y + 17, 7f, 17f, 162, gridH, 256, 256);
				dimRects.add(new int[]{x + 7, y + 17, 162, gridH, Math.round((1f - b) * 255)});
			}
		} catch (Throwable e) {
			LunaCompat.warnOnce("containerTabs:blit", e);
			LunaDraw.mcPanel(ctx, x, y, w, h);
		}
		// 제목 - 바닐라처럼 회색, 그림자 없음(밝기만큼 어둡게)
		try {
			int tc = Math.round(0x40 * b);
			ctx.text(client.font, LunaCompat.textLiteral(LunaDraw.ellipsize(client.font, t.name(), w - 16)), x + 8, y + 6,
				0xFF000000 | (tc << 16) | (tc << 8) | tc, false);
		} catch (Throwable ignored) {
		}
		// 칸: 마지막으로 열었을 때 그대로. 처음 보는 상자면 아이템 찾기 색인(많은 순), 그것도 없으면 가운데에 방벽.
		List<ItemStack> snap = snaps.get(t.pos());
		List<ItemStack> top = snap;
		boolean unknown = false;
		if (top == null) {
			top = new ArrayList<>();
			ContainerIndex.Entry e = ContainerIndex.get(client, t.pos());
			if (e == null) {
				unknown = true;
			} else {
				List<Map.Entry<String, Integer>> items = new ArrayList<>((e.looks.isEmpty() ? e.items : e.looks).entrySet());
				items.sort((a, c) -> Integer.compare(c.getValue(), a.getValue()));
				for (Map.Entry<String, Integer> it : items) {
					ItemStack look = ContainerIndex.stackOfLook(it.getKey(), Math.max(1, it.getValue()));
					if (!look.isEmpty()) {
						look.setCount(Math.max(1, it.getValue()));
						top.add(look);
					}
					if (top.size() >= rows * 9) {
						break;
					}
				}
			}
		}
		for (int i = 0; i < rows * 9; i++) {
			ItemStack st = unknown ? (i == rows / 2 * 9 + 4 ? new ItemStack(Items.BARRIER) : ItemStack.EMPTY)
				: (i < top.size() ? top.get(i) : ItemStack.EMPTY);
			drawStack(ctx, st, x + 8 + (i % 9) * SLOT, y + 18 + (i / 9) * SLOT, !unknown);
		}
	}

	private void drawStack(GuiGraphicsExtractor ctx, ItemStack st, int x, int y, boolean count) {
		if (st == null || st.isEmpty()) {
			return;
		}
		try {
			ctx.item(st, x, y);
			if (count) {
				LunaCompat.drawItemOverlay(ctx, client.font, st, x, y);
			}
		} catch (Throwable ignored) {
		}
	}

	/** 어둡게(상자 그림이 아닌 판): 다음 층에서 판 안쪽(둥근 모서리 2px 안)을 덮는다. */
	private void darken(GuiGraphicsExtractor ctx, int x, int y, int w, int h, boolean lit) {
		int a = Math.round((1f - brightness(lit)) * 255);
		if (a > 0) {
			dimRects.add(new int[]{x + 2, y + 2, w - 4, h - 4, Math.min(255, a)});
		}
	}

	/** 상자 그림이 아닌 블록(화로, 깔때기 …)의 옆 판: 바닐라 판 + 블록 아이콘과 이름 + 마지막으로 본 내용물 + 어둡게. */
	private void drawCard(GuiGraphicsExtractor ctx, Tab t, int x, int y, int w, int h, boolean lit) {
		LunaDraw.mcPanel(ctx, x, y, w, h);
		try {
			ctx.item(t.icon(), x + 8, y + 6);
		} catch (Throwable ignored) {
		}
		ctx.text(client.font, LunaCompat.textLiteral(LunaDraw.ellipsize(client.font, t.name(), w - 40)), x + 28, y + 10, 0xFF404040, false);
		ContainerIndex.Entry e = ContainerIndex.get(client, t.pos());
		int gx = x + BORDER;
		int gy = y + 26;
		int rows = Math.max(1, (h - 26 - BORDER) / SLOT);
		List<ItemStack> stacks = new ArrayList<>();
		if (e != null) {
			List<Map.Entry<String, Integer>> items = new ArrayList<>((e.looks.isEmpty() ? e.items : e.looks).entrySet());
			items.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
			for (Map.Entry<String, Integer> it : items) {
				ItemStack look = ContainerIndex.stackOfLook(it.getKey(), Math.max(1, it.getValue()));
				if (!look.isEmpty()) {
					look.setCount(Math.max(1, it.getValue()));
					stacks.add(look);
				}
				if (stacks.size() >= PREVIEW_COLS * rows) {
					break;
				}
			}
		}
		for (int i = 0; i < PREVIEW_COLS * rows; i++) {
			int sx = gx + (i % PREVIEW_COLS) * SLOT;
			int sy = gy + (i / PREVIEW_COLS) * SLOT;
			if (sx + SLOT > x + w - BORDER + 1) {
				continue;
			}
			LunaDraw.mcSlot(ctx, sx, sy);
			ItemStack st = e == null ? (i == PREVIEW_COLS / 2 ? new ItemStack(Items.BARRIER) : ItemStack.EMPTY)
				: (i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
			drawStack(ctx, st, sx + 1, sy + 1, e != null);
		}
		darken(ctx, x, y, w, h, lit);
	}

	/** 화면에 보이는 옆 판 자리 안인지: -1 왼쪽, 1 오른쪽, 0 아님. */
	private int sideAt(AbstractContainerScreen<?> hs, Tab[] nb, double mx, double my) {
		int[] r = cardRects(hs, nb);
		int sw = client.getWindow().getGuiScaledWidth();
		for (int s = 0; s < 2; s++) {
			if (nb[s] == null) {
				continue;
			}
			int x1 = Math.max(0, r[s]);
			int x2 = Math.min(sw, r[s] + r[2 + s]);
			if (x2 > x1 && mx >= x1 && mx < x2 && my >= r[4 + s] && my < r[4 + s] + r[6 + s]) {
				return s == 0 ? -1 : 1;
			}
		}
		return 0;
	}

	/** 가운데 상자 창의 빈 곳(칸, 버튼 위가 아님)인지. */
	private boolean centerEmptyAt(AbstractContainerScreen<?> hs, double mx, double my) {
		int[] box = guiBox(hs);
		if (!LunaDraw.in((int) mx, (int) my, box[0], box[1], box[2], box[3])) {
			return false;
		}
		try {
			if (hs instanceof kr.lunaslight.mod.mixin.ContainerScreenAccessorMixin acc && acc.lunaslight$hoveredSlot() != null) {
				return false;
			}
			if (hs.getChildAt(mx, my).isPresent()) {
				return false;
			}
		} catch (Throwable ignored) {
		}
		return true;
	}

	/** 돌림판 입력: 옆 판을 누르면 그 상자, 옆 판이나 가운데 빈 곳을 끌면 화면 전체가 같이 움직이고 놓으면 넘긴다. */
	private void carouselInput(AbstractContainerScreen<?> hs, int mouseX, int mouseY) {
		boolean down = client.getWindow() != null
			&& kr.lunaslight.mod.util.LunaInput.mouseDown(net.minecraft.client.Minecraft.getInstance(), 0);
		Tab[] nb = neighbours(hs);
		if (down && !carDown) {
			carDown = true;
			carPress = 0;
			if (!holdingStack()) {
				int side = sideAt(hs, nb, mouseX, mouseY);
				if (side != 0) {
					carPress = side;
				} else if ((nb[0] != null || nb[1] != null) && centerEmptyAt(hs, mouseX, mouseY)) {
					carPress = 2;
				}
			}
			if (carPress != 0) {
				carStartX = mouseX;
				carDx = 0;
				carArmed = true;
				carDragging = carPress != 2;   // 가운데는 조금 움직여야 끌기 시작(그냥 누른 건 무시)
			}
		} else if (down && carArmed) {
			int raw = (int) Math.round(mouseX - carStartX);
			if (!carDragging && Math.abs(raw) >= DRAG_START && !holdingStack()) {
				carDragging = true;
			}
			if (carDragging) {
				int lim = 176 + GAP;
				carDx = Math.max(nb[1] == null ? 0 : -lim, Math.min(nb[0] == null ? 0 : lim, raw));
				shiftX = carDx;
			}
		} else if (!down) {
			if (carArmed) {
				Tab go = null;
				if (carDragging) {
					if (carDx <= -SWIPE) {
						go = nb[1];          // 왼쪽으로 끌었다 = 오른쪽(다음) 상자가 들어온다
					} else if (carDx >= SWIPE) {
						go = nb[0];          // 오른쪽으로 끌었다 = 왼쪽(이전) 상자
					} else if (Math.abs(carDx) < DRAG_START && carPress != 2) {
						go = carPress < 0 ? nb[0] : nb[1];   // 옆 판을 그냥 눌렀다
					}
				}
				carDragging = false;
				carArmed = false;
				carDx = 0;
				shiftX = 0;
				if (go != null) {
					open(go);
				}
			}
			carDown = false;
			carPress = 0;
		}
	}

	/** 휠: 가운데 상자 빈 곳이나 옆 판 위에서 굴리면 넘긴다(아래 = 다음, 위 = 이전). 칸 위는 그대로 둔다. true면 휠을 먹는다. */
	private boolean carouselScroll(Object screen, double mx, double my, double amount) {
		if (amount == 0 || style.get() != Style.ROULETTE || !showsOn(screen) || carDragging || holdingStack()) {
			return false;
		}
		AbstractContainerScreen<?> hs = (AbstractContainerScreen<?>) screen;
		Tab[] nb = neighbours(hs);
		if (sideAt(hs, nb, mx, my) == 0 && !centerEmptyAt(hs, mx, my)) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (now - lastWheelAt < 300 || (pendingOpen != null && now - pendingSince < 1000)) {
			return true;   // 새 창이 열리는 중 - 한 번에 여러 칸 넘어가지 않게
		}
		Tab go = amount < 0 ? nb[1] : nb[0];
		if (go == null) {
			return false;
		}
		lastWheelAt = now;
		open(go);
		return true;
	}

	/** 띠 바깥쪽 끝의 작은 화살표(흰색, 그림자). */
	private static final class LunaIcons2 {
		static void arrow(GuiGraphicsExtractor ctx, net.minecraft.client.Minecraft mc, int x, int y, boolean left) {
			try {
				LunaCompat.guiPush(ctx);
				LunaCompat.guiTranslateZ(ctx, 250);
				kr.lunaslight.mod.gui.LunaIcons.drawInBox(ctx, mc.font, left ? kr.lunaslight.mod.gui.LunaIcons.LEFT : kr.lunaslight.mod.gui.LunaIcons.RIGHT,
					x, y, 12, 0xE0FFFFFF);
			} catch (Throwable ignored) {
			} finally {
				LunaCompat.guiPop(ctx);
			}
		}
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
		// 49-531차(사용자: "화면이 깜빡이는데 최대한 없애 줘"): 예전엔 먼저 화면을 닫아(게임 화면이 한 번 보이고 마우스가 잡혔다 풀림) 깜빡였다.
		// 이제 지금 창을 연 채로 블록을 누른다 - 서버가 새 상자 창을 열며 이전 창을 닫으므로 화면이 바로 바뀐다.
		if (!LunaCompat.interactBlock(client, t.pos())) {
			pendingOpen = null;
			LunaCompat.sendActionBar(client, "§7이 버전에서는 바로 열 수 없습니다");
		}
	}

	/** 지금 마우스 자리(창 픽셀). 못 읽으면 null. */
	private double[] cursorPos() {
		return kr.lunaslight.mod.util.LunaInput.cursorPos(client);   // 49-215차: 26.3은 SDL
	}

	/** 마우스를 그 자리로 옮기고, 게임이 들고 있는 마우스 좌표도 같이 맞춘다(안 맞추면 다음 움직임 전까지 어긋남). */
	private void restoreCursor(double[] pos) {
		try {
			kr.lunaslight.mod.util.LunaInput.setCursorPos(client, pos[0], pos[1]);
			Object mouse = client.mouseHandler;
			String[] names = new String[]{"xpos", "ypos"};
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
	private int[] guiBox(AbstractContainerScreen<?> hs) {
		int ox = 0, oy = 0, bw = 176, bh = 166;
		try {
			java.lang.reflect.Field fx = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "x");
			java.lang.reflect.Field fy = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "y");
			java.lang.reflect.Field fw = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "backgroundWidth");
			java.lang.reflect.Field fh = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "backgroundHeight");
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
