# Day 08 — Trivy (Filesystem Mode): Dependency/SCA Scan

[⬅️ Day 07](../day-07-semgrep-sast/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 09](../day-09-trivy-image-scan/notes.md)

---

## ✅ Yang Dipelajari

- [x] Konsep **SCA (Software Composition Analysis)** — mengecek CVE di dependency, bukan di kode sendiri
- [x] Trivy filesystem mode untuk scan `pom.xml` (termasuk transitive dependency)
- [x] Perbedaan antara temuan CVE nyata vs error infrastruktur (rate limit Maven Central)
- [x] `spring-boot-starter-parent` mengunci versi dependency turunan — 1 upgrade bisa perbaiki banyak CVE sekaligus
- [x] Kadang upgrade **patch version** tidak cukup, perlu upgrade **minor/major version**
- [x] Cara override versi dependency transitive lewat `<properties>` tanpa deklarasi manual

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **SCA (Software Composition Analysis)** | Cek tanggal kadaluarsa bahan masakan | Mengecek apakah bahan (library) yang dipakai punya "resep berbahaya" (CVE) yang sudah diketahui publik |
| **Transitive dependency** | Bahan dari bahan | Library yang otomatis ikut terbawa karena dependency lain membutuhkannya, bukan yang kita tulis manual |
| **CVE (Common Vulnerabilities and Exposures)** | Nomor laporan polisi | ID standar industri untuk 1 kerentanan spesifik yang sudah diverifikasi dan didokumentasikan publik |
| **`spring-boot-starter-parent`** | Paket menu lengkap dengan harga tetap | Mengunci versi semua dependency turunan supaya saling kompatibel — upgrade versi parent bisa perbaiki banyak CVE sekaligus |
| **Severity filter (Critical/High)** | Prioritas triase UGD | Fokus ke yang paling berbahaya dulu, supaya tidak tenggelam di "alert fatigue" dari ratusan temuan Low/Medium |

**Kenapa dependency scanning penting?**
Sebagian besar breach besar di dunia nyata bukan dari kode yang ditulis sendiri, tapi dari library pihak ketiga yang versinya sudah punya CVE dikenal publik dan lupa di-update (contoh terkenal: Log4Shell 2021).

---

## 💻 Langkah 1 — Tambahkan Job `dependency-scan` (Percobaan Pertama, Gagal Infrastruktur)

```yaml
  dependency-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout kode
        uses: actions/checkout@v4

      - name: Trivy filesystem scan
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: './account-service'
          severity: 'CRITICAL,HIGH'
          exit-code: '1'
```

**Hasil percobaan pertama:** gagal, tapi bukan karena CVE — melainkan error infrastruktur:
```
FATAL Error remote Maven repository returned 429 Too Many Requests
for https://repo.maven.apache.org/...
The repository blocks all subsequent requests from this IP until the block clears.
```

**Insight:** Trivy filesystem mode untuk Java perlu resolve dependency tree lengkap dari Maven Central kalau belum ada cache lokal — GitHub Actions runner IP kena rate limit karena dipakai bersama banyak orang lain.

---

## 💻 Langkah 2 — Perbaikan: Populate Maven Cache Sebelum Scan

```yaml
  dependency-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout kode
        uses: actions/checkout@v4

      - name: Setup Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'

      - name: Resolve dependencies (populate Maven cache)
        run: mvn -B dependency:resolve
        working-directory: ./account-service

      - name: Trivy filesystem scan
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: 'fs'
          scan-ref: './account-service'
          severity: 'CRITICAL,HIGH'
          exit-code: '1'
```

**Insight:** `mvn dependency:resolve` men-download semua `.pom`/`.jar` ke cache lokal (`~/.m2`) sebelum Trivy scan — Trivy tinggal baca dari cache, tidak perlu hit Maven Central lagi. `cache: 'maven'` di `setup-java` juga bikin run berikutnya lebih cepat karena GitHub Actions menyimpan cache antar-run.

---

## 🔬 Temuan CVE — Percobaan 1 (Sebelum Upgrade)

**Total: 27 kerentanan (4 Critical, 23 High)**, semuanya di transitive dependency bawaan `spring-boot-starter-parent:3.3.0`:

