# Day 13 — Pod Security Standards + OPA/Gatekeeper

[⬅️ Day 12](../day-12-review-devsecops/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 14](../day-14-iac-security-scanning/notes.md)

---

## ✅ Yang Dipelajari

- [x] Konsep **Admission Control** — validasi real-time saat resource dibuat, beda dengan kube-score yang statis (Day 10)
- [x] **Pod Security Standards (PSS)** — kebijakan bawaan Kubernetes (Privileged, Baseline, Restricted)
- [x] Cara mengaktifkan PSS lewat label namespace
- [x] Perbedaan `Warning` vs benar-benar `Forbidden`/blocked
- [x] **OPA/Gatekeeper** — admission controller untuk custom policy
- [x] Struktur 2 lapis Gatekeeper: `ConstraintTemplate` (cetakan aturan, pakai Rego) dan `Constraint` (aktivasi aturan)
- [x] Cara memverifikasi konfigurasi keamanan **benar-benar** aktif di pod nyata, bukan cuma asumsi dari tidak adanya error
- [x] 2 lapis admission control bisa aktif bersamaan dengan tingkat ketegasan berbeda (warning vs blocking)

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **Admission Control** | Satpam yang cek di pintu masuk, real-time | Validasi yang terjadi **saat** resource dibuat/diubah di cluster — beda dengan kube-score yang cuma laporan sebelum submit |
| **Pod Security Standards (PSS)** | Aturan gedung apartemen bawaan | 3 level kebijakan siap pakai dari Kubernetes: Privileged (bebas), Baseline (standar), Restricted (ketat) — tidak bisa dikustomisasi |
| **OPA/Gatekeeper** | Aturan RT/RW yang kamu buat sendiri | Tool tambahan yang memungkinkan menulis aturan **custom** sesuai kebutuhan spesifik, di luar apa yang dicover PSS |
| **ConstraintTemplate** | Cetakan/jenis peraturan baru | Definisi logika aturan (pakai bahasa Rego) — semacam mendaftarkan "jenis pelanggaran baru yang bisa dicek" |
| **Constraint** | Penerapan aturan itu ke target tertentu | Mengaktifkan ConstraintTemplate dengan parameter spesifik (resource apa yang kena aturan ini) |
| **Rego** | Bahasa untuk menulis logika aturan | Bahasa query khusus dipakai OPA untuk mendefinisikan kondisi pelanggaran |

**Kenapa PSS dan Gatekeeper dianggap pelengkap, bukan pengganti satu sama lain?**
PSS itu kebijakan **generik** bawaan Kubernetes (soal privilege, root access, dll) — cakupannya terbatas ke pola-pola umum. Gatekeeper memungkinkan aturan **spesifik** sesuai kebutuhan organisasi (misal "wajib resource limit", "image cuma boleh dari registry tertentu") yang tidak dicover PSS sama sekali.

---

## 💻 Bagian 1 — Pod Security Standards

### Langkah 1: Aktifkan level `baseline`

```bash
kubectl label namespace default pod-security.kubernetes.io/enforce=baseline
kubectl apply -f k8s/deployment.yaml
kubectl get pods
```

**Hasil:** lolos tanpa masalah — manifest yang sudah di-hardening sejak Day 10 (non-root user, `readOnlyRootFilesystem`, `allowPrivilegeEscalation: false`) sudah sesuai standar `baseline`.

### Langkah 2: Naikkan ke level `restricted` (paling ketat)

```bash
kubectl label namespace default pod-security.kubernetes.io/enforce=restricted --overwrite
kubectl rollout restart deployment account-service
```

**Hasil percobaan pertama — muncul Warning:**
```
Warning: would violate PodSecurity "restricted:latest": unrestricted capabilities
(container "account-service" must set securityContext.capabilities.drop=["ALL"]),
seccompProfile (pod or container "account-service" must set
securityContext.seccompProfile.type to "RuntimeDefault" or "Localhost")
```

**Insight penting:** `kubectl rollout restart` **tidak membuat pod benar-benar baru** dari nol kalau manifest tidak berubah — jadi warning muncul tapi pod lama tetap jalan. Validasi PodSecurity yang sesungguhnya baru benar-benar diuji saat ada **perubahan manifest** via `kubectl apply`.

### Langkah 3: Perbaiki manifest untuk memenuhi `restricted` penuh

```yaml
          securityContext:
            runAsNonRoot: true
            runAsUser: 10001
            runAsGroup: 10001
            readOnlyRootFilesystem: true
            allowPrivilegeEscalation: false
            capabilities:
              drop:
                - ALL
            seccompProfile:
              type: RuntimeDefault
```

