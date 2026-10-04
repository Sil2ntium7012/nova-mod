package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 셜커 상자 툴팁 미리보기.
 *
 * 49-23차: "셜커 미리보기가 내 인벤토리 보는 것처럼, 셜커 툴팁 모드처럼 색상도 셜커에 맞게" - 글자 목록 대신
 * **9×3 인벤토리 격자**(셜커 색 배경)를 툴팁 이름 줄 바로 아래에 그린다.
 *   ItemStackTooltipDataMixin(getTooltipData) → TooltipData Proxy(GridPayload) → Fabric TooltipComponentCallback →
 *   gui.ShulkerTooltipComponent(격자 그리기). 슬롯별 내용물은 LunaCompat.containerSlots(1.20.5+ CONTAINER 컴포넌트 /
 *   그 전 BlockEntityTag NBT). 바닐라가 넣는 "x개 더..." 글자 줄(container.shulkerBox.*)은 격자를 쓸 때 지운다.
 * 격자를 못 쓰는 상황(설정 끔, 컴포넌트 클래스 없음)에서는 예전처럼 글자 목록.
 */
public class ShulkerPeekModule extends Module {

	/** TooltipData Proxy의 핸들러 겸 격자 데이터(슬롯 27개, 상자 색, 색 배경 여부). */
	public record GridPayload(List<ItemStack> slots, int color, boolean tint) implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) {
			return switch (method.getName()) {
				case "hashCode" -> System.identityHashCode(proxy);
				case "equals" -> args != null && args.length == 1 && proxy == args[0];
				case "toString" -> "LunaShulkerGrid";
				default -> null;
			};
		}
	}

	// 49-41차(사용자: "셜커 미리보기도 왜 설정이 따로 있는 거야"): 설정 없음 - 항상 9×3 격자 + 상자 색.

	private static ShulkerPeekModule instance;
	private static boolean componentReady;
	private static Constructor<?> componentCtor;

	public ShulkerPeekModule() {
		super("shulker_peek", "셜커 상자", ModuleCategory.INVENTORY, "셜커 상자 툴팁에 내용물 격자");
		defaultEnabled(true);
		instance = this;
		LunaCompat.registerTooltipCallback(this::isEnabled, this::onTooltip);
		try {
			Class<?> cls = Class.forName("kr.lunaslight.mod.gui.ShulkerTooltipComponent");
			componentCtor = cls.getConstructor(GridPayload.class);
			componentReady = LunaCompat.registerTooltipComponentCallback(ShulkerPeekModule::componentFor);
		} catch (Throwable t) {
			LunaCompat.warnOnce("shulkerGrid:init", t);
			componentReady = false;
		}
	}

	/** Fabric TooltipComponentCallback: 우리 Proxy 데이터면 격자 컴포넌트, 아니면 null(다른 처리기로). */
	private static Object componentFor(Object data) {
		if (data == null || componentCtor == null || !Proxy.isProxyClass(data.getClass())) {
			return null;
		}
		InvocationHandler h = Proxy.getInvocationHandler(data);
		if (!(h instanceof GridPayload payload)) {
			return null;
		}
		try {
			return componentCtor.newInstance(payload);
		} catch (Throwable t) {
			LunaCompat.warnOnce("shulkerGrid:component", t);
			return null;
		}
	}

	/** ItemStackTooltipDataMixin용: 이 스택에 격자 툴팁 데이터를 줄지(아니면 null). */
	public static Object tooltipDataFor(ItemStack stack) {
		ShulkerPeekModule m = instance;
		if (m == null || !componentReady || !m.isEnabled() || !isShulkerBox(stack)) {
			return null;
		}
		List<ItemStack> slots = LunaCompat.containerSlots(stack, 27);
		if (slots == null) {
			return null;
		}
		return LunaCompat.tooltipDataProxy(new GridPayload(slots, boxColor(stack), true));
	}

	private static boolean usesGrid() {
		ShulkerPeekModule m = instance;
		return m != null && componentReady;
	}

	private static String boxPath(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
			return null;
		}
		net.minecraft.util.Identifier id = LunaCompat.getItemId(blockItem);
		return id == null ? null : id.getPath();
	}

	public static boolean isShulkerBox(ItemStack stack) {
		String path = boxPath(stack);
		return path != null && path.endsWith("shulker_box");
	}

	/** 아이템 id의 염료 접두사("red_shulker_box" → 빨강)로 상자 색. 무염색은 셜커 특유의 보라. */
	public static int boxColor(ItemStack stack) {
		String path = boxPath(stack);
		if (path == null) {
			return 0xFF9A7BB0;
		}
		int cut = path.indexOf("_shulker_box");
		String dye = cut > 0 ? path.substring(0, cut) : "";
		return switch (dye) {
			case "white" -> 0xFFE9ECEC;
			case "orange" -> 0xFFF07613;
			case "magenta" -> 0xFFBD44B3;
			case "light_blue" -> 0xFF3AAFD9;
			case "yellow" -> 0xFFF8C527;
			case "lime" -> 0xFF70B919;
			case "pink" -> 0xFFED8DAC;
			case "gray" -> 0xFF3E4447;
			case "light_gray" -> 0xFF8E8E86;
			case "cyan" -> 0xFF158991;
			case "purple" -> 0xFF792AAC;
			case "blue" -> 0xFF35399D;
			case "brown" -> 0xFF724728;
			case "green" -> 0xFF546D1B;
			case "red" -> 0xFFA12722;
			case "black" -> 0xFF141519;
			default -> 0xFF9A7BB0;
		};
	}

	private void onTooltip(ItemStack stack, List<Text> lines) {
		if (!isShulkerBox(stack)) {
			return;
		}
		if (usesGrid()) {
			// 49-32차(사용자: "아이템이 이미지로 보이는데 글로도 보이면 안 되지"):
			// 바닐라는 "그리고 x개 더" 줄(container.shulkerBox.*)뿐 아니라 내용물 **이름 줄**도
			// 최대 5개까지 넣는다("조약돌 x64" 꼴). 그 줄들의 번역 키는 컨테이너가 아니라
			// 아이템 이름 키라서 예전 조건에 안 걸려 격자 아래에 글자가 그대로 남아 있었다.
			// 이제 개수 꼴("… xN")로 끝나는 줄까지 같이 지운다(첫 줄=아이템 이름은 보존).
			for (int i = lines.size() - 1; i >= 1; i--) {
				Text line = lines.get(i);
				String key = LunaCompat.translationKeyOf(line);
				if (key != null && key.startsWith("container.shulkerBox.")) {
					lines.remove(i);
					continue;
				}
				String plain = line == null ? "" : line.getString().trim();
				if (plain.matches(".+ x\\d+$")) {
					lines.remove(i);
				}
			}
			return;
		}
		boolean[] any = {false};
		LunaCompat.forEachContainerItem(stack, contained -> {
			if (contained.isEmpty()) {
				return;
			}
			if (!any[0]) {
				lines.add(LunaCompat.textLiteral("§7내용물:"));
				any[0] = true;
			}
			lines.add(LunaCompat.textLiteral("  §f" + contained.getCount() + "x " + contained.getName().getString()));
		});
		if (!any[0]) {
			lines.add(LunaCompat.textLiteral("§7(비어 있음)"));
		}
	}
}
