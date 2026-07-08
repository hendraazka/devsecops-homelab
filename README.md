# DevSecOps Homelab Journey

Belajar DevSecOps step by step di homelab pribadi, dimulai dari fondasi
DevOps (build, CI/CD, deploy ke Kubernetes) lalu menambahkan layer security
di setiap tahap pipeline (SAST, secret scanning, image scanning, DAST, dll).

Stack: Java (Spring Boot) - Docker - GitHub Actions - Kubernetes (kind)

## Progress

| Hari | Topik | Status |
|---|---|---|
| [Day 01](./day-01-app-and-dockerfile) | Aplikasi contoh + Dockerfile | ✅ |
| [Day 02](./day-02-ci-basics) | CI dasar dengan GitHub Actions | ✅ |

## Struktur Repo
- `account-service/` - kode aplikasi yang terus dikembangkan tiap hari
- `day-XX-.../` - catatan/log belajar tiap hari
