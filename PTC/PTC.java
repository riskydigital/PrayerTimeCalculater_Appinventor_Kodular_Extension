package com.riskydigital.PTC; // Sesuai dengan nama paket utama

import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleObject;
import com.google.appinventor.components.annotations.DesignerComponent;
import com.google.appinventor.components.annotations.UsesPermissions;
import com.google.appinventor.components.annotations.Options;      
import com.google.appinventor.components.common.ComponentCategory;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.util.YailList;

// Import Helper Enums dari paket baru
import com.riskydigital.PTC.helpers.PrayerMethods; 
import com.riskydigital.PTC.helpers.HighLatAdjustment; 
import com.riskydigital.PTC.helpers.TimeFormat; // Import baru

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;


/**
 * Ekstensi untuk menghitung waktu sholat, waktu matahari, dan arah Kiblat.
 * Versi ini mendukung inisialisasi state sekali di awal dan perhitungan waktu individual, serta kalkulasi bulanan dan tahunan.
 */
@DesignerComponent(
    version = 121, // Versi dinaikkan untuk kontrol format waktu
    description = "Kalkulator Waktu Sholat (Fajr, Dhuhr, Asr, Maghrib, Isha, Dhuha, Kulminasi, Terbit/Tenggelam) dan Arah Kiblat. Mendukung preset, sudut kustom, inisialisasi sekali, perhitungan individual, bulanan, dan tahunan, serta penyesuaian lintang tinggi. Akurasi waktu dapat dipilih (HH:MM atau HH:MM:SS).",
    category = ComponentCategory.EXTENSION,
    nonVisible = true,
    iconName = "images/extension.png")
@SimpleObject(external = true)
@UsesPermissions(permissionNames = "android.permission.INTERNET")
public class PTC extends AndroidNonvisibleComponent { 
    
    // --- Variabel State Perhitungan ---
    private static final double SUNRISE_SUNSET_ANGLE = 0.833; 
    private static final double DHUHA_ANGLE = 3.0; 

    // Variabel yang digunakan dalam perhitungan (nilai default adalah MWL)
    private double currentFajrAngle = 18.0; 
    private double currentIshaAngle = 17.0;
    private int currentAsrFactor = 1; // 1=Shafii (default), 2=Hanafi
    private String currentMethod = PrayerMethods.MWL.toUnderlyingValue(); 
    private String currentHighLatAdj = HighLatAdjustment.NONE.toUnderlyingValue(); // Default: Tidak ada penyesuaian
    private String currentOutputFormat = TimeFormat.HH_MM_SS.toUnderlyingValue(); // Default: Akurasi hingga detik
    
    // State yang diatur sekali oleh SetLocationDateAndInit atau CalculateAllTimes
    private double lat = Double.NaN;
    private double lon = Double.NaN;
    private double timezoneOffset = Double.NaN;
    private int day = 1, month = 1, year = 1970;
    private Calendar calendar = new GregorianCalendar(1970, 0, 1);

    // --- Konstruktor ---

    public PTC(ComponentContainer container) { 
        super(container.$form());
    }

    // --- Fungsi Bantuan Matematika dan Waktu (Helper Functions) ---

    /** Konversi sudut ke radian. */
    private double degToRad(double angle) {
        return Math.PI * angle / 180.0;
    }

    /** Konversi radian ke sudut. */
    private double radToDeg(double angle) {
        return angle * 180.0 / Math.PI;
    }

    /** Memperbaiki sudut agar berada di antara 0 dan 360. */
    private double fixAngle(double a) {
        a = a - 360.0 * Math.floor(a / 360.0); 
        a = a < 0 ? a + 360.0 : a;
        return a;
    }
    
    /** Memperbaiki waktu agar berada di antara 0 dan 24. */
    private double fixTime(double t) {
        t = t - 24.0 * Math.floor(t / 24.0);
        t = t < 0 ? t + 24.0 : t;
        return t;
    }

