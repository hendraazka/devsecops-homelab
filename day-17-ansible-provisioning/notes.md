# Day 17 — Ansible: Provisioning Otomatis

[⬅️ Day 16](../day-16-jenkins-migration/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 18](../day-18-argocd-gitops/notes.md)

---

## ✅ Yang Dipelajari

- [x] Kenapa Ansible relevan setelah Day 16 — mengubah command manual jadi playbook yang bisa diulang
- [x] Konsep **idempotency** — playbook aman dijalankan berkali-kali, hasil konsisten
- [x] Inventory dengan `ansible_connection=docker` — mengelola container langsung tanpa SSH
- [x] Struktur playbook: `hosts`, `tasks`, module (`apt`, `group`, `user`, `get_url`)
- [x] Perbedaan `become: true` (sudo) dengan `ansible_user=root` (koneksi langsung sebagai root)
- [x] Bonus: pemulihan nyata pasca-restart laptop (cluster kind baru, update kubeconfig Jenkins)
- [x] Ansible murni CLI — filosofi "automation as code", GUI (AWX) adalah lapisan terpisah opsional

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **Idempotency** | Menyalakan lampu yang sudah menyala | Menjalankan aksi yang sama berkali-kali menghasilkan kondisi akhir yang sama, tidak ada efek samping dari pengulangan |
| **Playbook** | Resep masakan tertulis | File YAML berisi daftar task yang mendefinisikan "kondisi akhir yang diinginkan" pada sistem target |
| **Inventory** | Daftar tamu undangan | File yang mendaftar target mana saja (host) yang mau dikelola Ansible, dan cara menghubunginya |
| **`ansible_connection=docker`** | Masuk lewat pintu belakang, bukan pintu depan | Ansible terhubung ke container langsung lewat Docker API (`docker exec`), bukan SSH seperti target server biasa |
| **`become` vs `ansible_user=root`** | Minta izin naik jabatan vs sudah jadi bos dari awal | `become` (sudo) untuk naik privilege di tengah sesi; `ansible_user=root` untuk koneksi yang sudah root sejak awal — cocok untuk container minimal tanpa `sudo` |

**Kenapa Ansible relevan setelah Day 16?**
Setup manual Jenkins di Day 16 (install Maven, Docker CLI, fix GID, kubectl, kind) melibatkan banyak command berurutan yang rawan lupa/salah urutan kalau harus diulang. Ansible mengubah proses itu jadi 1 file yang bisa dijalankan ulang kapan saja dengan hasil yang bisa diandalkan.

---

## 💻 Langkah 1 — Install Ansible

```bash
sudo apt update
sudo apt install -y ansible
ansible --version
```

---

## 💻 Langkah 2 — Inventory dan Playbook

`ansible/inventory.ini`:
```ini
[jenkins_servers]
jenkins ansible_connection=docker ansible_user=root
```

`ansible/setup-jenkins-tools.yml`:
```yaml
---
- name: Setup tools yang dibutuhkan Jenkins untuk pipeline
  hosts: jenkins_servers
  gather_facts: false

  tasks:
    - name: Update apt cache
      apt:
        update_cache: true

    - name: Install Maven
      apt:
        name: maven
        state: present

    - name: Install Docker CLI
      apt:
        name: docker.io
        state: present

    - name: Pastikan grup docker ada dengan GID yang benar
      group:
        name: docker
        gid: 1001
        state: present

    - name: Tambahkan user jenkins ke grup docker
      user:
        name: jenkins
        groups: docker
        append: true

    - name: Install kubectl
      get_url:
        url: "https://dl.k8s.io/release/v1.31.0/bin/linux/amd64/kubectl"
        dest: /usr/local/bin/kubectl
        mode: '0755'

    - name: Install kind
      get_url:
        url: "https://kind.sigs.k8s.io/dl/v0.29.0/kind-linux-amd64"
        dest: /usr/local/bin/kind
        mode: '0755'
```

