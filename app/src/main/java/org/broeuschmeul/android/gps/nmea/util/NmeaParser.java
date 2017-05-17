/*
 * Copyright (C) 2010, 2011, 2012 Herbert von Broeuschmeul
 * Copyright (C) 2010, 2011, 2012 BluetoothGPS4Droid Project
 * Copyright (C) 2011, 2012 UsbGPS4Droid Project
 * 
 * This file is part of UsbGPS4Droid.
 *
 * UsbGPS4Droid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * UsbGPS4Droid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with UsbGPS4Droid. If not, see <http://www.gnu.org/licenses/>.
 */

package org.broeuschmeul.android.gps.nmea.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Log;

import org.broeuschmeul.android.gps.usb.provider.USBGpsApplication;

import static android.content.ContentValues.TAG;

/**
 * This class is used to parse NMEA sentences an generate the Android Locations when there is a new GPS FIX.
 * It manage also the Mock Location Provider (enable/disable/fix & status notification)
 * and can compute the the checksum of a NMEA sentence.
 *
 * @author Herbert von Broeuschmeul
 */
public class NmeaParser {
    /**
     * Tag used for log messages
     */
    private static final String LOG_TAG = NmeaParser.class.getSimpleName();

    public static final String SATELLITE_KEY = "satellites";

    private Context appContext;

    private String fixTime = null;
    private long fixTimestamp;

    private boolean hasGGA = false;
    private boolean hasRMC = false;
    private LocationManager lm;
    private float precision = 10f;
    private boolean mockGpsAutoEnabled = false;
    private boolean mockGpsEnabled = false;
    private String mockLocationProvider = null;

    private int mockStatus = LocationProvider.OUT_OF_SERVICE;

    private Location fix = null;
    private String lastSentenceTime = "";

    public NmeaParser(Context context) {
        this(5f, context);
    }

    public NmeaParser(float precision, Context context) {
        this.precision = precision;
        this.appContext = context;
    }

    public void setLocationManager(LocationManager lm) {
        this.lm = lm;
    }

