val appVersion = file("../version.txt").readText().trim()
version = appVersion

plugins {
    id("org.springframework.boot") version "3.2.6"
    id("io.spring.dependency-management") version "1.1.4"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") // JPA & persistence
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3") // MariaDB driver    
    runtimeOnly("com.h2database:h2") // H2 Database for development/testing
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0") // Jakarta Persistence
    implementation("org.springframework.boot:spring-boot-starter") // Spring Application core dependency
    implementation("org.hibernate.orm:hibernate-core:6.4.4.Final") // Hibernate Core with MariaDB dialect
    //implementation("javax.servlet:javax.servlet-api:4.0.1") // Added for servlet support
    implementation("org.apache.pdfbox:pdfbox:2.0.30") // Apache PDFBox for PDF processing
    implementation("com.github.librepdf:openpdf:1.3.30") // OpenPDF for PDF generation/processing
    implementation("com.itextpdf:itextpdf:5.5.13.3") // iText PDF library (classic open source)
    implementation("org.apache.poi:poi-ooxml:5.2.3") // Apache POI for Word/Excel processing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    testImplementation("org.springframework.security:spring-security-test")
    implementation("org.json:json:20240303") // For JSON processing in OpenAIUtil
    implementation("org.webjars:jquery:3.6.0") // jQuery as WebJars dependency
    // Provide Chart.js as a WebJar so installations without external CDN access still work
    implementation("org.webjars.npm:chart.js:3.9.1")

    // docx4j for manipulating Word documents (used by AssessmentReporterWord)
    implementation("org.docx4j:docx4j-JAXB-ReferenceImpl:11.5.9")
    // JAXB runtime required by docx4j on Jakarta platform
    //implementation("org.glassfish.jaxb:jaxb-runtime:4.0.2")
    //implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.0")

    // JFreeChart for generating charts to embed in reports
    implementation("org.jfree:jfreechart:1.5.4")

    // JavaMail support for email sending
    implementation("org.springframework.boot:spring-boot-starter-mail")
}

application {
    mainClass.set("com.govinc.Theia01Application")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-XX:+EnableDynamicAgentLoading", "-Xshare:off")
    testLogging {
        showStandardStreams = true
        showExceptions = false
        showCauses = false
        showStackTraces = false
        events("passed", "skipped", "failed")
    }
}

tasks.named<ProcessResources>("processResources") {

    inputs.file("../version.txt")

    filter<org.apache.tools.ant.filters.ReplaceTokens>(
        mapOf("tokens" to mapOf("app.version" to appVersion))
    )
}