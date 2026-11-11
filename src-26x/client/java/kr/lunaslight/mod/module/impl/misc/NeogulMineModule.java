package kr.lunaslight.mod.module.impl.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.impl.inventory.ItemPickupToastModule;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.NeogulDungeonHud;
import kr.lunaslight.mod.util.NeogulMineFeed;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-608차(사용자 사진: 광산에서 캐면 인벤토리 대신 광물 창고로 바로 들어가서 화면 가운데 "돌 1개 +"만 뜨고 아이템 획득이 안 뜸, 광물 창고 사진 -
 * "광물 창고 개수를 토대로 가진 개수 뜨고, 켜면 광창을 열라고 화면에 띄우고, 빼면 뺀 것도 기억, 너굴 기능, 켜면 가운데 알림은 안 뜨게,
 * 오팔 아쿠아마린 루비 페리도트 사파이어는 획득을 강조하고 서브타이틀 쪽에 몇 개 나왔다고" / "대신 아이템 획득이 켜져 있어야 해"):
 * <b>광물 획득</b>.
 * <ul>
 *   <li>서버 광산 알림 줄에서 새로 얻은 것({@link NeogulMineFeed})을 아이템 획득 줄로 띄운다. 보유 개수 = 광물 창고 개수 + 그 뒤로 얻은 것.</li>
 *   <li>광물 창고(/광물창고) 화면이 열려 있는 동안 칸 설명의 "보관 수량 : N개"를 계속 읽어 기억한다(꺼낸 것도 그대로 반영). 섬 광물창고는 따로라 안 읽는다.</li>
 *   <li>한 번도 안 열었으면 화면 위쪽에 "광물 창고를 한 번 열어 주세요".</li>
 *   <li>켜져 있고 아이템 획득도 켜져 있으면 서버 알림(화면 가운데 오른쪽)은 숨긴다(NeogulDungeonHud.hideMine).</li>
 *   <li>보석 5가지(오팔, 아쿠아마린, 루비, 페리도트, 사파이어)는 획득 줄을 금색으로, 화면 가운데 아래(서브타이틀 자리)에 "루비 수정 2개 나왔어요!".</li>
 * </ul>
 * 아이템 획득이 꺼져 있으면 아무것도 안 바꾼다(서버 알림 그대로).
 */
public class NeogulMineModule extends Module {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern STORED = Pattern.compile("보관\\s*수량\\s*[:：]\\s*([\\d,]+)\\s*개(?:\\s*/\\s*([\\d,]+)\\s*개)?");

	/** 광산 알림 그림 이름 → 보통 이름(광물 창고를 안 열었을 때). */
	private static final Map<String, String> KO = new HashMap<>();
	/** 너굴 광물(ng118~123) → 광물 창고 이름에서 찾을 낱말. */
	private static final Map<String, String> NG_WORD = new HashMap<>();
	/** 그림이 없을 때 쓸 바닐라 아이템. */
	private static final Map<String, Item> NG_ICON = new HashMap<>();
	private static final Map<String, String> NG_KEY = Map.of("ng118", "cook_coal", "ng119", "opal", "ng120", "aquamarine", "ng121", "ruby",
			"ng122", "sapphire", "ng123", "peridot");
	private static final Set<String> GEMS = Set.of("ng119", "ng120", "ng121", "ng122", "ng123");
	private static final Map<String, Integer> GEM_COLOR = Map.of("ng119", 0xFFEDE7F6, "ng120", 0xFF7FE3F0, "ng121", 0xFFFF6B86,
			"ng122", 0xFF7D9BFF, "ng123", 0xFF7EE08A);

	static {
		String[][] ko = {{"stone", "돌"}, {"cobblestone", "조약돌"}, {"cobbled_deepslate", "심층암 조약돌"}, {"deepslate", "심층암"},
				{"sandstone", "사암"}, {"coal", "석탄"}, {"coal_ore", "석탄 광석"}, {"copper_ingot", "구리 주괴"}, {"copper_ore", "구리 광석"},
				{"raw_copper", "구리 원석"}, {"iron_ingot", "철 주괴"}, {"iron_ore", "철 광석"}, {"raw_iron", "철 원석"}, {"gold_ingot", "금 주괴"},
				{"gold_ore", "금 광석"}, {"raw_gold", "금 원석"}, {"diamond", "다이아몬드"}, {"diamond_ore", "다이아몬드 광석"},
				{"emerald", "에메랄드"}, {"emerald_ore", "에메랄드 광석"}, {"lapis_lazuli", "청금석"}, {"lapis_ore", "청금석 광석"},
				{"ng118", "요리용 석탄"}, {"ng119", "오팔 조각"}, {"ng120", "아쿠아마린"}, {"ng121", "루비 수정"}, {"ng122", "사파이어"},
				{"ng123", "페리도트"}};
		for (String[] k : ko) {
			KO.put(k[0], k[1]);
		}
		NG_WORD.put("ng118", "요리용");
		NG_WORD.put("ng119", "오팔");
		NG_WORD.put("ng120", "아쿠아마린");
		NG_WORD.put("ng121", "루비");
		NG_WORD.put("ng122", "사파이어");
		NG_WORD.put("ng123", "페리도트");
		NG_ICON.put("ng118", Items.CHARCOAL);
		NG_ICON.put("ng119", Items.QUARTZ);
		NG_ICON.put("ng120", Items.PRISMARINE_CRYSTALS);
		NG_ICON.put("ng121", Items.REDSTONE);
		NG_ICON.put("ng122", Items.LAPIS_LAZULI);
		NG_ICON.put("ng123", Items.EMERALD);
	}

	private final Map<String, Integer> counts = new LinkedHashMap<>();
	/** 49-612차: 창고 한도(보관 수량 N개 / 한도), 꽉 차서 인벤토리로 가는 중인 것. */
	private final Map<String, Integer> maxes = new HashMap<>();
	private final java.util.Set<String> full = new java.util.HashSet<>();
	private final Map<String, JsonElement> iconRaw = new LinkedHashMap<>();
	private final Map<String, ItemStack> icons = new HashMap<>();
	/** 49-635차: 창고 화면에서 읽은 이름(칸 이름) - 그림 읽기와 따로 저장(그림을 못 읽어도 창고 이름인 건 남게). */
	private final java.util.Set<String> store = new java.util.LinkedHashSet<>();
	private boolean opened, loaded, dirty;
	private long dirtyAt;
	private Object lastConn;
	private int readTick;
	// 49-629차(사용자: "아이템 모두 꺼내기나 따로 꺼내기 했을 때 광창 미리보기가 안 바뀜"): 창고에서 꺼낼 때.
	// 꺼내기 창(한 광물만 보이는 창)도 읽고, 창고를 본 뒤 상자 창이 이어지는 동안(+닫고 1.5초) 인벤토리에 그 광물이 늘면 창고에서 뺀다.
	private long withdrawUntil;
	private Map<String, Integer> invLast;
	/** 창 설명에서 마지막으로 읽은 값, 그 값 이후 꺼낸 개수(서버가 설명을 안 고쳐 줘도 4틱마다 옛 값으로 되돌아가지 않게). */
	private final Map<String, Integer> loreVal = new HashMap<>();
	private final Map<String, Integer> pend = new HashMap<>();

	/** 설명에 적힌 값 v → 실제로 쓸 개수(같은 값이 계속 보이면 그 뒤로 꺼낸 만큼 뺀다, 값이 바뀌면 서버 값 그대로). */
	private int applyLore(String name, int v) {
		Integer lv = loreVal.put(name, v);
		Integer pd = pend.get(name);
		if (lv != null && lv == v && pd != null) {
			return Math.max(0, v - pd);
		}
		pend.remove(name);
		return v;
	}

	private record Gem(String name, int color, int[] n, long[] at) {
	}

	private final List<Gem> gems = new ArrayList<>();

	public NeogulMineModule() {
		super("neogul_mine", "광물 획득", ModuleCategory.SERVER,
				"광산에서 캔 광물을 아이템 획득에 광물 창고 개수와 함께 표시 | 보석은 강조 (아이템 획득이 켜져 있어야 합니다)");
		serverGroup("너굴마을");
		newFeature("49-608");
		instance = this;
		NeogulMineFeed.listener = this::onGain;
		NeogulDungeonHud.hideMine = () -> working();
		ItemPickupToastModule.externalOverflow = (name, n) -> {
			// 창고가 꽉 차 인벤토리로 들어온 것 - 창고 개수에 더했던 걸 되돌리고, 창고를 다시 볼 때까지 그 광물은 창고 줄을 안 띄운다
			if (name == null) {
				return;
			}
			if (opened && counts.containsKey(name)) {
				counts.put(name, Math.max(0, counts.get(name) - n));
				markDirty();
			}
			long[] rg0 = recent.remove(name);
			if (rg0 != null) {
				kr.lunaslight.mod.util.LunaStats.addOre(statName(name), -Math.min(n, rg0[0]));   // 창고가 아니라 인벤토리로 간 것
			}
			full.add(name);
		};
	}

	/** 켜져 있고, 아이템 획득도 켜져 있고, 너굴마을. */
	private boolean working() {
		return isEnabled() && ItemPickupToastModule.on() && client.player != null && NeogulData.onNeogul(client);
	}

	// ==================== 저장 ====================

	private Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-mine-storage.json");
	}

	private void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path f = file();
			if (!Files.exists(f)) {
				return;
			}
			JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			opened = o.has("opened") && o.get("opened").getAsBoolean();
			if (o.has("counts")) {
				for (var e : o.getAsJsonObject("counts").entrySet()) {
					counts.put(e.getKey(), e.getValue().getAsInt());
				}
			}
			if (o.has("icons")) {
				for (var e : o.getAsJsonObject("icons").entrySet()) {
					iconRaw.put(e.getKey(), e.getValue());
				}
			}
			if (o.has("store") && o.get("store").isJsonArray()) {   // 49-635차
				for (var e : o.getAsJsonArray("store")) {
					store.add(e.getAsString());
				}
			} else {
				store.addAll(iconRaw.keySet());
			}
			if (dedupe()) {   // 49-631차
				markDirty();
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulMine:load", t);
		}
	}

	private com.mojang.serialization.DynamicOps<JsonElement> ops() {
		return client.level == null ? null : client.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
	}

	private void save() {
		dirty = false;
		try {
			JsonObject o = new JsonObject();
			o.addProperty("opened", opened);
			JsonObject c = new JsonObject();
			for (var e : counts.entrySet()) {
				c.addProperty(e.getKey(), e.getValue());
			}
			o.add("counts", c);
			var ops = ops();
			JsonObject ic = new JsonObject();
			for (var e : iconRaw.entrySet()) {
				ic.add(e.getKey(), e.getValue());
			}
			if (ops != null) {
				for (var e : icons.entrySet()) {
					try {
						ItemStack.CODEC.encodeStart(ops, e.getValue()).result().ifPresent(j -> ic.add(e.getKey(), j));
					} catch (Throwable ignored) {
					}
				}
			}
			o.add("icons", ic);
			com.google.gson.JsonArray sa = new com.google.gson.JsonArray();
			for (String n : store) {
				sa.add(n);
			}
			o.add("store", sa);   // 49-635차
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulMine:save", t);
		}
	}

	private void markDirty() {
		if (!dirty) {
			dirtyAt = System.currentTimeMillis();
		}
		dirty = true;
	}

	private ItemStack icon(String name) {
		ItemStack st = icons.get(name);
		if (st != null) {
			return st;
		}
		JsonElement raw = iconRaw.get(name);
		var ops = ops();
		if (raw != null && ops != null) {
			try {
				st = ItemStack.CODEC.parse(ops, raw).result().orElse(null);
			} catch (Throwable ignored) {
				st = null;
			}
			iconRaw.remove(name);
			if (st != null) {
				icons.put(name, st);
			}
		}
		return st;
	}

	// ==================== 49-613차: 광물 창고 미리보기(NeogulMinePeekModule)가 읽는 것 ====================

	private static NeogulMineModule instance;

	/** 49-614차: 최근 창고에 들어온 양(이름 → {개수, 마지막 시각}) - 미리보기의 "+n". */
	private final Map<String, long[]> recent = new HashMap<>();

	/** 이름의 최근 "+n"(2.5초 안, 없으면 null): {개수, 마지막 시각}. */
	public static long[] recentGain(String name) {
		NeogulMineModule m = instance;
		if (m == null) {
			return null;
		}
		long[] r = m.recent.get(name);
		return r != null && System.currentTimeMillis() - r[1] < 2500 ? r : null;
	}

	/** 광물 창고 내용 {아이콘, 이름, 개수, 한도(모르면 -1)} - 개수가 0보다 큰 것만, 창고 칸 순서. 한 번도 안 열었으면 null. */
	public static List<Object[]> storage() {
		NeogulMineModule m = instance;
		if (m == null) {
			return null;
		}
		m.ensureLoaded();
		if (!m.opened) {
			return null;
		}
		List<Object[]> out = new ArrayList<>();
		for (var e : m.counts.entrySet()) {
			if (e.getValue() <= 0) {
				continue;
			}
			ItemStack ic = m.icon(e.getKey());
			out.add(new Object[]{ic == null ? new ItemStack(Items.STONE) : ic, e.getKey(), e.getValue(), m.maxes.getOrDefault(e.getKey(), -1)});
		}
		return out;
	}

	// ==================== 광물 창고 읽기 ====================

	/** 통계에 쓸 이름("[재료] 요리용 석탄" → "요리용 석탄"). */
	private static String statName(String n) {
		return n == null ? "" : n.replaceAll("\\[[^\\]]*\\]", "").replaceAll("\\s+", " ").trim();
	}

	private static String clean(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || (cp >= 0xA000 && cp <= 0xA4CF)))
				.forEach(sb::appendCodePoint);
		return sb.toString().replaceAll("\\s+", " ").trim();
	}

	private void readStorage(AbstractContainerScreen<?> hs) {
		String title = clean(hs.getTitle().getString());
		if (title.contains("섬") && title.contains("광물")) {
			return;   // 섬 광물창고(광물 생성기)는 따로
		}
		var slots = hs.getMenu().slots;
		int box = Math.max(0, slots.size() - 36);
		Map<String, Integer> found = new LinkedHashMap<>();
		Map<String, ItemStack> stacks = new HashMap<>();
		for (int i = 0; i < box; i++) {
			ItemStack st = slots.get(i).getItem();
			if (st == null || st.isEmpty()) {
				continue;
			}
			String name = clean(st.getHoverName().getString());
			if (name.startsWith("섬 광물창고")) {
				return;
			}
			for (String l : LunaCompat.itemLore(st)) {
				Matcher m = STORED.matcher(l);
				if (m.find()) {
					try {
						found.put(name, Integer.parseInt(m.group(1).replace(",", "")));
						if (m.group(2) != null) {
							maxes.put(name, Integer.parseInt(m.group(2).replace(",", "")));
						}
						stacks.put(name, st.copyWithCount(1));
					} catch (NumberFormatException ignored) {
					}
					break;
				}
			}
		}
		if (found.size() < 3) {
			// 49-629차: 꺼내기 창(따로 꺼내기 개수 고르는 창 등) - 이미 아는 광물만 보이면 그 개수만 고친다
			if (!opened || found.isEmpty() || !counts.keySet().containsAll(found.keySet())) {
				return;   // 광물 창고가 아님
			}
			boolean ch = false;
			for (var e : found.entrySet()) {
				Integer mx = maxes.get(e.getKey());
				if (mx == null || e.getValue() < mx) {
					full.remove(e.getKey());
				}
				int v = applyLore(e.getKey(), e.getValue());
				Integer old = counts.put(e.getKey(), v);
				ch |= old == null || old != v;
			}
			if (ch) {
				markDirty();
			}
			return;
		}
		startWithdrawWatch();
		boolean changed = !opened || !store.containsAll(found.keySet());
		store.addAll(found.keySet());   // 49-635차
		opened = true;
		for (var e : found.entrySet()) {
			Integer mx = maxes.get(e.getKey());
			if (mx == null || e.getValue() < mx) {
				full.remove(e.getKey());   // 자리가 생겼다
			} else {
				full.add(e.getKey());
			}
			int v = applyLore(e.getKey(), e.getValue());   // 49-629차
			Integer old = counts.put(e.getKey(), v);
			if (old == null || old != v) {
				changed = true;
			}
			if (!icons.containsKey(e.getKey())) {
				icons.put(e.getKey(), stacks.get(e.getKey()));
				iconRaw.remove(e.getKey());
				changed = true;
			}
		}
		changed |= dedupe();   // 49-631차
		if (changed) {
			markDirty();
		}
	}

	/** 49-631차: 창고 화면에서 읽은 이름인가(그림을 창고 칸에서 가져온 것). */
	private boolean isStore(String n) {
		return store.contains(n) || icons.containsKey(n) || iconRaw.containsKey(n);
	}

	/** 49-631차: 창고 이름과 같은 광물인데 창고에 없는 이름으로 따로 쌓인 줄을 지운다(창고 값이 맞는 값). */
	private boolean dedupe() {
		boolean ch = false;
		for (var it = counts.keySet().iterator(); it.hasNext(); ) {
			String k = it.next();
			if (isStore(k)) {
				continue;
			}
			if (!store.isEmpty()) {
				// 49-635차(사진: 사암 6, 오팔 조각 두 줄): 창고 칸에 없는 이름은 창고에 든 게 아니다(인벤토리로 감) - 지운다
				it.remove();
				recent.remove(k);
				ch = true;
				continue;
			}
			String sk = statName(k);
			for (String n : new ArrayList<>(counts.keySet())) {
				if (!n.equals(k) && isStore(n) && statName(n).equals(sk)) {
					it.remove();
					recent.remove(k);
					ch = true;
					break;
				}
			}
		}
		return ch;
	}

	// ==================== 49-629차: 꺼냄 ====================

	/** 창고 화면을 읽었다 - 꺼내기 감시를 켜고(꺼져 있었으면 지금 인벤토리를 기준으로). */
	private void startWithdrawWatch() {
		if (invLast == null || System.currentTimeMillis() > withdrawUntil) {
			invLast = invOres();
		}
		withdrawUntil = System.currentTimeMillis() + 1500;
	}

	/** 인벤토리(0~35칸)에 든 창고 광물 이름별 개수. */
	private Map<String, Integer> invOres() {
		Map<String, Integer> out = new HashMap<>();
		try {
			var inv = client.player.getInventory();
			for (int i = 0; i < 36; i++) {
				ItemStack st = inv.getItem(i);
				if (st == null || st.isEmpty()) {
					continue;
				}
				String n = clean(st.getHoverName().getString());
				if (counts.containsKey(n)) {
					out.merge(n, st.getCount(), Integer::sum);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulMine:inv", t);
		}
		return out;
	}

	/** 매 틱: 감시 중이면 인벤토리에 는 광물만큼 창고 개수를 줄인다(창고 화면을 다시 읽으면 그 값으로 덮어씀). */
	private void withdrawTick() {
		if (invLast == null || client.player == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now > withdrawUntil) {
			invLast = null;
			pend.clear();      // 다음에 창고를 열면 서버 값 그대로
			loreVal.clear();
			return;
		}
		if (LunaCompat.screenOf(client) instanceof AbstractContainerScreen<?> hs && !(hs instanceof InventoryScreen)) {
			withdrawUntil = now + 1500;   // 창고에서 이어진 상자 창(꺼내기 창)이 떠 있는 동안 계속
		}
		Map<String, Integer> cur = invOres();
		boolean ch = false;
		for (var e : cur.entrySet()) {
			int d = e.getValue() - invLast.getOrDefault(e.getKey(), 0);
			Integer have = counts.get(e.getKey());
			if (d > 0 && have != null && have > 0) {
				counts.put(e.getKey(), Math.max(0, have - d));
				pend.merge(e.getKey(), d, Integer::sum);
				full.remove(e.getKey());
				ch = true;
			}
		}
		invLast = cur;
		if (ch) {
			markDirty();
		}
	}

	// ==================== 얻음 ====================

	private String nameFor(String id) {
		String base = KO.getOrDefault(id, id);
		if (!opened) {
			return base;
		}
		// 49-631차(사진: "요리용 석탄"이 두 줄 - 174와 [재료] 요리용 석탄 180): 창고에 없는 이름(전에 KO 표 이름으로 만든 것)이 먼저 잡혀
		// 따로 쌓였다. 이제 창고에서 읽은 이름부터 찾고, 없을 때만 표 이름.
		for (String n : counts.keySet()) {
			if (isStore(n) && statName(n).equals(base)) {
				return n;
			}
		}
		String word = NG_WORD.get(id);
		for (String n : counts.keySet()) {
			if (!isStore(n)) {
				continue;
			}
			if (word != null) {
				if (n.contains(word)) {
					return n;
				}
				continue;
			}
			ItemStack st = icon(n);
			String rid = st == null ? null : LunaCompat.registryIdOf("ITEM", st.getItem());
			if (rid != null && id.equals(rid.replace("minecraft:", ""))) {
				boolean custom = false;
				for (String w : NG_WORD.values()) {
					custom |= n.contains(w);
				}
				if (!custom) {
					return n;
				}
			}
		}
		return base;
	}

	private ItemStack iconFor(String id, String name) {
		ItemStack st = icon(name);
		if (st != null && !st.isEmpty()) {
			return st;
		}
		String key = NG_KEY.get(id);
		if (key != null) {
			ItemStack ns = NeogulData.icon(NeogulData.get(key), 1);   // 49-612차: 서버 팩 모양(nexo 모델)
			if (ns != null && !ns.isEmpty()) {
				return ns;
			}
		}
		Item it = NG_ICON.get(id);
		if (it == null) {
			it = LunaCompat.itemById("minecraft:" + id);
		}
		return new ItemStack(it == null ? Items.STONE : it);
	}

	private void onGain(String id, int n) {
		try {
			if (!working()) {
				return;
			}
			ensureLoaded();
			String name = nameFor(id);
			if (opened && !store.isEmpty() && !isStore(name)) {
				// 49-635차: 창고에 칸이 없는 것(사암 등) - 인벤토리로 들어가니 아이템 획득이 따로 띄운다(보석이면 몇 개 나왔다는 알림만)
				if (GEMS.contains(id)) {
					gemNote(id, name, n);
				}
				return;
			}
			if (full.contains(name)) {
				return;   // 창고가 꽉 차 인벤토리로 들어감 - 아이템 획득이 인벤토리로 따로 띄운다
			}
			int total = -1;
			if (opened) {
				int have = counts.getOrDefault(name, 0);
				Integer mx = maxes.get(name);
				if (mx != null && have + n > mx) {
					n = Math.max(0, mx - have);   // 한도까지만 창고로
					full.add(name);
					if (n == 0) {
						return;
					}
				}
				total = have + n;
				counts.put(name, total);
			}
			boolean gem = GEMS.contains(id);
			if (!ItemPickupToastModule.addExternal(iconFor(id, name), name, n, total, gem)) {
				if (opened) {
					counts.put(name, Math.max(0, counts.getOrDefault(name, 0) - n));   // 방금 인벤토리로 들어온 것 - 되돌림
				}
				full.add(name);
				return;
			}
			kr.lunaslight.mod.util.LunaStats.addOre(statName(name), n);   // 49-617차: 통계 광물 탭
			long now0 = System.currentTimeMillis();
			long[] rg = recent.get(name);
			if (rg != null && now0 - rg[1] < 2500) {
				rg[0] += n;
				rg[1] = now0;
			} else {
				recent.put(name, new long[]{n, now0});
			}
			markDirty();
			if (gem) {
				gemNote(id, name, n);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulMine:gain", t);
		}
	}

	/** 보석이 나왔다 - 서브타이틀 자리 "N개 나왔다"(2.5초 안 같은 보석은 합침). */
	private void gemNote(String id, String name, int n) {
		long now = System.currentTimeMillis();
		for (Gem g : gems) {
			if (g.name.equals(name) && now - g.at[0] < 2500) {
				g.n[0] += n;
				g.at[0] = now;
				return;
			}
		}
		gems.add(new Gem(name, GEM_COLOR.getOrDefault(id, 0xFFFFD36A), new int[]{n}, new long[]{now}));
		while (gems.size() > 3) {
			gems.remove(0);
		}
	}

	@Override
	public void onTick() {
		Object conn = client.getConnection();
		if (conn != lastConn) {
			lastConn = conn;
			NeogulMineFeed.reset();
		}
		if (dirty && System.currentTimeMillis() - dirtyAt > 3000) {
			save();
		}
		if (!working()) {
			return;
		}
		ensureLoaded();
		NeogulMineFeed.tick(client, true);
		if (++readTick % 4 == 0 && LunaCompat.screenOf(client) instanceof AbstractContainerScreen<?> hs && !(hs instanceof InventoryScreen)) {
			readStorage(hs);
		}
		withdrawTick();   // 49-629차
	}

	/**
	 * 49-615차: 광물 창고 미리보기만 켜고 광물 획득은 꺼 둔 경우에도 창고를 열면 개수를 읽게(NeogulMinePeekModule.onTick이 부름).
	 * 광물 획득이 켜져 있으면 그쪽 onTick이 이미 읽으므로 아무것도 안 한다.
	 */
	public static void readForPeek() {
		NeogulMineModule m = instance;
		if (m == null || m.working() || m.client.player == null || !NeogulData.onNeogul(m.client)) {
			return;
		}
		m.ensureLoaded();
		if (m.dirty && System.currentTimeMillis() - m.dirtyAt > 3000) {
			m.save();
		}
		if (++m.readTick % 4 == 0 && LunaCompat.screenOf(m.client) instanceof AbstractContainerScreen<?> hs && !(hs instanceof InventoryScreen)) {
			m.readStorage(hs);
		}
		m.withdrawTick();   // 49-629차
	}

	@Override
	protected void onDisable() {
		if (dirty) {
			save();
		}
	}

	// ==================== 그리기 ====================

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		if (isPreview()) {
			drawGem(ctx, previewCenterX(), previewCenterY() - 4, "루비 수정", 2, 0xFFFF6B86, 1f);
			return;
		}
		if (client.player == null || !isEnabled() || !NeogulData.onNeogul(client)) {
			return;
		}
		ensureLoaded();
		if (!ItemPickupToastModule.on()) {
			if ("mine".equals(NeogulDungeonHud.location)) {
				drawHint(ctx, sw, "광물 획득은 아이템 획득을 켜야 보여요");
			}
			return;
		}
		if (!opened) {
			drawHint(ctx, sw, "광물 창고(/광물창고)를 한 번 열어 주세요");
		}
		long now = System.currentTimeMillis();
		gems.removeIf(g -> now - g.at[0] > 2800);
		int y = sh / 2 + 18;
		for (int i = gems.size() - 1; i >= 0; i--) {
			Gem g = gems.get(i);
			long age = now - g.at[0];
			float a = age < 150 ? age / 150f : age > 2400 ? Math.max(0f, (2800 - age) / 400f) : 1f;
			drawGem(ctx, sw / 2, y, g.name, g.n[0], g.color, a);
			y += 16;
		}
	}

	private void drawHint(GuiGraphicsExtractor ctx, int sw, String text) {
		var font = client.font;
		int w = LunaDraw.width(font, text) + 22, h = 16;
		int x = (sw - w) / 2, y = 34;
		LunaDraw.roundRectBordered(ctx, x, y, w, h, 4, 0xE0141A22, 0x66FFD36A);
		LunaDraw.roundRect(ctx, x + 6, y + 4, 3, 8, 1, 0xFFFFD36A);
		LunaDraw.text(ctx, font, text, x + 13, LunaDraw.textY(y, h), 0xFFFFFFFF);
	}

	/** 서브타이틀 자리(화면 가운데 아래)에 "루비 수정 2개 나왔어요!"(1.5배). */
	private void drawGem(GuiGraphicsExtractor ctx, int cx, int y, String name, int n, int color, float alpha) {
		var font = client.font;
		String tail = " " + n + "개 나왔어요!";
		int w = LunaDraw.width(font, name) + LunaDraw.width(font, tail);
		float base = LunaDraw.alpha();
		LunaDraw.setAlpha(base * alpha);
		try {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, cx, y);
			LunaCompat.guiScale(ctx, 1.5f, 1.5f);
			int x = -w / 2;
			LunaDraw.text(ctx, font, name, x + 1, 1, 0xFF000000 | ((color & 0xFCFCFC) >> 2));
			LunaDraw.text(ctx, font, name, x, 0, color);
			int tx = x + LunaDraw.width(font, name);
			LunaDraw.text(ctx, font, tail, tx + 1, 1, 0xFF3A3A3A);
			LunaDraw.text(ctx, font, tail, tx, 0, 0xFFFFFFFF);
			LunaCompat.guiPop(ctx);
		} finally {
			LunaDraw.setAlpha(base);
		}
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public String newFeatureScene() {
		return "stone";
	}

	/** 49-612차: 게임에서 보이는 그대로 - 진짜 아이템 획득 줄(오른쪽 아래)과 보석 알림(가운데 아래). */
	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		List<Object[]> rows = new ArrayList<>();
		rows.add(new Object[]{new ItemStack(Items.STONE), "돌", 1, 1284, false});
		rows.add(new Object[]{iconFor("ng121", "루비 수정"), "루비 수정", 2, 14, true});
		ItemPickupToastModule.drawSample(ctx, x + w, y + h - 40, rows);
		drawGem(ctx, x + w / 2, y + 22, "루비 수정", 2, 0xFFFF6B86, 1f);
	}
}
