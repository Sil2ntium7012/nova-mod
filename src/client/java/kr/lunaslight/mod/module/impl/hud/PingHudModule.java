package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
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
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (client.player == null) {
			return;
		}
		ClientPlayNetworkHandler handler = client.getNetworkHandler();
		int latency;
		if (isPreview()) {
			latency = 42; // 49-22차 미리보기 예시
		} else {
			if (handler == null) {
				return;
			}
			PlayerListEntry entry = handler.getPlayerListEntry(client.player.getUuid());
			latency = entry != null ? entry.getLatency() : -1;
		}

		String text = latency >= 0 ? ("Ping: " + latency + "ms") : "Ping: --";
		int textWidth = LunaCompat.getTextWidth(client.textRenderer, text);
		int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), textWidth);
		int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), client.textRenderer.fontHeight);
		drawHudLine(context, text, x, y, textColor.getArgb());
	}
}
