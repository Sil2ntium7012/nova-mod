package kr.lunaslight.mod.util;

import kr.lunaslight.mod.network.NeogulSkillPayloads;
import net.minecraft.client.Minecraft;

/**
 * 49-431차(사용자: "너굴마을 이번 업데이트로 스킬 키 세팅 생겼는데 이거 호환해 줘"): 너굴마을 던전 스킬 키를 노바가 직접 처리한다.
 *
 * <p>너굴마을은 숫자키(2~9)는 모드 없이 받고, 그 밖의 키(F1~F4, R F C V, 직접 고른 키)는 NG-SkillKeydown 모드가 있어야 한다.
 * 그 모드가 하는 일(서버가 {@code ng:skillkey}/{@code ng:skillkey2}로 지금 고른 키를 알려 주면, 그 키를 누를 때 게임에 넘기지 않고
 * {@code ng:skillcast}로 스킬 번호를 보낸다)을 노바가 똑같이 한다 - 노바만 깔아도 /스킬키세팅의 모든 키가 된다.
 * NG-SkillKeydown이 같이 깔려 있으면 노바는 빠진다(같은 채널을 두 번 등록하면 게임이 안 켜지고, 스킬이 두 번 나간다).
 *
 * <p>스킬 모드는 서버가 켠다(던전 안). 화면(채팅, 인벤토리)이 열려 있으면 안 가로챈다.
 */
public final class NeogulSkillKeys {
	private NeogulSkillKeys() {
	}

	/** F1~F4, R F C V(GLFW 키 번호 - 26.1~26.2). */
	private static final int[] KEYS_FKEY = {290, 291, 292, 293};
	private static final int[] KEYS_RFCV = {82, 70, 67, 86};

	private static volatile int[] keys;
	private static volatile boolean skillMode;
	private static boolean registered;
	private static boolean otherMod;
	/** 스킬 번호별 마지막 시전 시각(HUD 반짝임). */
	private static final long[] castAt = new long[5];

