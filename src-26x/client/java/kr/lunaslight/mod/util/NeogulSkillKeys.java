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

	/** 지금 던전 스킬 모드인가(HUD). */
	public static boolean skillMode() {
		return active() && skillMode && keys != null;
	}

	/** 1~4번 스킬 키(GLFW 번호, 없으면 -1). 숫자키 방식(서버가 키를 안 줌)이면 2~5. */
	public static int keyOf(int slot) {
		int[] k = keys;
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
		if (!skillMode()) {
			return -1;
		}
		int[] k = keys;
		for (int i = 0; i < k.length; i++) {
			if (k[i] == key) {
				return i + 1;
			}
		}
		return -1;
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
		int slot = slotFor(key);
		if (slot < 0) {
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
