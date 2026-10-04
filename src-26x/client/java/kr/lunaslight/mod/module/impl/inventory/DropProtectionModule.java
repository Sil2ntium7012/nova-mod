package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;

/**
 * 49-8차 전면 재작성: "버리기 방지인데 버려진다" 원인 수정.
 *
 * 예전 구현은 END_CLIENT_TICK에서 dropKey.wasPressed()를 소비했는데, 바닐라의 입력 처리
 * (MinecraftClient.handleInputEvents - 같은 틱 안에서 우리보다 먼저 실행됨)가 이미 큐를 비우고
 * 아이템을 버린 뒤라 항상 한 발 늦었음 → 보호가 전혀 안 됨.
 *
 * 이제 START_CLIENT_TICK(틱 시작, 바닐라 입력 처리보다 앞)에서 드롭 키 큐를 우리가 먼저
 * 비워서 바닐라가 볼 수 없게 만들고, 정책에 따라 우리가 직접 버리기를 실행함:
 *  - 모든 버리기 차단: 아무것도 안 함(액션바로 차단 안내)
 *  - 도구 두 번 누르기: 첫 누름엔 액션바 경고만, 짧은 시간 안에 다시 누르면 실제 드롭
 *  - 그 외: 바닐라와 동일하게 즉시 드롭(Ctrl 누르고 있으면 스택 전체 - 바닐라 동작 재현)
 *
 * 모듈이 꺼져 있으면 큐를 건드리지 않아 바닐라 그대로 동작. 화면(인벤토리 등)이 열려 있을 때의
 * 드롭(호버 슬롯 Q)은 키 큐가 아니라 화면 keyPressed로 처리되므로 여기 영향 없음(의도).
 */
public class DropProtectionModule extends Module {

	private static final String[] TOOL_CLASS_NAMES = {
		"net.minecraft.item.PickaxeItem",
		"net.minecraft.world.item.AxeItem",
		"net.minecraft.world.item.ShovelItem",
		"net.minecraft.world.item.HoeItem",
		"net.minecraft.item.SwordItem",
		"net.minecraft.item.MiningToolItem",
		"net.minecraft.world.item.ProjectileWeaponItem",
	};

	private static final List<Class<?>> TOOL_CLASSES = resolveToolClasses();

	private static List<Class<?>> resolveToolClasses() {
		List<Class<?>> list = new ArrayList<>();
		for (String name : TOOL_CLASS_NAMES) {
			try {
				list.add(LunaCompat.classForName(name));
			} catch (ClassNotFoundException ignored) {
				// 이 버전에는 없는 도구 클래스 - 건너뜀.
			}
		}
		return list;
	}

	private final BooleanSetting requireDoublePressForTools;
	private final BooleanSetting protectEnchanted;
	private final BooleanSetting protectDurable;
	private final BooleanSetting blockAllDrops;
	private final IntSetting doublePressWindowTicks;

	private int tickCounter = 0;
	private int lastToolDropAttemptTick = -1000;
	private Object pendingItem = null;   // 확인 대기 중인 아이템(손에 든 게 바뀌면 대기 취소)

