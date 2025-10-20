package com.riskydigital.PTC.helpers;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

// --- ENUM UNTUK METODE PERHITUNGAN SHOLAT ---

/**
 * Enum ini menyediakan konstanta metode perhitungan sholat 
 * dan mengimplementasikan OptionList untuk membuat dropdown di App Inventor.
 */
public enum PrayerMethods implements OptionList<String> {
    // Mendefinisikan nilai yang akan dikembalikan
    MWL("MWL"), 
    ISNA("ISNA"), 
    EGYPT("EGYPT"), 
    KARACHI("KARACHI"), 
    CUSTOM("CUSTOM");

    private final String methodValue;

    // Konstruktor untuk menetapkan nilai string
    PrayerMethods(String value) {
        this.methodValue = value;
    }

    // 1. Mengembalikan nilai string yang mendasarinya (Wajib)
    public String toUnderlyingValue() {
        return methodValue;
    }

    private static final Map<String, PrayerMethods> lookup = new HashMap<>();

    static {
        // Mengisi lookup map saat kelas dimuat
        for (PrayerMethods method : PrayerMethods.values()) {
            lookup.put(method.toUnderlyingValue(), method);
        }
    }

    // 2. Fungsi statis untuk mengubah nilai string kembali ke enum (Wajib)
    public static PrayerMethods fromUnderlyingValue(String value) {
        return lookup.get(value);
    }
}

