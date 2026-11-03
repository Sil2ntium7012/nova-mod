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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 49-502차(사용자: "너굴마을 창고 열면 창고 모두 미리보기 가능한 기능, 너굴마을 카테고리에 창고. 하이픽셀 모드 보면 상자 쫙 펼쳐서 본인이 보고
 * 있는 것만 조금 더 밝고 상자별 이름 정하게 할 수 있는 게 있어"): <b>창고 미리보기</b>.
 *
 * <ul>
 *   <li>창고를 열면(목록의 "N번 창고" 버튼, /창고N, 창 제목) 그 창고의 칸을 기억한다(config/lunaslight/neogul-storage.json - 다시 켜도 남는다).</li>
 *   <li>창고 목록이나 창고 안에서 GUI 왼쪽(너굴 아이템 목록이 꺼져 있으면 오른쪽도)에 기억한 창고를 전부 펼쳐 보인다. 지금 보고 있는 창고만 밝고 나머지는 어둡게.</li>
 *   <li>판을 누르면 그 창고로(목록이면 그 버튼을 누르고, 아니면 /창고N). 판 제목을 우클릭하면 이름을 바꾼다(Enter 저장, Esc 취소).</li>
 *   <li>판 위 아이템에 마우스를 올리면 설명.</li>
 * </ul>
 */
public class NeogulStorageModule extends Module {

	private static final Pattern BUTTON = Pattern.compile("(\\d+)\\s*번\\s*창고");
	private static final Pattern TITLE = Pattern.compile("(\\d+)\\s*번\\s*창고|창고\\s*(\\d+)");
	private static final Pattern COMMAND = Pattern.compile("^창고\\s*(\\d+)$");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final IntSetting dim = register(new IntSetting(
			"dim", "다른 창고 밝기", "지금 보고 있지 않은 창고 판의 밝기입니다.", 55, 15, 100, 5).unit("%"));

	/** 창고 하나: 칸(상자 몫) 그대로, 줄 수. */
	private static final class Box {
		List<ItemStack> items = new ArrayList<>();
		JsonArray raw;          // 아직 안 푼 저장본(세계가 열려야 풀 수 있다)
		int rows = 6;
	}

	private final TreeMap<Integer, Box> boxes = new TreeMap<>();
	private final Map<Integer, String> names = new TreeMap<>();
	private boolean loaded;

	private int pending = -1;
	private long pendingAt;
	private Object lastScreen;
	private int current = -1;
	/** 지금 화면의 "N번 창고" 버튼 → 메뉴 칸 번호. */
	private final Map<Integer, Integer> buttons = new TreeMap<>();
	private int snapTicks;
	private boolean dirty;

	private int editing = -1;
	private String editBuf = "";
	private Object editScreen;

	public NeogulStorageModule() {
		super("neogul_storage", "창고 미리보기", ModuleCategory.SERVER, "창고를 모두 펼쳐 보기 | 창고 이름");
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
					pending = Integer.parseInt(m.group(1));
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

	// ==================== 열린 창고 알아내기 + 기억 ====================

	/** 마우스 아래 칸이 "N번 창고" 버튼이면 다음에 열리는 창이 그 창고다. */
	private void noteButtonClick(Screen s) {
		if (!active() || !(s instanceof kr.lunaslight.mod.mixin.ContainerScreenAccessorMixin acc)) {
			return;
		}
		try {
			Slot sl = acc.lunaslight$hoveredSlot();
			if (sl == null || sl.container == client.player.getInventory() || sl.getItem().isEmpty()) {
				return;
			}
			Matcher m = BUTTON.matcher(plain(sl.getItem().getHoverName().getString()));
			if (m.find()) {
				pending = Integer.parseInt(m.group(1));
				pendingAt = System.currentTimeMillis();
			}
		} catch (Throwable ignored) {
		}
	}

	private static String plain(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		s.codePoints().filter(cp -> !((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000)).forEach(sb::appendCodePoint);
		return sb.toString();
	}

	@Override
	public void onTick() {
		if (!active()) {
			current = -1;
			buttons.clear();
			return;
		}
		ensureLoaded();
		Object screen = LunaCompat.screenOf(client);
		if (screen != lastScreen) {
			lastScreen = screen;
			current = -1;
			if (editing >= 0 && screen != editScreen) {
				commitEdit();
			}
			if (screen instanceof AbstractContainerScreen<?> hs && !(screen instanceof InventoryScreen)) {
				if (pending >= 0 && System.currentTimeMillis() - pendingAt < 4000) {
					current = pending;
				} else {
					Matcher m = TITLE.matcher(plain(hs.getTitle().getString()));
					if (m.find()) {
						current = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
					}
				}
				pending = -1;
			}
		}
		buttons.clear();
		if (!(screen instanceof AbstractContainerScreen<?> hs) || screen instanceof InventoryScreen) {
			if (dirty) {
				save();
			}
			return;
		}
		var slots = hs.getMenu().slots;
		int n = slots.size() - 36;
		for (int i = 0; i < n && i < slots.size(); i++) {
			ItemStack st = slots.get(i).getItem();
			if (st.isEmpty()) {
				continue;
			}
			Matcher m = BUTTON.matcher(plain(st.getHoverName().getString()));
			if (m.find()) {
				buttons.putIfAbsent(Integer.parseInt(m.group(1)), i);
			}
		}
		for (int k : buttons.keySet()) {
			boxes.computeIfAbsent(k, x -> new Box());
		}
		// 보고 있는 창고: 칸을 그대로 적어 둔다(상자 몫이 9의 배수일 때)
		if (current >= 0 && n >= 9 && n <= 54 && n % 9 == 0) {
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
			save();   // 5초마다 한 번(창을 닫을 때도 저장)
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
					names.put(Integer.parseInt(e.getKey()), e.getValue().getAsString());
				}
			}
			if (o.has("boxes")) {
				for (var e : o.getAsJsonObject("boxes").entrySet()) {
					JsonObject bo = e.getValue().getAsJsonObject();
					Box b = new Box();
					b.rows = bo.has("rows") ? bo.get("rows").getAsInt() : 6;
					b.raw = bo.has("items") ? bo.getAsJsonArray("items") : null;
					boxes.put(Integer.parseInt(e.getKey()), b);
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulStorage:load", t);
		}
	}

	private com.mojang.serialization.DynamicOps<JsonElement> ops() {
		return client.level == null ? null : client.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
	}

	/** 저장본을 아이템으로 푼다(세계가 열려 있어야 한다). */
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
		return b.items;
	}

	private void save() {
		dirty = false;
		try {
			var ops = ops();
			JsonObject o = new JsonObject();
			JsonObject nm = new JsonObject();
			for (var e : names.entrySet()) {
				nm.addProperty(String.valueOf(e.getKey()), e.getValue());
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
				bx.add(String.valueOf(e.getKey()), bo);
			}
			o.add("boxes", bx);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulStorage:save", t);
		}
	}

	private String nameOf(int k) {
		String n = names.get(k);
		return n == null || n.isBlank() ? k + "번 창고" : n;
	}

	// ==================== 배치 ====================

	private record Panel(int key, int x, int y, int w, int h, int cell, int rows) {
	}

	private List<Panel> layout(AbstractContainerScreen<?> hs) {
		List<Panel> out = new ArrayList<>();
		if (boxes.isEmpty()) {
			return out;
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		int bx = 0, bw = 176;
		if (hs instanceof kr.lunaslight.mod.mixin.ContainerScreenAccessorMixin acc) {
			bx = acc.lunaslight$leftPos();
			bw = acc.lunaslight$imageWidth();
		}
		int maxRows = 1;
		for (Box b : boxes.values()) {
			maxRows = Math.max(maxRows, Math.max(1, Math.min(6, b.rows)));
		}
		List<int[]> regions = new ArrayList<>();   // {x1, x2}
		regions.add(new int[]{6, bx - 6});
		boolean jei = false;
		try {
			jei = kr.lunaslight.mod.util.NeogulJei.on();
		} catch (Throwable ignored) {
		}
		if (!jei) {
			regions.add(new int[]{bx + bw + 6, sw - 6});
		}
		int n = boxes.size();
		int gap = 4;
		for (int cell = 14; cell >= 6; cell--) {
			int pw = 9 * cell + 6;
			int ph = maxRows * cell + 16;
			int rowsFit = Math.max(0, (sh - 12 + gap) / (ph + gap));
			int cap = 0;
			for (int[] r : regions) {
				cap += Math.max(0, (r[1] - r[0] + gap) / (pw + gap)) * rowsFit;
			}
			if (cap < n && cell > 6) {
				continue;
			}
			// 왼쪽 판은 GUI 쪽(오른쪽)부터 채운다
			int idx = 0;
			List<Integer> keys = new ArrayList<>(boxes.keySet());
			for (int ri = 0; ri < regions.size() && idx < n; ri++) {
				int[] r = regions.get(ri);
				int cols = Math.max(0, (r[1] - r[0] + gap) / (pw + gap));
				if (cols == 0) {
					continue;
				}
				int take = Math.min(n - idx, cols * rowsFit);
				int usedRows = (take + cols - 1) / cols;
				int y0 = Math.max(6, (sh - (usedRows * (ph + gap) - gap)) / 2);
				for (int j = 0; j < take; j++) {
					int col = j % cols, row = j / cols;
					int x = ri == 0 ? r[1] - (col + 1) * pw - col * gap : r[0] + col * (pw + gap);
					int key = keys.get(idx++);
					Box b = boxes.get(key);
					out.add(new Panel(key, x, y0 + row * (ph + gap), pw, ph, cell, Math.max(1, Math.min(6, b.rows))));
				}
			}
			return out;
		}
		return out;
	}

	// ==================== 그리기 ====================

	private void onScreenFrame(Object screen, GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		if (!active() || !(screen instanceof AbstractContainerScreen<?> hs) || screen instanceof InventoryScreen) {
			return;
		}
		if (current < 0 && buttons.isEmpty()) {
			return;   // 창고 화면이 아니다
		}
		List<Panel> panels = layout(hs);
		if (panels.isEmpty()) {
			return;
		}
		int accent = LunaDraw.ACCENT;
		ItemStack hoverStack = ItemStack.EMPTY;
		List<int[]> dims = new ArrayList<>();
		int dimA = Math.round((100 - Math.max(15, Math.min(100, dim.get()))) * 2.55f);
		for (Panel p : panels) {
			boolean cur = p.key() == current;
			boolean hov = LunaDraw.in(mouseX, mouseY, p.x(), p.y(), p.w(), p.h());
			LunaDraw.mcPanel(ctx, p.x(), p.y(), p.w(), p.h());
			String title = editing == p.key() ? editBuf + ((System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "") : nameOf(p.key());
			ctx.text(client.font, LunaCompat.textLiteral(LunaDraw.ellipsize(client.font, title, p.w() - 8)), p.x() + 4, p.y() + 4,
				editing == p.key() ? 0xFF1A5FB4 : 0xFF404040, false);
			int gx = p.x() + 3, gy = p.y() + 13;
			int c = p.cell();
			for (int i = 0; i < p.rows() * 9; i++) {
				int sx = gx + (i % 9) * c, sy = gy + (i / 9) * c;
				ctx.fill(sx, sy, sx + c - 1, sy + c - 1, 0xFF8B8B8B);
				ctx.fill(sx, sy, sx + c - 1, sy + 1, 0xFF373737);
				ctx.fill(sx, sy, sx + 1, sy + c - 1, 0xFF373737);
			}
			Box b = boxes.get(p.key());
			List<ItemStack> items = b == null ? List.of() : itemsOf(b);
			if (items.isEmpty()) {
				LunaDraw.textCentered(ctx, client.font, "열어 보면 보여요", p.x() + p.w() / 2, gy + p.rows() * c / 2 - 4, 0xFF404040);
			} else {
				float k = c / 18f;
				LunaCompat.guiPush(ctx);
				try {
					LunaCompat.guiTranslate(ctx, gx, gy);
					LunaCompat.guiScale(ctx, k, k);
					for (int i = 0; i < items.size() && i < p.rows() * 9; i++) {
						ItemStack st = items.get(i);
						if (st.isEmpty()) {
							continue;
						}
						int ix = (i % 9) * 18, iy = (i / 9) * 18;
						ctx.item(st, ix, iy);
						LunaCompat.drawItemOverlay(ctx, client.font, st, ix, iy);
					}
				} finally {
					LunaCompat.guiPop(ctx);
				}
				if (hov) {
					int col = (mouseX - gx) / c, row = (mouseY - gy) / c;
					int i = row * 9 + col;
					if (mouseX >= gx && mouseY >= gy && col < 9 && row < p.rows() && i < items.size() && !items.get(i).isEmpty()) {
						hoverStack = items.get(i);
					}
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
		}
		if (!dims.isEmpty()) {
			// 다음 층에서 덮어야 아이템까지 같이 어두워진다
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
		if (!active() || !(s instanceof AbstractContainerScreen<?> hs) || s instanceof InventoryScreen || (current < 0 && buttons.isEmpty())) {
			return false;
		}
		for (Panel p : layout(hs)) {
			if (!LunaDraw.in(mx, my, p.x(), p.y(), p.w(), p.h())) {
				continue;
			}
			boolean rightClick = button == 1 || button == kr.lunaslight.mod.util.LunaInput.fromGlfwButton(1);
			if (rightClick) {
				startEdit(p.key(), s);
				return true;
			}
			if (editing >= 0) {
				commitEdit();
			}
			if (p.key() == current) {
				return true;
			}
			open(hs, p.key());
			return true;
		}
		if (editing >= 0) {
			commitEdit();
		}
		return false;
	}

	private void open(AbstractContainerScreen<?> hs, int key) {
		pending = key;
		pendingAt = System.currentTimeMillis();
		Integer slot = buttons.get(key);
		if (slot != null && client.gameMode != null) {
			client.gameMode.handleContainerInput(hs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
		} else {
			LunaCompat.sendChatOrCommand(client, "/창고" + key);
		}
	}

	private final TextCapture.Target editTarget = new TextCapture.Target() {
		@Override
		public boolean wants() {
			return editing >= 0 && client != null && LunaCompat.screenOf(client) == editScreen;
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
				editing = -1;
			}
			return true;   // E 같은 글자 키로 창이 닫히지 않게
		}
	};

	private void startEdit(int key, Object screen) {
		if (editing >= 0) {
			commitEdit();
		}
		editing = key;
		editBuf = names.getOrDefault(key, "");
		editScreen = screen;
		TextCapture.setTarget(editTarget);
	}

	private void commitEdit() {
		if (editing < 0) {
			return;
		}
		String v = editBuf.trim();
		if (v.isEmpty()) {
			names.remove(editing);
		} else {
			names.put(editing, v);
		}
		editing = -1;
		save();
	}
}
