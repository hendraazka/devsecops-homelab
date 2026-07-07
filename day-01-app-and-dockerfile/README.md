# Day 01 - Aplikasi Contoh + Dockerfile

## Yang dipelajari
- Struktur project Spring Boot (Maven standard directory layout)
- Membuat REST API sederhana (GET, GET by id, POST) dengan Spring Boot
- Konsep multi-stage Docker build: kenapa build stage dan runtime stage dipisah
- Best practice keamanan dasar di level Dockerfile:
  - base image minimal (alpine)
  - non-root user (USER appuser)
  - HEALTHCHECK memanfaatkan endpoint actuator

## Yang dikerjakan
- Membuat `account-service`: REST API "Account Balance Service"
  - `GET /api/accounts` - lihat semua akun
  - `GET /api/accounts/{accountNumber}` - lihat satu akun
  - `POST /api/accounts` - buat akun baru
  - `GET /actuator/health` - health check
- Menjalankan aplikasi langsung (mvn spring-boot:run) - berhasil
- Membuat Dockerfile multi-stage
- Build & run aplikasi sebagai Docker container - berhasil

## Kendala yang ditemui & solusi
- Konflik versi Java (JDK 17 vs 21 terinstall bersamaan) menyebabkan
  `mvn spring-boot:run` gagal build. Diselesaikan dengan
  `update-alternatives --config java/javac` + set `JAVA_HOME` eksplisit.
- Docker belum terhubung ke WSL2 karena Docker Desktop belum dinyalakan
  dan WSL Integration belum aktif.

## Kode
Lihat folder [`account-service/`](../account-service)
