package kr.lunaslight.mod.mixin;

// ⚠️ 컴파일 확인 필요: net.minecraft.entity.TntEntity의 남은 도화선(fuse) 필드가
// Yarn 1.21.1+build.3 매핑에서 실제로 "fuse"라는 이름인지 100% 확신할 수 없습니다.
// 필드명이 다르면 fabric-loom genSources로 실제 필드명을 확인 후 아래 @Accessor 값을 수정해야 합니다.

import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TntEntity.class)
public interface TntEntityAccessor {
	@Accessor("fuse")
	int getFuse();
}
