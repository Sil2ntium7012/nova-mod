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
			Resource sub = lunaslight$substitute(e.getKey(), e.getValue());
			if (sub == null) {
				continue;
			}
			if (copy == null) {
				copy = new java.util.HashMap<>(m);
			}
			copy.put(e.getKey(), sub);
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
		if (kr.lunaslight.mod.util.NeogulTextures.replacement(id.getNamespace(), id.getPath()) != null) {
			Optional<Resource> r0 = cir.getReturnValue();
			if (r0 != null && r0.isPresent()) {
				Resource sub = lunaslight$substitute(id, r0.get());
				if (sub != null) {
					cir.setReturnValue(Optional.of(sub));
				}
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

	/** 49-700차: 바꿀 그림이면 지금 리소스팩의 바닐라 그림(요리용 석탄 → 숯)을 여는 Resource. 팩 출처는 원래 것 그대로. */
	@org.spongepowered.asm.mixin.Unique
	private Resource lunaslight$substitute(Identifier id, Resource orig) {
		String path = kr.lunaslight.mod.util.NeogulTextures.replacement(id.getNamespace(), id.getPath());
		if (path == null || orig == null) {
			return null;
		}
		try {
			Optional<Resource> v = ((MultiPackResourceManager) (Object) this).getResource(Identifier.withDefaultNamespace(path));
			if (v == null || v.isEmpty()) {
				return null;
			}
			Resource van = v.get();
			return new Resource(orig.source(), van::open);
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("neogulTextures", t);
			return null;
		}
	}
}
