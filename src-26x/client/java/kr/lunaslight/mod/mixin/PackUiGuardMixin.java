package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.PackUiGuard;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;

/**
 * 49-452차: [내 리소스팩 우선]에서 서버 팩에도 있는 UI 파일(셰이더, textures/gui)은 내 팩(zip/폴더)에서 없는 것처럼({@link PackUiGuard}).
 * 하나씩 읽기(getResource)는 null, 목록 읽기(listResources - GUI 스프라이트 아틀라스)는 그 파일만 빼고 넘긴다.
 */
@Mixin({net.minecraft.server.packs.FilePackResources.class, net.minecraft.server.packs.PathPackResources.class})
public abstract class PackUiGuardMixin {

	@Inject(method = "getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/server/packs/resources/IoSupplier;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void lunaslight$guardGet(PackType type, Identifier id, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
		if (type == PackType.CLIENT_RESOURCES && PackUiGuard.active() && PackUiGuard.hides((PackResources) (Object) this, id)) {
			cir.setReturnValue(null);
		}
	}

	@ModifyVariable(method = "listResources(Lnet/minecraft/server/packs/PackType;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/server/packs/PackResources$ResourceOutput;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private PackResources.ResourceOutput lunaslight$guardList(PackResources.ResourceOutput out) {
		if (out == null || !PackUiGuard.active()) {
			return out;
		}
		PackResources self = (PackResources) (Object) this;
		String pid = self.packId();
		if (pid == null || !pid.startsWith("file/")) {
			return out;
		}
		return (id, supplier) -> {
			if (!PackUiGuard.hides(self, id)) {
				out.accept(id, supplier);
			}
		};
	}
}
