package kr.lunaslight.mod.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-401차(사용자: "우리 서버에서는 음성 채팅 사용 안 함을 없애고 마이크 음소거를 N으로"): 내 서버(Module.serverLocked)에 있는 동안
 * Simple Voice Chat의 "음성 채팅 사용 안 함" 키를 풀고 조작 설정 목록에서 뺀다(options.keyMappings에서 빼 둠), "마이크 음소거"는 N.
 * 나오면 원래대로. 바꾸기 전 키는 config/lunaslight/voice-keys.json에 적어 두어 게임을 그 상태로 꺼도 다음 실행에 돌려놓는다.
 * 음성 채팅 모드가 없으면 아무것도 안 한다.
 */
public final class VoiceKeys {
	private VoiceKeys() {
	}

	private static boolean startupChecked;
	private static boolean applied;
	private static KeyMapping mute;
	private static KeyMapping disable;
	private static int disableIndex = -1;
	private static String muteBackup;
	private static String disableBackup;

	private static Path file() {
		return FabricLoader.getInstance().getGameDir().resolve("config").resolve("lunaslight").resolve("voice-keys.json");
	}

	private static boolean isMute(String n) {
		return n != null && n.endsWith("mute_microphone");
	}

	private static boolean isDisable(String n) {
		return n != null && n.endsWith("disable_voice_chat");
	}

	/** 매 틱(ModuleManager): 내 서버 상태에 맞춘다. */
	public static void sync(boolean locked) {
		try {
			Minecraft mc = Minecraft.getInstance();
			if (mc == null || mc.options == null) {
				return;
			}
			if (!startupChecked) {
				startupChecked = true;
				restoreFromFile(mc);
			}
			if (locked && !applied) {
				applied = true;
				apply(mc);
			} else if (!locked && applied) {
				applied = false;
				unapply(mc);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("voiceKeys", t);
		}
	}

	private static void find(Minecraft mc) {
		KeyMapping[] keys = mc.options.keyMappings;
		for (int i = 0; i < keys.length; i++) {
			KeyMapping k = keys[i];
			if (k == null) {
				continue;
			}
			if (mute == null && isMute(k.getName())) {
				mute = k;
			} else if (isDisable(k.getName())) {
				disable = k;
				disableIndex = i;
			}
		}
	}

	private static void apply(Minecraft mc) throws Exception {
		find(mc);
		if (mute == null && disable == null) {
			return;   // 음성 채팅 모드 없음
		}
		muteBackup = mute == null ? null : mute.saveString();
		disableBackup = disable == null ? null : disable.saveString();
		JsonObject o = new JsonObject();
		if (muteBackup != null) {
			o.addProperty("mute", muteBackup);
		}
		if (disableBackup != null) {
			o.addProperty("disable", disableBackup);
		}
		Files.createDirectories(file().getParent());
		Files.writeString(file(), o.toString(), StandardCharsets.UTF_8);
		if (mute != null) {
			mute.setKey(InputConstants.getKey("key.keyboard.n"));
		}
		if (disable != null) {
			disable.setKey(InputConstants.UNKNOWN);
			setKeys(mc, without(mc.options.keyMappings, disable));
		}
		KeyMapping.resetMapping();
		kr.lunaslight.mod.LunaClientMod.LOGGER.info("[Nova] 내 서버 - 음성 채팅 키: 마이크 음소거 N, 사용 안 함 숨김");
	}

	private static void unapply(Minecraft mc) throws Exception {
		if (mute == null && disable == null) {
			return;
		}
		if (mute != null && muteBackup != null && "key.keyboard.n".equals(mute.saveString())) {
			mute.setKey(InputConstants.getKey(muteBackup));
		}
		if (disable != null) {
			if (disableBackup != null) {
				disable.setKey(InputConstants.getKey(disableBackup));
			}
			KeyMapping[] keys = mc.options.keyMappings;
			boolean there = false;
			for (KeyMapping k : keys) {
				there |= k == disable;
			}
			if (!there) {
				KeyMapping[] out = new KeyMapping[keys.length + 1];
				int at = disableIndex < 0 || disableIndex > keys.length ? keys.length : disableIndex;
				System.arraycopy(keys, 0, out, 0, at);
				out[at] = disable;
				System.arraycopy(keys, at, out, at + 1, keys.length - at);
				setKeys(mc, out);
			}
		}
		KeyMapping.resetMapping();
		Files.deleteIfExists(file());
	}

	/** 지난 실행을 내 서버에 있던 채로 껐으면(파일이 남아 있음) 키를 돌려놓는다. */
	@SuppressWarnings("deprecation")
	private static void restoreFromFile(Minecraft mc) {
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try {
			JsonObject o = new JsonParser().parse(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			for (KeyMapping k : mc.options.keyMappings) {
				if (k == null) {
					continue;
				}
				if (isMute(k.getName()) && o.has("mute") && "key.keyboard.n".equals(k.saveString())) {
					k.setKey(InputConstants.getKey(o.get("mute").getAsString()));
				} else if (isDisable(k.getName()) && o.has("disable")) {
					k.setKey(InputConstants.getKey(o.get("disable").getAsString()));
				}
			}
			KeyMapping.resetMapping();
			Files.deleteIfExists(f);
		} catch (Throwable t) {
			LunaCompat.warnOnce("voiceKeys:restore", t);
		}
	}

	private static KeyMapping[] without(KeyMapping[] keys, KeyMapping drop) {
		int n = 0;
		for (KeyMapping k : keys) {
			if (k != drop) {
				n++;
			}
		}
		KeyMapping[] out = new KeyMapping[n];
		int i = 0;
		for (KeyMapping k : keys) {
			if (k != drop) {
				out[i++] = k;
			}
		}
		return out;
	}

	/** options.keyMappings(final)를 바꾼다 - 조작 설정 목록이 이 배열로 만들어진다. */
	private static void setKeys(Minecraft mc, KeyMapping[] keys) throws Exception {
		java.lang.reflect.Field f = net.minecraft.client.Options.class.getDeclaredField("keyMappings");
		f.setAccessible(true);
		f.set(mc.options, keys);
	}
}
