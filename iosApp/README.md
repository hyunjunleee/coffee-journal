# iOS 호스트 앱 (2차 확장용 스켈레톤)

이 폴더는 공유 모듈(`shared`)을 SwiftUI에서 호스팅하는 최소 진입점만 담고 있다. Xcode 프로젝트 파일은 macOS에서 생성한다.

1. macOS에서 `gradle.properties`의 `coffeejournal.enableIos=true`로 바꾼다. (`shared/build.gradle.kts`가 `iosArm64`, `iosSimulatorArm64` 타깃과 `Shared` 정적 프레임워크를 활성화한다.)
2. `./gradlew :shared:embedAndSignAppleFrameworkForXcode`를 Xcode "Run Script" 빌드 단계로 추가하거나, `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`로 프레임워크를 만든 뒤 Xcode 프로젝트에 링크한다.
3. Xcode에서 iOS App 프로젝트를 이 폴더(`iosApp`)에 만들고 `iOSApp.swift`, `ContentView.swift`, `Info.plist`를 대상에 포함한다. Bundle identifier: `com.coffeejournal.app`.
4. 남은 iOS 작업: `shared/src/iosMain/.../ui/platform/ImagePicker.ios.kt`(PHPicker/카메라), 백업 파일 IO(`BackupFileIo.ios.kt`, UIDocumentPicker/ShareSheet) 실제 구현. 데이터베이스(Room + BundledSQLiteDriver)와 사진 저장소(`IosPhotoStore`)는 이미 iosMain에 있다.
