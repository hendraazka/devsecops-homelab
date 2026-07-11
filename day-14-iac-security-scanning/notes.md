# Day 14 — IaC Security Scanning (tfsec + Checkov)

[⬅️ Day 13](../day-13-pss-opa-gatekeeper/notes.md) | [⬅️ Kembali ke index](../README.md)

---

## ✅ Yang Dipelajari

- [x] Kenapa IaC (Infrastructure as Code) perlu discan — misconfiguration cloud sama bahayanya dengan kerentanan kode
- [x] tfsec vs Checkov — dua tools analisis statis Terraform dengan cakupan yang saling melengkapi
- [x] Analisis statis IaC **tidak butuh** kredensial cloud aktif — cukup baca file `.tf`
- [x] Risk acceptance terdokumentasi untuk temuan yang tidak relevan/berlebihan untuk skala homelab
- [x] Investigasi mendalam: GitHub Action wrapper pihak ketiga (`tfsec-action`) ternyata **tidak meneruskan exit code dengan benar**
- [x] Solusi: pakai binary CLI langsung untuk kontrol penuh atas exit code (pola yang sama seperti kube-score, Day 10)
- [x] `--exclude` (tfsec) dan `skip_check` (Checkov) untuk pengecualian eksplisit yang terdokumentasi

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **IaC Security Scanning** | Cek cetak biru bangunan sebelum dibangun | Analisis statis terhadap kode infrastruktur (Terraform, dll), menemukan misconfiguration sebelum benar-benar di-provision ke cloud |
| **tfsec** | Inspektur cepat, spesialis Terraform | Fokus khusus Terraform, sangat cepat (hitungan milidetik), sedang dimigrasikan ke ekosistem Trivy |
| **Checkov** | Inspektur menyeluruh, multi-format | Cakupan lebih luas (Terraform, CloudFormation, K8s, Dockerfile), lebih verbose, dari Prisma Cloud |
| **Risk acceptance terdokumentasi** | Keputusan sadar, tertulis, beralasan | Mengecualikan temuan tertentu dengan ID spesifik dan alasan jelas — bukan mematikan seluruh pemeriksaan |
| **Exit code tidak diteruskan action wrapper** | Alarm yang bunyi tapi kabelnya putus ke sirine utama | Tool sebenarnya mendeteksi masalah dan keluar dengan kode error, tapi wrapper/action di sekelilingnya "menyerap" sinyal itu sehingga job tetap dianggap sukses |

**Kenapa IaC scanning penting, khususnya soal security group dan S3?**
Kesalahan konfigurasi cloud seperti security group terbuka ke `0.0.0.0/0` atau S3 bucket public adalah penyebab breach yang sangat umum di dunia nyata — seringkali lebih sering terjadi dibanding kerentanan di kode aplikasi itu sendiri, karena kesalahannya "tersembunyi" di file konfigurasi yang jarang direview seketat kode aplikasi.

---

## 💻 Langkah 1 — Contoh Terraform dengan Misconfiguration Sengaja

`terraform/s3-and-security-group/main.tf` (versi awal):
```hcl
resource "aws_s3_bucket_public_access_block" "app_data" {
  bucket = aws_s3_bucket.app_data.id
  block_public_acls       = false
  block_public_policy     = false
  ignore_public_acls      = false
  restrict_public_buckets = false
}

resource "aws_security_group" "app_sg" {
  ingress {
    from_port   = 22
    to_port     = 22
    cidr_blocks = ["0.0.0.0/0"]
  }
  # ... port 8080 juga 0.0.0.0/0, egress juga 0.0.0.0/0
}
```

**Insight:** analisis statis IaC tidak butuh `terraform apply` sungguhan atau kredensial AWS — cukup file `.tf` untuk dianalisis.

---

## 🔬 Hasil Scan Awal — tfsec

```
Result #1-3 CRITICAL: Security group rule allows ingress/egress from/to 0.0.0.0/0 (SSH, port 8080, egress)
Result #4-7 HIGH: Public access block tidak memblokir ACL/policy public
Result #8-9 HIGH: Bucket tidak ada enkripsi, tidak ada customer-managed key
Result #10-11 MEDIUM: Bucket tidak ada logging, tidak ada versioning
Result #12 LOW: Security group rule tidak ada description

passed: 5, critical: 3, high: 6, medium: 2, low: 1
5 passed, 12 potential problem(s) detected.
```