	/** 시작 때 한 번(26.1.2 너굴마을 버전에서만). */
	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			// 49-521차: 스킬 키의 마크 기본 동작(F 손 바꾸기 등)을 던전 스킬 모드 동안 막는다 - 아래 drainVanilla
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(NeogulSkillKeys::drainVanilla);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulSkill:drain", t);
		}
		try {
			otherMod = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ng_skillkeydown");
			if (otherMod) {
				kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 너굴 스킬 키: NG-SkillKeydown 모드가 있어 그 모드에 맡김");
				return;
			}
			net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(NeogulSkillPayloads.STATE, NeogulSkillPayloads.STATE_CODEC);
			net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.clientboundPlay().register(NeogulSkillPayloads.STATE2, NeogulSkillPayloads.STATE2_CODEC);
			net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(NeogulSkillPayloads.Cast.TYPE, NeogulSkillPayloads.Cast.CODEC);
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(NeogulSkillPayloads.STATE, (p, ctx) -> receive(p.data()));
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(NeogulSkillPayloads.STATE2, (p, ctx) -> receive2(p.data()));
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
		} catch (Throwable t) {
			otherMod = true;   // 못 붙었으면 아무것도 안 함
			LunaCompat.warnOnce("neogulSkill:init", t);
		}
	}

	/** 노바가 스킬 키를 맡고 있나(다른 모드가 없고 등록됨). */
	public static boolean active() {
		return registered && !otherMod;
	}

	static void receive(byte[] d) {
		if (d == null || d.length < 3 || d[0] != 1) {
			return;
		}
		keys = d[1] == 1 ? KEYS_FKEY : d[1] == 2 ? KEYS_RFCV : null;
		skillMode = d[2] == 1;
	}

	static void receive2(byte[] d) {
		if (d == null || d.length < 18 || d[0] != 2) {
			return;
		}
		java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(d, 2, 16);
		int[] k = new int[4];
		for (int i = 0; i < 4; i++) {
			k[i] = buf.getInt();
		}
		keys = k;
		skillMode = d[1] == 1;
	}

	public static void reset() {
		keys = null;
		skillMode = false;
	}

	// ==================== 49-453차: NG-SkillKeydown 모드가 깔려 있을 때 ====================
	// 사용자: "키 모드 내장한 거 아니었어? 안 되는데 보이지도 않고". 너굴마을 프로필에 NG-SkillKeydown이 깔려 있으면 노바는 채널을 그 모드에
	// 맡기는데(같은 채널을 둘이 등록하면 충돌), 그동안 노바 HUD는 상태를 몰라서 키캡이 한 번도 안 떴다. 이제 그 모드의 상태
	// (kr.mcng.skillkeydown.SkillKeys - skillMode(), keys)를 읽어서 HUD를 띄운다. 키 처리와 시전은 그대로 그 모드가 한다.

	private static boolean extTried;
	private static java.lang.reflect.Method extSkillMode;
	private static java.lang.reflect.Field extKeys;

	private static synchronized void ext() {
		if (extTried) {
			return;
		}
		extTried = true;
		try {
			Class<?> c = Class.forName("kr.mcng.skillkeydown.SkillKeys");
			extSkillMode = c.getMethod("skillMode");
			extKeys = c.getDeclaredField("keys");
			extKeys.setAccessible(true);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulSkill:ext", t);
		}
	}

	private static int[] keysNow() {
		if (!otherMod) {
			return keys;
		}
		ext();
		try {
			return extKeys == null ? null : (int[]) extKeys.get(null);
		} catch (Throwable t) {
			return null;
		}
	}

	private static boolean modeNow() {
		if (!otherMod) {
			return skillMode;
		}
		ext();
		try {
			return extSkillMode != null && Boolean.TRUE.equals(extSkillMode.invoke(null));
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 49-520차(사용자: "/스킬키세팅은 원래 1~4번인데 여기서 더 많이 변경 가능하게 따로 노바 설정에서"): 노바 설정의 스킬별 키(조합키, 마우스 옆 버튼 포함).
	 * -1이면 서버(/스킬키세팅) 키를 따른다. 지정하면 그 스킬은 서버 키 대신 이 키로 쓴다. NeogulSkillKeysModule이 넣는다.
	 */
	public static final int[] OVERRIDE = {-1, -1, -1, -1};

	/** 노바가 직접 시전할 수 있나(NG-SkillKeydown 모드가 없을 때만 - 그 모드가 채널을 쓰고 있으면 노바는 못 보낸다). */
	public static boolean canCast() {
		return registered && !otherMod;
	}

	/** 던전 스킬 모드(서버가 켬) - 숫자키 방식이어도. */
	public static boolean inSkillMode() {
		return registered && modeNow();
	}

	/** 스킬 시전(노바 설정 키 - 마우스 버튼 폴링에서도 부름). */
	public static void cast(int slot) {
		if (!canCast() || slot < 1 || slot > 4) {
			return;
		}
		try {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new NeogulSkillPayloads.Cast(slot));
			castAt[slot] = System.currentTimeMillis();
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulSkill:cast", t);
		}
	}

	/** 지금 던전 스킬 모드인가(HUD). 노바가 맡았든 NG-SkillKeydown 모드가 맡았든. */
	public static boolean skillMode() {
		return registered && modeNow() && keysNow() != null;
	}

	/** 1~4번 스킬 키(GLFW 번호, 없으면 -1). 숫자키 방식(서버가 키를 안 줌)이면 2~5. */
	public static int keyOf(int slot) {
		if (slot >= 1 && slot <= 4 && OVERRIDE[slot - 1] >= 0 && canCast()) {
			return LunaCompat.baseKey(OVERRIDE[slot - 1]);
		}
		int[] k = keysNow();
		if (k == null || slot < 1 || slot > k.length) {
			return -1;
		}
		return k[slot - 1];
	}

	public static long castAt(int slot) {
		return slot >= 1 && slot <= 4 ? castAt[slot] : 0L;
	}

	/** 키 번호 → 스킬 번호(1~4), 아니면 -1. */
	public static int slotFor(int key) {
		// 노바 설정 키가 먼저(숫자키 방식이어도 던전 스킬 모드면)
		if (canCast() && inSkillMode()) {
			Minecraft mc = Minecraft.getInstance();
			int held = LunaCompat.heldModifiersAny(mc);
			for (int i = 0; i < 4; i++) {
				int v = OVERRIDE[i];
				if (v < 0 || LunaCompat.isMouseKeyCode(LunaCompat.baseKey(v)) || LunaCompat.baseKey(v) != key) {
					continue;
				}
				int m = LunaCompat.keyModifiers(v);
				if (m == 0 || (m & held) == m) {
					return i + 1;
				}
			}
		}
		if (!skillMode()) {
			return -1;
		}
		int[] k = keysNow();
		if (k == null) {
			return -1;
		}
		for (int i = 0; i < k.length; i++) {
			if (OVERRIDE[i] >= 0 && canCast()) {
				continue;   // 이 스킬은 노바 설정 키로 바꿨다 - 서버 키는 쉰다
			}
			if (k[i] == key) {
				return i + 1;
			}
		}
		return -1;
	}

	/** 숫자키 방식(서버가 키를 안 줌)일 때 1~4번 스킬의 서버 기본 키: 2, 3, 4, 5. */
	private static final int[] KEYS_NUMBER = {50, 51, 52, 53};

	/** 이 스킬의 서버 키(노바 설정 키와 상관없이). */
	private static int serverKey(int slot) {
		int[] k = keysNow();
		if (k == null) {
			return modeNow() ? KEYS_NUMBER[slot - 1] : -1;
		}
		return slot <= k.length ? k[slot - 1] : -1;
	}

	/** 노바 키로 바꾼 스킬의 서버 기본 키인지(그 키는 스킬도, 마크 동작도 안 한다). */
	private static boolean replacedServerKey(int key) {
		if (!canCast() || !inSkillMode()) {
			return false;
		}
		for (int i = 0; i < 4; i++) {
			if (OVERRIDE[i] >= 0 && serverKey(i + 1) == key && LunaCompat.baseKey(OVERRIDE[i]) != key) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 49-521차(사용자: "쿨타임일 때 누르면 능력 대신 그 키가 눌리던데 그거 없애 줘"): 던전 스킬 모드 동안 스킬 키(서버 키, 노바 키)에 걸린 마크 동작
	 * (F 손 바꾸기, Q 버리기 …)의 눌림을 매 틱 처음에 비운다. NG-SkillKeydown 모드가 쿨타임 중 키를 그냥 넘겨도 마크는 아무것도 안 한다.
	 * 숫자키 방식의 2~5는 서버가 핫바 바꾸기로 스킬을 받으므로 비우지 않는다(노바 키로 바꾼 것만).
	 */
	private static void drainVanilla(Minecraft mc) {
		if (mc == null || mc.player == null || !inSkillMode() || LunaCompat.screenOf(mc) != null) {
			return;
		}
		java.util.Set<Integer> skillKeys = new java.util.HashSet<>();
		int[] k = keysNow();
		for (int i = 0; i < 4; i++) {
			if (k != null && i < k.length && k[i] >= 0) {
				skillKeys.add(k[i]);
			} else if (k == null && OVERRIDE[i] >= 0 && canCast()) {
				skillKeys.add(KEYS_NUMBER[i]);   // 숫자키 방식인데 노바 키로 바꾼 스킬의 숫자
			}
			if (OVERRIDE[i] >= 0 && canCast() && !LunaCompat.isMouseKeyCode(LunaCompat.baseKey(OVERRIDE[i]))) {
				skillKeys.add(LunaCompat.baseKey(OVERRIDE[i]));
			}
		}
		if (skillKeys.isEmpty()) {
			return;
		}
		try {
			for (net.minecraft.client.KeyMapping km : mc.options.keyMappings) {
				Object bk = LunaCompat.boundKeyOfBinding(km);
				if (bk instanceof com.mojang.blaze3d.platform.InputConstants.Key key && "KEYSYM".equals(key.getType().name())
						&& skillKeys.contains(key.getValue())) {
					while (km.consumeClick()) {
						// 비움
					}
				}
			}
			// 핫바 숫자키는 KeyMapping(keyHotbarSlots)이라 위에서 같이 비워진다
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulSkill:drainVanilla", t);
		}
	}

	/** 키 이벤트(KeyboardKeyMixin 맨 앞). true면 게임에 넘기지 않는다. action 1 누름, 2 반복, 0 뗌. */
	public static boolean onKey(int key, int action) {
		if (action == 0 || key < 0) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || LunaCompat.screenOf(mc) != null) {
			return false;
		}
		// 49-521차(사용자: "기본 키들은 능력 안 나가게"): 노바 키로 바꾼 스킬의 서버 기본 키는 먹기만 한다(스킬 X, 마크 동작 X)
		if (replacedServerKey(key)) {
			return true;
		}
		int slot = slotFor(key);
		if (slot < 0) {
			return false;
		}
		if (otherMod) {
			// 49-453차: 시전은 NG-SkillKeydown 모드가 한다 - 노바는 HUD 반짝임만
			if (action == 1) {
				castAt[slot] = System.currentTimeMillis();
			}
			return false;
		}
		if (action == 1) {
			try {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new NeogulSkillPayloads.Cast(slot));
				castAt[slot] = System.currentTimeMillis();
			} catch (Throwable t) {
				LunaCompat.warnOnce("neogulSkill:cast", t);
			}
		}
		return true;
	}
}
