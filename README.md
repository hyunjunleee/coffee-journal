# Coffee Journal

개인 커피 저널 웹앱(`coffee-journal-empty-template`)을 분석하고, 같은 기능을 갖는 모바일 앱을 Kotlin Multiplatform + Compose Multiplatform으로 구현한 저장소입니다. 1차는 Android, 공유 모듈은 iOS 확장을 염두에 두고 설계했습니다.

## 문서
- `docs/coffee-journal-site-analysis.md` — 웹 원본 전수 분석(구조·기능·데이터·참조 데이터·버그)
- `docs/android-app-design.md` — 앱 설계서(스택 결정, 모듈, DB 스키마, 화면, 디자인 토큰, 규칙, 마일스톤)
- `docs/dev/implementation-notes.md` — 구현 규약(빌드, 패키지 소유, API, 디자인)
- `docs/feature-plan-v2.md` — 2차 기능 설계(SGIS 한국 지도·OpenStreetMap 상세 지도·지도 앱 링크, 추출 타이머, 비교표·계산기, CVA 양식, 통계, 알림·위젯, 서명 키·CI)
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
- 흐름 테스트(Robolectric + Compose UI Test)는 에뮬레이터 없이 실제 앱 화면을 탭·입력해 웹 원본 동작(`script3.js`)과 저장된 데이터를 함께 검증한다: 기록 3모드 입력·수정·삭제, 레시피 런처, 단계 로그, 추출 타이머(가짜 시계·진동·화면 유지·프로세스 복원), 추출 비교표, 비율·추출수율 계산기, SCA CVA 양식, 통계, 플레이버 휠, 블렌드, 커핑 원두 편집, 달력·로드맵, 원두 탭 9뷰(지도 탭 판정 포함), SGIS 지도 제스처(두 손가락 확대, 확대 중 끌기, 기본 배율에서는 페이지 스크롤), 상세 지도(MapLibre 대신 가짜 렌더러로 핀·패널·지도 앱 링크·오프라인·렌더러 실패·십자 위치 지정), 장비, 백업 왕복(웹 파일 포함)·손상 파일·원자적 복원, 한글 조합 입력, 자정 전환, 저장 경합, 접근성 라벨.
- `androidApp/screenshots/`에는 주요 화면 PNG가 커밋되어 있고, 320dp 폭·글자 1.3/2.0배 렌더 매트릭스(`screenshots/matrix/`)는 같은 명령으로 생성된다(커밋하지 않음).
요구 사항: JDK 17+, Android SDK Platform 36 / Build-Tools 35. Gradle 래퍼(8.14.3) 포함.

### 빌드 공급망
- 라이브러리는 공식 저장소에서만 받습니다: Google Maven(androidx·com.android·com.google), Maven Central, Gradle Plugin Portal(플러그인).
- 받은 모든 파일은 `gradle/verification-metadata.xml`의 SHA-256과 대조되고, 다르면 빌드가 멈춥니다. Gradle 배포본은 `gradle-wrapper.properties`의 `distributionSha256Sum`으로 검증됩니다.
- 의존성을 추가·변경했다면: `./gradlew --write-verification-metadata sha256 help :androidApp:assembleDebug :androidApp:assembleRelease :shared:testDebugUnitTest :androidApp:testDebugUnitTest`로 새 항목을 기록하고, 추가된 줄의 출처를 확인한 뒤 커밋합니다. 이어서 `./gradlew :androidApp:updateThirdPartyNotices`로 앱 내 라이브러리 목록을 다시 만들어 함께 커밋합니다(목록이 낡으면 빌드 전 `checkThirdPartyNotices`가 실패). POM의 라이선스가 목록에 없는 종류면 생성이 멈추므로 `gradle/third-party-notices.gradle.kts`의 `spdx()`와 `LicenseTexts`에 추가합니다.
- 예: 알림·위젯(2차 §3)에서 `androidx.work:work-runtime` 2.12.0, `androidx.glance:glance-appwidget` 1.2.0(테스트 전용 `work-testing` 2.12.0, `glance-appwidget-testing` 1.2.0)을 `gradle/libs.versions.toml`에 더하고 위 두 명령을 차례로 실행했습니다. 새로 기록된 항목은 모두 Google Maven의 `androidx.*`(glance·work·datastore·tracing·core-remoteviews·lifecycle-service/livedata 등과 그 POM이 가리키는 compose·collection 버전)이고, 라이선스는 Apache-2.0과 BSD-3-Clause(Glance에 든 protobuf)로 기존 목록 안이었습니다.
- Maven Central이 요청 수를 제한하는 빌드 환경에서는 `-Pcoffeejournal.mavenCentralMirror=https://maven-central.storage-download.googleapis.com/maven2/`(또는 `~/.gradle/gradle.properties`)로 미러를 앞에 둘 수 있습니다. 이때도 파일은 위 체크섬으로 검증됩니다.

