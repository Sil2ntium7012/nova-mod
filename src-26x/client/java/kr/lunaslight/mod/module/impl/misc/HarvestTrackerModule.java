package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.gui.LunaIcons;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;

import kr.lunaslight.mod.module.setting.StringSetting;
import kr.lunaslight.mod.util.HarvestLog;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-48차: 작물 계산기 - 캐는 동안 종류·개수·시간·번 돈을 실시간으로 세고, 한 판이 끝나면 기록으로 남긴다.
 *
 * <b>어떻게 "캤다"를 아는가</b>: 블록 파괴 이벤트는 서버 쪽 이벤트라 남의 서버에서는 안 온다. 그래서
 * <b>인벤토리가 늘어난 것</b>을 본다(아이템 획득 토스트와 같은 방식) - 곡괭이로 캐든 낫으로 베든
 * 서버 종류와 무관하게 동작하고, 상자에서 꺼낸 것만 빼면 된다(화면이 열려 있는 동안은 세지 않는다).
 *
 * <b>한 판(세션)</b>: 첫 수확에 열리고, [쉬는 시간](기본 2분) 동안 아무것도 안 들어오면 닫힌다.
 * 닫힐 때 [최소 기록 시간](기본 1분)보다 짧으면 버린다 - 잠깐 주운 것까지 기록에 쌓이지 않게.
 * 다시 캐면 새 판이 열린다. 시간은 <b>실제로 캔 시간</b>(첫 수확 ~ 마지막 수확)만 센다.
 *
 * 가격은 [가격표] 설정에 {@code 아이템id=가격} 쉼표 목록으로 들어간다(설정 화면에서 고르면 자동으로 채워짐).
 * 가격이 없는 아이템은 <b>돈에 안 더해지되 개수·시간은 센다</b>(무엇을 캤는지는 남는 게 맞으니까).
 */
public class HarvestTrackerModule extends Module {

