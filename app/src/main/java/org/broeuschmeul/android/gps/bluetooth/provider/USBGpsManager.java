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

package org.broeuschmeul.android.gps.bluetooth.provider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.broeuschmeul.android.gps.usb.provider.BuildConfig;
import org.broeuschmeul.android.gps.usb.provider.R;
import org.broeuschmeul.android.gps.nmea.util.NmeaParser;
import org.broeuschmeul.android.gps.sirf.util.SirfUtils;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.location.GpsStatus.NmeaListener;
import android.os.Build;
import android.preference.PreferenceManager;
import android.app.AppOpsManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.Log;


/**
 * This class is used to establish and manage the connection with the bluetooth GPS.
 *
 * @author Herbert von Broeuschmeul
 */
public class USBGpsManager {

    /**
     * Tag used for log messages
     */
    private static final String LOG_TAG = "UsbGPS";
    private boolean debug = false;

    private UsbManager usbManager = null;
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private final BroadcastReceiver mUsbReceiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device.getVendorId() == GPSVendorId & device.getProductId() == GPSProductId) {
                    Log.e(LOG_TAG, "USB device detached, stopping service");
                    disable();
                }
            }
        }
    };

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.e(LOG_TAG, action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    final UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            if (usbManager.hasPermission(device)) {
                                Log.d(LOG_TAG, "We have permission, good!");
                                connected = true;
                                if (setDeviceSpeed) {
                                    Log.v(LOG_TAG, "will set device speed: " + deviceSpeed);
                                } else {
                                    Log.v(LOG_TAG, "will use default device speed: " + defaultDeviceSpeed);
                                    deviceSpeed = defaultDeviceSpeed;
                                }
//								// reset eventual disabling cause
//								// setDisableReason(0);
//								// connection obtained so reset the number of connection try
//								nbRetriesRemaining = 1+maxConnectionRetries ;
//								notificationManager.cancel(R.string.connection_problem_notification_title);
                                Log.v(LOG_TAG, "starting usb reading task");
                                connectedGps = new ConnectedGps(device, deviceSpeed);
                                connectionAndReadingPool.execute(connectedGps);
                                Log.v(LOG_TAG, "usb reading thread started");
                            }
                        }
                    } else {
                        Log.d(LOG_TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    /**
     * A utility class used to manage the communication with the bluetooth GPS whn the connection has been established.
     * It is used to read NMEA data from the GPS or to send SIRF III binary commands or SIRF III NMEA commands to the GPS.
     * You should run the main read loop in one thread and send the commands in a separate one.
     *
     * @author Herbert von Broeuschmeul
     */
    private class ConnectedGps extends Thread {
        /**
         * GPS bluetooth socket used for communication.
         */
        private final File gpsDev;
        private final UsbDevice gpsUsbDev;
        private final UsbInterface intf;
        private UsbEndpoint endpointIn = null;
        private UsbEndpoint endpointOut = null;
        private final UsbDeviceConnection connection;
        private boolean closed = false;
        /**
         * GPS InputStream from which we read data.
         */
        private final InputStream in;
        /**
         * GPS output stream to which we send data (SIRF III binary commands).
         */
        private final OutputStream out;
        /**
         * GPS output stream to which we send data (SIRF III NMEA commands).
         */
        private final PrintStream out2;
        /**
         * A boolean which indicates if the GPS is ready to receive data.
         * In fact we consider that the GPS is ready when it begins to sends data...
         */
        private boolean ready = false;

        public ConnectedGps(UsbDevice device) {
            this(device, defaultDeviceSpeed);
        }

        public ConnectedGps(UsbDevice device, String deviceSpeed) {
            this.gpsDev = null;
            this.gpsUsbDev = device;
            intf = device.getInterface(0);
            int i = intf.getEndpointCount();
            endpointIn = null;
            while (i > 1) {
                UsbEndpoint curEndpoint = intf.getEndpoint(--i);
                if ((curEndpoint.getDirection() == UsbConstants.USB_DIR_IN) && (curEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                    endpointIn = curEndpoint;
                }
                if ((curEndpoint.getDirection() == UsbConstants.USB_DIR_OUT) && (curEndpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK)) {
                    endpointOut = curEndpoint;
                }
                if ((endpointIn != null) && (endpointOut != null)) {
                    i = 0;
                }
            }
//			endpointIn = intf.getEndpoint(2);
            final int TIMEOUT = 1000;
//			final int TIMEOUT = 0;
            connection = usbManager.openDevice(device);
            boolean resclaim = false;
            Log.d(LOG_TAG, "claiming interface");
            resclaim = connection.claimInterface(intf, true);
            Log.d(LOG_TAG, "data claim " + resclaim);

            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            PrintStream tmpOut2 = null;

            tmpIn = new InputStream() {
                private byte[] buffer = new byte[128];
                private byte[] usbBuffer = new byte[64];
                private byte[] oneByteBuffer = new byte[1];
                private ByteBuffer bufferWrite = ByteBuffer.wrap(buffer);
                private ByteBuffer bufferRead = (ByteBuffer) ByteBuffer.wrap(buffer).limit(0);
                private boolean closed = false;

                @Override
                public int read() throws IOException {
                    int b = 0;
                    //if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "trying to read data");
                    int nb = 0;
                    while ((nb == 0) && (!closed)) {
                        nb = this.read(oneByteBuffer, 0, 1);
                    }
                    if (nb > 0) {
                        b = oneByteBuffer[0];
                    } else {
                        // TODO : if nb = 0 then we have a pb
                        b = -1;
                        Log.e(LOG_TAG, "data read() error code: " + nb);
                    }
                    if (b <= 0) {
                        Log.e(LOG_TAG, "data read() error: char " + b);
                    }
                    //if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data: " + b + " char: " + (char)b);
                    return b;
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#available()
                 */
                @Override
                public int available() throws IOException {
                    // TODO Auto-generated method stub
                    //if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data available "+bufferRead.remaining());
                    return bufferRead.remaining();
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#mark(int)
                 */
                @Override
                public void mark(int readlimit) {
                    // TODO Auto-generated method stub
                    //if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data mark");
                    super.mark(readlimit);
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#markSupported()
                 */
                @Override
                public boolean markSupported() {
                    // TODO Auto-generated method stub
                    //if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data markSupported");
                    return super.markSupported();
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#read(byte[], int, int)
                 */
                @Override
                public int read(byte[] buffer, int offset, int length)
                        throws IOException {
                    //if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data read buffer - offset: " + offset + " length: " + length);
                    int nb = 0;
                    ByteBuffer out = ByteBuffer.wrap(buffer, offset, length);
                    if ((!bufferRead.hasRemaining()) && (!closed)) {
                        //if (BuildConfig.DEBUG || debug) Log.i(LOG_TAG, "data read buffer empty " + Arrays.toString(usbBuffer));
                        int n = connection.bulkTransfer(endpointIn, usbBuffer, 64, TIMEOUT);
                        //if (BuildConfig.DEBUG || debug) Log.w(LOG_TAG, "data read: nb: " + n + " " + Arrays.toString(usbBuffer));
                        if (n > 0) {
                            if (n > bufferWrite.remaining()) {
                                bufferRead.rewind();
                                bufferWrite.clear();
                            }
                            bufferWrite.put(usbBuffer, 0, n);
                            bufferRead.limit(bufferWrite.position());
//							if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data read: nb: " + n + " current: " + bufferRead.position() + " limit: " + bufferRead.limit() + " " + Arrays.toString(bufferRead.array()));
                        } else {
                            //	Log.e(LOG_TAG, "data read(buffer...) error: " + nb );
                        }
                    }
                    if (bufferRead.hasRemaining()) {
//						if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data : asked: " + length + " current: " + bufferRead.position() + " limit: " + bufferRead.limit() + " " + Arrays.toString(bufferRead.array()));
                        nb = Math.min(bufferRead.remaining(), length);
                        out.put(bufferRead.array(), bufferRead.position() + bufferRead.arrayOffset(), nb);
                        bufferRead.position(bufferRead.position() + nb);
//						if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data : given: " + nb + " current: " + bufferRead.position() + " limit: " + bufferRead.limit() + " " + Arrays.toString(bufferRead.array()));
//						if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data : given: " + nb + " offset: " + offset + " " + Arrays.toString(buffer));
                    }
                    return nb;
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#read(byte[])
                 */
                @Override
                public int read(byte[] buffer) throws IOException {
                    // TODO Auto-generated method stub
                    if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data read buffer");
                    return super.read(buffer);
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#reset()
                 */
                @Override
                public synchronized void reset() throws IOException {
                    // TODO Auto-generated method stub
                    if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data reset");
                    super.reset();
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#skip(long)
                 */
                @Override
                public long skip(long byteCount) throws IOException {
                    // TODO Auto-generated method stub
                    if (BuildConfig.DEBUG || debug) Log.d(LOG_TAG, "data skip");
                    return super.skip(byteCount);
                }

                /* (non-Javadoc)
                 * @see java.io.InputStream#close()
                 */
                @Override
                public void close() throws IOException {
                    super.close();
                    closed = true;
                }
            };

            tmpOut = new OutputStream() {
                private byte[] buffer = new byte[128];
                private byte[] usbBuffer = new byte[64];
                private byte[] oneByteBuffer = new byte[1];
                private ByteBuffer bufferWrite = ByteBuffer.wrap(buffer);
                private ByteBuffer bufferRead = (ByteBuffer) ByteBuffer.wrap(buffer).limit(0);
                private boolean closed = false;

                @Override
                public void write(int oneByte) throws IOException {
                    if (BuildConfig.DEBUG || debug)
                        Log.d(LOG_TAG, "trying to write data (one byte): " + oneByte + " char: " + (char) oneByte);
                    oneByteBuffer[0] = (byte) oneByte;
                    this.write(oneByteBuffer, 0, 1);
                    if (BuildConfig.DEBUG || debug)
                        Log.d(LOG_TAG, "writen data (one byte): " + oneByte + " char: " + (char) oneByte);
                }

                /* (non-Javadoc)
                 * @see java.io.OutputStream#write(byte[], int, int)
                 */
                @Override
                public void write(byte[] buffer, int offset, int count)
                        throws IOException {
                    if (BuildConfig.DEBUG || debug)
                        Log.d(LOG_TAG, "trying to write data : " + Arrays.toString(buffer) + " offset " + offset + " count: " + count);
                    bufferWrite.clear();
                    bufferWrite.put(buffer, offset, count);
                    if (BuildConfig.DEBUG || debug)
                        Log.d(LOG_TAG, "trying to write data : " + Arrays.toString(this.buffer));
                    int n = 0;
                    if (!closed) {
                        n = connection.bulkTransfer(endpointOut, this.buffer, count, TIMEOUT);
                    } else {
                        Log.e(LOG_TAG, "error while trying to write data: outputStream closed");
                    }
                    if (n != count) {
                        Log.e(LOG_TAG, "error while trying to write data: " + Arrays.toString(this.buffer));
                        Log.e(LOG_TAG, "error while trying to write data: " + n + " bytes writen when expecting " + count);
                        throw new IOException("error while trying to write data: " + Arrays.toString(this.buffer));
                    }
                    if (BuildConfig.DEBUG || debug)
                        Log.d(LOG_TAG, "writen data (one byte): " + Arrays.toString(this.buffer));
                }

                /* (non-Javadoc)
                 * @see java.io.OutputStream#close()
                 */
                @Override
                public void close() throws IOException {
                    // TODO Auto-generated method stub
                    super.close();
                    closed = true;
                }

                /* (non-Javadoc)
                 * @see java.io.OutputStream#flush()
                 */
                @Override
                public void flush() throws IOException {
                    // TODO Auto-generated method stub
                    super.flush();
                }

                /* (non-Javadoc)
                 * @see java.io.OutputStream#write(byte[])
                 */
                @Override
                public void write(byte[] buffer) throws IOException {
                    // TODO Auto-generated method stub
                    super.write(buffer);
                }

            };

            try {
                if (tmpOut != null) {
                    tmpOut2 = new PrintStream(tmpOut, false, "US-ASCII");
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(LOG_TAG, "error while getting usb output streams", e);
            }

            in = tmpIn;
            out = tmpOut;
            out2 = tmpOut2;
            final int[] speedList = {Integer.parseInt(deviceSpeed), 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200};
//			final List<String> speedList = Arrays.asList(new String[]{"1200", "2400", "4800", "9600", "19200", "38400", "57600", "115200"});
            final byte[] data = {(byte) 0xC0, 0x12, 0x00, 0x00, 0x00, 0x00, 0x08};
            final ByteBuffer connectionSpeedBuffer = ByteBuffer.wrap(data, 0, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            final byte[] sirfBin2Nmea = SirfUtils.genSirfCommandFromPayload(callingService.getString(R.string.sirf_bin_to_nmea));
            final byte[] datax = new byte[7];
            final ByteBuffer connectionSpeedInfoBuffer = ByteBuffer.wrap(datax, 0, 7).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            final int res1 = connection.controlTransfer(0x21, 34, 0, 0, null, 0, 0);
            if (setDeviceSpeed) {
////				connection.controlTransfer(0x40, 0, 0, 0, null, 0, 0);				//reset
////				connection.controlTransfer(0x40, 0, 1, 0, null, 0, 0);				//clear Rx
////				connection.controlTransfer(0x40, 0, 2, 0, null, 0, 0);				//clear Tx
////				connection.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 0);	//flow control none
////				connection.controlTransfer(0x40, 0x03, 0x4138, 0, null, 0, 0);	//baudrate 9600
////				connection.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 0);	//data bit 8, parity none, stop bit 1, tx off
            }
            if (sirfGps) {
                Log.e(LOG_TAG, "trying to switch from SiRF binaray to NMEA");
                connection.bulkTransfer(endpointOut, sirfBin2Nmea, sirfBin2Nmea.length, 0);
            }
            Thread autoConf = new Thread() {

                /* (non-Javadoc)
                 * @see java.lang.Thread#run()
                 */
                @Override
                public void run() {
//					final byte[] data = { (byte) 0xC0, 0x12, 0x00, 0x00, 0x00, 0x00, 0x08 };
//					final ByteBuffer connectionSpeedBuffer = ByteBuffer.wrap(data, 0, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
//					final byte[] sirfBin2Nmea = SirfUtils.genSirfCommandFromPayload(callingService.getString(R.string.sirf_bin_to_nmea));
//					final byte[] datax = new byte[7];
//					final ByteBuffer connectionSpeedInfoBuffer = ByteBuffer.wrap(datax,0,7).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    try {
                        int res0 = connection.controlTransfer(0xA1, 33, 0, 0, datax, 7, 0);
                        USBGpsManager.this.deviceSpeed = Integer.toString(connectionSpeedInfoBuffer.getInt(0));
                        Log.e(LOG_TAG, "info connection: " + Arrays.toString(datax));
                        Log.e(LOG_TAG, "info connection speed: " + USBGpsManager.this.deviceSpeed);
                        Thread.sleep(4000);
                        Log.e(LOG_TAG, "trying to use speed in range: " + Arrays.toString(speedList));
                        for (int speed : speedList) {
                            if (!ready && !closed) {
                                USBGpsManager.this.deviceSpeed = Integer.toString(speed);
                                Log.e(LOG_TAG, "trying to use speed " + speed);
                                Log.d(LOG_TAG, "initializing connection:  " + speed + " baud and 8N1 (0 bits no parity 1 stop bit");
                                connectionSpeedBuffer.putInt(0, speed);
                                int res2 = connection.controlTransfer(0x21, 32, 0, 0, data, 7, 0);
                                if (sirfGps) {
                                    Log.e(LOG_TAG, "trying to switch from SiRF binaray to NMEA");
                                    connection.bulkTransfer(endpointOut, sirfBin2Nmea, sirfBin2Nmea.length, 0);
                                }
                                Log.e(LOG_TAG, "data init " + res1 + " " + res2);
                                Thread.sleep(4000);
                            }
                        }
                        res0 = connection.controlTransfer(0xA1, 33, 0, 0, datax, 7, 0);
                        Log.e(LOG_TAG, "info connection: " + Arrays.toString(datax));
                        Log.e(LOG_TAG, "info connection speed: " + connectionSpeedInfoBuffer.getInt(0));
                        if (!closed) {
                            Thread.sleep(10000);
                        }
                    } catch (InterruptedException e) {
                        Log.e(LOG_TAG, "autoconf thread interupted", e);
                    } finally {
                        if ((!closed) && (!ready) || (lastRead + 4000 < SystemClock.uptimeMillis())) {
                            setMockLocationProviderOutOfService();
                            Log.e(LOG_TAG, "Something went wrong in auto config");
                            // cleanly closing everything...
                            ConnectedGps.this.close();
                            USBGpsManager.this.disableIfNeeded();
                        }
                    }
                }

            };
            Log.e(LOG_TAG, "trying to find speed ");
            ready = false;
            autoConf.start();
        }

        public boolean isReady() {
            return ready;
        }

        private long lastRead = 0;

        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, "US-ASCII"), 128);
//				InputStreamReader reader = new InputStreamReader(in,"US-ASCII");
                String s;
                long now = SystemClock.uptimeMillis();
                // we will wait more at the beginning of the connection
                lastRead = now + 45000;
                while ((enabled) && (now < lastRead + 4000) && (!closed)) {
//					if (true || reader.ready())
                    {
                        s = reader.readLine();
                        if (s != null) {
                            //Log.v(LOG_TAG, "data: "+System.currentTimeMillis()+" "+s);
                            if (notifyNmeaSentence(s + "\r\n")) {
                                ready = true;
//								lastRead = Math.max(SystemClock.uptimeMillis(), lastRead);
                                lastRead = SystemClock.uptimeMillis();
                                if (nbRetriesRemaining < maxConnectionRetries) {
                                    // reset eventual disabling cause
                                    // setDisableReason(0);
                                    // connection is good so reseting the number of connection try
                                    Log.v(LOG_TAG, "connection is good so reseting the number of connection try");
                                    nbRetriesRemaining = maxConnectionRetries;
                                    notificationManager.cancel(R.string.connection_problem_notification_title);
                                }
                            }
                        } else {
                            Log.d(LOG_TAG, "data: not ready " + System.currentTimeMillis());
                            SystemClock.sleep(500);
                        }
                    }
//					SystemClock.sleep(10);
                    now = SystemClock.uptimeMillis();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "error while getting data", e);
                setMockLocationProviderOutOfService();
            } finally {
                // cleanly closing everything...
                Log.e(LOG_TAG, "Something went wrong getting data");
                this.close();
                disableIfNeeded();
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                do {
                    Thread.sleep(100);
                } while ((enabled) && (!ready) && (!closed));
                if ((enabled) && (ready) && (!closed)) {
                    out.write(buffer);
                    out.flush();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Exception during write", e);
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Exception during write", e);
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The data to write
         */
        public void write(String buffer) {
            try {
                do {
                    Thread.sleep(100);
                } while ((enabled) && (!ready) && (!closed));
                if ((enabled) && (ready) && (!closed)) {
                    out2.print(buffer);
                    out2.flush();
                }
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Exception during write", e);
            }
        }

        public void close() {
            ready = false;
            closed = true;
            try {
                Log.d(LOG_TAG, "closing USB GPS output sream");
                in.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "error while closing GPS NMEA output stream", e);
            } finally {
                try {
                    Log.d(LOG_TAG, "closing USB GPS input streams");
                    out2.close();
                    out.close();
                } catch (IOException e) {
                    Log.e(LOG_TAG, "error while closing GPS input streams", e);
                } finally {
                    if (BuildConfig.DEBUG || debug)
                        Log.e(LOG_TAG, "releasing usb interface for connection: " + connection);
                    boolean released = connection.releaseInterface(intf);
                    if (released) {
                        if (BuildConfig.DEBUG || debug)
                            Log.e(LOG_TAG, "usb interface released for connection: " + connection);
                    } else {
                        if (BuildConfig.DEBUG || debug)
                            Log.e(LOG_TAG, "unable to release usb interface for connection: " + connection);
                    }
                    if (BuildConfig.DEBUG || debug)
                        Log.e(LOG_TAG, "closing usb connection: " + connection);
                    connection.close();
//					try {
//			        	Log.d(LOG_TAG, "closing Bluetooth GPS socket");
//						socket.close();
//					} catch (IOException e) {
//						Log.e(LOG_TAG, "error while closing GPS socket", e);
//					}
                }
            }
        }
    }

    private Service callingService;
    private UsbDevice gpsDev;
    private String gpsDeviceAddress;
    private NmeaParser parser = new NmeaParser(10f);
    private boolean enabled = false;
    private ExecutorService notificationPool;
    private ScheduledExecutorService connectionAndReadingPool;
    private List<NmeaListener> nmeaListeners = Collections.synchronizedList(new LinkedList<NmeaListener>());
    private LocationManager locationManager;
    private SharedPreferences sharedPreferences;
    private ConnectedGps connectedGps;
    private int disableReason = 0;

    private NotificationCompat.Builder connectionProblemNotificationBuilder;
    private NotificationCompat.Builder serviceStoppedNotificationBuilder;

    private Context appContext;
    private NotificationManager notificationManager;
    private int maxConnectionRetries;
    private int nbRetriesRemaining;
    private boolean connected = false;
    private boolean setDeviceSpeed = false;
    private boolean sirfGps = false;
    private String deviceSpeed = "auto";
    private String defaultDeviceSpeed = "4800";
    private int GPSProductId = 8963;
    private int GPSVendorId = 1659;

    /**
     * @param callingService
     * @param deviceAddress
     * @param maxRetries
     */
    public USBGpsManager(Service callingService, String deviceAddress, int maxRetries) {
        this.gpsDeviceAddress = deviceAddress;
        this.callingService = callingService;
        this.maxConnectionRetries = maxRetries;
        this.nbRetriesRemaining = 1 + maxRetries;
        this.appContext = callingService.getApplicationContext();
        locationManager = (LocationManager) callingService.getSystemService(Context.LOCATION_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(callingService);
        deviceSpeed = sharedPreferences.getString(USBGpsProviderService.PREF_GPS_DEVICE_SPEED, callingService.getString(R.string.defaultGpsDeviceSpeed));
        defaultDeviceSpeed = callingService.getString(R.string.defaultGpsDeviceSpeed);
        setDeviceSpeed = !deviceSpeed.equals(callingService.getString(R.string.autoGpsDeviceSpeed));
        sirfGps = sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_GPS, false);
        notificationManager = (NotificationManager) callingService.getSystemService(Context.NOTIFICATION_SERVICE);
        parser.setLocationManager(locationManager);



        Intent stopIntent = new Intent(USBGpsProviderService.ACTION_STOP_GPS_PROVIDER);
        // PendingIntent stopPendingIntent = PendingIntent.getService(appContext, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent stopPendingIntent = PendingIntent.getService(appContext, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        connectionProblemNotificationBuilder = new NotificationCompat.Builder(appContext)
                .setContentIntent(stopPendingIntent)
                .setSmallIcon(R.drawable.ic_stat_notify);


        Intent restartIntent = new Intent(USBGpsProviderService.ACTION_START_GPS_PROVIDER);
        PendingIntent restartPendingIntent = PendingIntent.getService(appContext, 0, restartIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        serviceStoppedNotificationBuilder = new NotificationCompat.Builder(appContext)
                .setContentIntent(restartPendingIntent)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(appContext.getString(R.string.service_closed_because_connection_problem_notification_title))
                .setContentText(appContext.getString(R.string.service_closed_because_connection_problem_notification));

        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        callingService.registerReceiver(mUsbReceiver2, filter);

        usbManager = (UsbManager) callingService.getSystemService(callingService.USB_SERVICE);

    }

    private void setDisableReason(int reasonId) {
        disableReason = reasonId;
    }

    /**
     * @return
     */
    public int getDisableReason() {
        return disableReason;
    }

    /**
     * @return true if the bluetooth GPS is enabled
     */
    public synchronized boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables the bluetooth GPS Provider.
     *
     * @return
     */
    public boolean isMockLocationEnabled() {
        boolean isMockLocation = false;
        try {
            //if marshmallow
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AppOpsManager opsManager = (AppOpsManager) appContext.getSystemService(Context.APP_OPS_SERVICE);
                isMockLocation = (opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), BuildConfig.APPLICATION_ID) == AppOpsManager.MODE_ALLOWED);
            } else {
                // in marshmallow this will always return true
                isMockLocation = !android.provider.Settings.Secure.getString(appContext.getContentResolver(), "mock_location").equals("0");
            }
        } catch (Exception e) {
            return isMockLocation;
        }

        return isMockLocation;
    }

    public synchronized boolean enable() {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        notificationManager.cancel(R.string.service_closed_because_connection_problem_notification_title);
        if (!enabled) {
            Log.d(LOG_TAG, "enabling Bluetooth GPS manager");
//			final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//	        if (bluetoothAdapter == null) {
//	            // Device does not support Bluetooth
//	        	Log.e(LOG_TAG, "Device does not support Bluetooth");
//	        	disable(R.string.msg_bluetooth_unsupported);
//	        } else if (!bluetoothAdapter.isEnabled()) {
//	        	// Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//	        	// startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
//	        	Log.e(LOG_TAG, "Bluetooth is not enabled");
//	        	disable(R.string.msg_bluetooth_disabled);
//	        } else
            if (!isMockLocationEnabled()) {
                Log.e(LOG_TAG, "Mock location provider OFF");
                disable(R.string.msg_mock_location_disabled);
//	        } else if ( (! locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
//	        		 && (sharedPreferences.getBoolean(USBGpsProviderService.PREF_REPLACE_STD_GPS, true))
//	        			) {
//	        	Log.e(LOG_TAG, "GPS location provider OFF");
//	        	disable(R.string.msg_gps_provider_disabled);
            } else {
//				final BluetoothDevice gpsDevice = bluetoothAdapter.getRemoteDevice(gpsDeviceAddress);
                final String gpsDevice = gpsDeviceAddress;
                Boolean foundDevice = false;

                for (UsbDevice testDevice : usbManager.getDeviceList().values()) {
                    Log.e(LOG_TAG, "current device: " + testDevice.getProductId() + " " + testDevice.getVendorId());

                    if (testDevice.getVendorId() == GPSVendorId & testDevice.getProductId() == GPSProductId) {
                        Log.v(LOG_TAG, "Found correct device");
                        //					try {
                        //						gpsSocket = gpsDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                        //						gpsDev = new File(gpsDeviceAddress);
                        gpsDev = testDevice;
                        //					} catch (IOException e) {
                        //	    				Log.e(LOG_TAG, "Error during connection", e);
                        //	    				gpsDev = null;
                        //					}
                        if (gpsDev == null) {
                            Log.e(LOG_TAG, "Error while establishing connection: no device");
                            disable(R.string.msg_gps_unavaible);
                        } else {
                            Runnable connectThread = new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        connected = false;
                                        Log.v(LOG_TAG, "current device: " + gpsDev.getDeviceName());
                                        if (/*(bluetoothAdapter.isEnabled()) && */(nbRetriesRemaining > 0)) {
                                            //										try {
                                            if (connectedGps != null) {
                                                connectedGps.close();
                                            }
                                            PendingIntent permissionIntent = PendingIntent.getBroadcast(callingService, 0, new Intent(ACTION_USB_PERMISSION), 0);
                                            //								            HashMap<String, UsbDevice> connectedUsbDevices = usbManager.getDeviceList();
                                            //								            UsbDevice device = connectedUsbDevices.values().iterator().next();
                                            UsbDevice device = gpsDev;
                                            if (device != null && usbManager.hasPermission(device)) {
                                                Log.d(LOG_TAG, "We have permession, good!");
                                                connected = true;
                                                if (setDeviceSpeed) {
                                                    Log.v(LOG_TAG, "will set devive speed: " + deviceSpeed);
                                                } else {
                                                    Log.v(LOG_TAG, "will use default device speed: " + defaultDeviceSpeed);
                                                    deviceSpeed = defaultDeviceSpeed;
                                                }
                                                //												// reset eventual disabling cause
                                                //												// setDisableReason(0);
                                                //												// connection obtained so reset the number of connection try
                                                //												nbRetriesRemaining = 1+maxConnectionRetries ;
                                                //												notificationManager.cancel(R.string.connection_problem_notification_title);
                                                Log.v(LOG_TAG, "starting usb reading task");
                                                connectedGps = new ConnectedGps(device, deviceSpeed);
                                                connectionAndReadingPool.execute(connectedGps);
                                                Log.v(LOG_TAG, "usb reading thread started");
                                            } else if (device != null) {
                                                Log.d(LOG_TAG, "We don't have permession, so resquesting...");
                                                usbManager.requestPermission(device, permissionIntent);
                                            } else {
                                                Log.e(LOG_TAG, "Error while establishing connection: no device - " + gpsDevice);
                                                disable(R.string.msg_gps_unavaible);
                                            }
                                        }
                                    } finally {
                                        nbRetriesRemaining--;
                                        if (!connected) {
                                            disableIfNeeded();
                                        }
                                    }
                                }
                            };
                            this.enabled = true;
                            callingService.registerReceiver(mUsbReceiver, filter);
                            Log.d(LOG_TAG, "Bluetooth GPS manager enabled");
                            Log.v(LOG_TAG, "starting notification thread");
                            notificationPool = Executors.newSingleThreadExecutor();
                            Log.v(LOG_TAG, "starting connection and reading thread");
                            connectionAndReadingPool = Executors.newSingleThreadScheduledExecutor();
                            Log.v(LOG_TAG, "starting connection to socket task");
                            connectionAndReadingPool.scheduleWithFixedDelay(connectThread, 5000, 60000, TimeUnit.MILLISECONDS);
                            if (sirfGps) {
                                enableSirfConfig(sharedPreferences);
                            }
                        }
                        ;
                        foundDevice = true;
                        break;
                    }
                }
                if (!foundDevice) {
                    return false;
                }
            }
        }
        return this.enabled;
    }

    /**
     * Disables the bluetooth GPS Provider if the maximal number of connection retries is exceeded.
     * This is used when there are possibly non fatal connection problems.
     * In these cases the provider will try to reconnect with the bluetooth device
     * and only after a given retries number will give up and shutdown the service.
     */
    private synchronized void disableIfNeeded() {
        if (enabled) {
            if (nbRetriesRemaining > 0) {
                // Unable to connect
                Log.e(LOG_TAG, "Unable to establish connection");

                String pbMessage = appContext.getResources().getQuantityString(R.plurals.connection_problem_notification, nbRetriesRemaining, nbRetriesRemaining);

                Notification connectionProblemNotification = connectionProblemNotificationBuilder
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle(appContext.getString(R.string.connection_problem_notification_title))
                        .setContentText(pbMessage)
                        .setNumber(1 + maxConnectionRetries - nbRetriesRemaining)
                        .build();

                notificationManager.notify(R.string.connection_problem_notification_title, connectionProblemNotification);

            } else {
//				notificationManager.cancel(R.string.connection_problem_notificzation_title);
//				serviceStoppedNotification.when = System.currentTimeMillis();
//				notificationManager.notify(R.string.service_closed_because_connection_problem_notification_title, serviceStoppedNotification);
                disable(R.string.msg_two_many_connection_problems);
            }
        }
    }

    /**
     * Disables the bluetooth GPS provider.
     * <p>
     * It will:
     * <ul>
     * <li>close the connection with the bluetooth device</li>
     * <li>disable the Mock Location Provider used for the bluetooth GPS</li>
     * <li>stop the BlueGPS4Droid service</li>
     * </ul>
     * The reasonId parameter indicates the reason to close the bluetooth provider.
     * If its value is zero, it's a normal shutdown (normally, initiated by the user).
     * If it's non-zero this value should correspond a valid localized string id (res/values..../...)
     * which will be used to display a notification.
     *
     * @param reasonId the reason to close the bluetooth provider.
     */
    public synchronized void disable(int reasonId) {
        Log.d(LOG_TAG, "disabling Bluetooth GPS manager reason: " + callingService.getString(reasonId));
        setDisableReason(reasonId);
        disable();
    }

    /**
     * Disables the bluetooth GPS provider.
     * <p>
     * It will:
     * <ul>
     * <li>close the connection with the bluetooth device</li>
     * <li>disable the Mock Location Provider used for the bluetooth GPS</li>
     * <li>stop the BlueGPS4Droid service</li>
     * </ul>
     * If the bluetooth provider is closed because of a problem, a notification is displayed.
     */
    public synchronized void disable() {
        notificationManager.cancel(R.string.connection_problem_notification_title);
        if (getDisableReason() != 0) {

            Notification serviceStoppedNotification = serviceStoppedNotificationBuilder
                    .setWhen(System.currentTimeMillis())
                    .setContentText(appContext.getString(R.string.service_closed_because_connection_problem_notification, appContext.getString(getDisableReason())))
                    .setContentTitle(appContext.getString(R.string.service_closed_because_connection_problem_notification_title))
                    .build();

            notificationManager.notify(R.string.service_closed_because_connection_problem_notification_title, serviceStoppedNotification);
        }
        if (enabled) {
            Log.d(LOG_TAG, "disabling Bluetooth GPS manager");
            callingService.unregisterReceiver(mUsbReceiver);
            callingService.unregisterReceiver(mUsbReceiver2);
            enabled = false;
            connectionAndReadingPool.shutdown();
            // stop GPS device
//			try {
//				File gpsControl = new File("/sys/devices/platform/gps_control/enable");
//				if (gpsControl.isFile()){												
//					if (gpsControl.canWrite()){
//						OutputStream os = new FileOutputStream(gpsControl);
//						os.write('0');
//						os.flush();
//						os.close();
//					}
//				}
//			} catch (FileNotFoundException e) {
//				// TODO update message
//				Log.e(LOG_TAG, "Error while stoping GPS: ", e);
//			} catch (IOException e) {
//				// TODO update message
//				Log.e(LOG_TAG, "Error while stoping GPS: ", e);
//			} catch (SecurityException e) {
//				// TODO update message
//				Log.e(LOG_TAG, "Error while stoping GPS: ", e);
//			}
            Runnable closeAndShutdown = new Runnable() {
                @Override
                public void run() {
                    try {
                        connectionAndReadingPool.awaitTermination(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (!connectionAndReadingPool.isTerminated()) {
                        connectionAndReadingPool.shutdownNow();
                        if (connectedGps != null) {
                            connectedGps.close();
                        }
//						if ((gpsDev != null) && ((connectedGps == null) || (connectedGps.gpsDev != gpsDev))){
//							try {
//								Log.d(LOG_TAG, "closing Bluetooth GPS socket");
//								gpsDev.close();
//							} catch (IOException closeException) {
//								Log.e(LOG_TAG, "error while closing socket", closeException);
//							}
//						}
                    }
                }
            };
            notificationPool.execute(closeAndShutdown);
            nmeaListeners.clear();
            disableMockLocationProvider();
            notificationPool.shutdown();
//			connectionAndReadingPool.shutdown();
            callingService.stopSelf();
            Log.d(LOG_TAG, "Bluetooth GPS manager disabled");
        }
    }

    /**
     * Enables the Mock GPS Location Provider used for the bluetooth GPS.
     * In fact, it delegates to the NMEA parser.
     *
     * @param gpsName the name of the Location Provider to use for the bluetooth GPS
     * @param force   true if we want to force auto-activation of the mock location provider (and bypass user preference).
     * @see NmeaParser#enableMockLocationProvider(java.lang.String)
     */
    public void enableMockLocationProvider(String gpsName, boolean force) {
        if (parser != null) {
            Log.d(LOG_TAG, "enabling mock locations provider: " + gpsName);
            parser.enableMockLocationProvider(gpsName, force);
        }
    }

    /**
     * Enables the Mock GPS Location Provider used for the bluetooth GPS.
     * In fact, it delegates to the NMEA parser.
     *
     * @param gpsName the name of the Location Provider to use for the bluetooth GPS
     * @see NmeaParser#enableMockLocationProvider(java.lang.String)
     */
    public void enableMockLocationProvider(String gpsName) {
        if (parser != null) {
            Log.d(LOG_TAG, "enabling mock locations provider: " + gpsName);
            boolean force = sharedPreferences.getBoolean(USBGpsProviderService.PREF_FORCE_ENABLE_PROVIDER, true);
            parser.enableMockLocationProvider(gpsName, force);
        }
    }

    /**
     * Disables the current Mock GPS Location Provider used for the bluetooth GPS.
     * In fact, it delegates to the NMEA parser.
     *
     * @see NmeaParser#disableMockLocationProvider()
     */
    public void disableMockLocationProvider() {
        if (parser != null) {
            Log.d(LOG_TAG, "disabling mock locations provider");
            parser.disableMockLocationProvider();
        }
    }

    /**
     * Getter use to know if the Mock GPS Listener used for the bluetooth GPS is enabled or not.
     * In fact, it delegates to the NMEA parser.
     *
     * @return true if the Mock GPS Listener used for the bluetooth GPS is enabled.
     * @see NmeaParser#isMockGpsEnabled()
     */
    public boolean isMockGpsEnabled() {
        boolean mockGpsEnabled = false;
        if (parser != null) {
            mockGpsEnabled = parser.isMockGpsEnabled();
        }
        return mockGpsEnabled;
    }

    /**
     * Getter for the name of the current Mock Location Provider in use.
     * In fact, it delegates to the NMEA parser.
     *
     * @return the Mock Location Provider name used for the bluetooth GPS
     * @see NmeaParser#getMockLocationProvider()
     */
    public String getMockLocationProvider() {
        String mockLocationProvider = null;
        if (parser != null) {
            mockLocationProvider = parser.getMockLocationProvider();
        }
        return mockLocationProvider;
    }

    /**
     * Indicates that the bluetooth GPS Provider is out of service.
     * In fact, it delegates to the NMEA parser.
     *
     * @see NmeaParser#setMockLocationProviderOutOfService()
     */
    private void setMockLocationProviderOutOfService() {
        if (parser != null) {
            parser.setMockLocationProviderOutOfService();
        }
    }

    /**
     * Adds an NMEA listener.
     * In fact, it delegates to the NMEA parser.
     *
     * @param listener a {@link NmeaListener} object to register
     * @return true if the listener was successfully added
     * @see NmeaParser#addNmeaListener(NmeaListener)
     */
    public boolean addNmeaListener(NmeaListener listener) {
        if (!nmeaListeners.contains(listener)) {
            Log.d(LOG_TAG, "adding new NMEA listener");
            nmeaListeners.add(listener);
        }
        return true;
    }

    /**
     * Removes an NMEA listener.
     * In fact, it delegates to the NMEA parser.
     *
     * @param listener a {@link NmeaListener} object to remove
     * @see NmeaParser#removeNmeaListener(NmeaListener)
     */
    public void removeNmeaListener(NmeaListener listener) {
        Log.d(LOG_TAG, "removing NMEA listener");
        nmeaListeners.remove(listener);
    }

    /**
     * Notifies the reception of a NMEA sentence from the bluetooth GPS to registered NMEA listeners.
     *
     * @param nmeaSentence the complete NMEA sentence received from the bluetooth GPS (i.e. $....*XY where XY is the checksum)
     * @return true if the input string is a valid NMEA sentence, false otherwise.
     */
    private boolean notifyNmeaSentence(final String nmeaSentence) {
        boolean res = false;
        if (enabled) {
            Log.v(LOG_TAG, "parsing and notifying NMEA sentence: " + nmeaSentence);
            String sentence = null;
            try {
                sentence = parser.parseNmeaSentence(nmeaSentence);
            } catch (SecurityException e) {
                Log.e(LOG_TAG, "error while parsing NMEA sentence: " + nmeaSentence, e);
                // a priori Mock Location is disabled
                sentence = null;
                disable(R.string.msg_mock_location_disabled);
            }
            final String recognizedSentence = sentence;
            final long timestamp = System.currentTimeMillis();
            if (recognizedSentence != null) {
                res = true;
                Log.v(LOG_TAG, "notifying NMEA sentence: " + recognizedSentence);
                synchronized (nmeaListeners) {
                    for (final NmeaListener listener : nmeaListeners) {
                        notificationPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                listener.onNmeaReceived(timestamp, recognizedSentence);
                            }
                        });
                    }
                }
            }
        }
        return res;
    }

    /**
     * Sends a NMEA sentence to the bluetooth GPS.
     *
     * @param command the complete NMEA sentence (i.e. $....*XY where XY is the checksum).
     */
    public void sendPackagedNmeaCommand(final String command) {
        Log.e(LOG_TAG, "sending NMEA sentence: " + command);
        connectedGps.write(command);
        Log.e(LOG_TAG, "sent NMEA sentence: " + command);
    }

    /**
     * Sends a SIRF III binary command to the bluetooth GPS.
     *
     * @param commandHexa an hexadecimal string representing a complete binary command
     *                    (i.e. with the <em>Start Sequence</em>, <em>Payload Length</em>, <em>Payload</em>, <em>Message Checksum</em> and <em>End Sequence</em>).
     */
    public void sendPackagedSirfCommand(final String commandHexa) {
        final byte[] command = SirfUtils.genSirfCommand(commandHexa);
        Log.e(LOG_TAG, "sendind SIRF sentence: " + commandHexa);
        connectedGps.write(command);
        Log.e(LOG_TAG, "sent SIRF sentence: " + commandHexa);
    }

    /**
     * Sends a NMEA sentence to the bluetooth GPS.
     *
     * @param sentence the NMEA sentence without the first "$", the last "*" and the checksum.
     */
    public void sendNmeaCommand(String sentence) {
        String command = String.format((Locale) null, "$%s*%02X\r\n", sentence, parser.computeChecksum(sentence));
        sendPackagedNmeaCommand(command);
    }

    /**
     * Sends a SIRF III binary command to the bluetooth GPS.
     *
     * @param payload an hexadecimal string representing the payload of the binary command
     *                (i.e. without <em>Start Sequence</em>, <em>Payload Length</em>, <em>Message Checksum</em> and <em>End Sequence</em>).
     */
    public void sendSirfCommand(String payload) {
        String command = SirfUtils.createSirfCommandFromPayload(payload);
        sendPackagedSirfCommand(command);
    }

    private void enableNMEA(boolean enable) {
//			SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(callingService);
//			String deviceSpeed = sharedPreferences.getString(USBGpsProviderService.PREF_GPS_DEVICE_SPEED, callingService.getString(R.string.defaultGpsDeviceSpeed));
        if (deviceSpeed.equals(callingService.getString(R.string.autoGpsDeviceSpeed))) {
            deviceSpeed = callingService.getString(R.string.defaultGpsDeviceSpeed);
        }
        SystemClock.sleep(400);
        if (enable) {
//				int gll = (sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GLL, false)) ? 1 : 0 ;
//				int vtg = (sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_VTG, false)) ? 1 : 0 ;
//				int gsa = (sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GSA, false)) ? 5 : 0 ;
//				int gsv = (sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GSV, false)) ? 5 : 0 ;
//				int zda = (sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_ZDA, false)) ? 1 : 0 ;
//				int mss = 0;
//				int epe = 0;
//				int gga = 1;
//				int rmc = 1;
//				String command = getString(R.string.sirf_bin_to_nmea_38400_alt, gga, gll, gsa, gsv, rmc, vtg, mss, epe, zda);
//				String command = getString(R.string.sirf_bin_to_nmea_alt, gga, gll, gsa, gsv, rmc, vtg, mss, epe, zda, Integer.parseInt(deviceSpeed));
            String command = callingService.getString(R.string.sirf_bin_to_nmea);
            this.sendSirfCommand(command);
        } else {
//				this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_to_binary));
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_to_binary_alt, Integer.parseInt(deviceSpeed)));
        }
        SystemClock.sleep(400);
    }

    private void enableNmeaGGA(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gga_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gga_off));
        }
    }

    private void enableNmeaGLL(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gll_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gll_off));
        }
    }

    private void enableNmeaGSA(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gsa_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gsa_off));
        }
    }

    private void enableNmeaGSV(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gsv_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gsv_off));
        }
    }

    private void enableNmeaRMC(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_rmc_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_rmc_off));
        }
    }

    private void enableNmeaVTG(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_vtg_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_vtg_off));
        }
    }

    private void enableNmeaZDA(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_zda_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_zda_off));
        }
    }

    private void enableSBAS(boolean enable) {
        if (enable) {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_sbas_on));
        } else {
            this.sendNmeaCommand(callingService.getString(R.string.sirf_nmea_sbas_off));
        }
    }

    public void enableSirfConfig(final Bundle extra) {
        Log.e(LOG_TAG, "spooling SiRF config: " + extra);
        if (isEnabled()) {
            notificationPool.execute(new Runnable() {
                @Override
                public void run() {
                    while ((enabled) && ((!connected) || (connectedGps == null) || (!connectedGps.isReady()))) {
                        Log.v(LOG_TAG, "writing thread is not ready");
                        SystemClock.sleep(500);
                    }
                    if (isEnabled() && (connected) && (connectedGps != null) && (connectedGps.isReady())) {
                        Log.e(LOG_TAG, "init SiRF config: " + extra);
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_GGA)) {
                            enableNmeaGGA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GGA, true));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_RMC)) {
                            enableNmeaRMC(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_RMC, true));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_GLL)) {
                            enableNmeaGLL(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GLL, false));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_VTG)) {
                            enableNmeaVTG(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_VTG, false));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_GSA)) {
                            enableNmeaGSA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GSA, false));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_GSV)) {
                            enableNmeaGSV(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GSV, false));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_ZDA)) {
                            enableNmeaZDA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_ZDA, false));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_STATIC_NAVIGATION)) {
                            enableStaticNavigation(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_STATIC_NAVIGATION, false));
                        } else if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_NMEA)) {
                            enableNMEA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_NMEA, true));
                        }
                        if (extra.containsKey(USBGpsProviderService.PREF_SIRF_ENABLE_SBAS)) {
                            enableSBAS(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_SBAS, true));
                        }
                        Log.e(LOG_TAG, "initialized SiRF config: " + extra);
                    }
                }
            });
        }
    }

    public void enableSirfConfig(final SharedPreferences extra) {
        Log.e(LOG_TAG, "spooling SiRF config: " + extra);
        if (isEnabled()) {
            notificationPool.execute(new Runnable() {
                @Override
                public void run() {
                    while ((enabled) && ((!connected) || (connectedGps == null) || (!connectedGps.isReady()))) {
                        Log.v(LOG_TAG, "writing thread is not ready");
                        SystemClock.sleep(500);
                    }
                    if (isEnabled() && (connected) && (connectedGps != null) && (connectedGps.isReady())) {
                        Log.e(LOG_TAG, "init SiRF config: " + extra);
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_GLL)) {
                            enableNmeaGLL(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GLL, false));
                        }
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_VTG)) {
                            enableNmeaVTG(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_VTG, false));
                        }
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_GSA)) {
                            enableNmeaGSA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GSA, false));
                        }
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_GSV)) {
                            enableNmeaGSV(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GSV, false));
                        }
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_ZDA)) {
                            enableNmeaZDA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_ZDA, false));
                        }
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_STATIC_NAVIGATION)) {
                            enableStaticNavigation(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_STATIC_NAVIGATION, false));
                        } else if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_NMEA)) {
                            enableNMEA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_NMEA, true));
                        }
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_SBAS)) {
                            enableSBAS(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_SBAS, true));
                        }
                        sendNmeaCommand(callingService.getString(R.string.sirf_nmea_gga_on));
                        sendNmeaCommand(callingService.getString(R.string.sirf_nmea_rmc_on));
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_GGA)) {
                            enableNmeaGGA(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_GGA, true));
                        }
                        if (extra.contains(USBGpsProviderService.PREF_SIRF_ENABLE_RMC)) {
                            enableNmeaRMC(extra.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_RMC, true));
                        }
                    }
                }
            });
        }
    }

    private void enableStaticNavigation(boolean enable) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(callingService);
        boolean isInNmeaMode = sharedPreferences.getBoolean(USBGpsProviderService.PREF_SIRF_ENABLE_NMEA, true);
        if (isInNmeaMode) {
            enableNMEA(false);
        }
        if (enable) {
            this.sendSirfCommand(callingService.getString(R.string.sirf_bin_static_nav_on));
        } else {
            this.sendSirfCommand(callingService.getString(R.string.sirf_bin_static_nav_off));
        }
        if (isInNmeaMode) {
            enableNMEA(true);
        }
    }

}
