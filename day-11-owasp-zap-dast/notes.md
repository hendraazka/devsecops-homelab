# Day 11 — OWASP ZAP: DAST (Dynamic Application Security Testing)

[⬅️ Day 10](../day-10-kube-score-manifest-scan/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 12](../day-12-review-devsecops/notes.md)

---

## ✅ Yang Dipelajari

- [x] Bedanya DAST dengan semua tools sebelumnya — menyerang aplikasi yang benar-benar hidup, bukan analisis statis
- [x] Pola "build → scan image → scan runtime (DAST) → push bersyarat" di CD
- [x] ZAP baseline scan vs full scan — trade-off kecepatan vs kedalaman
- [x] `continue-on-error` untuk memisahkan kegagalan infrastruktur dari kegagalan security sungguhan
- [x] `host.docker.internal` — cara container Docker Desktop for Windows/WSL2 mengakses port di host
- [x] Startup Spring Boot bisa sangat lambat kalau CPU limit terlalu ketat — dampaknya ke liveness/readiness probe
- [x] Validasi silang: hasil scan image mentah (CD) vs instance live di cluster (manual) harus konsisten

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **DAST** | Uji coba sistem alarm rumah sungguhan | Menyerang aplikasi yang benar-benar berjalan, mengirim request nyata dan menganalisis respons — beda dengan SAST yang cuma baca kode |
| **ZAP Baseline Scan** | Patroli cepat keliling kompleks | Scan cepat (menit), cocok untuk CI; beda dengan "full scan" yang jauh lebih dalam tapi bisa berjam-jam |
| **`continue-on-error`** | Catatan "opsional" di checklist | Step boleh gagal tanpa menghentikan seluruh job — dipakai untuk bagian yang bukan inti keputusan pass/fail |
| **`host.docker.internal`** | Alamat khusus "rumah tetangga" | Hostname yang disediakan Docker Desktop untuk container mengakses port yang terbuka di mesin host (Windows/WSL2), pengganti `localhost` yang tidak selalu berfungsi karena arsitektur VM Docker Desktop |
| **CPU throttling saat startup** | Mobil nanjak dengan mesin dibatasi | CPU limit yang terlalu ketat memperlambat proses startup aplikasi berat (JVM + Spring Boot), berpotensi bikin startup melebihi toleransi probe |

**Kenapa DAST logis jadi tahap terakhir shift-left?**
DAST butuh aplikasi yang benar-benar hidup dan bisa diakses — jadi baru bisa dijalankan setelah semua tahap sebelumnya (build, scan statis, deploy) selesai. Ini pelengkap akhir siklus keamanan, menutup celah yang cuma muncul saat runtime.

---

## 💻 Langkah 1 — Tambahkan ZAP Scan Otomatis ke `cd.yml`

```yaml
      - name: Jalankan container untuk DAST scan
        run: |
          docker run -d --name zap-target -p 8080:8080 ghcr.io/${{ github.repository_owner }}/account-service:latest
          echo "Menunggu aplikasi siap..."
          for i in {1..20}; do
            if curl -sf http://localhost:8080/actuator/health/readiness; then
              echo "Aplikasi siap!"
              break
            fi
            sleep 3
          done

      - name: OWASP ZAP Baseline Scan
        continue-on-error: true
        uses: zaproxy/action-baseline@v0.12.0
        with:
          target: 'http://localhost:8080'
          cmd_options: '-I'
          allow_issue_writing: false
          artifact_name: zap-report-${{ github.event.workflow_run.head_sha }}

      - name: Stop container DAST scan
        if: always()
        run: docker stop zap-target && docker rm zap-target
```

**Insight:**
- Diletakkan **setelah** Trivy image scan, **sebelum** push — konsisten dengan pola "build → scan → push bersyarat" sejak Day 09.
- Image yang di-scan adalah yang **sudah dibuild lokal** di runner (`load: true` dari Day 09) — dijalankan sebagai container biasa, di-scan, lalu dihentikan; tidak perlu akses ke cluster kind lokal sama sekali (menghindari masalah jaringan Day 10).
- Loop `curl` dengan retry memanfaatkan endpoint `/actuator/health/readiness` (Day 10) untuk memastikan aplikasi benar-benar siap sebelum scan dimulai.
- `if: always()` pada step stop container — memastikan container selalu dibersihkan, baik scan sukses maupun gagal.

