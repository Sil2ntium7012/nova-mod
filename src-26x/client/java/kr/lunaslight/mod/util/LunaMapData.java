package kr.lunaslight.mod.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.network.LunaMapBoundsPayload;
import kr.lunaslight.mod.network.LunaZonePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-407차: LUNA FESTIVAL 서버가 보내는 지도 정보(요청: "Xaero's Minimap / World Map에 반영").
 * <ul>
 *   <li>{@code luna:zone}: 도시능력자 자기장. {"world","cx","cz","r","nx","nz","nr","stage","shrinking","left"} -
 *       지금 자기장 = 파란 원, 다음 안전지대 = 흰 원. {"world","clear":true}면 지움.</li>
 *   <li>{@code luna:mapbounds}: 게임 맵 범위 {"world","minX","minZ","maxX","maxZ","name"} - 월드맵을 열면 범위 전체가 한눈에
 *       보이게 맞추고, 범위 밖으로는 카메라와 축소가 못 나가게, 범위 밖은 어둡게.</li>
 * </ul>
 * 받은 때의 차원을 기억해 그 차원에서만 그린다(월드를 옮기면 서버가 새로 보냄). 연결이 끊기면 다 지운다.
 * 그리기는 Xaero 쪽 {@code kr.lunaslight.mod.compat.xaero.XaeroBridge}(Xaero가 깔려 있을 때만, 리플렉션으로 켬).
 */
public final class LunaMapData {
	private LunaMapData() {
	}

	/** 원 하나(블록 좌표, 반지름, ARGB). */
	public record Circle(double x, double z, double r, int color) {
	}

	/** 범위(블록 좌표). */
	public record Bounds(double minX, double minZ, double maxX, double maxZ, String name) {
		public double centerX() {
			return (minX + maxX) / 2.0;
		}

		public double centerZ() {
			return (minZ + maxZ) / 2.0;
		}
	}

	public static final int ZONE_COLOR = 0xFF3D8BFF;   // 지금 자기장(파랑)
	public static final int NEXT_COLOR = 0xFFFFFFFF;   // 다음 안전지대(흰색)

	private static volatile boolean zoneOn;
	private static double cx, cz, r, nx, nz, nr;
	private static int stage, left;
	private static boolean shrinking;
	private static ResourceKey<Level> zoneDim;
	/** 그리는 값(0.5초마다 오는 값 사이를 부드럽게). */
	private static double dcx, dcz, dr, dnx, dnz, dnr;
	private static boolean shownOnce;
	private static long lastNs;

	private static volatile Bounds bounds;
	private static ResourceKey<Level> boundsDim;
	/** 범위가 새로 왔다(다음에 월드맵을 열 때 맞춘다). */
	private static volatile long boundsVersion;

