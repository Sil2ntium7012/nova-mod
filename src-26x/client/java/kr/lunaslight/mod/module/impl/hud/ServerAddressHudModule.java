package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-48차: 서버 주소 - 지금 접속한 서버를 HUD에 띄운다(여러 서버를 오가면 어디인지 헷갈릴 때).
 *
 * 이름(서버 목록에 적어 둔 것)과 주소(host:port) 중에서 고를 수 있다.
 *
 * <p>49-122차(사용자: "서버 주소 가리기 없애고, 싱글+LAN 안 켰을 땐 이 기능 못 쓰게"): [주소 가리기] 설정을
 * 없앴고, <b>싱글플레이인데 LAN도 안 열려 있으면</b>(= 보여 줄 서버 주소가 없을 때) 아무것도 그리지 않는다.
 */
public class ServerAddressHudModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 149)));
	private final ColorSetting textColor = register(new ColorSetting(
			"text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
	// 49-53차(4-16): 예전엔 [이름으로]가 주소를 **대신** 했다(그래서 이름이 있으면 주소를 볼 수 없었다).
	// 이제 주소는 늘 아랫줄에 있고, 이름·버전은 각각 켜고 끌 수 있는 윗줄이다.
	private final BooleanSetting showName = register(new BooleanSetting(
			"show_name", "서버 이름", "서버 목록에 적어 둔 이름을 주소 윗줄에 보여줍니다.", true));
	private final BooleanSetting showVersion = register(new BooleanSetting(
			"show_version", "버전", "마인크래프트 버전을 주소 윗줄에 같이 보여줍니다.", false));

	public ServerAddressHudModule() {
		super("server_address_hud", "서버 주소", ModuleCategory.HUD, "지금 접속한 서버");
		enableHudStyle();
	}

	/** 윗줄(이름 · 버전). 둘 다 꺼져 있으면 null - 그러면 주소 한 줄만 나온다. */
	private String topLine(String name) {
		String version = showVersion.get() ? kr.lunaslight.mod.util.LunaVersion.current() : null;
		boolean hasName = showName.get() && name != null && !name.isEmpty();
		if (hasName && version != null) {
			return name + "  §7" + version;
		}
		if (hasName) {
			return name;
		}
		return version == null ? null : "§7" + version;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		String name;
		String address;
		if (isPreview()) {
			name = "하이의 놀이터";
			address = "play.example.net";
		} else {
			if (client.level == null) {
				return;
			}
			if (LunaCompat.isSinglePlayer(client)) {
				// 49-122차: 싱글이면서 LAN도 안 열려 있으면 보여 줄 서버 주소가 없다 - 이 기능은 안 뜬다.
				if (!LunaCompat.isLanOpen(client)) {
					return;
				}
				String label = LunaCompat.currentServerLabel(client);
				name = label == null || label.isEmpty() ? "LAN 서버" : label;
				address = null;   // LAN 공개 월드 - 이름만
			} else {
				// 49-256차: 기본 이름("Minecraft 서버" 등)이면 서버 목록에 저장된 이름을, 그것도 없으면 이름 없이 주소만
				name = LunaCompat.currentServerDisplayName(client);
				address = LunaCompat.currentServerAddress(client);
			}
		}

		java.util.List<String> lines = new java.util.ArrayList<>(2);
		String top = topLine(name);
		if (top != null) {
			lines.add(top);
		}
		if (address != null && !address.isEmpty()) {
			lines.add(address);
		}
		if (lines.isEmpty()) {
			return;
		}
		int w = hudLinesWidth(lines);
		int h = hudLinesHeight(lines);
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), w);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), h);
		drawHudLines(context, lines, x, y, textColor.getArgb());
	}
}
