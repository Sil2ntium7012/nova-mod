package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.TextCapture;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * 49-42차: 인벤토리 연 채 이동 - 스텁을 실제로. 믹스인 없이:
 * 인벤토리/상자 화면이 열려 있는 동안 매 틱 이동 키(앞/뒤/좌/우/점프/달리기)의 **실제 GLFW 상태**를 읽어
 * 그 KeyBinding#setPressed에 넣는다. 바닐라는 화면이 열리면 키 이벤트를 화면에만 주고 키바인딩을 갱신하지 않지만,
 * 플레이어 이동(KeyboardInput.tick → isPressed)은 매 틱 그대로 돌기 때문에 이것만으로 걷는다(Inventory Walk 방식).
 * 마우스는 커서 그대로라 시점은 못 돌린다.
 * 글자를 치는 상황은 제외: 크리에이티브 검색 탭(검색칸이 보일 때), 입력칸에 포커스가 있을 때(모루 이름 등), 우리 검색창(TextCapture).
 * 49-248차(사용자: "인벤토리 무빙 크리에이티브 인벤토리도 되게"): 크리에이티브 창은 검색 탭만 빼고 된다.
 */
public class InventoryMoveModule extends Module {

	private final BooleanSetting jump = register(new BooleanSetting(
			"jump", "점프", "화면을 연 채 스페이스로 점프합니다.", true));
	private final BooleanSetting sprint = register(new BooleanSetting(
			"sprint", "달리기", "화면을 연 채 달리기 키가 먹습니다.", true));

	private boolean applied;

	public InventoryMoveModule() {
		super("inventory_move", "인벤토리 무빙", ModuleCategory.INVENTORY, "인벤토리/상자를 열어 둔 채 WASD로 걷기");
	}

	@Override
	protected void onDisable() {
		release();
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null || client.options == null) {
			release();
			return;
		}
		Object screen = client.currentScreen;
		if (!(screen instanceof HandledScreen) || (LunaCompat.isCreativeInventory(screen) && creativeSearchActive(screen))
				|| LunaCompat.isTextFieldFocused(screen) || TextCapture.active()) {
			release();
			return;
		}
		Object[] keys = LunaCompat.movementKeys(client);
		for (int i = 0; i < keys.length; i++) {
			Object kb = keys[i];
			if (kb == null) {
				continue;
			}
			// 49-47차(사용자): 웅크리기(5번)는 아예 제외. 화면을 연 채 Shift가 먹으면 상자 옮기기
			// 같은 Shift 조작과 겹쳐서 내려앉았다 일어났다 한다.
			if (i == 5 || (i == 4 && !jump.get()) || (i == 6 && !sprint.get())) {
				continue;
			}
			int code = LunaCompat.boundKeyCode(kb);
			boolean down = code >= 0 && LunaCompat.isKeyPressedAny(client, code);
			LunaCompat.setKeyPressed(kb, down);
		}
		applied = true;
	}

	/** 화면이 닫히거나 조건이 풀리면 우리가 누른 상태를 전부 뗀다(바닐라 unpressAll과 같은 정리). */
	private void release() {
		if (!applied || client == null) {
			return;
		}
		applied = false;
		if (client.currentScreen == null) {
			return;   // 화면이 닫힌 뒤엔 바닐라가 실제 키 이벤트로 다시 관리
		}
		for (Object kb : LunaCompat.movementKeys(client)) {
			LunaCompat.setKeyPressed(kb, false);
		}
	}

	/** 크리에이티브 창의 검색칸이 지금 보이거나 포커스면 true(검색 탭 - 글자를 치는 중이라 이동 키를 안 넣는다). 못 알아내면 true(안전하게 막음). */
	private static boolean creativeSearchActive(Object screen) {
		try {
			for (Class<?> c = screen.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
				for (java.lang.reflect.Field f : c.getDeclaredFields()) {
					if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !net.minecraft.client.gui.widget.TextFieldWidget.class.isAssignableFrom(f.getType())) {
						continue;
					}
					f.setAccessible(true);
					Object box = f.get(screen);
					if (box == null) {
						continue;
					}
					Object vis = LunaCompat.callNoArg(box, "isVisible");
					Object foc = LunaCompat.callNoArg(box, "isFocused");
					if (vis == null && foc == null) {
						return true;
					}
					return Boolean.TRUE.equals(vis) || Boolean.TRUE.equals(foc);
				}
			}
			return false;   // 검색칸이 없는 크리에이티브 창
		} catch (Throwable t) {
			return true;
		}
	}
}
