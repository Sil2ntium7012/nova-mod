package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.gui.LunaTooltipFrame;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaVersion;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 툴팁 꾸미기 + 정보.
 *
 * 49-23차: 등급 색 테두리 상자(TooltipBackgroundMixin + gui.LunaTooltipFrame), 인챈트 숫자, 인챈트 피해,
 * 초당 피해, 내구도, ID, NBT - 전부 개별 스위치.
 *
 * 49-25차(사용자: "공격 피해·공격 속도·주로 사용하는 손에 있을 때 이런 거 없애고 공격 피해·공격 속도는 따로
 * 설정으로, 곡괭이는 채굴 능력, 음식은 글자 말고 마크 배고픔 아이콘으로"):
 *  - 바닐라 능력치 블록("주로 사용하는 손에 있을 때:" 머리줄 + " 7 공격 피해" 같은 줄 + 앞의 빈 줄)을 번역 키
 *    (item.modifiers.* / attribute.modifier.*)로 찾아 **지우고**, 값은 번역 인자에서 읽어 둔다(언어 무관).
 *  - 그 자리에 공격 피해 / 공격 속도 / 방어 정보를 각각 설정으로 짧은 한 줄씩. 모르는 능력치는 잃지 않게
 *    "이름 값"으로 다시 붙인다.
 *  - 도구(곡괭이·도끼·삽·괭이): 채굴 등급(재료 접두사) + 채굴 속도(TOOL 컴포넌트 / ToolMaterial, 효율 포함).
 *  - 음식: TooltipData Proxy(FoodPayload) → gui.FoodTooltipComponent가 이름 줄 아래에 고기 아이콘 두 줄
 *    (배고픔 / 포만감 금색 윤곽). 컴포넌트 API가 없는 1.15~1.16은 글자 한 줄로 대체.
 *
 * ItemTooltipCallback은 LunaCompat.registerTooltipCallback(리플렉션 Proxy, 시그니처 무관)으로 한 번만 등록.
 */
public class ItemTooltipInfoModule extends Module {

	/** TooltipData Proxy의 핸들러 겸 음식 데이터(배고픔, 포만감, 포만감 색). */
	public record FoodPayload(float hunger, float saturation, int saturationColor) implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> args != null && args.length == 1 && proxy == args[0];
				case "toString" -> "LunaFoodTooltip";
				default -> null;
			};
		}
	}

	// ---- 모양 ----
	private final BooleanSetting fancyFrame = register(new BooleanSetting(
			"fancy_frame", "툴팁 상자", "툴팁 배경을 테두리가 있는 그라데이션 상자로 바꿉니다.", true).style());
	private final BooleanSetting rarityBorder = register(new BooleanSetting(
			"rarity_border", "등급 색", "테두리를 아이템 등급 색으로 칠합니다. 끄면 Nova 색입니다.", true).style());
	private final BooleanSetting nameSeparator = register(new BooleanSetting(
			"name_separator", "이름 구분선", "아이템 이름 아래에 구분선을 넣습니다.", true).style());
	private final BooleanSetting tooltipShadow = register(new BooleanSetting(
			"shadow", "그림자", "툴팁 상자에 그림자를 넣습니다.", true).style());

	// ---- 능력치 ----
	private final BooleanSetting hideVanillaAttributes = register(new BooleanSetting(
			"hide_vanilla_attributes", "기본 능력치 숨김", "마인크래프트 기본 능력치 줄(주로 사용하는 손에 있을 때 등)을 숨깁니다.", true));
	// 49-32차(사용자: "툴팁 켜면 블록 아래 파란색으로 뜨는 카테고리 표시도 없애는 기능"):
	// 크리에이티브에서 보이는 파란 분류 줄(건축 블록, 자연 등)을 지운다. 번역 키가
	// itemGroup.* 이라 언어와 무관하게 찾아낸다.
	private final BooleanSetting hideItemGroup = register(new BooleanSetting(
			"hide_item_group", "분류 줄 숨김", "크리에이티브에서 이름 아래에 뜨는 파란 분류 줄을 숨깁니다.", true));
	private final BooleanSetting attackDamage = register(new BooleanSetting(
			"attack_damage", "공격 피해", "무기의 공격 피해를 한 줄로 표시합니다.", true));
	private final BooleanSetting attackSpeed = register(new BooleanSetting(
			"attack_speed", "공격 속도", "무기의 공격 속도를 한 줄로 표시합니다.", true));
	private final BooleanSetting dps = register(new BooleanSetting(
			"dps", "초당 피해", "공격 피해와 공격 속도를 곱한 초당 피해를 표시합니다.", true));
	private final BooleanSetting armorInfo = register(new BooleanSetting(
			"armor_info", "방어 정보", "갑옷의 방어력과 강도를 한 줄로 표시합니다.", true));
	// 49-114차(사용자: "채굴 ~~까지·채굴 속도 아예 없애"): 채굴 정보 설정·표시 전부 제거.

	// ---- 인챈트 ----
	private final BooleanSetting enchantNumbers = register(new BooleanSetting(
			"enchant_numbers", "인챈트 숫자", "인챈트 단계를 로마 숫자 대신 숫자로 표시합니다.", true));
	// 49-114차(사용자: "인챈트 피해 따로 뜨는 거 없애고 기본 공격력 + 인챈트 공격력 이렇게"):
	// "날카로움 피해 +2" 같은 별도 줄을 없애고, 날카로움 보너스는 공격 피해 줄에 "기본 + 인챈트"로 합친다.

	// ---- 기타 ----
	private final BooleanSetting foodInfo = register(new BooleanSetting(
			"food_info", "음식 정보", "음식이 채워 주는 배고픔과 포만감을 고기 아이콘으로 표시합니다.", true));
	private final ColorSetting saturationColor = register(new ColorSetting(
			"saturation_color", "포만감 색", "포만감 아이콘 윤곽의 색입니다.", 0xFFFFD24A));
	private final BooleanSetting durabilityBar = register(new BooleanSetting(
			"durability_bar", "내구도 막대", "툴팁 아래 테두리에 남은 내구도를 막대로 깝니다(툴팁이 길어지지 않습니다).", true).style());
	private final BooleanSetting durability = register(new BooleanSetting(
			"durability", "내구도", "남은 내구도와 최대 내구도를 숫자로도 표시합니다.", true));
	private final BooleanSetting compactEnchants = register(new BooleanSetting(
			"compact_enchants", "인챈트 압축", "인챈트를 한 줄에 둘씩 붙여 툴팁 길이를 줄입니다.", true));
	private final BooleanSetting bagCount = register(new BooleanSetting(
			"bag_count", "가방 개수", "이 아이템을 가방에 모두 몇 개 갖고 있는지 보여 줍니다.", true));
	private final BooleanSetting showId = register(new BooleanSetting(
			"show_id", "아이템 ID", "minecraft:diamond_sword 같은 아이템 ID를 표시합니다.", false));
	private final BooleanSetting showNbt = register(new BooleanSetting(
			"show_nbt", "NBT", "커스텀 데이터와 컴포넌트를 최대 5줄로 요약합니다.", false));

	/** 번역 인자를 못 읽을 때의 대체: "7 공격 피해" / "+3 방어" 같은 줄. */
	private static final Pattern ATTR_LINE = Pattern.compile("^\\s*([+-]?[0-9]+(?:[.,][0-9]+)?)(%?)\\s+(.+?)\\s*$");

	/** 바닐라 공격 피해 줄에 날카로움 보너스가 이미 들어 있는 시대(~1.20.6). 1.21+는 안 들어감. */
	private static final boolean VANILLA_INCLUDES_SHARPNESS = !LunaVersion.isWithin("1.21", null);

	private static ItemTooltipInfoModule instance;
	private static boolean componentReady;
	private static Constructor<?> componentCtor;

	public ItemTooltipInfoModule() {
		super("item_tooltip_info", "툴팁", ModuleCategory.INVENTORY, "등급 테두리 | 인챈트 | 공격 | 음식 | 내구도");
		defaultEnabled(true);
		instance = this;
		foodInfo.withColor(saturationColor);
		// 49-216차(버전별 점검): 툴팁 상자는 TooltipBackgroundRenderer(DrawContext) 훅이라 1.20부터만 된다. 1.19.4 이하는
		// 배경을 BufferBuilder로 한꺼번에 그려 끼어들 자리가 없다 - 켜도 아무 일 없는 줄을 흐리게 둔다.
		java.util.function.BooleanSupplier noFrame = () -> !kr.lunaslight.mod.util.LunaVersion.isWithin("1.20", null);
		fancyFrame.disabledWhen(noFrame);
		rarityBorder.disabledWhen(noFrame);
		nameSeparator.disabledWhen(noFrame);
		tooltipShadow.disabledWhen(noFrame);
		LunaCompat.registerTooltipCallback(this::isEnabled, this::onTooltip);
		try {
			Class<?> cls = Class.forName("kr.lunaslight.mod.gui.FoodTooltipComponent");
			componentCtor = cls.getConstructor(FoodPayload.class);
			componentReady = LunaCompat.registerTooltipComponentCallback(ItemTooltipInfoModule::componentFor);
		} catch (Throwable t) {
			LunaCompat.warnOnce("foodTooltip:init", t);
			componentReady = false;
		}
		syncFrame();
	}

	@Override
	protected void onEnable() {
		syncFrame();
	}

	@Override
	protected void onDisable() {
		LunaTooltipFrame.enabled = false;
		LunaTooltipFrame.durabilityBar = false;
	}

	@Override
	public void onTick() {
		syncFrame();
	}

	private void syncFrame() {
		LunaTooltipFrame.enabled = isEnabled() && fancyFrame.get();
		LunaTooltipFrame.rarityBorder = rarityBorder.get();
		LunaTooltipFrame.nameSeparator = nameSeparator.get();
		LunaTooltipFrame.shadow = tooltipShadow.get();
		LunaTooltipFrame.durabilityBar = durabilityBar.get();
	}

	// ==================== 음식 아이콘 컴포넌트 ====================

	/** Fabric TooltipComponentCallback: 우리 FoodPayload면 아이콘 컴포넌트, 아니면 null(다른 처리기로). */
	private static Object componentFor(Object data) {
		if (data == null || componentCtor == null || !Proxy.isProxyClass(data.getClass())) {
			return null;
		}
		InvocationHandler h = Proxy.getInvocationHandler(data);
		if (!(h instanceof FoodPayload payload)) {
			return null;
		}
		try {
			return componentCtor.newInstance(payload);
		} catch (Throwable t) {
			LunaCompat.warnOnce("foodTooltip:component", t);
			return null;
		}
	}

	/** ItemStackTooltipDataMixin용: 음식이면 아이콘 툴팁 데이터(아니면 null). */
	public static Object tooltipDataFor(ItemStack stack) {
		ItemTooltipInfoModule m = instance;
		if (m == null || !componentReady || !m.isEnabled() || !m.foodInfo.get()) {
			return null;
		}
		float[] food = LunaCompat.foodValues(stack);
		if (food == null || (food[0] <= 0f && food[1] <= 0f)) {
			return null;
		}
		return LunaCompat.tooltipDataProxy(new FoodPayload(food[0], food[1], m.saturationColor.getArgb()));
	}

	// ==================== 49-70차(4-32) ====================

	/**
	 * 가방(핫바 포함) 전체에서 이 아이템이 몇 개인지. 못 세면 0.
	 *
	 * <p><b>왜 쓸모가 있나</b>: 상자 정리나 건축 중에 제일 자주 하는 질문이 "이거 몇 개 남았지"인데,
	 * 지금까지는 가방을 열어 눈으로 세야 했다. 41칸을 도는 일이라 툴팁 한 번당 비용도 사실상 없다.
	 *
	 * <p>NBT·인챈트가 달라도 <b>같은 종류면 같이 센다</b> - "다이아 곡괭이 3개"를 알고 싶은 것이지
	 * "효율5짜리 3개"를 알고 싶은 게 아닌 경우가 대부분이다.
	 */
	private int bagCount(ItemStack stack) {
		try {
			if (client == null || client.player == null) {
				return 0;
			}
			Object inv = LunaCompat.getPlayerInventory(client.player);
			int size = LunaCompat.invSize(inv);
			if (size <= 0) {
				return 0;
			}
			int total = 0;
			for (int i = 0; i < size; i++) {
				ItemStack st = LunaCompat.invGetStack(inv, i);
				if (st != null && !st.isEmpty() && st.getItem() == stack.getItem()) {
					total += st.getCount();
				}
			}
			return total;
		} catch (Throwable ignored) {
			return 0;
		}
	}

	/**
	 * 인챈트 줄을 <b>한 줄에 둘씩</b> 붙인다. 인챈트가 대여섯 개 붙은 장비의 툴팁이 화면 절반을 먹는 것을
	 * 줄이는 게 목적이다.
	 *
	 * <p><b>저주는 안 묶는다.</b> 저주 줄은 빨간색이고 그게 경고다 - 회색 줄에 섞어 붙이면 그 경고가 죽는다.
	 * 저주가 하나라도 있으면 통째로 손대지 않는다(반만 묶으면 더 어지럽다).
	 *
	 * <p>연속으로 붙어 있는 인챈트 줄 구간만 다룬다. 서버가 중간에 다른 줄을 끼워 넣었으면 그 구간에서 끊는다.
	 */
	private void compactEnchantLines(List<Text> lines, List<LunaCompat.EnchantInfo> enchants) {
		java.util.Set<String> names = new java.util.HashSet<>();
		for (LunaCompat.EnchantInfo e : enchants) {
			if (e.id() != null && e.id().contains("curse")) {
				return;                      // 저주가 섞인 장비는 그대로 둔다
			}
			if (e.name() == null) {
				continue;
			}
			String full = e.name().getString();
			names.add(full);
			names.add(baseName(full, e.level()) + " " + e.level());   // 숫자 표기로 바뀐 줄도 같은 것
		}
		if (names.size() < 2) {
			return;
		}
		int start = -1;
		for (int i = 0; i < lines.size(); i++) {
			Text line = lines.get(i);
			boolean isEnchant = line != null && names.contains(line.getString());
			if (isEnchant && start < 0) {
				start = i;
			}
			boolean last = i == lines.size() - 1;
			if (start >= 0 && (!isEnchant || last)) {
				int end = isEnchant && last ? i : i - 1;
				if (end - start >= 1) {
					merged(lines, start, end);
					return;                  // 한 구간만 다룬다(보통 하나뿐이다)
				}
				start = -1;
			}
		}
	}

	/** lines[start..end]를 두 개씩 한 줄로 합친다. */
	private void merged(List<Text> lines, int start, int end) {
		List<String> texts = new ArrayList<>();
		for (int i = start; i <= end; i++) {
			texts.add(lines.get(i).getString());
		}
		List<Text> out = new ArrayList<>();
		for (int i = 0; i < texts.size(); i += 2) {
			String joined = i + 1 < texts.size()
					? "§7" + texts.get(i) + " §8| §7" + texts.get(i + 1)
					: "§7" + texts.get(i);
			out.add(LunaCompat.textLiteral(joined));
		}
		for (int i = end; i >= start; i--) {
			lines.remove(i);
		}
		lines.addAll(start, out);
	}

	// ==================== 유틸 ====================

	private static String fmt(double v) {
		if (Math.abs(v - Math.rint(v)) < 0.005) {
			return String.valueOf((long) Math.rint(v));
		}
		return String.format(java.util.Locale.ROOT, "%.1f", v);
	}

	/** 원래 줄의 스타일(회색/저주 빨강)을 유지한 새 글자 줄. setStyle은 MutableText(1.16+)/BaseText(1.15)라 리플렉션. */
	private static Text styled(String literal, Text styleFrom) {
		Text t = LunaCompat.textLiteral(literal);
		if (styleFrom != null) {
			try {
				Method m = LunaCompat.findMethod(t.getClass(), "setStyle", net.minecraft.text.Style.class);
				if (m != null) {
					m.invoke(t, styleFrom.getStyle());
				}
			} catch (Throwable ignored) {
			}
		}
		return t;
	}

	/** 번역 키 후보들 중 실제로 번역되는 첫 문자열(없으면 null). */
	private static String translated(String... keys) {
		for (String k : keys) {
			String s = LunaCompat.translate(k);
			if (s != null && !s.isEmpty() && !s.equals(k)) {
				return s;
			}
		}
		return null;
	}

	/** "attribute.name.generic.attack_damage" / "attribute.name.attack_damage" / "…generic.attackDamage" → "attackdamage". */
	private static String normalizeAttribute(String key) {
		if (key == null) {
			return null;
		}
		String k = key;
		if (k.startsWith("attribute.name.")) {
			k = k.substring("attribute.name.".length());
		}
		for (String prefix : new String[]{"generic.", "player.", "zombie.", "horse."}) {
			if (k.startsWith(prefix)) {
				k = k.substring(prefix.length());
			}
		}
		return k.replace("_", "").toLowerCase(java.util.Locale.ROOT);
	}

	// ==================== 능력치 블록 ====================

	/** 바닐라 능력치 한 줄에서 읽은 값. */
	private record Attr(String key, String display, double value, boolean percent, boolean base) {
	}

	/** 바닐라 능력치 블록 하나("주로 사용하는 손에 있을 때:" + 줄들 + 앞 빈 줄). */
	private static final class AttrBlock {
		int start;      // 지울 첫 줄(빈 줄 포함)
		int end;        // 지울 마지막 줄(포함)
		String slot;    // mainhand / offhand / head / chest / …
		final List<Attr> attrs = new ArrayList<>();
	}

	/** 줄에서 번역 조각으로 능력치를 읽음(없으면 null). */
	private static Attr parseAttr(Text line, List<LunaCompat.TranslatablePart> parts) {
		for (LunaCompat.TranslatablePart p : parts) {
			if (!p.key().startsWith("attribute.modifier.")) {
				continue;
			}
			String rest = p.key().substring("attribute.modifier.".length()); // equals.0 / plus.0 / take.1
			boolean base = rest.startsWith("equals");
			boolean take = rest.startsWith("take");
			boolean percent = !rest.endsWith(".0");
			Object[] args = p.args();
			if (args == null || args.length < 2) {
				break;
			}
			String num = args[0] instanceof Text t ? t.getString() : String.valueOf(args[0]);
			double value;
			try {
				value = Double.parseDouble(num.trim().replace(',', '.'));
			} catch (NumberFormatException ex) {
				break;
			}
			if (take) {
				value = -value;
			}
			String key = null;
			String display = null;
			if (args[1] instanceof Text nameText) {
				List<LunaCompat.TranslatablePart> nameParts = LunaCompat.translatableParts(nameText);
				if (!nameParts.isEmpty()) {
					key = normalizeAttribute(nameParts.get(0).key());
				}
				display = nameText.getString();
			} else if (args[1] != null) {
				display = String.valueOf(args[1]);
			}
			if (key == null && display != null) {
				key = display.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
			}
			return new Attr(key, display, value, percent, base);
		}
		// 번역 인자를 못 읽는 경우(구버전 리플렉션 실패): 글자로
		Matcher m = ATTR_LINE.matcher(line.getString());
		if (m.matches()) {
			try {
				double value = Double.parseDouble(m.group(1).replace(',', '.'));
				String name = m.group(3);
				String dmgName = translated("attribute.name.generic.attack_damage", "attribute.name.attack_damage", "attribute.name.generic.attackDamage");
				String spdName = translated("attribute.name.generic.attack_speed", "attribute.name.attack_speed", "attribute.name.generic.attackSpeed");
				String key = name.equals(dmgName) ? "attackdamage" : (name.equals(spdName) ? "attackspeed" : name.replace(" ", "").toLowerCase(java.util.Locale.ROOT));
				return new Attr(key, name, value, !m.group(2).isEmpty(), !m.group(1).startsWith("+") && !m.group(1).startsWith("-"));
			} catch (NumberFormatException ignored) {
			}
		}
		return null;
	}

	/** 툴팁 줄들에서 바닐라 능력치 블록을 전부 찾음(순서대로). */
	private static List<AttrBlock> findBlocks(List<Text> lines) {
		List<AttrBlock> blocks = new ArrayList<>(2);
		AttrBlock current = null;
		for (int i = 0; i < lines.size(); i++) {
			Text line = lines.get(i);
			if (line == null) {
				continue;
			}
			List<LunaCompat.TranslatablePart> parts = LunaCompat.translatableParts(line);
			String header = null;
			for (LunaCompat.TranslatablePart p : parts) {
				if (p.key().startsWith("item.modifiers.")) {
					header = p.key().substring("item.modifiers.".length());
					break;
				}
			}
			if (header != null) {
				current = new AttrBlock();
				current.slot = header;
				current.start = i > 0 && LunaCompat.isBlankText(lines.get(i - 1)) ? i - 1 : i;
				current.end = i;
				blocks.add(current);
				continue;
			}
			if (current != null) {
				Attr a = parseAttr(line, parts);
				if (a != null) {
					current.attrs.add(a);
					current.end = i;
				} else {
					current = null;
				}
			}
		}
		return blocks;
	}

	// ==================== 툴팁 ====================

	private void onTooltip(ItemStack stack, List<Text> lines) {
		if (stack.isEmpty()) {
			return;
		}
		syncFrame();
		LunaTooltipFrame.noteStack(stack);

		List<LunaCompat.EnchantInfo> enchants = (enchantNumbers.get() || dps.get()
				|| attackDamage.get() || compactEnchants.get())
				? LunaCompat.enchantments(stack) : List.of();

		// ---- 인챈트 숫자 표기: "날카로움 V" → "날카로움 5" ----
		if (enchantNumbers.get() && !enchants.isEmpty()) {
			for (LunaCompat.EnchantInfo e : enchants) {
				if (e.name() == null) {
					continue;
				}
				String full = e.name().getString();
				String base = baseName(full, e.level());
				if (base.equals(full)) {
					continue; // 레벨 단어가 없는 이름(최대 1레벨 인챈트) - 그대로
				}
				for (int i = 0; i < lines.size(); i++) {
					Text line = lines.get(i);
					if (line != null && full.equals(line.getString())) {
						lines.set(i, styled(base + " " + e.level(), line));
						break;
					}
				}
			}
		}

		// ---- 바닐라 능력치 블록 읽기(+ 숨기기) ----
		List<AttrBlock> blocks = findBlocks(lines);
		Map<String, Attr> attrs = new LinkedHashMap<>();
		List<Attr> unknown = new ArrayList<>();
		for (AttrBlock b : blocks) {
			for (Attr a : b.attrs) {
				String k = a.key() == null ? "" : a.key();
				switch (k) {
					case "attackdamage", "attackspeed", "armor", "armortoughness", "knockbackresistance" -> {
						if (!attrs.containsKey(k)) {
							attrs.put(k, a);
						}
					}
					default -> unknown.add(a);
				}
			}
		}
		// 파란 분류 줄(itemGroup.*) 제거 - 이름 줄(0번)은 건드리지 않는다.
		if (hideItemGroup.get()) {
			for (int i = lines.size() - 1; i >= 1; i--) {
				if (isItemGroupLine(lines.get(i))) {
					lines.remove(i);
				}
			}
		}
		int insertAt = blocks.isEmpty() ? -1 : blocks.get(0).start;
		if (hideVanillaAttributes.get() && !blocks.isEmpty()) {
			for (int bi = blocks.size() - 1; bi >= 0; bi--) {
				AttrBlock b = blocks.get(bi);
				for (int i = Math.min(b.end, lines.size() - 1); i >= b.start; i--) {
					lines.remove(i);
				}
			}
		} else if (!blocks.isEmpty()) {
			insertAt = Math.min(lines.size(), blocks.get(blocks.size() - 1).end + 1);
		}

		List<Text> extra = new ArrayList<>();

		// ---- 공격: 피해 / 속도 / 초당 피해 / 인챈트 피해 ----
		Attr dmg = attrs.get("attackdamage");
		Attr spd = attrs.get("attackspeed");
		// 49-114차: 날카로움만 평타에 그대로 더해지는 보너스라 공격 피해에 합친다. 강타·살충·찌르기·힘은
		// 특정 대상/원거리에만 붙는 조건부라 평타 숫자에 못 넣는다 → 별도 줄로도 안 띄운다(사용자 요청).
		double bonus = 0;
		if (!enchants.isEmpty()) {
			for (LunaCompat.EnchantInfo e : enchants) {
				if ("minecraft:sharpness".equals(e.id())) {
					bonus += 0.5 * e.level() + 0.5;
				}
			}
		}
		double damageTotal = -1;
		if (dmg != null && dmg.value() > 0) {
			// 바닐라 값 = 무기 + 맨손 1(+ ~1.20.6은 날카로움까지). base = 인챈트 뺀 기본, damageTotal = base + 인챈트.
			double base = VANILLA_INCLUDES_SHARPNESS ? dmg.value() - bonus : dmg.value();
			damageTotal = base + bonus;
			if (attackDamage.get()) {
				// 사용자: "기본 공격력 + 인챈트 공격력 이런식으로". 인챈트가 있으면 "6 + 1 = 7", 없으면 그냥 "7".
				String line = bonus > 0
						? "§7공격 피해 §f" + fmt(base) + " §a+ " + fmt(bonus) + " §8= §f" + fmt(damageTotal)
						: "§7공격 피해 §f" + fmt(damageTotal);
				extra.add(LunaCompat.textLiteral(line));
			}
		}
		if (spd != null && spd.value() > 0 && attackSpeed.get()) {
			extra.add(LunaCompat.textLiteral("§7공격 속도 §f" + fmt(spd.value())));
		}
		if (dps.get() && damageTotal > 0 && spd != null && spd.value() > 0) {
			String line = "§7초당 피해 §f" + fmt(damageTotal * spd.value());
			if (bonus > 0) {
				double baseDamage = VANILLA_INCLUDES_SHARPNESS ? damageTotal - bonus : dmg.value();
				line += " §8(기본 " + fmt(baseDamage * spd.value()) + ")";
			}
			extra.add(LunaCompat.textLiteral(line));
		}
		// 49-114차: 인챈트 피해 별도 줄 제거(날카로움은 위 공격 피해에 합쳐짐).

		// ---- 방어: 방어력 · 강도 · 넉백 저항 ----
		if (armorInfo.get()) {
			StringBuilder sb = new StringBuilder();
			Attr armor = attrs.get("armor");
			Attr tough = attrs.get("armortoughness");
			Attr kb = attrs.get("knockbackresistance");
			if (armor != null && armor.value() != 0) {
				sb.append("§7방어력 §f").append(fmt(armor.value()));
			}
			if (tough != null && tough.value() != 0) {
				if (sb.length() > 0) {
					sb.append(" §8| ");
				}
				sb.append("§7강도 §f").append(fmt(tough.value()));
			}
			if (kb != null && kb.value() != 0) {
				if (sb.length() > 0) {
					sb.append(" §8| ");
				}
				// 바닐라는 0.1을 "1"로 보여줌(×10) → 퍼센트로
				sb.append("§7넉백 저항 §f").append(fmt(kb.value() * 10)).append('%');
			}
			if (sb.length() > 0) {
				extra.add(LunaCompat.textLiteral(sb.toString()));
			}
		}

		// ---- 숨긴 블록의 모르는 능력치는 잃지 않게 "이름 값"으로 ----
		if (hideVanillaAttributes.get()) {
			for (Attr a : unknown) {
				if (a.display() == null) {
					continue;
				}
				String v = (a.base() ? "" : (a.value() >= 0 ? "+" : "")) + fmt(a.value()) + (a.percent() ? "%" : "");
				extra.add(LunaCompat.textLiteral("§7" + a.display() + " §f" + v));
			}
		}

		// 49-114차: 채굴 정보(채굴 등급 …까지 · 채굴 속도) 제거 - 사용자 요청.

		if (!extra.isEmpty()) {
			if (insertAt < 0 || insertAt > lines.size()) {
				if (!lines.isEmpty() && !LunaCompat.isBlankText(lines.get(lines.size() - 1))) {
					lines.add(LunaCompat.textLiteral(""));
				}
				lines.addAll(extra);
			} else {
				if (insertAt > 0 && !LunaCompat.isBlankText(lines.get(insertAt - 1))) {
					extra.add(0, LunaCompat.textLiteral(""));
				}
				lines.addAll(insertAt, extra);
			}
		}

		// ---- 음식(컴포넌트를 못 쓰는 1.15~1.16만 글자로) ----
		if (foodInfo.get() && !componentReady) {
			float[] food = LunaCompat.foodValues(stack);
			if (food != null) {
				lines.add(LunaCompat.textLiteral("§7배고픔 §f+" + fmt(food[0]) + " §8| §7포만감 §6+" + fmt(food[1])));
			}
		}

		boolean advanced = LunaCompat.advancedTooltips(client);

		// ---- 내구도 ----
		if (durability.get() && !advanced) {
			try {
				if (stack.isDamageable() && stack.getMaxDamage() > 0) {
					int max = stack.getMaxDamage();
					int left = max - stack.getDamage();
					float ratio = left / (float) max;
					String col = ratio > 0.5f ? "§a" : (ratio > 0.25f ? "§e" : "§c");
					lines.add(LunaCompat.textLiteral("§7내구도 " + col + left + "§8/" + max));
				}
			} catch (Throwable ignored) {
			}
		}

		// ---- 가방에 몇 개(49-70차, 4-32) ----
		if (bagCount.get()) {
			int n = bagCount(stack);
			// 지금 보고 있는 한 칸만 갖고 있으면 아무 정보가 아니다 - 안 적는다
			if (n > stack.getCount()) {
				lines.add(LunaCompat.textLiteral("§8가방에 " + n + "개"));
			}
		}

		// ---- 인챈트 묶어 쓰기(49-70차, 4-32) ----
		if (compactEnchants.get() && enchants.size() >= 2) {
			compactEnchantLines(lines, enchants);
		}

		// ---- ID ----
		if (showId.get() && !advanced) {
			net.minecraft.util.Identifier id = LunaCompat.getItemId(stack.getItem());
			if (id != null) {
				lines.add(LunaCompat.textLiteral("§8" + id));
			}
		}

		// ---- NBT / 컴포넌트 ----
		if (showNbt.get()) {
			List<String> nbt = LunaCompat.nbtSummary(stack, 5, 60);
			if (!nbt.isEmpty()) {
				lines.add(LunaCompat.textLiteral("§8NBT:"));
				for (String s : nbt) {
					lines.add(LunaCompat.textLiteral("§8 " + s));
				}
			}
		}
	}

	/** "날카로움 V"에서 레벨 단어를 뗀 이름. */
	private static String baseName(String full, int level) {
		String levelWord = LunaCompat.translate("enchantment.level." + level);
		if (levelWord != null && !levelWord.isEmpty() && !levelWord.startsWith("enchantment.level.") && full.endsWith(" " + levelWord)) {
			return full.substring(0, full.length() - levelWord.length() - 1);
		}
		if (full.matches(".*\\s(I|II|III|IV|V|VI|VII|VIII|IX|X)$")) {
			return full.substring(0, full.lastIndexOf(' '));
		}
		return full;
	}

	/** 그 줄이 크리에이티브 분류(itemGroup.*) 줄인지. */
	private static boolean isItemGroupLine(Text line) {
		if (line == null) {
			return false;
		}
		for (LunaCompat.TranslatablePart p : LunaCompat.translatableParts(line)) {
			if (p.key() != null && p.key().startsWith("itemGroup.")) {
				return true;
			}
		}
		return false;
	}
}
