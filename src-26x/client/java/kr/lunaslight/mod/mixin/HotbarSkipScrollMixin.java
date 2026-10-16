package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaFest;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 49-374차: LUNA FESTIVAL - 휠로 핫바 Q·F·C 칸(7·8·9번)을 건너뛴다(LunaFest.scrollSlot). */
@Mixin(MouseHandler.class)
public abstract class HotbarSkipScrollMixin {
	@ModifyArg(method = "onScroll", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"), index = 0, require = 0)
	private int lunaslight$skipQfc(int slot) {
		return LunaFest.scrollSlot(slot);
	}
}