### 배포 (고정 서명 키 · CI)
- 릴리스 APK는 프로젝트 고정 키로 서명된다. 키 파일과 비밀번호는 저장소에 넣지 않고, 환경 변수(`COFFEEJOURNAL_KEYSTORE_FILE`, `COFFEEJOURNAL_KEYSTORE_PASSWORD`, `COFFEEJOURNAL_KEY_ALIAS`, `COFFEEJOURNAL_KEY_PASSWORD`)나 `~/.gradle/gradle.properties`의 `coffeejournal.keystore.*`로 넘긴다. 없으면 릴리스 APK는 서명 없이 만들어진다.
- 버전 코드는 커밋 시각(2026-01-01부터의 분)이라, 뒤 커밋의 APK가 항상 앞 APK 위에 설치된다. 버전 이름에는 커밋 해시가 붙는다.
- GitHub Actions(`.github/workflows/android.yml`): 푸시·PR마다 전체 테스트 → 서명된 릴리스 APK를 아티팩트로 올린다. `v*` 태그를 푸시하면 GitHub Release에 APK가 첨부된다. 서명된 APK는 푸시할 때마다 GitHub의 **APK builds** 릴리스(태그 `apk-builds`) 한 곳에 `coffee-journal-<버전 코드>-<커밋>.apk`로 계속 쌓인다(만료 없음, 버전 코드가 클수록 최신). 저장소 Secrets에 `COFFEEJOURNAL_KEYSTORE_BASE64`(키 파일의 base64), `COFFEEJOURNAL_KEYSTORE_PASSWORD`, `COFFEEJOURNAL_KEY_ALIAS`, `COFFEEJOURNAL_KEY_PASSWORD`를 등록해야 서명된다.
- 키를 잃어버리면 같은 앱으로 업데이트할 수 없으니 키 파일과 비밀번호는 따로 안전하게 보관한다.

- 상세 지도(MapLibre)의 네이티브 라이브러리가 4개 ABI(arm64-v8a, armeabi-v7a, x86, x86_64) 모두 들어 있어(압축 전 약 49 MB) 하나의 APK로 어느 기기에나 설치됩니다. 네이티브 라이브러리는 압축해 넣어(`jniLibs.useLegacyPackaging`) 릴리스 APK는 약 31 MiB(기존 약 16 MiB)이고, 설치할 때 기기에 맞는 것만 풀립니다.

### 알림 · 홈 화면 위젯 (Android)
- 기타 탭 맨 아래 "알림 설정"에서 켜면 하루 한 번(기본 09:00) 보관함 원두의 예상 피크 시작일, 마시는 중 원두의 소진 임박(평소 원두량 기준 2잔 이하), Coffee D-day 마일스톤(30·100일 단위)을 알립니다. 종류별로 끌 수 있고, 같은 알림은 두 번 보내지 않습니다. Android 13 이상은 켤 때 알림 권한을 묻습니다(거절하면 꺼진 채로 안내). 알림을 누르면 원두 보관함 또는 홈이 열립니다.
- 홈 화면 위젯(위젯 목록의 "커피 D-day · 마시는 중")은 홈 화면과 같은 D-day 문구, 마시는 중 원두와 잔여량, "+ 새 기록"(새 기록 폼으로 바로)을 보여 주고, 기록을 저장하거나 앱을 나갈 때, 자정 직후에 갱신됩니다.
- 모두 기기 안에서 계산합니다. 알림 설정은 이 기기의 설정이라 JSON 백업에 들어가지 않습니다. 하루 점검은 WorkManager가 맡으므로 휴대폰 절전 상태에 따라 조금 늦게 올 수 있습니다. iOS는 같은 규칙을 공유 코드에 두었고 전달·위젯 연결은 아직입니다.