	public static void init() {
		try {
			PayloadTypeRegistry.clientboundPlay().register(LunaZonePayload.TYPE, LunaZonePayload.CODEC);
			ClientPlayNetworking.registerGlobalReceiver(LunaZonePayload.TYPE, (p, ctx) -> onZone(p.json()));
			PayloadTypeRegistry.clientboundPlay().register(LunaMapBoundsPayload.TYPE, LunaMapBoundsPayload.CODEC);
			ClientPlayNetworking.registerGlobalReceiver(LunaMapBoundsPayload.TYPE, (p, ctx) -> onBounds(p.json()));
			ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> clearAll());
		} catch (Throwable t) {
			LunaClientMod.LOGGER.info("[Nova] 지도 패킷(luna:zone, luna:mapbounds) 등록 실패: " + t);
		}
		FabricLoader fl = FabricLoader.getInstance();
		if (fl.isModLoaded("xaerominimap") || fl.isModLoaded("xaerobetterpvp") || fl.isModLoaded("xaeroworldmap")) {
			try {
				// 49-407차: Xaero 쪽 코드는 Xaero 클래스를 직접 쓰므로, Xaero가 없거나 빌드에서 빠졌으면 여기서 조용히 끝난다.
				Class.forName("kr.lunaslight.mod.compat.xaero.XaeroBridge").getMethod("init").invoke(null);
			} catch (Throwable t) {
				LunaClientMod.LOGGER.info("[Nova] Xaero 지도 연동을 못 켬: " + t);
			}
		}
	}

	private static ResourceKey<Level> dimNow() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null || mc.level == null ? null : mc.level.dimension();
	}

	private static void clearAll() {
		zoneOn = false;
		shownOnce = false;
		bounds = null;
	}

	private static double num(JsonObject o, String k, double def) {
		try {
			return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
		} catch (Throwable t) {
			return def;
		}
	}

	@SuppressWarnings("deprecation")
	private static JsonObject parse(String json) {
		try {
			JsonElement el = new JsonParser().parse(json == null ? "" : json.trim());
			return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("lunaMap:json", t);
			return null;
		}
	}

	static void onZone(String json) {
		JsonObject o = parse(json);
		if (o == null) {
			return;
		}
		if (o.has("clear") && o.get("clear").isJsonPrimitive() && o.get("clear").getAsBoolean()) {
			zoneOn = false;
			shownOnce = false;
			return;
		}
		cx = num(o, "cx", 0);
		cz = num(o, "cz", 0);
		r = Math.max(0, num(o, "r", 0));
		nx = num(o, "nx", cx);
		nz = num(o, "nz", cz);
		nr = Math.max(0, num(o, "nr", 0));
		stage = (int) num(o, "stage", 0);
		left = (int) num(o, "left", 0);
		shrinking = o.has("shrinking") && o.get("shrinking").isJsonPrimitive() && o.get("shrinking").getAsBoolean();
		ResourceKey<Level> d = dimNow();
		if (!zoneOn || d != zoneDim) {
			shownOnce = false;
		}
		zoneDim = d;
		zoneOn = true;
	}

	static void onBounds(String json) {
		JsonObject o = parse(json);
		if (o == null) {
			return;
		}
		if (o.has("clear") && o.get("clear").isJsonPrimitive() && o.get("clear").getAsBoolean()) {
			bounds = null;
			return;
		}
		double x0 = num(o, "minX", 0), z0 = num(o, "minZ", 0), x1 = num(o, "maxX", 0), z1 = num(o, "maxZ", 0);
		if (x1 - x0 < 1 || z1 - z0 < 1) {
			bounds = null;
			return;
		}
		String name = o.has("name") && o.get("name").isJsonPrimitive() ? o.get("name").getAsString() : "";
		boundsDim = dimNow();
		bounds = new Bounds(x0, z0, x1, z1, name);
		boundsVersion++;
	}

	/** 이 차원에서 그릴 원들(지금 자기장 파랑, 다음 안전지대 흰색). 그리는 쪽이 프레임마다 부른다. */
	public static List<Circle> circles(ResourceKey<Level> dim) {
		List<Circle> out = new ArrayList<>(2);
		if (!zoneOn || zoneDim == null || dim == null || !zoneDim.equals(dim)) {
			return out;
		}
		long now = System.nanoTime();
		if (!shownOnce) {
			dcx = cx;
			dcz = cz;
			dr = r;
			dnx = nx;
			dnz = nz;
			dnr = nr;
			shownOnce = true;
		} else {
			double dt = Math.min(0.25, Math.max(0, (now - lastNs) / 1e9));
			double k = 1 - Math.exp(-dt * 4.0);   // 0.5초마다 오는 값 사이를 따라붙음
			dcx += (cx - dcx) * k;
			dcz += (cz - dcz) * k;
			dr += (r - dr) * k;
			dnx += (nx - dnx) * k;
			dnz += (nz - dnz) * k;
			dnr += (nr - dnr) * k;
		}
		lastNs = now;
		if (dnr > 0.5 && (Math.abs(dnr - dr) > 0.5 || Math.abs(dnx - dcx) > 0.5 || Math.abs(dnz - dcz) > 0.5)) {
			out.add(new Circle(dnx, dnz, dnr, NEXT_COLOR));
		}
		if (dr > 0.5) {
			out.add(new Circle(dcx, dcz, dr, ZONE_COLOR));
		}
		return out;
	}

	/** 이 차원의 맵 범위(없으면 null). */
	public static Bounds bounds(ResourceKey<Level> dim) {
		Bounds b = bounds;
		return b == null || boundsDim == null || dim == null || !boundsDim.equals(dim) ? null : b;
	}

	/** 지금 차원의 맵 범위(없으면 null). */
	public static Bounds boundsNow() {
		return bounds(dimNow());
	}

	public static long boundsVersion() {
		return boundsVersion;
	}

	/** 남은 초, 단계, 줄어드는 중(지금은 표시 안 함 - 필요하면 HUD에서). */
	public static int left() {
		return left;
	}

	public static int stage() {
		return stage;
	}

	public static boolean shrinking() {
		return shrinking;
	}
}
