package kr.lunaslight.mod.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 49-391차: 너굴 아이템 목록(NeogulJei)이 인벤토리 창 자리와 마우스 아래 칸을 읽는 길(protected 필드). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessorMixin {
	@Accessor("leftPos")
	int lunaslight$leftPos();

	@Accessor("topPos")
	int lunaslight$topPos();

	@Accessor("imageWidth")
	int lunaslight$imageWidth();

	@Accessor("imageHeight")
	int lunaslight$imageHeight();

	@Accessor("hoveredSlot")
	Slot lunaslight$hoveredSlot();
}
