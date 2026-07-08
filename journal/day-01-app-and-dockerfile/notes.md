# Day 01 — Aplikasi Contoh + Dockerfile

[⬅️ Kembali ke index](../README.md) | [➡️ Day 02](../day-02-ci-basics/notes.md)

---

## ✅ Yang Dipelajari

- [x] Struktur project Spring Boot (Maven standard directory layout)
- [x] Membuat REST API sederhana dengan Spring Boot (GET, GET by id, POST)
- [x] Konsep **multi-stage Docker build**
- [x] Best practice keamanan dasar di level Dockerfile (non-root user, minimal base image, healthcheck)
- [x] Menjalankan aplikasi langsung (Maven) dan via Docker container
- [x] Push project pertama ke GitHub

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **`pom.xml`** | Daftar belanja resep | File konfigurasi Maven — dependency, versi, cara build |
| **Package Java** | Alamat rumah | Namespace untuk menghindari konflik nama class, harus cocok dengan lokasi folder |
| **POJO (Model)** | Formulir kosong | Class sederhana berisi data + getter/setter, dipakai untuk convert ke/dari JSON |
| **`@SpringBootApplication`** | Saklar utama | Menyalakan seluruh mesin Spring Boot: konfigurasi, auto-setup komponen, pencarian otomatis Controller/Model |
| **Multi-stage Docker build** | Dapur vs meja saji | Stage 1 (build tools besar) dipakai lalu dibuang; stage 2 (runtime minimal) yang jadi image final |
| **Non-root user di container** | Kunci ruangan terbatas | Kalau container di-compromise, attacker tidak otomatis dapat privilege tertinggi |

**Kenapa butuh Dockerfile setelah aplikasi sudah bisa jalan dengan `mvn spring-boot:run`?**
Supaya aplikasi bisa jalan **konsisten** di komputer manapun (laptop lain, GitHub Actions, Kubernetes) tanpa perlu install Java/Maven manual di setiap tempat — semua kebutuhan sudah "dibungkus" jadi satu image.

---

## 💻 Langkah 1 — Struktur Folder Maven

```bash
mkdir -p account-service/src/main/java/com/homelab/accountservice/controller
mkdir -p account-service/src/main/java/com/homelab/accountservice/model
mkdir -p account-service/src/main/resources
mkdir -p account-service/src/test/java/com/homelab/accountservice
```

**Insight:** Maven mengenali source code otomatis kalau folder mengikuti konvensi `src/main/java/...` — tidak perlu setting manual lokasi source.

---

## 💻 Langkah 2 — `pom.xml`

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

**Insight:** `scope=test` pada `spring-boot-starter-test` memastikan library testing **tidak ikut** ke image production — makin sedikit library yang ikut, makin kecil kemungkinan ada CVE nempel nanti pas discan Trivy.

---

## 💻 Langkah 3 — Entry Point Aplikasi

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

**Insight:** `@SpringBootApplication` otomatis men-scan class lain (Controller, Model) di package ini dan sub-package-nya — makanya `AccountController` di folder `controller/` bisa "ketemu" tanpa didaftarkan manual.

---

## 💻 Langkah 4 — Model Data

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

**Insight:** Getter/setter wajib ada dengan pola nama `getXxx()`/`setXxx()` karena library Jackson (convert Java ↔ JSON) mengenali field lewat pola nama ini, bukan lewat field `private`-nya langsung.

---

## 💻 Langkah 5 — Controller (Endpoint API)

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

**Insight:** Endpoint `POST` ini menerima input dari luar tanpa validasi apapun (nomor akun bisa kosong, saldo bisa negatif) — ini contoh kode yang "berfungsi tapi belum aman", akan jadi temuan menarik nanti waktu Semgrep (SAST) dijalankan.

---

## 💻 Langkah 6 — Konfigurasi Aplikasi

`account-service/src/main/resources/application.properties`

```properties
spring.application.name=account-service
server.port=8080

management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

**Insight:** Actuator defaultnya **menyembunyikan** hampir semua endpoint monitoring demi keamanan — harus di-`include` eksplisit, contoh nyata prinsip *defense in depth* (default tertutup, dibuka manual seperlunya).

---

## 💻 Langkah 7 — Unit Test Dasar

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

**Insight:** Method test-nya kosong, tapi tetap berguna sebagai "smoke test" — `@SpringBootTest` mencoba menyalakan seluruh aplikasi dulu; kalau ada error konfigurasi di manapun, test ini otomatis gagal.

---

## 💻 Langkah 8 — Dockerfile Multi-Stage

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

**Insight:** `COPY pom.xml` dipisah dari `COPY src` supaya Docker bisa cache layer download dependency — kalau cuma source code yang berubah, dependency tidak perlu di-download ulang setiap build.

---

## 🔬 Verifikasi

### Jalan langsung (Maven)

```bash
mvn spring-boot:run
```

```bash
curl http://localhost:8080/api/accounts
curl http://localhost:8080/actuator/health
```

### Jalan via Docker

```bash
docker build -t account-service:day1 .
docker run -p 8080:8080 account-service:day1
```

---

## 💻 Push Pertama ke GitHub

```bash
git init
git add .
git commit -m "day 1: Spring Boot account-service app + multi-stage Dockerfile"
git branch -M main
git remote add origin https://github.com/hendraazka/devsecops-homelab.git
git push -u origin main
```

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| `mvn spring-boot:run` gagal: `release version 17 not supported` | 2 JDK terinstall bersamaan (17 & 21), Maven memakai versi tidak konsisten dengan `javac` | `sudo update-alternatives --config java` dan `--config javac`, pilih versi 17 di keduanya, lalu set `JAVA_HOME` eksplisit: `echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc && source ~/.bashrc` |
| `pom.xml` gagal parse: `only whitespace content allowed before start tag` | Heredoc sebelumnya ter-paste sebagian, teks command ikut masuk jadi isi file | `rm pom.xml`, buat ulang, pastikan copy 1 blok utuh dari `cat > ...` sampai `EOF` penutup |
| `docker: command not found` di WSL | Docker Desktop belum dinyalakan / WSL Integration belum diaktifkan | Nyalakan Docker Desktop di Windows, aktifkan toggle di Settings > Resources > WSL Integration |
| Download dependency terputus di tengah `docker build` (`Premature end of Content-Length`) | Gangguan koneksi internet sesaat | Jalankan ulang `docker build` — Docker otomatis pakai cache layer yang sudah berhasil sebelumnya |

---

## 📌 Insight Penting

- Multi-stage Docker build adalah pola standar untuk image production: build tools besar dipakai lalu dibuang, cuma hasil jadi yang dibawa ke image final.
- Konfigurasi environment (versi Java, dsb) yang tidak konsisten di laptop adalah sumber bug klasik — ini salah satu alasan utama kenapa containerization penting.
- Sebagian prinsip security (non-root user, minimal base image) sudah bisa diterapkan sejak tahap Dockerfile, sebelum tools DevSecOps formal manapun dipakai — ini contoh nyata *shift-left*.

---

[⬅️ Kembali ke index](../README.md) | [➡️ Day 02](../day-02-ci-basics/notes.md)
