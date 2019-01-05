package com.microntek.android.gps.ubx.data;

import android.location.Location;

public class UbxNavResetOdo extends UbxData {

    private static final String LOG_TAG = UbxNavResetOdo.class.getSimpleName();

    private static final byte[] base =
            {(byte) 0xB5, (byte) 0x62, (byte) 0x01, (byte) 0x10 // header
                    , (byte) 0x00, (byte) 0x00 // len
                    , (byte) 0x11, (byte) 0x34}; // checksum

    public UbxNavResetOdo(byte[] data) {
        super(data);
    }

    public UbxNavResetOdo() {
        byte[] data = (byte[])base.clone();
        this.data = data;
    }

    @Override
    public boolean parse(Location fix) {
        return false;
    }

    @Override
    public long getITow() {
        return -1;
    }
}
