# Day 02 - CI Dasar dengan GitHub Actions

## Yang dipelajari
- Konsep Continuous Integration (CI): setiap push/PR otomatis di-build & di-test
  oleh server GitHub, bukan cuma divalidasi manual di laptop sendiri.
- Struktur file workflow GitHub Actions (`.github/workflows/*.yml`):
  - `on:` - trigger kapan workflow dijalankan (push, pull_request)
  - `jobs:` - pekerjaan yang dilakukan, jalan di runner baru (ubuntu-latest)
  - `steps:` - urutan perintah: checkout kode, setup Java, build, test
- `working-directory` diperlukan karena source code ada di dalam folder
  `account-service/`, bukan di root repo.
- Konsep `git revert`: membuat commit baru yang membatalkan efek commit
  sebelumnya, tanpa menghapus history - beda dengan `git reset` yang
  menghapus commit dari history.

## Yang dikerjakan
- Membuat `.github/workflows/ci.yml` - pipeline CI yang menjalankan
  `mvn clean compile` dan `mvn test` otomatis setiap push/PR ke branch main.
- Push dan verifikasi CI jalan sukses (run #1 - hijau).
- Eksperimen: sengaja merusak kode (syntax error) untuk membuktikan CI
  benar-benar mendeteksi masalah (run #2 - gagal/merah, sesuai rencana).
- Memperbaiki dengan `git revert`, CI kembali sukses (run #3 - hijau).

## Hasil
Siklus lengkap terbukti bekerja:
kode benar (sukses) -> sengaja dirusak (CI mendeteksi, gagal) -> direvert (pulih, sukses lagi)

## Referensi
- Workflow: [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
- Contoh run sukses: [run #1](https://github.com/hendraazka/devsecops-homelab/actions/runs/28917291150)
- Contoh run gagal: [run #2](https://github.com/hendraazka/devsecops-homelab/actions/runs/28917604573)
- Contoh run revert: [run #3](https://github.com/hendraazka/devsecops-homelab/actions/runs/28917852592)
