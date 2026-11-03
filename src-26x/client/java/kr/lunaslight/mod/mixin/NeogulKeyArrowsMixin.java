package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.NeogulKeyArrows;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** 49-503차: 너굴마을 리소스팩의 WASD 키캡 그림을 화살표로 바꿔 넘긴다({@link NeogulKeyArrows}, 켰을 때만). */
@Mixin(MultiPackResourceManager.class)
public abstract class NeogulKeyArrowsMixin {

	@Inject(method = "getResource", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$keyArrows(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
		if (id == null) {
			return;
		}
		int dir = NeogulKeyArrows.dirOf(id.getNamespace(), id.getPath());
		if (dir < 0) {
			return;
		}
		Optional<Resource> r = cir.getReturnValue();
		if (r == null || r.isEmpty()) {
			return;
		}
		Resource orig = r.get();
		String key = id.toString();
		cir.setReturnValue(Optional.of(new Resource(orig.source(), () -> {
			try (java.io.InputStream in = orig.open()) {
				return new java.io.ByteArrayInputStream(NeogulKeyArrows.convert(key, in, dir));
			}
		})));
	}
}
