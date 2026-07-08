# Day 03 - CD: Build & Push Docker Image ke ghcr.io

## Yang dipelajari
- Konsep Continuous Delivery (CD): setelah kode lolos CI, otomatis dibungkus
  jadi Docker image dan disimpan di registry, siap dipakai kapan saja.
- `workflow_run` trigger: cara menghubungkan 2 workflow file terpisah
  (cd.yml menunggu ci.yml selesai), beda dengan `needs:` yang cuma berlaku
  antar-job dalam satu file yang sama.
- `if: github.event.workflow_run.conclusion == 'success'` - filter supaya
  job build & push image hanya benar-benar jalan kalau CI sukses.
- `permissions: packages: write` - GITHUB_TOKEN bawaan perlu izin eksplisit
  untuk bisa push ke ghcr.io (GitHub Container Registry).
- Setting repo "Workflow permissions" harus "Read and write" di
  Settings > Actions > General.
- Image tagging ganda (`latest` + commit SHA) untuk traceability - bisa
  tahu persis image dibuild dari commit mana.

## Yang dikerjakan
- Membuat `.github/workflows/cd.yml` - build & push image ke
  `ghcr.io/hendraazka/account-service` otomatis setelah CI sukses.
- Push dan verifikasi: CI jalan dulu (sukses) -> CD otomatis menyusul (sukses).
- Verifikasi image muncul di GitHub Packages dengan 2 tag (latest + commit SHA).

## Hasil
Pipeline CI -> CD lengkap dan terbukti bekerja end-to-end:
push kode -> CI test -> CD build & push image -> image tersedia di ghcr.io

## Referensi
- Workflow: [`.github/workflows/cd.yml`](../.github/workflows/cd.yml)
- Contoh run CD sukses: [run #1](https://github.com/hendraazka/devsecops-homelab/actions/runs/28919253390)
- Image: `ghcr.io/hendraazka/account-service`
