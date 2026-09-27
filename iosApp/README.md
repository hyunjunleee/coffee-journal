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
    - 결과 `coffee-journal-<빌드 번호>-<커밋>-unsigned.ipa`를 "iOS builds" 릴리스에 올린다.
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

## iOS에 아직 없는 것

- **상세 지도**: MapLibre Native iOS를 Xcode 프로젝트에 붙이기 전까지는 버튼이 숨겨지고 SGIS 지도만 쓴다.

AI 노트 도우미는 iOS에서도 같다. 키는 Keychain에 이 기기 전용(iCloud 키체인·다른 기기로 옮기는 백업 제외)으로 저장하고, 요청은 NSURLSession으로 보낸다.

## 참고

- `Info.plist`의 `CADisableMinimumFrameDurationOnPhone = true`는 지우지 않는다. 이 키가 없으면 Compose Multiplatform이 실행 직후 앱을 종료한다(`enforceStrictPlistSanityCheck` 기본값).
- 의존성 검증: `gradle/verification-metadata.xml`은 Linux에서 기록한다.
  - Mac에서만 받는 것(Kotlin/Native의 macOS 컴파일러 등)이 빠져 있으면, CI의 `app` 작업이 빌드 전에 멈추고 빠진 항목을 `verification` 아티팩트로 남긴다.
  - 그 항목의 출처(Maven Central·Google·JetBrains)를 확인한 뒤 커밋한다.
- 출처 · 라이선스
  - iOS 목록(`shared/src/iosMain/.../ui/about/PlatformLibraries.ios.kt`)은 `./gradlew :shared:updateThirdPartyNotices -Pcoffeejournal.iosKlibs=true`로 만든다.
  - 프레임워크에 링크되는 klib의 POM에서 만들고, POM이 없는 Kotlin/Native 런타임과 skiko 안의 Skia(BSD-3-Clause)를 덧붙인다.
- Kotlin 버전: Koin 4.2의 iOS 라이브러리가 Kotlin 2.3.20으로 빌드되어 있어서 Kotlin을 2.3.20으로 맞췄다. 2.2 컴파일러는 새 klib ABI를 읽지 못한다.
