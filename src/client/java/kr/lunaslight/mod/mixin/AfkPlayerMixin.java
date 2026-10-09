package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.AfkWatch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-312차: AFK 플레이어의 치장과 이름표.
 * <ul>
 *   <li>망토 칸(PlayerModelPart 첫 번째 = CAPE)을 안 보이게 - 바닐라 망토, 노바 망토, 망토 무늬 겉날개 모두. 1.14.4 ~ 1.21.8은
 *       렌더러가 이걸 묻는다(1.21.2+는 렌더 상태 capeVisible도 PlayerStateHook이 끈다).</li>
 *   <li>1.21.1 이하: 이 플레이어를 그리는 동안(AfkPoseLegacyMixin) 표시 이름 뒤에 회색 AFK - 머리 위 이름표에만 붙는다.</li>
 * </ul>
 */
@Mixin(PlayerEntity.class)
public abstract class AfkPlayerMixin {

	@Inject(method = {"isPartVisible", "isSkinOverlayVisible"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$afkCape(@Coerce Object part, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && part instanceof Enum<?> && ((Enum<?>) part).ordinal() == 0 && AfkWatch.afkEntity(this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$afkName(CallbackInfoReturnable<Text> cir) {
		if (AfkWatch.rendering != null && AfkWatch.rendering == (Object) this) {
			cir.setReturnValue(AfkWatch.label(cir.getReturnValue()));
		}
	}
}
