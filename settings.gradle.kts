pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
// 1. Cambiamos el nombre global del proyecto
rootProject.name = "CineParche"

// 2. Definimos los nuevos nombres para tus módulos
include(":CineParcheApp")
include(":CineParcheUrl")

// 3. Mapeamos estos nombres a las carpetas físicas actuales
project(":CineParcheApp").projectDir = file("app")
project(":CineParcheUrl").projectDir = file("app/tvfullurl")