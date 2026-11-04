package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.NeogulDungeonHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * 49-538차(사용자: 던전 UI - "왜 안바뀌는데"): <b>너굴 던전 UI</b>. 그리기와 서버 HUD 읽기는 {@link NeogulDungeonHud}.
 *
 * <p>사용자 요청대로: 던전에서 인벤토리를 열면 오른쪽 아래에 켜기/끄기, 처음이면 미리보기 그림과 함께 켤지 묻는다. 켜도 지금 던전에서는
 * 안 바뀌고 다음에 던전에 들어갈 때부터(싸우다 화면이 바뀌면 위험). 끄면 바로 원래 서버 화면.
 */
public class NeogulDungeonUiModule extends Module {

	private final BooleanSetting asked = register(new BooleanSetting("asked", "물어봄", "처음 묻는 창을 이미 봤는지", false));

	private int[] btn;          // 오른쪽 아래 켜기/끄기 [x, y, w, h]
	private int[] yesBtn, noBtn; // 처음 묻는 창 버튼
	private boolean asking;
	private boolean migrated;

	public NeogulDungeonUiModule() {
		super("neogul_dungeon_ui", "너굴 던전 UI", ModuleCategory.SERVER, "던전 핫바 | 하트 | 스킬 카드 | 경험치 바");
		serverGroup("너굴마을");
		// 49-550차(사용자: "왜 아직도 이러냐고" - 기본 꺼짐 + 던전에서 인벤토리를 열어야만 묻는 창이 떠서 한 번도 안 켜졌다):
		// 기본 켬. 아직 안 물어본 사람은 켜 두고, 너굴마을에서 인벤토리를 처음 열 때 "이대로 / 원래대로"를 묻는다.
		// 켜지는 건 여전히 던전에 들어가는 순간에만(싸우는 중에 안 바뀜).
		defaultEnabled(true);
		newFeature("49-552");   // 49-552차: 묻는 건 들어갈 때 뜨는 새 기능 창(NewFeatures)이 한다
		asked.hidden();
		NeogulDungeonHud.enabled = this::isEnabled;
		NeogulDungeonHud.init();
		try {
			net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
				if (!migrated && mc != null && mc.player != null) {
					migrated = true;
					if (!asked.get() && !isEnabledStored()) {
						setEnabled(true);   // 예전 판(기본 끔)에서 묻는 창을 못 본 사람
						kr.lunaslight.mod.config.LunaClientConfig.save();
					}
				}
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonUi:tick", t);
		}
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
		try {
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
				if (screen instanceof InventoryScreen) {
					net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen)
						.register((s, e) -> !onClick(s, e.x(), e.y(), e.button()));
				}
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonUi:init", t);
		}
	}

	@Override
	public boolean hasPreview() {
		return false;
	}

	@Override
	public boolean newFeatureAsked() {
		return asked.get();
	}

	@Override
	public void onNewFeatureAnswered(boolean on) {
		asked.setValue(true);
	}

	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int px, int py, int pw, int ph) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		float s = Math.min(0.8f, pw / 340f);
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, px, py);
			LunaCompat.guiScale(ctx, s, s);
			int vw = Math.round(pw / s), vh = Math.round(ph / s);
			NeogulDungeonHud.drawAt(ctx, mc, vw / 2, vh, mc.player, NeogulDungeonHud.sample(),
					NeogulDungeonHud.hp >= 0 ? NeogulDungeonHud.hp : 39, NeogulDungeonHud.hpMax > 0 ? NeogulDungeonHud.hpMax : 51,
					-1, NeogulDungeonHud.hunger >= 0 ? NeogulDungeonHud.hunger : 20,
					NeogulDungeonHud.level >= 0 ? NeogulDungeonHud.level : 21, NeogulDungeonHud.pct >= 0 ? NeogulDungeonHud.pct : 45, true);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
		if (client == null || isPreview() || !NeogulDungeonHud.active) {
			return;
		}
		NeogulDungeonHud.draw(ctx, client);
	}

	private boolean showHere(Object screen) {
		Minecraft mc = Minecraft.getInstance();
		if (!(screen instanceof InventoryScreen) || mc == null || mc.player == null || !NeogulData.onNeogul(mc)) {
			return false;
		}
		return asked.get() && NeogulDungeonHud.inDungeon();   // 49-552차: 처음 묻기는 새 기능 창이 함 - 인벤토리엔 던전에서 켜기/끄기 버튼만
	}

	private void onScreenFrame(Object screen, GuiGraphicsExtractor ctx, int mx, int my) {
		btn = null;
		yesBtn = null;
		noBtn = null;
		asking = false;
		if (!showHere(screen)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		try {
			ctx.nextStratum();
		} catch (Throwable ignored) {
		}
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		if (!asked.get()) {
			drawAsk(ctx, mc, sw, sh, mx, my);
			return;
		}
		boolean on = isEnabled();
		String label = on ? (NeogulDungeonHud.active ? "던전 UI 켜짐" : "던전 UI 켜짐 (다음 던전부터)") : "던전 UI 꺼짐";
		int w = LunaDraw.width(mc.font, label) + 22;
		int h = 16;
		int x = sw - w - 6, y = sh - h - 6;
		btn = new int[]{x, y, w, h};
		boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
		LunaDraw.roundRect(ctx, x, y, w, h, 4, hov ? 0xF0262B36 : 0xE01A1E26);
		LunaDraw.roundRectOutline(ctx, x, y, w, h, 4, on ? 0xFFE8B84A : 0xFF4A505C);
		LunaDraw.roundRect(ctx, x + 6, y + 6, 4, 4, 2, on ? 0xFFFFD36A : 0xFF6A707C);
		LunaDraw.text(ctx, mc.font, label, x + 15, y + 4, on ? 0xFFFFF0C8 : 0xFFB8BEC8);
	}

	private void drawAsk(GuiGraphicsExtractor ctx, Minecraft mc, int sw, int sh, int mx, int my) {
		asking = true;
		ctx.fill(0, 0, sw, sh, 0xA0000000);
		int w = Math.min(sw - 20, 300), h = 180;
		int x = (sw - w) / 2, y = Math.max(6, (sh - h) / 2);
		LunaDraw.roundRect(ctx, x, y, w, h, 6, 0xF2141821);
		LunaDraw.roundRectOutline(ctx, x, y, w, h, 6, 0xFFE8B84A);
		LunaDraw.textCentered(ctx, mc.font, "던전 UI가 새 모양으로 바뀌었어요", x + w / 2, y + 8, 0xFFFFE6A8);
		LunaDraw.textCentered(ctx, mc.font, "던전에서 핫바, 하트, 스킬 카드, 경험치 바가 이렇게 나와요", x + w / 2, y + 20, 0xFFB8BEC8);
		// 미리보기: 실제 그리기를 작게
		int px = x + 8, py = y + 34, pw = w - 16, ph = 92;
		ctx.fillGradient(px, py, px + pw, py + ph, 0xFF3A4A5E, 0xFF1C2430);
		ctx.enableScissor(px, py, px + pw, py + ph);
		float s = Math.min(0.8f, pw / 340f);
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, px, py);
			LunaCompat.guiScale(ctx, s, s);
			int vw = Math.round(pw / s), vh = Math.round(ph / s);
			NeogulDungeonHud.drawAt(ctx, mc, vw / 2, vh, mc.player, NeogulDungeonHud.sample(),
					NeogulDungeonHud.hp >= 0 ? NeogulDungeonHud.hp : 39, NeogulDungeonHud.hpMax > 0 ? NeogulDungeonHud.hpMax : 51,
					NeogulDungeonHud.armor >= 0 ? NeogulDungeonHud.armor : 12, NeogulDungeonHud.hunger >= 0 ? NeogulDungeonHud.hunger : 20,
					NeogulDungeonHud.level >= 0 ? NeogulDungeonHud.level : 21, NeogulDungeonHud.pct >= 0 ? NeogulDungeonHud.pct : 45, true);
		} catch (Throwable t) {
			LunaCompat.warnOnce("dungeonUi:preview", t);
		} finally {
			LunaCompat.guiPop(ctx);
			ctx.disableScissor();
		}
		LunaDraw.textCentered(ctx, mc.font, "던전에 들어가는 순간 바뀌어요 (싸우는 중에는 안 바뀜)", x + w / 2, py + ph + 4, 0xFF8A90A0);
		LunaDraw.textCentered(ctx, mc.font, "던전에서 인벤토리 오른쪽 아래 버튼으로 끄고 켜요", x + w / 2, py + ph + 15, 0xFF8A90A0);
		int bw = 80, bh = 16, by = y + h - bh - 6;
		yesBtn = new int[]{x + w / 2 - bw - 4, by, bw, bh};
		noBtn = new int[]{x + w / 2 + 4, by, bw, bh};
		button(ctx, mc, yesBtn, "이대로 쓰기", true, mx, my);
		button(ctx, mc, noBtn, "원래대로", false, mx, my);
	}

	private static void button(GuiGraphicsExtractor ctx, Minecraft mc, int[] b, String label, boolean primary, int mx, int my) {
		boolean hov = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
		int fill = primary ? (hov ? 0xFFF0C25A : 0xFFE0AE44) : (hov ? 0xFF323844 : 0xFF262B34);
		LunaDraw.roundRect(ctx, b[0], b[1], b[2], b[3], 4, fill);
		LunaDraw.textCentered(ctx, mc.font, label, b[0] + b[2] / 2, b[1] + 4, primary ? 0xFF2A1E08 : 0xFFE0E4EC);
	}

	private static boolean in(int[] b, double mx, double my) {
		return b != null && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
	}

	/** 처리했으면 true(인벤토리로 안 넘김). */
	private boolean onClick(Screen s, double mx, double my, int button) {
		if (!showHere(s)) {
			return false;
		}
		if (asking) {
			if (button == 0 && (in(yesBtn, mx, my) || in(noBtn, mx, my))) {
				boolean yes = in(yesBtn, mx, my);
				asked.setValue(true);
				setEnabled(yes);
				kr.lunaslight.mod.config.LunaClientConfig.save();
				LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
			}
			return true;   // 묻는 동안은 인벤토리를 못 누른다
		}
		if (button == 0 && in(btn, mx, my)) {
			setEnabled(!isEnabled());
			kr.lunaslight.mod.config.LunaClientConfig.save();
			LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
			return true;
		}
		return false;
	}
}
