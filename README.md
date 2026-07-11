# 🔐 DevSecOps Homelab Journey

Catatan progress belajar DevSecOps di homelab pribadi (Windows + WSL2),
sebagai lanjutan dari [DevOps Homelab Journey](https://github.com/hendraazka/devops-homelab-journey)
sekaligus persiapan karir di bidang DevSecOps.

Dimulai dari fondasi DevOps (build, CI/CD, deploy ke Kubernetes), lalu
menambahkan layer security di setiap tahap pipeline: secret scanning,
SAST, dependency scanning, container image scanning, Kubernetes manifest
scanning, sampai DAST.

**Status: 12 hari roadmap utama selesai ✅**

---

## 🧰 Tech Stack

| Kategori | Tools |
|---|---|
| Bahasa & Framework | Java 17, Spring Boot 3.5.14 |
| Containerization | Docker (multi-stage build) |
| CI/CD | GitHub Actions |
| Orkestrasi | Kubernetes (kind) |
| Container Registry | GitHub Container Registry (ghcr.io) |
| Secret Scanning | Gitleaks |
| SAST | Semgrep |
| Dependency/SCA Scan | Trivy (filesystem mode) |
| Container Image Scan | Trivy (image mode) |
| K8s Manifest Scan | kube-score |
| DAST | OWASP ZAP |

---

## 📅 Progress

### Part 1 — DevOps Foundation

| Hari | Topik | Status | Notes |
|---|---|---|---|
| Day 01 | Aplikasi contoh (Spring Boot) + Dockerfile multi-stage | ✅ | [notes.md](./day-01-app-and-dockerfile/notes.md) |
| Day 02 | CI dasar dengan GitHub Actions | ✅ | [notes.md](./day-02-ci-basics/notes.md) |
| Day 03 | CD — Build & push image ke ghcr.io | ✅ | [notes.md](./day-03-cd-build-push-image/notes.md) |
| Day 04 | Manifest Kubernetes + deploy ke kind cluster | ✅ | [notes.md](./day-04-kubernetes-deploy/notes.md) |
| Day 05 | Review end-to-end Part 1 | ✅ | [notes.md](./day-05-review-end-to-end/notes.md) |

### Part 2 — DevSecOps (Security Layer)

| Hari | Tool | Status | Notes |
|---|---|---|---|
| Day 06 | Gitleaks — Secret scanning di CI | ✅ | [notes.md](./day-06-gitleaks-secret-scanning/notes.md) |
| Day 07 | Semgrep — SAST | ✅ | [notes.md](./day-07-semgrep-sast/notes.md) |
| Day 08 | Trivy (fs) — Dependency/SCA scan | ✅ | [notes.md](./day-08-trivy-dependency-scan/notes.md) |
| Day 09 | Trivy (image) — Container image scan | ✅ | [notes.md](./day-09-trivy-image-scan/notes.md) |
| Day 10 | kube-score — Kubernetes manifest scan | ✅ | [notes.md](./day-10-kube-score-manifest-scan/notes.md) |
| Day 11 | OWASP ZAP — DAST | ✅ | [notes.md](./day-11-owasp-zap-dast/notes.md) |
| Day 12 | Review end-to-end Part 2 (penutup) | ✅ | [notes.md](./day-12-review-devsecops/notes.md) |


### Part 3 — DevSecOps (Advanced Security & Tooling)

| Hari | Tool | Status | Notes |
|---|---|---|---|
| Day 13 | Pod Security Standards + OPA/Gatekeeper |  | [notes.md] |
| Day 14 | IaC Security Scanning (tfsec/Checkov)  |  | [notes.md] |
| Day 15 | SonarQube — SAST & Code Quality (pembanding Semgrep)   |  | [notes.md] |
| Day 16 | Jenkins — migrasi pipeline dari GitHub Actions   |  | [notes.md] |
| Day 17 | Ansible — provisioning cluster & tools otomatis  |  | [notes.md] |
| Day 18 | ArgoCD — GitOps penuh, menutup celah Continuous Deployment   |  | [notes.md] |
---

## 🔒 Pipeline Keamanan Lengkap

```
push kode
   ↓
CI (5 pemeriksaan paralel):
   ├─ build-and-test      → kode benar secara fungsional
   ├─ secret-scan          → tidak ada credential bocor (Gitleaks)
   ├─ sast-scan            → tidak ada pola kode rawan (Semgrep)
   ├─ dependency-scan      → tidak ada CVE di library (Trivy fs)
   └─ k8s-manifest-scan    → konfigurasi K8s aman (kube-score)
   ↓ (semua harus lolos)
CD:
   ├─ build image (lokal)
   ├─ image-scan           → tidak ada CVE di image (Trivy image)
   ├─ dast-scan            → tidak ada vulnerability runtime (OWASP ZAP)
   ├─ push ke ghcr.io       (hanya kalau semua di atas lolos)
   └─ auto-update manifest → commit tag baru [skip ci]
   ↓
kubectl apply manual
   ↓
aplikasi live di cluster kind, terverifikasi bisa diakses
```

---

## 📁 Struktur Repo

```
devsecops-homelab/
├── account-service/          # Kode aplikasi Spring Boot
├── k8s/                       # Manifest Kubernetes (Deployment, Service)
├── .github/workflows/         # Pipeline CI (ci.yml) dan CD (cd.yml)
├── day-01-app-and-dockerfile/
│   └── notes.md                # Catatan belajar per hari
├── day-02-ci-basics/
├── day-03-cd-build-push-image/
├── day-04-kubernetes-deploy/
├── day-05-review-end-to-end/
├── day-06-gitleaks-secret-scanning/
├── day-07-semgrep-sast/
├── day-08-trivy-dependency-scan/
├── day-09-trivy-image-scan/
├── day-10-kube-score-manifest-scan/
├── day-11-owasp-zap-dast/
├── day-12-review-devsecops/
└── JOURNEY-SUMMARY.md         # Ringkasan lengkap Day 1-4 (arsip awal)
```

**Pola tiap `notes.md`:** checklist yang dipelajari, tabel konsep kunci
(dengan analogi), step by step lengkap dengan kode, eksperimen langsung,
tabel troubleshooting nyata yang dialami, dan insight penting di akhir.

---

## 🔗 Referensi Cepat

- Aplikasi: [`account-service/`](./account-service)
- Pipeline CI: [`.github/workflows/ci.yml`](./.github/workflows/ci.yml)
- Pipeline CD: [`.github/workflows/cd.yml`](./.github/workflows/cd.yml)
- Manifest K8s: [`k8s/`](./k8s)
- Image: `ghcr.io/hendraazka/account-service`

---

## 📌 Highlight Pembelajaran

- **7 gate keamanan** aktif di pipeline: 5 di CI (test, secret, SAST, dependency, manifest) + 2 di CD (image scan, DAST)
- **27 CVE ditemukan dan diperbaiki** lewat upgrade Spring Boot bertahap (Day 08)
- **Command injection nyata** ditemukan Semgrep dan diperbaiki dengan `ProcessBuilder` (Day 07)
- **GitOps-lite** dibangun dari nol: CD auto-update manifest dengan `[skip ci]` untuk mencegah infinite loop (Day 10)
- **Debugging production-like**: `CrashLoopBackOff`, CPU throttling vs probe timing, node `NotReady`, divergent branch — semua dialami dan diselesaikan secara sistematis

---

