pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Cloud sessions only: Maven Central rate-limits (429) the ~160 MB Robolectric graphics jar, so
        // scripts/fetch-robolectric-runtime.sh downloads it with curl into ~/.m2. When it's there, take
        // that one module from ~/.m2. CI doesn't have the file and downloads it normally.
        val nativeRuntime = "org/robolectric/nativeruntime-dist-compat"
        if (File(System.getProperty("user.home"), ".m2/repository/$nativeRuntime").isDirectory) {
            exclusiveContent {
                forRepository { mavenLocal { metadataSources { artifact() } } }
                filter { includeModule("org.robolectric", "nativeruntime-dist-compat") }
            }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "RiftTracker"
include(":app")
