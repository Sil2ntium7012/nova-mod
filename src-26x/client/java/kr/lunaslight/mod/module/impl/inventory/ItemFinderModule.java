package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.util.LunaProjection;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.ContainerIndex;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * 49-27차: 아이템 찾기(사용자: "아이템에 가져다 대고 키를 누르면 인벤토리가 닫히면서 그 아이템이 어느 상자나
 * 어느 셜커에 있는지 표시해 주는 기능 - 아이템 잃어버린 거 찾게").
 *
 * 동작:
 *  ① 상자·통·셜커 화면을 열 때마다 그 상자의 **위치와 내용물**을 기록해 둔다(util.ContainerIndex,
 *     서버/월드 + 차원별로 config/lunaslight/containers.json에 저장). 상자 안에 든 셜커 상자는 그 안의
 *     내용물(아이템 컴포넌트/NBT)까지 같이 기록한다.
 *  ② 아무 화면에서나 아이템에 마우스를 올린 채 [찾기 키]를 누르면 화면이 닫히고, 그 아이템이 든 상자 자리에
 *     이펙트(맥동 테두리 상자 + 빛기둥 + "상자 ×64 / 23블록 북동" 이름표)가 뜬다. 화면 밖이면 가장자리 화살표.
 *     49-170차(사용자: "HUD가 아니라 상자에 이펙트가 뜨는 걸로 전면 교체")에 HUD 목록을 뺐다 - 요약은 액션바 한 줄.
 *  ③ 내 인벤토리에 든 셜커 상자 안도 같이 찾는다.
 *
 * 기록은 "내가 열어 본 상자"만이라 서버에서도 안전하다(월드를 스캔하지 않음).
 * 1.15.2는 Container/Slot 이름이 달라 컴파일에서 제외(ModuleManager가 리플렉션 등록).
 */
public class ItemFinderModule extends Module implements kr.lunaslight.mod.module.BackgroundTick {

	private final KeybindSetting findKey = register(new KeybindSetting(
			"find_key", "찾기 키", "아이템에 마우스를 올린 채 누르면 그 아이템이 어디에 있는지 찾아 줍니다.", com.mojang.blaze3d.platform.InputConstants.KEY_U));   // 49-245차: 기본 U

	// 49-234차(사용자: "화면 닫기 이딴 기능이 왜 있어, 쓸모없는 게 너무 많아"): [화면 닫기] 설정 삭제 - 찾으면 늘 화면을 닫는다
	// (상자 자리 이펙트는 화면이 닫혀야 보인다).

	private final BooleanSetting record = register(new BooleanSetting(
			"record", "상자 기록", "상자를 열 때마다 위치와 내용물을 기억해 둡니다. 끄면 새로 기록하지 않습니다.", true));

	private final IntSetting maxResults = register(new IntSetting(
			"max_results", "최대 개수", "한 번에 보여 줄 위치의 최대 개수입니다.", 5, 1, 10, 1));

	private final IntSetting showSeconds = register(new IntSetting(
			"show_seconds", "표시 시간", "찾은 결과를 화면에 띄워 두는 시간(초)입니다.", 12, 3, 60, 1).unit("초"));

	private final BooleanSetting alsoHeld = register(new BooleanSetting(
			"also_held", "손 아이템 포함", "화면이 없을 때 손에 든 아이템으로도 찾습니다. 꺼 두면 칸에 올린 아이템만 찾습니다.", false));

	private final BooleanSetting chatMessage = register(new BooleanSetting(
			"chat_message", "채팅 표시", "찾은 결과를 채팅창에도 남깁니다.", false));

	// 49-170차(사용자: "아이템 찾기 HUD가 아니라 상자에 이펙트가 뜨는 걸로 전면 교체"): HUD 목록·위치·글자 색 설정을 빼고
	// 상자 자리의 이펙트(맥동 테두리 상자 + 이름표 + 빛기둥, 화면 밖이면 가장자리 화살표)만 남겼다.
	private final ColorSetting accentColor = register(new ColorSetting(
			"accent_color", "이펙트 색", "상자 테두리, 빛기둥, 이름표의 색입니다.", 0xFFA9D973));
	private final BooleanSetting beam = register(new BooleanSetting(
			"beam", "빛기둥", "상자 위로 빛기둥을 세웁니다.", true));

	/** 마지막으로 바라본 블록(상자 화면이 열릴 때 그 상자의 위치로 씀). */
	private BlockPos lastLookedBlock;
	/** 49-125차: 인벤토리 탭이 "이 블록을 연다"고 알려 준 위치 - 바라본 블록 대신 이걸 상자 위치로 쓴다. */
	private static volatile BlockPos noted;

	private static volatile long notedAt;

	public static void noteOpen(BlockPos pos) {
		noted = pos;
		notedAt = System.currentTimeMillis();
	}

	/**
	 * 49-245차(사용자: "아이템 찾기 처음 할 때 이상한 곳에 상자 생기는 오류"): 화면이 없는 동안 조준한 블록. 조준이 블록에서 벗어나면 null로
	 * 돌아간다(예전엔 마지막으로 본 블록을 계속 들고 있어서, 엔티티나 서버 메뉴로 연 화면이 한참 전에 본 상자 자리에 적혔다).
	 */
	private BlockPos lookCandidate;
	private Object lastScreen;

	private final List<ContainerIndex.Hit> results = new ArrayList<>();
	private String resultTitle;
	private long resultsUntil;

	public ItemFinderModule() {
		super("item_finder", "아이템 찾기", ModuleCategory.INVENTORY, "열어 본 상자/셜커에서 아이템 위치 찾기");
		defaultEnabled(false);   // 49-157차(사용자: "아이템 찾기 기능 기본 비활성화")
	}

	// ==================== 기록 ====================

	@Override
	public void onTick() {
		if (client.player == null) {
			return;
		}
		recordTick();
		if (isEnabled() && findKey.isDown(client) && !keyHeld) {
			keyHeld = true;
			onFindKey();
		} else if (!findKey.isDown(client)) {
			keyHeld = false;
		}
	}

	/**
	 * 49-194차: 이 기능이 꺼져 있어도 블록 정보(상자 내용물)나 인벤토리 탭(Shift 미리보기)이 켜져 있으면 상자 기록은 한다
	 * (예전엔 아이템 찾기를 켜야만 기록돼서 두 기능이 늘 빈 기록을 봤다).
	 */
	@Override
	public void backgroundTick() {
		if (client.player != null && recordWanted()) {
			recordTick();
		}
	}

	private static boolean recordWanted() {
		kr.lunaslight.mod.module.ModuleManager mm = kr.lunaslight.mod.module.ModuleManager.get();
		return mm.find("block_info_hud").map(Module::isEnabled).orElse(false)
			|| mm.find("container_tabs").map(Module::isEnabled).orElse(false);
	}

	private void recordTick() {
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (noted != null && System.currentTimeMillis() - notedAt > 3000) {
			noted = null;   // 탭으로 열려던 상자가 안 열렸다
		}
		if (screen == null) {
			lookCandidate = LunaCompat.targetedBlock(client);
		}
		if (screen != lastScreen) {
			// 화면이 바뀌는 순간: 방금까지 열려 있던 상자를 **그 상자를 열 때 정한 자리**에 기록
			if (lastScreen instanceof AbstractContainerScreen<?> hs) {
				recordContainer(hs);
			}
			lastScreen = screen;
			openTicks = 0;
			// 새 상자 화면: 위치를 지금 정한다(인벤토리 탭이 알려 준 자리 > 화면이 뜨기 직전 조준한 블록)
			if (screen instanceof AbstractContainerScreen<?>) {
				if (noted != null) {
					lastLookedBlock = noted;
					noted = null;
				} else {
					lastLookedBlock = lookCandidate;
				}
			}
		} else if (screen instanceof AbstractContainerScreen<?> hs) {
			// 49-41차: 열려 있는 동안에도 1초마다 기록(서버가 내용물을 늦게 보내거나 연결이 끊겨 닫히는 경우 대비)
			if (++openTicks % 20 == 0) {
				recordContainer(hs);
			}
		}
	}

	private boolean keyHeld;
	private int openTicks;

	/** 지금 열린(또는 방금 닫힌) 상자 화면의 내용물을 기록. */
	private void recordContainer(AbstractContainerScreen<?> screen) {
		boolean allowed = isEnabled() ? record.get() : recordWanted();
		if (!allowed || client.player == null || lastLookedBlock == null) {
			return;
		}
		try {
			var handler = screen.getMenu();
			if (handler == client.player.inventoryMenu) {
				return; // 내 인벤토리는 기록하지 않음
			}
			List<ItemStack> contents = new ArrayList<>();
			int boxSlots = 0;
			for (Slot s : handler.slots) {
				if (s.container instanceof Inventory) {
					continue;
				}
				boxSlots++;
				ItemStack st = s.getItem();
				if (st != null && !st.isEmpty()) {
					contents.add(st);
				}
			}
			if (contents.isEmpty() && handler.slots.size() <= 45) {
				return; // 제작대·화로처럼 보관함이 아닌 화면
			}
			if (!ContainerIndex.isStorageAt(client, lastLookedBlock, boxSlots)) {
				return; // 49-234차: 그 자리에 보관함 블록이 없거나 칸 수가 다르다(서버 메뉴 등)
			}
			String title = "상자";
			try {
				title = screen.getTitle().getString();
			} catch (Throwable ignored) {
			}
			ContainerIndex.record(client, lastLookedBlock, title, contents);
		} catch (Throwable t) {
			LunaCompat.warnOnce("itemFinder:record", t);
		}
	}

	// ==================== 찾기 ====================

	private void onFindKey() {
		if (client.player == null) {
			return;
		}
		ItemStack target = hoveredStack();
		if (target == null || target.isEmpty()) {
			LunaCompat.sendActionBar(client, "§7찾을 아이템 칸에 마우스를 올린 채 눌러 주세요");
			return;
		}
		// 지금 열린 상자도 최신 내용으로 먼저 기록
		if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) instanceof AbstractContainerScreen<?> hs) {
			recordContainer(hs);
		}
		List<ContainerIndex.Hit> found = ContainerIndex.find(client, target, maxResults.get());
		results.clear();
		results.addAll(found);
		resultTitle = target.getHoverName().getString();
		resultsUntil = System.currentTimeMillis() + showSeconds.get() * 1000L;

		if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
			LunaCompat.setScreen(null); // 49-36차: 1.16은 openScreen
		}
		if (found.isEmpty()) {
			// 49-41차: 아무 표시도 없이 끝나면 "안 되는 것"으로 보인다 - 액션바로 알려 준다
			int known = ContainerIndex.size(client);
			LunaCompat.sendActionBar(client, known == 0
				? "§7상자를 한 번 열어 보면 기억해 둡니다"
				: "§f" + resultTitle + "§7 없음 (상자 " + known + "곳)");
		} else {
			// 49-170차: HUD 목록 대신 액션바 한 줄(어디에 몇 곳) - 자세한 위치는 상자 이펙트가 알려 준다
			StringBuilder sb = new StringBuilder("§f" + resultTitle + " §7" + found.size() + "곳");
			int inv = 0;
			ContainerIndex.Hit nearest = null;
			for (ContainerIndex.Hit h : found) {
				if (h.inInventory) {
					inv += h.count;
				} else if (nearest == null || h.distance(client) < nearest.distance(client)) {
					nearest = h;
				}
			}
			if (nearest != null) {
				sb.append(" §8| §7가장 가까운 ").append(nearest.shortName()).append(" ").append(Math.round(nearest.distance(client))).append("블록 ").append(nearest.direction(client));
			}
			if (inv > 0) {
				sb.append(" §8| §7내 인벤토리 셜커 ×").append(inv);
			}
			LunaCompat.sendActionBar(client, sb.toString());
		}
		if (chatMessage.get()) {
			if (found.isEmpty()) {
				LunaCompat.printLocalMessage(client, "§7[Nova] §f" + resultTitle + "§7 없음");
			} else {
				LunaCompat.printLocalMessage(client, "§7[Nova] §f" + resultTitle + " §7" + found.size() + "곳");
				for (ContainerIndex.Hit h : found) {
					LunaCompat.printLocalMessage(client, "§8| §7" + h.shortName() + " ×" + h.count
						+ (h.inInventory ? "" : " §8" + Math.round(h.distance(client)) + "블록 " + h.direction(client)));
				}
			}
		}
	}

	/**
	 * 지금 **마우스를 올린 칸**의 아이템. 49-32차 수정(사용자: "있지도 않은 아이템을 막 찾으려고 해"):
	 * 예전에는 올린 칸이 없으면 커서에 든 아이템, 화면이 없으면 **손에 든 아이템**으로 넘어갔다.
	 * 그래서 그냥 돌아다니다 키를 누르면 손에 든 아무 아이템을 찾아 버렸다.
	 * 이제 올린 칸이 없으면 아무것도 찾지 않고 안내만 한다([손에 든 아이템도] 설정으로 되살릴 수 있음).
	 */
	private ItemStack hoveredStack() {
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (screen instanceof AbstractContainerScreen) {
			try {
				java.lang.reflect.Field f = LunaCompat.getFieldCompat(AbstractContainerScreen.class, "focusedSlot");
				f.setAccessible(true);
				Object v = f.get(screen);
				if (v instanceof Slot s) {
					ItemStack st = s.getItem();
					if (st != null && !st.isEmpty()) {
						return st;
					}
				}
				ItemStack cursor = LunaCompat.cursorStack(client.player); // 49-36차: ≤1.16은 PlayerInventory#getCursorStack
				if (cursor != null && !cursor.isEmpty()) {
					return cursor;
				}
			} catch (Throwable t) {
				LunaCompat.warnOnce("itemFinder:hovered", t);
			}
			return null;
		}
		if (!alsoHeld.get()) {
			return null;
		}
		ItemStack main = client.player.getMainHandItem();
		return main == null || main.isEmpty() ? null : main;
	}

	// ==================== 표시 ====================

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (isPreview()) {
			drawPreview(context);
			return;
		}
		if (client.player == null || results.isEmpty() || System.currentTimeMillis() > resultsUntil || kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
			return;
		}
		drawWorldMarkers(context);
	}

	/**
	 * 설정 화면 미리보기. 49-234차(사용자: "아이템 찾기 미리보기가 이상해"): 선으로 그린 비스듬한 상자 대신 진짜 상자 아이콘(1.5배)을
	 * 맥동하는 테마색 테두리 칸 안에 두고, 위로 빛기둥과 이름표.
	 */
	private void drawPreview(GuiGraphicsExtractor context) {
		int cx = previewCenterX(), cy = previewCenterY() + 10;
		int col = accentColor.getArgb();
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
		int box = 32;
		int bx = cx - box / 2, by = cy - box / 2;
		if (beam.get()) {
			context.fillGradient(cx - 2, previewY() + 4, cx + 2, by, col & 0x00FFFFFF, LunaDraw.withAlpha(col, 0xA0));
		}
		// 49-245차: 모서리 둥글기 줄임(7/5 → 3/2)
		LunaDraw.roundRect(context, bx - 3, by - 3, box + 6, box + 6, 3, LunaDraw.withAlpha(col, Math.round(0x20 + 0x30 * pulse)));
		LunaDraw.roundRect(context, bx, by, box, box, 2, 0xB00A0C0F);
		LunaDraw.roundRectOutline(context, bx, by, box, box, 2, LunaDraw.withAlpha(col, 0xF0));
		ItemStack chest = new ItemStack(net.minecraft.world.item.Items.CHEST);
		if (LunaCompat.guiTransformSupported(context)) {
			LunaCompat.guiPush(context);
			LunaCompat.guiTranslate(context, cx - 12, cy - 12);
			LunaCompat.guiScale(context, 1.5f, 1.5f);
			context.item(chest, 0, 0);
			LunaCompat.guiPop(context);
		} else {
			context.item(chest, cx - 8, cy - 8);
		}
		drawTag(context, cx, by - 6, "상자 ×64", "23블록 북동", col);
	}

	/** 이름표: 위 줄 "상자 ×64", 아래 줄 거리와 방향. 가운데 정렬, 둥근 검은 바탕. */
	private void drawTag(GuiGraphicsExtractor context, int cx, int bottomY, String top, String sub, int col) {
		int w1 = LunaCompat.getTextWidth(client.font, top);
		int w2 = sub == null ? 0 : LunaCompat.getTextWidth(client.font, sub);
		int w = Math.max(w1, w2) + 10;
		int h = sub == null ? 13 : 23;
		int x = cx - w / 2, y = bottomY - h;
		LunaDraw.roundRectBorderedFlat(context, x, y, w, h, 2, 0xD00A0C0F, LunaDraw.withAlpha(col, 0x90));
		LunaCompat.drawHudText(context, client.font, top, cx - w1 / 2, y + 3, 0xFFF2F4F6);
		if (sub != null) {
			LunaCompat.drawHudText(context, client.font, sub, cx - w2 / 2, y + 13, col);
		}
		// 아래 작은 꼬리
		context.fill(cx - 1, y + h, cx + 1, y + h + 2, LunaDraw.withAlpha(col, 0x90));
	}

	private final double[][] view = new double[8][3];
	private static final int[][] EDGES = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

	/**
	 * 찾은 상자 자리에 이펙트: 맥동하는 테두리 상자(넓고 옅은 빛 + 가는 밝은 선), 빛기둥, 위에 이름표(개수 | 거리 방향).
	 * 화면 밖이면 화면 가장자리에 그 방향 화살표와 거리. 벽 너머로도 보인다(HUD 투영).
	 */
	private void drawWorldMarkers(GuiGraphicsExtractor context) {
		try {
			LunaProjection proj = LunaProjection.capture(client);
			if (proj == null) {
				return;
			}
			int col = accentColor.getArgb();
			double[] out = new double[3];
			float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
			double grow = 0.03 + 0.05 * pulse;
			int inv = 0;
			for (ContainerIndex.Hit hit : results) {
				if (hit.inInventory || hit.entry == null) {
					inv += hit.inInventory ? hit.count : 0;
					continue;
				}
				if (!ContainerIndex.stillThere(client, hit.entry)) {
					continue;   // 49-245차: 그 자리에 상자가 없다(지워졌거나 잘못된 기록) - 표시하지 않고 기록에서도 뺀다
				}
				double bx = hit.entry.x, by = hit.entry.y, bz = hit.entry.z;
				// 49-256차: 큰 상자는 두 칸을 한 상자로(ox/oz = 다른 반쪽 쪽, 기록 자리가 x, z 작은 쪽이라 0 또는 1)
				double ex2 = 1 + Math.max(0, hit.entry.ox), ez2 = 1 + Math.max(0, hit.entry.oz);
				double cxw = bx + ex2 / 2, czw = bz + ez2 / 2;
				double dx = cxw - proj.camX, dy = by + 0.5 - proj.camY, dz = czw - proj.camZ;
				int dist = (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
				// 상자 테두리(살짝 커졌다 작아졌다)
				for (int i = 0; i < 8; i++) {
					double x = (i & 1) == 0 ? bx - grow : bx + ex2 + grow;
					double z = (i & 2) == 0 ? bz - grow : bz + ez2 + grow;
					double y = (i & 4) == 0 ? by - grow : by + 1 + grow;
					proj.toView(x, y, z, view[i]);
				}
				float lw = dist > 40 ? 1.0f : dist > 16 ? 1.4f : 1.8f;
				int glow = LunaDraw.withAlpha(col, Math.round(0x2A + 0x2A * pulse));
				// 49-245차(사용자: "상자 테두리 끝머리 원이 너무 커"): 굵은 빛 선이 모서리마다 세 번 겹쳐 둥근 덩어리로 보였다 - 가늘게
				for (int[] edge : EDGES) {
					proj.drawViewSegment(context, view[edge[0]], view[edge[1]], lw * 1.8f, glow);
				}
				for (int[] edge : EDGES) {
					proj.drawViewSegment(context, view[edge[0]], view[edge[1]], lw, LunaDraw.withAlpha(col, 0xF0));
				}
				if (beam.get()) {
					proj.drawBeam(context, cxw, by + 1, czw, 3.0, 0.22, (col & 0x00FFFFFF) | 0x90000000);
				}
				// 이름표(상자 위) - 화면 밖이면 가장자리 화살표
				if (proj.project(cxw, by + 1.35 + grow, czw, out)
						&& out[0] >= -20 && out[0] <= proj.sw + 20 && out[1] >= -20 && out[1] <= proj.sh + 20) {
					drawTag(context, (int) out[0], (int) out[1], hit.shortName() + " ×" + hit.count, dist + "블록 " + hit.direction(client), col);
				} else {
					drawEdgeArrow(context, proj, cxw, by + 0.5, czw, dist, col);
				}
			}
			if (inv > 0) {
				// 내 인벤토리의 셜커 안: 자리가 없으니 화면 아래 가운데 한 줄
				String t = "내 인벤토리 셜커 안 ×" + inv;
				drawTag(context, proj.sw / 2, proj.sh - 58, t, null, col);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("itemFinder:world", t);
		}
	}

	/** 화면 밖 목표: 화면 가장자리(여백 14)에 그쪽을 가리키는 화살표 + 거리. */
	private void drawEdgeArrow(GuiGraphicsExtractor context, LunaProjection proj, double wx, double wy, double wz, int dist, int col) {
		double[] v = new double[3];
		proj.toView(wx, wy, wz, v);
		// 뷰 공간의 오른쪽/위 성분으로 방향(카메라 뒤면 뒤집힌다)
		// 49-256차(사용자: "내 뒤에 있는데 화살표가 화면 위에 나와 - 앞이면 위, 뒤면 아래"): 예전엔 카메라 뒤면 두 성분을 다 뒤집어서
		// 내 뒤 바닥의 상자가 화면 위로 갔다. 이제 뒤면 화면 아래 가장자리(왼쪽/오른쪽은 그대로), 앞이면 실제 방향.
		double ax = v[0], ay = -v[1];
		if (v[2] < 0) {
			ay = Math.abs(v[2]) + Math.abs(v[1]);   // 아래쪽으로
		}
		double len = Math.hypot(ax, ay);
		if (len < 1e-6) {
			return;
		}
		ax /= len;
		ay /= len;
		double cx = proj.sw / 2.0, cy = proj.sh / 2.0;
		double m = 14;
		double tx = Math.abs(ax) < 1e-6 ? Double.MAX_VALUE : ((ax > 0 ? proj.sw - m : m) - cx) / ax;
		double ty = Math.abs(ay) < 1e-6 ? Double.MAX_VALUE : ((ay > 0 ? proj.sh - m : m) - cy) / ay;
		double t = Math.min(tx, ty);
		double ex = cx + ax * t, ey = cy + ay * t;
		// 화살표: 끝점 + 뒤로 벌린 두 날개
		double bxp = ex - ax * 9, byp = ey - ay * 9;
		double px = -ay, py = ax;
		int c = LunaDraw.withAlpha(col, 0xF0);
		LunaProjection.lineAA(context, ex, ey, bxp + px * 5, byp + py * 5, 2f, c, proj.sw, proj.sh);
		LunaProjection.lineAA(context, ex, ey, bxp - px * 5, byp - py * 5, 2f, c, proj.sw, proj.sh);
		LunaProjection.lineAA(context, bxp + px * 5, byp + py * 5, bxp - px * 5, byp - py * 5, 2f, c, proj.sw, proj.sh);
		String s = dist + "블록";
		int w = LunaCompat.getTextWidth(client.font, s);
		int lx = (int) Math.round(ex - ax * 20 - w / 2.0), ly = (int) Math.round(ey - ay * 20 - 4);
		lx = Math.max(2, Math.min(proj.sw - w - 2, lx));
		ly = Math.max(2, Math.min(proj.sh - 10, ly));
		LunaDraw.roundRect(context, lx - 3, ly - 2, w + 6, 12, 2, 0xC00A0C0F);
		LunaCompat.drawHudText(context, client.font, s, lx, ly, col);
	}
}
