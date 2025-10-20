package com.riskydigital.PTC.helpers;

import com.google.appinventor.components.common.OptionList;
import java.util.HashMap;
import java.util.Map;


// --- ENUM BARU UNTUK PENYESUAIAN LINTANG TINGGI ---

/**
 * Enum ini menyediakan metode penyesuaian waktu sholat untuk High Latitude (Lintang Tinggi).
 * Ini diperlukan untuk perhitungan astronomis yang lengkap.
 */
public enum HighLatAdjustment implements OptionList<String> {
    NONE("NONE"),
    MIDNIGHT("MIDNIGHT"); // Middle of the Night Rule (Tengah Malam)

    private final String adjustmentValue;
    
    HighLatAdjustment(String value) {
        this.adjustmentValue = value;
    }

    @Override
    public String toUnderlyingValue() {
        return adjustmentValue;
    }

    private static final Map<String, HighLatAdjustment> lookup = new HashMap<>();

    static {
        for (HighLatAdjustment adj : HighLatAdjustment.values()) {
            lookup.put(adj.toUnderlyingValue(), adj);
        }
    }

    public static HighLatAdjustment fromUnderlyingValue(String value) {
        return lookup.get(value);
    }
}
