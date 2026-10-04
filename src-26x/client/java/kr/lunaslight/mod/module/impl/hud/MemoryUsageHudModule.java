package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MemoryUsageHudModule extends Module {

    private final PositionSetting position = register(new PositionSetting(
            "position", "위치", "표시 위치입니다.",
            HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 55)));

    private final ColorSetting textColor = register(new ColorSetting(
            "text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));

    private final IntSetting updateIntervalTicks = register(new IntSetting(
            "update_interval_ticks", "갱신 주기", "메모리 사용량을 다시 재는 주기(틱)입니다.", 20, 5, 100, 1).unit("틱"));

    private int ticksSinceUpdate = 0;
    private long usedMb = 0;
    private long maxMb = 0;

    public MemoryUsageHudModule() {
        super("memory_usage_hud", "메모리", ModuleCategory.HUD, "메모리 사용량");
        enableHudStyle();
    }

    @Override
    protected void onEnable() {
        ticksSinceUpdate = 0;
        recalculate();
    }

    private void recalculate() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        usedMb = used / (1024 * 1024);
        maxMb = max / (1024 * 1024);
    }

    @Override
    public void onTick() {
        ticksSinceUpdate++;
        if (ticksSinceUpdate >= updateIntervalTicks.get()) {
            ticksSinceUpdate = 0;
            recalculate();
        }
    }

    @Override
    public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        String text = usedMb + " / " + maxMb + " MB";
        int textWidth = LunaCompat.getTextWidth(client.font, text);
        int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), textWidth);
        int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
        drawHudLine(context, text, x, y, textColor.getArgb());
    }
}
