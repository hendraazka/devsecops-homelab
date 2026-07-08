# Day 05 — Review End-to-End Part 1

[⬅️ Day 04](../day-04-kubernetes-deploy/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 06](../day-06-gitleaks-secret-scanning/notes.md)

---

## ✅ Yang Dipelajari

- [x] Melihat pipeline penuh berjalan end-to-end: push kode → CI → CD → (celah) → deploy K8s
- [x] Menemukan celah nyata: image baru di registry **tidak otomatis** ter-deploy ke cluster
- [x] `kubectl rollout restart` — memaksa Deployment menarik ulang image terbaru
- [x] Rollout adalah proses **bertahap** (pod baru dibuat dulu, baru pod lama dimatikan), bukan instan
- [x] Konsep Continuous Delivery vs Continuous **Deployment** (butuh tools tambahan seperti ArgoCD/Flux untuk full-otomatis)

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **Continuous Delivery (CD)** | Barang siap kirim di gudang | Image sudah dibuild & tersedia di registry, siap dipakai — tapi belum tentu otomatis dipakai |
| **Continuous Deployment** | Barang otomatis sampai ke pelanggan | Image baru otomatis ter-deploy ke cluster tanpa aksi manual (butuh tools tambahan: ArgoCD, Flux — GitOps) |
| **`kubectl rollout restart`** | Ganti shift kerja bertahap | Membuat pod baru dulu (dengan image terbaru), baru mematikan pod lama satu-satu setelah pod baru siap |
| **`imagePullPolicy: Always`** (default utk tag `:latest`) | Selalu cek gudang terbaru | Setiap pod dibuat/restart, Kubernetes selalu tarik ulang image dari registry, bukan pakai cache lokal |

**Insight utama hari ini:**
Pipeline CI/CD yang sudah kita bangun (Day 02-03) itu **Continuous Delivery**, bukan **Continuous Deployment**. Image baru memang otomatis tersedia di ghcr.io, tapi cluster Kubernetes tidak tahu-menahu soal itu sampai ada perintah eksplisit (`kubectl apply` ulang atau `kubectl rollout restart`).

---

## 💻 Langkah 1 — Buat Perubahan Kecil (Endpoint Baru)

Tambahkan endpoint `DELETE` ke `AccountController.java`:

```java
@DeleteMapping("/{accountNumber}")
public String deleteAccount(@PathVariable String accountNumber) {
    accounts.remove(accountNumber);
    return "Akun " + accountNumber + " berhasil dihapus";
}
```

---

## 💻 Langkah 2 — Push dan Amati Pipeline

```bash
git add .
git commit -m "day 5: add DELETE endpoint - review end-to-end pipeline"
git push
```

Amati di tab Actions: **CI** jalan dulu → sukses → **CD** otomatis menyusul → image baru ter-push ke ghcr.io dengan endpoint DELETE.

---

## 🔬 Eksperimen: Membuktikan Celah CD vs CI/CD Penuh

### Test endpoint baru ke pod yang sedang jalan (SEBELUM rollout restart)

```bash
kubectl port-forward service/account-service 8080:8080
```
```bash
curl -X DELETE http://localhost:8080/api/accounts/1002
```

**Hasil:** `405 Method Not Allowed` — pod yang sedang jalan masih pakai image lama (dari Day 04), belum ada endpoint DELETE, meskipun image baru sudah ada di registry.

### Paksa Kubernetes tarik image terbaru

```bash
kubectl rollout restart deployment account-service
kubectl rollout status deployment account-service
kubectl get pods
```

### Test ulang

```bash
kubectl port-forward service/account-service 8080:8080
```
```bash
curl -X DELETE http://localhost:8080/api/accounts/1002
```

**Hasil:** berhasil — `"Akun 1002 berhasil dihapus"`.

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| `vi /src/main/java/...`: file/folder not found | Path diawali `/` (absolute path dari root filesystem), padahal maksudnya relative path dari folder saat ini | Hapus `/` di depan: `src/main/java/...` (tanpa garis miring awal) |
| Endpoint DELETE tetap `405` walau sudah `rollout restart` | Proses testing (`curl` via `port-forward`) dilakukan saat rollout **belum sepenuhnya selesai** — port-forward yang sudah terbuka duluan sempat nyambung ke pod lama yang masih dalam proses terminating | Tunggu `kubectl rollout status` benar-benar selesai, `Ctrl+C` matikan port-forward lama, jalankan ulang `kubectl port-forward` dari awal, baru test lagi |

---

## 📌 Insight Penting

- **CD ≠ Continuous Deployment.** CD (Continuous Delivery) berhenti di "image siap dipakai di registry". Untuk benar-benar otomatis sampai ke cluster tanpa aksi manual, butuh pendekatan **GitOps** (ArgoCD, Flux) — topik lanjutan di luar 12 hari roadmap utama, tapi baik untuk diketahui posisinya.
- Rollout Kubernetes itu **bertahap**, bukan instan — penting dipahami supaya tidak salah kesimpulan ("kok masih error?") padahal prosesnya memang belum selesai.
- Kesalahan kecil seperti path `/` di depan command (`/src/...` vs `src/...`) adalah pengingat pentingnya paham **absolute path vs relative path** di Linux — sumber bug klasik yang sering bikin bingung pemula.

---

[⬅️ Day 04](../day-04-kubernetes-deploy/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 06](../day-06-gitleaks-secret-scanning/notes.md)
