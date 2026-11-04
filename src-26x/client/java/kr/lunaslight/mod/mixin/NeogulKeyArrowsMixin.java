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

	/** 49-533차: 아이템 그림은 아틀라스가 listResources로 모으므로 여기서도 바꾼다(26.1~26.2 Predicate, 26.3 Selector - 이름만으로 맞춤). */
	@Inject(method = "listResources", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$listTextures(CallbackInfoReturnable<java.util.Map<Identifier, Resource>> cir) {
		java.util.Map<Identifier, Resource> m = cir.getReturnValue();
		if (m == null || m.isEmpty()) {
			return;
		}
		java.util.Map<Identifier, Resource> copy = null;
		for (java.util.Map.Entry<Identifier, Resource> e : m.entrySet()) {
			byte[] png = kr.lunaslight.mod.util.NeogulTextures.replacement(e.getKey().getNamespace(), e.getKey().getPath());
			if (png == null) {
				continue;
			}
			if (copy == null) {
				copy = new java.util.HashMap<>(m);
			}
			Resource orig = e.getValue();
			copy.put(e.getKey(), new Resource(orig.source(), () -> new java.io.ByteArrayInputStream(png)));
		}
		if (copy != null) {
			cir.setReturnValue(copy);
		}
	}

	@Inject(method = "getResource", at = @At("RETURN"), cancellable = true, require = 0)
	private void lunaslight$keyArrows(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
		if (id == null) {
			return;
		}
		// 49-533차: 너굴 아이템 그림 바꾸기(요리용 석탄)
		byte[] png = kr.lunaslight.mod.util.NeogulTextures.replacement(id.getNamespace(), id.getPath());
		if (png != null) {
			Optional<Resource> r0 = cir.getReturnValue();
			if (r0 != null && r0.isPresent()) {
				cir.setReturnValue(Optional.of(new Resource(r0.get().source(), () -> new java.io.ByteArrayInputStream(png))));
			}
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
