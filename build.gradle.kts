plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.roudraneel"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.1.8"

dependencies {
    // Spring AI MCP server on Spring WebMVC: supports STREAMABLE (Streamable HTTP), SSE and STDIO
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.ai:spring-ai-starter-mcp-client")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
