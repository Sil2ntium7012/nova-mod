package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.module.impl.inventory.ItemTooltipInfoModule;
import kr.lunaslight.mod.module.impl.inventory.ShulkerPeekModule;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * 49-23차: 셜커 상자 툴팁을 인벤토리 격자로. ItemStack#getTooltipData()(1.17+, Optional<TooltipData>)가
 * 비어 있고 셜커 상자면 ShulkerPeekModule이 만든 TooltipData(Proxy)를 돌려줘서, Fabric
 * TooltipComponentCallback → ShulkerTooltipComponent가 이름 줄 바로 아래에 9×3 격자를 그리게 한다.
 * 49-25차: 음식이면 ItemTooltipInfoModule의 FoodPayload(고기 아이콘 두 줄, gui.FoodTooltipComponent).
 */
@Mixin(ItemStack.class)
public abstract class ItemStackTooltipDataMixin {

	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$shulkerGrid(CallbackInfoReturnable<Optional<?>> cir) {
		Object current = cir.getReturnValue();
		if (current instanceof Optional<?> o && o.isPresent()) {
			return;
		}
		ItemStack self = (ItemStack) (Object) this;
		Object data = ShulkerPeekModule.tooltipDataFor(self);
		if (data == null) {
			data = ItemTooltipInfoModule.tooltipDataFor(self);
		}
		if (data != null) {
			cir.setReturnValue(Optional.of(data));
		}
	}
}
