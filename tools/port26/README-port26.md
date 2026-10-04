# src-26x 자동 포팅 파이프라인 (49-125차)

main 트리(`src/client/java`, Yarn 1.21.11 이름)를 26.x 트리(`src-26x/client/java`, Mojang 이름)로 옮기는 도구.
클라우드 세션(리눅스, JDK 21)에서 돌렸다. PC에는 필요한 재료가 전부 있다(아래 목록).

## 재료(전부 PC 그레이들 캐시에 있음)
- Yarn tiny: `Nova-Mod/.gradle/loom-cache/projects/v1_21_11/source_mappings/*.tiny` (named=yarn, official, intermediary)
- Mojang tiny: `Nova-Mod/.gradle/loom-cache/projects/v_reverse_1_21_11/source_mappings/*.tiny` (named=mojang)
- Yarn MC jar: `Nova-Mod/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-{clientOnly,common}-6dd721cd7d/1.21.11-net.fabricmc.yarn.../*.jar`
- 26.2 Mojang MC jar: `.../minecraft-{clientOnly,common}-043a8b3edf/26.2/*.jar` (class 파일 버전 69 → javac 21로 읽으려면 65로 패치)
- Mercury(소스 리매퍼, JDT 내장): `~/.gradle/caches/modules-2/files-2.1/net.fabricmc/mercury/0.4.3/*/mercury-0.4.3.jar`
  + `org.cadixdev/{lorenz-0.5.7, bombe-0.3.4, at-0.1.0-rc1}`
- 라이브러리 jar: joml, guava, gson, fastutil, lwjgl(+glfw/opengl/stb), brigadier, datafixerupper, authlib, slf4j, log4j-api, commons-lang3, jetbrains annotations, fabric-loader, sponge-mixin
- fabric-api 1.21.11 (intermediary) → `tiny-remapper` 로 yarn 이름으로 리맵해서 Mercury classpath 에 넣어야
  Fabric 이벤트 람다 안(`ClientTickEvents.END_CLIENT_TICK.register(client -> …)`)까지 이름이 바뀐다.
- compat/screen-input-modern/…/NovaScreenBase.java 를 main 트리에 같이 넣어야 Screen 상속 멤버(font, minecraft, onClose…)가 바뀐다.

## 순서
1. `javac -cp mercury:lorenz:bombe:at Remap26.java`
2. `java -cp … Remap26 <yarn.tiny> <mojang.tiny> <main 소스 dir> <out dir> <classpath>` → Mojang 1.21.11 이름
3. `python3 port.py <out dir> <port26 dir>` → 26.x 전용 이름(fix262)
   - GuiGraphics→GuiGraphicsExtractor, ClickType→ContainerInput
   - drawString/drawText→text, drawCenteredString→centeredText, renderItem→item, renderFakeItem→fakeItem,
     renderItemDecorations→itemDecorations, renderBackground→extractBackground, renderImage→extractImage,
     handleInventoryMouseClick→handleContainerInput, getUuid→getUUID, hasStatusEffect→hasEffect,
     getEffectType→getEffect, getScaleFactor→getGuiScale
   - Screen 자식: `void render(GuiGraphicsExtractor…)`→`extractRenderState`, `super.render(`→`super.extractRenderState(`
   - `client.screen`→`NovaCompat.screenOf(client)`, `client.setScreen(x)`→`NovaCompat.showScreen(client, x)`,
     `client.getMainRenderTarget()`→`NovaCompat.mainRenderTarget(client)`
   - 문자열 안의 `"net.minecraft…"` 클래스 이름도 Mojang 이름으로. 멤버 이름 문자열(리플렉션)은 그대로 둔다 -
     런타임에 yarnmap.txt 가 yarn→mojang 으로 풀어 준다(프로덕션 jar).
4. `build_cand.sh` : zip26(직전 26x 트리) 위에 port26 을 덮고 javac 로 컴파일 검사(26.2 jar + 라이브러리).
   baseline 잡음(fabric-api/voicechat/modmenu 없음)만 남으면 OK.
5. **손으로 유지하는 파일**(자동 덮지 않음): `mixin/*`(26.x 전용 Hud/Gui 쌍둥이), `util/NovaCompat.java`
   (26.x 전용 헬퍼 screenOf/showScreen/mainRenderTarget 등이 있어 base 는 26x 것, main 의 새 헬퍼만 add_methods.py 로 추가),
   `gui/NovaGfx.java`(26.x BlendFactor/ColorTargetState 분기).
6. 26.x 믹스인 대상 메서드 이름: Hud(26.2)/Gui(26.1.x) 쌍둥이. `extractItemHotbar`, `extractPlayerHealth`, `extractCrosshair`,
   PlayerTabOverlay: `getPlayerInfos`, `extractRenderState`, `getNameForDisplay`.
7. 결과를 src-26x 에 쓰고 src-26x.zip 도 같이 갱신(apply-26x.cmd 가 zip 을 풀기 때문).
