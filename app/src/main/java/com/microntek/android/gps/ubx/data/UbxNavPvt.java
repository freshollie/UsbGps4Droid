package com.microntek.android.gps.ubx.data;

import android.location.Location;
import android.os.Bundle;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class UbxNavPvt extends UbxData implements Pvt {

    private static final String LOG_TAG = UbxNavPvt.class.getSimpleName();

    // スレッドセーブじゃないので取り扱いには注意
    static SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmssSSS");

    private boolean enableSpeedParam = false;

    public UbxNavPvt(byte[] data, boolean enableSpeedParam) {
        super(data);
        this.enableSpeedParam = enableSpeedParam;
    }

    @Override
    public boolean parse(Location fix) {
        int idx = IDX_LEN + 2 + 0; // iTOW Offset=0
        long time = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 20; // gpsFix Offset=20
        byte status = data[idx];

        idx = IDX_LEN + 2 + 21; // flags Offset=21
        byte flags = data[idx];

        idx = IDX_LEN + 2 + 24; // lon Offset=24
        long lon = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 28; // lat Offset=28
        long lat = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 36; // hMSL Offset=36
        long height = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 60; // gSpeed Offset=60
        long speed = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 64; // headMot Offset=64
        long heading = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 40; // hAcc Offset=40
        long acc = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 72; // headVeh Offset=72
        long headingAcc = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 23; // headVeh Offset=23
        byte useSv = data[idx];
        fix.setLatitude((double) lat / 10000000);
        fix.setLongitude((double) lon / 10000000);
        fix.setAccuracy((float) acc / 1000);
        fix.setAltitude((double) height / 1000);
        if (enableSpeedParam)
            fix.setSpeed((float) speed / 1000);
        fix.setBearing((float) heading / 100000);
        //fix.setBearingAccuracyDegrees(headingAcc/100000);

        Bundle bundle = fix.getExtras();
        bundle.putInt(SATELLITE_KEY, (int) useSv);
        bundle.putInt(FIX_STATUS_KEY, (int) status);
        bundle.putInt(SBAS_STATUS_KEY, (int) flags & 0x02);
        fix.setExtras(bundle);

        return true;
    }


    @Override
    public long parseUbxTime() {
        return parseUbxTime(data, IDX_LEN + 2 + 4);
    }

    private long parseUbxTime(byte[] ubx, int idx) {
        long timestamp = 0;

        // 年～ミリ秒
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%04d", byte2hex(ubx, idx, 2)));
        sb.append(String.format("%02d", byte2hex(ubx, idx+2, 1)));
        sb.append(String.format("%02d", byte2hex(ubx, idx+3, 1)));
        sb.append(String.format("%02d", byte2hex(ubx, idx+4, 1)));
        sb.append(String.format("%02d", byte2hex(ubx, idx+5, 1)));
        sb.append(String.format("%02d", byte2hex(ubx, idx+6, 1)));
        sb.append(String.format("%03d", (byte2hex(ubx, idx+12, 4) / 1000000)));

        String time = sb.toString();
        //log("time: " + time);

        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

        try {
            if (time != null) {
                timestamp = fmt.parse(time).getTime();
            }
        } catch (ParseException e) {
            logError("Error while parsing UBX time", e);
        }
        log("Timestamp from gps = " + String.valueOf(timestamp) + " System clock says " + System.currentTimeMillis());
        return timestamp;
    }

    @Override
    public long getITow() {
        int idx = IDX_LEN + 2 + 0; // iTOW Offset=0
        long time = byte2hex(data, idx, 4);
        return time;
    }

    @Override
    public boolean isFix() {
        int idx = IDX_LEN + 2 + 21; // gpsFix Offset=21
        byte status = data[idx];
        return status != 0x00;
    }
}
