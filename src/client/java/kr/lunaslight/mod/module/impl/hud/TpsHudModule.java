package kr.lunaslight.mod.module.impl.hud;

// ⚠️ 중요한 한계 (지어내지 않고 명시): 바닐라/서버 API로는 클라이언트가 실제 서버 TPS를 직접 알 방법이 없습니다
// (플러그인 API나 서버 모드가 별도로 노출해야 함). 이 모듈은 클라이언트가 받는 채팅/시스템 메시지 중
// "TPS" 또는 "틱" 뒤에 숫자가 오는 패턴을 정규식 1개로 감지해서 최근 값을 저장, 표시하는 방식입니다.
// 그런 메시지가 없는 서버에서는 항상 "TPS 정보 없음"이 표시됩니다. 완벽한 파서가 아니며 오탐/미탐이 있을 수 있습니다.
//
// ⚠️ 컴파일 확인 필요: 채팅 메시지 수신 훅으로 net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
// (GAME 또는 ALLOW_GAME 이벤트, Text 파라미터를 받음)를 사용한다고 가정했습니다. 이 클래스/이벤트명이
// Fabric API 0.106.1+1.21.1 에 정확히 존재하는지, 파라미터 시그니처가 (Text message, boolean overlay)인지
// 확신하지 못해 표시합니다. 만약 없다면 net.minecraft.client.gui.hud.ChatHud쪽을 mixin해야 할 수 있습니다.

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TpsHudModule extends Module {

    private final PositionSetting position = register(new PositionSetting(
            "position", "위치", "표시 위치입니다.",
            HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 38)));

    private final ColorSetting textColor = register(new ColorSetting(
            "text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));

    // 예: "TPS: 19.8", "Current TPS 20.0", "틱: 19.9" 등을 대략 감지하는 단순 정규식
    private static final Pattern TPS_PATTERN = Pattern.compile(
            "(?i)(?:tps|틱)\\D{0,5}(\\d{1,3}(?:\\.\\d{1,2})?)");

    private String lastTpsText = null;

    public TpsHudModule() {
        super("tps_hud", "TPS", ModuleCategory.HUD, "서버 틱 속도(추정)");

        // 49-216차: ChatEvents(시대별 두 벌) - 1.14.4~1.19.2에서도 들어간다
        kr.lunaslight.mod.util.ChatEvents.register((Text message) -> {
            if (!isEnabled()) return;
            String raw = message.getString();
            Matcher matcher = TPS_PATTERN.matcher(raw);
            if (matcher.find()) {
                lastTpsText = matcher.group(1);
            }
        });
        enableHudStyle();
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        String text = lastTpsText != null ? ("TPS: " + lastTpsText) : "TPS 정보 없음";
        int textWidth = kr.lunaslight.mod.util.LunaCompat.getTextWidth(client.textRenderer, text);
        int x = position.get().resolveX(kr.lunaslight.mod.util.WindowAccess.of(client).getScaledWidth(), textWidth);
        int y = position.get().resolveY(kr.lunaslight.mod.util.WindowAccess.of(client).getScaledHeight(), client.textRenderer.fontHeight);
        drawHudLine(context, text, x, y, textColor.getArgb());
    }
}
