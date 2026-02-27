plugins {
    java
    application
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.bhlangonijr:chesslib:1.3.6")
}

application {
    mainClass.set("com.example.Main")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17)) // or 21
    }
}