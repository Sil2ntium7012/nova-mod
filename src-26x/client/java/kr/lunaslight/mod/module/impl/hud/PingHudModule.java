package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import kr.lunaslight.mod.util.LunaCompat;

/**
 * ⚠️ 컴파일 확인 필요: ClientPlayNetworkHandler#getPlayerListEntry(UUID)와
 * PlayerListEntry#getLatency()는 Yarn 매핑 기준으로 널리 알려진 공개 API입니다. 싱글플레이/로컬
 * 서버에서는 PlayerListEntry가 없거나 latency가 0으로 나올 수 있어 null 체크를 넣었습니다.
 */
public class PingHudModule extends Module {

	private final PositionSetting position;
	private final ColorSetting textColor;

	public PingHudModule() {
		super("ping_hud", "핑", ModuleCategory.HUD, "서버 응답 시간");
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 21)));
		textColor = register(new ColorSetting("text_color", "글자 색", "글자의 색입니다.", LunaTheme.HUD_TEXT_DEFAULT));
		enableHudStyle();
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (client.player == null) {
			return;
		}
		ClientPacketListener handler = client.getConnection();
		int latency;
		if (isPreview()) {
			latency = 42; // 49-22차 미리보기 예시
		} else {
			if (handler == null) {
				return;
			}
			PlayerInfo entry = handler.getPlayerInfo(client.player.getUUID());
			latency = entry != null ? entry.getLatency() : -1;
		}

		String text = latency >= 0 ? ("Ping: " + latency + "ms") : "Ping: --";
		int textWidth = LunaCompat.getTextWidth(client.font, text);
		int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), textWidth);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}
}