**Insight tiap bagian baru:**
- `capabilities.drop: [ALL]` — melepas semua Linux capabilities (izin granular seperti bind port rendah, manipulasi jaringan) yang biasanya container dapat sebagian secara default; aplikasi kita tidak butuh privilege khusus apapun.
- `seccompProfile.type: RuntimeDefault` — mengaktifkan filter seccomp yang membatasi system call apa saja yang boleh dipanggil ke kernel Linux, mencegah container memanggil system call berbahaya meski aplikasi di dalamnya sudah dikompromikan.

```bash
kubectl apply -f k8s/deployment.yaml
```

**Hasil:** `deployment.apps/account-service configured` — **tidak ada baris Warning sama sekali**.

---

## 🔬 Verifikasi Ganda — Bukti Konkret, Bukan Asumsi

Penting untuk tidak percaya begitu saja "tidak ada error = aman". Dilakukan 2 verifikasi independen:

### Verifikasi 1: Output `kubectl apply` bersih dari `Warning:`
Dibandingkan langsung dengan percobaan sebelumnya yang menampilkan warning eksplisit — kali ini benar-benar tidak ada baris warning apapun.

### Verifikasi 2: Cek konfigurasi yang benar-benar tersimpan di pod nyata
```bash
kubectl get pod <nama-pod> -o yaml | grep -A 15 "securityContext:"
```

**Hasil:**
```yaml
securityContext:
  allowPrivilegeEscalation: false
  capabilities:
    drop:
    - ALL
  readOnlyRootFilesystem: true
  runAsGroup: 10001
  runAsNonRoot: true
  runAsUser: 10001
  seccompProfile:
    type: RuntimeDefault
```

**Insight:** ini pembuktian ganda — kalau konfigurasi ini tidak memenuhi syarat `restricted`, API server Kubernetes sudah pasti menolak pod ini dibuat sama sekali (bukan cuma warning). Kombinasi 2 bukti (output bersih + konfigurasi aktual di pod) memberi keyakinan solid, bukan sekadar "kelihatannya berhasil".

---

## 💻 Bagian 2 — OPA/Gatekeeper: Custom Policy

### Langkah 1: Install Gatekeeper

```bash
kubectl apply -f https://raw.githubusercontent.com/open-policy-agent/gatekeeper/master/deploy/gatekeeper.yaml
kubectl get pods -n gatekeeper-system
```

**Hasil:** semua komponen (`gatekeeper-audit`, `gatekeeper-controller-manager` x3) berhasil `Running`.

### Langkah 2: Buat ConstraintTemplate — aturan "wajib resource limit"

`opa-policies/require-resource-limits-template.yaml`:
```yaml
apiVersion: templates.gatekeeper.sh/v1
kind: ConstraintTemplate
metadata:
  name: requireresourcelimits
spec:
  crd:
    spec:
      names:
        kind: RequireResourceLimits
  targets:
    - target: admission.k8s.gatekeeper.sh
      rego: |
        package requireresourcelimits

        violation[{"msg": msg}] {
          container := input.review.object.spec.template.spec.containers[_]
          not container.resources.limits.cpu
          msg := sprintf("Container '%v' tidak punya resources.limits.cpu", [container.name])
        }

        violation[{"msg": msg}] {
          container := input.review.object.spec.template.spec.containers[_]
          not container.resources.limits.memory
          msg := sprintf("Container '%v' tidak punya resources.limits.memory", [container.name])
        }
```

**Insight:** bagian `rego:` adalah logika aturan dalam bahasa manusia: "untuk setiap container di Deployment, kalau tidak punya `resources.limits.cpu` ATAU tidak punya `resources.limits.memory`, tolak dengan pesan error yang jelas."

### Langkah 3: Buat Constraint — aktivasi aturan ke semua Deployment

`opa-policies/require-resource-limits-constraint.yaml`:
```yaml
apiVersion: constraints.gatekeeper.sh/v1beta1
kind: RequireResourceLimits
metadata:
  name: deployment-must-have-limits
spec:
  match:
    kinds:
      - apiGroups: ["apps"]
        kinds: ["Deployment"]
```

```bash
kubectl apply -f opa-policies/require-resource-limits-template.yaml
kubectl apply -f opa-policies/require-resource-limits-constraint.yaml
```

---

## 🔬 Uji Coba — Analisis Hasil

### Uji 1: Deployment TANPA resource limit (harus ditolak)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-no-limits
spec:
  replicas: 1
  selector:
    matchLabels:
      app: test-no-limits
  template:
    metadata:
      labels:
        app: test-no-limits
    spec:
      containers:
        - name: nginx
          image: nginx:alpine
