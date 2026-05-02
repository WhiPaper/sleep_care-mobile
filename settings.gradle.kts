pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "sleep_care-mobile"
// app은 모바일, watch는 Wear OS, watch-contracts는 양쪽 공통 프로토콜 모듈입니다.
include(":app")
include(":watch")
include(":watch-contracts")
