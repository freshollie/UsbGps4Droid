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

package com.microntek.android.gps.ubx.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils.SimpleStringSplitter;
import android.util.Log;

import com.microntek.android.gps.ubx.data.Pvt;
import com.microntek.android.gps.ubx.data.UbxData;
import com.microntek.android.gps.usb.provider.BuildConfig;
import com.microntek.android.gps.usb.provider.R;
import com.microntek.android.gps.usb.provider.USBGpsApplication;
import com.microntek.android.gps.usb.provider.driver.USBGpsProviderService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

;

/**
 * This class is used to parse NMEA sentences an generate the Android Locations when there is a new GPS FIX.
 * It manage also the Mock Location Provider (enable/disable/fix & status notification)
 * and can compute the the checksum of a NMEA sentence.
 *
 * @author Herbert von Broeuschmeul
 */
public class UbxParser {
    /**
     * Tag used for log messages
     */
    private static final String LOG_TAG = UbxParser.class.getSimpleName();

    public static final String SYSTEM_TIME_FIX = "system_time_fix";

    private Context appContext;

    private long fixTime = -1;
    private long fixTimestamp;

    private LocationManager lm;
    private boolean mockGpsAutoEnabled = false;
    private boolean mockGpsEnabled = false;
    private String mockLocationProvider = null;

    private int mockStatus = LocationProvider.OUT_OF_SERVICE;

    private Location fix = null;
    private long lastSentenceTime = -1;

    private boolean enableSpeedParam = false;

    public UbxParser(Context context) {
         this.appContext = context;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        enableSpeedParam = preferences.getBoolean(USBGpsProviderService.PREF_USE_SPEED, true);
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
                        log("Mock provider: " +
                                            prov.getName() +
                                            " " +
                                            prov.getPowerRequirement() +
                                            " " + prov.getAccuracy() +
                                            " " + lm.isProviderEnabled(mockLocationProvider)
                        );

                        try {
                            lm.removeTestProvider(mockLocationProvider);

                        } catch (IllegalArgumentException e) {
                            log("unable to remove current provider Mock provider: " +
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
                            enableSpeedParam,
                            true,
                            Criteria.POWER_MEDIUM,
                            Criteria.ACCURACY_FINE
                    );

                    if (force || (prov == null)) {
                        log("enabling Mock provider: " + mockLocationProvider);
                        lm.setTestProviderEnabled(mockLocationProvider, true);
                        mockGpsAutoEnabled = true;
                    }

                    mockGpsEnabled = true;

                } else {
                    log("Mock provider already enabled: " + mockLocationProvider);
                }

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    log("Mock provider: " +
                            prov.getName() +
                            " " + prov.getPowerRequirement() +
                            " " + prov.getAccuracy() +
                            " " + lm.isProviderEnabled(mockLocationProvider)
                    );
                }
            }
        } catch (SecurityException e) {
            logError("Error while enabling Mock Locations Provider", e);
            disableMockLocationProvider();

        }
    }

    public void disableMockLocationProvider() {
        try {
            LocationProvider prov;
            if (mockLocationProvider != null && !mockLocationProvider.equals("") && mockGpsEnabled) {
                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    log("Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                mockGpsEnabled = false;

                if (mockGpsAutoEnabled) {
                    log("disabling Mock provider: " + mockLocationProvider);
                    lm.setTestProviderEnabled(mockLocationProvider, false);
                }

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    log("Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                lm.clearTestProviderEnabled(mockLocationProvider);

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    log("Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                lm.clearTestProviderStatus(mockLocationProvider);
                lm.removeTestProvider(mockLocationProvider);

                prov = lm.getProvider(mockLocationProvider);

                if (prov != null) {
                    log("Mock provider: " + prov.getName() + " " + prov.getPowerRequirement() + " " + prov.getAccuracy() + " " + lm.isProviderEnabled(mockLocationProvider));
                }

                log("removed mock GPS");

            } else {
                log("Mock provider already disabled: " + mockLocationProvider);

            }

        } catch (SecurityException e) {
            logError("Error while enabling Mock Mocations Provider", e);

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
        fixTime = -1;

        if (fix != null) {
            ((USBGpsApplication) appContext).notifyNewLocation(fix);
            log("New Fix: " + System.currentTimeMillis() + " " + fix);

            if (lm != null && mockGpsEnabled) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    fix.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                }

                try {
                    lm.setTestProviderLocation(mockLocationProvider, fix);

                } catch (IllegalArgumentException e) {
                    log("Tried to notify a fix that was incomplete");
                    log("Accuracy = " + Float.toString(fix.getAccuracy()));

                }
                log("New Fix notified to Location Manager: " + mockLocationProvider);

            } else {
                log("Fix could not be notified, no locationManager");

            }
            //this.fix = null;
        }
    }

    private void notifyStatusChanged(int status, Bundle extras, long updateTime) {
        fixTime = -1;

        if (this.mockStatus != status) {
            log("New mockStatus: " + System.currentTimeMillis() + " " + status);

            if (lm != null && mockGpsEnabled) {
                lm.setTestProviderStatus(mockLocationProvider, status, extras, updateTime);

                log("New mockStatus notified to Location Manager: " +
                        status +
                        " " +
                        mockLocationProvider
                );
            }
            this.fix = null;
            this.mockStatus = status;
        }
    }

    // parse UBX Sentence
    public boolean parseUbxSentence(UbxData ubx) throws SecurityException {

        if (fix == null) {
            fix = new Location(mockLocationProvider);
            Bundle bundle = new Bundle();
            fix.setExtras(bundle);
        }

        // 各情報を解析して反映する
        if(ubx.parse(fix)) {
            long time = ubx.getITow();

            // 位置情報を取り扱うメッセージの場合
            if(ubx instanceof Pvt) {
                if(((Pvt)ubx).isFix()) {
                    if (this.mockStatus != LocationProvider.AVAILABLE) {
                        long updateTime = ((Pvt)ubx).parseUbxTime();
                        notifyStatusChanged(LocationProvider.AVAILABLE, null, updateTime);
                    }

                    if (time != fixTime) {

                        fixTimestamp = ((Pvt)ubx).parseUbxTime();
                        lastSentenceTime = fixTimestamp;

                        fix.setTime(fixTimestamp);
                        Bundle bundle = fix.getExtras();
                        bundle.putLong(SYSTEM_TIME_FIX, System.currentTimeMillis());
                        fix.setExtras(bundle);

                        // 位置情報を反映
                        fixTime = time;
                        notifyFix(fix);
                    }
                }
                else {
                    if (this.mockStatus != LocationProvider.TEMPORARILY_UNAVAILABLE) {
                        long updateTime = ((Pvt)ubx).parseUbxTime();
                        lastSentenceTime = fixTimestamp;
                        notifyStatusChanged(LocationProvider.TEMPORARILY_UNAVAILABLE, null, updateTime);
                    }
                }
            }
            return true;
        }
        return false;
    }

    public byte computeChecksum(String s) {
        byte checksum = 0;
        for (char c : s.toCharArray()) {
            checksum ^= (byte) c;
        }
        return checksum;
    }

    public long getLastSentenceTime() {
        return lastSentenceTime;
    }

    public void clearLastSentenceTime() {
        lastSentenceTime = -1;
    }

    private void log(String message) {
        if (BuildConfig.DEBUG) Log.d(LOG_TAG, message);
    }

    private void logError(String message, Exception e) {
        if (BuildConfig.DEBUG) Log.e(LOG_TAG, message, e);
    }
}
