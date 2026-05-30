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
    }
}

rootProject.name = "SlowClock"
include(":app")
include(":core:model")
include(":core:common")
include(":core:ui")
include(":core:alarm")
include(":core:data")
include(":feature:main")
include(":feature:addschedule")
include(":feature:recommendation")
include(":feature:information")
include(":feature:profile")
include(":feature:familygroup")
