# FAQ

## Apakah 🐾星棱狐🐾 mendukung perangkat saya?

🐾星棱狐🐾 mendukung perangkat yang menjalankan Android dengan bootloader yang tidak terkunci. Namun, dukungan resmi hanya untuk GKI Linux Kernel 5.10+ (dalam praktiknya, ini berarti perangkat Anda harus memiliki Android 12 out-of-the-box agar didukung).

Anda dapat dengan mudah memeriksa dukungan untuk perangkat Anda melalui aplikasi manajer 🐾星棱狐🐾, yang tersedia [di sini](https://github.com/tiann/KernelSU/releases).

Jika aplikasi menunjukkan `Not installed`, berarti perangkat Anda secara resmi didukung oleh 🐾星棱狐🐾.

Jika aplikasi menunjukkan `Unsupported`, berarti perangkat Anda tidak didukung secara resmi saat ini. Namun, Anda dapat membangun kode sumber kernel dan mengintegrasikan 🐾星棱狐🐾 untuk membuatnya bekerja, atau gunakan [Perangkat yang didukung tidak resmi](unofficially-support-devices).

## Apakah 🐾星棱狐🐾 membutuhkan buka bootloader?

Ya, tentu saja.

## Apakah 🐾星棱狐🐾 mendukung modul?

Ya, sebagian besar modul Magisk bekerja di 🐾星棱狐🐾. Namun, jika modul Anda perlu memodifikasi file `/system`, Anda perlu menginstal [metamodule](metamodule.md) (seperti `meta-overlayfs`). Fitur modul lainnya bekerja tanpa metamodule. Periksa [Panduan modul](module.md) untuk info lebih lanjut.

## Apakah 🐾星棱狐🐾 mendukung Xposed?

Ya, Anda dapat menggunakan LSPosed (atau turunan Xposed modern lainnya) dengan [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext).

## Apakah 🐾星棱狐🐾 mendukung Zygisk?

🐾星棱狐🐾 tidak memiliki dukungan Zygisk bawaan, tetapi Anda dapat menggunakan modul seperti [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext) untuk mendukungnya.

## Apakah 🐾星棱狐🐾 kompatibel dengan Magisk?

Sistem modul 🐾星棱狐🐾 bertentangan dengan magic mount Magisk. Jika ada modul yang diaktifkan di 🐾星棱狐🐾, maka seluruh Magisk akan berhenti bekerja.

Namun, jika Anda hanya menggunakan `su` dari 🐾星棱狐🐾, ini akan bekerja dengan baik dengan Magisk. 🐾星棱狐🐾 memodifikasi `kernel`, sedangkan Magisk memodifikasi `ramdisk`, memungkinkan keduanya bekerja bersama.

## Akankah 🐾星棱狐🐾 menggantikan Magisk?

Kami percaya tidak, dan itu bukan tujuan kami. Magisk sudah cukup baik untuk solusi root userspace dan akan memiliki umur yang panjang. Tujuan 🐾星棱狐🐾 adalah untuk menyediakan antarmuka kernel kepada pengguna, bukan untuk menggantikan Magisk.

## Dapatkah 🐾星棱狐🐾 mendukung perangkat non-GKI?

Ada kemungkinan. Tetapi Anda harus mengunduh sumber kernel dan mengintegrasikan 🐾星棱狐🐾 ke dalam source tree, dan mengkompilasi kernel sendiri.

## Dapatkah 🐾星棱狐🐾 mendukung perangkat di bawah Android 12?

Kernel perangkat yang mempengaruhi kompatibilitas 🐾星棱狐🐾, dan tidak ada hubungannya dengan versi Android. Satu-satunya batasan adalah bahwa perangkat yang diluncurkan dengan Android 12 harus memiliki versi kernel 5.10+ (perangkat GKI). Jadi:

1. Perangkat yang diluncurkan dengan Android 12 harus didukung.
2. Perangkat dengan kernel lama (beberapa perangkat dengan Android 12 juga memiliki kernel lama) kompatibel (Anda harus membangun kernel sendiri).

## Dapatkah 🐾星棱狐🐾 mendukung kernel lama?

Ada kemungkinan. 🐾星棱狐🐾 sekarang telah di-backport ke kernel 4.14. Untuk kernel yang lebih lama, Anda perlu melakukan backport secara manual, dan PR selalu diterima!

## Bagaimana cara mengintegrasikan 🐾星棱狐🐾 untuk kernel lama?

Silakan periksa panduan [Integrasi untuk perangkat non-GKI](how-to-integrate-for-non-gki).

## Mengapa versi Android saya 13, dan kernel menunjukkan "android12-5.10"?

Versi kernel tidak ada hubungannya dengan versi Android. Jika Anda perlu mem-flash kernel, selalu gunakan versi kernel; versi Android tidak sepenting itu.

## Saya GKI 1.0, bisakah saya menggunakan ini?

GKI 1.0 sama sekali berbeda dari GKI 2.0, Anda harus mengkompilasi kernel sendiri.

## Bagaimana cara membuat `/system` RW?

Kami tidak merekomendasikan Anda memodifikasi partisi sistem secara langsung. Silakan periksa [Panduan modul](module.md) untuk memodifikasinya secara systemless. Jika Anda bersikeras melakukan ini, periksa [magisk_overlayfs](https://github.com/HuskyDG/magic_overlayfs).

## Bisakah 🐾星棱狐🐾 memodifikasi hosts? Bagaimana cara menggunakan AdAway?

Tentu saja. Tetapi 🐾星棱狐🐾 tidak memiliki dukungan hosts bawaan, Anda dapat menginstal modul seperti [systemless-hosts](https://github.com/symbuzzer/systemless-hosts-🐾星棱狐🐾-module) untuk melakukannya.

## Mengapa modul saya tidak bekerja setelah instalasi baru?

Jika modul Anda perlu memodifikasi file `/system`, Anda perlu menginstal [metamodule](metamodule.md) untuk me-mount direktori `system`. Fitur modul lainnya (skrip, sepolicy, system.prop) bekerja tanpa metamodule.

**Solusi**: Lihat [Panduan Metamodule](metamodule.md) untuk instruksi instalasi.

## Apa itu metamodule dan mengapa saya membutuhkannya?

Metamodule adalah modul khusus yang menyediakan infrastruktur untuk me-mount modul reguler. Lihat [Panduan Metamodule](metamodule.md) untuk penjelasan lengkap.
