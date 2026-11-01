# Resistance Icon (Fabric 1.21.11)

Shows the Resistance effect icon above any player who has the effect.

## Build
1. Install JDK 21.
2. Generate a Gradle wrapper (`gradle wrapper`) or copy `gradlew`, `gradlew.bat`, and `gradle/` from a project made at https://fabricmc.net/develop
3. Verify the versions in `gradle.properties` match what that site lists for 1.21.11.
4. Run `./gradlew build` -> jar is in `build/libs/`.
5. Drop the jar (plus Fabric API) in your mods folder. Client-side only.

## Build without installing anything (GitHub)
1. Create a free GitHub account and a new repository.
2. Upload all files from this folder (including the hidden `.github` folder).
3. Open the repo's Actions tab, open the latest "build" run, and download the `resistance-icon` artifact.
4. Unzip it and use the jar that does NOT end in `-sources.jar`.
