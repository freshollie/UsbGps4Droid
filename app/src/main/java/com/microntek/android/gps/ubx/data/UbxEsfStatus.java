package com.microntek.android.gps.ubx.data;

import android.location.Location;
import android.os.Bundle;

public class UbxEsfStatus extends UbxData {

    private static final String LOG_TAG = UbxEsfStatus.class.getSimpleName();

    public UbxEsfStatus(byte[] data) {
        super(data);
    }

    @Override
    public boolean parse(Location fix) {
        int idx = IDX_LEN + 2 + 12; // fusionMode Offset=12
        long fusionMode = byte2hex(data, idx, 1);

        Bundle bundle = fix.getExtras();
        bundle.putInt(FUSION_STATUS_KEY, (int) fusionMode);
        fix.setExtras(bundle);

        return true;
    }

    @Override
    public long getITow() {
        int idx = IDX_LEN + 2 + 0; // iTOW Offset=0
        long time = byte2hex(data, idx, 4);
        return time;
    }
}
