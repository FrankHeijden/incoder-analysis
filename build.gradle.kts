plugins {
    id("java")
}

group = "dev.frankheijden"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("org.apache.httpcomponents:httpclient:4.5.13")
    implementation("io.github.cdimascio:dotenv-java:2.2.4")
    implementation("net.lingala.zip4j:zip4j:2.10.0")
}
