pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://artifact.bytedance.com/repository/AwemeOpenSDK") }
    }
}

rootProject.name = "DouyinDownloader"
include(":app")
