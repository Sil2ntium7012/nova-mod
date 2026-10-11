package kr.lunaslight.mod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 49-346차(사용자: "아이템 찾기 상자 홀로그램이 내 화면을 따라다녀서 이상하게 보여"): 26.x는 HUD(우리 투영)가 월드보다 <b>먼저</b>
 * 뽑힌다(extract 단계) - 걷기 흔들림(bobView)/맞은 기울기(bobHurt) 행렬은 그 뒤 renderLevel에서 만들어져, 우리는 늘 한 프레임
 * 전 행렬을 쓰고 있었다. 걷거나 돌 때 상자가 월드와 한 박자 어긋나 화면에 끌려다니는 것처럼 보였다. 이 인보커로 지금 프레임의
 * 카메라 상태에서 같은 행렬을 직접 만들어 쓴다(LunaProjection.freshBob).
 */
@Mixin(GameRenderer.class)
public interface GameRendererBobMixin {

	@Invoker("bobHurt")
	void lunaslight$bobHurt(CameraRenderState state, PoseStack poseStack);

	@Invoker("bobView")
	void lunaslight$bobView(CameraRenderState state, PoseStack poseStack);
}
