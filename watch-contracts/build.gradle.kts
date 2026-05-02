plugins {
    id("org.jetbrains.kotlin.jvm")
}

// Android 의존성이 없는 순수 Kotlin 공통 계약 모듈입니다.
// 모바일 앱과 워치 앱이 같은 JSON 프로토콜을 공유하기 위해 분리했습니다.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}
