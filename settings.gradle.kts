pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()

        // TODO: Trying to include libs directory
        //flatDir {
        //    dirs("libs")
        //}
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

rootProject.name = "SeekThermalGodotAndroidPlugin"
include(":plugin")
include(":seek-thermal")
include(":seek-thermal")