    public void enableMockLocationProvider(String gpsName, boolean force) {
        try {
            LocationProvider prov;

            if (gpsName != null && !gpsName.isEmpty()) {
                if (!gpsName.equals(mockLocationProvider)) {
                    disableMockLocationProvider();
                    mockLocationProvider = gpsName;

                }

                if (!mockGpsEnabled) {
                    prov = lm.getProvider(mockLocationProvider);

                    if (prov != null) {
                        Log.v(LOG_TAG, "Mock provider: " +
                                            prov.getName() +
                                            " " +
                                            prov.getPowerRequirement() +
                                            " " + prov.getAccuracy() +
                                            " " + lm.isProviderEnabled(mockLocationProvider)
                        );

                        try {
                            lm.removeTestProvider(mockLocationProvider);

                        } catch (IllegalArgumentException e) {
                            Log.d(LOG_TAG, "unable to remove current provider Mock provider: " +
                                    mockLocationProvider);
                        }
                    }

                    prov = lm.getProvider(mockLocationProvider);

                    lm.addTestProvider(
                            mockLocationProvider,
                            false,
                            true,
                            false,
                            false,
                            true,
                            true,
                            true,
                            Criteria.POWER_MEDIUM,
                            Criteria.ACCURACY_FINE
                    );

                    if (force || (prov == null)) {
                        Log.d(LOG_TAG, "enabling Mock provider: " + mockLocationProvider);
                        lm.setTestProviderEnabled(mockLocationProvider, true);
                        mockGpsAutoEnabled = true;
                    }

                    mockGpsEnabled = true;

                } else {
                    Log.d(LOG_TAG, "Mock provider already enabled: " + mockLocationProvider);
                }

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    Log.e(LOG_TAG, "Mock provider: " +
                                        prov.getName() +
                                        " " + prov.getPowerRequirement() +
                                        " " + prov.getAccuracy() +
                                        " " + lm.isProviderEnabled(mockLocationProvider)
                    );
                }
            }
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Error while enabling Mock Locations Provider", e);
            disableMockLocationProvider();

        }
    }

    public void disableMockLocationProvider() {
        try {
            LocationProvider prov;
            if (mockLocationProvider != null && !mockLocationProvider.equals("") && mockGpsEnabled) {
                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    Log.v(LOG_TAG, "Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                mockGpsEnabled = false;

                if (mockGpsAutoEnabled) {
                    Log.d(LOG_TAG, "disabling Mock provider: " + mockLocationProvider);
                    lm.setTestProviderEnabled(mockLocationProvider, false);
                }

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    Log.v(LOG_TAG, "Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                lm.clearTestProviderEnabled(mockLocationProvider);

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    Log.v(LOG_TAG, "Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                lm.clearTestProviderStatus(mockLocationProvider);
                lm.removeTestProvider(mockLocationProvider);

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    Log.v(LOG_TAG, "Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                Log.d(LOG_TAG, "removed mock GPS");

            } else {
                Log.d(LOG_TAG, "Mock provider already disabled: " + mockLocationProvider);

            }

        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Error while enabling Mock Mocations Provider", e);

        } finally {
            mockLocationProvider = null;
            mockGpsEnabled = false;
            mockGpsAutoEnabled = false;
            mockStatus = LocationProvider.OUT_OF_SERVICE;

        }
    }

    /**
     * @return the mockGpsEnabled
     */
    public boolean isMockGpsEnabled() {
        return mockGpsEnabled;
    }

    public void setMockLocationProviderOutOfService() {
        notifyStatusChanged(LocationProvider.OUT_OF_SERVICE, null, System.currentTimeMillis());
    }

    /**
     * @return the mockLocationProvider
     */
    public String getMockLocationProvider() {
        return mockLocationProvider;
    }

    /**
     * Notifies a new location fix to the MockLocationProvider
     * @param fix the location
     * @throws SecurityException
     */
    private void notifyFix(Location fix) throws SecurityException {
        fixTime = null;
        hasGGA = false;
        hasRMC = false;

        if (fix != null) {
            ((USBGpsApplication) appContext).notifyNewLocation(fix);
            Log.v(LOG_TAG, "New Fix: " + System.currentTimeMillis() + " " + fix);

            if (lm != null && mockGpsEnabled) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                }

                try {
                    lm.setTestProviderLocation(mockLocationProvider, fix);

                } catch (IllegalArgumentException e) {
                    Log.d(LOG_TAG, "Tried to notify a fix that was incomplete");
                    Log.d(LOG_TAG, "Accuracy = " + Float.toString(fix.getAccuracy()));

                }
                Log.v(LOG_TAG, "New Fix notified to Location Manager: " + mockLocationProvider);

            } else {
                Log.v(LOG_TAG, "Fix could not be notified, no locationManager");

            }
            this.fix = null;
        }
    }

    private void notifyStatusChanged(int status, Bundle extras, long updateTime) {
        fixTime = null;
        hasGGA = false;
        hasRMC = false;
        if (this.mockStatus != status) {
            Log.d(LOG_TAG, "New mockStatus: " + System.currentTimeMillis() + " " + status);

            if (lm != null && mockGpsEnabled) {
                lm.setTestProviderStatus(mockLocationProvider, status, extras, updateTime);

                Log.v(LOG_TAG,
                        "New mockStatus notified to Location Manager: " +
                                status +
                                " " +
                                mockLocationProvider
                );
            }
            this.fix = null;
            this.mockStatus = status;
        }
    }

    // parse NMEA Sentence
    public String parseNmeaSentence(String gpsSentence) throws SecurityException {
        String nmeaSentence = null;

        Log.v(LOG_TAG, "data: " + System.currentTimeMillis() + " " + gpsSentence);

        // Check that status is in a readable format
        Pattern xx = Pattern.compile("\\$([^*$]*)(?:\\*([0-9A-F][0-9A-F]))?\r\n");

        Matcher m = xx.matcher(gpsSentence);

        if (m.matches()) {
            nmeaSentence = m.group(0);
            String sentence = m.group(1);
            String checkSum = m.group(2);
            Log.v(LOG_TAG,
                    "data: " +
                            System.currentTimeMillis() +
                            " " +
                            sentence +
                            " checksum: " +
                            checkSum +
                            " control: " +
                            String.format("%02X", computeChecksum(sentence))
            );

            // If we don't have a valid checksum then we obviously don't have the correct sentence
            if (checkSum != null &&
                    checkSum.equals(String.format("%02X", computeChecksum(sentence)))) {

                SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(',');
                splitter.setString(sentence);
                String command = splitter.next();

                switch (command) {
                    case "GPGGA": {
                        /* $GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47

                            Where:
                                 GGA          Global Positioning System Fix Data
                                 123519       Fix taken at 12:35:19 UTC
                                 4807.038,N   Latitude 48 deg 07.038' N
                                 01131.000,E  Longitude 11 deg 31.000' E
                                 1            Fix quality: 0 = invalid
                                                           1 = GPS fix (SPS)
                                                           2 = DGPS fix
                                                           3 = PPS fix
                                                           4 = Real Time Kinematic
                                                           5 = Float RTK
                                                           6 = estimated (dead reckoning) (2.3 feature)
                                                           7 = Manual input mode
                                                           8 = Simulation mode
                                 08           Number of satellites being tracked
                                 0.9          Horizontal dilution of position
                                 545.4,M      Altitude, Meters, above mean sea level
                                 46.9,M       Height of geoid (mean sea level) above WGS84
                                                  ellipsoid
                                 (empty field) time in seconds since last DGPS update
                                 (empty field) DGPS station ID number
                                 *47          the checksum data, always begins with *
                         */

                        // UTC time of fix HHmmss.S
                        String time = splitter.next();
                        lastSentenceTime = time;

                        // latitude ddmm.M
                        String lat = splitter.next();

                        // direction (N/S)
                        String latDir = splitter.next();

                        // longitude dddmm.M
                        String lon = splitter.next();

                        // direction (E/W)
                        String lonDir = splitter.next();

                        /* fix quality:
                            0= invalid
                            1 = GPS fix (SPS)
                            2 = DGPS fix
                            3 = PPS fix
                            4 = Real Time Kinematic
                            5 = Float RTK
                            6 = estimated (dead reckoning) (2.3 feature)
                            7 = Manual input mode
                            8 = Simulation mode
                         */
                        String quality = splitter.next();

                        // Number of satellites being tracked
                        String nbSat = splitter.next();

                        // Horizontal dilution of position (float)
                        String hdop = splitter.next();

                        // Altitude, Meters, above mean sea level
                        String alt = splitter.next();

                        // Height of geoid (mean sea level) above WGS84 ellipsoid
                        String geoAlt = splitter.next();

                        // time in seconds since last DGPS update
                        // DGPS station ID number
                        if (quality != null && !quality.equals("") && !quality.equals("0")) {
                            if (this.mockStatus != LocationProvider.AVAILABLE) {
                                long updateTime = parseNmeaTime(time);
                                notifyStatusChanged(LocationProvider.AVAILABLE, null, updateTime);
                            }

                            if (!time.equals(fixTime)) {
                                notifyFix(fix);
                                fix = new Location(mockLocationProvider);
                                fixTime = time;
                                fixTimestamp = parseNmeaTime(time);
                                fix.setTime(fixTimestamp);
                                //Log.v(LOG_TAG, "Fix: "+fix);
                            }

                            if (lat != null && !lat.equals("")) {
                                fix.setLatitude(parseNmeaLatitude(lat, latDir));
                            }

                            if (lon != null && !lon.equals("")) {
                                fix.setLongitude(parseNmeaLongitude(lon, lonDir));
                            }

                            if (hdop != null && !hdop.equals("")) {
                                fix.setAccuracy(Float.parseFloat(hdop) * precision);
                            }

                            if (alt != null && !alt.equals("")) {
                                fix.setAltitude(Double.parseDouble(alt));
                            }

                            if (nbSat != null && !nbSat.equals("")) {
                                Bundle extras = new Bundle();
                                extras.putInt(SATELLITE_KEY, Integer.parseInt(nbSat));
                                fix.setExtras(extras);
                            }

                            //Log.v(LOG_TAG, "Fix: "+System.currentTimeMillis()+" "+fix);
                            hasGGA = true;

                            if (hasRMC) {
                                notifyFix(fix);
                            }

                        } else if (quality != null && quality.equals("0")) {
                            if (this.mockStatus != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                                long updateTime = parseNmeaTime(time);
                                notifyStatusChanged(LocationProvider.TEMPORARILY_UNAVAILABLE, null, updateTime);
                            }
                        }

                        break;
                    }
                    case "GPRMC": {
                        /* $GPRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*6A

                           Where:
                             RMC          Recommended Minimum sentence C
                             123519       Fix taken at 12:35:19 UTC
                             A            Status A=active or V=Void.
                             4807.038,N   Latitude 48 deg 07.038' N
                             01131.000,E  Longitude 11 deg 31.000' E
                             022.4        Speed over the ground in knots
                             084.4        Track angle in degrees True
                             230394       Date - 23rd of March 1994
                             003.1,W      Magnetic Variation
                             *6A          The checksum data, always begins with *
                        */

                        // UTC time of fix HHmmss.S
                        String time = splitter.next();
                        lastSentenceTime = time;

                        // fix status (A/V)
                        String status = splitter.next();

                        // latitude ddmm.M
                        String lat = splitter.next();

                        // direction (N/S)
                        String latDir = splitter.next();

                        // longitude dddmm.M
                        String lon = splitter.next();

                        // direction (E/W)
                        String lonDir = splitter.next();

                        // Speed over the ground in knots
                        String speed = splitter.next();

                        // Track angle in degrees True
                        String bearing = splitter.next();

                        // UTC date of fix DDMMYY
                        String date = splitter.next();

                        // Magnetic Variation ddd.D
                        String magn = splitter.next();

                        // Magnetic variation direction (E/W)
                        String magnDir = splitter.next();

                        // for NMEA 0183 version 3.00 active the Mode indicator field is added
                        // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator )
                        if (status != null && !status.equals("") && status.equals("A")) {
                            if (this.mockStatus != LocationProvider.AVAILABLE) {
                                long updateTime = parseNmeaTime(time);
                                notifyStatusChanged(LocationProvider.AVAILABLE, null, updateTime);
                            }

                            if (!time.equals(fixTime)) {
                                notifyFix(fix);
                                fix = new Location(mockLocationProvider);
                                fixTime = time;
                                fixTimestamp = parseNmeaTime(time);
                                fix.setTime(fixTimestamp);
                                //Log.v(LOG_TAG, "Fix: "+fix);
                            }

                            if (lat != null && !lat.equals("")) {
                                fix.setLatitude(parseNmeaLatitude(lat, latDir));
                            }

                            if (lon != null && !lon.equals("")) {
                                fix.setLongitude(parseNmeaLongitude(lon, lonDir));
                            }

                            if (speed != null && !speed.equals("")) {
                                fix.setSpeed(parseNmeaSpeed(speed, "N"));
                            }

                            if (bearing != null && !bearing.equals("")) {
                                fix.setBearing(Float.parseFloat(bearing));
                            }
                            //	Log.v(LOG_TAG, "Fix: "+System.currentTimeMillis()+" "+fix);
                            hasRMC = true;
                            if (hasGGA) {
                                notifyFix(fix);
                            }
                        } else if (status != null && status.equals("V")) {
                            if (this.mockStatus != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                                long updateTime = parseNmeaTime(time);
                                notifyStatusChanged(LocationProvider.TEMPORARILY_UNAVAILABLE, null, updateTime);
                            }
                        }

                        break;
                    }
                    case "GPGSA": {
                        /*  $GPGSA,A,3,04,05,,09,12,,,24,,,,,2.5,1.3,2.1*39

                            Where:
                                 GSA      Satellite status
                                 A        Auto selection of 2D or 3D fix (M = manual)
                                 3        3D fix - values include: 1 = no fix
                                                                   2 = 2D fix
                                                                   3 = 3D fix
                                 04,05... PRNs of satellites used for fix (space for 12)
                                 2.5      PDOP (Position dilution of precision)
                                 1.3      Horizontal dilution of precision (HDOP)
                                 2.1      Vertical dilution of precision (VDOP)
                                 *39      the checksum data, always begins with *
                         */
                        // mode : A Auto selection of 2D or 3D fix / M = manual
                        String mode = splitter.next();

                        // fix type  : 1 - no fix / 2 - 2D / 3 - 3D
                        String fixType = splitter.next();

                        // discard PRNs of satellites used for fix (space for 12)
                        for (int i = 0; ((i < 12) && (!"1".equals(fixType))); i++) {
                            splitter.next();
                        }

                        // Position dilution of precision (float)
                        String pdop = splitter.next();

                        // Horizontal dilution of precision (float)
                        String hdop = splitter.next();

                        // Vertical dilution of precision (float)
                        String vdop = splitter.next();

                        break;
                    }
                    case "GPVTG": {
                    /*  $GPVTG,054.7,T,034.4,M,005.5,N,010.2,K*48

                        where:
                                VTG          Track made good and ground speed
                                054.7,T      True track made good (degrees)
                                034.4,M      Magnetic track made good
                                005.5,N      Ground speed, knots
                                010.2,K      Ground speed, Kilometers per hour
                                *48          Checksum
                     */
                        // Track angle in degrees True
                        String bearing = splitter.next();

                        // T
                        splitter.next();

                        // Magnetic track made good
                        String magn = splitter.next();

                        // M
                        splitter.next();

                        // Speed over the ground in knots
                        String speedKnots = splitter.next();

                        // N
                        splitter.next();

                        // Speed over the ground in Kilometers per hour
                        String speedKm = splitter.next();

                        // K
                        splitter.next();
                        // for NMEA 0183 version 3.00 active the Mode indicator field is added
                        // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator)

                        break;
                    }
                    case "GPGLL": {
                    /*  $GPGLL,4916.45,N,12311.12,W,225444,A,*1D

                        Where:
                             GLL          Geographic position, Latitude and Longitude
                             4916.46,N    Latitude 49 deg. 16.45 min. North
                             12311.12,W   Longitude 123 deg. 11.12 min. West
                             225444       Fix taken at 22:54:44 UTC
                             A            Data Active or V (void)
                             *iD          checksum data
                     */
                        // latitude ddmm.M
                        String lat = splitter.next();
                        // direction (N/S)
                        String latDir = splitter.next();
                        // longitude dddmm.M
                        String lon = splitter.next();
                        // direction (E/W)
                        String lonDir = splitter.next();
                        // UTC time of fix HHmmss.S
                        String time = splitter.next();
                        lastSentenceTime = time;

                        // fix status (A/V)
                        String status = splitter.next();
                        // for NMEA 0183 version 3.00 active the Mode indicator field is added
                        // Mode indicator, (A=autonomous, D=differential, E=Estimated, N=not valid, S=Simulator )
                        break;
                    }
                }
            } else {
                // As we have received some awful data, it is safe to assume we have missed the
                // current fix, so reset all of the current values and restart
                Log.e(TAG, "Sentence invalid, checksums don't match");
                hasGGA = false;
                hasRMC = false;
                fixTime = null;
                return null;
            }
        }
        return nmeaSentence;
    }

    public double parseNmeaLatitude(String lat, String orientation) {
        double latitude = 0.0;

        if (lat != null && orientation != null && !lat.equals("") && !orientation.equals("")) {
            double temp1 = Double.parseDouble(lat);
            double temp2 = Math.floor(temp1 / 100);
            double temp3 = (temp1 / 100 - temp2) / 0.6;
            if (orientation.equals("S")) {
                latitude = -(temp2 + temp3);
            } else if (orientation.equals("N")) {
                latitude = (temp2 + temp3);
            }
        }
        return latitude;
    }

    public double parseNmeaLongitude(String lon, String orientation) {
        double longitude = 0.0;
        if (lon != null && orientation != null && !lon.equals("") && !orientation.equals("")) {
            double temp1 = Double.parseDouble(lon);
            double temp2 = Math.floor(temp1 / 100);
            double temp3 = (temp1 / 100 - temp2) / 0.6;
            if (orientation.equals("W")) {
                longitude = -(temp2 + temp3);
            } else if (orientation.equals("E")) {
                longitude = (temp2 + temp3);
            }
        }
        return longitude;
    }

    public float parseNmeaSpeed(String speed, String metric) {
        float meterSpeed = 0.0f;
        if (speed != null && metric != null && !speed.equals("") && !metric.equals("")) {
            float temp1 = Float.parseFloat(speed) / 3.6f;
            if (metric.equals("K")) {
                meterSpeed = temp1;
            } else if (metric.equals("N")) {
                meterSpeed = temp1 * 1.852f;
            }
        }
        return meterSpeed;
    }

    public long parseNmeaTime(String time) {
        long timestamp = 0;
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat fmt = new SimpleDateFormat("HHmmss.SSS");
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            if (time != null) {
                long now = System.currentTimeMillis();
                long today = now - (now % 86400000L);
                long temp1;

                // sometime we don't have millisecond in the time string, so we have to reformat it
                temp1 = fmt.parse(String.format((Locale) null, "%010.3f", Double.parseDouble(time))).getTime();
                long temp2 = today + temp1;
                // if we're around midnight we could have a problem...
                if (temp2 - now > 43200000L) {
                    timestamp = temp2 - 86400000L;
                } else if (now - temp2 > 43200000L) {
                    timestamp = temp2 + 86400000L;
                } else {
                    timestamp = temp2;
                }
            }
        } catch (ParseException e) {
            Log.e(LOG_TAG, "Error while parsing NMEA time", e);
        }
        Log.d(LOG_TAG, "Timestamp from gps = " + String.valueOf(timestamp) + " System clock says " + System.currentTimeMillis());
        return timestamp;
    }

    public byte computeChecksum(String s) {
        byte checksum = 0;
        for (char c : s.toCharArray()) {
            checksum ^= (byte) c;
        }
        return checksum;
    }

    public String getLastSentenceTime() {
        return lastSentenceTime;
    }

    public void clearLastSentenceTime() {
        lastSentenceTime = "";
    }
}
