package kr.lunaslight.mod.module.impl.render;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.EntityHideHook;
import net.minecraft.world.entity.Entity;

/**
 * 49-60차(3-11 · 3-6): 엔티티 줄이기 - 많아서 느려지는 두 가지를 그리기 전에 걸러낸다.
 *
 * <ul>
 *   <li><b>겹친 것 줄이기</b>(3-11) - 같은 자리에 같은 종류가 잔뜩 쌓였을 때(드랍템 30세트, 몹 농장)
 *       앞의 몇 개만 그린다. 30개를 그리나 4개를 그리나 보이는 그림은 거의 같은데 비용은 8배 차이다.</li>
 *   <li><b>멀리 있는 것 숨기기</b>(3-6) - 정한 거리보다 먼 엔티티를 안 그린다.</li>
 * </ul>
 *
 * <p><b>플레이어는 절대 대상이 아니다.</b> 남이 안 보이는 건 성능 개선이 아니라 게임이 달라지는 것이다
 * (PVP에서 상대가 사라지면 그건 고장이거나 치트다). 그래서 설정으로도 못 켠다.
 *
 * <p><b>그리기만 막는다</b> - 아이템은 그대로 주울 수 있고 몹도 그대로 때릴 수 있다(히트박스·상호작용은
 * 렌더와 무관하다).
 *
 * <p>겹친 것 판단은 <b>1틱에 한 번</b>만 한다. 매 프레임 "내 주변에 같은 게 몇 개지?"를 세면 엔티티 수의
 * 제곱이 되어 고치려던 문제를 더 키운다. 틱마다 월드 엔티티를 한 번 훑어 "안 그릴 것" 목록을 만들어 두고,
 * 렌더 관문은 그 목록을 보기만 한다(동일성 Set이라 박싱도 리플렉션도 없다).
 *
 * <p>거리 판단은 렌더 관문이 이미 넘겨주는 카메라 좌표로 그 자리에서 뺄셈 세 번이다 - 목록도 API도 없다.
 *
 * <p>3-6의 "가려진 것 안 그리기"(진짜 오클루전 컬링)는 여기 없다 - 그건 레이캐스트를 따로 굴려야 하는
 * 별개의 큰 작업이고, 어설프게 하면 오히려 느려진다. 이 모듈은 <b>거리와 겹침</b>만 다룬다.
 */
public class EntityCullModule extends Module {

	private final BooleanSetting thin = register(new BooleanSetting(
			"thin", "겹침 감소", "같은 자리에 같은 종류가 쌓이면 앞의 몇 개만 그립니다.", true));

	private final IntSetting maxStack = register(new IntSetting(
			"max_stack", "한 자리 최대", "같은 자리/같은 종류를 최대 몇 개까지 그릴지입니다.", 4, 1, 20, 1));

	private final BooleanSetting far = register(new BooleanSetting(
			"far", "원거리 숨김", "정한 거리보다 먼 엔티티를 안 그립니다.", false));

	private final IntSetting distance = register(new IntSetting(
			"distance", "거리", "이 거리(블록)보다 멀면 안 그립니다.", 64, 16, 256, 8).unit("블록"));

	public EntityCullModule() {
		super("entity_cull", "엔티티 줄이기", ModuleCategory.FEATURE, "겹친 엔티티 | 먼 엔티티 생략");
		// 49-61차에 [그래픽] 설정 페이지로 옮겼다가 49-195차(사용자: "엔티티 줄이기 기능으로 옮기기")에 [기능] 격자로 되돌렸다.
		// 줄인 아이템은 빛기둥도 안 세운다(ItemLightBeamModule이 EntityHideHook.shouldHide를 본다).
	}

	@Override
	protected void onEnable() {
		push();
	}

	@Override
	protected void onDisable() {
		EntityHideHook.thinned = null;
		EntityHideHook.maxDistanceSq = 0;
	}

	@Override
	public void onTick() {
		push();
		if (!isEnabled() || !thin.get() || client == null || client.level == null) {
			EntityHideHook.thinned = null;
			return;
		}
		rebuild();
	}

	private void push() {
		boolean on = isEnabled();
		double d = far.get() ? distance.get() : 0;
		EntityHideHook.maxDistanceSq = on && d > 0 ? d * d : 0;
	}

	/**
	 * 한 틱치 "안 그릴 것" 목록 만들기. 자리 = 블록 한 칸, 종류 = EntityType 하나.
	 * 훑는 순서가 매 틱 크게 흔들리지 않으므로 살아남는 개체도 대체로 그대로다(깜빡이지 않게).
	 */
	private void rebuild() {
		int max = maxStack.get();
		java.util.Set<Entity> out = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		java.util.HashMap<Long, int[]> counts = new java.util.HashMap<>();
		try {
			for (Entity e : client.level.entitiesForRendering()) {
				if (e == null || e instanceof net.minecraft.world.entity.player.Player) {
					continue;
				}
				int[] c = counts.computeIfAbsent(cellKey(e), k -> new int[1]);
				if (++c[0] > max) {
					out.add(e);
				}
			}
		} catch (Throwable ignored) {
			return;   // 월드가 바뀌는 중 등 - 이번 틱은 건너뛴다(기존 목록 유지)
		}
		// 비었으면 null로 둔다 - 렌더 관문이 "꺼짐"으로 보고 더 빨리 빠져나간다.
		EntityHideHook.thinned = out.isEmpty() ? null : out;
	}

	/** (블록 한 칸 + 엔티티 종류)를 long 하나로. 충돌해도 조금 더/덜 줄어들 뿐 고장은 아니다. */
	private static long cellKey(Entity e) {
		long x = (long) Math.floor(e.getX()) & 0x3FFFFFFL;
		long y = (long) Math.floor(e.getY()) & 0xFFFL;
		long z = (long) Math.floor(e.getZ()) & 0x3FFFFFFL;
		long pos = (x << 38) | (y << 26) | z;
		return pos * 31L + typeHash(e);
	}

	private static int typeHash(Entity e) {
		try {
			Object type = e.getType();
			return type == null ? 0 : System.identityHashCode(type);
		} catch (Throwable ignored) {
			return 0;
		}
	}
}
