package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaFest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 49-374차: LUNA FESTIVAL - 숫자 키 7·8·9로 핫바 Q·F·C 칸을 고르지 않는다(LunaFest.keySlot). */
@Mixin(Minecraft.class)
public abstract class HotbarSkipKeyMixin {
	@ModifyArg(method = "handleKeybinds", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"), index = 0, require = 0)
	private int lunaslight$skipQfc(int slot) {
		return LunaFest.keySlot(slot);
	}
}