    /** Konversi Waktu Desimal (Jam.Menit) ke format HH:MM atau HH:MM:SS berdasarkan state. */
    private String floatToTime(double time) {
        if (Double.isNaN(time)) return "N/A";

        time = fixTime(time); 

        if (currentOutputFormat.equals(TimeFormat.HH_MM.toUnderlyingValue())) {
            // HH:MM Format: Bulatkan ke menit terdekat
            
            // Tambahkan setengah menit (0.5 / 60.0 jam) untuk pembulatan
            time += (0.5 / 60.0); 
            time = fixTime(time); 

            int hours = (int) Math.floor(time);
            int minutes = (int) Math.floor((time - hours) * 60.0);
            
            return String.format(Locale.US, "%02d:%02d", hours, minutes);
        } else {
            // HH:MM:SS Format
            int hours = (int) Math.floor(time);
            double fractionalHours = time - hours;
            
            int minutes = (int) Math.floor(fractionalHours * 60.0);
            double fractionalMinutes = (fractionalHours * 60.0) - minutes;
            
            // Hitung detik, dibulatkan ke bilangan bulat terdekat
            int seconds = (int) Math.round(fractionalMinutes * 60.0);
            
            // Tangani pembulatan ke atas
            if (seconds == 60) {
                minutes++;
                seconds = 0;
            }
            if (minutes == 60) {
                hours++;
                minutes = 0;
            }
            hours = (int) fixTime(hours); // Pastikan jam berada dalam rentang 0-23
            
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
    }
    
    /** Mengkonversi tanggal Gregorian ke Hari Julian. */
    private double dateToJDN(int day, int month, int year) {
        if (year < 0) year++;
        if (month <= 2) {
            year--;
            month += 12;
        }
        double A = Math.floor(year / 100.0);
        double B = 2 - A + Math.floor(A / 4.0);
        return Math.floor(365.25 * (year + 4716.0)) + Math.floor(30.6001 * (month + 1)) + day + B - 1524.5;
    }

    /** Hitung Deklinasi Matahari. */
    private double sunDeclination(double jd) {
        double D = jd - 2451545.0; 
        double g = fixAngle(357.529 + 0.98560028 * D); 
        double q = fixAngle(280.459 + 0.98564736 * D); 
        double L = fixAngle(q + 1.915 * Math.sin(degToRad(g)) + 0.020 * Math.sin(degToRad(2 * g))); 
        double e = 23.439 - 0.00000036 * D; 
        double d = radToDeg(Math.asin(Math.sin(degToRad(e)) * Math.sin(degToRad(L)))); 
        return d;
    }

    /** Hitung Persamaan Waktu (Equation of Time). */
    private double equationOfTime(double jd) {
        double D = jd - 2451545.0; 
        double g = fixAngle(357.529 + 0.98560028 * D);
        double q = fixAngle(280.459 + 0.98564736 * D);
        double L = fixAngle(q + 1.915 * Math.sin(degToRad(g)) + 0.020 * Math.sin(degToRad(2 * g)));
        double e = 23.439 - 0.00000036 * D;

        double y = Math.tan(degToRad(e / 2.0));
        y *= y;

        double sin2L = Math.sin(degToRad(2.0 * L));
        double sin4L = Math.sin(degToRad(4.0 * L));
        double cos2L = Math.cos(degToRad(2.0 * L));

        double E = y * sin2L - 2.0 * 0.017 * Math.sin(degToRad(g)) + 4.0 * y * 0.0049 * cos2L;
        E = radToDeg(E);
        return E; 
    }
    
    // --- Fungsi Inisialisasi State ---
    
    /** Inisialisasi lokasi, tanggal, dan zona waktu untuk semua perhitungan sholat berikutnya. */
    @SimpleFunction(description = "Inisialisasi lokasi, tanggal, dan zona waktu untuk semua perhitungan sholat berikutnya.")
    public void SetLocationDateAndInit(double latitude, double longitude, double timezone, int day, int month, int year) {
        this.lat = latitude;
        this.lon = longitude;
        this.timezoneOffset = timezone;
        this.day = day;
        this.month = month; 
        this.year = year;
        
        // Inisialisasi Calendar (perlu -1 untuk bulan karena Calendar menggunakan 0-11)
        try {
            this.calendar = new GregorianCalendar(year, month - 1, day);
        } catch (Exception e) {
            // Jika terjadi error tanggal, biarkan state tetap (default)
        }
    }


    // --- Fungsi Perhitungan Raw (Private) ---

    /** Hitung waktu transisi (Midday/Kulminasi). */
    private double timeTransit(double angle) {
        double time = 12.0 + timezoneOffset - lon / 15.0 - equationOfTime(dateToJDN(day, month, year)) / 60.0;
        return time;
    }
    
    private double getRawMiddayTime() {
        return timeTransit(0.0);
    }

    /** * Hitung waktu terbit/tenggelam atau sholat (subh/isha) untuk sudut tertentu. 
     * @param angle Sudut matahari (negatif untuk Fajr/Isha).
     * @param time Waktu estimasi (midday).
     * @param direction Arah (-1: Pagi, +1: Sore)
     */
    private double sunAngleTime(double angle, double time, int direction) {
        double decl = sunDeclination(dateToJDN(day, month, year) + time / 24.0);
        double latRad = degToRad(lat);
        double declRad = degToRad(decl);
        double angleRad = degToRad(angle);

        double term1 = Math.sin(angleRad) - Math.sin(latRad) * Math.sin(declRad);
        double term2 = Math.cos(latRad) * Math.cos(declRad);
        
        if (term2 < 1.0e-6) {
             return Double.NaN;
        }

        double cosArg = term1 / term2;
        if (cosArg > 1.0) cosArg = 1.0;
        if (cosArg < -1.0) cosArg = -1.0;

        // Jika hasilnya NaN, artinya sudut matahari tidak pernah tercapai (High Latitude)
        if (Math.abs(lat) >= 48.5 && Math.abs(cosArg) > 1.0) {
            return Double.NaN; 
        }

        double H = radToDeg(Math.acos(cosArg)); // Sudut jam (Hour Angle)
        
        // Menggunakan 'direction' (-1 atau +1) untuk menentukan waktu sebelum atau sesudah Midday
        return timeTransit(0.0) + direction * H / 15.0; 
    }

    /** Hitung waktu Asar. */
    private double asrTime(double time) {
        double decl = sunDeclination(dateToJDN(day, month, year) + time / 24.0);
        double declRad = degToRad(decl);
        double latRad = degToRad(lat);
        
        // Rumus Asr (Menggunakan currentAsrFactor: 1=Shafii, 2=Hanafi)
        double angle_asr = radToDeg(Math.atan(1.0 / (currentAsrFactor + Math.tan(Math.abs(latRad - declRad)))));
        
        double H = radToDeg(Math.acos((Math.sin(degToRad(angle_asr)) - Math.sin(latRad) * Math.sin(declRad)) / (Math.cos(latRad) * Math.cos(declRad))));
        
        return timeTransit(0.0) + H / 15.0;
    }

    /**
     * Helper: Melakukan seluruh perhitungan waktu sholat untuk satu hari spesifik
     * menggunakan lat/lon/timezone/angles yang tersimpan dalam state.
     * Hasil dikembalikan sebagai array String (10 item).
     */
    private String[] calculateDayTimesArray(int d, int m, int y) {
        // Simpan state tanggal lama
        int originalDay = this.day;
        int originalMonth = this.month;
        int originalYear = this.year;

        // Set state tanggal baru untuk perhitungan
        this.day = d;
        this.month = m;
        this.year = y;

        // --- Kalkulasi Raw Times ---
        double midday = getRawMiddayTime();
        
        // Initial Raw Times
        double fajrRaw = sunAngleTime(-currentFajrAngle, midday, -1); 
        double sunriseRaw = sunAngleTime(-SUNRISE_SUNSET_ANGLE, midday, -1);
        double dhuhaRaw = sunAngleTime(-DHUHA_ANGLE, midday, -1);
        double dhuhrRaw = midday; 
        double asrRaw = asrTime(midday);
        double maghribRaw = sunAngleTime(-SUNRISE_SUNSET_ANGLE, midday, +1);
        double ishaRaw = sunAngleTime(-currentIshaAngle, midday, +1); 

        // 3. Iterasi untuk Akurasi
        for (int i = 0; i < 3; i++) {
            fajrRaw = sunAngleTime(-currentFajrAngle, fixTime(fajrRaw), -1);
            asrRaw = asrTime(fixTime(asrRaw));
            ishaRaw = sunAngleTime(-currentIshaAngle, fixTime(ishaRaw), +1);
        }
        
        // --- Penyesuaian Lintang Tinggi (Astronomical Rule) ---
        if (currentHighLatAdj.equals(HighLatAdjustment.MIDNIGHT.toUnderlyingValue())) {
            
            // Hitung durasi malam (Sunset - Fajr)
            double nightDuration = fixTime(fajrRaw - maghribRaw);
            
            // Perbaikan Fajr (Menggunakan Tengah Malam)
            if (Double.isNaN(fajrRaw)) {
                // Asumsi Fajr adalah tengah malam - (durasi malam / 2)
                fajrRaw = fixTime(midday - 0.5 * nightDuration / 24.0);
            }
            
            // Perbaikan Isha (Menggunakan Tengah Malam)
            if (Double.isNaN(ishaRaw)) {
                // Asumsi Isha adalah tengah malam + (durasi malam / 2)
                ishaRaw = fixTime(midday + 0.5 * nightDuration / 24.0);
            }
        }
        
        // --- Pembentukan Hasil (10 item waktu) ---
        String[] results = new String[10];
        results[0] = floatToTime(fajrRaw);
        results[1] = floatToTime(sunriseRaw);
        results[2] = floatToTime(dhuhaRaw);
        results[3] = floatToTime(dhuhrRaw);
        results[4] = floatToTime(asrRaw);
        results[5] = floatToTime(maghribRaw); // Maghrib = Sunset
        results[6] = floatToTime(maghribRaw); // Sunset
        results[7] = floatToTime(ishaRaw);
        results[8] = floatToTime(midday);
        results[9] = String.format(Locale.US, "%.2f", fixAngle(calculateQibla(lat, lon)));

        // Kembalikan state tanggal ke nilai semula
        this.day = originalDay;
        this.month = originalMonth;
        this.year = originalYear;

        return results;
    }
    
    // --- Public Individual Calculation Functions (Menggunakan State) ---

    @SimpleFunction(description = "Menghitung waktu Midday (Kulminasi Matahari) berdasarkan pengaturan terakhir.")
    public String CalculateMiddayTime() {
        return calculateDayTimesArray(day, month, year)[8];
    }

    @SimpleFunction(description = "Menghitung waktu Subuh (Fajr) berdasarkan pengaturan terakhir.")
    public String CalculateFajrTime() {
        return calculateDayTimesArray(day, month, year)[0];
    }

    @SimpleFunction(description = "Menghitung waktu Terbit Matahari (Sunrise) berdasarkan pengaturan terakhir.")
    public String CalculateSunriseTime() {
        return calculateDayTimesArray(day, month, year)[1];
    }
    
    @SimpleFunction(description = "Menghitung waktu Dhuha berdasarkan pengaturan terakhir.")
    public String CalculateDhuhaTime() {
        return calculateDayTimesArray(day, month, year)[2];
    }

    @SimpleFunction(description = "Menghitung waktu Zuhur (Dhuhr) berdasarkan pengaturan terakhir. Sama dengan Midday.")
    public String CalculateDhuhrTime() {
        return calculateDayTimesArray(day, month, year)[3];
    }

    @SimpleFunction(description = "Menghitung waktu Ashar (Asr) berdasarkan pengaturan terakhir.")
    public String CalculateAsrTime() {
        return calculateDayTimesArray(day, month, year)[4];
    }

    @SimpleFunction(description = "Menghitung waktu Maghrib berdasarkan pengaturan terakhir. Sama dengan Sunset.")
    public String CalculateMaghribTime() {
        return calculateDayTimesArray(day, month, year)[5];
    }

    @SimpleFunction(description = "Menghitung waktu Tenggelam Matahari (Sunset) berdasarkan pengaturan terakhir.")
    public String CalculateSunsetTime() {
        return calculateDayTimesArray(day, month, year)[6];
    }
    
    @SimpleFunction(description = "Menghitung waktu Isya (Isha) berdasarkan pengaturan terakhir.")
    public String CalculateIshaTime() {
        return calculateDayTimesArray(day, month, year)[7];
    }


    // --- Fungsi Kalkulasi Bulanan dan Tahunan ---

    /** Mendapatkan jumlah hari dalam bulan tertentu. */
    private int getDaysInMonth(int m, int y) {
        Calendar tempCal = new GregorianCalendar(y, m - 1, 1);
        return tempCal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }
    
    /** Membuat header kolom untuk output list. */
    private YailList createHeaderList() {
        return YailList.makeList(new String[]{
            "Tanggal", 
            "Fajr", 
            "Sunrise", 
            "Dhuha", 
            "Dhuhr", 
            "Asr", 
            "Maghrib", 
            "Sunset", 
            "Isha", 
            "Midday", 
            "Qibla"
        });
    }

    /**
     * Menghitung waktu sholat untuk semua hari dalam satu bulan.
     * @param targetMonth Bulan yang ditargetkan (1-12).
     * @param targetYear Tahun yang ditargetkan.
     * @return YailList of YailList, dengan baris pertama sebagai header.
     */
    @SimpleFunction(description = "Menghitung waktu sholat untuk semua hari dalam satu bulan. Mengembalikan list of lists dengan header.")
    public YailList CalculateMonthlyTimes(int targetMonth, int targetYear) {
        List<Object> monthlyList = new ArrayList<>();
        monthlyList.add(createHeaderList()); // Tambahkan header

        int daysInMonth = getDaysInMonth(targetMonth, targetYear);
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy", Locale.US);

        for (int d = 1; d <= daysInMonth; d++) {
            // Set tanggal sementara untuk perhitungan
            Calendar currentDayCal = new GregorianCalendar(targetYear, targetMonth - 1, d);
            String dateStr = dateFormatter.format(currentDayCal.getTime());

            // Lakukan perhitungan
            String[] dailyTimes = calculateDayTimesArray(d, targetMonth, targetYear);
            
            // Gabungkan Tanggal + Waktu
            List<String> row = new ArrayList<>();
            row.add(dateStr);
            for (String time : dailyTimes) {
                row.add(time);
            }
            monthlyList.add(YailList.makeList(row));
        }

        return YailList.makeList(monthlyList);
    }

    /**
     * Menghitung waktu sholat untuk semua hari dalam satu tahun.
     * @param targetYear Tahun yang ditargetkan.
     * @return YailList of YailList, dengan baris pertama sebagai header.
     */
    @SimpleFunction(description = "Menghitung waktu sholat untuk semua hari dalam satu tahun. Mengembalikan list of lists dengan header.")
    public YailList CalculateYearlyTimes(int targetYear) {
        List<Object> yearlyList = new ArrayList<>();
        yearlyList.add(createHeaderList()); // Tambahkan header

        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy", Locale.US);

        for (int m = 1; m <= 12; m++) {
            int daysInMonth = getDaysInMonth(m, targetYear);
            for (int d = 1; d <= daysInMonth; d++) {
                // Set tanggal sementara untuk perhitungan
                Calendar currentDayCal = new GregorianCalendar(targetYear, m - 1, d);
                String dateStr = dateFormatter.format(currentDayCal.getTime());

                // Lakukan perhitungan
                String[] dailyTimes = calculateDayTimesArray(d, m, targetYear);
                
                // Gabungkan Tanggal + Waktu
                List<String> row = new ArrayList<>();
                row.add(dateStr);
                for (String time : dailyTimes) {
                    row.add(time);
                }
                yearlyList.add(YailList.makeList(row));
            }
        }
        return YailList.makeList(yearlyList);
    }
    
    // --- Preset Metode Sholat ---
    
    /** Menyetel variabel state berdasarkan preset metode. */
    private void applyMethod(String method) {
        currentMethod = method;
        // Menggunakan nilai string dari Enum Helper (toUnderlyingValue) untuk switch
        if (method.equals(PrayerMethods.MWL.toUnderlyingValue())) {
            currentFajrAngle = 18.0;
            currentIshaAngle = 17.0;
            currentAsrFactor = 1;
        } else if (method.equals(PrayerMethods.ISNA.toUnderlyingValue())) {
            currentFajrAngle = 15.0;
            currentIshaAngle = 15.0;
            currentAsrFactor = 1;
        } else if (method.equals(PrayerMethods.EGYPT.toUnderlyingValue())) {
            currentFajrAngle = 19.5;
            currentIshaAngle = 17.5;
            currentAsrFactor = 1;
        } else if (method.equals(PrayerMethods.KARACHI.toUnderlyingValue())) {
            currentFajrAngle = 18.0;
            currentIshaAngle = 18.0;
            currentAsrFactor = 1;
        } else if (method.equals(PrayerMethods.CUSTOM.toUnderlyingValue())) {
            // Biarkan custom angles tidak berubah
        } else {
            // Default ke MWL jika string tidak dikenali
            applyMethod(PrayerMethods.MWL.toUnderlyingValue());
        }
    }
    
    // --- Fungsi Publik untuk App Inventor ---

    /** * Mengatur metode perhitungan waktu sholat. 
     * Input berupa dropdown (blok helper) yang berisi MWL, ISNA, dst.
     */
    @SimpleFunction(description = "Mengatur metode perhitungan waktu sholat. Gunakan dropdown untuk memilih metode. Default adalah MWL.")
    public void SetCalculationMethod(@Options(PrayerMethods.class) String method) {
        applyMethod(method);
    }
    
    /**
     * Mengatur metode penyesuaian untuk lintang tinggi (high latitude), 
     * yang diperlukan untuk perhitungan astronomis yang akurat di daerah ekstrem.
     * @param adjustmentMethod Metode penyesuaian (NONE, MIDNIGHT).
     */
    @SimpleFunction(description = "Mengatur metode penyesuaian untuk lintang tinggi (high latitude). Pilih NONE, MIDNIGHT, atau lainnya.")
    public void SetHighLatitudeAdjustment(@Options(HighLatAdjustment.class) String adjustmentMethod) {
        currentHighLatAdj = adjustmentMethod;
    }
    
    /**
     * Mengatur format output waktu (HH:MM atau HH:MM:SS).
     * @param format HH_MM untuk akurasi menit, HH_MM_SS untuk akurasi detik.
     */
    @SimpleFunction(description = "Mengatur format output waktu. Pilih HH_MM (menit) atau HH_MM_SS (detik). Default adalah HH:MM:SS.")
    public void SetTimeFormat(@Options(TimeFormat.class) String format) {
        currentOutputFormat = format;
    }

    /**
     * Mengatur sudut kustom untuk Fajr dan Isha, serta Faktor Asr.
     * Secara otomatis beralih ke metode CUSTOM.
     * @param fajrAngle Sudut matahari Subuh (misal, 18.0).
     * @param ishaAngle Sudut matahari Isya (misal, 17.0).
     * @param asrFactor Faktor Asr (1 untuk Shafii/Maliki/Hanbali, 2 untuk Hanafi).
     */
    @SimpleFunction(description = "Mengatur sudut kustom untuk Fajr dan Isha, serta Faktor Asr (1=Shafii, 2=Hanafi). Otomatis beralih ke metode CUSTOM.")
    public void SetCustomAngles(double fajrAngle, double ishaAngle, int asrFactor) {
        currentFajrAngle = fajrAngle;
        currentIshaAngle = ishaAngle;
        currentAsrFactor = asrFactor;
        currentMethod = PrayerMethods.CUSTOM.toUnderlyingValue();
    }
    
    /** Fungsi Debug: Mengembalikan sudut Fajr dan Isha yang sedang aktif serta faktor Asr. */
    @SimpleFunction(description = "Mengembalikan list: [Fajr Angle, Isha Angle, Asr Factor] yang sedang digunakan.")
    public YailList GetActiveAngles() {
        return YailList.makeList(new Double[]{currentFajrAngle, currentIshaAngle, (double)currentAsrFactor});
    }


    /**
     * Menghitung dan mengembalikan semua waktu sholat, waktu matahari, dan arah Kiblat.
     * Fungsi ini juga menginisialisasi lokasi/tanggal yang akan digunakan oleh fungsi perhitungan individual.
     * @param latitude Lintang lokasi (derajat).
     * @param longitude Bujur lokasi (derajat).
     * @param timezone Zona waktu dalam jam (misalnya 7.0 untuk WIB).
     * @param day Hari (1-31).
     * @param month Bulan (1-12).
     * @param year Tahun (e.g., 2024).
     * @return YailList berisi waktu sholat (HH:MM:SS), Kulminasi (Midday), dan Arah Kiblat (derajat).
     * Urutan: Fajr, Sunrise, Duha, Dhuhr, Asr, Maghrib, Sunset, Isha, Midday, Qibla.
     */
    @SimpleFunction(description = "Menghitung semua waktu sholat, waktu Dhuha, Kulminasi, Terbit/Tenggelam, dan Arah Kiblat. Mengembalikan daftar urutan: Fajr, Sunrise, Duha, Dhuhr, Asr, Maghrib, Sunset, Isha, Midday, Qibla. Fungsi ini juga mengatur lokasi dan tanggal untuk perhitungan individual.")
    public YailList CalculateAllTimes(double latitude, double longitude, double timezone, int day, int month, int year) {
        
        // 1. Inisialisasi State (agar perhitungan individual selanjutnya valid)
        SetLocationDateAndInit(latitude, longitude, timezone, day, month, year);

        // 2. Kalkulasi Raw Times (Ambil dari fungsi helper)
        String[] dailyTimes = calculateDayTimesArray(day, month, year);

        // --- 3. Pembentukan Hasil ---
        return YailList.makeList(dailyTimes);
    }
    
    /**
     * Menghitung arah Kiblat dari lokasi tertentu.
     */
    @SimpleFunction(description = "Menghitung arah Kiblat (dalam derajat dari Utara) dari lintang dan bujur yang diberikan.")
    public double calculateQibla(double lat, double lon) {
        // Koordinat Mekah (Ka'bah)
        final double latKaaba = 21.4225; 
        final double lonKaaba = 39.8262; 

        double latRad = degToRad(lat);
        double lonRad = degToRad(lon);
        double latKaabaRad = degToRad(latKaaba);
        double lonKaabaRad = degToRad(lonKaaba);
        
        double lonDiff = lonKaabaRad - lonRad;

        // Formula Arah Kiblat (Menggunakan Atan2)
        double qiblaAngle = radToDeg(Math.atan2(
            Math.sin(lonDiff),
            (Math.cos(latRad) * Math.tan(latKaabaRad)) - (Math.sin(latRad) * Math.cos(lonDiff))
        ));

        return fixAngle(qiblaAngle);
    }
}