	public DropProtectionModule() {
		super("drop_protection", "버리기 보호", ModuleCategory.INVENTORY, "도구를 실수로 버리지 않게 보호");
		requireDoublePressForTools = register(new BooleanSetting("require_double_press_for_tools",
			"도구 두 번 확인", "도구와 무기는 짧은 시간 안에 두 번 눌러야 버려집니다.", true));
		protectEnchanted = register(new BooleanSetting("protect_enchanted",
			"인챈트 보호", "인챈트가 붙은 아이템도 두 번 눌러야 버려집니다.", true));
		protectDurable = register(new BooleanSetting("protect_durable",
			"내구도 장비", "방어구/활처럼 내구도가 있는 장비도 두 번 눌러야 버려집니다.", true));
		blockAllDrops = register(new BooleanSetting("block_all_drops", "버리기 차단",
			"어떤 아이템도 버릴 수 없게 됩니다.", false));
		doublePressWindowTicks = register(new IntSetting("double_press_window_ticks", "인정 시간",
			"두 번 누름으로 인정하는 간격(틱)입니다. 20틱이 1초입니다.", 15, 5, 40, 1).unit("틱"));

		// 틱 "시작"(바닐라 입력 처리 전)에 드롭 키를 가로챔 - 등록은 1회, 내부에서 isEnabled 체크.
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			try {
				interceptDropKey();
			} catch (Throwable ignored) {
			}
		});
	}

	/**
	 * 49-32차 수정(사용자: "도구 2번 눌러 버리기 지금 안돼 바로 버려져").
	 *
	 * 원인: 마인크래프트가 1.21.2부터 PickaxeItem·SwordItem 같은 **도구 클래스를 아예 없애고**
	 * 데이터 컴포넌트로 바꿨다. 그래서 위 TOOL_CLASSES가 최신 버전에서는 전부 해석 실패 →
	 * 목록이 비어 → 곡괭이도 "도구가 아님"으로 새어 나가 그대로 즉시 버려졌다.
	 * 이제 ① 구버전 클래스 ② TOOL/WEAPON 컴포넌트 ③ 내구도 순으로 본다.
	 */
	private boolean isProtected(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		Object item = stack.getItem();
		for (Class<?> cls : TOOL_CLASSES) {
			if (cls.isInstance(item)) {
				return true;
			}
		}
		if (LunaCompat.isToolLike(stack)) {
			return true;
		}
		if (protectDurable.get() && LunaCompat.hasDurability(stack)) {
			return true;
		}
		if (protectEnchanted.get() && stack.isEnchanted()) {
			return true;
		}
		return false;
	}

	private void interceptDropKey() {
		tickCounter++;
		if (!isEnabled() || client.player == null || client.options == null) {
			return;
		}
		// 화면이 열려 있으면 개입하지 않음(그때의 Q는 화면 쪽 처리라 큐를 안 씀)
		if (kr.lunaslight.mod.util.LunaCompat.screenOf(client) != null) {
			return;
		}

		// 큐에 쌓인 누름을 전부 비워 바닐라가 처리하지 못하게 함
		int presses = 0;
		while (LunaCompat.wasKeyBindingPressed(
				LunaCompat.getKeyBindingField(client.options, "dropKey", "keyDrop"))) {
			presses++;
			if (presses > 10) {
				break; // 안전장치
			}
		}
		if (presses == 0) {
			return;
		}

		if (blockAllDrops.get()) {
			LunaCompat.sendActionBar(client, "§c버리기가 차단되어 있습니다 (Nova 설정 → 아이템 버리기 보호)");
			return;
		}

		ItemStack mainHand = client.player.getMainHandItem();
		boolean entireStack = isCtrlDown();

		if (isProtected(mainHand) && requireDoublePressForTools.get()) {
			// 49-20차 버그 수정(사용자: "도구 2번 눌러도 버리기가 안 돼").
			// 한 틱(50ms)은 프레임보다 길어서, Q를 빠르게 두 번 치면 두 누름이 **같은 틱의
			// 큐에 함께** 쌓인다. 예전 코드는 큐를 통째로 비운 뒤 그걸 "한 번의 시도"로만
			// 취급해서, 두 번째 누름을 그대로 삼켜버렸다 → 아무리 빨리 두 번 눌러도 영원히
			// 경고만 뜨고 절대 안 버려짐. 이제 같은 틱에 2번 이상 들어오면 그 자체를
			// "두 번 누름"으로 인정한다.
			int window = doublePressWindowTicks.get();
			boolean sameItem = pendingItem != null && pendingItem == mainHand.getItem();
			boolean withinWindow = sameItem && tickCounter - lastToolDropAttemptTick <= window;

			if (presses >= 2 || withinWindow) {
				dropWithSwing(entireStack);
				lastToolDropAttemptTick = -1000;
				pendingItem = null;
			} else {
				lastToolDropAttemptTick = tickCounter;
				pendingItem = mainHand.getItem();
				LunaCompat.sendActionBar(client, "§e한 번 더 누르면 버려집니다 §7(" + mainHand.getHoverName().getString() + ")");
			}
			return;
		}

		// 보호 대상이 아닌 걸 눌렀으면 대기 중이던 확인은 취소(다른 아이템으로 넘어간 것)
		pendingItem = null;
		lastToolDropAttemptTick = -1000;

		// 보호 대상이 아니면 바닐라와 동일하게(누른 횟수만큼) 드롭
		for (int i = 0; i < presses; i++) {
			dropWithSwing(entireStack);
		}
	}

	/**
	 * 49-17차 수정(사용자: "버릴 때 손동작이 안 보임"): 바닐라는 입력 처리에서
	 * `if (player.dropSelectedItem(ctrl)) player.swingHand(MAIN_HAND);`로 **버리기가 성공하면 손을 흔든다**
	 * (1.21.11 바이트코드 확인 - dropSelectedItem 자체는 패킷만 보내고 스윙하지 않음).
	 * 우리가 바닐라 대신 드롭을 수행하면서 이 스윙을 빼먹어 손 동작이 사라졌었다.
	 */
	private void dropWithSwing(boolean entireStack) {
		try {
			Object p = client.player;
			java.lang.reflect.Method drop = null;
			try {
				drop = p.getClass().getMethod("drop", boolean.class);
			} catch (NoSuchMethodException none) {
				// 49-215차: 26.3은 LocalPlayer.drop(boolean)이 없어지고 MultiPlayerGameMode.dropItem(player, 전부)가
				// 버리기와 손 흔들기를 같이 한다(26.3 바이트코드 확인)
				client.gameMode.getClass().getMethod("dropItem", net.minecraft.client.player.LocalPlayer.class, boolean.class)
						.invoke(client.gameMode, p, entireStack);
				return;
			}
			if (Boolean.TRUE.equals(drop.invoke(p, entireStack))) {
				p.getClass().getMethod("swing", net.minecraft.world.InteractionHand.class)
						.invoke(p, net.minecraft.world.InteractionHand.MAIN_HAND);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("drop:swing", t);
		}
	}

	private boolean isCtrlDown() {
		try {
			long handle = client.getWindow().handle();
			return kr.lunaslight.mod.util.LunaCompat.isKeyPressed(net.minecraft.client.Minecraft.getInstance(), InputConstants.KEY_LCONTROL)
				|| kr.lunaslight.mod.util.LunaCompat.isKeyPressed(net.minecraft.client.Minecraft.getInstance(), InputConstants.KEY_RCONTROL);
		} catch (Throwable ignored) {
			return false;
		}
	}
}
