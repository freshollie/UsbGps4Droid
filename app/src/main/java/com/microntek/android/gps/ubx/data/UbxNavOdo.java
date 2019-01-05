package com.microntek.android.gps.ubx.data;

import android.location.Location;
import android.os.Bundle;

public class UbxNavOdo extends UbxData {

    private static final String LOG_TAG = UbxNavOdo.class.getSimpleName();

    public UbxNavOdo(byte[] data) {
        super(data);
    }

    @Override
    public boolean parse(Location fix) {
        int idx = IDX_LEN + 2 + 8; // distance Offset=8
        long distance = byte2hex(data, idx, 4);

        idx = IDX_LEN + 2 + 12; // totalDistance Offset=12
        long totalDistance = byte2hex(data, idx, 4);
        Bundle bundle = fix.getExtras();

        bundle.putInt(DISTANCE1_STATUS_KEY, (int) distance);
        bundle.putInt(DISTANCE2_STATUS_KEY, (int) totalDistance);

        fix.setExtras(bundle);

        return true;
    }

    @Override
    public long getITow() {
        int idx = IDX_LEN + 2 + 4; // iTOW Offset=4
        long time = byte2hex(data, idx, 4);
        return time;
    }
}
