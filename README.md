# 🔐 DevSecOps Homelab Journey

Catatan progress belajar DevSecOps di homelab pribadi (Windows + WSL2),
sebagai lanjutan dari [DevOps Homelab Journey](https://github.com/hendraazka/devops-homelab-journey)
sekaligus persiapan karir di bidang DevSecOps.

Dimulai dari fondasi DevOps (build, CI/CD, deploy ke Kubernetes), lalu
menambahkan layer security di setiap tahap pipeline: secret scanning,
SAST, dependency scanning, container image scanning, Kubernetes manifest
scanning, sampai DAST.

---

## 🧰 Tech Stack

| Kategori | Tools |
|---|---|
| Bahasa & Framework | Java 17, Spring Boot |
| Containerization | Docker (multi-stage build) |
| CI/CD | GitHub Actions |
| Orkestrasi | Kubernetes (kind) |
| Container Registry | GitHub Container Registry (ghcr.io) |
| Security (menyusul) | Gitleaks, Semgrep, Trivy, kube-score, OWASP ZAP |

---

## 📅 Progress

### Part 1 — DevOps Foundation

| Hari | Topik | Status | Notes |
|---|---|---|---|
| Day 01 | Aplikasi contoh (Spring Boot) + Dockerfile multi-stage | ✅ | [notes.md](./day-01-app-and-dockerfile/notes.md) |
| Day 02 | CI dasar dengan GitHub Actions | ✅ | [notes.md](./day-02-ci-basics/notes.md) |
| Day 03 | CD — Build & push image ke ghcr.io | ✅ | [notes.md](./day-03-cd-build-push-image/notes.md) |
| Day 04 | Manifest Kubernetes + deploy ke kind cluster | ✅ | [notes.md](./day-04-kubernetes-deploy/notes.md) |
| Day 05 | Review end-to-end Part 1 | ⬜ | - |

### Part 2 — DevSecOps (Security Layer)

| Hari | Topik | Status | Notes |
|---|---|---|---|
| Day 06 | Gitleaks — Secret scanning di CI | ⬜ | - |
| Day 07 | Semgrep — SAST | ⬜ | - |
| Day 08 | Trivy (filesystem mode) — Dependency/SCA scan | ⬜ | - |
| Day 09 | Trivy (image mode) — Container image scan | ⬜ | - |
| Day 10 | kube-score/kubesec — Kubernetes manifest scan | ⬜ | - |
| Day 11 | OWASP ZAP — DAST | ⬜ | - |
| Day 12 | Review end-to-end Part 2 | ⬜ | - |

---

## 📁 Struktur Repo

```
devsecops-homelab/
├── account-service/          # Kode aplikasi Spring Boot (terus berkembang tiap hari)
├── k8s/                      # Manifest Kubernetes (Deployment, Service)
├── .github/workflows/        # Pipeline CI (ci.yml) dan CD (cd.yml)
├── day-01-app-and-dockerfile/
│   └── notes.md              # Catatan belajar per hari (step by step + konsep + troubleshooting)
├── day-02-ci-basics/
│   └── notes.md
├── day-03-cd-build-push-image/
│   └── notes.md
├── day-04-kubernetes-deploy/
│   └── notes.md
└── JOURNEY-SUMMARY.md        # Ringkasan lengkap seluruh journey (semua hari jadi 1 file)
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

## 🔧 Opsional Lanjutan (setelah Day 12)

Setelah seluruh siklus DevSecOps utama (Day 1-12) tuntas dengan
GitHub Actions + Kubernetes manifest, sebagai exercise tambahan:

- **Jenkins** — migrasikan pipeline yang sama (CI + security scan) untuk
  memahami konsep CI/CD yang sama dalam tool berbeda (banyak dipakai di
  perusahaan enterprise/legacy)
- **Ansible** — provisioning/konfigurasi cluster sebelum deploy aplikasi
  (melengkapi pengalaman Ansible dari [DevOps Homelab Journey](https://github.com/hendraazka/devops-homelab-journey))
- **ArgoCD / Flux (GitOps)** — otomatisasi penuh dari "image baru di registry" sampai "ter-deploy ke cluster" tanpa `kubectl rollout restart` manual, melengkapi celah yang ditemukan di Day 05 (CD saat ini masih berhenti di *Continuous Delivery*, belum *Continuous Deployment*)
