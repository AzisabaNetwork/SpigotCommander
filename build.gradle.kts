plugins {
    java
}

group = "net.azisaba"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.15.2-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains:annotations:24.0.1")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        from(sourceSets.main.get().resources.srcDirs) {
            filter(
                org.apache.tools.ant.filters.ReplaceTokens::class,
                mapOf("tokens" to mapOf("version" to project.version.toString()))
            )
            filteringCharset = "UTF-8"
        }
    }

    compileJava {
        options.encoding = "UTF-8"
    }
}
