plugins {
    java
}

group = "me.ehsan"
version = "1.1-beta"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("net.kyori:adventure-text-minimessage:4.14.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}