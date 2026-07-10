# Day 10 — kube-score: Kubernetes Manifest Scan

[⬅️ Day 09](../day-09-trivy-image-scan/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 11](../day-11-owasp-zap-dast/notes.md)

---

## ✅ Yang Dipelajari

- [x] Kenapa manifest Kubernetes perlu di-scan — misconfiguration adalah sumber insiden umum di runtime cluster
- [x] kube-score — analisis statis manifest YAML tanpa perlu cluster aktif
- [x] Security context: non-root user, `readOnlyRootFilesystem`, `allowPrivilegeEscalation`
- [x] Liveness vs readiness probe harus pakai endpoint berbeda (bukan sekadar path berbeda, tapi state yang beda secara konsep)
- [x] Konsep **risk acceptance** yang terdokumentasi — tidak semua temuan security harus/bisa diperbaiki
- [x] Kendala arsitektur nyata: GitHub Actions (cloud) tidak bisa akses cluster kind (lokal) — kenapa ArgoCD/Flux dirancang bekerja terbalik (pull dari dalam cluster)
- [x] Pola "CD auto-update manifest + commit `[skip ci]`" — GitOps-lite tanpa infrastruktur kompleks
- [x] Troubleshooting nyata: probe timing yang terlalu ketat menyebabkan `CrashLoopBackOff`
- [x] Cara membaca `kubectl describe pod` dan `kubectl logs` untuk mendiagnosis pod bermasalah

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **kube-score** | Inspektur bangunan baca cetak biru | Analisis statis manifest YAML berdasarkan best practice, tanpa perlu cluster aktif |
| **`readOnlyRootFilesystem`** | Ruangan yang tidak bisa dicoret-coret | Filesystem container jadi read-only; attacker yang berhasil masuk tidak bisa menulis file berbahaya |
| **Risk acceptance** | Keputusan sadar terima risiko tertentu | Mendokumentasikan alasan kenapa temuan tertentu tidak diperbaiki, bukan diam-diam diabaikan |
| **`[skip ci]`** | Tanda "jangan diperiksa lagi" | Mencegah commit otomatis dari CD memicu CI/CD baru — menghindari infinite loop |
| **GitOps (ArgoCD/Flux)** | Cluster yang "menjemput" perubahan sendiri | Tools diinstall DI DALAM cluster, cluster yang aktif cek (pull) repo Git — menghindari masalah cluster lokal tidak bisa diakses dari luar |
| **`CrashLoopBackOff`** | Orang yang terus pingsan-bangun berulang | Container terus dimatikan & di-restart Kubernetes karena dianggap gagal probe/health check, sebelum sempat benar-benar stabil |

**Kenapa GitHub Actions tidak bisa langsung `kubectl apply` ke cluster kind kita?**
Cluster kind jalan di laptop lokal (privat, tidak terhubung internet), sedangkan GitHub Actions runner adalah komputer cloud milik GitHub — dia tidak bisa "masuk" ke jaringan lokal laptop. Ini alasan arsitektur nyata kenapa GitOps tools (ArgoCD/Flux) dirancang bekerja terbalik: instal di dalam cluster, cluster yang pull dari Git, bukan Git yang push ke cluster.

---

## 💻 Langkah 1 — Tambahkan Job `k8s-manifest-scan`

```yaml
  k8s-manifest-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout kode
        uses: actions/checkout@v4

      - name: Download kube-score
        run: |
          curl -Lo kube-score https://github.com/zegl/kube-score/releases/download/v1.19.0/kube-score_1.19.0_linux_amd64
          chmod +x kube-score

      - name: Scan manifest Kubernetes
        run: ./kube-score score k8s/*.yaml
```

**Insight:** kube-score belum punya GitHub Action resmi seperti Gitleaks/Semgrep/Trivy — jadi kita download binary-nya langsung dari GitHub Releases, pola umum untuk tools yang belum terintegrasi penuh.

---

## 🔬 Temuan Percobaan Pertama — 7 Critical + 1 Warning

