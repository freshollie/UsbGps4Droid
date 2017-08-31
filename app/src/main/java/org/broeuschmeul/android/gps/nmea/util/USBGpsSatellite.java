package org.broeuschmeul.android.gps.nmea.util;

/**
 * Created by freshollie on 31/08/17.
 */

public class USBGpsSatellite {
    public boolean valid;
    public boolean ephemeris;
    public boolean almanac;
    public boolean usedInFix;
    public int prn;
    public float snr;
    public float elevation;
    public float azimuth;

    public USBGpsSatellite(int prn) {
        this.prn = prn;
    }
}
