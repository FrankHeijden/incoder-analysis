plugins {
    id("java")
    id("net.kyori.blossom") version "1.3.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "dev.frankheijden"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.6.3")
    annotationProcessor("info.picocli:picocli-codegen:4.6.3")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("io.github.cdimascio:dotenv-java:2.2.4")
    implementation("net.lingala.zip4j:zip4j:2.10.0")
}

tasks {
    blossom {
        replaceToken("%%version%%", version, "src/main/java/dev/frankheijden/incoderanalysis/DataSetDownloader.java")
    }

    jar {
        manifest {
            attributes["Main-Class"] = "dev.frankheijden.incoderanalysis.DataSetDownloader"
        }
    }
}


