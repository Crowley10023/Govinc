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
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0") // Jakarta Persistence
    implementation("org.springframework.boot:spring-boot-starter") // Spring Application core dependency
    implementation("org.hibernate.orm:hibernate-core:6.4.4.Final") // Hibernate Core with MariaDB dialect
    implementation("javax.servlet:javax.servlet-api:4.0.1") // Added for servlet support
    implementation("org.apache.pdfbox:pdfbox:2.0.30") // Apache PDFBox for PDF processing
    implementation("com.github.librepdf:openpdf:1.3.30") // OpenPDF for PDF generation/processing
    implementation("com.itextpdf:itextpdf:5.5.13.3") // iText PDF library (classic open source)
    implementation("org.apache.poi:poi-ooxml:5.2.3") // Apache POI for Word/Excel processing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

}

application {
    mainClass.set("com.govinc.Theia01Application")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
