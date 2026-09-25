# Coffee Journal

개인 커피 저널 웹앱(`coffee-journal-empty-template`)을 분석하고, 같은 기능을 갖는 모바일 앱을 Kotlin Multiplatform + Compose Multiplatform으로 구현한 저장소입니다. 1차는 Android, 공유 모듈은 iOS 확장을 염두에 두고 설계했습니다.

## 문서
- `docs/coffee-journal-site-analysis.md` — 웹 원본 전수 분석(구조·기능·데이터·참조 데이터·버그)
- `docs/android-app-design.md` — 앱 설계서(스택 결정, 모듈, DB 스키마, 화면, 디자인 토큰, 규칙, 마일스톤)
- `docs/dev/implementation-notes.md` — 구현 규약(빌드, 패키지 소유, API, 디자인)
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
./gradlew :shared:testDebugUnitTest            # 도메인 규칙·백업 코덱 등 단위 테스트
./gradlew :androidApp:recordRoborazziDebug     # 실제 화면을 JVM에서 렌더한 PNG → androidApp/screenshots/
```
요구 사항: JDK 17+, Android SDK Platform 36 / Build-Tools 35. Gradle 래퍼(8.14.3) 포함.

## 데이터
모든 데이터는 기기 로컬 SQLite(Room)와 앱 전용 사진 폴더에 저장됩니다. 서버·계정·AI 호출은 없습니다. 웹 백업 JSON과 상호 호환되는 백업/복원을 제공합니다.
