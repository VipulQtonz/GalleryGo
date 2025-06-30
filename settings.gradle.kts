pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://jcenter.bintray.com")
        maven("https://maven.aliyun.com/repository/jcenter")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://jcenter.bintray.com")
        maven("https://maven.aliyun.com/repository/jcenter")
    }
}

rootProject.name = "PhotoGallery"
include(":app")