```

```bash
kubectl apply -f /tmp/test-no-limits.yaml
```

**Hasil — 2 lapis pertahanan aktif bersamaan:**
```
Warning: would violate PodSecurity "restricted:latest": allowPrivilegeEscalation != false,
unrestricted capabilities, runAsNonRoot != true, seccompProfile ...

Error from server (Forbidden): error when creating "/tmp/test-no-limits.yaml":
admission webhook "validation.gatekeeper.sh" denied the request:
[deployment-must-have-limits] Container 'nginx' tidak punya resources.limits.cpu
[deployment-must-have-limits] Container 'nginx' tidak punya resources.limits.memory
```

**Analisis:**
- **PodSecurity** memberi **Warning** saja (soal privilege escalation, capabilities, dll) — image `nginx:alpine` polos belum dikonfigurasi security context.
- **Gatekeeper** memberi **Error, benar-benar menolak (`Forbidden`)** — Deployment ini **tidak pernah terbuat** di cluster sama sekali.

**Insight penting:** custom policy (Gatekeeper) dalam kasus ini bertindak lebih tegas (hard block) dibanding PSS yang di beberapa kondisi cuma memberi peringatan — menunjukkan custom policy bisa disetel sesuai kebutuhan ketegasan organisasi, tidak terbatas pada perilaku bawaan Kubernetes.

### Uji 2: Redeploy `account-service` (sudah punya resource limit sejak Day 04)

```bash
kubectl apply -f k8s/deployment.yaml
```

**Hasil:** `deployment.apps/account-service configured` — bersih tanpa error maupun warning. Lolos **kedua** lapis kebijakan sekaligus (PodSecurity `restricted` dan Gatekeeper `require-resource-limits`).

### Pembersihan resource test

```bash
kubectl delete -f /tmp/test-no-limits.yaml --ignore-not-found
```

Mengonfirmasi resource `test-no-limits` memang tidak pernah benar-benar tercipta di cluster sejak awal (ditolak Gatekeeper), bukan sesuatu yang perlu benar-benar dihapus.

---

## 🔧 Troubleshooting & Klarifikasi

| Situasi | Penjelasan |
|---|---|
| Muncul Warning saat `kubectl rollout restart`, tapi pod tetap jalan tanpa masalah | `rollout restart` tidak membuat pod benar-benar baru dari manifest yang sama — validasi PSS penuh baru teruji nyata saat ada perubahan manifest lewat `kubectl apply` |
| Ragu apakah manifest benar-benar sudah comply `restricted`, meski tidak ada error | Verifikasi ganda: (1) cek tidak ada baris `Warning:` di output `kubectl apply`, dan (2) cek langsung isi `securityContext` di pod nyata dengan `kubectl get pod -o yaml` — dua bukti independen lebih meyakinkan daripada asumsi tunggal |
| `kubectl delete` untuk resource test menunjukkan pesan seolah tidak ada apa-apa | Ini bukan bug — resource memang tidak pernah tercipta karena ditolak Gatekeeper sejak awal, `--ignore-not-found` mencegah error yang membingungkan |

---

## 📌 Insight Penting

- **Admission control adalah lapisan pertahanan real-time** — beda dari scanning statis (kube-score, Trivy, dll) yang sifatnya laporan sebelum/sesudah fakta, admission control benar-benar **mencegah** resource bermasalah masuk ke cluster sama sekali.
- **PSS dan Gatekeeper saling melengkapi**: PSS untuk baseline security generik (cepat diaktifkan, tanpa instalasi), Gatekeeper untuk aturan bisnis spesifik yang tidak dicover kebijakan bawaan.
- **Selalu verifikasi dengan bukti konkret**, bukan asumsi dari ketiadaan pesan error — cross-check output command dan kondisi aktual resource di cluster.
- **Custom policy bisa lebih tegas dari kebijakan bawaan** — Gatekeeper benar-benar block (Forbidden), sementara PSS di beberapa kondisi cuma memberi warning; pemahaman ini penting untuk merancang strategi governance cluster yang sesuai kebutuhan organisasi.
- Kerja keras hardening manifest sejak Day 10 (non-root, readOnlyRootFilesystem, dll) terbukti bernilai nyata — manifest `account-service` lolos comply penuh ke level keamanan Kubernetes paling ketat tanpa perlu perubahan besar tambahan.

---

[⬅️ Day 12](../day-12-review-devsecops/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 14](../day-14-iac-security-scanning/notes.md)
