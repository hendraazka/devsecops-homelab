# Day 02 — CI Dasar dengan GitHub Actions

[⬅️ Day 01](../day-01-app-and-dockerfile/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 03](../day-03-cd-build-push-image/notes.md)

---

## ✅ Yang Dipelajari

- [x] Konsep **Continuous Integration** — validasi otomatis di lingkungan bersih, bukan cuma "jalan di laptop saya"
- [x] Struktur file workflow GitHub Actions (`on:`, `jobs:`, `steps:`)
- [x] Setup pipeline yang otomatis `mvn test` setiap ada push/PR
- [x] Cara membaca hasil run di tab **Actions** GitHub
- [x] `git revert` — cara aman membatalkan commit tanpa menghapus history

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **CI (Continuous Integration)** | Satpam pemeriksa | Setiap perubahan kode otomatis dicek (compile + test) sebelum dianggap "aman" |
| **`on:`** | Alarm pemicu | Menentukan kapan workflow dijalankan (push, pull_request, dll) |
| **`runs-on: ubuntu-latest`** | Ruang kerja baru & bersih | Setiap run memakai komputer virtual baru, bukan laptop kamu |
| **`actions/checkout`** | Mengantar bahan baku | Menarik kode dari repo ke dalam runner, karena runner-nya kosong |
| **`working-directory`** | Menentukan ruangan kerja | Karena `pom.xml` ada di dalam folder `account-service/`, bukan di root repo |
| **`git revert`** | Transaksi koreksi bank | Membuat commit baru yang membatalkan efek commit lain, history lama tetap utuh |

**Kenapa CI penting?**
Tanpa CI, kode yang rusak baru ketahuan setelah orang lain (atau kamu sendiri) `git pull` dan aplikasinya error. Dengan CI, kesalahan terdeteksi **begitu kode di-push**, sebelum menyebar jadi masalah orang lain.

---

## 💻 Langkah 1 — Buat Folder Workflow

```bash
mkdir -p .github/workflows
```

---

## 💻 Langkah 2 — `ci.yml`

`.github/workflows/ci.yml`

```yaml
name: CI - Build and Test

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout kode
        uses: actions/checkout@v4

      - name: Setup Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn -B clean compile
        working-directory: ./account-service

      - name: Run tests
        run: mvn -B test
        working-directory: ./account-service
```

**Insight:** `setup-java` dengan `java-version: '17'` eksplisit menghindari masalah bentrok versi Java seperti yang terjadi di laptop lokal — di CI, environment-nya **selalu konsisten** setiap kali run.

---

## 🔬 Eksperimen: Membuktikan CI Benar-benar Mendeteksi Error

### Sengaja rusak kode

```bash
cd account-service
echo "ini bukan kode java yang valid {{{" >> src/main/java/com/homelab/accountservice/model/Account.java
cd ..
git add .
git commit -m "test: sengaja bikin error untuk lihat CI gagal"
git push
```

**Hasil:** run CI berikutnya muncul ❌ merah — pembuktian CI benar-benar membaca dan mengecek isi kode, bukan formalitas.

### Perbaiki dengan `git revert`

```bash
git log --oneline -5              # cari commit hash yang mau dibatalkan
git revert <commit-hash>          # buat commit baru yang membatalkan
git push
```

**Hasil:** run CI kembali ✅ hijau. History tetap lengkap: ada commit error, DAN ada commit revert — tidak ada yang dihapus paksa.

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| Folder `.github/workflows` belum ada saat mau isi file `ci.yml` | Command `mkdir -p` sebelumnya ter-skip | Jalankan ulang `mkdir -p .github/workflows` sebelum membuat file |
| Sempat bingung run mana yang "gagal" di antara 3 run berurutan | Ada 3 run: sukses → sengaja gagal → revert sukses lagi | Cek riwayat run satu-satu di tab Actions berdasarkan waktu, bukan cuma warna terakhir |

---

## 📌 Insight Penting

- CI hanya berguna kalau **benar-benar mendeteksi** masalah — makanya penting sesekali sengaja membuktikannya dengan merusak kode secara terkontrol.
- `git revert` adalah cara **aman** membatalkan perubahan yang sudah di-push bersama, berbeda dengan `git reset` yang menghapus history.
- Pipeline CI berjalan di lingkungan yang **selalu bersih dan konsisten** — inilah kenapa masalah seperti "bentrok versi Java" yang terjadi di laptop lokal, tidak terjadi di CI.

---

[⬅️ Day 01](../day-01-app-and-dockerfile/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 03](../day-03-cd-build-push-image/notes.md)