**Insight tiap bagian:**
- `gather_facts: false` — mempercepat eksekusi, tidak perlu info detail sistem untuk playbook sederhana ini.
- Module `apt`, `group`, `user`, `get_url` semuanya **idempotent** secara bawaan — inti kekuatan Ansible dibanding sekadar script bash.
- Task "Pastikan grup docker ada dengan GID yang benar" (`gid: 1001`) secara langsung mereplikasi perbaikan manual Day 16, tapi sekarang terdokumentasi sebagai kode, bukan command yang dijalankan lalu dilupakan.

---

## 🔧 Troubleshooting

### Masalah 1 — `sudo: not found`

```
fatal: [jenkins]: FAILED! => module_stderr: "/bin/sh: 1: sudo: not found\n"
```

**Penyebab:** `become: true` (versi awal playbook) defaultnya memakai `sudo` untuk naik ke root — tapi container Jenkins berbasis Debian minimal tidak punya `sudo` terinstall (konsisten dengan pelajaran Day 16: container minimal itu "kosong").

**Solusi:** ganti pendekatan — hapus `become: true`, gunakan `ansible_user=root` di inventory supaya Ansible connect langsung sebagai root (setara `docker exec -u root` yang dipakai manual di Day 16).

### Masalah 2 — Warning nama grup dan host sama

```
[WARNING]: Found both group and host with same name: jenkins
```

**Penyebab:** grup `[jenkins]` dan host `jenkins` di inventory memakai nama identik.

**Solusi:** ganti nama grup jadi `jenkins_servers`, dipisahkan jelas dari nama host `jenkins`.

### Bonus — Pemulihan Nyata Pasca-Restart Laptop

Di tengah proses Day 17, laptop sempat direstart, memicu skenario pemulihan nyata:

1. `docker ps -a` menunjukkan container `jenkins` berstatus `Exited`, cluster kind sempat `NotReady` lalu di-recreate.
2. Cluster baru mendapat **IP internal Docker berbeda** (`172.19.0.2` → `172.19.0.3`) dan **port API server berbeda** (`39979` → `41861`).
3. Kubeconfig Jenkins perlu di-generate ulang menyesuaikan alamat baru:
```bash
docker start jenkins
docker network connect kind jenkins
docker cp ~/.kube/config jenkins:/tmp/kubeconfig-original
docker exec -u root jenkins bash -c "sed 's|https://127.0.0.1:41861|https://172.19.0.3:6443|' /tmp/kubeconfig-original > /var/jenkins_home/.kube-config"
```
4. Manifest `k8s/deployment.yaml` dan `service.yaml` di-apply ulang karena cluster baru = state kosong.

**Insight:** ini pengalaman nyata soal *disaster recovery* skala kecil — setiap kali cluster kind dibuat ulang, alamat jaringannya berubah, dan segala sesuatu yang bergantung pada alamat lama (kubeconfig Jenkins) perlu disesuaikan.

---

## 🔬 Pembuktian Idempotency

### Percobaan 1 — Jalankan playbook saat semua tools sudah terinstall

```bash
ansible-playbook -i ansible/inventory.ini ansible/setup-jenkins-tools.yml
```

**Hasil:**
```
PLAY RECAP
jenkins : ok=7    changed=0    unreachable=0    failed=0
```

Semua 7 task sukses, tapi **tidak ada satupun `changed`** — Ansible mendeteksi semua tools sudah sesuai kondisi yang diinginkan, tidak melakukan instalasi ulang yang sia-sia.

### Percobaan 2 — Sengaja hapus 1 tool, jalankan ulang

```bash
docker exec -u root jenkins bash -c "rm -f /usr/local/bin/kind"
ansible-playbook -i ansible/inventory.ini ansible/setup-jenkins-tools.yml
```

**Hasil:**
```
TASK [Install kind]
changed: [jenkins]

PLAY RECAP
jenkins : ok=7    changed=1    unreachable=0    failed=0
```