---

## 🔧 Troubleshooting CD — 3 Masalah Berurutan (Semua Infrastruktur, Bukan Aplikasi)

### Masalah 1 — Permission GitHub API

```
Error: Resource not accessible by integration - create-an-issue
```

**Penyebab:** `zaproxy/action-baseline` defaultnya mencoba membuat GitHub Issue otomatis untuk laporan, tapi `GITHUB_TOKEN` tidak diberi permission `issues: write`.

**Solusi:**
```yaml
allow_issue_writing: false
```

### Masalah 2 — Artifact Name Conflict

```
Error: Create Artifact Container failed: The artifact name zap_scan is not valid.
```

**Penyebab:** nama artifact default (`zap_scan`) bentrok, kemungkinan karena beberapa run CD sempat overlap.

**Solusi:**
```yaml
artifact_name: zap-report-${{ github.event.workflow_run.head_sha }}
```

### Masalah 3 — Instabilitas Infrastruktur Upload Artifact GitHub

```
Create Artifact Container - Error is not retryable
Status Code: 400 Bad Request
```

**Penyebab:** meski nama artifact sudah unik, error yang sama tetap muncul — mengindikasikan gangguan sesaat di layanan artifact upload GitHub Actions sendiri, bukan sesuatu yang bisa diperbaiki dari sisi konfigurasi kita. Scan ZAP sendiri **sukses total** (`PASS: 66`, `FAIL-NEW: 0`) — masalah murni terjadi di tahap setelah scan selesai.

**Solusi:**
```yaml
continue-on-error: true
```

**Insight:** laporan lengkap sudah terlihat di log job, jadi upload artifact sebenarnya cuma "nice to have". Trade-off yang diterima sadar: prioritaskan hasil scan yang terbaca di log, tidak bergantung pada fitur pelengkap yang ternyata kadang tidak stabil.

---

## 🔬 Hasil Scan ZAP (Otomatis di CD)

```
WARN-NEW: Storable and Cacheable Content [10049] x 1
    http://localhost:8080 (404 Not Found)
FAIL-NEW: 0    FAIL-INPROG: 0    WARN-NEW: 1    PASS: 66
```

**Insight:** 66 pemeriksaan lolos, cuma 1 warning ringan soal HTTP caching header pada response 404 — bukan kerentanan serius.

---

## 🔬 DAST Manual — Scan ke Instance Live di Cluster kind

### Troubleshooting: Pod Baru Gagal Stabil (`CrashLoopBackOff` Lagi)

```bash
kubectl get pods
# account-service-5c798744bd-98lmg   0/1   Running   3 (102s ago)   7m37s
```

```bash
kubectl logs account-service-5c798744bd-98lmg --previous
# ...
# Started AccountServiceApplication in 87.694 seconds (process running for 93.689)
```

**Penyebab:** startup Spring Boot memakan **87 detik** — jauh melebihi ekspektasi normal, hampir menyamai total toleransi probe yang sudah diperbesar di Day 10 (`initialDelaySeconds: 45` + beberapa retry). Kemungkinan besar disebabkan **CPU throttling**: limit `cpu: "500m"` terlalu ketat untuk proses startup JVM + Spring Boot yang berat, terutama saat laptop sedang banyak beban (Docker Desktop, WSL2, cluster, proses lain berjalan bersamaan).

**Solusi — naikkan CPU limit DAN perbesar toleransi probe (dua sisi):**
```yaml
          resources:
            requests:
              memory: "256Mi"
              cpu: "500m"
              ephemeral-storage: "256Mi"
            limits:
              memory: "512Mi"
              cpu: "1000m"
              ephemeral-storage: "512Mi"
          livenessProbe:
            initialDelaySeconds: 90
            periodSeconds: 10
            failureThreshold: 6
          readinessProbe:
            initialDelaySeconds: 60
            periodSeconds: 10
            failureThreshold: 6
```