	// 49-210차(사용자: "가격 등록을 설정 말고 기능에 다 넣어"): 톱니 메뉴에만 있던 [작물 계산기] 창을 이 기능의 설정 맨 위
	// 버튼으로 옮겼다. [가격 등록]은 가격표 탭, [수확 기록]은 기록 탭으로 바로 연다.
	private final kr.lunaslight.mod.module.setting.ActionSetting priceAction = register(new kr.lunaslight.mod.module.setting.ActionSetting(
			"open_prices", "가격 등록", "작물/광물 가격표에 아이템과 가격을 등록합니다. 등록한 것만 셉니다.", "등록",
			() -> openHarvestScreen(2)));
	private final kr.lunaslight.mod.module.setting.ActionSetting historyAction = register(new kr.lunaslight.mod.module.setting.ActionSetting(
			"open_history", "수확 기록", "지난 판의 기록과 총합을 봅니다.", "열기",
			() -> openHarvestScreen(0)));
	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.LEFT_CENTER, 6, -50)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));
	// 49-76차(6-12-3, 사용자: "작물/광물 별로 가격표 따로 등록"): 가격표가 여러 벌이 됐다. 설정 화면엔 안 보이고
	// (hidden) [작물 계산기] 화면의 [가격표] 탭이 다룬다. 옛 `prices` 한 벌은 처음 읽을 때 "작물" 표로 옮긴다.
	private final StringSetting prices = register(new StringSetting(
			"prices", "가격표(옛)", "", "").hidden());
	private final StringSetting tables = register(new StringSetting(
			"price_tables", "가격표 묶음", "", "").hidden());
	private final StringSetting activeTable = register(new StringSetting(
			"active_table", "가격표", "", HarvestLog.DEFAULT_TABLES[0]).hidden());
	private final IntSetting idleMinutes = register(new IntSetting(
			"idle_minutes", "휴식 시간", "이 시간 동안 아무것도 안 캐면 한 판을 닫고 기록에 남깁니다(분).", 1, 1, 10, 1));
	private final IntSetting minMinutes = register(new IntSetting(
			"min_minutes", "최소 기록 시간", "이보다 짧은 판은 기록에 남기지 않습니다(분).", 1, 0, 10, 1));
	// 49-122차(사용자: "등록하지도 않은 나무 판자가 왜 떠 · 등록한 것만 떠야지"): 가격표에 등록한 것만 센다(고정).
	// 예전 [가격표 항목만] 토글(기본 꺼짐)이 헷갈렸다 - 이제 항상 등록 항목만 세므로 토글을 없앴다.
	private final BooleanSetting showList = register(new BooleanSetting(
			"show_list", "종류 목록", "지금 캐고 있는 종류를 줄줄이 보여줍니다.", true));

	// 49-114차(사용자: "무조건 고퀄리티 고정, 설정하는 거 없애"): 테마 설정(hud_theme·HudTheme enum)을 없애고
	// 항상 고퀄(drawFancy)로 그린다. drawBasic은 안 부르지만 남겨 둔다(참고용).

	// 49-112차(사용자: "광물 표에서 작물 보이고 작물 표에서 광물 보임 → 표별로 나눠야") + 49-116차(사용자:
	// "앵무조개·블레이즈막대 왜 있어" → 몹·낚시 드롭 제거): 피커 목록을 작물/광물 둘로 나누고 순수 채굴만 남긴다.
	private static final String[] CROP_ITEMS = {
			// 49-124차(사용자: "호박씨 수박씨도 왜 있는 건데 없애"): 심어 캐도 안 나오는 씨앗 제거(밀·비트 씨앗은 수확 시 나오니 유지).
			"minecraft:wheat", "minecraft:wheat_seeds", "minecraft:carrot", "minecraft:potato", "minecraft:beetroot",
			"minecraft:beetroot_seeds", "minecraft:nether_wart", "minecraft:sugar_cane", "minecraft:bamboo",
			"minecraft:melon_slice", "minecraft:pumpkin", "minecraft:cocoa_beans", "minecraft:sweet_berries",
			"minecraft:glow_berries", "minecraft:kelp", "minecraft:cactus", "minecraft:chorus_fruit", "minecraft:apple",
			"minecraft:melon", "minecraft:carved_pumpkin",
			"minecraft:sugar", "minecraft:honeycomb", "minecraft:honey_bottle",
	};
	private static final String[] ORE_ITEMS = {
			// 49-122차(사용자: "부싯돌·점토·발광석 빼고 돌 넣어"): flint/clay_ball/glowstone_dust 제거, stone 추가.
			// 49-124차(사용자: "조약돌 추가, 석탄블록 같은 가공 광물블록/네더라이트 파편 없애"): cobblestone 추가,
			// coal_block(가공 블록)·netherite_scrap(제련물이라 캐서 안 나옴) 제거. raw_iron_block·ancient_debris는 유지.
			"minecraft:cobblestone", "minecraft:stone", "minecraft:coal", "minecraft:raw_iron", "minecraft:raw_copper", "minecraft:raw_gold",
			"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:copper_ingot", "minecraft:diamond",
			"minecraft:emerald", "minecraft:lapis_lazuli", "minecraft:redstone", "minecraft:quartz",
			"minecraft:amethyst_shard", "minecraft:ancient_debris", "minecraft:obsidian",
			"minecraft:raw_iron_block",
	};

	/** 지금 고른 표에 맞는 피커 목록. "작물" 표면 작물만, "광물" 표면 광물만, 그 밖(사용자 표)이면 둘 다. */
	private java.util.List<String> pickListFor(String table) {
		if (HarvestLog.DEFAULT_TABLES[0].equals(table)) {
			return java.util.Arrays.asList(CROP_ITEMS);
		}
		if (HarvestLog.DEFAULT_TABLES.length > 1 && HarvestLog.DEFAULT_TABLES[1].equals(table)) {
			return java.util.Arrays.asList(ORE_ITEMS);
		}
		java.util.List<String> both = new java.util.ArrayList<>(java.util.Arrays.asList(CROP_ITEMS));
		both.addAll(java.util.Arrays.asList(ORE_ITEMS));
		return both;
	}

	// 49-195차(사용자: "설정에 따로 있는 작물 계산기 가격표 삭제"): 설정 페이지의 [가격표 등록](price_list) 줄을 뺐다.
	// 가격은 [작물 계산기] 화면의 [가격표] 탭에서만 다룬다(저장소 tables/activeTable은 그대로).

	// ==================== 진행 중인 한 판 ====================

	private HarvestLog.Session session;
	private long lastGainAt;
	private Map<Item, Integer> lastCounts;
	private Object lastPlayer;
	private long graceUntil;

	// 49-124차(사용자: "버리고 먹으면 카운트 늘어 - 내가 캔 것만 세야지, 32개 캤는데 68원"): 인벤토리가 늘어난 것만
	// 보면 땅에서 주운 것·상자에서 꺼낸 것·버렸다 다시 주운 것까지 세어 두 배가 됐다. 그래서 <b>직전에 블록을 깬</b>
	// 직후에 들어온 것만 캔 걸로 친다. 클라 블록 파괴 이벤트(초당 블록과 같은 방식)가 없는 구버전에선 예전대로 전부 센다.
	private volatile long lastBreakAt;
	private volatile long lastBreakCounted;
	private long lastUseCounted;   // 49-234차: 우클릭 수확(서버 플러그인 자동 재심기 등 - 블록이 안 깨진다)   // 49-170차: 등록 블록을 깬 시각(얻은 거는 이 뒤 것만)
	private boolean breakGateReady;
	private static final long BREAK_WINDOW_MS = 2000L;

	public HarvestTrackerModule() {
		super("harvest_tracker", "작물 계산기", ModuleCategory.HUD, "캔 작물/광물의 개수/시간/번 돈");
		enableHudStyle();
		breakGateReady = registerBreakListener();
	}

	/** 클라이언트 블록 파괴 시각을 기록(리플렉션 - 클래스가 있는 fabric-api에서만). 못 걸면 false(게이트 없이 예전 방식). */
	private boolean registerBreakListener() {
		try {
			Class<?> eventsClass = Class.forName(
					"net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents");
			Object event = eventsClass.getField("AFTER").get(null);
			Class<?> afterItf = Class.forName(
					"net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents$After");
			java.lang.reflect.InvocationHandler handler = (proxy, method, args) -> {
				String n = method.getName();
				if ("afterBlockBreak".equals(n)) {
					// 49-170차: (world, player, pos, state) - 어떤 블록을 깼는지 보고 등록된 작물/광물이면 "캔 거" 1
					onBlockBroken(args != null && args.length >= 4 ? args[3] : null);
					return null;
				}
				return switch (n) {
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					case "toString" -> "LunaHarvestBreak";
					default -> null;
				};
			};
			Object listener = java.lang.reflect.Proxy.newProxyInstance(
					afterItf.getClassLoader(), new Class<?>[]{afterItf}, handler);
			event.getClass().getMethod("register", Object.class).invoke(event, listener);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 49-170차(사용자: "캔 거 따로 얻은 거 따로 둘 다 계산, 내가 버린 거나 캐서 나온 게 아니면 인식 X"):
	 * 블록을 깬 순간 그 블록이 지금 가격표의 어떤 아이템에 해당하면 <b>캔 거</b>(session.harvests)를 하나 올리고,
	 * 그 뒤 BREAK_WINDOW_MS 안에 인벤토리에 늘어난 등록 아이템만 <b>얻은 거</b>(session.items)로 센다.
	 * 블록 → 아이템은 block.asItem()(당근/감자/사탕무/밀/코코아/베리/켈프...)에 예외 몇 개(수박 → 조각, 후렴화 → 열매,
	 * 광석 → 원석/보석)를 더한다. 상자에서 꺼내거나 남이 준 것, 깬 직후가 아닌 획득은 세지 않는다.
	 */
	private void onBlockBroken(Object state) {
		long now = System.currentTimeMillis();
		lastBreakAt = now;
		if (!isEnabled() || state == null) {
			return;
		}
		try {
			if (!countableBlock(state)) {
				return;
			}
			if (session == null) {
				session = new HarvestLog.Session();
				session.startedAt = now;
			}
			session.harvests++;
			lastBreakCounted = now;
			lastGainAt = now;
			session.endedAt = now;
			session.activeMs = now - session.startedAt;
		} catch (Throwable t) {
			LunaCompat.warnOnce("harvest:break", t);
		}
	}

	/**
	 * 49-234차(사용자: "작물 계산기 제대로 작동을 아예 안 해"): 이 블록(상태)을 깨거나 우클릭으로 거두면 지금 셀 아이템이 나오는지.
	 * 예전엔 밀/비트가 안 셌다 - 밀 작물 블록의 asItem은 밀이 아니라 <b>밀 씨앗</b>(씨앗이 작물을 심는 아이템)이라 표의 "밀"과
	 * 안 맞았다. 씨앗 → 작물 열매도 이어 준다.
	 */
	private boolean countableBlock(Object state) {
		try {
			Object block = state == null ? null : LunaCompat.callNoArg(state, "getBlock");
			Object item = block == null ? null : LunaCompat.callNoArg(block, "asItem");
			Identifier id = item instanceof Item it ? LunaCompat.getItemId(it) : null;
			if (id == null) {
				return false;
			}
			String bid = id.toString();
			java.util.Collection<String> keys = countableIds();
			if (keys.contains(bid)) {
				return true;
			}
			for (String alt : dropsOf(bid)) {
				if (keys.contains(alt)) {
					return true;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("harvest:countable", t);
		}
		return false;
	}

	/**
	 * 지금 세는 아이템 id들. 49-234차: 가격표가 비어 있으면(가격을 아직 안 넣었으면) 아무것도 안 세던 것을 고쳐, 그 표의 기본 목록
	 * (작물 표 = 작물, 광물 표 = 광물)을 가격 0으로 센다. 가격을 하나라도 넣으면 예전처럼 넣은 것만.
	 */
	private java.util.Collection<String> countableIds() {
		Map<String, Double> table = pricesNow();
		if (!table.isEmpty()) {
			return table.keySet();
		}
		return pickListFor(activeTable());
	}

	/** 블록 아이템 id → 깨면 나오는 아이템 id들(asItem과 다른 것만). */
	private static java.util.List<String> dropsOf(String blockItemId) {
		return switch (blockItemId) {
			case "minecraft:melon" -> java.util.List.of("minecraft:melon_slice");
			case "minecraft:chorus_flower", "minecraft:chorus_plant" -> java.util.List.of("minecraft:chorus_fruit");
			case "minecraft:kelp_plant" -> java.util.List.of("minecraft:kelp");
			case "minecraft:cave_vines", "minecraft:cave_vines_plant" -> java.util.List.of("minecraft:glow_berries");
			case "minecraft:carved_pumpkin", "minecraft:pumpkin" -> java.util.List.of("minecraft:pumpkin", "minecraft:carved_pumpkin");
			case "minecraft:beetroots" -> java.util.List.of("minecraft:beetroot", "minecraft:beetroot_seeds");
			case "minecraft:wheat" -> java.util.List.of("minecraft:wheat", "minecraft:wheat_seeds");
			case "minecraft:wheat_seeds" -> java.util.List.of("minecraft:wheat", "minecraft:wheat_seeds");   // 밀 작물 블록 asItem = 씨앗
			case "minecraft:beetroot_seeds" -> java.util.List.of("minecraft:beetroot", "minecraft:beetroot_seeds");
			case "minecraft:oak_leaves", "minecraft:dark_oak_leaves" -> java.util.List.of("minecraft:apple");
			case "minecraft:coal_ore", "minecraft:deepslate_coal_ore" -> java.util.List.of("minecraft:coal");
			case "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> java.util.List.of("minecraft:raw_iron", "minecraft:iron_ingot");
			case "minecraft:copper_ore", "minecraft:deepslate_copper_ore" -> java.util.List.of("minecraft:raw_copper", "minecraft:copper_ingot");
			case "minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore" -> java.util.List.of("minecraft:raw_gold", "minecraft:gold_ingot", "minecraft:gold_nugget");
			case "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore" -> java.util.List.of("minecraft:diamond");
			case "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore" -> java.util.List.of("minecraft:emerald");
			case "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore" -> java.util.List.of("minecraft:lapis_lazuli");
			case "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore" -> java.util.List.of("minecraft:redstone");
			case "minecraft:nether_quartz_ore" -> java.util.List.of("minecraft:quartz");
			case "minecraft:amethyst_cluster" -> java.util.List.of("minecraft:amethyst_shard");
			case "minecraft:stone" -> java.util.List.of("minecraft:cobblestone", "minecraft:stone");
			case "minecraft:raw_iron_block" -> java.util.List.of("minecraft:raw_iron_block");
			default -> java.util.List.of();
		};
	}

	/** 모든 표(이름 → id → 개당 가격). 옛 한 벌짜리 설정이 남아 있으면 "작물" 표로 옮기고 비운다. */
	public java.util.LinkedHashMap<String, java.util.Map<String, Double>> priceTables() {
		java.util.LinkedHashMap<String, java.util.Map<String, Double>> t = HarvestLog.parseTables(tables.get());
		String legacy = prices.get();
		if (legacy != null && !legacy.isBlank()) {
			java.util.Map<String, Double> old = HarvestLog.parsePrices(legacy);
			t.computeIfAbsent(HarvestLog.DEFAULT_TABLES[0], k -> new java.util.LinkedHashMap<>()).putAll(old);
			prices.setValue("");
			tables.setValue(HarvestLog.writeTables(t));
		}
		return t;
	}

	public void savePriceTables(java.util.Map<String, java.util.Map<String, Double>> t) {
		tables.setValue(HarvestLog.writeTables(t));
	}

	public String activeTable() {
		String a = activeTable.get();
		java.util.Map<String, ?> t = priceTables();
		if (a == null || !t.containsKey(a)) {
			a = t.keySet().iterator().next();
			activeTable.setValue(a);
		}
		return a;
	}

	public void setActiveTable(String name) {
		activeTable.setValue(name);
	}

	private String cachedRaw, cachedActive;
	private java.util.Map<String, Double> cachedNow = new java.util.LinkedHashMap<>();

	/** 지금 쓰는 표(돈 계산용). 틱마다 불리므로 설정 문자열이 바뀌었을 때만 다시 푼다. */
	public java.util.Map<String, Double> pricesNow() {
		String raw = tables.get();
		String act = activeTable.get();
		boolean legacyPending = prices.get() != null && !prices.get().isBlank();
		if (!legacyPending && java.util.Objects.equals(raw, cachedRaw) && java.util.Objects.equals(act, cachedActive)) {
			return cachedNow;
		}
		cachedNow = priceTables().getOrDefault(activeTable(), new java.util.LinkedHashMap<>());
		cachedRaw = tables.get();
		cachedActive = activeTable.get();
		return cachedNow;
	}

	/** 지금 진행 중인 판(없으면 null) - 화면에서 "지금 이 판" 표시에 쓴다. */
	public HarvestLog.Session current() {
		return session;
	}

	// ==================== 세기 ====================

	private Map<Item, Integer> countInventory() {
		Map<Item, Integer> counts = new HashMap<>();
		if (client.player == null) {
			return counts;
		}
		// 49-48차: 인벤토리 접근은 버전마다 갈려 LunaCompat 경유(아이템 획득 토스트와 같은 방식).
		Object inv = LunaCompat.getPlayerInventory(client.player);
		if (inv == null) {
			return counts;
		}
		try {
			int size = LunaCompat.invSize(inv);
			for (int i = 0; i < size; i++) {
				ItemStack s = LunaCompat.invGetStack(inv, i);
				if (s != null && !s.isEmpty()) {
					counts.merge(s.getItem(), s.getCount(), Integer::sum);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("harvest:count", t);
		}
		return counts;
	}

	// 49-122차: 드물게 나오는 아이템(잎에서 가끔 떨어지는 사과 등). 이번 판에 끼어 있으면 세션 유지 시간을 늘린다.
	private static final java.util.Set<String> SLOW_ITEMS = java.util.Set.of("minecraft:apple");
	private static final long SLOW_IDLE_MS = 6 * 60_000L;   // 6분

	/** 지금 판에 드물게 나오는(사과 등) 등록 아이템이 들어 있는지. */
	private boolean sessionHasSlowItem() {
		if (session == null) {
			return false;
		}
		for (String id : session.items.keySet()) {
			if (SLOW_ITEMS.contains(id)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();
		if (client == null || client.player == null || client.level == null) {
			closeIfOpen(now);
			lastCounts = null;
			lastPlayer = null;
			return;
		}
		if (client.player != lastPlayer) {
			lastPlayer = client.player;
			lastCounts = null;
			graceUntil = now + 2000;   // 접속 직후 인벤토리가 통째로 "늘어난" 것으로 보이는 걸 무시
		}
		// 쉬는 시간이 지나면 판을 닫는다(캐다 말고 쉬는 중에도 돈다)
		// 49-122차(사용자: "사과는 드물게 나오니 연계(세션 유지) 시간을 알아서 늘려줘"): 이번 판에 드물게 나오는
		// 아이템(사과 등)이 끼어 있으면 유지 시간을 넉넉히(최소 SLOW_IDLE) 늘려 잠깐 안 나와도 판이 안 끊기게 한다.
		long idleMs = idleMinutes.get() * 60_000L;
		if (session != null && sessionHasSlowItem()) {
			idleMs = Math.max(idleMs, SLOW_IDLE_MS);
		}
		if (session != null && now - lastGainAt > idleMs) {
			closeIfOpen(now);
		}
		Map<Item, Integer> counts = countInventory();
		// 화면(상자·인벤토리)이 열려 있는 동안은 세지 않는다 - 상자에서 꺼낸 걸 "캤다"로 치지 않으려고.
		// 49-124차: 방금 블록을 깬 직후에 늘어난 것만 캔 걸로 친다(버리기·먹기·주운 것 제외). 게이트가 없는 구버전은 예전대로.
		// 49-234차: 우클릭으로 거두는 서버(블록이 안 깨지고 열매만 들어옴)도 센다 - 셀 작물을 보며 사용 키를 누르는 동안 문을 연다.
		if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null && client.options.keyUse.isDown()) {
			net.minecraft.core.BlockPos p = LunaCompat.targetedBlock(client);
			if (p != null && countableBlock(client.level.getBlockState(p))) {
				lastUseCounted = now;
			}
		}
		boolean minedRecently = !breakGateReady || (now - lastBreakCounted <= BREAK_WINDOW_MS)
				|| (now - lastUseCounted <= BREAK_WINDOW_MS);
		if (lastCounts != null && now >= graceUntil && kr.lunaslight.mod.util.LunaCompat.screenOf(client) == null && minedRecently) {
			Map<String, Integer> gained = new LinkedHashMap<>();
			for (Map.Entry<Item, Integer> e : counts.entrySet()) {
				int before = lastCounts.getOrDefault(e.getKey(), 0);
				int delta = e.getValue() - before;
				if (delta <= 0) {
					continue;
				}
				Identifier id = LunaCompat.getItemId(e.getKey());
				if (id != null) {
					gained.put(id.toString(), delta);
				}
			}
			if (!gained.isEmpty()) {
				record(gained, now);
			}
		}
		lastCounts = counts;
	}

	private void record(Map<String, Integer> gained, long now) {
		Map<String, Double> table = pricesNow();
		boolean any = false;
		for (Map.Entry<String, Integer> e : gained.entrySet()) {
			Double price = table.get(e.getKey());
			// 49-122차: 가격표에 등록한 것만 센다(등록 안 한 건 캔 걸로 안 침 - 판자·주운 잡템이 기록에 안 뜨게).
			// 49-234차: 표가 비어 있으면 그 표 기본 목록을 가격 0으로 센다.
			if (price == null) {
				if (!table.isEmpty() || !pickListFor(activeTable()).contains(e.getKey())) {
					continue;
				}
				price = 0.0;
			}
			if (session == null) {
				session = new HarvestLog.Session();
				session.startedAt = now;
			}
			session.items.merge(e.getKey(), e.getValue(), Integer::sum);
			if (price != null) {
				session.earned += price * e.getValue();
			}
			any = true;
		}
		if (any) {
			// 49-108차: 이 감지 한 번 = "한 번 캤다". 49-170차: 블록 깨기 이벤트가 있는 버전은 거기서 세므로(정확한 블록 수)
			// 이벤트가 없는 구버전에서만 여기서 올린다.
			if (!breakGateReady || (lastUseCounted > lastBreakCounted && now - lastUseCounted <= BREAK_WINDOW_MS)) {
				session.harvests++;   // 49-234차: 우클릭 수확은 깨기 이벤트가 없어 여기서 센다
			}
			lastGainAt = now;
			session.endedAt = now;
			session.activeMs = now - session.startedAt;
		}
	}

	/** 판을 닫아 기록에 남긴다(짧으면 버림). */
	private void closeIfOpen(long now) {
		if (session == null) {
			return;
		}
		HarvestLog.Session s = session;
		session = null;
		if (s.activeMs >= minMinutes.get() * 60_000L && s.totalCount() > 0) {
			HarvestLog.add(s);
		}
	}

	@Override
	protected void onDisable() {
		closeIfOpen(System.currentTimeMillis());
	}

	private void openHarvestScreen(int tab) {
		LunaCompat.setScreen(new kr.lunaslight.mod.gui.LunaHarvestScreen(LunaCompat.screenOf(client), tab));
	}

	// ==================== 표시 ====================

	private String nameOf(String id) {
		Item item = LunaCompat.itemById(id);
		if (item == null) {
			return id.startsWith("minecraft:") ? id.substring(10) : id;
		}
		try {
			return new ItemStack(item).getHoverName().getString();
		} catch (Throwable t) {
			return id;
		}
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		double money;
		long active;
		int harvests;
		List<Map.Entry<String, Integer>> items = new ArrayList<>();
		if (isPreview()) {
			money = 12450;
			active = 1_100_000L;
			harvests = 384;
			items.add(Map.entry("minecraft:iron_ore", 64));
			items.add(Map.entry("minecraft:coal", 128));
			items.add(Map.entry("minecraft:wheat", 192));
		} else {
			if (session == null || session.items.isEmpty()) {
				return;
			}
			money = session.earned;
			active = Math.max(session.activeMs, 0);
			harvests = session.harvests;
			for (Map.Entry<String, Integer> e : session.items.entrySet()) {
				items.add(e);
				if (items.size() >= 8) {
					break;
				}
			}
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		// 49-114차: 무조건 고퀄 고정.
		drawFancy(context, money, active, harvests, items, sw, sh);
	}

	/** 기본 테마: 글자 줄. 49-108차(기능마다 나눠서): 돈 / 시간·캔횟수 / 종류를 각 줄로. */
	private void drawBasic(GuiGraphicsExtractor ctx, double money, long active, int harvests,
			List<Map.Entry<String, Integer>> items, int sw, int sh) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + HarvestLog.money(money) + "원");
		lines.add("§7" + HarvestLog.duration(active) + "  §8|  §7채굴 " + harvests);
		if (showList.get()) {
			StringBuilder row = new StringBuilder();
			int n = 0;
			for (Map.Entry<String, Integer> e : items) {
				if (n > 0 && n % 2 == 0) {
					lines.add(row.toString());
					row.setLength(0);
				} else if (row.length() > 0) {
					row.append("§8  |  ");
				}
				row.append("§7").append(nameOf(e.getKey())).append(" §f×").append(e.getValue());
				n++;
				if (lines.size() >= 6) {
					break;
				}
			}
			if (row.length() > 0) {
				lines.add(row.toString());
			}
		}
		int w = hudLinesWidth(lines);
		int h = hudLinesHeight(lines);
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		drawHudLines(ctx, lines, x, y, textColor.getArgb());
	}

	/**
	 * 49-170차(사용자: "작물 계산기 HUD도 더 깔끔하게, 키스트로크 3D 잘 만들었잖아 예쁘게, 회색 말고 꾸미던가"):
	 * 키캡처럼 두께(아래 옆면 3px)가 있는 카드. 위에 강조색 띠(그라데이션), 돈을 크게, 오른쪽에 시간,
	 * 그 아래 [캔 거 n] [얻은 거 n] 두 개의 작은 키캡(강조색 윗면 + 어두운 옆면), 구분선 아래 종류마다
	 * [아이콘 이름 … ×개수(강조색)] 줄. 배경/윤곽선/글자 색은 [스타일] 설정을 따르고, 강조색은 테마색.
	 */
	private void drawFancy(GuiGraphicsExtractor ctx, double money, long active, int harvests,
			List<Map.Entry<String, Integer>> items, int sw, int sh) {
		var tr = client.font;
		int fontH = tr.lineHeight;
		int pad = 9;
		int depth = 3;
		float mscale = 1.5f;
		String moneyStr = HarvestLog.money(money) + "원";
		int moneyW = Math.round(LunaCompat.getTextWidth(tr, moneyStr) * mscale);
		int moneyH = Math.round(fontH * mscale);
		String timeStr = HarvestLog.duration(active);
		int timeW = LunaCompat.getTextWidth(tr, timeStr);

		int gained = 0;
		for (Map.Entry<String, Integer> e : items) {
			gained += e.getValue();
		}
		String chipA = "채굴 " + harvests;
		String chipB = "획득 " + gained;
		int chipH = fontH + 6;
		int chipAW = LunaCompat.getTextWidth(tr, chipA) + 12;
		int chipBW = LunaCompat.getTextWidth(tr, chipB) + 12;
		int chipsW = chipAW + 6 + chipBW;

		boolean list = showList.get() && !items.isEmpty();
		int iconSz = 16;
		int rowH = 18;
		int rowsW = 0;
		List<ItemStack> icons = new ArrayList<>();
		List<String> names = new ArrayList<>();
		List<String> counts = new ArrayList<>();
		if (list) {
			for (Map.Entry<String, Integer> e : items) {
				String nm = nameOf(e.getKey());
				String ct = "×" + e.getValue();
				names.add(nm);
				counts.add(ct);
				Item it = LunaCompat.itemById(e.getKey());
				icons.add(it == null ? ItemStack.EMPTY : new ItemStack(it));
				rowsW = Math.max(rowsW, iconSz + 6 + LunaCompat.getTextWidth(tr, nm) + 18 + LunaCompat.getTextWidth(tr, ct) + 10);
			}
		}

		int contentW = Math.max(moneyW + 14 + timeW, Math.max(chipsW, rowsW));
		int w = pad + contentW + pad;
		int bandH = 4;
		int headH = bandH + 7 + moneyH + 6 + chipH + 7;
		int listH = list ? (5 + items.size() * rowH + 3) : 0;
		int h = headH + listH + depth;

		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);

		int accent = LunaDraw.ACCENT | 0xFF000000;
		int accentRGB = accent & 0x00FFFFFF;
		int text = textColor.getArgb();
		int face = hudBgEnabled() ? hudBgColorArgb() : 0xE6151A21;
		if (((face >>> 24) & 0xFF) < 0x60) {
			face = (face & 0x00FFFFFF) | 0x60000000;   // 너무 투명하면 두께가 안 보인다
		}
		int side = LunaDraw.lerpColor(face | 0xFF000000, 0xFF000000, 0.55f);
		int faceH = h - depth;
		// 옆면(두께) + 윗면
		LunaDraw.roundRect(ctx, x, y + depth, w, faceH, 6, side);
		LunaDraw.roundRect(ctx, x, y, w, faceH, 6, face);
		if (hudBgOutlineEnabled()) {
			LunaDraw.roundRectBorderedFlat(ctx, x, y, w, faceH, 6, 0x00000000, hudBgOutlineColorArgb());
		}
		// 위 강조 띠 + 그 아래로 번지는 빛
		LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, bandH, 2, accent);
		ctx.fillGradient(x + 2, y + 1 + bandH, x + w - 2, y + 1 + bandH + 18, (0x38 << 24) | accentRGB, 0x00000000);
		ctx.fill(x + 6, y + 1, x + w - 6, y + 2, 0x50FFFFFF);   // 띠 위 하이라이트

		// 돈(크게) + 오른쪽 시간
		int my = y + bandH + 7;
		drawScaledText(ctx, moneyStr, x + pad, my, mscale, text);
		LunaDraw.text(ctx, tr, timeStr, x + w - pad - timeW, LunaDraw.textY(my, moneyH), LunaDraw.TEXT_SUB);

		// 키캡 두 개: 캔 거 / 얻은 거
		int cy = my + moneyH + 6;
		drawChip(ctx, x + pad, cy, chipAW, chipH, chipA, accent);
		drawChip(ctx, x + pad + chipAW + 6, cy, chipBW, chipH, chipB, LunaDraw.lerpColor(accent, 0xFFFFFFFF, 0.35f));

		if (list) {
			int dy = y + headH;
			ctx.fill(x + pad, dy, x + w - pad, dy + 1, 0x22FFFFFF);
			int ry = dy + 5;
			for (int i = 0; i < items.size(); i++) {
				if (!icons.get(i).isEmpty()) {
					ctx.item(icons.get(i), x + pad, ry + (rowH - 16) / 2);
				}
				LunaDraw.text(ctx, tr, names.get(i), x + pad + iconSz + 6, LunaDraw.textY(ry, rowH), text);
				String ct = counts.get(i);
				int cw = LunaCompat.getTextWidth(tr, ct) + 8;
				LunaDraw.pill(ctx, x + w - pad - cw, ry + (rowH - (fontH + 4)) / 2, cw, fontH + 4, (0x30 << 24) | accentRGB);
				LunaDraw.text(ctx, tr, ct, x + w - pad - cw + 4, LunaDraw.textY(ry, rowH), accent);
				ry += rowH;
			}
		}
	}

	/** 작은 키캡: 윗면(색) + 아래 옆면 2px(어둡게) + 위 하이라이트, 글자는 어두운 색. */
	private void drawChip(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int color) {
		int depth = 2;
		int side = LunaDraw.lerpColor(color, 0xFF000000, 0.5f);
		LunaDraw.roundRect(ctx, x, y + depth, w, h - depth, 4, side);
		LunaDraw.roundRect(ctx, x, y, w, h - depth, 4, color);
		ctx.fill(x + 3, y + 1, x + w - 3, y + 2, 0x60FFFFFF);
		int tc = kr.lunaslight.mod.util.LunaTheme.luminance(color) > 0.5f ? 0xFF14181C : 0xFFF4F6F8;
		LunaDraw.text(ctx, client.font, label, x + 6, LunaDraw.textY(y, h - depth), tc);
	}

	/** 글자를 s배로 (x,y) 좌상단에 그림(고퀄 테마의 큰 돈 표시용). */
	private void drawScaledText(GuiGraphicsExtractor ctx, String text, float x, float y, float s, int color) {
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, x, y);
		LunaCompat.guiScale(ctx, s, s);
		LunaCompat.drawHudText(ctx, client.font, text, 0, 0, color);
		LunaCompat.guiPop(ctx);
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
