# Day 06 — Gitleaks: Secret Scanning di CI

[⬅️ Day 05](../day-05-review-end-to-end/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 07](../day-07-semgrep-sast/notes.md)

---

## ✅ Yang Dipelajari

- [x] Kenapa secret scanning masuk paling awal di DevSecOps (shift-left paling ekstrem)
- [x] Cara kerja Gitleaks — scan pola yang menyerupai secret (API key, token, password)
- [x] Menambahkan job baru yang jalan **paralel** dengan job test di file workflow yang sama
- [x] `fetch-depth: 0` — perlunya scan seluruh history Git, bukan cuma commit terbaru
- [x] Konsep **allowlist** pada secret scanner — contoh/dummy yang terlalu umum tidak terdeteksi
- [x] `git revert` **tidak menghapus** secret dari history — cuma menyembunyikan di commit terbaru
- [x] Langkah penanganan yang benar kalau secret asli sampai ter-push (revoke dulu, baru bersihkan kode)

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **Secret scanning** | Detektor logam di bandara | Memindai kode mencari pola yang menyerupai credential sebelum "masuk pesawat" (ter-commit permanen) |
| **Shift-left** | Cegah penyakit vs obati | Mendeteksi masalah sedini mungkin (di commit), bukan setelah deploy — makin awal, makin murah diperbaiki |
| **Allowlist** | Daftar pengecualian di detektor | Pola yang dikenal sebagai "contoh resmi/dokumentasi", sengaja diabaikan supaya tidak jadi false positive terus-menerus |
| **`fetch-depth: 0`** | Baca seluruh buku, bukan cuma bab terakhir | Ambil seluruh history commit Git, karena secret yang pernah di-commit & dihapus pun masih ada di history |
| **Job paralel dalam 1 file** | Dua kasir kerja bersamaan | `build-and-test` dan `secret-scan` sejajar (bukan `needs:`), jalan bersamaan mempercepat total waktu pipeline |

**Kenapa secret scanning didahulukan dari SAST/dependency/image scan lainnya?**
Karena akibatnya paling fatal kalau lolos (credential bocor bisa dipakai orang lain dalam hitungan menit), tapi pencegahannya paling murah (cukup 1 job scan ringan di CI).

---

## 💻 Langkah 1 — Tambahkan Job `secret-scan` ke `ci.yml`

Ditambahkan **sejajar** dengan job `build-and-test` yang sudah ada (bukan menggantikan):

```yaml
  secret-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout kode
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Gitleaks scan
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

**Insight:** Indentasi YAML sangat sensitif — `secret-scan:` harus sejajar persis (2 spasi) dengan `build-and-test:`, sama-sama child dari `jobs:`. Salah indentasi sedikit bisa bikin seluruh workflow gagal parse.

Push dan verifikasi 2 job jalan paralel di 1 workflow run — keduanya ✅ sukses (karena memang belum ada secret di kode).

---

## 🔬 Eksperimen 1 — Dummy Secret yang Ternyata Masuk Allowlist

```bash
cat >> account-service/src/main/resources/application.properties << 'EOF'

# sengaja untuk testing gitleaks
aws.secret.key=AKIAIOSFODNN7EXAMPLE
EOF
```

Push, cek hasilnya.

**Hasil:** job `secret-scan` tetap ✅ **sukses** — tidak terdeteksi sama sekali!

**Penyebab:** `AKIAIOSFODNN7EXAMPLE` adalah contoh AWS key resmi yang dipakai di seluruh dokumentasi AWS — saking umumnya, Gitleaks (dan hampir semua secret scanner) sudah memasukkan pola ini ke **allowlist default**, supaya tidak menghasilkan false positive setiap kali orang menulis tutorial/dokumentasi.

---

## 🔬 Eksperimen 2 — Dummy Secret yang Di-random (Tidak Masuk Allowlist)

```bash
sed -i '/aws.secret.key=AKIAIOSFODNN7EXAMPLE/d; /sengaja untuk testing gitleaks/d' account-service/src/main/resources/application.properties

