package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 49-518차(사용자: 던전 UI - 스킬 카드, 쿨타임, 레벨/경험치 게이지, 지역이 "던전"인지): 너굴마을 서버는 이 HUD를 액션바, 보스바,
 * 타이틀 글자(ng:hud 글꼴의 개인 영역 글자 - 스킬 카드 U+E210~, 쿨타임 숫자 U+E230~, 지역 U+E140~)로 그린다. 노바가 이걸 읽어 다시 그리려면
 * 실제로 어떤 글자가 어떤 순서로 오는지 알아야 해서, 너굴마을에 있는 동안 바뀔 때마다 기록한다.
 *
 * <p>파일: config/lunaslight/neogul-hud-probe.txt(2MB에서 멈춤). 줄마다 시각, 종류(actionbar/boss/title/subtitle), 글자(개인 영역 글자는
 * \\uXXXX로), 조각별 글꼴. 1초에 한 번 보고 같은 글은 다시 안 적는다(쿨타임 숫자처럼 바뀌는 것만 남는다).
 */
public final class NeogulHudProbe {
	private NeogulHudProbe() {
	}

	private static final long MAX_BYTES = 2L * 1024 * 1024;
	private static final Map<String, String> LAST = new HashMap<>();
	private static boolean registered;
	private static boolean full;
	private static int tick;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(NeogulHudProbe::onTick);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulProbe:init", t);
		}
	}

	private static void onTick(Minecraft mc) {
		if (full || ++tick % 20 != 0 || mc == null || mc.player == null) {
			return;
		}
		try {
			if (!NeogulData.onNeogul(mc)) {
				return;
			}
			Object gui = mc.gui;
			note("actionbar", LunaCompat.getFieldValue(gui, "overlayMessageString"));
			note("title", LunaCompat.getFieldValue(gui, "title"));
			note("subtitle", LunaCompat.getFieldValue(gui, "subtitle"));
			Object boss = LunaCompat.getFieldValue(gui, "bossOverlay");
			Object events = boss == null ? null : LunaCompat.getFieldValue(boss, "events");
			if (events instanceof Map<?, ?> m) {
				int i = 0;
				for (Object ev : m.values()) {
					if (ev instanceof net.minecraft.world.BossEvent be) {
						note("boss" + i++, be.getName());
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulProbe:tick", t);
		}
	}

	private static void note(String kind, Object o) {
		if (!(o instanceof Component c)) {
			return;
		}
		StringBuilder text = new StringBuilder();
		StringBuilder fonts = new StringBuilder();
		c.visit((FormattedText.StyledContentConsumer<Object>) (style, s) -> {
			for (int i = 0; i < s.length(); ) {
				int cp = s.codePointAt(i);
				if ((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000 || cp < 0x20) {
					text.append(String.format("\\u%04X", cp));
				} else {
					text.appendCodePoint(cp);
				}
				i += Character.charCount(cp);
			}
			if (!s.isEmpty()) {
				Object f = style.getFont();
				fonts.append(f == null ? "-" : f.toString().replaceAll("\\s+", "")).append('[').append(s.length()).append("] ");
			}
			return Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		String line = text.toString();
		if (line.isEmpty() || line.equals(LAST.get(kind))) {
			return;
		}
		LAST.put(kind, line);
		write(String.format("%tT %s | %s | %s%n", System.currentTimeMillis(), kind, line, fonts.toString().trim()));
	}

	private static void write(String s) {
		try {
			Path p = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("neogul-hud-probe.txt");
			Files.createDirectories(p.getParent());
			if (Files.exists(p) && Files.size(p) > MAX_BYTES) {
				full = true;
				return;
			}
			Files.writeString(p, s, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulProbe:write", t);
		}
	}
}
