# PrayerTimeCalculater_Appinventor_Kodular_Extension
Kalkulator Waktu Sholat (Fajr, Dhuhr, Asr, Maghrib, Isha, Dhuha, Kulminasi, Terbit/Tenggelam) dan Arah Kiblat. Mendukung preset, sudut kustom, inisialisasi sekali, dan perhitungan individual.

Cara Menggunakan Ekstensi Ini di MIT App Inventor/Kodular/Sejenis

    Kompilasi: Kode Java di atas harus dikompilasi ke dalam file .aix (Ekstensi App Inventor). Anda memerlukan lingkungan pengembangan Java (seperti Android Studio atau command line ant) dan App Inventor Sources/SDK untuk melakukan kompilasi.
        Catatan: Saya hanya menyediakan kode sumber Java, Anda harus mengkompilasinya sendiri di luar lingkungan ini.
        
    Impor di App Inventor:
        Di proyek App Inventor Anda, buka palet "Extensions" dan klik "Import extension".
        Unggah file .aix yang telah Anda kompilasi.
        
    Penggunaan di Blok:
        Ekstensi akan muncul di palet Anda sebagai PrayerTimeCalculator.
        Gunakan blok fungsi CalculateAllTimes dengan 6 input: latitude, longitude, timezone, day, month, dan year.
        Fungsi ini akan mengembalikan sebuah Daftar (List) dengan 10 item dalam urutan berikut:
            Waktu Fajr (HH:MM)
            Waktu Sunrise (Terbit Matahari) (HH:MM)
            Waktu Dhuha (HH:MM)
            Waktu Dhuhr (HH:MM)
            Waktu Asr (HH:MM)
            Waktu Maghrib (HH:MM)
            Waktu Sunset (Tenggelam Matahari) (HH:MM)
            Waktu Isha (HH:MM)
            Waktu Midday (Kulminasi) (HH:MM)
            Arah Qibla (Derajat dari Utara, 0-360)
            
Anda juga dapat menggunakan fungsi terpisah calculateQibla jika hanya membutuhkan arah kiblat.

Kode ini menggunakan metode perhitungan MWL dengan sudut yang telah ditentukan dan faktor Asr Shafii. Jika Anda ingin mengubah metode (misalnya, ke ISNA, Karachi, atau lain-lain), Anda perlu memodifikasi nilai FAJR_ANGLE, ISHA_ANGLE, dan ASR_FACTOR di bagian awal kode.
