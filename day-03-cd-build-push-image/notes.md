# Day 03 — CD: Build & Push Docker Image ke ghcr.io

[⬅️ Day 02](../day-02-ci-basics/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 04](../day-04-kubernetes-deploy/notes.md)

---

## ✅ Yang Dipelajari

- [x] Konsep **Continuous Delivery (CD)** — kode yang lolos CI otomatis dibungkus jadi image dan disimpan di registry
- [x] `workflow_run` — menghubungkan 2 file workflow terpisah (CI dan CD)
- [x] `permissions: packages: write` — memberi izin `GITHUB_TOKEN` push ke registry
- [x] Login otomatis ke ghcr.io tanpa setup token manual
- [x] Image tagging ganda (`latest` + commit SHA) untuk traceability
- [x] Verifikasi image muncul di GitHub Packages

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **CD (Continuous Delivery)** | Petugas pengemasan | Setelah barang (kode) lolos QC (CI), otomatis dikemas (image) dan disimpan di gudang (registry) |
| **`workflow_run`** | Estafet lari | Workflow B menunggu workflow A benar-benar selesai dulu, baru mulai jalan |
| **`needs:`** vs **`workflow_run`** | Rekan 1 tim vs 2 tim beda gedung | `needs:` cuma berlaku antar-job dalam **1 file** yang sama; `workflow_run` menghubungkan **2 file workflow terpisah** |
| **`GITHUB_TOKEN`** | Kartu akses bawaan | Token otomatis tersedia di setiap run, tidak perlu bikin akun/token terpisah seperti di Docker Hub |
| **Image tag (SHA vs latest)** | Nomor resi vs status umum | `latest` = versi terbaru saat ini; tag commit SHA = bukti persis image ini dibuild dari commit yang mana |

**Kenapa CD dipisah dari CI, tidak digabung 1 file saja?**
Supaya alurnya jelas terbaca: CI fokus "apakah kode ini benar?", CD fokus "kalau benar, kemas dan simpan". Ini juga mendekati pola CD sungguhan di dunia kerja — pipeline terpisah tapi saling terhubung berdasarkan hasil, bukan 1 file besar yang mengerjakan semuanya.

---

## 💻 Langkah 1 — Cek Setting Permission Repo

Sebelum membuat workflow, pastikan dulu **Settings > Actions > General > Workflow permissions** diset **"Read and write permissions"** — kalau masih default (read-only), proses push image akan gagal meskipun `cd.yml` sudah benar.

---

## 💻 Langkah 2 — `cd.yml`

`.github/workflows/cd.yml`

```yaml
name: CD - Build and Push Image

on:
  workflow_run:
    workflows: ["CI - Build and Test"]
    types:
      - completed
    branches: [main]

jobs:
  build-and-push-image:
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout kode (commit yang sama dengan yang di-test CI)
        uses: actions/checkout@v4
        with:
          ref: ${{ github.event.workflow_run.head_sha }}

      - name: Login ke ghcr.io
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build dan push image
        uses: docker/build-push-action@v5
        with:
          context: ./account-service
          push: true
          tags: |
            ghcr.io/${{ github.repository_owner }}/account-service:latest
            ghcr.io/${{ github.repository_owner }}/account-service:${{ github.event.workflow_run.head_sha }}
```

**Insight:**
- `if: github.event.workflow_run.conclusion == 'success'` — job ini tetap "terpicu" setiap CI selesai, tapi **hanya benar-benar jalan** kalau hasilnya sukses. Kalau CI gagal, build & push image otomatis di-skip.
- `ref: ${{ github.event.workflow_run.head_sha }}` — memastikan yang di-build adalah **persis commit yang sama** yang lolos CI, bukan commit terbaru yang mungkin sudah berubah lagi.

---

## 🔬 Verifikasi

Setelah push, urutan workflow yang terjadi otomatis:
1. **CI - Build and Test** jalan dulu (dari trigger push biasa)
2. Setelah CI sukses, **CD - Build and Push Image** otomatis menyusul (trigger `workflow_run`)

Cek di tab **Actions** — harus muncul 2 run berurutan, keduanya ✅.

Cek image benar-benar ada:
1. Buka `https://github.com/<username>?tab=packages`
2. Klik package `account-service`
3. Harus muncul 2 tag: `latest` dan tag commit SHA

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| (Preventif — dicek sebelum push) push image berpotensi gagal permission | Default `GITHUB_TOKEN` di level repo hanya "read" | Ubah setting repo ke "Read and write permissions" di Settings > Actions > General, sebelum push |

---

## 📌 Insight Penting

- `workflow_run` adalah pola yang lebih dekat dengan CD sungguhan: pipeline terpisah tapi saling terhubung berdasarkan hasil, bukan digabung jadi 1 file besar.
- Tagging image dengan commit SHA (bukan cuma `latest`) penting untuk **traceability** — kalau ada masalah di production, bisa tahu persis image itu dibuild dari commit mana, dan bisa rollback dengan pasti.
- `GITHUB_TOKEN` bawaan menyederhanakan banyak hal dibanding registry lain (misal Docker Hub) yang butuh setup token/akun terpisah.

---

[⬅️ Day 02](../day-02-ci-basics/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 04](../day-04-kubernetes-deploy/notes.md)
