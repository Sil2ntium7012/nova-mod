package kr.lunaslight.mod.module.impl.misc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.TextCapture;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-502차(사용자: "너굴마을 창고 열면 창고 모두 미리보기, 하이픽셀 모드처럼 상자 쫙 펼쳐서 보고 있는 것만 밝게, 상자별 이름"): <b>창고 미리보기</b>.
 *
 * <p>49-508차(사용자 사진: "3번 창고가 내가 봤던 상자로 돼 있는 오류, 상자에 리소스팩 적용, 기존 창고 다 가리고 창고 고르는 식으로 가운데에,
 * 4개의 상자(던전/엔더 창고, 귀속 보관함, 낚시 창고)도 인벤 양쪽에 2개씩"):
 * <ul>
 *   <li>잠긴 창고 버튼을 누르면 서버가 목록을 다시 열었고, 그 목록을 그 창고로 적어 버렸다 → "N번 창고" 버튼이 2개 이상 있는 창은 늘 목록으로 본다
 *       (창고로 적지 않음). 예전에 잘못 적힌 것(버튼이 2개 이상 든 창고)은 읽을 때 지운다.</li>
 *   <li>판 = 바닐라 상자 그림(textures/gui/container/generic_54.png - 리소스팩 그대로)을 줄여 그린 것. 제목, 칸, 아이템 모두 같은 비율.</li>
 *   <li>창고 목록 화면: 서버 창(인벤 위쪽)을 가리고 그 자리 가운데에 번호 창고를 펼쳐 고르게 한다. 번호 없는 4개(던전 창고 등)는 인벤 양쪽에 2개씩.
 *       판 오른쪽 위에는 서버 버튼 그림(잠김 표시 포함)을 작게.</li>
 *   <li>창고 안: 예전처럼 GUI 옆에 펼치고 지금 창고만 밝게.</li>
 *   <li>판 클릭 = 그 창고(목록이면 버튼, 창고 안이면 /창고N - 번호 창고만), 판 우클릭 = 이름 바꾸기.</li>
 * </ul>
 */
public class NeogulStorageModule extends Module {

	// 49-558차(사용자: "왜 섬 업그레이드나 다른 창에서 창고 미리보기가 뜨는 거야"): "(\d+)번 창고"가 이름 <b>어디에든</b> 있으면 버튼으로 봐서
	// 섬 업그레이드의 "3번 창고 해금" 같은 아이템 2개로 목록 화면이라고 착각했다 - 이름 전체가 "N번 창고"(뒤에 잠김 정도)일 때만.
	private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\s*번\\s*창고(?:\\s*(?:잠김|잠금|미개방))?$");
	/** 번호 없는 창고: 이름 끝이 "창고"나 "보관함"(던전 창고, 엔더 창고, 귀속 보관함, 낚시 창고 …). */
	private static final Pattern SPECIAL = Pattern.compile("^\\s*((?:[가-힣A-Za-z ]{1,12}(?:창고|보관함))|엔더 ?상자)\\s*$");
	private static final Pattern TITLE = Pattern.compile("^\\s*(\\d+)\\s*번\\s*창고\\s*$|^\\s*창고\\s*(\\d+)\\s*$");   // 49-558차: 제목 전체가 창고 이름일 때만
	private static final Pattern COMMAND = Pattern.compile("^창고\\s*(\\d+)$");
	private static final List<String> SPECIAL_ORDER = List.of("던전 창고", "엔더 창고", "귀속 보관함", "낚시 창고");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final net.minecraft.resources.Identifier TEX = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

	private final IntSetting dim = register(new IntSetting(
			"dim", "다른 창고 밝기", "창고 안에서 지금 보고 있지 않은 창고 판의 밝기입니다.", 55, 15, 100, 5).unit("%"));

	/** 창고 하나: 칸(상자 몫) 그대로, 줄 수. */
	private static final class Box {
		List<ItemStack> items = new ArrayList<>();
		JsonArray raw;          // 아직 안 푼 저장본(세계가 열려야 풀 수 있다)
		int rows = 6;
	}

	/** 열쇠: 번호 창고는 "1", "2" …, 나머지는 이름("던전 창고"). */
	private static final Comparator<String> ORDER = (a, b) -> {
		boolean na = isNum(a), nb = isNum(b);
		if (na && nb) {
			return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
		}
		if (na != nb) {
			return na ? -1 : 1;
		}
		int ia = SPECIAL_ORDER.indexOf(a), ib = SPECIAL_ORDER.indexOf(b);
		if (ia >= 0 || ib >= 0) {
			return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
		}
		return a.compareTo(b);
	};

	private static boolean isNum(String k) {
		if (k == null || k.isEmpty()) {
			return false;
		}
		for (int i = 0; i < k.length(); i++) {
			if (!Character.isDigit(k.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private final TreeMap<String, Box> boxes = new TreeMap<>(ORDER);
	private final Map<String, String> names = new TreeMap<>(ORDER);
	private boolean loaded;

	private String pending;
	private long pendingAt;
	private Object lastScreen;
	private String current;
	/** 지금 화면의 창고 버튼 → 메뉴 칸 번호(목록 화면일 때). */
	private final Map<String, Integer> buttons = new LinkedHashMap<>();
	private final Map<String, ItemStack> buttonIcons = new LinkedHashMap<>();
	private boolean menuScreen;
	private int snapTicks;
	/** 49-510차: 창고 안에서 다른 창고를 누르면 /창고로 목록을 열고, 목록이 뜨면 그 창고 버튼을 대신 누른다. */
	/** 49-537차: 한 번 누른 탭(0.3초 안에 또 누르면 이름 바꾸기, 아니면 연다). */
	private String clickKey;
	private long clickAt;
	private AbstractContainerScreen<?> clickScreen;
	private String retryKey;
	private Object retryScreen;
	private int retryTick, retries;
	private String chainKey;
	private long chainAt;
	/** 49-517차: 명령어로 연 창고(열쇠, 보낸 때, 보낸 때 화면) - 안 열리면 목록을 거쳐 연다. */
	private String cmdKey;
	private long cmdAt;
	private Object cmdScreen;
	/** 49-530차: 명령어로 안 열린 창고(권한 없음 등) - 다음부터는 바로 목록을 거쳐 연다(1.5초 기다리지 않게). */
	private final java.util.Set<String> cmdFailed = new java.util.HashSet<>();
	/** 목록에서 본 창고 버튼 그림(창고 안에서도 쓰려고 지우지 않는다). */
	private final Map<String, ItemStack> savedIcons = new java.util.HashMap<>();
	private int screenTicks;
	private boolean dirty;

	private String editing;
	private String editBuf = "";
	private Object editScreen;

	public NeogulStorageModule() {
		super("neogul_storage", "창고 미리보기", ModuleCategory.SERVER, "창고를 모두 펼쳐 보기 | 창고 고르기 | 창고 이름");
		serverGroup("너굴마을");
		defaultEnabled(true);
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
		try {
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (screen instanceof AbstractContainerScreen<?>) {
					net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen)
						.register((s, e) -> !onClick(s, e.x(), e.y(), e.button()));
					net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.beforeMouseClick(screen)
						.register((s, e) -> noteButtonClick(s));
				}
			});
			net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.COMMAND.register(cmd -> {
				Matcher m = COMMAND.matcher(cmd == null ? "" : cmd.trim());
				if (m.find()) {
					pending = m.group(1);
					pendingAt = System.currentTimeMillis();
				}
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulStorage:init", t);
		}
	}

	private boolean active() {
		return isEnabled() && client != null && client.player != null && NeogulData.onNeogul(client);
	}

	// ==================== 창고 알아내기 + 기억 ====================

	private static String plain(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000)).forEach(sb::appendCodePoint);
		return sb.toString().trim();
	}

	/** 버튼 아이템 이름 → 창고 열쇠(아니면 null). */
	private static String keyOf(ItemStack st) {
		if (st == null || st.isEmpty()) {
			return null;
		}
		String n = plain(st.getHoverName().getString());
		Matcher m = NUMBERED.matcher(n.replaceAll("[^가-힣A-Za-z0-9 ]", " ").replaceAll("\\s+", " ").trim());
		if (m.find()) {
			return m.group(1);
		}
		// 49-516차(사용자: "던전, 낚시, 엔더 등 4개의 창고가 안 보이잖아"): 버튼 이름 앞에 리소스팩 글꼴 아이콘(개인 영역 밖 글자)이 붙어
		// "^...창고$"에 안 맞았다 - 한글, 영문, 숫자, 띄어쓰기만 남기고 맞춘다.
		Matcher s = SPECIAL.matcher(n.replaceAll("[^가-힣A-Za-z0-9 ]", " ").replaceAll("\\s+", " ").trim());
		if (s.find()) {
			return s.group(1).replaceAll("\\s+", " ").trim();
		}
		return null;
	}

	/** 마우스 아래 칸이 창고 버튼이면 다음에 열리는 창이 그 창고다. */
	private void noteButtonClick(Screen s) {
		if (!active() || !(s instanceof kr.lunaslight.mod.mixin.ContainerScreenAccessorMixin acc)) {
			return;
		}
		try {
			Slot sl = acc.lunaslight$hoveredSlot();
			if (sl == null || sl.container == client.player.getInventory()) {
				return;
			}
			String k = keyOf(sl.getItem());
			if (k != null && menuScreen) {   // 49-558차: 창고 목록에서 누른 것만(다른 창의 비슷한 이름 아이템은 무시)
				pending = k;
				pendingAt = System.currentTimeMillis();
			}
		} catch (Throwable ignored) {
		}
	}

	/** 창(상자 몫)에 든 창고 버튼 수. */
	private static int buttonCount(List<ItemStack> items) {
		int c = 0;
		for (ItemStack st : items) {
			if (keyOf(st) != null) {
				c++;
			}
		}
		return c;
	}

	@Override
	public void onTick() {
		if (!active()) {
			current = null;
			buttons.clear();
			menuScreen = false;
			return;
		}
		ensureLoaded();
		Object screen = LunaCompat.screenOf(client);
		if (switchUntil != 0L) {
			if (screen != switchScreen && screen != null) {
				switchUntil = 0L;   // 새 창이 왔다
			} else if (System.currentTimeMillis() >= switchUntil) {
				switchUntil = 0L;
				if (closeHeld && screen == switchScreen && client.player != null) {
					client.player.closeContainer();   // 서버가 닫았는데 새 창이 안 왔다 - 미뤘던 닫기
				}
			}
		}
		boolean changed = screen != lastScreen;
		if (changed) {
			lastScreen = screen;
			current = null;
			screenTicks = 0;
			if (editing != null && screen != editScreen) {
				commitEdit();
			}
		}
		buttons.clear();
		buttonIcons.clear();
		menuScreen = false;
		if (!(screen instanceof AbstractContainerScreen<?> hs) || screen instanceof InventoryScreen) {
			if (dirty) {
				save();
			}
			return;
		}
		var slots = hs.getMenu().slots;
		int n = Math.max(0, slots.size() - 36);
		for (int i = 0; i < n; i++) {
			ItemStack st = slots.get(i).getItem();
			String k = keyOf(st);
			if (k != null && !buttons.containsKey(k)) {
				buttons.put(k, i);
				buttonIcons.put(k, st.copy());
				savedIcons.put(k, st.copy());
			}
		}
		// 버튼이 2개 이상이면 목록 화면 - 창고로 적지 않는다(잠긴 창고를 누르면 서버가 목록을 다시 연다)
		// 49-558차: 창고 목록은 버튼이 14개(1~10번 + 특수 4) - 다른 창(섬 업그레이드 등)에 창고 이름 아이템이 몇 개 있어도 목록이 아니다
		menuScreen = buttons.size() >= 5;
		screenTicks++;
		if (clickKey != null && System.currentTimeMillis() - clickAt >= 300) {
			String k = clickKey;
			clickKey = null;
			if (clickScreen != null && screen == clickScreen && !k.equals(current)) {
				open(clickScreen, k);
			}
			clickScreen = null;
		}
		if (cmdKey != null) {
			if (screen != cmdScreen) {
				// 새 창이 떴다: 목록이면(명령어 권한 없음 등) 그 버튼을 대신 누르고, 창고면 끝.
				// 49-549차(사용자 사진: "다른 창고 누르면 이 화면 됨" - 목록에서 멈춤): 창이 뜬 첫 틱에는 칸이 아직 비어 있어 목록인지 모른다 -
				// 그때 "창고구나" 하고 끝내 버려서 목록에서 멈췄다. 목록으로 보이거나 4틱이 지날 때까지 기다린다.
				if (menuScreen) {
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 창고: 명령어가 목록을 열어 {} 버튼으로", cmdKey);
					chainKey = cmdKey;
					chainAt = System.currentTimeMillis();
					cmdFailed.add(cmdKey);
					cmdKey = null;
				} else if (screenTicks >= 4) {
					cmdKey = null;
				}
			} else if (System.currentTimeMillis() - cmdAt > 1500) {
				// 1.5초 안에 아무 창도 안 떴다(없는 명령어, 권한 없음) - 목록을 열어 버튼으로
				chainKey = cmdKey;
				chainAt = System.currentTimeMillis();
				cmdFailed.add(cmdKey);
				cmdKey = null;
				LunaCompat.sendChatOrCommand(client, "/창고");
			}
		}
		if (chainKey != null && System.currentTimeMillis() - chainAt > 6000) {
			chainKey = null;   // 목록이 안 떴다
		}
		if (menuScreen) {
			current = null;
			for (String k : buttons.keySet()) {
				boxes.computeIfAbsent(k, x -> new Box());
			}
			// 목록이 뜨고 칸이 다 들어온 뒤(2틱) 기다리던 창고 버튼을 누른다
			// 49-537차(사용자: "다른 창고 누르면 사운드 바바박 나면서 안 가지고 메인 창고 화면으로"): 1틱은 너무 일러 서버가 무시했다 - 3틱 뒤에 누른다(그동안 가림 판)
			if (chainKey != null && screenTicks >= 3 && client.gameMode != null) {
				Integer slot = buttons.get(chainKey);
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 창고: 목록에서 {} 누름(칸 {}, 버튼 {})", chainKey, slot, buttons.keySet());
				if (slot != null) {
					pending = chainKey;
					pendingAt = System.currentTimeMillis();
					beginSwitch();
					client.gameMode.handleContainerInput(hs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
					retryKey = chainKey;
					retryScreen = screen;
					retryTick = screenTicks;
					retries = 0;
				}
				chainKey = null;
			} else if (retryKey != null && screen == retryScreen && screenTicks - retryTick >= 15) {
				// 눌렀는데 0.75초가 지나도 목록 그대로면 한 번 더(최대 2번)
				Integer slot = buttons.get(retryKey);
				if (slot != null && retries < 2 && client.gameMode != null) {
					retries++;
					kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 창고: {} 다시 누름({})", retryKey, retries);
					retryTick = screenTicks;
					pending = retryKey;
					pendingAt = System.currentTimeMillis();
					client.gameMode.handleContainerInput(hs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
				} else {
					retryKey = null;
				}
			}
		} else if (changed) {
			retryKey = null;
			if (pending != null && System.currentTimeMillis() - pendingAt < 4000) {
				current = pending;
			} else {
				Matcher m = TITLE.matcher(plain(hs.getTitle().getString()));
				if (m.find()) {
					current = m.group(1) != null ? m.group(1) : m.group(2);
				}
			}
			pending = null;
		}
		// 보고 있는 창고: 칸을 그대로 적어 둔다(상자 몫이 9의 배수일 때)
		if (current != null && !menuScreen && screenTicks >= 3 && cmdKey == null && n >= 9 && n <= 54 && n % 9 == 0) {   // 49-549차: 칸이 다 들어온 뒤에만(목록을 빈 창고로 적지 않게)
			Box b = boxes.computeIfAbsent(current, x -> new Box());
			List<ItemStack> now = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				now.add(slots.get(i).getItem().copy());
			}
			if (b.raw != null || b.rows != n / 9 || !sameStacks(b.items, now)) {
				b.items = now;
				b.raw = null;
				b.rows = n / 9;
				dirty = true;
			}
		}
		if (dirty && ++snapTicks % 100 == 0) {
			save();
		}
	}

	private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!ItemStack.matches(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}

	// ==================== 저장 ====================

	private Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-storage.json");
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
			if (o.has("names")) {
				for (var e : o.getAsJsonObject("names").entrySet()) {
					names.put(e.getKey(), e.getValue().getAsString());
				}
			}
			if (o.has("boxes")) {
				for (var e : o.getAsJsonObject("boxes").entrySet()) {
					JsonObject bo = e.getValue().getAsJsonObject();
					Box b = new Box();
					b.rows = bo.has("rows") ? bo.get("rows").getAsInt() : 6;
					b.raw = bo.has("items") ? bo.getAsJsonArray("items") : null;
					boxes.put(e.getKey(), b);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulStorage:load", t);
		}
	}

	private com.mojang.serialization.DynamicOps<JsonElement> ops() {
		return client.level == null ? null : client.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
	}

	/** 저장본을 아이템으로 푼다(세계가 열려 있어야 한다). 목록이 잘못 적힌 것이면 비운다. */
	private List<ItemStack> itemsOf(Box b) {
		if (b.raw != null) {
			var ops = ops();
			if (ops == null) {
				return b.items;
			}
			List<ItemStack> out = new ArrayList<>();
			for (JsonElement je : b.raw) {
				ItemStack st = ItemStack.EMPTY;
				try {
					if (je != null && !je.isJsonNull()) {
						st = ItemStack.OPTIONAL_CODEC.parse(ops, je).result().orElse(ItemStack.EMPTY);
					}
				} catch (Throwable ignored) {
				}
				out.add(st);
			}
			b.items = out;
			b.raw = null;
		}
		if (!b.items.isEmpty() && buttonCount(b.items) >= 2) {
			b.items = new ArrayList<>();   // 49-508차: 예전에 창고 목록이 창고로 잘못 적힌 것
			dirty = true;
		}
		return b.items;
	}

	private void save() {
		dirty = false;
		try {
			var ops = ops();
			JsonObject o = new JsonObject();
			JsonObject nm = new JsonObject();
			for (var e : names.entrySet()) {
				nm.addProperty(e.getKey(), e.getValue());
			}
			o.add("names", nm);
			JsonObject bx = new JsonObject();
			for (var e : boxes.entrySet()) {
				Box b = e.getValue();
				JsonObject bo = new JsonObject();
				bo.addProperty("rows", b.rows);
				JsonArray arr = b.raw;
				if (arr == null && ops != null) {
					arr = new JsonArray();
					for (ItemStack st : b.items) {
						JsonElement je = null;
						try {
							je = ItemStack.OPTIONAL_CODEC.encodeStart(ops, st).result().orElse(null);
						} catch (Throwable ignored) {
						}
						arr.add(je == null ? com.google.gson.JsonNull.INSTANCE : je);
					}
				}
				if (arr != null) {
					bo.add("items", arr);
				}
				bx.add(e.getKey(), bo);
			}
			o.add("boxes", bx);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulStorage:save", t);
		}
	}

	private String nameOf(String k) {
		String n = names.get(k);
		if (n != null && !n.isBlank()) {
			return n;
		}
		return isNum(k) ? k + "번 창고" : k;
	}

	// ==================== 배치 ====================

	/** 판 하나: 상자 그림을 k배로 줄여 그린다(k = cell / 18). */
	private record Panel(String key, int x, int y, int w, int h, float k, int rows) {
	}

	private static int panelW(float k) {
		return Math.round(176 * k);
	}

	private static int panelH(float k, int rows) {
		return Math.round((rows * 18 + 24) * k);
	}

	private int rowsOf(String key) {
		Box b = boxes.get(key);
		return b == null ? 6 : Math.max(1, Math.min(6, b.rows));
	}

	private int[] guiBox(AbstractContainerScreen<?> hs) {
		if (hs instanceof kr.lunaslight.mod.mixin.ContainerScreenAccessorMixin acc) {
			return new int[]{acc.lunaslight$leftPos(), acc.lunaslight$topPos(), acc.lunaslight$imageWidth(), acc.lunaslight$imageHeight()};
		}
		return new int[]{(hs.width - 176) / 2, (hs.height - 166) / 2, 176, 166};
	}

	/** 내 인벤토리 칸이 시작하는 화면 y(목록 화면에서 서버 창을 가릴 아래 끝). */
	private int invTop(AbstractContainerScreen<?> hs, int[] box) {
		try {
			var slots = hs.getMenu().slots;
			for (Slot s : slots) {
				if (s.container == client.player.getInventory()) {
					return box[1] + s.y - 14;
				}
			}
		} catch (Throwable ignored) {
		}
		return box[1] + box[3] - 96;
	}

	/** 가리는 판(목록 화면): {x1, y1, x2, y2}. */
	private int[] cover;
	/** 가린 자리와 판 자리(StorageTooltipMixin이 그 아래 서버 칸 설명을 숨긴다). */
	private static volatile List<int[]> blocked = List.of();

	/** 이 자리가 가린 판이나 창고 판 위인지(그 아래 칸 설명을 그리지 않게). */
	public static boolean blocks(int x, int y) {
		for (int[] r : blocked) {
			if (x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) {
				return true;
			}
		}
		return false;
	}

	private List<Panel> layout(AbstractContainerScreen<?> hs) {
		cover = null;
		List<Panel> out = new ArrayList<>();
		if (boxes.isEmpty()) {
			return out;
		}
		// 49-530차(사용자 사진: 창고 안 왼쪽 이름 탭 - "창고 미리보기 처음부터 이런 느낌으로"): 창고 목록 화면도 같은 이름 탭(가운데 큰 판 격자 X).
		return layoutSide(hs, out);
	}

	/** 목록 화면: 인벤 위쪽 서버 창을 가리고 가운데에 번호 창고, 인벤 양쪽에 나머지를 2개씩. */
	private List<Panel> layoutPicker(AbstractContainerScreen<?> hs, List<Panel> out) {
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int[] box = guiBox(hs);
		int top = invTop(hs, box);
		List<String> nums = new ArrayList<>(), others = new ArrayList<>();
		for (String k : boxes.keySet()) {
			if (isNum(k)) {
				if (buttons.containsKey(k) || boxes.get(k).raw != null || !boxes.get(k).items.isEmpty()) {
					nums.add(k);
				}
			} else if (buttons.containsKey(k)) {
				others.add(k);
			}
		}
		int gap = 4;
		int availW = sw - 16, availH = top - 10;
		// 가운데: 번호 창고 - 다 들어가는 가장 큰 크기(한 줄 5개 기준)
		if (!nums.isEmpty()) {
			int n = nums.size();
			float best = 0;
			int bestCols = 1;
			for (int cols = Math.min(n, 6); cols >= 1; cols--) {
				int rowsN = (n + cols - 1) / cols;
				for (float k = 1.0f; k >= 0.30f; k -= 0.02f) {
					int pw = panelW(k), ph = panelH(k, 6);
					if (cols * pw + (cols - 1) * gap <= availW && rowsN * ph + (rowsN - 1) * gap <= availH) {
						if (k > best) {
							best = k;
							bestCols = cols;
						}
						break;
					}
				}
			}
			if (best == 0) {
				best = 0.3f;
				bestCols = Math.min(n, 6);
			}
			float k = best;
			int cols = bestCols;
			int rowsN = (n + cols - 1) / cols;
			int pw = panelW(k), ph = panelH(k, 6);
			int gw = cols * pw + (cols - 1) * gap, gh = rowsN * ph + (rowsN - 1) * gap;
			int x0 = (sw - gw) / 2, y0 = Math.max(6, (top - gh) / 2);
			for (int i = 0; i < n; i++) {
				int c = i % cols, r = i / cols;
				// 마지막 줄은 가운데 정렬
				int inRow = Math.min(cols, n - r * cols);
				int rowX = x0 + (gw - (inRow * pw + (inRow - 1) * gap)) / 2;
				int rows = rowsOf(nums.get(i));
				out.add(new Panel(nums.get(i), rowX + c * (pw + gap), y0 + r * (ph + gap), pw, panelH(k, rows), k, rows));
			}
			cover = new int[]{Math.min(x0, box[0]) - 4, Math.min(y0, box[1]) - 4, Math.max(x0 + gw, box[0] + box[2]) + 4, Math.max(y0 + gh, top) + 2};
		} else {
			cover = new int[]{box[0] - 4, box[1] - 4, box[0] + box[2] + 4, top + 2};
		}
		// 인벤 양쪽: 나머지(던전 창고 …) 2개씩 나란히.
		// 49-517차(사용자 사진: 4개가 왼쪽 번호 창고 위에 겹쳐 뜸, "위치가 인벤 양쪽에 있어야지"): 너굴 아이템 목록이 켜져 있어도
		// 왼쪽 2개, 오른쪽 2개(오른쪽은 목록 왼쪽 끝까지만). 가린 판 아래, 인벤 높이에 맞춘다. 한쪽에 자리가 없으면 다른 쪽으로.
		if (!others.isEmpty()) {
			int coverBottom = cover != null ? cover[3] : top;
			int yMin = coverBottom + 4, yMax = sh - 6;
			int invMid = (Math.max(yMin, top) + box[1] + box[3]) / 2;
			int rightEnd = sw - 6;
			try {
				int jl = kr.lunaslight.mod.util.NeogulJei.on() ? kr.lunaslight.mod.util.NeogulJei.listLeft() : -1;
				if (jl > 0) {
					rightEnd = jl - 6;
				}
			} catch (Throwable ignored) {
			}
			int leftEnd = box[0] - 6, leftStart = 6;
			int rightStart = box[0] + box[2] + 6;
			List<String> left = new ArrayList<>(), right = new ArrayList<>();
			for (int i = 0; i < others.size(); i++) {
				(i % 4 < 2 ? left : right).add(others.get(i));
			}
			if (rightEnd - rightStart < panelW(0.3f) * 2 + gap) {
				left.addAll(right);
				right.clear();
			}
			for (int side = 0; side < 2; side++) {
				List<String> list = side == 0 ? left : right;
				if (list.isEmpty()) {
					continue;
				}
				int sideW = side == 0 ? leftEnd - leftStart : rightEnd - rightStart;
				int cols = Math.min(2, list.size());
				int rowsN = (list.size() + cols - 1) / cols;
				float k = 0.3f;
				for (float t = 0.9f; t >= 0.30f; t -= 0.02f) {
					if (cols * panelW(t) + (cols - 1) * gap <= sideW && rowsN * panelH(t, 6) + (rowsN - 1) * gap <= yMax - yMin) {
						k = t;
						break;
					}
				}
				int pw = panelW(k), ph = panelH(k, 6);
				int gw = cols * pw + (cols - 1) * gap, gh = rowsN * ph + (rowsN - 1) * gap;
				int x0 = side == 0 ? leftEnd - gw : rightStart;
				int y0 = Math.max(yMin, Math.min(yMax - gh, invMid - gh / 2));
				for (int i = 0; i < list.size(); i++) {
					int c = i % cols, r = i / cols;
					int rows = rowsOf(list.get(i));
					out.add(new Panel(list.get(i), x0 + c * (pw + gap), y0 + r * (ph + gap), pw, panelH(k, rows), k, rows));
				}
			}
		}
		return out;
	}

	/** 탭 한 줄 높이. */
	private static final int TAB_H = 16;

	/**
	 * 창고 안. 49-517차(사용자: "상자를 열면 왼쪽에 이상하게 나오고"): 예전엔 GUI 옆에 모든 창고 판을 줄여 깔았다(작고 너굴 아이템 목록과 겹침).
	 * 이제 GUI 왼쪽에 창고 이름 탭(그림 + 이름)을 세로로 - 지금 창고는 강조, 마우스를 올리면 그 창고 판을 탭 옆에 크게 보여 주고, 누르면 명령어로 연다.
	 * 탭은 k = 0인 판으로 나타낸다.
	 */
	private List<Panel> layoutSide(AbstractContainerScreen<?> hs, List<Panel> out) {
		int sh = client.getWindow().getGuiScaledHeight();
		int[] box = guiBox(hs);
		List<String> keys = new ArrayList<>(boxes.keySet());
		if (keys.isEmpty()) {
			return out;
		}
		int w = 0;
		for (String k : keys) {
			w = Math.max(w, LunaCompat.getTextWidth(client.font, nameOf(k)));
		}
		w = Math.min(110, w + 26);
		int gap = 2;
		int perCol = Math.max(1, (sh - 12 + gap) / (TAB_H + gap));
		int cols = (keys.size() + perCol - 1) / perCol;
		int rows = Math.min(perCol, keys.size());
		int gh = rows * (TAB_H + gap) - gap;
		int y0 = Math.max(6, box[1] + Math.min(box[3], gh) / 2 - gh / 2 + (box[3] > gh ? (box[3] - gh) / 2 : 0));
		y0 = Math.max(6, Math.min(sh - 6 - gh, y0));
		for (int i = 0; i < keys.size(); i++) {
			int c = i / perCol, r = i % perCol;
			int x = box[0] - 6 - (cols - c) * (w + 4) + 4;
			out.add(new Panel(keys.get(i), x, y0 + r * (TAB_H + gap), w, TAB_H, 0f, 0));
		}
		return out;
	}

	/** 탭 위에 마우스를 올렸을 때 크게 보여 줄 판 자리(탭 왼쪽, 자리가 없으면 오른쪽). */
	private Panel hoverPanel(Panel tab, AbstractContainerScreen<?> hs) {
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int rows = rowsOf(tab.key());
		int room = tab.x() - 10;
		float k = Math.min(0.9f, room / 176f);
		int x;
		if (k >= 0.5f) {
			x = tab.x() - 4 - panelW(k);
		} else {
			k = Math.min(0.9f, (sw - tab.x() - tab.w() - 10) / 176f);
			x = tab.x() + tab.w() + 4;
		}
		k = Math.max(0.3f, k);
		int h = panelH(k, rows);
		int y = Math.max(6, Math.min(sh - 6 - h, tab.y() + TAB_H / 2 - h / 2));
		return new Panel(tab.key(), x, y, panelW(k), h, k, rows);
	}

	// ==================== 그리기 ====================

	private boolean shows() {
		return current != null || menuScreen;
	}

	/** 창고 판 하나(상자 그림, 제목, 버튼 그림, 아이템). 마우스 아래 아이템을 돌려준다. */
	private ItemStack drawPanel(GuiGraphicsExtractor ctx, Panel p, int mouseX, int mouseY, boolean cur, boolean hov, int accent,
			List<int[]> dims, int dimA) {
		ItemStack hoverStack = ItemStack.EMPTY;
		Box b = boxes.get(p.key());
		List<ItemStack> items = b == null ? List.of() : itemsOf(b);
		float k = p.k();
		int rows = p.rows();
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, p.x(), p.y());
			LunaCompat.guiScale(ctx, k, k);
			// 바닐라 상자 그림(리소스팩): 위(제목 + 칸) + 아래 테두리 7px
			ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEX, 0, 0, 0f, 0f, 176, rows * 18 + 17, 256, 256);
			ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEX, 0, rows * 18 + 17, 0f, 215f, 176, 7, 256, 256);
			String title = p.key().equals(editing) ? editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : nameOf(p.key());
			ctx.text(client.font, LunaCompat.textLiteral(LunaDraw.ellipsize(client.font, title, 176 - 30)), 8, 6,
				p.key().equals(editing) ? 0xFF1A5FB4 : 0xFF404040, false);
			ItemStack icon = buttonIcons.containsKey(p.key()) ? buttonIcons.get(p.key()) : savedIcons.get(p.key());
			if (icon != null) {
				LunaCompat.guiPush(ctx);
				LunaCompat.guiTranslate(ctx, 176 - 18, 2);
				LunaCompat.guiScale(ctx, 0.75f, 0.75f);
				ctx.item(icon, 0, 0);
				LunaCompat.guiPop(ctx);
			}
			if (items.isEmpty()) {
				LunaDraw.textCentered(ctx, client.font, "열어 보면 보여요", 88, 18 + rows * 9 - 4, 0xFF404040);
			} else {
				for (int i = 0; i < items.size() && i < rows * 9; i++) {
					ItemStack st = items.get(i);
					if (st.isEmpty()) {
						continue;
					}
					int ix = 8 + (i % 9) * 18, iy = 18 + (i / 9) * 18;
					ctx.item(st, ix, iy);
					LunaCompat.drawItemOverlay(ctx, client.font, st, ix, iy);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulStorage:draw", t);
		} finally {
			LunaCompat.guiPop(ctx);
		}
		if (hov && !items.isEmpty()) {
			int c = Math.round(18 * k);
			int gx = p.x() + Math.round(8 * k), gy = p.y() + Math.round(18 * k);
			int col = (mouseX - gx) / Math.max(1, c), row = (mouseY - gy) / Math.max(1, c);
			int i = row * 9 + col;
			if (mouseX >= gx && mouseY >= gy && col < 9 && row < rows && i < items.size() && !items.get(i).isEmpty()) {
				hoverStack = items.get(i);
			}
		}
		if (cur) {
			LunaDraw.roundRectOutline(ctx, p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, 0, 0xFF000000 | (accent & 0xFFFFFF));
		} else if (hov) {
			LunaDraw.roundRectOutline(ctx, p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, 0, 0xFFFFFFFF);
		}
		if (!cur && !hov && dimA > 0) {
			dims.add(new int[]{p.x() + 2, p.y() + 2, p.w() - 4, p.h() - 4});
		}
		return hoverStack;
	}

	/** 49-517차: 창고 안 왼쪽 탭(버튼 그림 + 이름). */
	private void drawTab(GuiGraphicsExtractor ctx, Panel p, boolean cur, boolean hov, int accent) {
		ctx.fill(p.x(), p.y(), p.x() + p.w(), p.y() + p.h(), cur ? 0xE0202634 : hov ? 0xE0262A33 : 0xD0101216);
		int line = cur ? (0xFF000000 | (accent & 0xFFFFFF)) : hov ? 0xFFFFFFFF : 0x50FFFFFF;
		LunaDraw.roundRectOutline(ctx, p.x(), p.y(), p.w(), p.h(), 0, line);
		ItemStack icon = buttonIcons.containsKey(p.key()) ? buttonIcons.get(p.key()) : savedIcons.get(p.key());
		int tx = p.x() + 5;
		if (icon != null) {
			LunaCompat.guiPush(ctx);
			try {
				LunaCompat.guiTranslate(ctx, p.x() + 2, p.y() + 2);
				LunaCompat.guiScale(ctx, 0.75f, 0.75f);
				ctx.item(icon, 0, 0);
			} finally {
				LunaCompat.guiPop(ctx);
			}
			tx = p.x() + 18;
		}
		String title = p.key().equals(editing) ? editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : nameOf(p.key());
		LunaDraw.text(ctx, client.font, LunaDraw.ellipsize(client.font, title, p.x() + p.w() - 4 - tx), tx, p.y() + 4,
			cur ? 0xFFFFFFFF : 0xFFD0D4DC);
	}

	/** 49-531차: 마지막으로 탭을 그린 때 - 창고를 바꾸는 동안(새 창이 떴는데 아직 틱 전) 탭이 한 번 사라졌다 나타나는 깜빡임을 막는다. */
	private long lastShowMs;

	private void onScreenFrame(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		boolean sticky = System.currentTimeMillis() - lastShowMs < 700 || (pending != null && System.currentTimeMillis() - pendingAt < 4000);
		if (!active() || !(screen instanceof AbstractContainerScreen<?> hs) || screen instanceof InventoryScreen || !(shows() || sticky)) {
			blocked = List.of();
			return;
		}
		if (shows()) {
			lastShowMs = System.currentTimeMillis();   // 진짜 창고/목록일 때만 갱신(이어 붙기만으로는 늘리지 않음)
		}
		List<Panel> panels = layout(hs);
		List<int[]> bl = new ArrayList<>();
		if (cover != null) {
			bl.add(cover);
		}
		for (Panel p : panels) {
			bl.add(new int[]{p.x(), p.y(), p.x() + p.w(), p.y() + p.h()});
		}
		// 49-530차(사용자: "누르면 바로 그 상자로 안 가지고 중간 화면으로 가잖아"): 목록을 거쳐 열 때(권한이 없어 명령어로 못 여는 창고)
		// 잠깐 뜨는 서버 목록 창을 가린다 - 그 사이 버튼을 대신 눌러 바로 그 창고가 열린다.
		if (menuScreen && chainKey != null) {
			int[] gb = guiBox(hs);
			try {
				ctx.nextStratum();
			} catch (Throwable ignored) {
			}
			ctx.fill(gb[0] - 2, gb[1] - 2, gb[0] + gb[2] + 2, gb[1] + gb[3] + 2, 0xF0101216);
			LunaDraw.textCentered(ctx, client.font, nameOf(chainKey) + " 여는 중…", gb[0] + gb[2] / 2, gb[1] + gb[3] / 2 - 4, 0xFFE8EAF0);
			bl.add(new int[]{gb[0] - 2, gb[1] - 2, gb[0] + gb[2] + 2, gb[1] + gb[3] + 2});
		}
		blocked = bl;
		if (panels.isEmpty()) {
			return;
		}
		try {
			ctx.nextStratum();   // 서버 창, 툴팁보다 위
		} catch (Throwable ignored) {
		}
		if (cover != null) {
			// 목록 화면: 서버 창고 창을 가린다(인벤은 그대로)
			ctx.fill(cover[0], cover[1], cover[2], cover[3], 0xE0101216);
			LunaDraw.roundRectOutline(ctx, cover[0], cover[1], cover[2] - cover[0], cover[3] - cover[1], 0, 0x50FFFFFF);
		}
		ItemStack hoverStack = ItemStack.EMPTY;
		List<int[]> dims = new ArrayList<>();
		int dimA = menuScreen ? 0 : Math.round((100 - Math.max(15, Math.min(100, dim.get()))) * 2.55f);
		int accent = LunaDraw.ACCENT;
		Panel hoverTab = null;
		for (Panel p : panels) {
			boolean cur = p.key().equals(current);
			boolean hov = LunaDraw.in(mouseX, mouseY, p.x(), p.y(), p.w(), p.h());
			if (p.k() == 0f) {
				drawTab(ctx, p, cur, hov, accent);
				if (hov) {
					hoverTab = p;
				}
				continue;
			}
			ItemStack hs0 = drawPanel(ctx, p, mouseX, mouseY, cur, hov, accent, dims, dimA);
			if (!hs0.isEmpty()) {
				hoverStack = hs0;
			}
		}
		if (hoverTab != null && screen instanceof AbstractContainerScreen<?> hsc) {
			Panel big = hoverPanel(hoverTab, hsc);
			try {
				ctx.nextStratum();   // 창고 칸 위
			} catch (Throwable ignored) {
			}
			drawPanel(ctx, big, mouseX, mouseY, big.key().equals(current), false, accent, new ArrayList<>(), 0);
			List<int[]> bl2 = new ArrayList<>(blocked);
			bl2.add(new int[]{big.x(), big.y(), big.x() + big.w(), big.y() + big.h()});
			blocked = bl2;
		}
		if (!dims.isEmpty()) {
			try {
				ctx.nextStratum();
			} catch (Throwable ignored) {
			}
			for (int[] d : dims) {
				ctx.fill(d[0], d[1], d[0] + d[2], d[1] + d[3], dimA << 24);
			}
		}
		if (!hoverStack.isEmpty()) {
			ctx.setTooltipForNextFrame(client.font, hoverStack, mouseX, mouseY);
		}
	}

	// ==================== 입력 ====================

	/** 판 클릭. 처리했으면 true(게임에 안 넘김). */
	private boolean onClick(Screen s, double mx, double my, int button) {
		if (!active() || !(s instanceof AbstractContainerScreen<?> hs) || s instanceof InventoryScreen || !shows()) {
			return false;
		}
		List<Panel> panels = layout(hs);
		for (Panel p : panels) {
			if (!LunaDraw.in(mx, my, p.x(), p.y(), p.w(), p.h())) {
				continue;
			}
			boolean rightClick = button == 1 || button == kr.lunaslight.mod.util.LunaInput.fromGlfwButton(1);
			if (rightClick) {
				startEdit(p.key(), s);
				return true;
			}
			if (editing != null) {
				commitEdit();
			}
			// 49-571차(사용자: "클릭 2번 이름 바꾸기에서 우클릭으로 바꾸기"): 이름 바꾸기는 우클릭만 - 좌클릭은 기다리지 않고 바로 연다
			if (!p.key().equals(current)) {
				open(hs, p.key());
			}
			return true;
		}
		if (editing != null) {
			commitEdit();
		}
		// 가린 자리(서버 창)는 눌러도 아무 일 없게
		return cover != null && mx >= cover[0] && mx < cover[2] && my >= cover[1] && my < cover[3];
	}

	/**
	 * 49-571차(사용자: "창고 넘어갈 때 중간 화면 안 뜨고 부드럽게, 소리 2번 나는 거"): 창고를 바꾸는 동안 서버가 보내는 "창 닫기"를 받지 않고
	 * (NeogulContainerCloseMixin) 지금 창을 띄운 채로 다음 창을 기다린다 - 그 사이 월드가 비치거나 마우스가 가운데로 튀지 않는다.
	 * 겹치는 소리는 한 번만(SoundFilterHook.switchSounds). 2.5초 안에 새 창이 안 오면 닫기를 그대로 한다.
	 */
	public static volatile long switchUntil;
	private static volatile boolean closeHeld;
	private Object switchScreen;

	public static boolean holdClose() {
		if (switchUntil != 0L && System.currentTimeMillis() < switchUntil) {
			closeHeld = true;
			return true;
		}
		return false;
	}

	private void beginSwitch() {
		switchUntil = System.currentTimeMillis() + 2500L;
		closeHeld = false;
		switchScreen = LunaCompat.screenOf(client);
		kr.lunaslight.mod.util.SoundFilterHook.switchSounds(2000L);
	}

	private void open(AbstractContainerScreen<?> hs, String key) {
		beginSwitch();
		Integer slot = buttons.get(key);
		if (slot != null && client.gameMode != null) {
			pending = key;
			pendingAt = System.currentTimeMillis();
			client.gameMode.handleContainerInput(hs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
			return;
		}
		// 49-517차(사용자: "다른 창고 갈 때 사운드 이상하게 나오고, 그냥 창고1~10 엔더상자 던전상자 이런 식으로 명령어로 열어 봐"):
		// 창고 안에서는 목록을 거치지 않고 명령어로 바로 연다(목록 열고 닫는 소리가 겹치지 않게). 1.5초 안에 안 열리거나
		// 목록이 뜨면(권한 없음) 49-510차처럼 목록 버튼을 대신 누른다.
		String cmd = commandOf(key);
		pending = key;
		pendingAt = System.currentTimeMillis();
		if (cmd != null && !cmdFailed.contains(key)) {
			cmdKey = key;
			cmdAt = System.currentTimeMillis();
			cmdScreen = LunaCompat.screenOf(client);
			kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 창고: {} 명령어 {}", key, cmd);
			LunaCompat.sendChatOrCommand(client, cmd);
			return;
		}
		chainKey = key;
		chainAt = System.currentTimeMillis();
		LunaCompat.sendChatOrCommand(client, "/창고");
	}

	/** 창고 → 여는 명령어(모르면 null). */
	private static String commandOf(String key) {
		if (isNum(key)) {
			return "/창고" + key;
		}
		String k = key.replace(" ", "");
		if (k.startsWith("던전")) {
			return "/던전상자";
		}
		if (k.startsWith("엔더")) {
			return "/엔더상자";
		}
		if (k.startsWith("귀속")) {
			return "/귀속보관함";
		}
		if (k.startsWith("낚시")) {
			return "/낚시창고";
		}
		if (k.startsWith("광물")) {
			return "/광물창고";
		}
		return null;
	}

	private final TextCapture.Target editTarget = new TextCapture.Target() {
		@Override
		public boolean wants() {
			return editing != null && client != null && LunaCompat.screenOf(client) == editScreen;
		}

		@Override
		public void onChar(int codePoint) {
			if (editBuf.codePointCount(0, editBuf.length()) < 20 && codePoint >= 32) {
				editBuf += new String(Character.toChars(codePoint));
			}
		}

		@Override
		public boolean onKey(int key, int action, int modifiers) {
			if (action == 0) {
				return true;
			}
			if (key == InputConstants.KEY_BACKSPACE && !editBuf.isEmpty()) {
				int cut = editBuf.offsetByCodePoints(editBuf.length(), -1);
				editBuf = editBuf.substring(0, cut);
			} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				commitEdit();
			} else if (key == InputConstants.KEY_ESCAPE) {
				editing = null;
			}
			return true;   // E 같은 글자 키로 창이 닫히지 않게
		}
	};

	private void startEdit(String key, Object screen) {
		if (editing != null) {
			commitEdit();
		}
		editing = key;
		editBuf = names.getOrDefault(key, "");
		editScreen = screen;
		TextCapture.setTarget(editTarget);
	}

	private void commitEdit() {
		if (editing == null) {
			return;
		}
		String v = editBuf.trim();
		if (v.isEmpty()) {
			names.remove(editing);
		} else {
			names.put(editing, v);
		}
		editing = null;
		save();
	}
}
