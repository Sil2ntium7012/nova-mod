package kr.lunaslight.mod.module.impl.misc;

import com.google.gson.JsonObject;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.DiscordIpc;
import kr.lunaslight.mod.util.LunaCompat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 49-125차(사용자: "디스코드에 플레이중 표시 기능"): <b>디스코드 상태</b>.
 *
 * <p>디스코드 프로필에 "Luna's Light - ○○ 서버 플레이 중 | 네더 | 12:34 경과"처럼 게임 안의 상태를 띄운다.
 * 모드가 디스코드 앱과 직접 통신하므로({@link DiscordIpc}) 런처를 꺼 둬도(마크를 켤 때 런처 종료 설정) 뜬다.
 *
 * <p>런처도 자기 연결로 "런처에서 대기 중 / ○○ 플레이 중"을 띄우는데, 둘 다 뜨면 겹친다. 그래서 모드가 디스코드에
 * 붙어 있는 동안 게임 폴더에 {@code .luna-discord.json}(5초마다 갱신)을 남기고, 런처는 이 파일이 살아 있으면
 * 자기 표시를 내려 놓는다(모드에 양보). 게임이 꺼지면 런처가 다시 "메인 화면"을 띄운다.
 */
public class DiscordStatusModule extends Module {

	private static final String INVITE = "https://discord.gg/PVkq8jQdeF";
	/**
	 * 49-186차(사용자: "디코 상태 잘 뜨는데 아이콘이 ? 박스로 떠"): 그림 이름(luna_logo, status_idle 등)은 디스코드 개발자
	 * 포털의 Art Assets에 올린 그림이어야 하는데 올라가 있지 않아 물음표 상자가 떴다. 디스코드는 그림 자리에 https 주소도
	 * 받으므로 런처 저장소에 이미 공개돼 있는 Luna 아이콘을 주소로 쓴다. 작은 그림은 올린 그림이 없어 뺀다.
	 */

	private final BooleanSetting showServer = register(new BooleanSetting(
			"show_server", "서버 이름", "어느 서버(또는 싱글 월드)에서 플레이 중인지 표시합니다.", true));

	private final BooleanSetting showAddress = register(new BooleanSetting(
			"show_address", "서버 주소", "서버 이름 대신 접속 주소를 표시합니다.", false));

	// 49-263차(사용자: "서버에서 오버월드라고만 떠, 서버 주소 뜨게"): 서버에 있으면 둘째 줄에 접속 주소
	private final BooleanSetting addressLine = register(new BooleanSetting(
			"address_line", "서버 주소 줄", "서버에 있으면 둘째 줄에 접속 주소를 보여 줍니다.", true));

	private final BooleanSetting showDimension = register(new BooleanSetting(
			"show_dimension", "차원", "오버월드, 네더, 엔드 중 어디에 있는지 표시합니다.", true));

	private final BooleanSetting showElapsed = register(new BooleanSetting(
			"show_elapsed", "경과 시간", "월드에 들어온 뒤 지난 시간을 표시합니다.", true));

	private final BooleanSetting showButton = register(new BooleanSetting(
			"show_button", "참여 버튼", "프로필에 '디스코드 참여하기' 버튼을 붙입니다.", true));

	private int ticks;
	private Object lastWorld;
	private long worldSince;
	private long menuSince = System.currentTimeMillis() / 1000L;
	private long lastMarkerMs;

	public DiscordStatusModule() {
		super("discord_status", "디스코드 상태", ModuleCategory.FEATURE, "디스코드 프로필에 플레이 중 표시");
		defaultEnabled(true);
	}

	@Override
	protected void onEnable() {
		ticks = 0;
		update();
	}

	@Override
	protected void onDisable() {
		DiscordIpc.stop();
		deleteMarker();
	}

	@Override
	public void onTick() {
		if (client == null) {
			return;
		}
		if (++ticks % 40 == 0) {   // 2초마다(바뀐 게 없으면 디스코드엔 안 보냄)
			update();
		}
		long now = System.currentTimeMillis();
		if (now - lastMarkerMs > 5000) {
			lastMarkerMs = now;
			writeMarker();
		}
	}

