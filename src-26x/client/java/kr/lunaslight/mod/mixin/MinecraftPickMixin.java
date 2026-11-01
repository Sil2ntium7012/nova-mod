package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.AnimalPass;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 49-456차: 조준(Minecraft.pick) 끝에서 동물 통과({@link AnimalPass}). */
@Mixin(Minecraft.class)
public abstract class MinecraftPickMixin {
	@Inject(method = "pick(F)V", at = @At("TAIL"), require = 0)
	private void lunaslight$animalPass(float partial, CallbackInfo ci) {
		AnimalPass.apply((Minecraft) (Object) this, partial);
	}
}
