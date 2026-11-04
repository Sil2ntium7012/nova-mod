package kr.lunaslight.mod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 49-552차(사용자: "앞으로는 신규 기능 나오면 맵 들어갔을 때 새로운 기능이 나왔고 킬 거냐고 물어봐"): <b>새 기능 묻기</b>.
 *
 * <p>기능(Module)이 {@code newFeature("태그")}로 새 기능 표시를 하면, 서버나 월드에 들어가고 3초 뒤(창이 안 열려 있을 때) 아직 안 물어본
 * 새 기능을 모아 {@link kr.lunaslight.mod.gui.NewFeatureScreen}으로 "켤까요?"를 묻는다. 너굴마을 묶음 기능은 너굴마을에서만, 던전
 * 안에서는 안 묻고 던전을 나온 뒤에(싸우는 중에 창이 뜨면 위험). 답한 것은 config/lunaslight/new-features.json에 "기능id@태그"로 적는다.
 * 태그를 바꾸면(기능을 크게 바꿨을 때) 다시 묻는다.
 */
public final class NewFeatures {
	private NewFeatures() {
	}

	private static final Set<String> ASKED = new LinkedHashSet<>();
	private static boolean loaded, registered;
	private static long joinAt;

	public static synchronized void init() {
		if (registered) {
			return;
		}
		registered = true;
		try {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> joinAt = System.currentTimeMillis());
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(NewFeatures::tick);
		} catch (Throwable t) {
			LunaCompat.warnOnce("newFeatures:init", t);
		}
	}

	private static void tick(Minecraft mc) {
		if (joinAt == 0 || mc == null || mc.player == null || System.currentTimeMillis() - joinAt < 3000) {
			return;
		}
		if (LunaCompat.screenOf(mc) != null) {
			return;   // 창이 닫히면 묻는다
		}
		try {
			if (NeogulDungeonHud.inDungeon()) {
				return;   // 던전을 나온 뒤에
			}
		} catch (Throwable ignored) {
		}
		joinAt = 0;
		List<Module> list = pending(mc);
		if (!list.isEmpty()) {
			LunaCompat.setScreen(new kr.lunaslight.mod.gui.NewFeatureScreen(list));   // 26.2부터 Minecraft.setScreen 없음
		}
	}

	/** 아직 안 물어본 새 기능(이 판에서 되고, 이 서버에 맞는 것). */
	public static List<Module> pending(Minecraft mc) {
		load();
		List<Module> out = new ArrayList<>();
		boolean neogul = false;
		try {
			neogul = NeogulData.onNeogul(mc);
		} catch (Throwable ignored) {
		}
		for (Module m : ModuleManager.get().all()) {
			String tag = m.newFeatureTag();
			if (tag == null || !m.isVersionSupported() || ASKED.contains(key(m)) || m.newFeatureAsked()) {
				continue;
			}
			if ("너굴마을".equals(m.serverGroup()) && !neogul) {
				continue;
			}
			out.add(m);
		}
		return out;
	}

	public static void markAsked(Module m) {
		load();
		ASKED.add(key(m));
		save();
	}

	private static String key(Module m) {
		return m.getId() + "@" + m.newFeatureTag();
	}

	private static Path file() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lunaslight").resolve("new-features.json");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		try {
			Path p = file();
			if (Files.exists(p)) {
				JsonElement e = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
				if (e.isJsonArray()) {
					for (JsonElement x : e.getAsJsonArray()) {
						ASKED.add(x.getAsString());
					}
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("newFeatures:load", t);
		}
	}

	private static void save() {
		try {
			JsonArray a = new JsonArray();
			for (String s : ASKED) {
				a.add(s);
			}
			Path p = file();
			Files.createDirectories(p.getParent());
			Files.writeString(p, a.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("newFeatures:save", t);
		}
	}
}
