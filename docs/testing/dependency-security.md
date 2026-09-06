# 의존성 보안 패치 검증

앱 실행 의존성과 빌드·테스트 도구의 의존성은 별도로 해석된다. `Android Dependency Audit`은 루트와 앱의 빌드 클래스패스, 앱의 전체 구성(컴파일·실행·ktlint·UTP), included build의 컴파일 구성을 함께 수집한다. 기존 debug 실행 그래프도 별도 파일로 유지하여 비교할 수 있다. 선언 전용 `(n)` 항목은 실제 선택 버전으로 세지 않고, 해석 실패는 감사의 미확인 항목으로 남긴다.

## 현재 적용한 최소 버전

`gradle/libs.versions.toml`의 `security-*` 별칭은 기존 전이 의존성에 대한 최소 제약이다. 기존 Bouncy Castle 강제 정렬은 유지하며 값은 같은 카탈로그에서 읽는다. 나머지 제약은 상위 버전 선택을 막지 않고 앱에 도구 라이브러리를 추가하지 않는다. Netty는 BOM으로 같은 4.1 패치 계열을 맞춘다.

| 좌표 | 최소 버전 | 적용 경로·근거 |
| --- | --- | --- |
| `io.netty:netty-bom` | `4.1.137.Final` | App Distribution·UTP. [공식 패치 릴리스](https://netty.io/news/2026/08/06/4-1-137-Final.html) |
| `org.jdom:jdom2` | `2.0.6.1` | AGP Jetifier. [릴리스 안내](https://www.jdom.org/news/) |
| `org.bitbucket.b_c:jose4j` | `0.9.6` | AGP bundletool. [공식 수정](https://bitbucket.org/b_c/jose4j/commits/19a90a64c47bb07c4aa5462f1316d5c293d81fcf) |
| `org.apache.commons:commons-lang3` | `3.18.0` | AGP·UTP. [릴리스 기록](https://commons.apache.org/proper/commons-lang/changes.html) |
| `org.apache.httpcomponents:httpclient` | `4.5.14` | UTP의 구버전 요청을 기존 빌드 도구 버전과 정렬. [공식 배포 기록](https://archive.apache.org/dist/httpcomponents/httpclient/RELEASE_NOTES-4.5.x.txt) |
| `com.google.guava:guava` | `32.1.3-android` | 앱 컴파일 의존성을 기존 실행 버전과 정렬. [릴리스 기록](https://github.com/google/guava/releases/tag/v32.1.3) |
| `ch.qos.logback:logback-classic`, `ch.qos.logback:logback-core` | `1.6.3` | ktlint 도구에만 적용. [릴리스·호환성 안내](https://logback.qos.ch/news.html) |
| `org.bouncycastle:bcprov-jdk18on` | `1.85.2` | 기존 루트·UTP 패치를 included build에도 적용하고 provider 패치를 반영. [공식 릴리스](https://www.bouncycastle.org/download/bouncy-castle-java/) |
| `org.bouncycastle:bcpkix-jdk18on`, `org.bouncycastle:bcutil-jdk18on` | `1.85` | provider 전용 1.85.2 패치에는 새 pkix/util 배포가 없으므로 기존 배포 버전 유지. [공식 릴리스](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-85/) |

Logback 1.3은 유지보수가 종료되어 1.6 계열로 옮겼다. ktlint가 호출하는 Logger/Level API와 실제 ktlint 실행을 확인한다. 이 도구는 JDK 21에서 실행하며 앱의 JVM 11 설정을 바꾸지 않는다. [Logback 요구 사항](https://logback.qos.ch/dependencies.html)

## 재검증

JDK 21에서 다음을 실행한다. `:app:dependencies`에서 UTP 및 ktlint의 선택 버전도 확인한다. 버전 변경 후 빌드·lint·단위 테스트와 UTP 연결 테스트를 함께 실행해야 한다.

```sh
./gradlew buildEnvironment :app:buildEnvironment :app:dependencies --max-workers=2
./gradlew -p build-logic :convention:dependencies --configuration compileClasspath
./gradlew assembleDebug testDebugUnitTest lintDebug ktlintCheck --max-workers=2
ANDROID_SERIAL=<전용-에뮬레이터> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.slowclock.ExampleInstrumentedTest,com.example.slowclock.AlarmCommandIdentityTest \
  --max-workers=2
node --test .github/scripts/*.test.mjs
```

연결 테스트는 폐기 가능한 에뮬레이터에서 수행한다. 이 검증은 실제 App Distribution 업로드나 운영 서비스 호출을 포함하지 않는다. 배포 시 서비스 인증·권한 검증은 [배포 절차](../release/distribution.md)를 따른다.

## 남아 있는 경계

2026-09-06 기준 `org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10`의 GHSA-r937-wjx7-w2jp는 경보를 유지한다. [공식 수정](https://github.com/JetBrains/kotlin/commit/bf51df665b458fda7c3eaf436c4d88dc119d7ec6)은 KAPT 증분 캐시 역직렬화 경로이며 현재 프로젝트는 KSP를 사용하고 KAPT 플러그인·작업을 사용하지 않는다. 패치가 포함된 2.4.20은 아직 시험판이므로, 안정 버전 공개 후 Kotlin·Compose compiler를 함께 갱신하고 전체 호환성 검증을 다시 수행한다. 이 판단을 전체 빌드 도구의 안전 보장이나 경보 해제로 확대하지 않는다. [Kotlin 공식 릴리스](https://github.com/JetBrains/kotlin/releases)

npm의 Functions 실행 의존성과 Firestore 테스트 도구는 각각의 lockfile과 테스트로 별도 검증한다. GitHub 경보 수는 기본 브랜치의 의존성 snapshot이 반영된 후 다시 확인한다.
