package kr.lunaslight.mod.util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 49-33차: 제작 도우미의 **조합법 창고**. 버전마다 완전히 다른 세 갈래에서 조합법을 긁어와
 * 하나의 모양({@link Entry})으로 정리한다.
 *
 * <p>왜 세 갈래인가 - 1.21.2부터 마인크래프트가 **조합법 전체를 클라이언트로 안 보낸다**.
 * 예전엔 RecipeManager가 클라이언트에도 통째로 있었는데, 지금은 "조합법 책에 풀린 것"만
 * 요약본(RecipeDisplay)으로 온다. 그래서:
 * <ol>
 *   <li><b>싱글플레이</b> - 내장 서버의 RecipeManager를 직접 읽는다(전 버전 · 전부 다 있음).</li>
 *   <li><b>서버 + 1.21.1 이하</b> - 클라이언트 World의 RecipeManager(역시 전부 다 있음).</li>
 *   <li><b>서버 + 1.21.2 이상</b> - 조합법 책(ClientRecipeBook)의 요약본. <b>내가 푼 것만</b> 나온다.</li>
 * </ol>
 * 어느 것도 안 되면 {@link Availability#NONE} - 화면이 "읽을 수 없다"고 안내한다(빈 화면 대신).
 *
 * <p>전부 리플렉션이고 한 단계라도 실패하면 그 조합법만 건너뛴다. 목록은 월드가 바뀌거나
 * 30초가 지나면 다시 만든다(모으는 데 수십 ms 걸리므로 프레임마다 하지 않는다).
 */
public final class LunaRecipes {
	private LunaRecipes() {
	}

	/** 조합법을 어디까지 읽을 수 있는지. */
	public enum Availability {
		/** 전부(싱글 또는 1.21.1 이하 서버). */
		FULL,
		/** 조합법 책에 풀린 것만(1.21.2+ 서버). */
		BOOK_ONLY,
		/** 못 읽음. */
		NONE
	}

	/** 한 칸에 들어갈 수 있는 아이템들(대체 가능한 것 - 예: 판자 6종). */
	public record Slot(List<ItemStack> options) {
		public ItemStack first() {
			return options.isEmpty() ? ItemStack.EMPTY : options.get(0);
		}

		public boolean isEmpty() {
			return options.isEmpty();
		}
	}

	/**
	 * 정리된 조합법 하나.
	 *
	 * @param width  격자 가로(모양 없는 조합·제련이면 0)
	 * @param height 격자 세로(0이면 그냥 재료 목록으로 그린다)
	 */
	public record Entry(ItemStack result, int width, int height, List<Slot> slots, String station, String id, Object handle) {
		public boolean shaped() {
			return width > 0 && height > 0;
		}

		/** 작업대(3×3)/인벤토리(2×2) 조합법인지. */
		public boolean isCrafting() {
			return "작업대".equals(station);
		}

		/** 이 격자 크기(2 또는 3)에 들어가는지. */
		public boolean fitsGrid(int size) {
			if (shaped()) {
				return width <= size && height <= size;
			}
			return slots.size() <= size * size;
		}
	}

	// ==================== 캐시 ====================

	private static List<Entry> cache = List.of();
	private static Availability cacheAvailability = Availability.NONE;
	private static long cacheAt;
	private static Object cacheWorld;
	private static final long TTL_MS = 30_000L;

	public static Availability availability(Minecraft client) {
		ensure(client);
		return cacheAvailability;
	}

	public static List<Entry> all(Minecraft client) {
		ensure(client);
		return cache;
	}

	/** 이 아이템을 **만드는** 조합법들. */
	public static List<Entry> recipesFor(Minecraft client, Item item) {
		if (item == null) {
			return new ArrayList<>();
		}
		ensure(client);
		List<Entry> hit = byResult.get(item);
		return hit == null ? new ArrayList<>() : hit;
	}

	/** 이 아이템을 **재료로 쓰는** 조합법들. */
	public static List<Entry> usagesOf(Minecraft client, Item item) {
		List<Entry> out = new ArrayList<>();
		if (item == null) {
			return out;
		}
		outer:
		for (Entry e : all(client)) {
			for (Slot s : e.slots()) {
				for (ItemStack st : s.options()) {
					if (!st.isEmpty() && st.getItem() == item) {
						out.add(e);
						continue outer;
					}
				}
			}
		}
		return out;
	}

	public static void invalidate() {
		cacheAt = 0;
	}

	private static void ensure(Minecraft client) {
		if (client == null) {
			cache = List.of();
			cacheAvailability = Availability.NONE;
			return;
		}
		long now = System.currentTimeMillis();
		if (now - cacheAt < TTL_MS && cacheWorld == client.level) {
			return;
		}
		cacheAt = now;
		cacheWorld = client.level;
		List<Entry> out = new ArrayList<>();
		Availability av = Availability.NONE;
		try {
			// 49-34차: 1.21.2+는 조합법 책을 먼저 읽는다 - 빠른 제작(clickRecipe)에 쓰는 NetworkRecipeId가
			// 책 항목에만 있어서, 싱글이라도 서버 RecipeManager 쪽 객체로는 격자에 못 넣는다.
			// (바닐라가 재료를 하나라도 얻으면 조합법을 자동으로 풀어 주므로 "만들 수 있는 것"은 거의 다 책에 있다)
			boolean modernBook = LunaCompat.classOrNull("net.minecraft.world.item.crafting.display.RecipeDisplayEntry") != null;
			boolean gotBook = modernBook && collectFromBook(client, out);
			if (gotBook) {
				av = Availability.BOOK_ONLY;
			}
			// ⚠️ 49-47차(사용자: "조합법이 안 열린 것도 해주고, 나무 판자 6개 있으면 막대기가 없어도 울타리 만들게"):
			// 조합법 책에는 **내가 이미 푼 것만** 들어 있다. 그래서 싱글플레이에서도 아직 안 푼 조합법
			// (울타리 등)이 목록에 아예 없었고, 재귀 계획도 "막대기 조합법이 없다"고 판단해 포기했다.
			// 책을 먼저 읽는 이유는 빠른 제작에 쓰는 NetworkRecipeId가 책 항목에만 있기 때문이므로,
			// 책을 읽은 뒤 RecipeManager(싱글·1.21.1 이하)가 있으면 **책에 없던 조합법만 덧붙인다**.
			// 덧붙인 쪽은 handle이 없어 자동 제작 클릭은 안 되지만, 목록·조합법 보기·재귀 계획엔 다 쓰인다.
			List<Entry> fromManager = new ArrayList<>();
			if (collectFromManager(client, fromManager)) {
				if (!gotBook) {
					out.addAll(fromManager);
				} else {
					java.util.Set<String> known = new java.util.HashSet<>();
					for (Entry e : out) {
						known.add(signature(e));
					}
					for (Entry e : fromManager) {
						if (known.add(signature(e))) {
							out.add(e);
						}
					}
				}
				av = Availability.FULL;
			} else if (!gotBook && collectFromBook(client, out)) {
				av = Availability.BOOK_ONLY;
			}
			// 49-91차(8-20, 사용자: "조합법 없어도 가능한 조합"): 1.21.2+ 서버에서는 책에 풀린 것만 오므로
			// 안 푼 조합법(울타리 등)이 목록에 없었다 → 게임 jar 안의 바닐라 조합법(data/minecraft/recipe/*.json)을
			// 읽어 책에 없던 것을 덧붙인다. 격자에 넣는 건 슬롯 클릭(placeManual)이라 책이 몰라도 된다.
			if (av != Availability.FULL) {
				java.util.Set<String> known = new java.util.HashSet<>();
				for (Entry e : out) {
					known.add(signature(e));
				}
				int added = 0;
				for (Entry e : jarRecipes(client)) {
					if (known.add(signature(e))) {
						out.add(e);
						added++;
					}
				}
				if (added > 0) {
					av = Availability.FULL;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:collect", t);
		}
		cache = List.copyOf(out);
		cacheAvailability = out.isEmpty() ? Availability.NONE : av;
		// 49-39차: 결과 아이템 → 조합법 색인(재귀 계획이 조합법마다 전체를 훑지 않게)
		Map<Item, List<Entry>> idx = new HashMap<>();
		for (Entry e : cache) {
			if (!e.result().isEmpty()) {
				idx.computeIfAbsent(e.result().getItem(), k -> new ArrayList<>()).add(e);
			}
		}
		byResult = idx;
	}

	private static Map<Item, List<Entry>> byResult = new HashMap<>();

	/**
	 * 49-47차: 조합법 책과 RecipeManager를 합칠 때 같은 조합법을 두 번 넣지 않기 위한 지문.
	 * 결과 아이템 + 칸 배치(칸마다 첫 후보 아이템) - 같은 결과라도 재료가 다르면 다른 조합법으로 남는다.
	 */
	private static String signature(Entry e) {
		StringBuilder sb = new StringBuilder();
		try {
			sb.append(System.identityHashCode(e.result().getItem())).append('#').append(e.result().getCount())
				.append('/').append(e.width()).append('x').append(e.height());
			for (Slot sl : e.slots()) {
				sb.append('|');
				if (!sl.isEmpty() && !sl.options().isEmpty()) {
					sb.append(System.identityHashCode(sl.options().get(0).getItem()));
				}
			}
		} catch (Throwable ignored) {
			return e.id() == null ? String.valueOf(System.identityHashCode(e)) : e.id();
		}
		return sb.toString();
	}

	// ==================== ① · ② RecipeManager (싱글 · 1.21.1 이하) ====================

	private static boolean collectFromManager(Minecraft client, List<Entry> out) {
		Object manager = recipeManager(client);
		if (manager == null) {
			return false;
		}
		Collection<?> values = managerValues(manager);
		if (values == null || values.isEmpty()) {
			return false;
		}
		Object registries = LunaCompat.callNoArg(client.level, "getRegistryManager");
		for (Object raw : values) {
			try {
				// 1.20.5+는 RecipeEntry<?> 껍데기 - value()로 벗긴다
				Object recipe = LunaCompat.callNoArg(raw, "value");
				String id = idOf(raw);
				if (recipe == null) {
					recipe = raw;
				}
				Entry e = fromRecipe(recipe, registries, id, raw);
				if (e != null) {
					out.add(e);
				}
			} catch (Throwable ignored) {
				// 이 조합법만 건너뜀
			}
		}
		return !out.isEmpty();
	}

	private static Object recipeManager(Minecraft client) {
		try {
			// ① 싱글플레이: 내장 서버가 진짜 목록을 들고 있다
			Object server = LunaCompat.callNoArg(client, "getServer");
			if (server != null) {
				Object m = LunaCompat.callNoArg(server, "getRecipeManager");
				if (m != null) {
					return m;
				}
			}
		} catch (Throwable ignored) {
			// ②로
		}
		try {
			// ② 1.21.1 이하 서버: 클라이언트 World에도 통째로 온다
			if (client.level != null) {
				return LunaCompat.callNoArg(client.level, "getRecipeManager");
			}
		} catch (Throwable ignored) {
			// 없음
		}
		return null;
	}

	private static Collection<?> managerValues(Object manager) {
		for (String name : new String[]{"values", "sortedValues", "getAllRecipes"}) {
			Object v = LunaCompat.callNoArg(manager, name);
			if (v instanceof Collection<?> c && !c.isEmpty()) {
				return c;
			}
		}
		// 구버전은 Map<RecipeType, Map<Identifier, Recipe>> 필드뿐
		Object f = LunaCompat.getFieldValue(manager, "recipesById", "recipes");
		if (f instanceof Map<?, ?> map) {
			List<Object> flat = new ArrayList<>();
			for (Object v : map.values()) {
				if (v instanceof Map<?, ?> inner) {
					flat.addAll(inner.values());
				} else if (v != null) {
					flat.add(v);
				}
			}
			return flat;
		}
		return null;
	}

	private static String idOf(Object recipeOrEntry) {
		for (String name : new String[]{"id", "getId"}) {
			Object v = LunaCompat.callNoArg(recipeOrEntry, name);
			if (v != null && !(v instanceof Integer)) {
				return String.valueOf(v);
			}
		}
		return "";
	}

	/** Recipe 객체 하나 → Entry. 재료를 못 읽으면 null. */
	private static Entry fromRecipe(Object recipe, Object registries, String id, Object handle) {
		ItemStack result = resultOf(recipe, registries);
		if (result == null || result.isEmpty()) {
			return null;
		}
		List<?> ingredients = null;
		for (String name : new String[]{"getIngredients", "ingredients"}) {
			Object v = LunaCompat.callNoArg(recipe, name);
			if (v instanceof List<?> l) {
				ingredients = l;
				break;
			}
		}
		if (ingredients == null || ingredients.isEmpty()) {
			return null;
		}
		List<Slot> slots = new ArrayList<>(ingredients.size());
		for (Object ing : ingredients) {
			slots.add(new Slot(ingredientStacks(ing)));
		}
		int w = intOf(recipe, "getWidth", "width");
		int h = intOf(recipe, "getHeight", "height");
		if (w * h != slots.size()) {
			w = 0;
			h = 0;
		}
		return new Entry(result, w, h, slots, stationOf(recipe), id, handle);
	}

	private static ItemStack resultOf(Object recipe, Object registries) {
		// 1.19.3+ : getResult(DynamicRegistryManager) / getOutput(DynamicRegistryManager)
		if (registries != null) {
			for (String name : new String[]{"getResult", "getOutput"}) {
				Object v = LunaCompat.call1(recipe, name, registries);
				if (v instanceof ItemStack s && !s.isEmpty()) {
					return s;
				}
			}
		}
		for (String name : new String[]{"getResult", "getOutput"}) {
			Object v = LunaCompat.callNoArg(recipe, name);
			if (v instanceof ItemStack s && !s.isEmpty()) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Ingredient → 들어갈 수 있는 아이템들. 버전별로 배열/리스트/RegistryEntry 목록으로 갈린다. */
	private static List<ItemStack> ingredientStacks(Object ingredient) {
		List<ItemStack> out = new ArrayList<>();
		if (ingredient == null) {
			return out;
		}
		// 1.21.2+ : Optional<Ingredient> 로 감싸 오는 자리가 있다
		if (ingredient instanceof java.util.Optional<?> opt) {
			return opt.map(LunaRecipes::ingredientStacks).orElse(out);
		}
		for (String name : new String[]{"getMatchingStacks", "getMatchingItems", "getItems", "getStacks"}) {
			Object v = LunaCompat.callNoArg(ingredient, name);
			addStacks(v, out);
			if (!out.isEmpty()) {
				return out;
			}
		}
		return out;
	}

	private static void addStacks(Object v, List<ItemStack> out) {
		if (v instanceof ItemStack[] arr) {
			for (ItemStack s : arr) {
				if (s != null && !s.isEmpty()) {
					out.add(s);
				}
			}
			return;
		}
		Iterable<?> it = v instanceof Iterable<?> i ? i
				: (v instanceof java.util.stream.Stream<?> st ? st.toList() : null);
		if (it == null) {
			return;
		}
		for (Object o : it) {
			ItemStack s = toStack(o);
			if (s != null && !s.isEmpty()) {
				out.add(s);
			}
		}
	}

	/** ItemStack · Item · RegistryEntry&lt;Item&gt; 어느 쪽이든 ItemStack으로. */
	private static ItemStack toStack(Object o) {
		if (o instanceof ItemStack s) {
			return s;
		}
		if (o instanceof Item item) {
			return new ItemStack(item);
		}
		// RegistryEntry<Item> → value()
		Object v = LunaCompat.callNoArg(o, "value");
		if (v instanceof Item item) {
			return new ItemStack(item);
		}
		if (v instanceof ItemStack s) {
			return s;
		}
		return null;
	}

	private static int intOf(Object owner, String... names) {
		for (String n : names) {
			Object v = LunaCompat.callNoArg(owner, n);
			if (v instanceof Number num) {
				return num.intValue();
			}
			Object f = LunaCompat.getFieldValue(owner, n);
			if (f instanceof Number num) {
				return num.intValue();
			}
		}
		return 0;
	}

	/** 어디서 만드는지(화면에 작게 표시). 클래스 이름으로만 대충 가른다. */
	private static String stationOf(Object recipe) {
		String n = recipe == null ? "" : recipe.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
		if (n.contains("smelt") || n.contains("furnace")) {
			return "화로";
		}
		if (n.contains("blast")) {
			return "용광로";
		}
		if (n.contains("smok")) {
			return "훈연기";
		}
		if (n.contains("campfire")) {
			return "모닥불";
		}
		if (n.contains("stonecut")) {
			return "석재 절단기";
		}
		if (n.contains("smithing")) {
			return "대장장이 작업대";
		}
		return "작업대";
	}

	// ==================== ③ 조합법 책(1.21.2+ 서버) ====================

	private static boolean collectFromBook(Minecraft client, List<Entry> out) {
		try {
			if (client.player == null || client.level == null) {
				return false;
			}
			Object book = LunaCompat.callNoArg(client.player, "getRecipeBook");
			Object ordered = book == null ? null : LunaCompat.callNoArg(book, "getOrderedResults");
			if (!(ordered instanceof Collection<?> groups)) {
				return false;
			}
			Object ctx = slotContext(client);
			for (Object group : groups) {
				Object list = LunaCompat.callNoArg(group, "getAllRecipes");
				if (list == null) {
					list = LunaCompat.call1(group, "getResults", Boolean.FALSE);
				}
				if (!(list instanceof Collection<?> entries)) {
					continue;
				}
				for (Object de : entries) {
					try {
						Entry e = fromDisplayEntry(de, ctx);
						if (e != null) {
							out.add(e);
						}
					} catch (Throwable ignored) {
						// 이 조합법만 건너뜀
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:book", t);
		}
		return !out.isEmpty();
	}

	/** SlotDisplay#getStacks에 넘길 문맥(SlotDisplayContexts.createParameters(World)). */
	private static Object slotContext(Minecraft client) {
		try {
			Class<?> cls = LunaCompat.classOrNull("net.minecraft.world.item.crafting.display.SlotDisplayContext");
			if (cls == null || client.level == null) {
				return null;
			}
			for (Method m : cls.getMethods()) {
				if (m.getParameterCount() == 1 && java.lang.reflect.Modifier.isStatic(m.getModifiers())
						&& m.getParameterTypes()[0].isInstance(client.level)
						&& LunaCompat.nameMatches(cls, "createParameters", m.getName())) {
					m.setAccessible(true);
					return m.invoke(null, client.level);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:slotContext", t);
		}
		return null;
	}

	private static Entry fromDisplayEntry(Object displayEntry, Object ctx) {
		Object display = LunaCompat.callNoArg(displayEntry, "display");
		if (display == null) {
			return null;
		}
		ItemStack result = firstStack(LunaCompat.callNoArg(display, "result"), ctx);
		if (result == null || result.isEmpty()) {
			// RecipeDisplayEntry#getStacks(ctx)로 한 번 더
			Object stacks = ctx == null ? null : LunaCompat.call1(displayEntry, "getStacks", ctx);
			List<ItemStack> l = new ArrayList<>();
			addStacks(stacks, l);
			if (l.isEmpty()) {
				return null;
			}
			result = l.get(0);
		}
		Object ing = LunaCompat.callNoArg(display, "ingredients");
		if (!(ing instanceof List<?> list) || list.isEmpty()) {
			return null;
		}
		List<Slot> slots = new ArrayList<>(list.size());
		for (Object sd : list) {
			List<ItemStack> opts = new ArrayList<>();
			addStacks(ctx == null ? null : LunaCompat.call1(sd, "getStacks", ctx), opts);
			slots.add(new Slot(opts));
		}
		int w = intOf(display, "width");
		int h = intOf(display, "height");
		if (w * h != slots.size()) {
			w = 0;
			h = 0;
		}
		Object netId = LunaCompat.callNoArg(displayEntry, "id");
		return new Entry(result, w, h, slots, stationOf(display), idOf(displayEntry), netId);
	}

	private static ItemStack firstStack(Object slotDisplay, Object ctx) {
		if (slotDisplay == null || ctx == null) {
			return ItemStack.EMPTY;
		}
		Object first = LunaCompat.call1(slotDisplay, "getFirst", ctx);
		if (first instanceof ItemStack s && !s.isEmpty()) {
			return s;
		}
		List<ItemStack> l = new ArrayList<>();
		addStacks(LunaCompat.call1(slotDisplay, "getStacks", ctx), l);
		return l.isEmpty() ? ItemStack.EMPTY : l.get(0);
	}

	// ==================== 49-34차: 지금 가진 재료로 만들 수 있는 것 + 빠른 제작 ====================

	/**
	 * 인벤토리 개수(have)로 이 조합법을 한 번 만들 수 있는지. 칸마다 대체 재료 중 있는 것을 골라 쓴다(탐욕).
	 * have는 건드리지 않는다.
	 */
	public static boolean craftable(Entry e, Map<Item, Integer> have) {
		Map<Item, Integer> left = new HashMap<>(have);
		for (Slot s : e.slots()) {
			if (s.isEmpty()) {
				continue;
			}
			boolean ok = false;
			for (ItemStack opt : s.options()) {
				if (opt.isEmpty()) {
					continue;
				}
				int n = left.getOrDefault(opt.getItem(), 0);
				if (n > 0) {
					left.put(opt.getItem(), n - 1);
					ok = true;
					break;
				}
			}
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	/** 지금 인벤토리로 만들 수 있는 작업대 조합법(격자 크기 2 또는 3에 맞는 것만). 결과 아이템별로 하나씩. */
	public static List<Entry> craftableNow(Minecraft client, int gridSize) {
		Map<Item, Integer> have = inventoryCounts(client);
		List<Entry> out = new ArrayList<>();
		java.util.Set<Item> seen = new java.util.HashSet<>();
		for (Entry e : all(client)) {
			if (!e.isCrafting() || !e.fitsGrid(gridSize) || e.result().isEmpty()) {
				continue;
			}
			if (!craftable(e, have)) {
				continue;
			}
			if (seen.add(e.result().getItem())) {
				out.add(e);
			}
		}
		return out;
	}

	/**
	 * 49-39차(사용자: "검색창 + JEI식 목록 + 재귀 자동 제작"): 지금 바로는 못 만들지만 **중간 재료를 먼저 만들면**
	 * 만들 수 있는 것들(전부 지금 인벤토리로 해결되는 계획만). craftableNow에 이미 있는 결과는 뺀다.
	 * 결과 아이템별 하나씩, 계획(steps)을 같이 돌려준다.
	 */
	public static List<Chain> craftableViaSteps(Minecraft client, int gridSize, List<Entry> direct) {
		java.util.Set<Item> seen = new java.util.HashSet<>();
		for (Entry e : direct) {
			seen.add(e.result().getItem());
		}
		List<Chain> out = new ArrayList<>();
		Map<Item, Integer> have = inventoryCounts(client);
		for (Entry e : all(client)) {
			if (!e.isCrafting() || !e.fitsGrid(gridSize) || e.result().isEmpty() || seen.contains(e.result().getItem())) {
				continue;
			}
			// 빠른 걸러내기: 재료 중 인벤토리에도 없고 조합법도 없는 게 있으면 계획해 봤자 실패
			boolean hopeless = false;
			for (Slot sl : e.slots()) {
				if (sl.isEmpty()) {
					continue;
				}
				boolean any = false;
				for (ItemStack opt : sl.options()) {
					if (opt.isEmpty()) {
						continue;
					}
					if (have.getOrDefault(opt.getItem(), 0) > 0 || byResult.containsKey(opt.getItem())) {
						any = true;
						break;
					}
				}
				if (!any) {
					hopeless = true;
					break;
				}
			}
			if (hopeless) {
				continue;
			}
			Plan plan = planFor(client, e, gridSize);
			if (plan == null || !plan.ready() || plan.steps().size() <= 1) {
				continue;
			}
			seen.add(e.result().getItem());
			out.add(new Chain(e, plan));
		}
		return out;
	}

	/** 목록 항목: 조합법 + 그걸 만들기 위한 계획(중간 단계 포함). */
	public record Chain(Entry entry, Plan plan) {
	}

	/**
	 * 특정 조합법 하나를 목표로 한 계획(격자 크기 안에서만 - 인벤토리 2×2에서 3×3 조합법을 중간 단계로 쓰지 않게).
	 * 각 단계는 Entry(handle 포함)를 들고 있어 자동 제작이 그대로 clickRecipe로 넣을 수 있다.
	 */
	public static Plan planFor(Minecraft client, Entry target, int gridSize) {
		return planFor(client, target, gridSize, 1);
	}

	/**
	 * 49-122차: 최종 결과를 <b>times번 조합</b>하는 계획(중간 단계는 필요한 만큼만 - resolveEntry가 남는 양을
	 * 인벤토리로 되돌려 과다 제작을 막는다). 예전 startAutoCraft가 "1개짜리 계획의 각 단계 횟수에 배수를 곱하던"
	 * 방식은 중간 산출물의 잉여(반블록 6개 나오는데 3개만 필요 등)를 무시해 재료를 통째로 갈아 버렸다 - 그래서 다시 짠다.
	 */
	public static Plan planFor(Minecraft client, Entry target, int gridSize, int times) {
		Map<Item, Integer> have = inventoryCounts(client);
		List<Step> steps = new ArrayList<>();
		Map<Item, Integer> missing = new LinkedHashMap<>();
		resolveEntry(client, target, Math.max(1, times), have, steps, missing, 0, new java.util.HashSet<>(), gridSize);
		java.util.Collections.reverse(steps);
		return new Plan(steps, missing);
	}

	private static void resolveEntry(Minecraft client, Entry r, int times, Map<Item, Integer> have,
			List<Step> steps, Map<Item, Integer> missing, int depth, java.util.Set<Item> visiting, int gridSize) {
		steps.add(new Step(r.result(), times, depth, false, r));
		Map<Item, Integer> per1 = new LinkedHashMap<>();
		for (Slot s : r.slots()) {
			ItemStack pick = pickAvailable(s, have);
			if (pick == null || pick.isEmpty()) {
				continue;
			}
			per1.merge(pick.getItem(), 1, Integer::sum);
		}
		for (Map.Entry<Item, Integer> e : per1.entrySet()) {
			Item item = e.getKey();
			int need = e.getValue() * times;
			int stock = have.getOrDefault(item, 0);
			int use = Math.min(stock, need);
			if (use > 0) {
				have.put(item, stock - use);
				need -= use;
			}
			if (need <= 0) {
				continue;
			}
			Entry sub = null;
			for (Entry cand : recipesFor(client, item)) {
				if (cand.isCrafting() && cand.fitsGrid(gridSize)) {   // 49-91차: handle 없어도(placeManual) 된다
					sub = cand;
					break;
				}
			}
			if (sub == null || depth + 1 >= MAX_DEPTH || !visiting.add(item)) {
				missing.merge(item, need, Integer::sum);
				continue;
			}
			int per = Math.max(1, sub.result().getCount());
			int subTimes = (need + per - 1) / per;
			resolveEntry(client, sub, subTimes, have, steps, missing, depth + 1, visiting, gridSize);
			// 만들고 남는 양은 인벤토리에 있는 셈
			int made = subTimes * per;
			if (made > need) {
				have.merge(item, made - need, Integer::sum);
			}
			visiting.remove(item);
		}
	}

	/**
	 * 바닐라 조합법 책의 [넣기]와 같은 경로로 격자에 재료를 채운다:
	 * ClientPlayerInteractionManager#clickRecipe(syncId, Recipe|RecipeEntry|NetworkRecipeId, craftAll).
	 * 실제 이동은 서버가 하므로 서버에서도 그대로 동작한다. 성공하면 true.
	 */
	public static boolean place(Minecraft client, Entry e, boolean craftAll) {
		try {
			if (client == null || client.player == null || client.gameMode == null || e.handle() == null) {
				return false;
			}
			int syncId = client.player.containerMenu.containerId;
			Object im = client.gameMode;
			for (Method m : im.getClass().getMethods()) {
				if (m.getParameterCount() != 3 || !LunaCompat.nameMatches(im.getClass(), "clickRecipe", m.getName())) {
					continue;
				}
				Class<?>[] pt = m.getParameterTypes();
				if (pt[0] != int.class || pt[2] != boolean.class || !pt[1].isInstance(e.handle())) {
					continue;
				}
				m.setAccessible(true);
				m.invoke(im, syncId, e.handle(), craftAll);
				return true;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:place", t);
		}
		return false;
	}

	// ==================== ④ 게임 jar의 바닐라 조합법(49-91차, 8-20) ====================

	private static List<Entry> jarCache;

	/** 게임 jar 안 data/minecraft/recipe(s)/*.json 중 작업대 조합법. 한 번 읽어 두고 재사용(태그는 첫 호출 때 푼다). */
	private static List<Entry> jarRecipes(Minecraft client) {
		if (jarCache != null) {
			return jarCache;
		}
		List<Entry> out = new ArrayList<>();
		try {
			java.net.URL probe = null;
			String dir = null;
			for (String d : new String[]{"data/minecraft/recipe/", "data/minecraft/recipes/"}) {
				probe = Minecraft.class.getResource("/" + d + "stick.json");
				if (probe != null) {
					dir = d;
					break;
				}
			}
			String u = probe == null ? "" : probe.toString();
			int bang = u.indexOf("!/");
			if (probe == null || !u.startsWith("jar:") || bang < 0) {
				jarCache = List.of();
				return jarCache;
			}
			java.nio.file.Path jar = java.nio.file.Paths.get(new java.net.URI(u.substring(4, bang)));
			Map<String, List<ItemStack>> tagCache = new HashMap<>();
			try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar.toFile())) {
				java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
				while (en.hasMoreElements()) {
					java.util.zip.ZipEntry ze = en.nextElement();
					String n = ze.getName();
					if (!n.startsWith(dir) || !n.endsWith(".json") || n.indexOf('/', dir.length()) >= 0) {
						continue;
					}
					try (java.io.Reader r = new java.io.InputStreamReader(zf.getInputStream(ze), java.nio.charset.StandardCharsets.UTF_8)) {
						com.google.gson.JsonElement je = com.google.gson.JsonParser.parseReader(r);
						if (!je.isJsonObject()) {
							continue;
						}
						Entry e = fromJson(je.getAsJsonObject(), "minecraft:" + n.substring(dir.length(), n.length() - 5), tagCache);
						if (e != null) {
							out.add(e);
						}
					} catch (Throwable ignored) {
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:jar", t);
		}
		jarCache = List.copyOf(out);
		return jarCache;
	}

	/** crafting_shaped / crafting_shapeless JSON → Entry. 모르는 아이템·태그가 있으면 null. */
	private static Entry fromJson(com.google.gson.JsonObject o, String id, Map<String, List<ItemStack>> tagCache) {
		String type = o.has("type") ? o.get("type").getAsString() : "";
		boolean shaped = type.endsWith("crafting_shaped");
		if (!shaped && !type.endsWith("crafting_shapeless")) {
			return null;
		}
		ItemStack result = resultFromJson(o.get("result"));
		if (result == null || result.isEmpty()) {
			return null;
		}
		List<Slot> slots = new ArrayList<>();
		int w = 0, h = 0;
		if (shaped) {
			com.google.gson.JsonArray pattern = o.getAsJsonArray("pattern");
			com.google.gson.JsonObject key = o.getAsJsonObject("key");
			if (pattern == null || key == null || pattern.size() == 0) {
				return null;
			}
			h = pattern.size();
			for (com.google.gson.JsonElement row : pattern) {
				w = Math.max(w, row.getAsString().length());
			}
			for (com.google.gson.JsonElement row : pattern) {
				String rs = row.getAsString();
				for (int c = 0; c < w; c++) {
					char ch = c < rs.length() ? rs.charAt(c) : ' ';
					if (ch == ' ' || !key.has(String.valueOf(ch))) {
						slots.add(new Slot(List.of()));
						continue;
					}
					List<ItemStack> opts = ingredientFromJson(key.get(String.valueOf(ch)), tagCache);
					if (opts.isEmpty()) {
						return null;
					}
					slots.add(new Slot(opts));
				}
			}
		} else {
			com.google.gson.JsonArray ings = o.getAsJsonArray("ingredients");
			if (ings == null || ings.size() == 0 || ings.size() > 9) {
				return null;
			}
			for (com.google.gson.JsonElement ing : ings) {
				List<ItemStack> opts = ingredientFromJson(ing, tagCache);
				if (opts.isEmpty()) {
					return null;
				}
				slots.add(new Slot(opts));
			}
		}
		return new Entry(result, w, h, slots, "작업대", id, null);
	}

	private static ItemStack resultFromJson(com.google.gson.JsonElement r) {
		if (r == null) {
			return null;
		}
		String id;
		int count = 1;
		if (r.isJsonPrimitive()) {
			id = r.getAsString();
		} else if (r.isJsonObject()) {
			com.google.gson.JsonObject ro = r.getAsJsonObject();
			id = ro.has("id") ? ro.get("id").getAsString() : ro.has("item") ? ro.get("item").getAsString() : null;
			if (ro.has("count")) {
				count = Math.max(1, ro.get("count").getAsInt());
			}
		} else {
			return null;
		}
		Item item = LunaCompat.itemById(id);
		return item == null ? null : new ItemStack(item, count);
	}

	/** {"item":..} · {"tag":..} · "id" · "#tag" · 그 배열 → 후보 아이템들. */
	private static List<ItemStack> ingredientFromJson(com.google.gson.JsonElement ing, Map<String, List<ItemStack>> tagCache) {
		List<ItemStack> out = new ArrayList<>();
		if (ing == null) {
			return out;
		}
		if (ing.isJsonArray()) {
			for (com.google.gson.JsonElement e : ing.getAsJsonArray()) {
				out.addAll(ingredientFromJson(e, tagCache));
			}
			return out;
		}
		String item = null, tag = null;
		if (ing.isJsonPrimitive()) {
			String v = ing.getAsString();
			if (v.startsWith("#")) {
				tag = v.substring(1);
			} else {
				item = v;
			}
		} else if (ing.isJsonObject()) {
			com.google.gson.JsonObject io = ing.getAsJsonObject();
			if (io.has("item")) {
				item = io.get("item").getAsString();
			} else if (io.has("tag")) {
				tag = io.get("tag").getAsString();
			}
		}
		if (item != null) {
			Item it = LunaCompat.itemById(item);
			if (it != null) {
				out.add(new ItemStack(it));
			}
		} else if (tag != null) {
			out.addAll(tagCache.computeIfAbsent(tag, LunaRecipes::tagItems));
		}
		return out;
	}

	/** 아이템 태그(#minecraft:planks)의 아이템들 - Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, id)). 1.19.3+. */
	private static List<ItemStack> tagItems(String tagId) {
		List<ItemStack> out = new ArrayList<>();
		try {
			String ns = "minecraft", path = tagId;
			int colon = tagId.indexOf(':');
			if (colon >= 0) {
				ns = tagId.substring(0, colon);
				path = tagId.substring(colon + 1);
			}
			Object ident = LunaCompat.identifier(ns, path);
			Class<?> keysCls = LunaCompat.classForName("net.minecraft.core.registries.Registries");
			Object itemKey = LunaCompat.getFieldCompat(keysCls, "ITEM").get(null);
			Class<?> tagKeyCls = LunaCompat.classForName("net.minecraft.tags.TagKey");
			Object tagKey = null;
			for (Method m : tagKeyCls.getMethods()) {
				if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 2
						&& m.getReturnType() == tagKeyCls && m.getParameterTypes()[1].isInstance(ident)) {
					tagKey = m.invoke(null, itemKey, ident);
					break;
				}
			}
			if (tagKey == null) {
				return out;
			}
			Object registry = LunaCompat.getFieldCompat(LunaCompat.classForName("net.minecraft.core.registries.BuiltInRegistries"), "ITEM").get(null);
			Object entries = null;
			for (Method m : registry.getClass().getMethods()) {
				if (m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(tagKey)
						&& Iterable.class.isAssignableFrom(m.getReturnType())
						&& LunaCompat.nameMatches(registry.getClass(), "iterateEntries", m.getName())) {
					m.setAccessible(true);
					entries = m.invoke(registry, tagKey);
					break;
				}
			}
			if (!(entries instanceof Iterable<?> it)) {
				return out;
			}
			for (Object holder : it) {
				Object v = LunaCompat.callNoArg(holder, "value");
				if (v instanceof Item item) {
					out.add(new ItemStack(item));
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:tag:" + tagId, t);
		}
		return out;
	}

	// ==================== 슬롯 클릭으로 격자 채우기(49-91차, 8-20) ====================

	/**
	 * 조합법 책을 거치지 않고 <b>슬롯 클릭</b>으로 격자를 채운다. 책의 [넣기]는 서버가 "내가 푼 조합법"일 때만
	 * 들어주므로(안 푼 것·서버가 책을 끈 곳에선 아무 일도 안 일어남) 이쪽이 어디서나 된다. 클릭은 클라이언트가
	 * 먼저 반영하고(예측) 서버가 따라오므로 한 프레임에 전부 보낸다 - 기다림이 없어 빠르다.
	 *
	 * @param sets 몇 세트 넣을지. 0 이하면 최대(재료·스택 한도 안에서).
	 * @return 넣은 세트 수(0 = 실패: 커서에 든 것이 있거나 재료 부족).
	 */
	public static int placeManual(Minecraft client, Entry e, int gridSize, int sets) {
		try {
			if (client == null || client.player == null || client.gameMode == null) {
				return 0;
			}
			var handler = client.player.containerMenu;
			int syncId = handler.containerId;
			int gridN = gridSize * gridSize;
			if (handler.slots.size() < gridN + 1 || !handler.getCarried().isEmpty()) {
				return 0;
			}
			boolean playerHandler = handler == client.player.inventoryMenu;
			// 격자 자리(핸들러 슬롯 번호) → 조합법 칸
			Map<Integer, Slot> want = new LinkedHashMap<>();
			if (e.shaped()) {
				if (e.width() > gridSize || e.height() > gridSize) {
					return 0;
				}
				for (int r = 0; r < e.height(); r++) {
					for (int c = 0; c < e.width(); c++) {
						Slot sl = e.slots().get(r * e.width() + c);
						if (!sl.isEmpty()) {
							want.put(1 + r * gridSize + c, sl);
						}
					}
				}
			} else {
				int i = 0;
				for (Slot sl : e.slots()) {
					if (sl.isEmpty()) {
						continue;
					}
					if (i >= gridN) {
						return 0;
					}
					want.put(1 + i, sl);
					i++;
				}
			}
			if (want.isEmpty()) {
				return 0;
			}
			// 격자에 남은 것은 인벤토리로(Shift+클릭)
			for (int g = 1; g <= gridN; g++) {
				if (!handler.getSlot(g).getItem().isEmpty()) {
					click(client, syncId, g, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE);
				}
			}
			for (int g = 1; g <= gridN; g++) {
				if (!handler.getSlot(g).getItem().isEmpty()) {
					return 0;   // 인벤토리가 가득
				}
			}
			// 인벤토리 개수
			Map<Item, Integer> have = new HashMap<>();
			int size = handler.slots.size();
			for (int i = 0; i < size; i++) {
				if (!isPlayerStorageSlot(handler, playerHandler, gridN, i)) {
					continue;
				}
				ItemStack st = handler.getSlot(i).getItem();
				if (st != null && !st.isEmpty()) {
					have.merge(st.getItem(), st.getCount(), Integer::sum);
				}
			}
			// 칸마다 재료 고르기 - 남은 개수가 가장 많은 후보(같은 아이템끼리 모이게)
			Map<Integer, Item> choice = new LinkedHashMap<>();
			Map<Item, Integer> perSet = new LinkedHashMap<>();
			Map<Item, Integer> stackMax = new HashMap<>();
			for (Map.Entry<Integer, Slot> en : want.entrySet()) {
				Item best = null;
				int bestN = 0;
				for (ItemStack opt : en.getValue().options()) {
					if (opt.isEmpty()) {
						continue;
					}
					int n = have.getOrDefault(opt.getItem(), 0) - perSet.getOrDefault(opt.getItem(), 0);
					if (n > bestN) {
						best = opt.getItem();
						bestN = n;
						stackMax.put(best, Math.max(1, opt.getMaxStackSize()));
					}
				}
				if (best == null) {
					return 0;
				}
				choice.put(en.getKey(), best);
				perSet.merge(best, 1, Integer::sum);
			}
			int max = 64;
			for (Map.Entry<Item, Integer> en : perSet.entrySet()) {
				max = Math.min(max, have.getOrDefault(en.getKey(), 0) / en.getValue());
				max = Math.min(max, stackMax.getOrDefault(en.getKey(), 64));
			}
			if (max <= 0) {
				return 0;
			}
			int n = sets <= 0 ? max : Math.min(sets, max);
			for (Item item : perSet.keySet()) {
				List<Integer> targets = new ArrayList<>();
				for (Map.Entry<Integer, Item> en : choice.entrySet()) {
					if (en.getValue() == item) {
						targets.add(en.getKey());
					}
				}
				int[] need = new int[targets.size()];
				java.util.Arrays.fill(need, n);
				for (int guard = 0; guard < 40; guard++) {
					int remaining = 0;
					for (int v : need) {
						remaining += v;
					}
					if (remaining == 0) {
						break;
					}
					int src = findSource(handler, playerHandler, gridN, item);
					if (src < 0) {
						break;
					}
					click(client, syncId, src, 0, net.minecraft.world.inventory.ContainerInput.PICKUP);   // 들기
					ItemStack cur = handler.getCarried();
					if (cur == null || cur.isEmpty() || cur.getItem() != item) {
						break;
					}
					if (n * targets.size() < 64) {
						// 49-122차(사용자: "판자 4스택 나옴"): 드래그(아래)는 집어 든 한 스택을 칸 수로 나눠 뿌리므로
						// 칸당 n보다 더 들어가 넘칠 수 있다(단일 칸 레시피가 특히). 칸당 넣을 총량이 한 스택 미만이면
						// 넘칠 위험이 있으니 우클릭으로 <b>정확히 n개씩</b> 놓는다(한 칸당 최대 63번, 한 번의 격자 채우기로 끝).
						for (int i = 0; i < targets.size(); i++) {
							while (need[i] > 0 && !handler.getCarried().isEmpty()) {
								click(client, syncId, targets.get(i), 1, net.minecraft.world.inventory.ContainerInput.PICKUP);
								need[i]--;
							}
						}
					} else {
						// 끌어 나누기(드래그) - 아직 모자란 칸들에 고르게. 세트 수보다 더 들어가도 괜찮다(그만큼 더 만든다).
						click(client, syncId, -999, 0, net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT);
						for (int i = 0; i < targets.size(); i++) {
							if (need[i] > 0) {
								click(client, syncId, targets.get(i), 1, net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT);
							}
						}
						click(client, syncId, -999, 2, net.minecraft.world.inventory.ContainerInput.QUICK_CRAFT);
						for (int i = 0; i < targets.size(); i++) {
							ItemStack in = handler.getSlot(targets.get(i)).getItem();
							need[i] = Math.max(0, n - (in == null ? 0 : in.getCount()));
						}
					}
					// 남은 것은 제자리로(같은 아이템이면 합쳐진다), 안 되면 빈 칸으로
					if (!handler.getCarried().isEmpty()) {
						click(client, syncId, src, 0, net.minecraft.world.inventory.ContainerInput.PICKUP);
					}
					if (!handler.getCarried().isEmpty()) {
						int empty = findSource(handler, playerHandler, gridN, null);
						if (empty >= 0) {
							click(client, syncId, empty, 0, net.minecraft.world.inventory.ContainerInput.PICKUP);
						}
					}
					if (!handler.getCarried().isEmpty()) {
						break;
					}
				}
			}
			return n;
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:placeManual", t);
			return 0;
		}
	}

	private static void click(Minecraft client, int syncId, int slot, int button, net.minecraft.world.inventory.ContainerInput type) {
		client.gameMode.handleContainerInput(syncId, slot, button, type, client.player);
	}

	/** 내 인벤토리 보관 칸인지(격자·결과·갑옷·보조손 제외). */
	private static boolean isPlayerStorageSlot(net.minecraft.world.inventory.AbstractContainerMenu handler, boolean playerHandler, int gridN, int i) {
		if (playerHandler) {
			return i >= 9 && i <= 44;
		}
		if (i <= gridN) {
			return false;
		}
		try {
			return handler.getSlot(i).container instanceof net.minecraft.world.entity.player.Inventory;
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** item이 든 가장 큰 보관 칸(item == null이면 빈 칸). 없으면 -1. */
	private static int findSource(net.minecraft.world.inventory.AbstractContainerMenu handler, boolean playerHandler, int gridN, Item item) {
		int best = -1, bestN = 0;
		int size = handler.slots.size();
		for (int i = 0; i < size; i++) {
			if (!isPlayerStorageSlot(handler, playerHandler, gridN, i)) {
				continue;
			}
			ItemStack st = handler.getSlot(i).getItem();
			if (item == null) {
				if (st == null || st.isEmpty()) {
					return i;
				}
				continue;
			}
			if (st != null && !st.isEmpty() && st.getItem() == item && st.getCount() > bestN) {
				best = i;
				bestN = st.getCount();
			}
		}
		return best;
	}

	// ==================== 재귀 제작 계획 ====================

	/** 계획 한 줄 - "무엇을 몇 개 만든다"(depth = 몇 단계 아래인지). entry는 자동 제작용(없으면 null). */
	public record Step(ItemStack result, int times, int depth, boolean missing, Entry entry) {
		public Step(ItemStack result, int times, int depth, boolean missing) {
			this(result, times, depth, missing, null);
		}
	}

	/** 목표 하나에 대한 계획. missing이 비어 있으면 지금 바로 다 만들 수 있다. */
	public record Plan(List<Step> steps, Map<Item, Integer> missing) {
		public boolean ready() {
			return missing.isEmpty();
		}
	}

	private static final int MAX_DEPTH = 6;

	/**
	 * 목표 아이템을 count개 만들기 위한 계획. 인벤토리에 있는 것부터 쓰고, 모자란 재료는
	 * 그 재료의 조합법으로 파고든다(최대 {@value #MAX_DEPTH}단계). 순환(A→B→A)은 방문 표시로 끊는다.
	 */
	public static Plan plan(Minecraft client, Item target, int count) {
		Map<Item, Integer> have = inventoryCounts(client);
		List<Step> steps = new ArrayList<>();
		Map<Item, Integer> missing = new LinkedHashMap<>();
		resolve(client, target, count, have, steps, missing, 0, new java.util.HashSet<>());
		java.util.Collections.reverse(steps);   // 깊은 것부터 = 먼저 만들어야 하는 것부터
		return new Plan(steps, missing);
	}

	private static void resolve(Minecraft client, Item item, int need, Map<Item, Integer> have,
			List<Step> steps, Map<Item, Integer> missing, int depth, java.util.Set<Item> visiting) {
		if (item == null || need <= 0) {
			return;
		}
		int stock = have.getOrDefault(item, 0);
		int use = Math.min(stock, need);
		if (use > 0) {
			have.put(item, stock - use);
			need -= use;
		}
		if (need <= 0) {
			return;
		}
		List<Entry> recipes = recipesFor(client, item);
		if (recipes.isEmpty() || depth >= MAX_DEPTH || !visiting.add(item)) {
			missing.merge(item, need, Integer::sum);
			return;
		}
		Entry r = recipes.get(0);
		int per = Math.max(1, r.result().getCount());
		int times = (need + per - 1) / per;
		steps.add(new Step(r.result(), times, depth, false, r));
		// 재료를 몇 개씩 쓰는지 센다(같은 재료가 여러 칸에 있으면 그만큼)
		Map<Item, Integer> per1 = new LinkedHashMap<>();
		for (Slot s : r.slots()) {
			ItemStack pick = pickAvailable(s, have);
			if (pick == null || pick.isEmpty()) {
				continue;
			}
			per1.merge(pick.getItem(), 1, Integer::sum);
		}
		for (Map.Entry<Item, Integer> e : per1.entrySet()) {
			resolve(client, e.getKey(), e.getValue() * times, have, steps, missing, depth + 1, visiting);
		}
		visiting.remove(item);
	}

	/** 대체 가능한 재료 중 인벤토리에 있는 것을 먼저 고른다(없으면 첫 번째). */
	private static ItemStack pickAvailable(Slot slot, Map<Item, Integer> have) {
		for (ItemStack s : slot.options()) {
			if (!s.isEmpty() && have.getOrDefault(s.getItem(), 0) > 0) {
				return s;
			}
		}
		return slot.first();
	}

	public static Map<Item, Integer> inventoryCounts(Minecraft client) {
		Map<Item, Integer> out = new HashMap<>();
		try {
			if (client == null || client.player == null) {
				return out;
			}
			var inv = client.player.getInventory();
			Object sizeObj = LunaCompat.callNoArg(inv, "size");
			int size = sizeObj instanceof Number n ? n.intValue() : 41;
			for (int i = 0; i < size; i++) {
				Object st = LunaCompat.call1(inv, "getStack", i);
				if (st instanceof ItemStack s && !s.isEmpty()) {
					out.merge(s.getItem(), s.getCount(), Integer::sum);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("recipes:inventory", t);
		}
		return out;
	}
}
