package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ScreenAnimHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-42차: 인벤토리/상자 화면 등장 애니메이션 - 화면 그리기 전체를 행렬 하나로 감싼다.
 *  · 1.20.1~1.21.10: HandledScreen#render(DrawContext, int, int, float) (javap 실측)
 *  · 1.21.11: render는 Screen 것을 그대로 쓰고 HandledScreen엔 renderMain(DrawContext, int, int, float)만 있음(tiny 실측)
 *  · 1.19 이하(MatrixStack): 대상 없음 → require=0
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenAnimMixin {

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), require = 0)
	private void lunaslight$animBegin(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ScreenAnimHook.begin(this, ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"), require = 0)
	private void lunaslight$animEnd(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ScreenAnimHook.end(this, ctx);
	}

	@Inject(method = "renderMain(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), require = 0)
	private void lunaslight$animBeginMain(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ScreenAnimHook.begin(this, ctx);
	}

	@Inject(method = "renderMain(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"), require = 0)
	private void lunaslight$animEndMain(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		ScreenAnimHook.end(this, ctx);
	}
}
