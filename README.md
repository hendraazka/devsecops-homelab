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

## Opsional Lanjutan (setelah Day 12)
Setelah seluruh siklus DevSecOps utama (Day 1-12) tuntas dengan
GitHub Actions + Kubernetes manifest, sebagai exercise tambahan:
- **Jenkins**: migrasikan pipeline yang sama (CI + security scan) ke Jenkins,
  untuk memahami konsep CI/CD yang sama dalam tool berbeda (banyak dipakai
  di perusahaan enterprise/legacy).
- **Ansible**: pakai untuk provisioning/konfigurasi cluster sebelum deploy
  aplikasi (melengkapi pengalaman Ansible dari homelab DevOps sebelumnya).
