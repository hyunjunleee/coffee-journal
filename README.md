# Coffee Journal

개인 커피 저널 웹앱(`coffee-journal-empty-template`)을 분석하고, 같은 기능을 갖는 모바일 앱을 Kotlin Multiplatform + Compose Multiplatform으로 구현한 저장소입니다. 1차는 Android, 공유 모듈은 iOS 확장을 염두에 두고 설계했습니다.

## 문서
- `docs/coffee-journal-site-analysis.md` — 웹 원본 전수 분석(구조·기능·데이터·참조 데이터·버그)
- `docs/android-app-design.md` — 앱 설계서(스택 결정, 모듈, DB 스키마, 화면, 디자인 토큰, 규칙, 마일스톤)
- `docs/dev/implementation-notes.md` — 구현 규약(빌드, 패키지 소유, API, 디자인)
- `docs/feature-plan-v2.md` — 2차 기능 설계(SGIS 한국 지도·지도 앱 링크, 추출 타이머, 비교표·계산기, CVA 양식, 통계, 알림·위젯, 서명 키·CI)
- `iosApp/README.md` — iOS 호스트 앱 준비 절차

## 구조
```
shared/      KMP 공유 모듈: domain(model·rules·reference) / data(db·repo·backup·photo) / ui(theme·nav·features) / di
androidApp/  Android 앱 (MainActivity → shared App()), JVM 스크린샷 테스트(Robolectric + Roborazzi)
iosApp/      SwiftUI 호스트 스켈레톤 (macOS에서 coffeejournal.enableIos=true 로 활성화)
```

## 빌드·테스트
```
export ANDROID_HOME=/opt/android-sdk          # Android SDK 위치 (local.properties의 sdk.dir 도 가능)
./gradlew :androidApp:assembleDebug            # APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :shared:testDebugUnitTest            # 도메인 규칙·백업 코덱·저장 파이프라인 등 단위 테스트
./gradlew :androidApp:testDebugUnitTest        # 실제 App()을 JVM에서 띄워 누르는 흐름·스크린샷 테스트
./gradlew :androidApp:recordRoborazziDebug     # 실제 화면을 JVM에서 렌더한 PNG → androidApp/screenshots/
```
- 흐름 테스트(Robolectric + Compose UI Test)는 에뮬레이터 없이 실제 앱 화면을 탭·입력해 웹 원본 동작(`script3.js`)과 저장된 데이터를 함께 검증한다: 기록 3모드 입력·수정·삭제, 레시피 런처, 단계 로그, 추출 타이머(가짜 시계·진동·화면 유지·프로세스 복원), 추출 비교표, 비율·추출수율 계산기, SCA CVA 양식, 통계, 플레이버 휠, 블렌드, 커핑 원두 편집, 달력·로드맵, 원두 탭 9뷰(지도 탭 판정 포함), 장비, 백업 왕복(웹 파일 포함)·손상 파일·원자적 복원, 한글 조합 입력, 자정 전환, 저장 경합, 접근성 라벨.
- `androidApp/screenshots/`에는 주요 화면 PNG가 커밋되어 있고, 320dp 폭·글자 1.3/2.0배 렌더 매트릭스(`screenshots/matrix/`)는 같은 명령으로 생성된다(커밋하지 않음).
요구 사항: JDK 17+, Android SDK Platform 36 / Build-Tools 35. Gradle 래퍼(8.14.3) 포함.

### 빌드 공급망
- 라이브러리는 공식 저장소에서만 받습니다: Google Maven(androidx·com.android·com.google), Maven Central, Gradle Plugin Portal(플러그인).
- 받은 모든 파일은 `gradle/verification-metadata.xml`의 SHA-256과 대조되고, 다르면 빌드가 멈춥니다. Gradle 배포본은 `gradle-wrapper.properties`의 `distributionSha256Sum`으로 검증됩니다.
- 의존성을 추가·변경했다면: `./gradlew --write-verification-metadata sha256 help :androidApp:assembleRelease :shared:testDebugUnitTest :androidApp:testDebugUnitTest`로 새 항목을 기록하고, 추가된 줄의 출처를 확인한 뒤 커밋합니다.
- Maven Central이 요청 수를 제한하는 빌드 환경에서는 `-Pcoffeejournal.mavenCentralMirror=https://maven-central.storage-download.googleapis.com/maven2/`(또는 `~/.gradle/gradle.properties`)로 미러를 앞에 둘 수 있습니다. 이때도 파일은 위 체크섬으로 검증됩니다.

### 출처 · 라이선스
- 앱의 기타 탭 맨 아래 "출처 · 오픈소스 라이선스"에서 데이터·디자인 출처(웹 템플릿, SCA·WCR 플레이버 휠, SCA 커핑 폼(2004·CVA SCA-103/104), SCA 추출 조절 차트, 카페 레시피, Natural Earth, Lucide·Feather 아이콘)와 APK에 들어간 오픈소스 라이브러리 전체를 라이선스별로 보여줍니다.
- 라이브러리 목록은 `./gradlew :androidApp:updateThirdPartyNotices`가 릴리스 런타임 클래스패스의 POM에서 생성하고(`shared/src/androidMain/.../ui/about/PlatformLibraries.android.kt`), 모든 빌드 전에 `checkThirdPartyNotices`가 목록이 최신인지 확인합니다.

## 데이터
모든 데이터는 기기 로컬 SQLite(Room)와 앱 전용 사진 폴더에 저장됩니다. 서버·계정·AI 호출은 없습니다. 웹 백업 JSON과 상호 호환되는 백업/복원을 제공합니다.