**Insight:** hanya task "Install kind" yang berubah status jadi `changed`, 6 task lainnya tetap `ok` — bukti nyata Ansible secara akurat mendeteksi **kondisi aktual** sistem dan hanya bertindak pada bagian yang benar-benar perlu diperbaiki.

---

## 🖥️ Catatan: GUI untuk Ansible (Opsional, Tidak Diimplementasikan)

Ansible inti murni CLI — filosofinya "automation as code": playbook sebagai file YAML yang bisa di-Git, direview lewat pull request, dan punya history perubahan. Ada beberapa produk terpisah yang menambahkan lapisan visual di atasnya:

| Tools | Keterangan |
|---|---|
| **AWX** | Versi open-source gratis dari Red Hat, dashboard web untuk kelola playbook, jadwalkan run, lihat history. Instalasi via AWX Operator, **butuh cluster Kubernetes sendiri** (deploy PostgreSQL, Redis, beberapa pod sekaligus) |
| **Red Hat Ansible Automation Platform (AAP)** | Versi berbayar/enterprise dari AWX, dipakai perusahaan besar dengan dukungan resmi |
| **Semaphore** | Alternatif GUI yang jauh lebih ringan — cukup 1 container Docker + database ringan, tidak butuh cluster Kubernetes terpisah |

### Apakah bisa diimplementasikan di homelab ini?

**AWX** — secara teknis *bisa* (cluster kind kita sudah mendukung), tapi **tidak disarankan** untuk skala homelab saat ini. AWX butuh resource signifikan (disarankan minimal 4GB RAM khusus untuknya), sementara laptop kita sudah menjalankan beban cukup padat sekaligus (cluster kind, Jenkins, `account-service`, Gatekeeper) — dan sudah beberapa kali mengalami masalah CPU throttling (Day 11) akibat keterbatasan resource. Menambah AWX berisiko memperparah masalah performa yang sudah pernah dialami.

**Semaphore** — jauh **lebih realistis** kalau suatu saat ingin mencoba GUI. Instalasinya simpel (mirip pola install Jenkins/SonarQube yang sudah kita lakukan), tidak butuh cluster Kubernetes terpisah, dan resource footprint-nya kecil.

**Keputusan untuk homelab ini:** tetap pakai CLI murni — ini juga cara paling umum dipakai di dunia kerja untuk skala penggunaan kecil-menengah; GUI biasanya baru dipertimbangkan ketika sudah ada puluhan/ratusan playbook yang dikelola banyak orang sekaligus.

---

## 📌 Insight Penting

- **Idempotency bukan sekadar teori** — terbukti nyata lewat 2 percobaan berurutan: kondisi normal (`changed=0`) vs kondisi rusak sebagian (`changed=1` tepat pada bagian yang rusak).
- **Container minimal sering tidak punya tools "dasar"** yang dianggap selalu ada di server biasa (seperti `sudo`) — penting menyesuaikan pendekatan (`ansible_user=root`) daripada memaksakan asumsi lama.
- **Ansible murni CLI dengan alasan filosofis** — playbook sebagai kode yang bisa di-Git, direview, dan diaudit; GUI (AWX) tersedia sebagai lapisan terpisah untuk skala penggunaan yang jauh lebih besar, tidak dibutuhkan untuk homelab.
- **Infrastruktur lokal (kind, Docker) rapuh terhadap restart** — alamat jaringan berubah setiap kali komponen dibuat ulang; pemulihan yang sistematis (cek status, sesuaikan konfigurasi terkait) lebih baik daripada asumsi "harusnya otomatis pulih sendiri".
- Playbook yang dibuat hari ini punya nilai praktis nyata — kalau container Jenkins rusak/hilang lagi di masa depan, pemulihan tools-nya tinggal 1 command, bukan mengulang belasan command manual seperti Day 16.

---

[⬅️ Day 16](../day-16-jenkins-migration/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 18](../day-18-argocd-gitops/notes.md)
