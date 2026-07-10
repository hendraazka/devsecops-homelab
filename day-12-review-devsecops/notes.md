# Day 12 — Review End-to-End Part 2 (Penutup Roadmap DevSecOps)

[⬅️ Day 11](../day-11-owasp-zap-dast/notes.md) | [⬅️ Kembali ke index](../README.md)

---

## ✅ Yang Dipelajari

- [x] Verifikasi seluruh pipeline 5-lapis security bekerja sebagai satu kesatuan
- [x] `git config pull.rebase` — menyelesaikan divergent branch antara commit lokal dan commit otomatis dari CD (bot)
- [x] Menutup celah validasi input yang sudah disinggung sejak Day 01 dan Day 07
- [x] Pembuktian penutup: kode → CI (5 gate) → CD (2 gate tambahan) → deploy → validasi berfungsi di runtime

---

## 🧠 Gambaran Besar — Pipeline Lengkap yang Sudah Dibangun

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

## 💻 Eksperimen Penutup — Menutup Celah Validasi Input

Sejak Day 01, endpoint `POST /api/accounts` sengaja dibiarkan tanpa validasi apapun sebagai bahan pembelajaran (dan sempat dikonfirmasi di Day 07 bahwa SAST generik tidak menangkap masalah ini karena levelnya *business logic*, bukan pola teknis).

`AccountController.java`:
```java
@PostMapping
public ResponseEntity<?> createAccount(@RequestBody Account account) {
    if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
        return ResponseEntity.badRequest().body("accountNumber tidak boleh kosong");
    }
    if (account.getBalance() < 0) {
        return ResponseEntity.badRequest().body("balance tidak boleh negatif");
    }
    accounts.put(account.getAccountNumber(), account);
    return ResponseEntity.ok(account);
}
```

**Insight:** perbaikan ini murni *business logic* — sesuatu yang **manusia** yang harus putuskan dan implementasikan, bukan sesuatu yang bisa "ditemukan otomatis" oleh SAST generik. Ini pengingat penting: tools DevSecOps **melengkapi**, bukan **menggantikan**, penilaian dan tanggung jawab developer.

---

## 🔧 Troubleshooting — Divergent Branch Saat Push

```
! [rejected]  main -> main (fetch first)
```

**Penyebab:** commit otomatis dari CD (`github-actions[bot]` yang update tag manifest, Day 10) sudah masuk ke remote sebelum kita sempat `git pull`, sehingga histori lokal dan remote bercabang (divergent).

```
fatal: Need to specify how to reconcile divergent branches.
```

**Solusi:**
```bash
git config pull.rebase false
git pull
git push
```

**Insight:** karena commit lokal (perubahan kode) dan commit remote (update tag manifest oleh bot) menyentuh file yang berbeda, merge otomatis berjalan tanpa konflik. Ini adalah konsekuensi nyata dari pola GitOps-lite yang dibangun sejak Day 10 — developer perlu terbiasa `git pull` sebelum push, karena repo bisa saja "berubah sendiri" oleh CD di antara waktu kerja.

---

## 🔬 Verifikasi Akhir — Validasi Input Bekerja di Runtime

```bash
curl -X POST http://localhost:8080/api/accounts \
  -d '{"accountNumber":"","ownerName":"Test","balance":1000000}'
# -> "accountNumber tidak boleh kosong"

curl -X POST http://localhost:8080/api/accounts \
  -d '{"accountNumber":"1003","ownerName":"Test","balance":-500}'
# -> "balance tidak boleh negatif"

curl -X POST http://localhost:8080/api/accounts \
  -d '{"accountNumber":"1003","ownerName":"Andi Wijaya","balance":7500000}'
# -> {"accountNumber":"1003","ownerName":"Andi Wijaya","balance":7500000.0}
```

Ketiga hasil sesuai ekspektasi — pembuktian bahwa perubahan yang lolos seluruh 7 gate keamanan (5 di CI + 2 di CD) benar-benar berfungsi seperti yang diharapkan setelah di-deploy.

---

## 📌 Rekap Seluruh Journey (Day 01-12)

### Part 1 — DevOps Foundation
| Hari | Pencapaian |
|---|---|
| 01 | Aplikasi Spring Boot + Dockerfile multi-stage dengan security dasar |
| 02 | CI dengan GitHub Actions, eksperimen `git revert` |
| 03 | CD — build & push image ke ghcr.io dengan `workflow_run` |
| 04 | Deploy ke Kubernetes (kind), Deployment + Service |
| 05 | Ditemukan celah CD vs Continuous Deployment, `kubectl rollout restart` |

### Part 2 — DevSecOps
| Hari | Tool | Pencapaian |
|---|---|---|
| 06 | Gitleaks | Secret scanning, konsep allowlist, history Git permanen |
| 07 | Semgrep | SAST, command injection ditemukan & diperbaiki dengan `ProcessBuilder` |
| 08 | Trivy (fs) | Dependency scan, 27 CVE → 0 lewat upgrade Spring Boot bertahap |
| 09 | Trivy (image) | Image scan, `apk update && apk upgrade` untuk patch OS packages |
| 10 | kube-score | Manifest scan, security context, probe terpisah, GitOps-lite, risk acceptance |
| 11 | OWASP ZAP | DAST, `host.docker.internal`, CPU throttling vs probe timing |
| 12 | — | Review end-to-end, menutup celah validasi input, verifikasi penuh |

---

## 📌 Insight Penting — Refleksi Keseluruhan

- **Shift-left bukan sekadar slogan** — terbukti nyata: makin awal masalah ditemukan (secret di commit, kode rawan sebelum build, CVE sebelum deploy), makin murah biaya perbaikannya dibanding ditemukan setelah production.
- **Tools saling melengkapi, tidak ada yang cukup sendirian** — Gitleaks tidak menangkap pola kode, Semgrep tidak menangkap CVE dependency, Trivy tidak menangkap misconfiguration K8s, kube-score tidak menangkap vulnerability runtime. Butuh kombinasi berlapis (defense in depth).
- **Tidak semua bisa diotomatisasi** — validasi input (business logic) dan risk acceptance (NetworkPolicy, PodDisruptionBudget di Day 10) tetap butuh keputusan dan penilaian manusia.
- **Infrastruktur nyata itu tidak sempurna** — rate limit Maven Central (Day 08), instabilitas artifact upload GitHub (Day 11), Docker Desktop yang perlu restart — bagian penting dari pengalaman DevSecOps adalah membedakan masalah kode vs masalah infrastruktur, dan tahu kapan harus `continue-on-error` vs benar-benar memperbaiki.
- **Homelab punya keterbatasan yang jujur diakui** — single-node cluster, tidak ada ArgoCD/Flux sungguhan, GitOps-lite manual — tapi prinsip dan cara berpikirnya tetap representatif untuk dunia kerja sungguhan.
- **Debugging sistematis adalah skill inti** — `kubectl describe`, `kubectl logs --previous`, membaca Events, memahami CPU throttling vs probe timing — ini kemampuan yang terbentuk lewat pengalaman troubleshooting nyata, bukan cuma ikut tutorial.

---

## 🔧 Opsional Lanjutan (Sudah Dicatat di README)

- **Jenkins** — migrasikan pipeline yang sama untuk memahami konsep CI/CD di tool berbeda
- **Ansible** — provisioning/konfigurasi cluster sebelum deploy
- **ArgoCD/Flux (GitOps)** — otomatisasi penuh tanpa celah timing yang dialami di Day 10-12

---

[⬅️ Day 11](../day-11-owasp-zap-dast/notes.md) | [⬅️ Kembali ke index](../README.md)
