package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ArmorBarHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-39차: 갑옷 줄을 ArmorBarColorModule이 대신 그린다(입은 조각별 색 + 인챈트 글린트).
 *  · 1.20.5?~1.21.11: 갑옷이 별도 renderArmor(ctx, player, y, lines, rowHeight, x)(정적, 1.21.11 javap·1.21.1 야른 문서)
 *    → HEAD에서 우리 줄을 그리고 취소. y는 안에서 계산하던 식(y − (lines−1)×rowHeight − 10) 그대로.
 *  · 1.20.1: renderStatusBars 안에서 갑옷 아이콘을 drawTexture(Identifier, x, y, u, v, w, h)로 세 번(가득/반/빈)
 *    찍는데 그 호출이 이 메서드의 처음 세 drawTexture(ordinal 0~2, javap 실측) → Redirect로 가로채 칸마다 우리 아이콘.
 *  · 1.20.2~1.20.4: 같은 구조지만 drawGuiTexture(Identifier, x, y, w, h) → 같은 방식(실측 전, require=0).
 * 전부 require = 0 - 시그니처가 다른 버전은 조용히 바닐라.
 */
@Mixin(InGameHud.class)
public abstract class ArmorBarMixin {

	// 49-153차: HealthBarMixin과 같은 이유로 시그니처를 못 박는다(이름만 맞고 인자가 다른 버전에서 게임이 깨지지 않게).
	@Inject(method = "renderArmor(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIII)V",
			at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$coloredArmorRow(DrawContext context, PlayerEntity player, int y, int lines,
			int rowHeight, int x, CallbackInfo ci) {
		int ay = y - (lines - 1) * rowHeight - 10;
		if (ArmorBarHook.dispatchRow(context, x, ay)) {
			ci.cancel();
		}
	}

	// ---- 1.20.1: 인라인 갑옷 아이콘 3종 ----
	@Redirect(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", ordinal = 0),
			require = 0)
	private void lunaslight$armorTex0(DrawContext ctx, Identifier tex, int x, int y, int u, int v, int w, int h) {
		if (!ArmorBarHook.dispatchSlot(ctx, x, y)) {
			kr.lunaslight.mod.util.LunaCompat.invokeNamed(ctx, "drawTexture", tex, x, y, u, v, w, h);
		}
	}

	@Redirect(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", ordinal = 1),
			require = 0)
	private void lunaslight$armorTex1(DrawContext ctx, Identifier tex, int x, int y, int u, int v, int w, int h) {
		if (!ArmorBarHook.dispatchSlot(ctx, x, y)) {
			kr.lunaslight.mod.util.LunaCompat.invokeNamed(ctx, "drawTexture", tex, x, y, u, v, w, h);
		}
	}

	@Redirect(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", ordinal = 2),
			require = 0)
	private void lunaslight$armorTex2(DrawContext ctx, Identifier tex, int x, int y, int u, int v, int w, int h) {
		if (!ArmorBarHook.dispatchSlot(ctx, x, y)) {
			kr.lunaslight.mod.util.LunaCompat.invokeNamed(ctx, "drawTexture", tex, x, y, u, v, w, h);
		}
	}

	// ---- 1.20.2~1.20.4: 낱장 스프라이트 3종 ----
	@Redirect(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 0),
			require = 0)
	private void lunaslight$armorSprite0(DrawContext ctx, Identifier tex, int x, int y, int w, int h) {
		if (!ArmorBarHook.dispatchSlot(ctx, x, y)) {
			kr.lunaslight.mod.util.LunaCompat.invokeNamed(ctx, "drawGuiTexture", tex, x, y, w, h);
		}
	}

	@Redirect(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 1),
			require = 0)
	private void lunaslight$armorSprite1(DrawContext ctx, Identifier tex, int x, int y, int w, int h) {
		if (!ArmorBarHook.dispatchSlot(ctx, x, y)) {
			kr.lunaslight.mod.util.LunaCompat.invokeNamed(ctx, "drawGuiTexture", tex, x, y, w, h);
		}
	}

	@Redirect(method = "renderStatusBars(Lnet/minecraft/client/gui/DrawContext;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 2),
			require = 0)
	private void lunaslight$armorSprite2(DrawContext ctx, Identifier tex, int x, int y, int w, int h) {
		if (!ArmorBarHook.dispatchSlot(ctx, x, y)) {
			kr.lunaslight.mod.util.LunaCompat.invokeNamed(ctx, "drawGuiTexture", tex, x, y, w, h);
		}
	}
}
