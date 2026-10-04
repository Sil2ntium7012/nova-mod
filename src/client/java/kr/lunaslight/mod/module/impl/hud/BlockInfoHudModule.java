package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.gui.LunaIcons;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.ContainerIndex;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Property;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 블록 정보 - 보고 있는 블록이 <b>무엇이고 캐면 뭐가 나오는지</b>를 한눈에.
 *
 * <p>49-99차(사용자: "캐면 이런 거 저런 거 적지 말고 그냥 뭐 나오는지 딱, 행운 들면 몇 개~몇 개 뜨는지도,
 * 모드(Jade 같은 것) 느낌으로 - 다만 한글 + 'Minecraft' 자리에 나오는 아이템"): Jade/WTHIT식 작은 판.
 *
 * <p>49-194차(사용자: Jade/WTHIT 툴팁 사진 4장 + "약간 이런 느낌으로 블록 정보를 바꿔줘"): 판을 그 모양으로 다시.
 * <ul>
 *   <li>어두운 반투명 판 + 보라 윤곽선 + 둥근 모서리(기본값. [배경] 설정으로 바꿀 수 있다).</li>
 *   <li>왼쪽 블록 아이콘, 오른쪽 위 <b>굵은 이름</b>(방향이 있는 블록은 회색 "(남쪽)"), 그 아래 "Minecraft" 자리에
 *       <b>나오는 아이템</b>(행운이면 몇~몇 개). 마인크래프트 것이 아닌 블록은 그 모드 이름을 파란 기울임 글자로.</li>
 *   <li>오른쪽에 필요한 도구 아이콘, 그 오른쪽 아래 모서리에 작은 초록 체크(지금 든 걸로 캐짐) 또는 빨강 X.</li>
 *   <li>블록 상태 줄: 작물 성장(57% / 다 자람), 신호 세기, 반복기 지연, 비교기 모드, 꿀, 퇴비, 가마솥, 케이크,
 *       리스폰 정박기 충전, 소리 블록 음 높이.</li>
 *   <li>상자류(상자, 통, 셜커, 화로, 양조기, 호퍼 …)는 <b>마지막으로 열어 봤을 때의 내용물</b>을 아이콘 격자로
 *       (아이템 찾기 색인 util.ContainerIndex). 엔더 상자는 어디서 열었든 가장 최근 것. 내용물은 서버가 클라에 안 보내
 *       주므로 열어 본 것만 알 수 있다 - 그래서 몇 분 전 기록인지 옆에 적는다.</li>
 *   <li>캐는 중일 때만 판 아래쪽에 초록 막대가 차오른다.</li>
 * </ul>
 *
 * <p>도구 판정은 표가 아니라 실제로 재 본다(1.16.5 이하엔 태그가 없어서). 드롭은 전리품표가 서버 데이터라
 * 클라엔 없어 알려진 것만 표로 두고, 표에 없으면 블록 자기 자신으로 본다(섬세한 손길은 계산 안 함).
 */
public class BlockInfoHudModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_CENTER, 0, 42)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "블록 이름 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
	private final BooleanSetting showState = register(new BooleanSetting(
			"show_state", "블록 상태", "방향, 작물 성장, 신호 세기 같은 블록 상태를 보여 줍니다.", true));
	private final BooleanSetting showContents = register(new BooleanSetting(
			"show_contents", "상자 내용물", "열어 본 적이 있는 상자의 내용물을 보여 줍니다. 다시 열면 새로 기억합니다.", true));
	// 49-256차(사용자: "명령어 쓸 때 나오는 영어 이름도 보이게, 기본은 꺼짐")
	private final BooleanSetting showId = register(new BooleanSetting(
			"show_id", "영어 id", "명령어에 쓰는 블록 id(minecraft:oak_planks 같은)를 보여 줍니다.", false));

	/** 49-194차: Jade 기본 판 색(보라빛 검정)과 보라 윤곽선. */
	public static final int DEFAULT_BG = 0xE8100010;
	public static final int DEFAULT_OUTLINE = 0xFF5B2FA0;

	public BlockInfoHudModule() {
		super("block_info_hud", "블록 정보", ModuleCategory.HUD, "블록 | 나오는 아이템 | 상자 내용물");
		// 49-156차(사용자: "배경 설정 따로, 배경 색도 조절"): 이 기능도 [배경] [배경 색] [윤곽선] [배경 모양] 설정을 갖는다.
		// 49-194차: 기본값을 Jade 느낌(보라빛 판 + 보라 윤곽선 + 둥근)으로. 예전 값은 설정 파일 기본값 판(2)에서 한 번 되돌린다.
		enableHudStyle(DEFAULT_BG, true, DEFAULT_OUTLINE, kr.lunaslight.mod.module.Module.HudShape.ROUND);
	}

	// ==================== 그리기 ====================

	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (isPreview()) {
			drawPanel(context, previewInfo(), 3, true, 0.62f, null);
			return;
		}
		if (client.player == null || client.world == null) {
			return;
		}
		HitResult hit = client.crosshairTarget;
		if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = bh.getBlockPos();
		BlockState state;
		try {
			state = client.world.getBlockState(pos);
		} catch (Throwable ignored) {
			return;
		}
		if (state == null || state.isAir()) {
			return;
		}
		Info info = infoOf(state);
		if (info == null) {
			return;
		}
		ItemStack held = client.player.getMainHandStack();
		int fortune = fortuneLevel(held);
		boolean harvestable = !info.toolRequired || suitableFor(held, state);
		Contents contents = showContents.get() && info.container != null ? contentsAt(pos, info) : null;
		drawPanel(context, info, fortune, harvestable, breakingProgress(pos), contents);
	}

	private static final int PAD = 5;
	private static final int ICON = 16;
	private static final int DROP = 12;
	private static final int CELL = 18;
	private static final int GRID_COLS = 9;
	private static final int GRID_ROWS = 2;
	private static final int DIM = 0xFFAAAAAA;

	private void drawPanel(DrawContext ctx, Info info, int fortune, boolean harvestable, float progress, Contents contents) {
		var tr = client.textRenderer;
		float dScale = DROP / 16f;
		String name = "§l" + info.name + (showState.get() && info.suffix != null ? "§r§7 " + info.suffix : "");
		List<String> lines = new ArrayList<>(4);
		if (info.modName != null) {
			lines.add("§9§o" + info.modName);
		}
		if (showId.get() && info.id != null && !info.id.isEmpty()) {
			lines.add("§7" + info.id);
		}
		if (showState.get()) {
			lines.addAll(info.stateLines);
		}

		// ---- 폭 ----
		List<String> counts = new ArrayList<>();
		int dropsW = 0;
		for (Drop d : info.drops) {
			String c = d.format(fortune);
			counts.add(c);
			dropsW += (dropsW > 0 ? 6 : 0) + DROP + 2 + LunaCompat.getTextWidth(tr, c);
		}
		if (info.drops.isEmpty()) {
			dropsW = LunaCompat.getTextWidth(tr, "안 나옴");
		}
		int textW = Math.max(LunaCompat.getTextWidth(tr, name), dropsW);
		for (String l : lines) {
			textW = Math.max(textW, LunaCompat.getTextWidth(tr, l));
		}
		int textX = PAD + ICON + 6;
		int panelW = textX + textW + 10 + ICON + PAD;
		int gridCols = 0;
		int gridRows = 0;
		String ago = null;
		if (contents != null && !contents.stacks.isEmpty()) {
			int n = contents.stacks.size() + (contents.more > 0 ? 1 : 0);
			gridCols = Math.min(GRID_COLS, n);
			gridRows = Math.min(GRID_ROWS, (n + GRID_COLS - 1) / GRID_COLS);
			ago = agoText(contents.seen);
			int capW = LunaCompat.getTextWidth(tr, "내용물") + 8 + LunaCompat.getTextWidth(tr, ago);
			panelW = Math.max(panelW, PAD * 2 + Math.max(gridCols * CELL - 2, capW));
		}

		// ---- 높이 ----
		int nameRow = 11;
		int dropRow = DROP;
		int h = PAD + nameRow + dropRow;
		if (!lines.isEmpty()) {
			h += 2 + lines.size() * 10 - 1;
		}
		if (gridRows > 0) {
			h += 5 + 11 + gridRows * CELL - 2;
		}
		int panelH = h + PAD + 1;

		int x;
		int y;
		if (isPreviewBoxed()) {
			x = previewCenterX() - panelW / 2;
			y = previewCenterY() - panelH / 2;
		} else {
			x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), panelW);
			y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), panelH);
		}

		drawHudBoxShadow(ctx, x, y, panelW, panelH);
		drawHudBox(ctx, x, y, panelW, panelH);   // 49-156차: 이 기능의 [배경] 설정대로

		int top = y + PAD;
		int iconY = top + (nameRow + dropRow - ICON) / 2;
		// ---- 왼쪽: 블록 아이콘 ----
		if (!info.icon.isEmpty()) {
			ctx.drawItem(info.icon, x + PAD, iconY);
		}
		// ---- 이름(굵게) ----
		int tx = x + textX;
		LunaCompat.drawHudText(ctx, tr, name, tx, top + 1, textColor.getArgb());
		// ---- "Minecraft" 자리: 나오는 아이템 ----
		int dy = top + nameRow;
		if (info.drops.isEmpty()) {
			LunaCompat.drawHudText(ctx, tr, "안 나옴", tx, dy + 2, 0xFF7C838B);
		} else {
			int lx = tx;
			for (int i = 0; i < info.drops.size(); i++) {
				if (i > 0) {
					lx += 6;
				}
				drawItemScaled(ctx, info.drops.get(i).stack, lx, dy, dScale);
				lx += DROP + 2;
				String c = counts.get(i);
				LunaCompat.drawHudText(ctx, tr, c, lx, dy + 2, DIM);
				lx += LunaCompat.getTextWidth(tr, c);
			}
		}
		// ---- 오른쪽: 필요한 도구 + 체크/X 배지 ----
		int toolX = x + panelW - PAD - ICON;
		String mark = harvestable ? LunaIcons.CHECK : LunaIcons.CLOSE;
		int markColor = harvestable ? 0xFF7ED957 : 0xFFE0544E;
		if (!info.toolIcon.isEmpty()) {
			ctx.drawItem(info.toolIcon, toolX, iconY);
			// 도구 아이콘 오른쪽 아래 모서리에 작은 배지(아이템보다 앞에 오게 z를 올린다 - 1.21.5 이하)
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslateZ(ctx, 250f);
			LunaCompat.guiTranslate(ctx, toolX + 8, iconY + 8);
			LunaCompat.guiScale(ctx, 0.75f, 0.75f);
			LunaIcons.draw(ctx, tr, mark, 1, 1, 0xE0000000);
			LunaIcons.draw(ctx, tr, mark, 0, 0, markColor);
			LunaCompat.guiPop(ctx);
		} else {
			LunaIcons.draw(ctx, tr, mark, toolX + (ICON - LunaIcons.SIZE) / 2 + 1,
					kr.lunaslight.mod.gui.LunaDraw.iconY(iconY, ICON), markColor);
		}
		// ---- 모드 이름, 블록 상태 ----
		int ly = top + nameRow + dropRow + 2;
		for (String l : lines) {
			LunaCompat.drawHudText(ctx, tr, l, tx, ly, DIM);
			ly += 10;
		}
		// ---- 상자 내용물 ----
		if (gridRows > 0) {
			int gy = ly - (lines.isEmpty() ? 0 : 1) + 5;
			if (lines.isEmpty()) {
				gy = top + nameRow + dropRow + 5;
			}
			int gx = x + PAD;
			ctx.fill(gx, gy - 3, x + panelW - PAD, gy - 2, 0x22FFFFFF);
			LunaCompat.drawHudText(ctx, tr, "내용물", gx, gy + 1, DIM);
			LunaCompat.drawHudText(ctx, tr, ago, x + panelW - PAD - LunaCompat.getTextWidth(tr, ago), gy + 1, 0xFF6F7680);
			gy += 11;
			int max = gridCols * gridRows;
			boolean overflow = contents.stacks.size() + (contents.more > 0 ? 1 : 0) > max || contents.more > 0;
			int shown = overflow ? max - 1 : Math.min(max, contents.stacks.size());
			for (int i = 0; i < shown; i++) {
				int cx = gx + (i % gridCols) * CELL;
				int cy = gy + (i / gridCols) * CELL;
				ctx.fill(cx - 1, cy - 1, cx + 17, cy + 17, 0x33000000);
				ItemStack st = contents.stacks.get(i);
				ctx.drawItem(st, cx, cy);
				drawCount(ctx, contents.counts.get(i), cx, cy);
			}
			if (overflow) {
				int rest = contents.stacks.size() - shown + contents.more;
				int cx = gx + (shown % gridCols) * CELL;
				int cy = gy + (shown / gridCols) * CELL;
				ctx.fill(cx - 1, cy - 1, cx + 17, cy + 17, 0x33000000);
				String t = "+" + rest;
				LunaCompat.drawHudText(ctx, tr, t, cx + 8 - LunaCompat.getTextWidth(tr, t) / 2, cy + 4, DIM);
			}
		}

		// ---- 캐는 중이면 판 아래쪽 초록 막대 ----
		if (progress > 0f) {
			float p = progress > 1f ? 1f : progress;
			int bx = x + 4;
			int bw = panelW - 8;
			int by = y + panelH - 4;
			ctx.fill(bx, by, bx + bw, by + 2, 0x30FFFFFF);
			ctx.fill(bx, by, bx + Math.round(bw * p), by + 2, 0xFF7ED957);
		}
	}

	/** 격자 칸의 개수 글자(아이템보다 앞에 오게 z를 올린다). 1000 이상은 1.2k처럼 줄인다. */
	private void drawCount(DrawContext ctx, int count, int cx, int cy) {
		if (count <= 1) {
			return;
		}
		String t = count >= 1000 ? (count >= 10000 ? (count / 1000) + "k" : String.format(java.util.Locale.ROOT, "%.1fk", count / 1000f)) : String.valueOf(count);
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslateZ(ctx, 250f);
		LunaCompat.drawHudText(ctx, client.textRenderer, t, cx + 17 - LunaCompat.getTextWidth(client.textRenderer, t), cy + 9, 0xFFFFFFFF);
		LunaCompat.guiPop(ctx);
	}

	private static String agoText(long seen) {
		long s = Math.max(0L, (System.currentTimeMillis() - seen) / 1000L);
		if (s < 60) {
			return "방금";
		}
		if (s < 3600) {
			return (s / 60) + "분 전";
		}
		if (s < 86400) {
			return (s / 3600) + "시간 전";
		}
		return (s / 86400) + "일 전";
	}

	/** 작은 아이콘: 16px 아이템을 s배로 축소해서 (x,y) 좌상단에 그림. */
	private void drawItemScaled(DrawContext ctx, ItemStack stack, float x, float y, float s) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, x, y);
		LunaCompat.guiScale(ctx, s, s);
		ctx.drawItem(stack, 0, 0);
		LunaCompat.guiPop(ctx);
	}

	// ==================== 상자 내용물(열어 본 기록) ====================

	private static final class Contents {
		final List<ItemStack> stacks = new ArrayList<>();
		final List<Integer> counts = new ArrayList<>();
		int more;
		long seen;
	}

	private ContainerIndex.Entry contentsEntry;
	private long contentsSeen;
	private Contents contentsCache;

	private Contents contentsAt(BlockPos pos, Info info) {
		ContainerIndex.Entry e;
		try {
			if (info.container == ContainerKind.ENDER) {
				e = ContainerIndex.latestTitled(client, info.name);
			} else {
				e = ContainerIndex.get(client, pos);
				if (e == null && info.container == ContainerKind.CHEST && info.partner != null) {
					e = ContainerIndex.get(client, pos.add(info.partner[0], 0, info.partner[1]));
				}
			}
		} catch (Throwable t) {
			return null;
		}
		if (e == null || e.items.isEmpty()) {
			return null;
		}
		if (e == contentsEntry && e.seen == contentsSeen && contentsCache != null) {
			return contentsCache;
		}
		Contents c = new Contents();
		c.seen = e.seen;
		List<Map.Entry<String, Integer>> items = new ArrayList<>(e.items.entrySet());
		items.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		for (Map.Entry<String, Integer> it : items) {
			if (c.stacks.size() >= GRID_COLS * GRID_ROWS) {
				c.more++;
				continue;
			}
			Item item = LunaCompat.itemById(it.getKey());
			if (item == null) {
				continue;
			}
			c.stacks.add(new ItemStack(item));
			c.counts.add(it.getValue());
		}
		contentsEntry = e;
		contentsSeen = e.seen;
		contentsCache = c;
		return c;
	}

	// ==================== 캔 정도(막대기) ====================
	// 지금 부수고 있는 블록의 진행도(0~1). ClientPlayerInteractionManager의 필드를 야른 이름으로 읽는다.
	// 49-122차(사용자: "캐는 거(막대 차는 게) 안 보여"): 예전엔 raw getDeclaredField(야른 이름)이라 런타임
	// 난독화 필드를 못 찾아 항상 0이었다(그래서 막대가 안 참). LunaCompat.getFieldValue가 yarnmap으로
	// 런타임 이름을 해석하므로 그걸로 바꾼다.
	private float breakingProgress(BlockPos pos) {
		Object im = client.interactionManager;
		if (im == null) {
			return 0f;
		}
		try {
			Object bp = LunaCompat.getFieldValue(im, "currentBreakingPos", "destroyBlockPos");
			if (bp != null && pos != null && !bp.equals(pos)) {
				return 0f;   // 다른(또는 안 캐는) 블록
			}
			Object v = LunaCompat.getFieldValue(im, "currentBreakingProgress", "destroyProgress");
			if (v instanceof Number n) {
				float f = n.floatValue();
				return f < 0f ? 0f : (f > 1f ? 1f : f);
			}
		} catch (Throwable ignored) {
		}
		return 0f;
	}

	/** 손에 든 아이템의 행운 레벨(없으면 0). */
	private static int fortuneLevel(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		try {
			for (LunaCompat.EnchantInfo e : LunaCompat.enchantments(stack)) {
				String id = e.id();
				if (id != null && (id.endsWith("fortune") || id.endsWith("행운"))) {
					return e.level();
				}
			}
		} catch (Throwable ignored) {
		}
		return 0;
	}

	// ==================== 블록 한 개를 읽어 들이기(캐시) ====================

	private enum Fkind { NONE, MULT, BONUS }

	private static final class Drop {
		final ItemStack stack;
		final int min;
		final int max;
		final Fkind fortune;
		final String fixedText;   // "드묾"처럼 숫자가 아닌 경우(잎 → 묘목). null이면 숫자로 계산.

		Drop(ItemStack stack, int min, int max, Fkind fortune) {
			this.stack = stack;
			this.min = min;
			this.max = max;
			this.fortune = fortune;
			this.fixedText = null;
		}

		Drop(ItemStack stack, String fixedText) {
			this.stack = stack;
			this.min = 0;
			this.max = 0;
			this.fortune = Fkind.NONE;
			this.fixedText = fixedText;
		}

		/** 손에 든 행운 레벨(f)로 개수 문구. "×3" 또는 "1~4". */
		String format(int f) {
			if (fixedText != null) {
				return fixedText;
			}
			int lo = min;
			int hi = max;
			if (f > 0) {
				switch (fortune) {
					case MULT -> hi = max * (f + 1);          // ore_drops: ×1~(행운+1)
					case BONUS -> hi = max + f;               // uniform_bonus: +0~행운
					default -> {
					}
				}
			}
			return lo == hi ? "×" + lo : lo + "~" + hi;
		}
	}

	private enum ContainerKind { CHEST, ENDER, OTHER }

	private static final class Info {
		String name = "";
		String id;                              // 49-256차: 블록 id(minecraft:oak_planks)
		String suffix;                          // 49-194차: 이름 뒤 회색 "(남쪽)" - 방향이 있는 블록만
		String modName;                         // 49-194차: 마인크래프트 것이 아니면 그 모드 이름(파란 기울임)
		final List<String> stateLines = new ArrayList<>(2);
		ContainerKind container;                // 49-194차: 상자류면 종류(내용물 기록을 찾는다), 아니면 null
		int[] partner;                          // 큰 상자의 나머지 반쪽 (dx, dz)
		ItemStack icon = ItemStack.EMPTY;
		final List<Drop> drops = new ArrayList<>(2);
		ItemStack toolIcon = ItemStack.EMPTY;   // 필요한 도구(등급 포함). 맨손 블록이면 EMPTY.
		boolean toolRequired;
	}

	private final Map<BlockState, Info> cache = new HashMap<>();

	private Info infoOf(BlockState state) {
		Info hit = cache.get(state);
		if (hit != null) {
			return hit;
		}
		Info info = new Info();
		try {
			info.name = state.getBlock().getName().getString();
			if (info.name == null || info.name.isEmpty()) {
				return null;
			}
			info.icon = stackOf(state);
			String st = String.valueOf(state);
			int a = st.indexOf('{'), b = st.indexOf('}');
			info.id = a >= 0 && b > a ? st.substring(a + 1, b) : null;
			fillDrops(info, state);
			fillTool(info, state);
			fillState(info, state);
		} catch (Throwable t) {
			LunaCompat.warnOnce("blockInfo:read", t);
			return null;
		}
		if (cache.size() > 512) {
			cache.clear();
		}
		cache.put(state, info);
		return info;
	}

	private static ItemStack stackOf(BlockState state) {
		try {
			ItemStack s = new ItemStack(state.getBlock());
			return s == null ? ItemStack.EMPTY : s;
		} catch (Throwable ignored) {
			return ItemStack.EMPTY;
		}
	}

	// ==================== 49-194차: 블록 상태, 모드 이름, 상자 종류 ====================

	private static Map<String, Object> props(BlockState state) {
		Map<String, Object> m = new LinkedHashMap<>();
		try {
			for (Property<?> p : state.getProperties()) {
				m.put(p.getName(), state.get(p));
			}
		} catch (Throwable ignored) {
		}
		return m;
	}

	private static int maxInt(BlockState state, String name) {
		try {
			for (Property<?> p : state.getProperties()) {
				if (!p.getName().equals(name)) {
					continue;
				}
				int max = 0;
				for (Object v : p.getValues()) {
					if (v instanceof Integer i && i > max) {
						max = i;
					}
				}
				return max;
			}
		} catch (Throwable ignored) {
		}
		return 0;
	}

	private static final java.util.Set<String> CROPS = new java.util.HashSet<>(java.util.Arrays.asList(
			"wheat", "carrots", "potatoes", "beetroots", "nether_wart", "cocoa", "sweet_berry_bush",
			"melon_stem", "pumpkin_stem", "torchflower_crop", "pitcher_crop", "kelp", "bamboo_sapling"));

	private static final java.util.Set<String> CONTAINERS = new java.util.HashSet<>(java.util.Arrays.asList(
			"barrel", "furnace", "smoker", "blast_furnace", "brewing_stand", "hopper", "dispenser", "dropper",
			"crafter", "chiseled_bookshelf", "decorated_pot"));

	private static String dirName(Object v) {
		switch (String.valueOf(v).toLowerCase(java.util.Locale.ROOT)) {
			case "north": return "북쪽";
			case "south": return "남쪽";
			case "east": return "동쪽";
			case "west": return "서쪽";
			case "up": return "위";
			case "down": return "아래";
			default: return null;
		}
	}

	private static int intOf(Object v) {
		return v instanceof Integer i ? i : -1;
	}

	private void fillState(Info info, BlockState state) {
		String full = LunaCompat.blockId(state);
		String ns = "minecraft";
		String id = full;
		if (full != null && full.indexOf(':') >= 0) {
			ns = full.substring(0, full.indexOf(':'));
			id = full.substring(full.indexOf(':') + 1);
		}
		if (id == null) {
			id = "";
		}
		if (!"minecraft".equals(ns)) {
			String modName = ns;
			try {
				final String n = ns;
				modName = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(n)
						.map(c -> c.getMetadata().getName()).orElse(n);
			} catch (Throwable ignored) {
			}
			info.modName = modName;
		}
		Map<String, Object> p = props(state);
		Object facing = p.get("facing");
		if (facing != null) {
			String d = dirName(facing);
			if (d != null) {
				info.suffix = "(" + d + ")";
			}
		}
		// 상자 종류
		if (id.equals("ender_chest")) {
			info.container = ContainerKind.ENDER;
		} else if (id.equals("chest") || id.equals("trapped_chest")) {
			info.container = ContainerKind.CHEST;
			info.partner = chestPartner(String.valueOf(p.get("type")), facing);
		} else if (id.endsWith("shulker_box") || id.endsWith("_chest") || CONTAINERS.contains(id)) {
			info.container = ContainerKind.OTHER;
		}
		// 상태 줄
		List<String> out = info.stateLines;
		Object age = p.get("age");
		if (age instanceof Integer a && (CROPS.contains(id) || id.endsWith("_stem"))) {
			int max = maxInt(state, "age");
			if (max > 0) {
				out.add(a >= max ? "§a다 자람" : "§7성장 §f" + Math.round(a * 100f / max) + "%");
			}
		}
		if (p.get("power") instanceof Integer pw) {
			out.add("§7신호 세기 §f" + pw);
		}
		if (id.equals("repeater") && p.get("delay") instanceof Integer dl) {
			out.add("§7지연 §f" + String.format(java.util.Locale.ROOT, "%.1f", dl * 0.1f) + "초");
		}
		if (id.equals("comparator") && p.get("mode") != null) {
			out.add("§7모드 §f" + ("subtract".equals(String.valueOf(p.get("mode")).toLowerCase(java.util.Locale.ROOT)) ? "빼기" : "비교"));
		}
		if (p.get("honey_level") instanceof Integer hl) {
			out.add("§7꿀 §f" + hl + "/5");
		}
		if (id.equals("composter") && p.get("level") instanceof Integer lv) {
			out.add(lv >= 8 ? "§a퇴비 완성" : "§7퇴비 §f" + Math.min(lv, 7) + "/7");
		}
		if (id.contains("cauldron") && p.get("level") instanceof Integer lv) {
			out.add("§7양 §f" + lv + "/3");
		}
		if (id.equals("cake") && p.get("bites") instanceof Integer b) {
			out.add("§7남은 조각 §f" + (7 - b) + "/7");
		}
		if (id.equals("respawn_anchor") && p.get("charges") instanceof Integer ch) {
			out.add("§7충전 §f" + ch + "/4");
		}
		if (id.equals("note_block") && p.get("note") instanceof Integer nt) {
			out.add("§7음 높이 §f" + nt);
		}
		if (id.equals("farmland") && p.get("moisture") instanceof Integer mo) {
			out.add(mo >= 7 ? "§b젖음" : "§7마름");
		}
	}

	/** 큰 상자의 나머지 반쪽 위치(dx, dz). 한 칸 상자면 null. 바닐라 ChestBlock.getFacing과 같은 방향 계산. */
	private static int[] chestPartner(String type, Object facing) {
		String t = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
		if (!t.equals("left") && !t.equals("right") || facing == null) {
			return null;
		}
		String[] order = {"north", "east", "south", "west"};
		int[][] off = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
		String f = String.valueOf(facing).toLowerCase(java.util.Locale.ROOT);
		for (int i = 0; i < 4; i++) {
			if (order[i].equals(f)) {
				int j = t.equals("left") ? (i + 1) % 4 : (i + 3) % 4;   // 왼쪽 = 시계 방향, 오른쪽 = 반시계 방향
				return off[j];
			}
		}
		return null;
	}

	// ==================== 드롭 표 ====================
	// "아이템id 개수 [행운공식]". 개수 = "N" 또는 "N-M". 행운공식 = mult(ore_drops) / bonus(uniform_bonus) / 없음.
	// 실측 전리품표 기준(26.1.2): 석탄·다이아·철·금·구리·청금석·에메랄드·석영·네더금(너깃) = ore_drops(mult),
	// 레드스톤 = uniform_bonus(bonus). 나머지는 행운 영향 없음.

	private static final Map<String, String[]> DROPS = new HashMap<>();

	static {
		String[] coal = {"coal 1 mult"};
		DROPS.put("coal_ore", coal);
		DROPS.put("deepslate_coal_ore", coal);
		String[] rawIron = {"raw_iron 1 mult"};
		DROPS.put("iron_ore", rawIron);
		DROPS.put("deepslate_iron_ore", rawIron);
		String[] rawCopper = {"raw_copper 2-5 mult"};
		DROPS.put("copper_ore", rawCopper);
		DROPS.put("deepslate_copper_ore", rawCopper);
		String[] rawGold = {"raw_gold 1 mult"};
		DROPS.put("gold_ore", rawGold);
		DROPS.put("deepslate_gold_ore", rawGold);
		DROPS.put("nether_gold_ore", new String[]{"gold_nugget 2-6 mult"});
		String[] diamond = {"diamond 1 mult"};
		DROPS.put("diamond_ore", diamond);
		DROPS.put("deepslate_diamond_ore", diamond);
		String[] emerald = {"emerald 1 mult"};
		DROPS.put("emerald_ore", emerald);
		DROPS.put("deepslate_emerald_ore", emerald);
		String[] lapis = {"lapis_lazuli 4-9 mult"};
		DROPS.put("lapis_ore", lapis);
		DROPS.put("deepslate_lapis_ore", lapis);
		String[] redstone = {"redstone 4-5 bonus"};
		DROPS.put("redstone_ore", redstone);
		DROPS.put("deepslate_redstone_ore", redstone);
		DROPS.put("nether_quartz_ore", new String[]{"quartz 1 mult"});
		DROPS.put("amethyst_cluster", new String[]{"amethyst_shard 4"});
		DROPS.put("budding_amethyst", new String[0]);
		// 흔한 지형
		DROPS.put("stone", new String[]{"cobblestone 1"});
		DROPS.put("deepslate", new String[]{"cobbled_deepslate 1"});
		String[] dirt = {"dirt 1"};
		DROPS.put("grass_block", dirt);
		DROPS.put("podzol", dirt);
		DROPS.put("mycelium", dirt);
		DROPS.put("dirt_path", dirt);
		DROPS.put("grass_path", dirt);
		DROPS.put("farmland", dirt);
		DROPS.put("clay", new String[]{"clay_ball 4"});
		DROPS.put("glowstone", new String[]{"glowstone_dust 2-4"});
		DROPS.put("sea_lantern", new String[]{"prismarine_crystals 2-3"});
		DROPS.put("snow", new String[]{"snowball 1"});
		DROPS.put("snow_block", new String[]{"snowball 4"});
		DROPS.put("ice", new String[0]);
		DROPS.put("frosted_ice", new String[0]);
		DROPS.put("bookshelf", new String[]{"book 3"});
		DROPS.put("melon", new String[]{"melon_slice 3-7"});
		DROPS.put("spawner", new String[0]);
		DROPS.put("mob_spawner", new String[0]);
		DROPS.put("wheat", new String[]{"wheat 1", "wheat_seeds 0-3"});
		DROPS.put("potatoes", new String[]{"potato 2-5"});
		DROPS.put("carrots", new String[]{"carrot 2-5"});
		DROPS.put("beetroots", new String[]{"beetroot 1", "beetroot_seeds 0-3"});
		DROPS.put("nether_wart", new String[]{"nether_wart 2-4"});
		DROPS.put("cocoa", new String[]{"cocoa_beans 1-3"});
		DROPS.put("sweet_berry_bush", new String[]{"sweet_berries 1-3"});
		// 49-101차(사용자: "가위 없으면 잔디는 씨앗 0~1개가 떠야지"): 잔디·고사리류는 맨손으로 캐면
		// 낮은 확률로 밀 씨앗이 떨어진다(가위로 캐야 블록 자체를 얻음). 표에 없으면 addSelf가 블록 자기 자신을
		// 잘못 띄우던 문제를 고침. 씨앗 개수는 확률표라 공식이 깔끔하지 않아 0~1로만 둔다(과장 X).
		String[] grassSeeds = {"wheat_seeds 0-1"};
		DROPS.put("short_grass", grassSeeds);
		DROPS.put("grass", grassSeeds);            // 1.15~1.19의 짧은 잔디 id
		DROPS.put("tall_grass", grassSeeds);
		DROPS.put("fern", grassSeeds);
		DROPS.put("large_fern", grassSeeds);
		DROPS.put("dead_bush", new String[]{"stick 0-2"});
	}

	private void fillDrops(Info info, BlockState state) {
		String id = idOf(state);
		String[] rule = id == null ? null : DROPS.get(id);
		if (rule == null && id != null
				&& (id.equals("glass") || id.equals("glass_pane") || id.endsWith("_glass") || id.endsWith("_glass_pane"))) {
			rule = new String[0];
		}
		if (rule == null && id != null && id.endsWith("_leaves")) {
			Item sapling = LunaCompat.itemById(
					"minecraft:" + id.substring(0, id.length() - "_leaves".length()) + "_sapling");
			if (sapling != null) {
				info.drops.add(new Drop(new ItemStack(sapling), "드묾"));
				return;
			}
			rule = new String[0];
		}
		if (rule == null) {
			addSelf(info);
			return;
		}
		for (String entry : rule) {
			String[] p = entry.split(" ");
			if (p.length < 2) {
				continue;
			}
			Item it = LunaCompat.itemById("minecraft:" + p[0]);
			if (it == null) {
				addSelf(info);   // 이 버전에 없는 아이템(예: 1.16.5의 원철) → 자기 자신
				continue;
			}
			int[] range = parseCount(p[1]);
			Fkind kind = Fkind.NONE;
			if (p.length >= 3) {
				kind = "mult".equals(p[2]) ? Fkind.MULT : "bonus".equals(p[2]) ? Fkind.BONUS : Fkind.NONE;
			}
			info.drops.add(new Drop(new ItemStack(it), range[0], range[1], kind));
		}
	}

	/** "1" → {1,1}, "2-5" → {2,5}. */
	private static int[] parseCount(String s) {
		int dash = s.indexOf('-');
		try {
			if (dash < 0) {
				int n = Integer.parseInt(s);
				return new int[]{n, n};
			}
			return new int[]{Integer.parseInt(s.substring(0, dash)), Integer.parseInt(s.substring(dash + 1))};
		} catch (Throwable ignored) {
			return new int[]{1, 1};
		}
	}

	private static void addSelf(Info info) {
		if (!info.icon.isEmpty() && info.drops.isEmpty()) {
			info.drops.add(new Drop(info.icon.copy(), 1, 1, Fkind.NONE));
		}
	}

	private static String idOf(BlockState state) {
		String full = LunaCompat.blockId(state);
		if (full == null) {
			return null;
		}
		int colon = full.indexOf(':');
		return colon >= 0 ? full.substring(colon + 1) : full;
	}

	// ==================== 도구 재 보기 ====================

	private static final String[] TOOL_KINDS = {"pickaxe", "shovel", "axe", "hoe", "sword"};

	/** 등급 사다리(접두 = 아이템 id 접두). */
	private static final String[] TIERS = {"wooden", "stone", "iron", "diamond", "netherite"};

	private void fillTool(Info info, BlockState state) {
		info.toolRequired = toolRequired(state);
		// 1) 종류: 가장 빠른 도구
		String kind = null;
		float best = 1.0f;
		for (String k : TOOL_KINDS) {
			ItemStack probe = probe("diamond_" + k);
			if (probe == null) {
				continue;
			}
			float speed = miningSpeed(probe, state);
			if (speed > best + 0.01f) {
				best = speed;
				kind = k;
			}
		}
		if (!info.toolRequired) {
			info.toolIcon = ItemStack.EMPTY;   // 맨손으로 캐지는 것 - 도구 아이콘 없음(체크만)
			return;
		}
		if (kind == null) {
			// 도구는 필요한데 종류를 못 잼(가위로 캐는 것 등) → 표시 없음
			info.toolIcon = ItemStack.EMPTY;
			return;
		}
		// 2) 등급: 나무→돌→철→다이아→네더라이트 중 처음으로 제대로 캐지는 것의 아이콘
		for (String tier : TIERS) {
			ItemStack probe = probe(tier + "_" + kind);
			if (probe != null && suitableFor(probe, state)) {
				info.toolIcon = probe.copy();
				return;
			}
		}
		// 다 안 되면 다이아 곡괭이 아이콘으로(대개 여기 안 온다)
		ItemStack fb = probe("diamond_" + kind);
		info.toolIcon = fb == null ? ItemStack.EMPTY : fb.copy();
	}

	private final Map<String, ItemStack> probes = new HashMap<>();

	private ItemStack probe(String id) {
		ItemStack cached = probes.get(id);
		if (cached == null) {
			Item it = LunaCompat.itemById("minecraft:" + id);
			cached = it == null ? ItemStack.EMPTY : new ItemStack(it);
			probes.put(id, cached);
		}
		return cached.isEmpty() ? null : cached;
	}

	private static float miningSpeed(ItemStack stack, BlockState state) {
		java.lang.reflect.Method m = LunaCompat.findAnyMethod(stack.getClass(), "getMiningSpeedMultiplier", BlockState.class);
		if (m == null) {
			m = LunaCompat.findAnyMethod(stack.getClass(), "getMiningSpeed", BlockState.class);
		}
		if (m == null) {
			return 1f;
		}
		try {
			Object r = m.invoke(stack, state);
			return r instanceof Float f ? f : 1f;
		} catch (Throwable ignored) {
			return 1f;
		}
	}

	private static boolean suitableFor(ItemStack stack, BlockState state) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		java.lang.reflect.Method m = LunaCompat.findAnyMethod(stack.getClass(), "isSuitableFor", BlockState.class);
		if (m == null) {
			m = LunaCompat.findAnyMethod(stack.getClass(), "isEffectiveOn", BlockState.class);
		}
		if (m == null) {
			return true;
		}
		try {
			return Boolean.TRUE.equals(m.invoke(stack, state));
		} catch (Throwable ignored) {
			return true;
		}
	}

	private static boolean toolRequired(BlockState state) {
		java.lang.reflect.Method m = LunaCompat.findAnyMethod(state.getClass(), "isToolRequired");
		if (m != null) {
			try {
				return Boolean.TRUE.equals(m.invoke(state));
			} catch (Throwable ignored) {
				return false;
			}
		}
		try {
			java.lang.reflect.Method gm = LunaCompat.findAnyMethod(state.getClass(), "getMaterial");
			Object material = gm == null ? null : gm.invoke(state);
			if (material == null) {
				return false;
			}
			java.lang.reflect.Method hand = LunaCompat.findAnyMethod(material.getClass(), "canBreakByHand");
			Object r = hand == null ? null : hand.invoke(material);
			return r instanceof Boolean b && !b;
		} catch (Throwable ignored) {
			return false;
		}
	}

	// ==================== 미리보기 ====================

	private Info previewInfo() {
		Info info = new Info();
		info.name = "다이아몬드 광석";
		info.id = "minecraft:diamond_ore";
		Item block = LunaCompat.itemById("minecraft:diamond_ore");
		info.icon = block == null ? ItemStack.EMPTY : new ItemStack(block);
		Item drop = LunaCompat.itemById("minecraft:diamond");
		if (drop != null) {
			info.drops.add(new Drop(new ItemStack(drop), 1, 1, Fkind.MULT));   // 행운 3 → 1~4
		}
		Item tool = LunaCompat.itemById("minecraft:iron_pickaxe");
		info.toolIcon = tool == null ? ItemStack.EMPTY : new ItemStack(tool);
		info.toolRequired = true;
		return info;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
