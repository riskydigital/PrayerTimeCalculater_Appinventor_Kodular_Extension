package com.riskydigital.PTC.helpers;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;

/**
 * Enum ini menyediakan format output waktu (HH:MM atau HH:MM:SS) 
 * dan mengimplementasikan OptionList untuk membuat dropdown di App Inventor.
 */
public enum TimeFormat implements OptionList<String> {
    // Mendefinisikan nilai yang akan dikembalikan
    HH_MM("HH:MM"), 
    HH_MM_SS("HH:MM:SS");

    private final String formatValue;

    // Konstruktor untuk menetapkan nilai string
    TimeFormat(String value) {
        this.formatValue = value;
    }

    // 1. Mengembalikan nilai string yang mendasarinya (Wajib)
    public String toUnderlyingValue() {
        return formatValue;
    }

    private static final Map<String, TimeFormat> lookup = new HashMap<>();

    static {
        // Mengisi lookup map saat kelas dimuat
        for (TimeFormat format : TimeFormat.values()) {
            lookup.put(format.toUnderlyingValue(), format);
        }
    }

    // 2. Fungsi statis untuk mengubah nilai string kembali ke enum (Wajib)
    public static TimeFormat fromUnderlyingValue(String value) {
        return lookup.get(value);
    }
}
