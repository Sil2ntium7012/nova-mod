package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-534차(관리자 요청: 친구 angryjin에게 장난 - "패치 이후 처음 나오는 허수아비 때 공포스러운 귀신 화면이랑 소리 딱 1번만"):
 * 너굴마을에서 닉네임이 angryjin인 사람에게만, 이 패치 뒤 처음 "허수아비가 나타났습니다" 채팅이 오면 2초 동안 귀신 얼굴 + 비명 소리를 한 번 보여 준다.
 * 한 번 보여 주면 config/lunaslight/.scarecrow-surprise-done 을 남겨 다시는 안 나온다. 깜빡임(번쩍임)은 없다 - 어두워지며 얼굴이 떠올랐다 사라진다.
 */
public final class NeogulScare {
	private NeogulScare() {
	}

	private static final String TARGET = "angryjin";
	private static final long SHOW_MS = 2000L;
	private static boolean registered;
	private static long shownAt;
	private static Boolean done;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			ChatState.addPlainListener(NeogulScare::onChat);
			LunaCompat.registerHudRenderCallback((ctx, tick) -> draw(ctx));
		} catch (Throwable t) {
			LunaCompat.warnOnce("scare:init", t);
		}
	}

	private static Path flag() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve(".scarecrow-surprise-done");
	}

	private static boolean done() {
		if (done == null) {
			try {
				done = Files.exists(flag());
			} catch (Throwable t) {
				done = true;
			}
		}
		return done;
	}

	private static void onChat(String line) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (line == null || mc == null || mc.player == null || done() || shownAt != 0) {
				return;
			}
			if (!TARGET.equalsIgnoreCase(mc.player.getName().getString()) || !NeogulData.onNeogul(mc)) {
				return;
			}
			String l = line.replace(" ", "");
			if (line.startsWith("<") || !l.contains("허수아비") || !l.contains("나타났")) {
				return;
			}
			shownAt = System.currentTimeMillis();
			done = true;
			try {
				Files.createDirectories(flag().getParent());
				Files.writeString(flag(), "shown " + java.time.LocalDateTime.now());
			} catch (Throwable ignored) {
			}
			LunaCompat.playUiSound(mc, "ENTITY_GHAST_SCREAM", 0.6f);
			LunaCompat.playUiSound(mc, "ENTITY_WARDEN_ROAR", 0.8f);
		} catch (Throwable t) {
			LunaCompat.warnOnce("scare:chat", t);
		}
	}

	/** 귀신 얼굴(16 x 20 칸). '.' 투명, 'h' 머리카락, 'f' 얼굴, 's' 그늘, 'e' 눈구멍, 'r' 눈동자, 'm' 입. */
	private static final String[] FACE = {
		"....hhhhhhhh....",
		"..hhhhhhhhhhhh..",
		".hhhhffffffhhhh.",
		".hhhffffffffhhh.",
		"hhhffffffffffhhh",
		"hhffeeeffeeeffhh",
		"hhfeeeesfeeeefhh",
		"hhfeereseereeshh",
		"hhfeeeesfeeeefhh",
		"hhffeesffseeffhh",
		"hhfffssffssfffhh",
		"hhffffsffsffffhh",
		"hhfffmmmmmmfffhh",
		"hhffmmmmmmmmffhh",
		"hhffmmmmmmmmffhh",
		"hhfffmmmmmmfffhh",
		"hhhffsmmmmsffhhh",
		"hhhhfffssfffhhhh",
		".hhhhhffffhhhhh.",
		"..hhh.hhhh.hhh..",
	};

	private static void draw(GuiGraphicsExtractor ctx) {
		if (shownAt == 0) {
			return;
		}
		long age = System.currentTimeMillis() - shownAt;
		if (age > SHOW_MS) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		// 어두움: 0.15초에 확 어두워지고 마지막 0.5초에 걷힘
		float a = age < 150 ? age / 150f : age > SHOW_MS - 500 ? Math.max(0f, (SHOW_MS - age) / 500f) : 1f;
		try {
			ctx.nextStratum();
		} catch (Throwable ignored) {
		}
		ctx.fill(0, 0, sw, sh, (Math.round(225 * a) << 24) | 0x080003);
		// 얼굴: 작게 시작해 빠르게 다가오고 살짝 떨림
		float grow = Math.min(1f, age / 350f);
		int px = Math.max(2, Math.round(Math.min(sw / 16f, sh / 20f) * (0.55f + 0.4f * grow)));
		int fw = 16 * px, fh = 20 * px;
		int shake = age < 1500 ? (int) (Math.sin(age / 18.0) * px * 0.25) : 0;
		int x0 = (sw - fw) / 2 + shake, y0 = (sh - fh) / 2;
		int alpha = Math.round(255 * a) << 24;
		for (int r = 0; r < FACE.length; r++) {
			String row = FACE[r];
			for (int c = 0; c < row.length(); c++) {
				int col = switch (row.charAt(c)) {
					case 'h' -> 0x0B0A0C;
					case 'f' -> 0xD9DDD6;
					case 's' -> 0x8C918A;
					case 'e' -> 0x050505;
					case 'r' -> 0xB0100C;
					case 'm' -> 0x120406;
					default -> -1;
				};
				if (col < 0) {
					continue;
				}
				ctx.fill(x0 + c * px, y0 + r * px, x0 + (c + 1) * px, y0 + (r + 1) * px, alpha | col);
			}
		}
	}
}
