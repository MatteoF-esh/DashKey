// Fichier : settings.gradle.kts

pluginManagement {
    repositories {
        google()
        mavenCentral()
        // AJOUTER CETTE LIGNE pour trouver des plugins comme KSP
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Test message simple"
include(":app")