Setelah `kubectl apply`, tunggu 90-120 detik: pod stabil `1/1 Running`, 0 restart.

### Troubleshooting: `--network host` Tidak Berfungsi di Docker Desktop

```bash
docker run --rm --network host -t zaproxy/zap-stable zap-baseline.py -t http://localhost:8080 -I
# Connection refused
```

**Penyebab:** di Docker Desktop for Windows/WSL2, container sebenarnya jalan di dalam VM terpisah — `--network host` tidak memetakan `localhost` container ke `localhost` WSL2 seperti di Linux native.

**Solusi — pakai hostname khusus Docker Desktop:**
```bash
docker run --rm -t zaproxy/zap-stable zap-baseline.py \
  -t http://host.docker.internal:8080 \
  -I
```

### Hasil Scan Manual — Konsisten dengan Hasil Otomatis di CD

```
WARN-NEW: Storable and Cacheable Content [10049] x 1
    http://host.docker.internal:8080/sitemap.xml (404 Not Found)
FAIL-NEW: 0    PASS: 66
```

**Insight penting:** hasil scan terhadap image mentah (di CD) dan instance live di cluster (manual) **identik** — pembuktian bahwa apa yang dites di pipeline benar-benar merepresentasikan apa yang berjalan di production/cluster, bukan cuma formalitas yang terpisah dari kenyataan runtime.

---

## 🔧 Ringkasan Troubleshooting

| Masalah | Penyebab | Solusi |
|---|---|---|
| ZAP action gagal buat GitHub Issue | `GITHUB_TOKEN` tidak punya permission `issues: write` | `allow_issue_writing: false` |
| Artifact upload gagal (nama bentrok) | Nama artifact default sama di beberapa run | `artifact_name` dengan akhiran unik (SHA commit) |
| Artifact upload tetap gagal meski nama unik | Gangguan sesaat infrastruktur GitHub Actions sendiri | `continue-on-error: true`, andalkan log sebagai sumber hasil utama |
| Pod baru `CrashLoopBackOff`, startup 87 detik | CPU limit terlalu ketat untuk startup JVM + Spring Boot yang berat | Naikkan CPU limit (500m → 1000m) dan perbesar toleransi probe |
| ZAP scan manual `Connection refused` ke `localhost` | `--network host` tidak berfungsi penuh di Docker Desktop for Windows/WSL2 | Gunakan `host.docker.internal` sebagai pengganti `localhost` |

---

## 📌 Insight Penting

- DAST melengkapi seluruh rangkaian scanning sebelumnya — menguji aplikasi dari sudut pandang penyerang sungguhan, bukan cuma membaca kode/konfigurasinya.
- Tidak semua kegagalan pipeline berarti masalah keamanan atau kode — penting membedakan **kegagalan infrastruktur pihak ketiga** (upload artifact GitHub tidak stabil) dari **kegagalan sungguhan** (scan menemukan vulnerability), supaya keputusan `continue-on-error` diambil dengan alasan yang tepat, bukan asal supaya hijau.
- CPU/resource limit yang terlalu ketat bisa jadi penyebab tidak langsung dari kegagalan yang **kelihatannya** soal probe/networking — startup aplikasi berat (JVM) sangat sensitif terhadap CPU throttling.
- Docker Desktop for Windows/WSL2 punya perilaku jaringan yang berbeda dari Linux native (`--network host` tidak selalu bekerja seperti yang diharapkan) — `host.docker.internal` adalah solusi standar untuk kasus ini.
- Validasi silang antara hasil scan otomatis (CD) dan manual (langsung ke cluster) adalah praktik baik untuk memastikan pipeline benar-benar merepresentasikan kondisi nyata, bukan sekadar checklist administratif.

---

[⬅️ Day 10](../day-10-kube-score-manifest-scan/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 12](../day-12-review-devsecops/notes.md)