| Temuan | Keputusan |
|---|---|
| Security Context User/Group ID | ✅ Perbaiki |
| ReadOnlyRootFilesystem | ✅ Perbaiki |
| Image Tag `latest` | ✅ Perbaiki (dengan catatan khusus) |
| Liveness = Readiness Probe sama | ✅ Perbaiki |
| Ephemeral Storage limit/request | ✅ Perbaiki |
| Pod NetworkPolicy | ⏸️ Diterima (butuh CNI khusus, tidak applicable single-node) |
| PodDisruptionBudget | ⏸️ Diterima (relevan multi-node) |
| PodAntiAffinity | ⏸️ Diterima (relevan multi-node) |

---

## 💻 Langkah 2 — Perbaikan Probe Terpisah (Spring Boot Actuator)

```bash
cd ~/devsecops-homelab/account-service
nano src/main/resources/application.properties
```

Tambahkan:
```properties
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

**Insight:** ini mengaktifkan 2 endpoint resmi terpisah: `/actuator/health/liveness` dan `/actuator/health/readiness` — bukan sekadar bikin path berbeda secara manual, tapi memanfaatkan state management resmi Spring Boot Actuator (liveness = app hidup, readiness = app siap terima traffic termasuk dependency).

Push perubahan ini dulu (supaya image baru dibuild dengan endpoint probe baru):
```bash
cd ~/devsecops-homelab
git add .
git commit -m "day 10: add separate liveness/readiness probe endpoints"
git push
```

---

## 💻 Langkah 3 — Perbaikan `deployment.yaml` (Security Context, Storage)

```yaml
          securityContext:
            runAsNonRoot: true
            runAsUser: 10001
            runAsGroup: 10001
            readOnlyRootFilesystem: true
            allowPrivilegeEscalation: false
          volumeMounts:
            - name: tmp
              mountPath: /tmp
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
              ephemeral-storage: "256Mi"
            limits:
              memory: "512Mi"
              cpu: "500m"
              ephemeral-storage: "512Mi"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 45
            periodSeconds: 10
            failureThreshold: 5
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
            failureThreshold: 5
      volumes:
        - name: tmp
          emptyDir: {}
```

`Dockerfile` (UID/GID harus konsisten dengan manifest):
```dockerfile
RUN addgroup -g 10001 -S appgroup && adduser -u 10001 -S appuser -G appgroup
```

**Insight tiap bagian:**
- `runAsUser/runAsGroup: 10001` — kube-score merekomendasikan UID/GID **di atas 10000** untuk menghindari konflik dengan user sistem di host. Percobaan pertama pakai `1000` masih kena temuan "low user ID", baru lolos setelah dinaikkan ke `10001`.
- `volumeMounts` + `emptyDir` untuk `/tmp` — konsekuensi wajib dari `readOnlyRootFilesystem: true`; aplikasi Java butuh folder writable untuk file sementara, disediakan lewat volume terpisah tanpa membuka seluruh filesystem.
- `failureThreshold: 5` dan `initialDelaySeconds` diperbesar — antisipasi startup Spring Boot yang tidak selalu konsisten cepatnya (ditemukan lewat troubleshooting nyata, lihat bagian bawah).

---

## 💻 Langkah 4 — CD Auto-update Manifest (GitOps-lite)

```yaml
      - name: Update tag image di manifest Kubernetes
        run: |
          sed -i "s|image: ghcr.io/${{ github.repository_owner }}/account-service:.*|image: ghcr.io/${{ github.repository_owner }}/account-service:${{ github.event.workflow_run.head_sha }}|" k8s/deployment.yaml

      - name: Commit dan push perubahan manifest
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add k8s/deployment.yaml
          git diff --staged --quiet && echo "Tidak ada perubahan" || git commit -m "chore: update k8s manifest image tag to ${{ github.event.workflow_run.head_sha }} [skip ci]"
          git push origin HEAD:main
```

Permission job juga perlu dinaikkan:
```yaml
    permissions:
      contents: write   # sebelumnya 'read'
      packages: write
