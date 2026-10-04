package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.SplashHook;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-67차(5-7): <b>1.15.2·1.16.5의 로딩창</b>.
 *
 * <p>49-47차에 만든 {@link SplashOverlayMixin}은 클래스 이름 {@code SplashOverlay}만 잡는다. 그런데
 * 그 이름은 1.17에 생긴 것이고, <b>1.15.2·1.16.5에서는 {@code SplashScreen}</b>이다(실측). 그래서 그
 * 두 버전에서만 시작 로딩창도, 리소스팩 적용 로딩창도 <b>모장 것 그대로</b> 나왔다 - 5-7을 손대면서
 * 발견한 진짜 구멍이다.
 *
 * <p>같은 클래스에 두 이름을 함께 적지 않고 <b>파일을 따로</b> 둔 이유: {@code targets}에 이 버전에
 * 없는 이름이 섞이면 믹스인 하나가 통째로 꺼져서, 1.17+에서 멀쩡하던 로딩창까지 같이 날아간다.
 * 파일을 나누면 각자 자기 버전에서만 붙고 나머지에서는 조용히 빠진다(mixins.json은 required=false).
 *
 * <p>render 시그니처도 두 버전이 다르다 - 1.15.2는 매트릭스 인자가 아예 없고(render(int,int,float)),
 * 1.16.5부터 MatrixStack을 받는다. 둘 다 {@code require = 0}이라 없는 쪽은 그냥 안 붙는다.
 */
@Mixin(targets = "net.minecraft.client.gui.screen.SplashScreen")
public abstract class SplashScreenLegacyMixin {

	@Inject(method = "render(IIF)V", at = @At("RETURN"), require = 0)
	private void lunaslight$splashEra0(int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SplashHook.renderNoMatrices(this);
	}

	@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;IIF)V", at = @At("RETURN"), require = 0)
	private void lunaslight$splashEra1(PoseStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SplashHook.render(this, LunaCompat.toDrawContext(matrices));
	}
}