cat >> account-service/src/main/resources/application.properties << 'EOF'

# sengaja untuk testing gitleaks
aws.secret.key=AKIAZQ3XJH8KDMPL2VNR
EOF
```

Push, cek hasilnya.

**Hasil:** job `secret-scan` ❌ **gagal** — kali ini terdeteksi!

**Detail temuan Gitleaks:**

| Rule ID | Commit | Start Line | Author | File |
|---|---|---|---|---|
| `aws-access-token` | `2b4ca39` | 9 | hendraazka | `account-service/src/main/resources/application.properties` |

**Insight:** Gitleaks memberi informasi lengkap — jenis secret, commit hash persis, nomor baris, dan author — memudahkan investigasi tanpa perlu menelusuri manual.

---

## 🔬 Eksperimen 3 — Membuktikan `git revert` Tidak Menghapus dari History

```bash
git revert <commit-hash-yang-berisi-secret>
git push
```

CI kembali ✅ hijau (di kondisi terbaru, baris secret sudah hilang). Tapi buktikan secret itu **masih ada** di history:

```bash
git show <commit-hash-lama>:account-service/src/main/resources/application.properties | tail -5
```

**Hasil:**
```
management.endpoint.health.show-details=always
# sengaja untuk testing gitleaks
aws.secret.key=AKIAZQ3XJH8KDMPL2VNR
```

Baris secret **masih utuh** terlihat kalau commit lama itu diintip langsung — `git revert` cuma menambah "lapisan baru" di atas, tidak menghapus yang lama.

---

## ⚠️ Prosedur yang Benar Kalau Secret ASLI Ter-push

1. **Revoke/rotate secret di provider aslinya SEGERA** (misal hapus/ganti key di AWS Console) — prioritas nomor 1. Anggap secret itu sudah "terbakar" begitu ter-push, terutama di repo public.
2. Baru setelah itu, bersihkan dari history Git kalau memang diperlukan, menggunakan tools khusus (`git filter-repo` atau BFG Repo-Cleaner) — dilakukan hati-hati karena bisa mengganggu orang lain yang sudah clone/pull repo yang sama.
3. Revert/hapus dari kode di commit terbaru (langkah minimum, tapi **tidak cukup sendirian**).

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| Dummy secret pertama (`AKIAIOSFODNN7EXAMPLE`) tidak terdeteksi Gitleaks | Pola tersebut ada di allowlist default Gitleaks (contoh resmi AWS yang terlalu umum) | Pakai dummy key yang di-random sendiri, tetap ikuti pola asli (`AKIA` + 16 karakter) tapi tidak masuk allowlist manapun |
| Docker Desktop sempat update & restart di tengah proses | Update otomatis Windows/Docker Desktop | Tidak berpengaruh ke eksperimen ini karena seluruhnya jalan di GitHub Actions (cloud), bukan di Docker lokal — tapi cluster `kind` lokal (Day 04-05) ikut mati dan perlu dicek ulang kalau mau lanjut deploy |

---

## 📌 Insight Penting

- Secret scanner **bukan jaminan mutlak** — selalu ada allowlist untuk mengurangi false positive, jadi tetap perlu kedisiplinan manual, bukan cuma mengandalkan tools.
- History Git bersifat **permanen** — sekali secret ter-commit dan ter-push, anggap sudah bocor selamanya sampai di-revoke di sumbernya, terlepas dari commit apapun setelahnya.
- Kecepatan reaksi penting: makin cepat secret di-revoke setelah ter-push (terutama di repo public), makin kecil jendela waktu bagi pihak lain untuk menyalahgunakannya.

---

[⬅️ Day 05](../day-05-review-end-to-end/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 07](../day-07-semgrep-sast/notes.md)
