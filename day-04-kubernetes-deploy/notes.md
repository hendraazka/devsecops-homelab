# Day 04 — Manifest Kubernetes + Deploy ke kind Cluster

[⬅️ Day 03](../day-03-cd-build-push-image/notes.md) | [⬅️ Kembali ke index](../README.md)

---

## ✅ Yang Dipelajari

- [x] Kenapa butuh Kubernetes setelah Docker (auto-restart, multi-replica, declarative state)
- [x] `kind` — menjalankan cluster Kubernetes sungguhan di dalam Docker, lokal
- [x] `Deployment` vs `Service`
- [x] Label & selector — mekanisme Kubernetes mengenali pod miliknya
- [x] Resource requests & limits
- [x] `livenessProbe` vs `readinessProbe`
- [x] `kubectl port-forward` untuk akses Service dari laptop lokal
- [x] Deploy image dari registry (ghcr.io), bukan build lokal lagi

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **Cluster** | Seluruh restoran | Kumpulan mesin (node) yang menjalankan Kubernetes |
| **Node** | Satu dapur/cabang | Satu mesin (fisik/virtual) dalam cluster |
| **Pod** | Satu piring makanan siap saji | Unit terkecil di Kubernetes, biasanya membungkus 1 container |
| **Deployment** | Resep + aturan jumlah porsi | Mengatur berapa banyak Pod yang harus selalu jalan, dan cara update-nya |
| **Service** | Nomor meja/alamat pemesanan | Cara Pod diakses dari luar/Pod lain, walau Pod-nya berganti-ganti IP |
| **`livenessProbe`** | Cek nadi pasien | "Apakah pod ini masih hidup? Kalau tidak, restart" |
| **`readinessProbe`** | Cek pasien siap pulang | "Apakah pod ini sudah siap menerima traffic?" — selama belum ready, tidak dikirimi traffic |

**Kenapa butuh Kubernetes setelah Docker?**
Docker cocok menjalankan 1-2 container secara manual. Tapi aplikasi production butuh: otomatis mengganti container yang crash, otomatis scaling, dan update tanpa downtime — semua ini yang dihandle Kubernetes.

**`kind` = Kubernetes IN Docker** — menjalankan cluster Kubernetes *sungguhan* di dalam container Docker di laptop sendiri. Konsep dan command-nya identik dengan Kubernetes di cloud/production, cuma skalanya kecil dan lokal.

---

## 💻 Langkah 1 — Buat Cluster

```bash
kind create cluster --name devsecops-homelab
```

Verifikasi:
```bash
kubectl cluster-info --context kind-devsecops-homelab
kubectl get nodes
```

**Insight:** Node baru butuh waktu ~30-60 detik sebelum status berubah jadi `Ready` (CNI/network plugin masih inisialisasi) — bukan selalu berarti error kalau sesaat masih `NotReady`.

Cek context aktif:
```bash
kubectl config current-context
```

---

## 💻 Langkah 2 — Pastikan Image ghcr.io Bisa Diakses

Karena manifest nanti menarik image dari `ghcr.io` (bukan image lokal), package harus berstatus **Public** — supaya `kind` bisa pull tanpa perlu setup `imagePullSecret` tambahan (topik untuk dipelajari terpisah nanti).

Cek/ubah di: `https://github.com/<username>?tab=packages` → pilih package → Package settings → Change visibility → Public.

---

## 💻 Langkah 3 — `k8s/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: account-service
  labels:
    app: account-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: account-service
  template:
    metadata:
      labels:
        app: account-service
    spec:
      containers:
        - name: account-service
          image: ghcr.io/hendraazka/account-service:latest
          ports:
            - containerPort: 8080
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 15
            periodSeconds: 5
```

**Insight:**
- `replicas: 2` — demonstrasi *high availability*: kalau 1 pod mati, masih ada 1 lagi yang tetap melayani traffic.
- `resources.requests/limits` — mencegah 1 container menghabiskan semua resource node; juga jadi salah satu hal yang diperiksa tools DevSecOps (kube-score) nanti.
- `livenessProbe`/`readinessProbe` memanfaatkan endpoint `/actuator/health` yang sudah disiapkan sejak Day 01.

---

## 💻 Langkah 4 — `k8s/service.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: account-service
spec:
  type: ClusterIP
  selector:
    app: account-service
  ports:
    - port: 8080
      targetPort: 8080
```

**Insight:** `selector: app: account-service` menghubungkan Service ke pod-pod yang dibuat Deployment (mencari pod dengan label yang cocok). `type: ClusterIP` berarti Service ini cuma bisa diakses dari dalam cluster — untuk testing dari laptop, perlu `kubectl port-forward`.

---

## 💻 Langkah 5 — Deploy

```bash
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

Cek status:
```bash
kubectl get pods
kubectl get svc
```

---

## 🔬 Verifikasi — Akses via Port-Forward

```bash
kubectl port-forward service/account-service 8080:8080
```

Di terminal lain:
```bash
curl http://localhost:8080/api/accounts
curl http://localhost:8080/actuator/health
```

**Hasil:** JSON yang sama seperti waktu testing di Day 01-03 — membuktikan seluruh siklus bekerja: kode → image di ghcr.io → benar-benar jalan di cluster Kubernetes, ditarik langsung dari registry.

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| Pod stuck status `Pending`, tidak kunjung `Running` | Node cluster berstatus `NotReady` (`untolerated taint node.kubernetes.io/unreachable`), kemungkinan gangguan koneksi Docker Desktop/WSL2 | Hapus cluster (`kind delete cluster --name devsecops-homelab`) dan buat ulang dari nol (`kind create cluster --name devsecops-homelab`) |
| Setelah cluster baru dibuat, node masih `NotReady` sesaat | Node baru butuh waktu untuk CNI selesai inisialisasi | Tunggu 30-60 detik, cek ulang `kubectl get nodes` |
| Perlu cek penyebab pod `Pending` | Event terbaru tidak langsung terlihat di `kubectl describe pod` | Gunakan `kubectl get events --sort-by='.lastTimestamp' | tail -20` untuk melihat riwayat event terurut waktu di seluruh cluster |

---

## 📌 Insight Penting

- Kubernetes = "manajer" yang otomatis menjaga kondisi aplikasi sesuai yang diinginkan (self-healing, scaling) — kamu deklarasikan kondisi yang diinginkan, Kubernetes yang urus caranya.
- `kind` menjalankan cluster Kubernetes sungguhan di dalam 1 container Docker — cocok untuk latihan di laptop dengan resource terbatas.
- Siklus DevOps end-to-end sudah lengkap: **push kode → CI test → CD build & push image → deploy ke Kubernetes → aplikasi terverifikasi bisa diakses**. Ini pondasi sebelum layer DevSecOps (Day 06 dst) ditambahkan di setiap titiknya.

---

[⬅️ Day 03](../day-03-cd-build-push-image/notes.md) | [⬅️ Kembali ke index](../README.md)
