# HEFT Cloud Workflow Simulation

> [!NOTE]
> **Dokumen Panduan & Hasil Analisis Lengkap**: Silakan buka berkas **[README_ANALISIS_DAN_TUTORIAL.md](file:///c:/Users/rayha/OneDrive/Documents/SEMESTER%205/1.%20Strategi%20Optimasi%20Komputasi%20Awan%20SOKA/1.%20Tugas/TUGAS%20HEFT/heft-simulation-master/README_ANALISIS_DAN_TUTORIAL.md)** untuk membaca analisis korelasi seluruh PDF tugas, penjelasan mendalam arsitektur dan algoritma HEFT, serta hasil uji coba live-run.

Simulator workflow cloud berbasis **CloudSim 7.0.1** untuk menguji algoritma **Heterogeneous Earliest Finish Time (HEFT)** pada lingkungan cloud yang memiliki resource heterogen.

Project ini merupakan implementasi untuk:

1. Membangun datacenter dan resource cloud sesuai desain eksperimen.
2. Menghasilkan mapping task-to-VM menggunakan HEFT.
3. Menjalankan workflow DAG pada CloudSim.
4. Mengukur makespan, pemanfaatan CPU, pemanfaatan RAM, konsumsi energi analitis, dan runtime scheduler.

## 1. Persyaratan Sistem

- Java Development Kit (JDK) 21 atau lebih baru.
- Apache Maven 3.8 atau lebih baru.
- Akses internet pada build pertama untuk mengunduh dependency Maven.

Cek instalasi:

```powershell
java -version
mvn -version
```

Jalankan semua perintah Maven dari folder project ini, yaitu `heft-simulation`.

## 2. Struktur Project

```text
heft-simulation/
|-- pom.xml
|-- README.md
|-- src/main/java/
|   |-- Main.java
|   |-- HEFTScheduler.java
|   |-- WorkflowBroker.java
|   |-- WorkflowDAG.java
|   |-- WorkflowTask.java
|   |-- WorkflowDatasetLoader.java
|   `-- SimulationResult.java
`-- src/main/resources/datasets/
    |-- montage.dax
    `-- cybershake.dax
```

Peran class utama:

| Class | Peran |
|---|---|
| `Main` | Inisialisasi CloudSim, datacenter, VM, workload, dan hasil eksperimen |
| `HEFTScheduler` | Menghitung upward rank, EFT, komunikasi, dan mapping task ke VM |
| `WorkflowDAG` | Model dan generator synthetic DAG |
| `WorkflowTask` | Model task, predecessor, successor, dan communication data |
| `WorkflowBroker` | Mengirim task sesuai dependency ke CloudSim |
| `WorkflowDatasetLoader` | Membaca workflow berformat DAX/XML |
| `SimulationResult` | Mencetak metrik hasil simulasi |

## 3. Desain Simulasi

### 3.1 Datacenter dan physical host

Simulator membuat dua datacenter:

- `Datacenter_0`: 4 host.
- `Datacenter_1`: 4 host.
- Total: 8 physical host heterogen.
- Host menggunakan variasi jumlah PE, MIPS per PE, RAM, bandwidth, dan storage.
- `VmAllocationPolicySimple` digunakan untuk alokasi VM.
- Host menggunakan `VmSchedulerTimeShared`.

Kapasitas host dibuat cukup untuk menguji skenario sampai 40 VM. Kegagalan alokasi di datacenter pertama dapat terjadi dan CloudSim akan mencoba datacenter berikutnya.

### 3.2 Virtual machine

VM dibuat heterogen dengan profil berulang berikut:

| VM profile | MIPS | vCPU | RAM |
|---:|---:|---:|---:|
| 1 | 1000 | 1 | 2 GB |
| 2 | 1200 | 1 | 2 GB |
| 3 | 1500 | 1 | 2 GB |
| 4 | 1800 | 1 | 2 GB |
| 5 | 2000 | 2 | 4 GB |
| 6 | 2200 | 2 | 4 GB |
| 7 | 2500 | 2 | 4 GB |
| 8 | 2800 | 2 | 8 GB |
| 9 | 3000 | 2 | 8 GB |
| 10 | 3500 | 4 | 8 GB |

Jumlah VM yang direkomendasikan untuk eksperimen adalah 10, 20, dan 40.

### 3.3 Task dan cloudlet

Setiap `WorkflowTask` dikonversi menjadi satu CloudSim `Cloudlet`.

- Synthetic task memiliki instruction length 5.000 sampai 50.000 MI.
- Synthetic edge memiliki communication data 10 sampai 100 MB.
- Cloudlet menggunakan `UtilizationModelFull`.
- Cloudlet memakai VM hasil mapping HEFT, bukan memilih VM secara acak.

## 4. Implementasi HEFT

HEFT memiliki dua tahap.

### 4.1 Upward rank

Untuk setiap task, scheduler menghitung:

```text
rank_u(i) = average_computation(i)
            + max(average_communication(i,j) + rank_u(j))
```

Task diurutkan berdasarkan `rank_u` secara descending. Karena synthetic DAG dibuat dengan edge dari ID kecil ke ID besar, graph selalu acyclic.

### 4.2 Processor selection

Untuk setiap task dan setiap VM:

```text
EST = waktu task siap berdasarkan predecessor dan availability VM
EFT = EST + execution time pada VM
```

VM dengan EFT terkecil dipilih. Scheduler juga menerapkan insertion policy agar task dapat ditempatkan pada gap kosong di jadwal VM.

Communication model:

```text
communication delay = data size / 100 MB/s
```

Jika predecessor dan successor berada pada VM yang sama, communication delay dianggap nol. Jika berbeda VM, broker menunda pengiriman cloudlet successor sesuai delay tersebut.

## 5. Workload

### 5.1 Synthetic DAG

Synthetic DAG dibuat deterministic menggunakan seed `2026`, sehingga eksperimen dapat direproduksi.

Ukuran yang digunakan:

- 100 task.
- 250 task.
- 500 task.

Generator membuat predecessor secara acak dengan maksimum tiga parent per task dan memastikan setiap task non-entry memiliki minimal satu predecessor.

### 5.2 Montage dan CyberShake

File berikut tersedia untuk pengujian:

- `src/main/resources/datasets/montage.dax`
- `src/main/resources/datasets/cybershake.dax`

File yang disertakan adalah **dataset contoh kecil untuk smoke test**, bukan full benchmark resmi Montage atau CyberShake. Untuk eksperimen penelitian, file tersebut dapat diganti dengan DAX resmi selama struktur XML dan atribut yang diperlukan tetap tersedia.

Loader DAX menggunakan:

- atribut `job id` sebagai identitas task;
- atribut `job runtime` untuk membentuk instruction length;
- elemen `child` dan `parent` untuk dependency;
- ukuran file output parent sebagai communication data.

Runtime DAX dikonversi menjadi instruction length dengan pendekatan:

```text
instruction length (MI) = runtime * 1000
```

## 6. Cara Build

Compile dan membersihkan hasil build sebelumnya:

```powershell
mvn clean compile
```

Jika berhasil, Maven menampilkan `BUILD SUCCESS`.

## 7. Cara Menjalankan Simulator

Format umum:

```powershell
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=<workload> <taskCount> <vmCount>"
```

Argumen:

| Argumen | Nilai | Keterangan |
|---|---|---|
| `workload` | `synthetic`, `montage`, `cybershake` | Jenis workload |
| `taskCount` | minimal 2 | Jumlah task untuk synthetic; diabaikan secara praktis oleh DAX |
| `vmCount` | 1 sampai 40 | Jumlah VM |

Tanpa argumen, simulator menjalankan synthetic 100 task pada 10 VM:

```powershell
mvn exec:java "-Dexec.mainClass=Main"
```

Contoh workload synthetic:

```powershell
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=synthetic 100 10"
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=synthetic 250 20"
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=synthetic 500 40"
```

Contoh workload DAX:

```powershell
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=montage 4 10"
mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=cybershake 5 10"
```

## 8. Matriks Eksperimen

Untuk mereplikasi rancangan eksperimen utama:

| Workload | Task | VM |
|---|---:|---:|
| Synthetic DAG | 100 | 10, 20, 40 |
| Synthetic DAG | 250 | 10, 20, 40 |
| Synthetic DAG | 500 | 10, 20, 40 |
| Montage DAX | sesuai file | 10, 20, 40 |
| CyberShake DAX | sesuai file | 10, 20, 40 |

Jalankan setiap kombinasi secara terpisah dan catat hasil pada bagian `FINAL SIMULATION RESULT`.

Contoh loop PowerShell untuk synthetic workload:

```powershell
foreach ($taskCount in 100, 250, 500) {
    foreach ($vmCount in 10, 20, 40) {
        mvn exec:java "-Dexec.mainClass=Main" "-Dexec.args=synthetic $taskCount $vmCount"
    }
}
```

## 9. Output dan Metrik

Output utama simulator mencakup:

- `Completed Tasks`: jumlah task yang berhasil selesai.
- `HEFT Makespan`: makespan berdasarkan jadwal HEFT.
- `CloudSim Actual`: makespan aktual dari event CloudSim.
- `Makespan Diff`: selisih estimasi HEFT dan hasil CloudSim.
- `CPU Utilization`: estimasi utilisasi CPU berdasarkan busy time task.
- `RAM Utilization`: estimasi utilisasi RAM berbasis RAM-time.
- `Energy`: estimasi konsumsi energi dalam Joule.
- `HEFT Runtime`: waktu komputasi scheduler dalam milidetik.
- `Invalid Dependency Order`: jumlah dependency yang dilanggar pada validasi jadwal.

Kriteria validasi dasar:

```text
Completed Tasks == total tasks
Invalid Dependency Order == 0
Unassigned Tasks == 0
```

## 10. Reproducibility dan Pencatatan Hasil

Untuk setiap eksperimen, catat minimal:

```text
workload
task count
VM count
seed
edge count
entry/exit task count
makespan
energy
CPU utilization
RAM utilization
HEFT runtime
completed task count
invalid dependency count
```

Synthetic workload reproducible selama seed `2026` dan konfigurasi resource tidak diubah.

## 11. Batasan Implementasi Saat Ini

- Dataset Montage dan CyberShake yang ada di repository adalah contoh kecil untuk pengujian format dan alur simulasi.
- Workflow DAX hanya membaca atribut dan elemen yang digunakan oleh loader saat ini; variasi DAX yang kompleks mungkin membutuhkan perluasan parser.
- Deadline workflow belum diterapkan sebagai constraint scheduling.
- Bandwidth digunakan untuk menghitung communication delay, tetapi belum dimodelkan sebagai transfer jaringan yang bersaing secara simultan.
- Konsumsi energi masih berupa model analitis berbasis waktu eksekusi task dan daya idle/full-load; belum menggunakan model power host dinamis dari setiap event CloudSim.
- CPU dan RAM utilization merupakan estimasi agregat, bukan trace resource utilization per time step.
- HEFT digunakan sebagai baseline heuristic dan belum dibandingkan dengan algoritma lain seperti list scheduling sederhana, Min-Min, atau metode multi-objective.

## 12. Interpretasi Hasil

Jumlah VM yang lebih besar tidak selalu menurunkan makespan karena:

- struktur dependency dapat membatasi paralelisme;
- task dengan rank tinggi dapat menjadi critical path;
- communication delay dapat meningkat ketika task berpindah VM;
- kapasitas host dan placement VM memengaruhi resource yang benar-benar tersedia.

Energy dan makespan perlu dianalisis bersama. Makespan kecil belum otomatis berarti energy kecil, terutama jika lebih banyak host aktif atau utilisasi resource meningkat.

## 13. Troubleshooting

### Maven tidak menemukan POM

Pastikan current directory berada di `heft-simulation`:

```powershell
Set-Location D:\SOKA\heft-simulation
mvn clean compile
```

### Workload tidak dikenali

Nilai workload harus salah satu dari:

```text
synthetic
montage
cybershake
```

### Dataset DAX tidak ditemukan

Jalankan Maven dari folder `heft-simulation`, bukan dari folder `D:\SOKA`, karena path dataset dibaca relatif terhadap current directory.

### VM belum terdaftar pada datacenter

Periksa jumlah VM dan kapasitas host. Untuk skenario standar gunakan jumlah VM 10, 20, atau 40 dan jangan menghapus resource host tanpa menyesuaikan kapasitas provisioning.

## 14. Pengembangan Lanjutan

Pengembangan yang disarankan untuk tahap berikutnya:

1. Menambahkan full dataset DAX resmi Montage dan CyberShake.
2. Menyimpan hasil eksperimen otomatis ke CSV.
3. Menambahkan deadline dan validasi deadline workflow.
4. Mengambil host placement aktual dari CloudSim untuk model energi yang lebih realistis.
5. Menambahkan baseline scheduler untuk perbandingan.
6. Menjalankan beberapa seed dan replikasi eksperimen untuk memperoleh rata-rata serta standar deviasi.
