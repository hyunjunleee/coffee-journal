# iOS 호스트 앱 (2차 확장용 스켈레톤)

이 폴더는 공유 모듈(`shared`)을 SwiftUI에서 호스팅하는 최소 진입점만 담고 있다. Xcode 프로젝트 파일은 macOS에서 생성한다.

1. macOS에서 `gradle.properties`의 `coffeejournal.enableIos=true`로 바꾼다. (`shared/build.gradle.kts`가 `iosArm64`, `iosSimulatorArm64` 타깃과 `Shared` 정적 프레임워크를 활성화한다.)
2. `./gradlew :shared:embedAndSignAppleFrameworkForXcode`를 Xcode "Run Script" 빌드 단계로 추가하거나, `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`로 프레임워크를 만든 뒤 Xcode 프로젝트에 링크한다.
3. Xcode에서 iOS App 프로젝트를 이 폴더(`iosApp`)에 만들고 `iOSApp.swift`, `ContentView.swift`, `Info.plist`를 대상에 포함한다. Bundle identifier: `com.coffeejournal.app`.
4. iosMain에는 commonMain의 모든 `expect`에 대한 `actual`이 있다: 사진 선택(`PHPickerViewController`, 이미지만), 카메라(`UIImagePickerController`, 카메라가 없으면 버튼 숨김), 링크 열기(`openURL(_:options:completionHandler:)`), 백업 저장·열기(`UIDocumentPickerViewController`, JSON), 텍스트 공유(`UIActivityViewController`). 데이터베이스(Room + BundledSQLiteDriver)와 사진 저장소(`IosPhotoStore`)도 iosMain에 있다. 이 코드는 Linux CI에서 컴파일되지 않으므로 macOS에서 처음 빌드할 때 확인한다.
5. `Info.plist`의 `CADisableMinimumFrameDurationOnPhone = true`는 지우지 않는다. Compose Multiplatform은 이 키가 없으면 실행 직후 앱을 종료한다(`enforceStrictPlistSanityCheck` 기본값).
6. 의존성 검증: `gradle/verification-metadata.xml`에는 Linux에서 받은 Android·JVM 산출물만 기록되어 있다. macOS에서 iOS 타깃을 처음 빌드할 때 `./gradlew --write-verification-metadata sha256 :shared:linkDebugFrameworkIosSimulatorArm64`로 iOS용 Kotlin/Native 산출물의 체크섬을 추가하고, 추가된 항목의 출처를 확인한 뒤 커밋한다.
7. 출처 · 라이선스 화면: iOS의 라이브러리 목록(`iosMain/.../ui/about/PlatformLibraries.ios.kt`)은 아직 비어 있다. iOS 앱은 Skia(skiko, BSD-3-Clause) 등 Android와 다른 라이브러리를 포함하므로, iOS 프레임워크의 의존성으로 목록을 만들어 넣은 뒤 배포한다.