## 🔬 Hasil Scan Awal — Checkov

```
Passed checks: 8, Failed checks: 15, Skipped checks: 0
```

Termasuk temuan unik yang tidak dicover tfsec:
```
CKV2_AWS_5: "Ensure that Security Groups are attached to another resource"
FAILED for resource: aws_security_group.app_sg
```

**Insight perbandingan:** tfsec menangkap 12 masalah dalam 6ms (sangat cepat, fokus pola teknis eksplisit), Checkov menangkap 15 masalah dengan cakupan lebih luas termasuk kelengkapan konfigurasi (seperti security group yang tidak di-attach ke resource apapun). Kedua tools saling melengkapi, tidak 100% identik cakupannya — praktik umum di dunia kerja memakai lebih dari satu scanner IaC untuk cakupan lebih lengkap.

---

## 💻 Langkah 2 — Perbaikan Manifest

```hcl
resource "aws_s3_bucket_public_access_block" "app_data" {
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "app_data" {
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
  }
}

resource "aws_s3_bucket_versioning" "app_data" {
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_logging" "app_data" {
  target_bucket = aws_s3_bucket.app_data.id
  target_prefix = "log/"
}

resource "aws_security_group" "app_sg" {
  ingress {
    description = "SSH from office IP only"
    cidr_blocks = ["203.0.113.10/32"]
  }
  ingress {
    description = "App port from internal VPC only"
    cidr_blocks = ["10.0.0.0/16"]
  }
  egress {
    description = "Allow HTTPS outbound only"
    from_port   = 443
    to_port     = 443
    cidr_blocks = ["0.0.0.0/0"]
  }
}
```

**Insight tiap perbaikan:** public access block semua `true`, enkripsi KMS ditambahkan, versioning & logging diaktifkan, CIDR dipersempit ke IP/range spesifik (bukan lagi terbuka global), setiap rule diberi description.

---

## 🔬 Hasil Scan Setelah Perbaikan

**tfsec:** 12 → **2 temuan** (1 Critical: egress HTTPS masih `0.0.0.0/0`; 1 High: masih pakai AWS-managed key bukan customer-managed key)

**Checkov:** 15 failed → **4 failed** (event notifications, lifecycle configuration, security group tidak di-attach ke resource, cross-region replication)

### Analisis Risk Acceptance

| Temuan | Tool | Diterima? | Alasan |
|---|---|---|---|
| Egress HTTPS ke `0.0.0.0/0` | tfsec | ✅ | Wajar untuk outbound HTTPS (API eksternal, download dependency) — tujuan bisa ke mana saja, tidak bisa dipersempit ke IP spesifik |
| Bukan customer-managed key | tfsec | ✅ | Butuh resource `aws_kms_key` terpisah + kelola rotation sendiri — kompleksitas berlebihan untuk homelab |
| Event notifications | Checkov | ✅ | Fitur tambahan (trigger Lambda dll) — tidak relevan untuk kasus penyimpanan data sederhana |
| Lifecycle configuration | Checkov | ✅ | Optimasi biaya, bukan soal keamanan langsung |
| SG tidak di-attach ke resource | Checkov | ✅ | Konsekuensi konteks homelab — security group didefinisikan ilustratif, tidak benar-benar attach ke EC2 sungguhan |
| Cross-region replication | Checkov | ✅ | Disaster recovery skala enterprise, berlebihan (dan mahal) untuk homelab |

---

## 💻 Langkah 3 — Integrasi ke CI (dengan 1 investigasi penting di tengah jalan)

### Percobaan Pertama — Pakai GitHub Action Wrapper (`tfsec-action`)

```yaml
      - name: tfsec scan
        uses: aquasecurity/tfsec-action@v1.0.3
        with:
          working_directory: terraform/s3-and-security-group
          soft_fail: false
```

**Hasil janggal:** job `iac-scan` **hijau/sukses**, padahal 2 temuan (egress, customer-managed key) belum di-exclude apapun — seharusnya gagal.

### Investigasi — Cek Log Detail, Bukan Percaya Status Checkmark Saja

