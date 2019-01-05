package com.microntek.android.gps.ubx.data;

import android.content.Context;
import android.location.Location;

import com.microntek.android.gps.usb.provider.USBGpsApplication;

public class UbxNotImplement extends UbxData {

    private static final String LOG_TAG = UbxNotImplement.class.getSimpleName();

    public UbxNotImplement(byte[] data) {
        super(data);

    }

    @Override
    public boolean parse(Location fix) {
        return false;
    }

    @Override
    public long getITow() {
        int idx = IDX_LEN + 2 + 0; // iTOW Offset=0
        long time = byte2hex(data, idx, 4);
        return time;
    }

}
