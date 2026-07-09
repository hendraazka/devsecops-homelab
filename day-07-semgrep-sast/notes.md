# Day 07 — Semgrep: SAST (Static Application Security Testing)

[⬅️ Day 06](../day-06-gitleaks-secret-scanning/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 08](../day-08-trivy-dependency-scan/notes.md)

---

## ✅ Yang Dipelajari

- [x] Bedanya SAST dengan secret scanning — Semgrep mem-parse **struktur kode**, bukan cuma cocokkan string
- [x] Konsep *tainted data flow* — melacak data dari input user sampai ke fungsi berbahaya
- [x] Menambahkan job `sast-scan` paralel dengan job lain di `ci.yml`
- [x] Ruleset publik Semgrep (`p/java`) dan batasannya (tidak menangkap semua jenis masalah)
- [x] Kerentanan **OS Command Injection** — apa itu dan kenapa berbahaya
- [x] Cara memperbaiki command injection dengan `ProcessBuilder`

---

## 🧠 Konsep Kunci

| Istilah | Analogi | Penjelasan |
|---|---|---|
| **SAST** | Editor yang paham tata bahasa | Menganalisis struktur & logika kode (bukan cuma teks), mendeteksi pola pemrograman yang rawan |
| **Tainted data flow** | Melacak aliran air kotor | Melacak data dari sumber tidak terpercaya (input user) sampai ke tempat berbahaya (eksekusi command, query DB) |
| **Ruleset publik (`p/java`)** | Buku panduan standar | Kumpulan pattern rawan yang sudah dikurasi & dikenal luas secara industri (biasanya ada nomor CWE) |
| **OS Command Injection** | Menitip pesan berbahaya ke kurir | Input user disisipkan langsung ke command shell, attacker bisa menyisipkan perintah tambahan yang ikut dieksekusi server |
| **`ProcessBuilder`** | Formulir terstruktur vs surat bebas | Command dan argumen dipisah eksplisit sebagai array, bukan digabung jadi 1 string yang diserahkan ke shell |

**Kenapa SAST beda level dari secret scanning (Gitleaks)?**
Gitleaks cari string statis yang cocok pola. Semgrep benar-benar "mengerti" bahasa pemrograman — bisa melacak bagaimana data mengalir dari satu baris ke baris lain, bukan cuma menilai 1 baris secara terisolasi.

---

## 💻 Langkah 1 — Tambahkan Job `sast-scan` ke `ci.yml`

```yaml
  sast-scan:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout kode
        uses: actions/checkout@v4

      - name: Semgrep scan
        uses: returntocorp/semgrep-action@v1
        with:
          config: p/java
```

Push, verifikasi 3 job jalan paralel (`build-and-test`, `secret-scan`, `sast-scan`) — semua ✅ sukses di percobaan pertama (kode masih "bersih" dari pola yang dikenali ruleset).

---

## 🔬 Eksperimen 1 — Endpoint Tanpa Validasi Input (TIDAK Terdeteksi)

Endpoint `POST`/`DELETE` yang dibuat sejak Day 01 menerima input tanpa validasi apapun (nomor akun bisa kosong, saldo bisa negatif) — tapi Semgrep **tidak menandai ini sebagai temuan**.

**Insight penting:** ini bukan bug Semgrep, tapi **batasan nyata SAST berbasis ruleset generik**. Ruleset publik fokus pada pola teknis yang sudah dikenal luas (SQL injection, command injection, crypto lemah) — bukan *business logic* seperti "apakah field ini seharusnya divalidasi". SAST tools tidak tahu *intent* bisnis di balik kode. Ini kenapa DevSecOps tetap butuh **code review manual** dan **threat modeling**, tidak bisa 100% mengandalkan tools otomatis.

---

## 🔬 Eksperimen 2 — OS Command Injection (TERDETEKSI)

Ditambahkan endpoint baru yang sengaja rawan:

```java
@GetMapping("/ping/{host}")
public String pingHost(@PathVariable String host) throws IOException {
    Runtime.getRuntime().exec("ping -c 1 " + host);
    return "Pinging " + host;
}
```

**Kenapa berbahaya:** parameter `host` dari URL langsung disambung ke command shell tanpa sanitasi — attacker bisa mengirim `host` berisi perintah tambahan (misal `; rm -rf /`) yang ikut tereksekusi di server.

### Hasil scan Semgrep

```
1 Blocking Code Finding

java.spring.security.injection.tainted-system-command.tainted-system-command
  Detected user input entering a method which executes a system command.
  This could result in a command injection vulnerability...
  Instead, use ProcessBuilder, separating the command into individual
  arguments, like this: new ProcessBuilder("ls", "-al", targetDirectory)

  45┆ Runtime.getRuntime().exec("ping -c 1 " + host);

Found 1 finding (1 blocking) from 60 rules.
Has findings for blocking rules so exiting with code 1
```

**Insight:** output Semgrep sangat informatif — nama rule, lokasi baris persis, penjelasan risiko, **rekomendasi perbaikan konkret**, bahkan link dokumentasi lengkap. Job CI otomatis exit code 1 (gagal) karena ini masuk kategori *blocking rule*.

---

## 💻 Langkah 2 — Perbaikan Sesuai Rekomendasi

```java
ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", host);
pb.start();
```

**Kenapa ini lebih aman:** command dan argumen dipisah eksplisit sebagai array — tidak ada lagi proses "gabung jadi 1 string lalu diserahkan ke shell untuk di-parse ulang", yang merupakan akar masalah command injection.

**Catatan jujur:** perbaikan ini menghilangkan celah *command injection klasik*, tapi **belum 100% aman** — `host` masih belum divalidasi formatnya (misal memastikan hanya berisi karakter valid untuk hostname/IP). Validasi input tambahan tetap idealnya diterapkan sebagai lapisan pertahanan berikutnya (*defense in depth*).

Push ulang — job `sast-scan` kembali ✅ hijau.

---

## 🔧 Troubleshooting yang Dialami

Tidak ada kendala teknis berarti di hari ini — proses berjalan sesuai rencana dari percobaan pertama.

---

## 📌 Insight Penting

- SAST (Semgrep) dan secret scanning (Gitleaks) saling melengkapi, bukan menggantikan — masing-masing punya area deteksi berbeda.
- Ruleset publik SAST punya **batasan nyata**: bagus untuk pola teknis yang dikenal luas, tapi tidak bisa menilai *business logic* atau *intent* di balik kode. Validasi input tetap tanggung jawab developer, bukan sepenuhnya bisa diserahkan ke tools.
- Command injection adalah salah satu kerentanan paling berbahaya (masuk OWASP Top 10) — kuncinya selalu **pisahkan command dari data**, jangan pernah gabung input user langsung ke string command/query.
- Perbaikan berdasarkan temuan security tools sebaiknya tetap dipahami akar masalahnya (bukan cuma "ganti kode sampai tools diam") — di sini kita paham *kenapa* `ProcessBuilder` lebih aman, bukan cuma copy-paste solusi.

---

[⬅️ Day 06](../day-06-gitleaks-secret-scanning/notes.md) | [⬅️ Kembali ke index](../README.md) | [➡️ Day 08](../day-08-trivy-dependency-scan/notes.md)
