package kr.lunaslight.mod.module.impl.hud;

// ⚠️ 컴파일 확인 필요: client.player.getPos()가 Vec3d를 반환한다고 가정했습니다(Yarn 1.21.1 기준 맞을 것으로 예상).
// 틱 간 위치 차이를 20배(20 tps) 해서 블록/초로 환산하며, 최근 4틱 평균으로 떨림을 줄였습니다.

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayDeque;
import java.util.Deque;

// 49-202차(너굴마을 운영진이 핵 클라이언트의 "Speed" 기능과 혼동): 이름만 바꿈(옛 SpeedometerHudModule, id speedometer_hud).
// 이동 속도를 숫자로 보여주기만 하고 이동에는 손대지 않는다.
public class MoveRateHudModule extends Module {

    private final PositionSetting position = register(new PositionSetting(
            "position", "위치", "표시 위치입니다.",
            HudPosition.of(HudPosition.Anchor.TOP_RIGHT, 4, 75)));

    private final ColorSetting textColor = register(new ColorSetting(
            "text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));

    private final BooleanSetting horizontalOnly = register(new BooleanSetting(
            "horizontal_only", "수평 한정", "위아래 움직임은 속도에서 뺍니다.", true));

    private static final int SAMPLE_COUNT = 4;

    private Vec3d lastPos = null;
    private final Deque<Double> recentRates = new ArrayDeque<>();

    public MoveRateHudModule() {
        super("move_rate_hud", "속도", ModuleCategory.HUD, "이동 속도");
        enableHudStyle();
    }

    @Override
    protected void onEnable() {
        lastPos = null;
        recentRates.clear();
    }

    @Override
    public void onTick() {
        if (client.player == null) return;
        Vec3d pos = kr.lunaslight.mod.util.LunaCompat.getPos(client.player);
        if (pos == null) return;
        if (lastPos != null) {
            double dx = pos.x - lastPos.x;
            double dz = pos.z - lastPos.z;
            double distance;
            if (horizontalOnly.get()) {
                distance = Math.sqrt(dx * dx + dz * dz);
            } else {
                double dy = pos.y - lastPos.y;
                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            }
            double blocksPerSecond = distance * 20.0;
            recentRates.addLast(blocksPerSecond);
            while (recentRates.size() > SAMPLE_COUNT) {
                recentRates.removeFirst();
            }
        }
        lastPos = pos;
    }

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        double average = 0;
        if (!recentRates.isEmpty()) {
            for (double v : recentRates) average += v;
            average /= recentRates.size();
        }
        if (isPreview() && average <= 0.001) {
            average = 4.32; // 49-26차: 미리보기 예시 값
        }
        String text = String.format("%.2f m/s", average);   // 49-124차(사용자: "속도는 m/s"): 채굴속도(b/s)와 구분
        int textWidth = LunaCompat.getTextWidth(client.textRenderer, text);
        int x = position.get().resolveX(WindowAccess.of(client).getScaledWidth(), textWidth);
        int y = position.get().resolveY(WindowAccess.of(client).getScaledHeight(), client.textRenderer.fontHeight);
        drawHudLine(context, text, x, y, textColor.getArgb());
    }
}