Log step "tfsec scan" di-expand, ditemukan baris:
```
15 passed, 2 potential problem(s) detected.
```

**Kesimpulan:** tfsec **benar-benar mendeteksi** 2 masalah, tapi `tfsec-action@v1.0.3` **tidak meneruskan exit code dengan benar** ke status job GitHub Actions — job tetap dianggap sukses meski tool di dalamnya melaporkan temuan. Ini bug/limitation nyata dari action wrapper pihak ketiga tersebut.

### Solusi — Pakai Binary CLI Langsung (Konsisten dengan Pola kube-score, Day 10)

```yaml
      - name: Checkout kode
        uses: actions/checkout@v4

      - name: Install tfsec
        run: |
          curl -s https://raw.githubusercontent.com/aquasecurity/tfsec/master/scripts/install_linux.sh | bash

      - name: tfsec scan
        run: |
          tfsec terraform/s3-and-security-group/ \
            --exclude aws-ec2-no-public-egress-sgr,aws-s3-encryption-customer-key

      - name: Checkov scan
        uses: bridgecrewio/checkov-action@master
        with:
          directory: terraform/s3-and-security-group
          skip_check: CKV2_AWS_62,CKV2_AWS_61,CKV2_AWS_5,CKV_AWS_144
          quiet: true
```

**Kendala tambahan saat perbaikan:** sempat lupa menyertakan kembali step "Checkout kode" saat mengganti dari action ke CLI — tanpa checkout, folder `terraform/` tidak akan ada sama sekali di runner.

**Hasil setelah perbaikan lengkap:**
```
passed: 15, ignored: 2, critical: 0, high: 0
No problems detected!
```

`ignored: 2` mengonfirmasi kedua exclusion terbaca dan diterapkan dengan benar — bukan "tidak ada temuan sama sekali", tapi temuan yang diterima risikonya secara eksplisit dan tercatat transparan.

---

## 🔧 Ringkasan Troubleshooting

| Masalah | Penyebab | Solusi |
|---|---|---|
| Job `iac-scan` hijau padahal tfsec menemukan 2 masalah | `tfsec-action@v1.0.3` tidak meneruskan exit code tfsec ke status job GitHub Actions dengan benar | Ganti ke binary CLI langsung (`curl` install + `run: tfsec ...`), exit code diteruskan native oleh shell |
| Step "Checkout kode" hilang setelah edit config | Tidak sengaja terhapus saat mengganti step action jadi CLI manual | Tambahkan kembali secara eksplisit sebagai step pertama job |
| File aneh bernama `-o` muncul di direktori | Kemungkinan flag `-o` pada suatu command tertulis/tereksekusi terpisah dari command aslinya, membuat shell menginterpretasikannya sebagai nama file | Diperiksa dengan `cat -- -o` (tanda `--` memaksa argumen setelahnya dibaca sebagai nama file, bukan flag) sebelum dihapus |

---

## 📌 Insight Penting

- **Jangan percaya status checkmark hijau begitu saja** — selalu buka dan baca log detail, terutama saat memakai GitHub Action wrapper pihak ketiga yang perilakunya tidak selalu transparan atau terdokumentasi dengan baik.
- **Binary CLI langsung lebih dapat diandalkan** untuk kontrol exit code dibanding action wrapper — pola ini konsisten dipilih sejak kube-score (Day 10) dan terbukti lagi solid di sini.
- **IaC scanning tidak butuh infrastruktur cloud aktif** — analisis statis terhadap file `.tf` cukup untuk menangkap misconfiguration signifikan sebelum resource benar-benar di-provision, mencegah biaya dan risiko yang jauh lebih besar.
- **Dua tools dengan tujuan serupa (tfsec, Checkov) tetap saling melengkapi** — cakupan yang tidak 100% sama membuat kombinasi keduanya lebih kuat daripada mengandalkan satu tool saja.
- Risk acceptance yang baik selalu **spesifik by ID** (`--exclude`, `skip_check`) dan **beralasan jelas** — bukan mematikan seluruh scan atau severity level secara membabi buta.

---

[⬅️ Day 13](../day-13-pss-opa-gatekeeper/notes.md) | [⬅️ Kembali ke index](../README.md)
