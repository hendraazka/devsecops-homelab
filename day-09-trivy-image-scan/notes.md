# Day 09 — Trivy (Image Mode): Container Image Scan

[⬅️ Day 08](../day-08-trivy-dependency-scan/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 10](../day-10-kube-score-manifest-scan/notes.md)

---

## ✅ Yang Dipelajari

- [x] Bedanya scan dependency Java (Day 08) dengan scan image Docker (hari ini) — scope beda
- [x] Image Docker bisa punya CVE di **OS packages** bawaan base image, terpisah dari CVE di JAR aplikasi
- [x] Pola "build → scan → push bersyarat" — mencegah image bervulnerabilitas ter-push ke registry
- [x] `push: false, load: true` di `docker/build-push-action` — build lokal dulu sebelum diputuskan push
- [x] `apk update && apk upgrade` di Dockerfile — memastikan image dapat patch OS terbaru saat build time

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **Image scan vs dependency scan** | Cek seluruh rumah vs cek 1 kamar | Dependency scan (Day 08) fokus ke library Java; image scan (hari ini) mencakup seluruh isi image: OS packages, base image, dan JAR yang sudah ter-compile |
| **Base image OS packages** | Perabotan bawaan rumah kontrakan | Package sistem (`libexpat`, `p11-kit`, dll) yang ikut terbawa dari base image, bukan yang kita install sengaja |
| **Build → Scan → Push bersyarat** | QC sebelum barang dikirim ke gudang | Image dibuild dan dicek dulu secara lokal; hanya dikirim ke registry (ghcr.io) kalau lolos pemeriksaan |
| **`apk update && apk upgrade`** | Update aplikasi HP sebelum dipakai | Menarik versi terbaru semua package Alpine terpasang saat build time, tidak bergantung kapan base image upstream terakhir di-rebuild |

**Kenapa scan image tetap perlu walau dependency Java sudah 0 CVE (Day 08)?**
Karena JAR aplikasi cuma sebagian dari isi image — base image (OS + package sistemnya) adalah komponen terpisah yang bisa punya kerentanan sendiri, di luar kendali kode Java yang kita tulis.

---

## 💻 Langkah 1 — Ubah `cd.yml`: Build Lokal → Scan → Push Bersyarat

```yaml
      - name: Build image (lokal dulu, belum push)
        uses: docker/build-push-action@v5
        with:
          context: ./account-service
          push: false
          load: true
          tags: |
            ghcr.io/${{ github.repository_owner }}/account-service:latest
            ghcr.io/${{ github.repository_owner }}/account-service:${{ github.event.workflow_run.head_sha }}

      - name: Trivy image scan
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: ghcr.io/${{ github.repository_owner }}/account-service:latest
          severity: 'CRITICAL,HIGH'
          exit-code: '1'

      - name: Push image (hanya jalan kalau scan lolos)
        run: |
          docker push ghcr.io/${{ github.repository_owner }}/account-service:latest
          docker push ghcr.io/${{ github.repository_owner }}/account-service:${{ github.event.workflow_run.head_sha }}
```

**Insight:**
- `push: false, load: true` — image dibuild dan dimuat ke Docker lokal di runner, bukan langsung dikirim ke registry.
- Step scan diletakkan **di antara** build dan push — kalau scan gagal (exit code 1), step `docker push` setelahnya otomatis tidak dijalankan (perilaku default GitHub Actions: berhenti di step yang gagal).
- Step push terakhir pakai `docker push` manual karena image sudah ada di lokal dengan tag yang sama persis seperti yang dibuild.

---

## 🔬 Temuan CVE — Percobaan Pertama

```
ghcr.io/hendraazka/account-service:latest (alpine 3.23.5)   5 vulnerabilities
app/app.jar                                                  0 vulnerabilities
```

**Total: 5 (HIGH: 5, CRITICAL: 0)** — semua di OS packages Alpine, bukan di JAR aplikasi:

| Library | CVE | Fixed Version |
|---|---|---|
| `libexpat` | CVE-2026-56131, 56407, 56408 (integer overflow, dll) | 2.8.2-r0 |
| `p11-kit` / `p11-kit-trust` | CVE-2026-2100 (NULL dereference) | 0.26.2-r0 |

**Insight:** `libexpat` (XML parser) dan `p11-kit` (crypto/PKCS#11) bukan sesuatu yang kita install sengaja — ikut terbawa sebagai bagian dari base image `eclipse-temurin:17-jre-alpine`. JAR aplikasi sendiri sudah bersih (0 temuan) berkat perbaikan Day 08.

---

## 💻 Langkah 2 — Perbaikan: Force Update Package Alpine di Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

RUN apk update && apk upgrade --no-cache

WORKDIR /app
```

**Insight:** Base image `eclipse-temurin:17-jre-alpine` adalah *floating tag* — versinya berubah seiring waktu, tapi runner bisa saja masih pakai versi yang ter-cache. `apk update && apk upgrade` memaksa image menarik versi terbaru dari **semua package Alpine terpasang** saat build time, tidak bergantung kapan maintainer base image terakhir melakukan rebuild.

**Posisi penting:** command ini harus dijalankan **sebelum** `USER appuser` — karena `apk upgrade` butuh privilege root untuk update package sistem, baru setelah itu kita turunkan ke non-root user untuk menjalankan aplikasi.

**Hasil:** 5 temuan CVE hilang total — image sekarang 0 CVE, sama seperti JAR aplikasinya.

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| Isi file `cd.yml` sempat terlihat terpotong saat `cat` | Tampilan sisa scroll terminal yang membingungkan, bukan file benar-benar rusak | Verifikasi ulang dengan `cat` dan `wc -l` — file ternyata sudah tersimpan lengkap dan benar |

---

## 📌 Insight Penting

- Scan dependency (level aplikasi) dan scan image (level container/OS) itu **saling melengkapi**, bukan salah satu cukup mewakili yang lain — keduanya perlu ada di pipeline.
- Base image yang "kelihatan minimal" (Alpine) tetap bisa punya CVE di package sistemnya — jangan berasumsi image kecil otomatis aman.
- Pola "build → scan → push bersyarat" adalah praktik penting di CD sungguhan: mencegah artefak bervulnerabilitas sampai ke registry produksi, bukan cuma mendeteksi setelah terlanjur tersebar.
- `apk update && apk upgrade` (atau perintah setara di distro lain) sebaiknya jadi kebiasaan standar di Dockerfile manapun yang pakai base image Linux minimal, sebagai lapisan pertahanan tambahan terhadap CVE OS yang baru ditemukan setelah base image terakhir di-rebuild.

---

[⬅️ Day 08](../day-08-trivy-dependency-scan/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 10](../day-10-kube-score-manifest-scan/notes.md)
