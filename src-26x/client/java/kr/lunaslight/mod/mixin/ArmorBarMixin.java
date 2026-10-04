package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.ArmorBarHook;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-39차: 갑옷 줄을 ArmorBarColorModule이 대신 그린다(입은 조각별 색 + 인챈트 글린트).
 *  · 1.20.5?~1.21.11: 갑옷이 별도 renderArmor(ctx, player, y, lines, rowHeight, x)(정적, 1.21.11 javap·1.21.1 야른 문서)
 *    → HEAD에서 우리 줄을 그리고 취소. y는 안에서 계산하던 식(y − (lines−1)×rowHeight − 10) 그대로.
 *  · 1.20.1: renderStatusBars 안에서 갑옷 아이콘을 drawTexture(Identifier, x, y, u, v, w, h)로 세 번(가득/반/빈)
 *    찍는데 그 호출이 이 메서드의 처음 세 drawTexture(ordinal 0~2, javap 실측) → Redirect로 가로채 칸마다 우리 아이콘.
 *  · 1.20.2~1.20.4: 같은 구조지만 drawGuiTexture(Identifier, x, y, w, h) → 같은 방식(실측 전, require=0).
 * 전부 require = 0 - 시그니처가 다른 버전은 조용히 바닐라.
 */
// 26.2: 인게임 HUD가 Gui에서 Hud 클래스로 분리됨. 26.1.x용 쌍둥이(…Mixin261, Gui 대상)가 따로 있다.
@Mixin(targets = "net.minecraft.client.gui.Hud")
public abstract class ArmorBarMixin {

	@Inject(method = "extractArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIII)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$coloredArmorRow(GuiGraphicsExtractor context, Player player, int y, int lines,
			int rowHeight, int x, CallbackInfo ci) {
		int ay = y - (lines - 1) * rowHeight - 10;
		if (ArmorBarHook.dispatchRow(context, x, ay)) {
			ci.cancel();
		}
	}

	// ---- 1.20.1: 인라인 갑옷 아이콘 3종 ----



	// ---- 1.20.2~1.20.4: 낱장 스프라이트 3종 ----


}
