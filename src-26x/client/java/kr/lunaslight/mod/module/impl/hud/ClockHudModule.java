package kr.lunaslight.mod.module.impl.hud;

// ⚠️ 컴파일 확인 필요: KeybindSetting은 KeyBinding으로 등록된 게 아니라 raw GLFW 키코드(getKeyCode())만 주므로,
// 여기서는 org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode)로 직접 눌림 여부를 폴링하여
// edge-detect 했습니다. GLFW 클래스/메서드 경로 및 client.getWindow().getHandle()의 정확한 존재 여부는
// Yarn 1.21.1 기준으로는 맞다고 알고 있으나 확실치 않아 표시합니다. keyCode가 -1(미설정)이면 아무 것도 하지 않습니다.

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.KeybindSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ClockHudModule extends Module {

    /** 49-47차(사용자: "시계에 영어로 돼있잖아"): 설정 화면에 그대로 보이는 이름이라 한글로. */
    public enum ClockMode {
        REAL_TIME("현재 시각"),
        STOPWATCH("스톱워치"),
        TIMER("타이머");

        private final String label;

        ClockMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final PositionSetting position = register(new PositionSetting(
            "position", "위치", "표시 위치입니다.",
            HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 89)));

    private final ColorSetting textColor = register(new ColorSetting(
            "text_color", "글자 색", "글자의 색입니다.", 0xFFFFFFFF));

    private final EnumSetting<ClockMode> mode = register(new EnumSetting<>(
            "mode", "모드", "실제 시간, 스톱워치, 타이머 중 하나를 표시합니다.",
            ClockMode.REAL_TIME, ClockMode.class));

    // 49-78차(사용자: "타이머 시간 설정하는 게 없어"): 타이머는 여기서 정한 시간부터 거꾸로 센다.
    // 모드가 [타이머]일 때만 밝고, 아닐 땐 어둡게 잠긴다(disabledWhen). 키도 [현재 시각]에선 쓸 데가 없어 잠근다.
    private final IntSetting timerMinutes = register(new IntSetting(
            "timer_minutes", "타이머 시간", "타이머가 거꾸로 세기 시작하는 시간입니다.", 5, 1, 180, 1).unit("분"));
    private final KeybindSetting stopwatchToggle = register(new KeybindSetting(
            "stopwatch_toggle", "시작/정지 키", "스톱워치/타이머를 시작하거나 멈추는 키입니다. 끝난 타이머는 이 키로 다시 시작합니다.", -1));

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("a hh:mm:ss", java.util.Locale.KOREAN);

    private boolean stopwatchRunning = false;
    private long stopwatchTicks = 0;

    private boolean timerRunning = false;
    private long timerTicks = 0;        // 49-78차: 남은 틱(예전엔 올라가기만 하는 가짜 타이머였다)
    private long timerDoneAtMs = 0;     // 0이 된 시각 - 몇 초간 깜빡이고 소리 한 번

    private boolean lastKeyPressed = false;

    public ClockHudModule() {
        super("clock_hud", "시계", ModuleCategory.HUD, "현재 시각 | 스톱워치 | 타이머");
        enableHudStyle();
        timerMinutes.disabledWhen(() -> mode.get() != ClockMode.TIMER);
        stopwatchToggle.disabledWhen(() -> mode.get() == ClockMode.REAL_TIME);
        timerMinutes.onChange(this::resetTimer);
        mode.onChange(() -> {
            resetStopwatch();
            resetTimer();
        });
    }

    @Override
    public void onTick() {
        int keyCode = stopwatchToggle.getKeyCode();
        if (keyCode != -1) {
            boolean pressedNow;
            try {
                pressedNow = stopwatchToggle.isDown(client); // 49-24차: 조합키/마우스 지원
            } catch (Exception e) {
                pressedNow = false;
            }
            if (pressedNow && !lastKeyPressed) {
                // edge: 방금 눌림
                if (mode.get() == ClockMode.STOPWATCH) {
                    stopwatchRunning = !stopwatchRunning;
                } else if (mode.get() == ClockMode.TIMER) {
                    if (timerTicks <= 0) {
                        timerTicks = timerMinutes.get() * 60L * 20L;   // 끝났거나 아직 안 돌린 상태 → 처음부터
                        timerDoneAtMs = 0;
                        timerRunning = true;
                    } else {
                        timerRunning = !timerRunning;
                    }
                }
            }
            lastKeyPressed = pressedNow;
        }

        if (mode.get() == ClockMode.STOPWATCH && stopwatchRunning) {
            stopwatchTicks++;
        }
        if (mode.get() == ClockMode.TIMER && timerRunning) {
            timerTicks--;
            if (timerTicks <= 0) {
                timerTicks = 0;
                timerRunning = false;
                timerDoneAtMs = System.currentTimeMillis();
                LunaCompat.playUiSound(client, "BLOCK_NOTE_BLOCK_BELL", 1.0f);
            }
        }
    }

    public void resetStopwatch() {
        stopwatchTicks = 0;
        stopwatchRunning = false;
    }

    public void resetTimer() {
        timerTicks = 0;
        timerRunning = false;
        timerDoneAtMs = 0;
    }

    private static String formatTicksAsClock(long ticks) {
        long totalSeconds = ticks / 20;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        String text;
        switch (mode.get()) {
            case STOPWATCH -> text = "스톱워치 " + formatTicksAsClock(stopwatchTicks);
            case TIMER -> {
                // 안 돌리고 있으면 설정한 시간을 그대로 보여 준다(무엇부터 셀지 보이게). 끝나면 5초간 00:00을 깜빡인다.
                long shown = timerTicks <= 0 && timerDoneAtMs == 0 ? timerMinutes.get() * 60L * 20L : timerTicks;
                text = "타이머 " + formatTicksAsClock(shown);
                if (timerDoneAtMs != 0 && System.currentTimeMillis() - timerDoneAtMs < 5000
                        && (System.currentTimeMillis() / 400) % 2 == 0) {
                    text = "타이머 §c" + formatTicksAsClock(0);
                }
            }
            default -> text = LocalTime.now().format(TIME_FORMAT);
        }

        int textWidth = LunaCompat.getTextWidth(client.font, text);
        int x = position.get().resolveX(client.getWindow().getGuiScaledWidth(), textWidth);
        int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), client.font.lineHeight);
        drawHudLine(context, text, x, y, textColor.getArgb());
    }
}
