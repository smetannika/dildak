# Client mod (Fabric 1.16.5, Java 8 target)

Requires JDK 17 to run Gradle (output is Java 8 bytecode) and Gradle 7.4.x.

    gradle wrapper --gradle-version 7.4.2     (once; or just open the folder in IntelliJ IDEA)
    ./gradlew build                           (Windows: gradlew.bat build)

Jar: build/libs/client-1.16.5-1.0.0.jar -> .minecraft/mods, together with Fabric API 0.42.0+1.16.

Right Shift = ClickGUI. LMB toggle, RMB settings, MMB bind key (Backspace = unbind).

## Build online (no Java needed)
Upload this folder to a GitHub repository (keep the .github folder). Open Actions -> build -> latest run -> Artifacts -> client-jar.
