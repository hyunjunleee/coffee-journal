# iOS 앱

안드로이드 앱과 같은 공유 Kotlin 코드(`shared`)를 SwiftUI 호스트(`iosApp/iosApp`)가 띄운다. Xcode 프로젝트는 커밋하지 않고 `project.yml`에서 [XcodeGen](https://github.com/yonaskolb/XcodeGen)으로 만든다.

## 빌드

- CI(`.github/workflows/ios.yml`):
  - `klibs`(Linux): 공유 코드나 iosApp이 바뀐 모든 푸시에서 돈다.
    - iOS용 Kotlin 코드를 klib으로 컴파일한다(Kotlin/Native 크로스 컴파일, `-Pcoffeejournal.iosKlibs=true`). iOS에서만 깨지는 변경을 macOS 없이 잡는다.
    - iOS 앱의 출처 · 라이선스 목록이 의존성과 맞는지도 확인한다.
  - `app`(macOS): 직접 돌리거나(Actions › iOS › Run workflow) 태그 v*에서만 돈다.
    - 비공개 저장소에서는 macOS 1분이 리눅스 약 10분 값이라 푸시마다 돌리지 않는다.
    - Shared 프레임워크를 링크하고, Xcode 프로젝트를 만들고, 서명 없이 아이폰용으로 빌드한다.
    - 결과 `coffee-journal-<빌드 번호>-<커밋>-unsigned.ipa`를 "iOS builds" 릴리스에 올린다. 태그 v*이면 그 태그의 릴리스에도 APK 옆에 붙인다.
  - `tests`(macOS): 직접 돌릴 때 tests를 켜면 `app` 옆에서 따로 돈다.
    - 공유 테스트(commonTest · iosTest)를 아이폰 시뮬레이터에서 돌린다.
    - 아래 "Mac에서 직접 빌드하기"와 같은 Debug 빌드를 시뮬레이터용으로 만들고, 시뮬레이터에서 실행해 30초 뒤에도 떠 있는지 확인한다. 그때 화면을 `ios-debug-app` 아티팩트로 남긴다.
  - 버전은 안드로이드와 같다. 버전 이름은 `androidApp/build.gradle.kts`의 versionName을 쓰고, 빌드 번호는 버전 코드와 같은 방식(2026-01-01부터 커밋 시각까지의 분)으로 계산한다.
- Mac에서 직접 빌드하기:
  1. `brew install xcodegen`
  2. `cd iosApp && xcodegen generate`
  3. `open CoffeeJournal.xcodeproj`로 연다.
  4. Signing & Capabilities에서 Team을 본인 Apple ID(Personal Team)로 고른 뒤 아이폰에 실행한다.
     - 빌드 중에 Xcode가 `./gradlew :shared:embedAndSignAppleFrameworkForXcode`로 공유 코드를 컴파일한다. 처음에는 몇 분 걸린다.

## 아이폰에 설치하기 (앱스토어 없이)

iOS는 서명된 앱만 설치된다. CI가 만든 .ipa에는 서명이 없으므로, 설치할 때 본인 Apple ID로 서명한다.

- **무료 Apple ID + 사이드로딩 도구**
  - 방법:
    - [Sideloadly](https://sideloadly.io)(Windows·Mac)에 .ipa를 끌어다 놓고, Apple ID로 로그인해 설치한다.
    - 또는 [AltStore](https://altstore.io)나 [SideStore](https://sidestore.io)를 쓴다.
  - 무료 계정으로 서명한 앱은 7일 뒤 만료되어 다시 서명해야 한다. 한 번에 3개 앱까지다.
    - AltStore는 같은 Wi-Fi에 있는 AltServer가 갱신한다.
    - SideStore는 처음 설정한 뒤에는 기기 혼자 갱신한다.
  - 처음 실행하기 전에:
    1. 설정 › 개인정보 보호 및 보안 › 개발자 모드를 켜고 재시동한다.
    2. 설정 › 일반 › VPN 및 기기 관리에서 본인 Apple ID 개발자를 신뢰한다.
  - 원격 푸시는 안 된다. 이 앱의 알림은 기기 안의 로컬 알림이라 그대로 동작한다.
- **Mac + Xcode**: 위 "Mac에서 직접 빌드하기"대로 실행하면 같은 무료 서명(7일)으로 설치된다.
- **Apple Developer Program(연 US$99)**
  - TestFlight로 설치 링크를 보낼 수 있다(빌드당 90일).
  - 또는 등록한 기기에 1년짜리 서명으로 설치할 수 있다.
  - 이 경우 CI에 인증서와 프로비저닝 프로파일을 Secret으로 넣어, 서명된 .ipa를 만들도록 바꾼다.

## 상세 지도 (MapLibre Native iOS)

상세 지도는 안드로이드와 같은 공유 코드(`MapLibreDetailMap`, maplibre-compose 0.12.1)로 그린다. 지도 엔진인 MapLibre Native iOS는 Gradle 의존성이 아니라 Xcode 프로젝트가 붙인다.

- `project.yml`의 `packages`: MapLibre의 공식 Swift 패키지 [maplibre-gl-native-distribution](https://github.com/maplibre/maplibre-gl-native-distribution)을 `exactVersion: 6.17.1`로 고정한다.
  - 패키지는 MapLibre 릴리스의 동적 `MapLibre.xcframework`를 가리키고, 체크섬이 그 패키지의 `Package.swift`에 적혀 있다.
  - 버전은 maplibre-compose 0.12.1이 빌드된 MapLibre iOS 버전이다(그 저장소의 `maplibreIosVersion`). maplibre-compose를 올리면 이 버전도 함께 올린다.
  - 같은 버전이 `gradle/libs.versions.toml`의 `maplibreIos`에 있고, Gradle 빌드가 두 값이 같은지 확인한다(출처 · 라이선스 목록이 이 값을 쓴다).
- CoffeeJournal 타깃의 `dependencies`에 MapLibre 제품을 링크한다.
  - Shared는 정적 프레임워크라, Kotlin 코드가 부르는 MapLibre 심볼은 앱을 링크할 때 이 프레임워크에서 풀린다.
  - Xcode가 MapLibre.framework를 앱의 `Frameworks/`에 넣는다(CI가 .ipa를 만들기 전에 확인한다).
- `Info.plist`에 더할 키는 없다. 사용자 위치를 쓰지 않으므로 위치 권한 문구도 두지 않는다. 한글 라벨은 기기의 시스템 글꼴로 그린다(MapLibre 기본값).
- Xcode가 처음 빌드할 때 GitHub에서 패키지와 xcframework(약 8.6 MB)를 받는다.
- 시뮬레이터 테스트(`:shared:iosSimulatorArm64Test`)는 Xcode가 아니라 Kotlin/Native가 링크하므로 같은 xcframework가 따로 있어야 한다.
  - `-Pcoffeejournal.maplibreFrameworkDir=<xcframework의 ios-arm64_x86_64-simulator 폴더>`로 넘긴다. 없으면 링크 전에 멈춘다.
  - CI는 패키지의 `Package.swift`에서 주소와 체크섬을 읽어 받고, 체크섬을 확인한 뒤 넘긴다.

## iOS에 아직 없는 것

- **홈 화면 위젯**: 안드로이드의 위젯(Coffee D-day, 마시는 중 원두, "+ 새 기록")은 iOS에 없다. WidgetKit 확장(Swift)과 앱·위젯이 데이터를 나누는 앱 그룹이 필요하고, 무료 Apple ID 서명으로는 앱 그룹이 제대로 되지 않을 수 있어 뒤로 미뤘다.

## AI 노트 도우미

iOS에서도 같다. 키는 Keychain에 이 기기 전용(iCloud 키체인·다른 기기로 옮기는 백업 제외)으로 저장하고, 요청은 NSURLSession으로 보낸다.

## 알림

설정 › 알림의 규칙과 문구는 안드로이드와 같다(공유 코드의 `Reminders`). 전달 방식만 다르다.

- **미리 예약**: iOS는 앱이 백그라운드에서 매일 정한 시각에 도는 것을 보장하지 않는다. 그래서 하루 점검 대신, 앞으로 보낼 알림을 미리 로컬 알림으로 예약한다.
  - 다시 계산하는 때:
    - 앱을 시작할 때와 앱으로 돌아올 때.
    - 기록·보관함·블렌드·D-day 시작일·알림 설정이 바뀔 때(저장이 몰리면 0.8초 뒤 한 번).
  - 계산: 하루 점검과 같은 함수(`ReminderData.dueOn`)로 다음 알림 시각부터 30번의 시각마다 "그날 점검이 보낼 알림"을 구한다. 각 알림은 처음 나오는 날에 한 번만 넣는다.
  - 예약: 그 날짜·시각(휴대폰의 현지 시각, 반복 없음)의 `UNCalendarNotificationTrigger`로 예약한다. 이 앱이 예약한 것(식별자 `coffee-journal.reminder.`로 시작)만 새 계획으로 바꾼다.
  - iOS는 앱마다 대기 알림을 64개까지만 둔다. 그래서 가까운 것부터 64개까지만 예약하고, 나머지는 다음 계산 때 예약한다.
- **두 번 보내지 않기**: 예약 시각이 지난 알림은 앱이 돌지 않아도 iOS가 보냈다. 다음 계산 때 안드로이드 점검과 같은 기록(`device.reminders.sent`)에 보낸 것으로 적고, 다시 예약하지 않는다.
- **바뀔 수 있는 것**
  - 피크 시작·D-day 마일스톤은 날짜가 정해져 있다. 보관함 원두의 로스팅일·피크 날짜나 D-day 시작일을 고치면 다시 계산된다.
  - 원두 소진 임박은 계산 때의 기록으로 정해진다. 남은 양은 기록한 추출의 원두량으로 계산하고(마시는 속도를 예측하지 않는다), 2잔 이하가 되면 다음 알림 시각에 예약된다.
    - 기록을 더하거나 고치거나 지우면 다시 계산되어 예약이 생기거나 옮겨지거나 취소된다.
    - 앱을 열지 않는 동안에는 기록이 그대로이므로 예약도 그대로다.
  - 앱을 30일 넘게 열지 않으면 그 뒤의 알림은 예약되어 있지 않다. 앱을 열면 다시 채운다.
- **권한**
  - 처음 켤 때 알림 허용(알림·소리·배지)을 묻는다. 거절하면 스위치는 꺼진 채로 안내가 나온다. "알림 설정 열기 →"는 설정 › 알림 › Coffee Journal을 연다.
  - 권한이 꺼져 있는 동안에는 아무것도 예약하지 않는다. 다시 허용하고 앱으로 돌아오면 예약한다.
  - 예약한 뒤 설정에서 알림을 끄고 그 시각이 지나면, 보이지 않았어도 보낸 것으로 기록된다.
- 앱을 쓰는 중에도 배너와 소리로 보인다. 알림을 누르면 앱이 열린다(안드로이드처럼 보관함·홈으로 옮겨 가지는 않는다).
- 홈 화면 위젯은 iOS에 없다.

## 참고

- `iosApp/MLNScaleBarStub.m`(빈 `MLNScaleBar` 클래스)은 Debug 빌드와 시뮬레이터 테스트에만 들어가고 Release(.ipa)에서는 빠진다. 지우면 Debug 빌드가 링크되지 않는다(이유는 파일 주석).
- `Info.plist`의 `CADisableMinimumFrameDurationOnPhone = true`는 지우지 않는다. 이 키가 없으면 Compose Multiplatform이 실행 직후 앱을 종료한다(`enforceStrictPlistSanityCheck` 기본값).
- 의존성 검증: `gradle/verification-metadata.xml`은 Linux에서 기록한다.
  - Mac에서만 받는 것(Kotlin/Native의 macOS 컴파일러 등)이 빠져 있으면, CI의 `app` 작업이 빌드 전에 멈추고 빠진 항목을 `verification` 아티팩트로 남긴다.
  - 그 항목의 출처(Maven Central·Google·JetBrains)를 확인한 뒤 커밋한다.
- 출처 · 라이선스
  - iOS 목록(`shared/src/iosMain/.../ui/about/PlatformLibraries.ios.kt`)은 `./gradlew :shared:updateThirdPartyNotices -Pcoffeejournal.iosKlibs=true`로 만든다.
  - 프레임워크에 링크되는 klib의 POM에서 만들고, POM이 없는 Kotlin/Native 런타임, skiko 안의 Skia(BSD-3-Clause), Swift 패키지로 들어오는 MapLibre Native iOS(BSD-2-Clause)를 덧붙인다.
- Kotlin 버전: Koin 4.2의 iOS 라이브러리가 Kotlin 2.3.20으로 빌드되어 있어서 Kotlin을 2.3.20으로 맞췄다. 2.2 컴파일러는 새 klib ABI를 읽지 못한다.
