package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.GlintHook;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 49-42차: 인챈트/포션 반짝임 숨김 - 아이템·손·갑옷 렌더러가 전부 ItemStack#hasGlint()로 판단하므로 여기서 false.
 * 1.15.2는 이름이 hasEnchantmentGlint (javap 실측), 1.16~1.21.11 hasGlint (tiny 실측). require=0.
 */
@Mixin(ItemStack.class)
public abstract class GlintMixin {

	@Inject(method = "hasGlint()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideGlint(CallbackInfoReturnable<Boolean> cir) {
		if (GlintHook.hide) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "hasEnchantmentGlint()Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$hideGlintLegacy(CallbackInfoReturnable<Boolean> cir) {
		if (GlintHook.hide) {
			cir.setReturnValue(false);
		}
	}
}
