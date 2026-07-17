# 🔐 DevSecOps Homelab Journey

Catatan progress belajar DevSecOps di homelab pribadi (Windows + WSL2),
sebagai lanjutan dari [DevOps Homelab Journey](https://github.com/hendraazka/devops-homelab-journey)
sekaligus persiapan karir di bidang DevSecOps.

Dimulai dari fondasi DevOps (build, CI/CD, deploy ke Kubernetes), lalu
menambahkan layer security di setiap tahap pipeline, dan dilanjutkan
dengan eksplorasi tools advanced (admission control, IaC scanning, code
quality, alternative CI/CD, provisioning otomatis, GitOps).

**Status: 12 hari roadmap utama selesai ✅ — lanjut eksplorasi Part 3**

---

## 🧰 Tech Stack

| Kategori | Tools |
|---|---|
| Bahasa & Framework | Java 17, Spring Boot 3.5.14 |
| Containerization | Docker (multi-stage build) |
| CI/CD | GitHub Actions, Jenkins |
| Orkestrasi | Kubernetes (kind) |
| Container Registry | GitHub Container Registry (ghcr.io) |
| Secret Scanning | Gitleaks |
| SAST | Semgrep, SonarQube (SonarCloud) |
| Dependency/SCA Scan | Trivy (filesystem mode) |
| Container Image Scan | Trivy (image mode) |
| K8s Manifest Scan | kube-score |
| DAST | OWASP ZAP |
| Admission Control | Pod Security Standards, OPA/Gatekeeper |
| IaC Security Scan | tfsec, Checkov |
| Provisioning | Ansible |
| GitOps | ArgoCD *(rencana Day 19)* |

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
| Day 12 | Review end-to-end Part 2 (penutup roadmap utama) | ✅ | [notes.md](./day-12-review-devsecops/notes.md) |

### Part 3 — Advanced Security & Tooling (Lanjutan)

| Hari | Topik | Status | Notes |
|---|---|---|---|
| Day 13 | Pod Security Standards + OPA/Gatekeeper | ✅ | [notes.md](./day-13-pss-opa-gatekeeper/notes.md) |
| Day 14 | IaC Security Scanning (tfsec + Checkov) | ✅ | [notes.md](./day-14-iac-security-scanning/notes.md) |
| Day 15 | SonarQube — SAST & Code Quality (pembanding Semgrep) | ✅ | [notes.md](./day-15-sonarqube-sast/notes.md) |
| Day 16 | Jenkins — migrasi pipeline & solusi Continuous Deployment | ✅ | [notes.md](./day-16-jenkins-migration/notes.md) |
| Day 17 | Ansible — provisioning otomatis tools Jenkins | ✅ | [notes.md](./day-17-ansible-provisioning/notes.md) |
| Day 18 | Semaphore — GUI ringan untuk Ansible | ✅ | [notes.md](./day-18-semaphore-gui/notes.md) |
| Day 19 | ArgoCD — GitOps, menutup celah Continuous Deployment | ✅ | [notes.md](./day-19-argocd-gitops/notes.md) |

---

## 🔒 Pipeline Keamanan Lengkap

### Alur utama (GitHub Actions)

```
push kode
   ↓
CI (6 pemeriksaan paralel):
   ├─ build-and-test        → kode benar secara fungsional
   ├─ secret-scan             → tidak ada credential bocor (Gitleaks)
   ├─ sast-scan                → tidak ada pola kode rawan (Semgrep)
   ├─ dependency-scan          → tidak ada CVE di library (Trivy fs)
   ├─ k8s-manifest-scan        → konfigurasi K8s aman (kube-score)
   ├─ iac-scan                 → konfigurasi Terraform aman (tfsec + Checkov)
   └─ sonarqube-scan           → code quality & security tambahan (SonarCloud)
   ↓ (semua harus lolos)
CD:
   ├─ build image (lokal)
   ├─ image-scan               → tidak ada CVE di image (Trivy image)
   ├─ dast-scan                → tidak ada vulnerability runtime (OWASP ZAP)
   ├─ push ke ghcr.io           (hanya kalau semua di atas lolos)
   └─ auto-update manifest     → commit tag baru [skip ci]
   ↓
kubectl apply manual (GitHub Actions tidak bisa akses cluster lokal)
   ↓
Cluster kind:
   ├─ Pod Security Standards (level restricted)  → validasi real-time saat apply
   └─ OPA/Gatekeeper (custom policy)              → validasi real-time saat apply
   ↓
aplikasi live, terverifikasi bisa diakses
```

### Alur alternatif (Jenkins lokal — Continuous Deployment sungguhan)

```
push kode ke GitHub
   ↓
Jenkins (dijalankan manual "Build Now", polling dari repo yang sama)
   ├─ Checkout
   ├─ Build & Test (Maven)
   ├─ Build Docker Image (lokal)
   ├─ kind load docker-image        → muat image ke node kind
   └─ kubectl set image + rollout   → auto-deploy LANGSUNG ke cluster
   ↓
aplikasi live, ter-deploy otomatis TANPA kubectl apply manual
```

**Insight:** dua alur ini sengaja dipertahankan berdampingan untuk perbandingan nyata — alur GitHub Actions unggul di kelengkapan security gate (7 pemeriksaan), alur Jenkins unggul di kemampuan deploy otomatis sungguhan karena berjalan di jaringan lokal yang sama dengan cluster.

---

## 📁 Struktur Repo

```
devsecops-homelab/
├── account-service/              # Kode aplikasi Spring Boot
│   └── src/, pom.xml, Dockerfile
├── k8s/                          # Manifest Kubernetes (Deployment, Service)
├── terraform/                    # Contoh IaC untuk latihan scanning (Day 14)
│   └── s3-and-security-group/main.tf
├── opa-policies/                 # ConstraintTemplate & Constraint Gatekeeper (Day 13)
│   ├── require-resource-limits-template.yaml
│   └── require-resource-limits-constraint.yaml
├── ansible/                      # Playbook provisioning (Day 17)
│   ├── inventory.ini
│   └── setup-jenkins-tools.yml
├── .github/workflows/            # Pipeline CI (ci.yml) dan CD (cd.yml)
├── Jenkinsfile                   # Pipeline alternatif via Jenkins (Day 16)
├── sonar-project.properties      # Konfigurasi SonarCloud (Day 15)
├── argocd-app.yaml               # Konfigurasi argocd (Day 19)
├── day-01 .. day-19.../          # Catatan belajar per hari
│   └── notes.md
└── JOURNEY-SUMMARY.md            # Ringkasan lengkap Day 1-4 (arsip awal)
```

**Pola tiap `notes.md`:** checklist yang dipelajari, tabel konsep kunci
(dengan analogi), step by step lengkap dengan kode, eksperimen langsung,
tabel troubleshooting nyata yang dialami, dan insight penting di akhir.
Sebagian hari (14 ke atas) juga menyertakan screenshot bukti visual di
folder `screenshots/` masing-masing.

---

## 🔗 Referensi Cepat

- Aplikasi: [`account-service/`](./account-service)
- Pipeline CI: [`.github/workflows/ci.yml`](./.github/workflows/ci.yml)
- Pipeline CD: [`.github/workflows/cd.yml`](./.github/workflows/cd.yml)
- Pipeline Jenkins: [`Jenkinsfile`](./Jenkinsfile)
- Manifest K8s: [`k8s/`](./k8s)
- Policy Gatekeeper: [`opa-policies/`](./opa-policies)
- Playbook Ansible: [`ansible/`](./ansible)
- Image: `ghcr.io/hendraazka/account-service`
- Dashboard SonarCloud: `sonarcloud.io/dashboard?id=hendraazka_devsecops-homelab`

---

## 📌 Highlight Pembelajaran

- **7 gate keamanan** aktif di CI/CD GitHub Actions: test, secret, SAST (Semgrep + SonarQube), dependency, manifest K8s, IaC, image, DAST
- **27 CVE ditemukan dan diperbaiki** lewat upgrade Spring Boot bertahap (Day 08)
- **Command injection nyata** ditemukan Semgrep dan diperbaiki dengan `ProcessBuilder` (Day 07), lalu disempurnakan lagi temuan SonarQube soal PATH variable (Day 15)
- **GitOps-lite** dibangun dari nol: CD auto-update manifest dengan `[skip ci]` untuk mencegah infinite loop (Day 10)
- **2 lapis admission control real-time** — Pod Security Standards dan OPA/Gatekeeper dengan custom policy (Day 13)
- **12 misconfiguration IaC** ditemukan dan diperbaiki di contoh Terraform, termasuk investigasi bug exit-code di GitHub Action wrapper (Day 14)
- **Continuous Deployment sungguhan** akhirnya terbukti bekerja lewat Jenkins lokal — 6 percobaan, 5 masalah nyata terselesaikan (Day 16)
- **Idempotency Ansible dibuktikan langsung**: `changed=0` saat kondisi normal, `changed=1` tepat saat 1 tool sengaja dirusak (Day 17)
- **Debugging production-like** yang dialami berulang kali: `CrashLoopBackOff`, CPU throttling vs probe timing, node `NotReady`, divergent branch, GID permission mismatch, IP/port cluster yang berubah setiap restart
- **GitOps dan Progressive Delivery diwujudkan via Argo CD: Migrasi penuh dari metode push-based ke pull-based (Argo CD), mengotomatisasi rekonsiliasi state cluster dengan Git, menerapkan strategi Application-of-Applications untuk multi-microservices.
---

## 🔧 Rencana Lanjutan

- **Day 18 — Semaphore**: GUI ringan untuk mengelola playbook Ansible, alternatif yang lebih realistis untuk skala homelab dibanding AWX (yang butuh cluster Kubernetes tersendiri dan resource besar)
- **Day 19 — ArgoCD (GitOps)**: instal di dalam cluster kind, cluster yang aktif "menjemput" (pull) perubahan dari Git — menutup celah Continuous Deployment yang di Day 16 masih butuh trigger manual "Build Now"
