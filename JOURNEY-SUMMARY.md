# Journey Summary — Day 01 s/d Day 04 (DevOps Foundation)

Dokumentasi lengkap: setiap langkah yang dikerjakan, kode yang dipakai
persis di langkah itu, konsep yang dipelajari, serta kendala nyata yang
ditemui beserta solusinya.

---

## Day 01 — Aplikasi Contoh + Dockerfile

### Langkah 1: Struktur folder Maven
```bash
mkdir -p account-service/src/main/java/com/homelab/accountservice/controller
mkdir -p account-service/src/main/java/com/homelab/accountservice/model
mkdir -p account-service/src/main/resources
mkdir -p account-service/src/test/java/com/homelab/accountservice
```
**Konsep**: Maven punya konvensi struktur folder baku ("standard directory
layout") supaya otomatis tahu lokasi source code tanpa konfigurasi manual.

### Langkah 2: `pom.xml` — daftar dependency & konfigurasi build
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.0</version>
        <relativePath/>
    </parent>
    <groupId>com.homelab</groupId>
    <artifactId>account-service</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>account-service</name>
    <description>Simple Account Balance Service - DevSecOps Homelab Demo</description>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```
**Konsep**: `spring-boot-starter-web` bikin aplikasi bisa jadi REST API,
`actuator` kasih endpoint health check, `test` khusus untuk unit test
(`scope=test` = tidak ikut ke image production).

### Langkah 3: Entry point aplikasi
`account-service/src/main/java/com/homelab/accountservice/AccountServiceApplication.java`
```java
package com.homelab.accountservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
```
**Konsep**: `@SpringBootApplication` = gabungan `@Configuration` +
`@EnableAutoConfiguration` + `@ComponentScan` — otomatis scan class lain
(Controller, dll) di package ini dan sub-packagenya.

### Langkah 4: Model data
`account-service/src/main/java/com/homelab/accountservice/model/Account.java`
```java
package com.homelab.accountservice.model;

public class Account {
    private String accountNumber;
    private String ownerName;
    private double balance;

    public Account() {
    }

    public Account(String accountNumber, String ownerName, double balance) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = balance;
    }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
```
**Konsep**: POJO dengan getter/setter — dibutuhkan library Jackson untuk
convert object Java ke JSON dan sebaliknya.

### Langkah 5: Controller (endpoint API)
`account-service/src/main/java/com/homelab/accountservice/controller/AccountController.java`
```java
package com.homelab.accountservice.controller;

import com.homelab.accountservice.model.Account;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final Map<String, Account> accounts = new HashMap<>();

    public AccountController() {
        accounts.put("1001", new Account("1001", "Budi Santoso", 5000000));
        accounts.put("1002", new Account("1002", "Siti Aminah", 12500000));
    }

    @GetMapping("/{accountNumber}")
    public Account getAccount(@PathVariable String accountNumber) {
        return accounts.get(accountNumber);
    }

    @GetMapping
    public Map<String, Account> getAllAccounts() {
        return accounts;
    }

    @PostMapping
    public Account createAccount(@RequestBody Account account) {
        accounts.put(account.getAccountNumber(), account);
        return account;
    }
}
```
**Konsep**: `@RestController` + `@RequestMapping("/api/accounts")` =
semua endpoint di class ini diawali path itu dan hasil return otomatis
jadi JSON.

### Langkah 6: Konfigurasi aplikasi
`account-service/src/main/resources/application.properties`
```properties
spring.application.name=account-service
server.port=8080

management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```
**Konsep**: Actuator defaultnya menyembunyikan hampir semua endpoint demi
keamanan — harus di-`include` eksplisit yang boleh diakses.

### Langkah 7: Unit test dasar
`account-service/src/test/java/com/homelab/accountservice/AccountServiceApplicationTests.java`
```java
package com.homelab.accountservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AccountServiceApplicationTests {
    @Test
    void contextLoads() {
    }
}
```
**Konsep**: "Smoke test" — method kosong, tapi `@SpringBootTest` mencoba
menyalakan seluruh aplikasi dulu; kalau ada error konfigurasi di manapun,
test ini otomatis gagal.

### Langkah 8: Dockerfile multi-stage
`account-service/Dockerfile`
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD wget -q --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```
**Konsep**: Stage 1 (build tools besar) dibuang, stage 2 (runtime, cuma
JRE minimal) yang jadi image final. Non-root user (`appuser`) membatasi
kerusakan kalau container di-compromise.

### Langkah 9: Verifikasi jalan langsung
```bash
mvn spring-boot:run
```
Test di terminal lain:
```bash
curl http://localhost:8080/api/accounts
curl http://localhost:8080/actuator/health
```

### Langkah 10: Verifikasi jalan via Docker
```bash
docker build -t account-service:day1 .
docker run -p 8080:8080 account-service:day1
```

### Langkah 11: Push pertama ke GitHub
```bash
git init
git add .
git commit -m "day 1: Spring Boot account-service app + multi-stage Dockerfile"
git branch -M main
git remote add origin https://github.com/hendraazka/devsecops-homelab.git
git push -u origin main
```

### Kendala & Solusi Day 01
| Masalah | Penyebab | Solusi |
|---|---|---|
| `mvn spring-boot:run` gagal: "release version 17 not supported" | 2 JDK terinstall (17 & 21), Maven pakai versi tidak konsisten | `sudo update-alternatives --config java` & `--config javac`, pilih 17, lalu `echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc && source ~/.bashrc` |
| `pom.xml` gagal parse: "only whitespace content allowed before start tag" | Heredoc sebelumnya ter-paste sebagian, isi command ikut masuk ke file | `rm pom.xml`, buat ulang, pastikan copy 1 blok utuh dari `cat > ...` sampai `EOF` |
| `docker: command not found` di WSL | Docker Desktop belum dinyalakan / WSL Integration belum aktif | Nyalakan Docker Desktop di Windows, aktifkan toggle WSL Integration di Settings > Resources |
| Download dependency di dalam `docker build` terputus ("Premature end of Content-Length") | Gangguan koneksi internet sesaat | Build ulang, Docker otomatis pakai cache layer yang sudah berhasil |
