plugins {
    java
}

group = "com.supremosan"
version = "0.1.0"

// Use an installed official server binary. No shared-source build or modification is needed.
val installedServer = providers.gradleProperty("hytaleServerJar")
    .orElse(providers.environmentVariable("HYTALE_SERVER_JAR"))
    .orElse(providers.environmentVariable("APPDATA").map {
        "$it/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar"
    })
val engineFiles = files(installedServer)

allprojects {
    apply(plugin = "java")
    group = "com.supremosan"
    version = "0.1.0"
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }
    dependencies {
        add("compileOnly", engineFiles)
        add("testCompileOnly", engineFiles)
    }
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests = false }
}

project(":example-extension") {
    dependencies { add("compileOnly", project(":")) }
    tasks.named<Jar>("jar") { archiveBaseName.set("CustomInventoryExample") }
}

val verifyInventory = tasks.register<JavaExec>("verifyInventory") {
    group = "verification"
    description = "Checks registry lifecycle, event routing, inventory validation and display projection."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath + configurations.compileClasspath.get()
    mainClass.set("com.supremosan.custominventory.InventoryVerification")
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
tasks.check { dependsOn(verifyInventory) }