```

**Insight:** setelah image lolos scan & push, CD otomatis update tag di manifest dan commit balik ke repo — mendekati konsep GitOps, tapi `kubectl apply` tetap manual (karena GitHub Actions cloud tidak bisa akses cluster lokal).

**Terverifikasi bekerja:** commit otomatis dari `github-actions[bot]` muncul dengan pesan `[skip ci]`, dan **tidak** memicu run CI/CD baru — mekanisme pencegahan infinite loop berhasil. Dicek langsung di halaman commits GitHub: commit itu tidak punya centang status run (✓ N/N) seperti commit manual lainnya.

---

## 💻 Langkah 5 — Risk Acceptance untuk Temuan yang Tidak Applicable

```yaml
      - name: Scan manifest Kubernetes
        run: |
          ./kube-score score k8s/*.yaml \
            --ignore-test pod-networkpolicy \
            --ignore-test deployment-has-poddisruptionbudget \
            --ignore-test deployment-has-host-podantiaffinity \
            --ignore-test container-image-tag
```

**Alasan tiap pengecualian (didokumentasikan, bukan diam-diam diabaikan):**
- `pod-networkpolicy`, `deployment-has-poddisruptionbudget`, `deployment-has-host-podantiaffinity` — relevan untuk cluster **multi-node** production; cluster kind kita single-node, rekomendasi ini tidak applicable.
- `container-image-tag` — trade-off sadar dari desain pipeline: CI scan manifest **sebelum** CD sempat update tag ke SHA commit (untuk mencegah infinite loop dari `[skip ci]`), sehingga CI selalu melihat tag `latest` di titik itu meski manifest final di repo sudah benar.

---

## 🔬 Troubleshooting Lengkap — Deploy ke Cluster Setelah Semua Perbaikan

Setelah `k8s-manifest-scan` hijau di CI, saatnya deploy manifest final ke cluster lokal secara manual.

### Masalah 1 — Node cluster `NotReady`, ada pod sisa `CrashLoopBackOff` 37 jam

```bash
kubectl get pods
# NAME                               READY   STATUS             RESTARTS      AGE
# account-service-54f9c456f6-4hdb2   0/1     CrashLoopBackOff   6 (24h ago)   37h
# account-service-54f9c456f6-8p8mn   0/1     CrashLoopBackOff   7 (24h ago)   37h
# account-service-56bff5678-m9zs9    0/1     Pending             0             62s

kubectl get nodes
# devsecops-homelab-control-plane   NotReady   control-plane   41h   v1.33.1
```

**Penyebab:** Docker Desktop sempat update/restart di Day 06-09, cluster kind jadi tidak sehat dan belum sempat diperbaiki sejak itu — ada pod "sisa" berumur 37 jam yang macet.

**Solusi — hapus cluster total, buat ulang dari nol:**
```bash
kind delete cluster --name devsecops-homelab
docker ps
kind create cluster --name devsecops-homelab
kubectl get nodes
# tunggu sampai status Ready
```

Deploy ulang manifest yang sudah bersih dari sisa masalah lama:
```bash
cd ~/devsecops-homelab
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl get pods
kubectl describe pod -l app=account-service | grep -E "Image:|Run As"
```

### Masalah 2 — Pod `Running` tapi `READY 0/1` terus-menerus, restart berkali-kali

```bash
kubectl get pods
# account-service-675b944b88-72bd7   0/1   Running   3 (57s ago)   6m11s

kubectl logs account-service-675b944b88-72bd7
# log berhenti tepat setelah "No active profile set" - tidak sampai
# "Tomcat started", tanpa exception tercetak

kubectl describe pod account-service-675b944b88-72bd7
# Events:
#   Warning  Unhealthy  Liveness probe failed: connection refused
#   Warning  Unhealthy  Readiness probe failed: connection refused
#   Normal   Killing    Container failed liveness probe, will be restarted
```

**Penyebab:** `initialDelaySeconds` liveness probe (30s, nilai awal) terlalu cepat — Spring Boot 3.5.14 (hasil upgrade Day 08) belum selesai startup saat probe pertama dicek. Probe gagal → Kubernetes anggap container rusak → dimatikan paksa sebelum sempat boot sempurna → siklus berulang terus.

**Solusi:** perbesar `initialDelaySeconds` (45s) dan `failureThreshold` (5) di kedua probe (lihat Langkah 3 di atas), lalu terapkan ulang:
```bash
kubectl apply -f k8s/deployment.yaml
# tunggu ~60 detik untuk rolling update selesai
kubectl get pods
# READY 1/1, RESTARTS tidak bertambah lagi
```

### Verifikasi Akhir — Semua Berfungsi

```bash
kubectl port-forward service/account-service 8080:8080
```
```bash
curl http://localhost:8080/api/accounts
curl http://localhost:8080/actuator/health/liveness
curl http://localhost:8080/actuator/health/readiness
```

**Hasil:** data akun muncul normal, kedua endpoint probe `{"status":"UP"}` — pembuktian lengkap bahwa `readOnlyRootFilesystem`, non-root user, dan probe terpisah semuanya berfungsi tanpa merusak aplikasi.

---

## 🔧 Ringkasan Troubleshooting

| Masalah | Penyebab | Solusi |
|---|---|---|
| Trivy/kube-score awalnya menolak UID `1000` | kube-score merekomendasikan UID/GID di atas 10000 untuk hindari konflik dengan user sistem host | Naikkan ke `10001` di Dockerfile dan manifest secara konsisten |
| Node cluster `NotReady`, pod sisa 37 jam `CrashLoopBackOff` | Docker Desktop sempat update/restart di hari-hari sebelumnya, cluster belum diperbaiki | `kind delete cluster` lalu `kind create cluster` dari nol |
| Pod `Running` tapi `READY 0/1` terus, restart berkali-kali | `initialDelaySeconds` probe (30s) terlalu cepat untuk startup Spring Boot 3.5.14 | Perbesar `initialDelaySeconds` (45s) dan `failureThreshold` (5) |
| `curl`/`port-forward` gagal konek | Konsekuensi dari pod `CrashLoopBackOff` di atas — tidak ada proses stabil mendengarkan port 8080 | Perbaiki dulu root cause (probe timing), baru port-forward bisa konek andal |

---

## 📌 Insight Penting

- Manifest yang "terlihat sudah baik" (ada resource limit, ada probe) ternyata masih jauh dari standar production sungguhan menurut kube-score — pentingnya scan otomatis dibanding cuma mengandalkan penilaian visual.
- **Tidak semua temuan security harus diperbaiki** — kadang keputusan yang benar adalah menerima risiko tertentu dengan alasan jelas dan terdokumentasi (risk acceptance), terutama untuk rekomendasi yang butuh infrastruktur di luar skala kita (multi-node cluster).
- Realita jaringan penting dipahami: cloud CI/CD runner **tidak otomatis** bisa menjangkau infrastruktur lokal — ini alasan arsitektural nyata di balik desain tools GitOps seperti ArgoCD/Flux.
- Upgrade versi framework (Spring Boot 3.3.x → 3.5.14 di Day 08) bisa berdampak ke perilaku runtime yang tidak terduga (waktu startup) — perubahan di satu hari bisa memunculkan masalah baru di hari lain, pentingnya testing menyeluruh setelah setiap perubahan besar.
- `kubectl describe pod` (bagian Events) dan `kubectl logs` adalah 2 command pertama yang harus dicek setiap kali pod bermasalah — describe untuk lihat "apa yang Kubernetes coba lakukan dan kenapa gagal", logs untuk lihat "apa yang terjadi di dalam aplikasi itu sendiri".
- Ini praktik jujur DevSecOps: implementasi teknis di homelab disederhanakan dibanding production sungguhan (repo config terpisah, ArgoCD, multi-node), tapi prinsip dan cara berpikirnya (shift-left, defense in depth, dokumentasi risk acceptance, debugging sistematis) tetap representatif.

---

[⬅️ Day 09](../day-09-trivy-image-scan/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 11](../day-11-owasp-zap-dast/notes.md)
