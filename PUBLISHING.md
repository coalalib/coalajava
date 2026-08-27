# Publishing and consuming coalajava

The library is published as a single Android library artifact (AAR) from the `:app` module,
built on demand by [JitPack](https://jitpack.io) straight from this repository.

## Consuming the artifact

```groovy
// settings.gradle
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

// build.gradle
dependencies {
    implementation 'com.github.coalalib:coalajava:<version>'
}
```

`<version>` is any git ref JitPack can resolve:

- a commit hash (short or full) — the recommended pin while the repository has no releases;
- a tag, once tags are introduced;
- `<branch>-SNAPSHOT` for the moving head of a branch (not reproducible; avoid in CI).

The first request for a given ref triggers the build on JitPack's side and can take a few
minutes; after that the artifact is served from their cache. Build logs live at
`https://jitpack.io/com/github/coalalib/coalajava/<version>/build.log`.

Note on the compile classpath: the public API exposes RxJava 2 types (`Observable`, `Single`),
which the POM declares with `runtime` scope. A consumer that calls those entry points must
declare `io.reactivex.rxjava2:rxjava` itself. The Rx surface is transitional and scheduled to
disappear in favour of the `suspend`/`Flow` API.

## Local development against a consumer (composite build)

To iterate on coala without publishing anything, let the consumer substitute the artifact
with a local checkout:

```groovy
// consumer's settings.gradle
includeBuild('../coalajava') {
    dependencySubstitution {
        // The module is named :app, so the coordinate must be mapped explicitly.
        substitute module('com.github.coalalib:coalajava') using project(':app')
    }
}
```

With that in place the coordinate in the consumer's dependency list stays unchanged, and
Gradle builds coala from sources in the same invocation.

## Publishing locally

```bash
./gradlew :app:publishToMavenLocal
```

Installs `com.github.coalalib:coalajava:<versionName>` (see `defaultConfig.versionName`)
into `~/.m2/repository`, including a sources jar. JitPack runs the same task — see
`jitpack.yml` for the exact build command and JDK.

## Rules the build must keep

- **No local jars in `app/libs`.** File dependencies are silently absent from both the AAR
  and the POM, so the published artifact breaks at runtime for every consumer. Every runtime
  dependency must be a Maven coordinate.
- The publication is `singleVariant("release")`; debug-only tooling must not leak into it.
