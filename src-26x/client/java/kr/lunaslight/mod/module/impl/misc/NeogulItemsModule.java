package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.NeogulItemsScreen;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ActionSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * 49-133차(사용자: "너굴 아이템 기본 키: R - 해당 키를 누르면 너굴마을에 존재하는 모든 아이템 및 도구 등의 출처,
 * 즉 아이템의 경로를 모두 확인할 수 있음. JEI 느낌, 마크 UI 그대로"): 키 하나로 {@link NeogulItemsScreen}을 연다.
 *
 * <p>틱마다 조금씩(2초에 한 번) 열린 화면의 슬롯이나 내 인벤토리를 훑어, 자료와 이름이 같은 아이템의 실제 모양을
 * 아이콘으로 배운다({@link NeogulData#learn}) - NPC 상점 창을 한 번 열어 보기만 해도 서버 모양 아이콘으로 바뀐다.
 */
public class NeogulItemsModule extends Module {

	private final KeybindSetting openKey = register(new KeybindSetting(
			"open_key", "열기 키", "너굴 아이템 창을 여는 키입니다. 창에서 한 번 더 누르면 닫힙니다.", InputConstants.KEY_R));

	private boolean wasDown;
	private int ticks;

	public NeogulItemsModule() {
		super("neogul_items", "너굴 아이템", ModuleCategory.SERVER, "R 키로 너굴마을 아이템의 출처와 제작법 보기");
		defaultEnabled(true);
		serverGroup("너굴마을");
		register(new ActionSetting("reload", "자료 갱신",
				"설정 폴더의 lunaslight/neogul_items.json을 고친 뒤 누르면 다시 읽습니다.", "갱신", NeogulData::reload));
	}

	private boolean active() {
		return NeogulData.onNeogul(client);   // 49-167차: 너굴마을 접속 중에만(설정 아님)
	}

	@Override
	public void onTick() {
		if (client == null || client.player == null) {
			wasDown = false;
			return;
		}
		Object screen = kr.lunaslight.mod.util.LunaCompat.screenOf(client);
		if (++ticks % 40 == 0 && active()) {
			learnIcons(screen);
		}
		boolean down = screen == null && openKey.isBound() && openKey.isDown(client);
		if (down && !wasDown && active()) {
			LunaCompat.setScreen(new NeogulItemsScreen(openKey.getKeyCode()));
		}
		wasDown = down;
	}

	private void learnIcons(Object screen) {
		try {
			// 열린 GUI(상자, NPC 상점 …)의 슬롯 - HandledScreen 이름이 버전마다 달라 리플렉션으로
			Object handler = screen == null ? null : LunaCompat.callNoArg(screen, "getMenu");
			Object slots = handler == null ? null : LunaCompat.getFieldValue(handler, "slots");
			if (slots instanceof java.util.List<?> list) {
				for (Object slot : list) {
					if (LunaCompat.callNoArg(slot, "getItem") instanceof ItemStack st) {
						NeogulData.learn(st);
					}
				}
				return;
			}
			Object inv = LunaCompat.getPlayerInventory(client.player);
			int n = Math.min(41, LunaCompat.invSize(inv));
			for (int i = 0; i < n; i++) {
				NeogulData.learn(LunaCompat.invGetStack(inv, i));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogul:learn", t);
		}
	}
}
