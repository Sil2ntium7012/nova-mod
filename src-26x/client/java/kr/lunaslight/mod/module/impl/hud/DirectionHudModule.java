package kr.lunaslight.mod.module.impl.hud;

// ⚠️ 컴파일 확인 필요: client.player.getYaw()/getPitch()는 Yarn 1.21.1 기준 float를 반환한다고 알고 있습니다.
// yaw 0은 남쪽(+Z), 90은 서쪽... 등 Minecraft 좌표계 기준 8방위 매핑은 일반적으로 알려진 공식을 사용했습니다
// (실제 게임 내 F3 디버그 화면과 비교 검증은 못 했으니 방위 매핑이 90도 어긋나 보이면 offset 조정 필요).

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class DirectionHudModule extends Module {

    private final PositionSetting position = register(new PositionSetting(
            "position", "위치", "표시 위치입니다.",
            HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 132)));

    private final ColorSetting textColor = register(new ColorSetting(
            "text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));

    private final BooleanSetting showNumeric = register(new BooleanSetting(
            "show_numeric", "각도 숫자", "보는 각도를 숫자로 함께 표시합니다.", true));

    private static final String[] LABELS_KR = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};

    public DirectionHudModule() {
        super("direction_hud", "방향", ModuleCategory.HUD, "바라보는 방위");
        enableHudStyle();
    }

    private static String yawToLabel(float yaw) {
        // Minecraft yaw: 0 = south(+Z), 90 = west(-X), 180 = north(-Z), -90/270 = east(+X)
        float normalized = yaw % 360f;
        if (normalized < 0) normalized += 360f;
        // south(0) 기준을 북(0)-북동-동... 배열 인덱스에 맞추기 위해 180도 회전 후 45도 단위 인덱스 계산
        float fromNorth = (normalized + 180f) % 360f;
        int index = Math.round(fromNorth / 45f) % 8;
        return LABELS_KR[index];
    }

    @Override
    public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        // 49-26차: 월드 밖(미리보기)에서는 예시 각도로
        boolean sample = client.player == null;
        if (sample && !isPreview()) {
            return;
        }
        float yaw = sample ? 174.0f : LunaCompat.getYaw(client.player);
        float pitch = sample ? -3.0f : LunaCompat.getPitch(client.player);
        String directionLabel = yawToLabel(yaw);

        String text;
        if (showNumeric.get()) {
            float normalizedYaw = yaw % 360f;
            if (normalizedYaw < 0) normalizedYaw += 360f;
            text = directionLabel + String.format(" | %.0f° / %.0f°", normalizedYaw, pitch);
        } else {
            text = directionLabel;
        }

        int textWidth = LunaCompat.getTextWidth(client.font, text);
        int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), textWidth);
        int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
        drawHudLine(context, text, x, y, textColor.getArgb());
    }
}
