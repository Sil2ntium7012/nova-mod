package kr.lunaslight.mod.util;

import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.network.LunaKeyPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 49-374차: LUNA FESTIVAL 서버(내 서버 + 채팅 탭이 켜진 연결 = {@link LunaChatTabs#isActive()})에서만 쓰는 손맛 기능.
 * <ul>
 *   <li>요청서 luna-key-c.md: C / X 키 → 서버로 {@code luna:key} "C" / "X"(LunaFestivalModule이 키를 본다).</li>
 *   <li>요청서 luna-mod-requests-2.md 1: 핫바 7·8·9번(인덱스 6, 7, 8 = Q·F·C 칸)에 능력 아이템(49-402차, luna: 모델)이 하나라도 있으면
 *       휠은 그 칸을 건너뛰고(6번에서 앞으로 → 1번, 1번에서 뒤로 → 6번), 숫자 7·8·9는 무시. 크리에이티브·관전은 그대로.
 *       자리막이 = minecraft:item_model 이 luna:empty 인 아이템. 서버도 같은 규칙으로 되돌리지만 핑만큼 테두리가 튀어서 미리 막는다.</li>
 * </ul>
 */
public final class LunaFest {
	private LunaFest() {
	}

	private static boolean keyReady;

	public static void init() {
		try {
			PayloadTypeRegistry.serverboundPlay().register(LunaKeyPayload.TYPE, LunaKeyPayload.CODEC);
			keyReady = true;
		} catch (Throwable t) {
			LunaClientMod.LOGGER.info("[Nova] luna:key 등록 실패 - C/X 키 신호를 못 보냅니다: " + t);
		}
	}

	/** 서버로 키 신호 하나. 이 서버가 아니면 안 보낸다. */
	public static void sendKey(String key) {
		if (!keyReady || !LunaChatTabs.isActive()) {
			return;
		}
		try {
			if (Minecraft.getInstance().getConnection() != null) {
				ClientPlayNetworking.send(new LunaKeyPayload(key));
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaFest:key", t);
		}
	}

	// ==================== 핫바 Q·F·C 칸 건너뛰기 ====================

	/**
	 * 49-402차(사용자: "특정 게임 아니면 선택되게"): 능력 게임에서만 7·8·9번에 능력 아이템(item_model이 luna: 쪽, 자리막이 luna:empty
	 * 제외)이 들어간다. 보트, 술래잡기, 스플리프처럼 7·8·9번에 보통 아이템이 들어가는 게임은 1~6번처럼 고를 수 있어야 해서,
	 * 이제 능력 아이템이 하나라도 있을 때만 막는다(예전: 자리막이 아닌 아이템이 하나라도 있으면 막음).
	 */
	private static boolean abilityItem(ItemStack s) {
		if (s == null || s.isEmpty()) {
			return false;
		}
		try {
			Object m = s.get(DataComponents.ITEM_MODEL);
			String id = m == null ? "" : m.toString();
			return id.startsWith("luna:") && !"luna:empty".equals(id);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 지금 7·8·9번 칸을 못 고르게 할까. */
	private static boolean locked() {
		if (!LunaChatTabs.isActive()) {
			return false;
		}
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
				return false;
			}
			Inventory inv = mc.player.getInventory();
			for (int i = 6; i <= 8; i++) {
				if (abilityItem(inv.getItem(i))) {
					return true;
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaFest:locked", t);
		}
		return false;
	}

	private static int current() {
		try {
			Minecraft mc = Minecraft.getInstance();
			return mc.player == null ? 0 : mc.player.getInventory().getSelectedSlot();
		} catch (Throwable t) {
			return 0;
		}
	}

	/** HotbarSkipScrollMixin: 휠로 고를 칸. 7·8·9번이면 굴린 방향으로 건너뛴다. */
	public static int scrollSlot(int target) {
		if (target < 6 || target > 8 || !locked()) {
			return target;
		}
		int cur = current();
		int delta = ((target - cur) % 9 + 9) % 9;
		return delta >= 1 && delta <= 4 ? 0 : 5;   // 앞으로(6번 → 1번) / 뒤로(1번 → 6번)
	}

	/** HotbarSkipKeyMixin: 숫자 키로 고를 칸. 7·8·9번이면 지금 칸 그대로. */
	public static int keySlot(int target) {
		if (target < 6 || target > 8 || !locked()) {
			return target;
		}
		return current();
	}
}
