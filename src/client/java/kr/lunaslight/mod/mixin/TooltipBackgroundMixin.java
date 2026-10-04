package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.gui.LunaTooltipFrame;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-23차: 툴팁 꾸미기. 바닐라 툴팁 배경(TooltipBackgroundRenderer.render)을 취소하고 LunaTooltipFrame이
 * 등급 색 테두리 상자를 대신 그린다. render의 시그니처가 세 시대로 갈려서(1.20.1~1.21.1 z 포함 /
 * 1.21.2~1.21.5 z + 텍스처 / 1.21.6+ 텍스처만) 디스크립터별 핸들러 셋을 두고 require=0으로 맞는 것만 적용.
 * 아이템/리소스팩이 자체 툴팁 텍스처를 지정한 경우(texture != null)는 존중해서 손대지 않는다.
 * 49-36차: 클래스 리터럴 대신 이름 문자열(targets) - TooltipBackgroundRenderer가 없는 1.19.2 이하에서도 컴파일되게.
 */
@Mixin(targets = "net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer")
public abstract class TooltipBackgroundMixin {

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$frameZ(DrawContext ctx, int x, int y, int w, int h, int z, CallbackInfo ci) {
		if (!LunaTooltipFrame.enabled) {
			return;
		}
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslateZ(ctx, z);
		boolean drawn;
		try {
			drawn = LunaTooltipFrame.render(ctx, x, y, w, h);
		} finally {
			LunaCompat.guiPop(ctx);
		}
		if (drawn) {
			ci.cancel();
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIIILnet/minecraft/util/Identifier;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$frameZTex(DrawContext ctx, int x, int y, int w, int h, int z, Identifier texture, CallbackInfo ci) {
		if (!LunaTooltipFrame.enabled || texture != null) {
			return;
		}
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslateZ(ctx, z);
		boolean drawn;
		try {
			drawn = LunaTooltipFrame.render(ctx, x, y, w, h);
		} finally {
			LunaCompat.guiPop(ctx);
		}
		if (drawn) {
			ci.cancel();
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIILnet/minecraft/util/Identifier;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$frameTex(DrawContext ctx, int x, int y, int w, int h, Identifier texture, CallbackInfo ci) {
		if (!LunaTooltipFrame.enabled || texture != null) {
			return;
		}
		if (LunaTooltipFrame.render(ctx, x, y, w, h)) {
			ci.cancel();
		}
	}
}
