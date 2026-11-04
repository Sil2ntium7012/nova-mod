package kr.lunaslight.mod.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

/**
 * 49-538차: 너굴 던전 UI({@link kr.lunaslight.mod.util.NeogulDungeonHud}) - 보스바가 바뀔 때마다 너굴 HUD 줄을 읽고,
 * 던전 UI가 켜져 있으면 이름을 왼쪽 위 상자까지만 남긴다.
 */
@Mixin(BossHealthOverlay.class)
public abstract class NeogulBossHudMixin {

	@Shadow
	@Final
	private Map<UUID, LerpingBossEvent> events;

	@Inject(method = "update", at = @At("RETURN"), require = 0)
	private void lunaslight$dungeonHud(CallbackInfo ci) {
		kr.lunaslight.mod.util.NeogulDungeonHud.onBossUpdate(events);
	}
}