	private void update() {
		try {
			DiscordIpc.setActivity(build());
		} catch (Throwable t) {
			LunaCompat.warnOnce("discordStatus:update", t);
		}
	}

	private JsonObject build() {
		Object world = client.world;
		if (world != lastWorld) {
			lastWorld = world;
			worldSince = System.currentTimeMillis() / 1000L;
		}
		String button = showButton.get() ? "디스코드 참여하기" : null;
		if (world == null || client.player == null) {
			return DiscordIpc.activity("메인 화면", null, showElapsed.get() ? menuSince : 0,
					kr.lunaslight.mod.util.LunaSocial.logoUrl(), "Nova Client", null, null, button, INVITE);
		}
		boolean single = LunaCompat.isSinglePlayer(client);
		String details;
		if (single) {
			String name = showServer.get() ? LunaCompat.currentServerLabel(client) : null;
			details = name == null || name.isEmpty() || "싱글플레이".equals(name) ? "싱글플레이" : "싱글플레이 | " + name;
		} else if (showServer.get()) {
			String label = serverLabel();
			details = label == null || label.isEmpty() ? "멀티플레이" : label + " 플레이 중";
		} else {
			details = "멀티플레이";
		}
		String state = null;
		if (!single && addressLine.get()) {
			String addr = LunaCompat.currentServerAddress(client);
			if (addr != null && !addr.isEmpty() && !details.contains(addr)) {
				state = addr;
			}
		}
		String dim = showDimension.get() ? dimensionName() : null;
		if (dim != null) {
			state = state == null ? dim : state + " | " + dim;
		}
		boolean paused = client.currentScreen != null && client.currentScreen.getClass().getSimpleName().matches(".*(GameMenu|Pause).*");
		if (paused) {
			state = state == null ? "일시정지" : state + " | 일시정지";
		}
		return DiscordIpc.activity(details, state, showElapsed.get() ? worldSince : 0,
				kr.lunaslight.mod.util.LunaSocial.logoUrl(), "Nova Client", null, null, button, INVITE);
	}

	/** 서버 목록에 적힌 이름(주소 표시를 켜면 주소). */
	private String serverLabel() {
		try {
			Object entry = LunaCompat.callNoArg(client, "getCurrentServerEntry");
			if (entry == null) {
				return null;
			}
			Object addr = LunaCompat.getFieldValue(entry, "address");
			Object name = LunaCompat.getFieldValue(entry, "name");
			if (showAddress.get() && addr instanceof String a && !a.isEmpty()) {
				return a;
			}
			String shown = LunaCompat.currentServerDisplayName(client);   // 49-256차: 기본 이름이면 서버 목록 이름
			if (shown != null) {
				return shown;
			}
			return addr instanceof String a ? a : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private String dimensionName() {
		try {
			Object key = LunaCompat.invokeNoArg(client.world, "getRegistryKey");
			Object value = key == null ? null : LunaCompat.invokeNoArg(key, "getValue");
			String id = value == null ? "" : String.valueOf(value);
			if (id.endsWith("overworld")) {
				return "오버월드";
			}
			if (id.endsWith("the_nether")) {
				return "네더";
			}
			if (id.endsWith("the_end")) {
				return "엔드";
			}
			return id.isEmpty() ? null : id.substring(id.indexOf(':') + 1);
		} catch (Throwable t) {
			return null;
		}
	}

	// ==================== 런처에 양보 표시 ====================

	private static Path markerFile() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(".luna-discord.json");
	}

	/** 디스코드에 붙어 있으면 런처가 자기 표시를 내리게 파일을 남긴다(끊겨 있으면 안 남김 - 런처 표시가 그대로 보이게). */
	private void writeMarker() {
		try {
			if (!DiscordIpc.isConnected()) {
				return;
			}
			JsonObject o = new JsonObject();
			o.addProperty("ts", System.currentTimeMillis());
			o.addProperty("active", true);
			Files.writeString(markerFile(), o.toString(), StandardCharsets.UTF_8);
		} catch (Throwable t) {
			LunaCompat.warnOnce("discordStatus:marker", t);
		}
	}

	private void deleteMarker() {
		try {
			Files.deleteIfExists(markerFile());
		} catch (Throwable ignored) {
		}
	}
}
