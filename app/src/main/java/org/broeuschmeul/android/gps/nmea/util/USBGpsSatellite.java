package org.broeuschmeul.android.gps.nmea.util;

/**
 * Created by freshollie on 31/08/17.
 *
 * Mimic of the android Satellite object, used for GpsTest activity
 */
public class USBGpsSatellite {
    public boolean valid = true;
    public boolean ephemeris = false;
    public boolean almanac = false;
    public boolean usedInFix = true;
    public int prn = -1;
    public float snr = -1;
    public float elevation = -1;
    public float azimuth = -1;

    public USBGpsSatellite(int prn) {
        this.prn = prn;
    }
}