| Library | Contoh CVE | Severity |
|---|---|---|
| `tomcat-embed-core` 10.1.24 | CVE-2025-24813 (Potential RCE) | CRITICAL |
| `jackson-databind` 2.17.1 | CVE-2026-54512 (Arbitrary code execution) | HIGH |
| `spring-boot` 3.3.0 | CVE-2026-40973 (RCE & Session Hijacking) | HIGH |
| `spring-core` 6.1.8 | CVE-2025-41249 (Annotation Detection Vuln) | HIGH |
| `spring-webmvc` | CVE-2024-38816/38819 (Path Traversal) | HIGH |

**Insight:** kita cuma menulis 3 dependency manual di `pom.xml` (`web`, `actuator`, `test`) — semua 27 temuan ini berasal dari *transitive dependency* yang otomatis terbawa.

---

## 💻 Langkah 3 — Perbaikan Tahap 1: Upgrade Patch Version

```xml
<version>3.3.13</version>  <!-- dari 3.3.0 -->
```

**Hasil:** 27 → **18 kerentanan** (3 Critical, 15 High). Berkurang signifikan, tapi belum nol.

**Insight:** upgrade versi *patch* (angka ketiga) di garis yang sama masih membawa perbaikan besar, tapi garis `3.3.x` punya batas — beberapa CVE cuma fix di versi Tomcat/Spring Core/Spring Boot yang **tidak tersedia** di garis `3.3.x` sama sekali.

---

## 💻 Langkah 4 — Perbaikan Tahap 2: Upgrade Minor Version + Override Eksplisit

```xml
<parent>
    ...
    <version>3.5.14</version>  <!-- dari 3.3.13 -->
</parent>

<properties>
    <java.version>17</java.version>
    <tomcat.version>10.1.55</tomcat.version>
    <jackson-bom.version>2.18.8</jackson-bom.version>
</properties>
```

**Hasil:** 18 → **0 kerentanan**. `dependency-scan` sukses, dan `build-and-test` tetap sukses (tidak ada breaking change yang berdampak ke kode sederhana kita).

**Insight:** Spring Boot menyediakan properti override resmi (`tomcat.version`, `jackson-bom.version`, dll) untuk memaksa versi dependency transitive tertentu tanpa perlu deklarasi `<dependency>` manual — pola yang direkomendasikan Spring Boot sendiri untuk kasus seperti ini.

---

## 🔧 Troubleshooting yang Dialami

| Masalah | Penyebab | Solusi |
|---|---|---|
| Trivy scan gagal: `429 Too Many Requests` dari Maven Central | Rate limit di IP shared GitHub Actions runner, belum ada cache Maven lokal | Tambah step `mvn dependency:resolve` + `cache: 'maven'` di `setup-java` sebelum Trivy scan |
| Masih ada 18 CVE tersisa setelah upgrade patch version (3.3.0 → 3.3.13) | Beberapa CVE cuma fix di versi Tomcat/Spring Core/Spring Boot yang tidak tersedia di garis 3.3.x | Upgrade ke garis minor version lebih baru (3.5.14) + override versi Tomcat/Jackson eksplisit di `<properties>` |

---

## 📌 Insight Penting

- Sebagian besar risiko keamanan aplikasi datang dari **dependency**, bukan kode sendiri — inilah kenapa SCA scanning sama pentingnya dengan SAST.
- **1 baris versi parent** bisa mengendalikan puluhan CVE sekaligus — memahami struktur dependency management Spring Boot sangat berharga untuk maintenance jangka panjang.
- Tidak semua CVE bisa diperbaiki dengan upgrade patch version kecil — kadang perlu keputusan sadar untuk upgrade minor/major version, dengan risiko breaking change yang perlu ditest ulang (`build-and-test` tetap harus hijau setelah upgrade).
- Selalu bedakan **temuan security sungguhan** vs **error infrastruktur** (seperti rate limit) — jangan buru-buru menganggap semua kegagalan CI sebagai masalah kode.

---

[⬅️ Day 07](../day-07-semgrep-sast/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 09](../day-09-trivy-image-scan/notes.md)