### 출처 · 라이선스
- 앱의 기타 탭 맨 아래 "출처 · 오픈소스 라이선스"에서 데이터·디자인 출처(웹 템플릿, SCA·WCR 플레이버 휠, SCA 커핑 폼(2004·CVA SCA-103/104), SCA 추출 조절 차트, 카페 레시피, Natural Earth, 한국 지도(통계청 SGIS 경계 · vuski/admdongkor), 상세 지도(OpenStreetMap ODbL 1.0 · OpenMapTiles · OpenFreeMap · MapLibre, MapLibre Native가 함께 넣은 라이브러리들의 고지 원문 포함), Lucide·Feather 아이콘)와 APK에 들어간 오픈소스 라이브러리 전체를 라이선스별로 보여줍니다.
- 라이브러리 목록은 `./gradlew :androidApp:updateThirdPartyNotices`가 릴리스 런타임 클래스패스의 POM에서 생성하고(`shared/src/androidMain/.../ui/about/PlatformLibraries.android.kt`), 모든 빌드 전에 `checkThirdPartyNotices`가 목록이 최신인지 확인합니다.

### 한국 지도 데이터
- 로스터리·카페 지도의 시·도, 시·군·구 경계는 앱에 들어 있습니다(오프라인에서 바로 열림). `tools/korea-map/build_korea_map.py`가 vuski/admdongkor `ver20260701` 행정동 GeoJSON(통계청 SGIS 경계 보정본; SGIS 공공누리 제1유형, 보정본 CC BY 4.0)을 시·군·구·시·도로 병합하고 단순화해 `shared/src/commonMain/kotlin/com/coffeejournal/domain/reference/KoreaMapData*.kt`를 생성합니다(입력 파일의 SHA-256을 생성 파일 머리말에 기록).
- 다시 만들기: `pip install shapely` 후 `python3 tools/korea-map/build_korea_map.py --input <받은 geojson>`(`--input`이 없으면 원본 URL에서 받음, `--preview <폴더>`로 PNG 미리보기).

### 상세 지도 (OpenStreetMap)
- 로스터리·카페 지도와 위치 지정의 "상세 지도"는 [OpenFreeMap](https://openfreemap.org/)의 벡터 타일(OpenStreetMap 데이터, OpenMapTiles 스키마; 키·계정 없음, 상업적 사용 허용)을 [MapLibre Native](https://maplibre.org/)로 앱 색에 맞춰 그립니다. 스타일은 앱 코드(`ui/map/detail/DetailMapStyle.kt`)에 있습니다.
- 지도 위에는 항상 "© OpenMapTiles © OpenStreetMap contributors"를 표시합니다(OpenStreetMap 데이터는 [ODbL 1.0](https://www.openstreetmap.org/copyright)).
- 새 의존성(Maven Central): `org.maplibre.compose:maplibre-compose` 0.12.1(BSD-3-Clause)과 그것이 가져오는 `org.maplibre.gl:android-sdk` 12.0.1(BSD-2-Clause, 네이티브 렌더러), MapLibre의 제스처·축척 막대·GeoJSON 라이브러리, OkHttp, Timber, Gson, Kermit, Spatial K 등. 체크섬은 `gradle/verification-metadata.xml`, 목록은 앱의 라이선스 화면에 있습니다.

## 데이터
모든 데이터는 기기 로컬 SQLite(Room)와 앱 전용 사진 폴더에 저장됩니다. 서버·계정·AI 호출은 없습니다. 웹 백업 JSON과 상호 호환되는 백업/복원을 제공합니다. "네이버 지도에서 열기" 같은 지도 링크는 해당 지도 앱(없으면 웹 지도)을 링크로 열 뿐, 앱이 직접 보내는 데이터는 없습니다.

네트워크를 쓰는 곳은 상세 지도뿐입니다. 상세 지도를 열었을 때만 화면에 보이는 지역의 지도 조각과 글꼴 조각을 OpenFreeMap(tiles.openfreemap.org, Cloudflare CDN을 거칠 수 있음)에서 받습니다. 요청에는 여느 인터넷 요청처럼 IP 주소와 User-Agent(앱 이름·버전, MapLibre·Android 버전)가 담기고, 기록·로스터리·카페 정보는 보내지 않습니다. 미리 받기는 하지 않고, 받은 조각은 MapLibre의 캐시(기기 안, 백업에 포함되지 않음)에만 남습니다. 위치 권한은 쓰지 않습니다. 네트워크가 없으면 상세 지도 대신 안내가 나오고, 시·도·시·군·구 지도는 앱에 들어 있어 그대로 동작합니다.
