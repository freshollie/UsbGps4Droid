/*
 * Copyright (C) 2010 Herbert von Broeuschmeul
 * Copyright (C) 2010 BluetoothGPS4Droid Project
 * 
 * This file is part of BluetoothGPS4Droid.
 *
 * BluetoothGPS4Droid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * BluetoothGPS4Droid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with BluetoothGPS4Droid. If not, see <http://www.gnu.org/licenses/>.
 */

package org.broeuschmeul.android.gps.bluetooth.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.broeuschmeul.android.gps.nmea.util.NmeaParser;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.location.GpsStatus.NmeaListener;
import android.os.SystemClock;
import android.util.Log;

public class BlueetoothGpsManager {

	private class ConnectedGps extends Thread {
		private final InputStream in;

		public ConnectedGps(BluetoothSocket socket) {
			InputStream tmpIn = null;
			try {
				tmpIn = socket.getInputStream();
			} catch (IOException e) {
				Log.e("BT test", "error while getting socket streams", e);
			}	
			in = tmpIn;
		}
	
		public void run() {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(in,"US-ASCII"));
				String s;
				long now = SystemClock.uptimeMillis();
				long lastRead = now;
				while((enabled) && (now < lastRead+5000 )){
					if (reader.ready()){
						s = reader.readLine();
						Log.e("BT test", "data: "+System.currentTimeMillis()+" "+s + "xxx");
						notifyNmeaSentence(s+"\r\n");
						lastRead = SystemClock.uptimeMillis();
					} else {
						Log.e("BT test", "data: not ready "+System.currentTimeMillis());
						SystemClock.sleep(500);
					}
					now = SystemClock.uptimeMillis();
				}
			} catch (IOException e) {
				Log.e("BT test", "error while getting data", e);
				setMockLocationProviderOutOfService();
			} finally {
				// remove because we want to retry...
				// disable();
			}
		}
	}

	private Service callingService;
	private BluetoothSocket gpsSocket;
	private String gpsDeviceAddress;
	private NmeaParser parser = new NmeaParser(10f);
	private boolean enabled = false;
	private ExecutorService notificationPool;
	private ScheduledExecutorService connectionAndReadingPool;
	private List<NmeaListener> nmeaListeners = Collections.synchronizedList(new LinkedList<NmeaListener>()); 
	private LocationManager locationManager;
	private ConnectedGps connectedGps;
	private Notification connectionProblemNotification;
	private Notification serviceStoppedNotification;
	private Context appContext;
	private NotificationManager notificationManager;

	public BlueetoothGpsManager(Service callingService, String deviceAddress) {
		this.gpsDeviceAddress = deviceAddress;
		this.callingService = callingService;
		this.appContext = callingService.getApplicationContext();
		locationManager = (LocationManager)callingService.getSystemService(Context.LOCATION_SERVICE);
		notificationManager = (NotificationManager)callingService.getSystemService(Context.NOTIFICATION_SERVICE);
		parser.setLocationManager(locationManager);	
		
		connectionProblemNotification = new Notification();
		connectionProblemNotification.icon = R.drawable.icon;
		Intent stopIntent = new Intent(BluetoothGpsProviderService.ACTION_STOP_GPS_PROVIDER);
		// PendingIntent stopPendingIntent = PendingIntent.getService(appContext, 0, stopIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		PendingIntent stopPendingIntent = PendingIntent.getService(appContext, 0, stopIntent, 0);
		connectionProblemNotification.contentIntent = stopPendingIntent;

		serviceStoppedNotification = new Notification();
		serviceStoppedNotification.icon=R.drawable.icon;
		Intent restartIntent = new Intent(BluetoothGpsProviderService.ACTION_START_GPS_PROVIDER);
		PendingIntent restartPendingIntent = PendingIntent.getService(appContext, 0, restartIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		serviceStoppedNotification.setLatestEventInfo(appContext, appContext.getString(R.string.service_closed_because_connection_problem_notification_title), appContext.getString(R.string.service_closed_because_connection_problem_notification_title), restartPendingIntent);


	}

	/**
	 * @return true if the bluetooth GPS is enabled
	 */
	public synchronized boolean isEnabled() {
		return enabled;
	}

	public synchronized boolean enable() {
		notificationManager.cancel(R.string.service_closed_because_connection_problem_notification_title);
		if (! enabled){
			final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			if (bluetoothAdapter == null) {
				// Device does not support Bluetooth
				Log.e("BT test", "Device does not support Bluetooth");
			} else if (!bluetoothAdapter.isEnabled()) {
				// Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				//	startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
				Log.e("BT test", "Bluetooth is not enabled");
			} else {
				final BluetoothDevice gpsDevice = bluetoothAdapter.getRemoteDevice(gpsDeviceAddress);
				if (gpsDevice == null){
					Log.e("BT test", "GPS device not found");       	    	
				} else {
					Log.e("BT test", "current device: "+gpsDevice.getName() + " -- " + gpsDevice.getAddress());
					try {
						gpsSocket = gpsDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
					} catch (IOException e) {
						Log.e("BT test", "Error during connection", e);
					}
					if (gpsSocket == null){
						Log.e("BT test", "Error while establishing connection: no socket");
					} else {
						Runnable connectThread = new Runnable() {							
							private int connectionTry=0;
							@Override
							public void run() {
								try {
									connectionTry++;
									Log.e("BT test", "current device: "+gpsDevice.getName() + " -- " + gpsDevice.getAddress());
									try {
										if (gpsSocket != null){
											Log.e("BT test", "trying to close old socket");
											gpsSocket.close();
										}
									} catch (IOException e) {
										Log.e("BT test", "Error during disconnection", e);
									}
									try {
										gpsSocket = gpsDevice.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
									} catch (IOException e) {
										Log.e("BT test", "Error during connection", e);
									}
									if (gpsSocket == null){
										Log.e("BT test", "Error while establishing connection: no socket");
									} else {
										// Cancel discovery because it will slow down the connection
										bluetoothAdapter.cancelDiscovery();
										// we increment the number of connection try
										// Connect the device through the socket. This will block
										// until it succeeds or throws an exception
										gpsSocket.connect();
										// connection obtained so reset the number of connection try
										connectionTry=0;
										notificationManager.cancel(R.string.connection_problem_notification_title);
										connectedGps = new ConnectedGps(gpsSocket);
										connectionAndReadingPool.execute(connectedGps);
									}
								} catch (IOException connectException) {
									// Unable to connect
									Log.e("BT test", "error while connecting to socket", connectException);
								} finally {
									if (connectionTry > 0)
									{
										// Unable to connect
										Log.e("BT test", "Unable to establish connection");
										connectionProblemNotification.when = System.currentTimeMillis();
										String pbMessage = appContext.getResources().getQuantityString(R.plurals.connection_problem_notification, 5-connectionTry, 5-connectionTry);
										connectionProblemNotification.setLatestEventInfo(appContext, 
												appContext.getString(R.string.connection_problem_notification_title), 
												pbMessage, 
												connectionProblemNotification.contentIntent);
										connectionProblemNotification.number = connectionTry;
										notificationManager.notify(R.string.connection_problem_notification_title, connectionProblemNotification);
									}
									// if bluetooth has bean disabled or
									// if two much tries consider that we are enable to connect. So close everything and get out
									if ((!bluetoothAdapter.isEnabled()) || (connectionTry >= 5 )){
										notificationManager.cancel(R.string.connection_problem_notification_title);
										serviceStoppedNotification.when = System.currentTimeMillis();
										notificationManager.notify(R.string.service_closed_because_connection_problem_notification_title, serviceStoppedNotification);
										disable();
									}
								}
							}
						};
						this.enabled = true;
						notificationPool = Executors.newSingleThreadExecutor();
						connectionAndReadingPool = Executors.newSingleThreadScheduledExecutor();
						connectionAndReadingPool.scheduleWithFixedDelay(connectThread, 100, 60000, TimeUnit.MILLISECONDS);
					}
				}
			}
		}
		return this.enabled;
	}

	public synchronized void disable() {
		if (enabled){
			enabled = false;
			if (gpsSocket != null){
				try {
					gpsSocket.close();
				} catch (IOException closeException) {
					Log.e("BT test", "error while closing socket", closeException);
				}
			}
			nmeaListeners.clear();
			disableMockLocationProvider();
			notificationPool.shutdown();
			connectionAndReadingPool.shutdown();
			notificationManager.cancel(R.string.connection_problem_notification_title);
			callingService.stopSelf();
		}
	}

	public void enableMockLocationProvider(String gpsName){
		if (parser != null){
			parser.enableMockLocationProvider(gpsName);
		}
	}

	public void disableMockLocationProvider(){
		if (parser != null){
			parser.disableMockLocationProvider();
		}
	}

	/**
	 * @return the mockGpsEnabled
	 */
	public boolean isMockGpsEnabled() {
		boolean mockGpsEnabled = false;
		if (parser != null){
			mockGpsEnabled = parser.isMockGpsEnabled();
		}
		return mockGpsEnabled;
	}
	/**
	 * @return the mockLocationProvider
	 */
	public String getMockLocationProvider() {
		String  mockLocationProvider = null;
		if (parser != null){
			mockLocationProvider = parser.getMockLocationProvider();
		}
		return mockLocationProvider;
	}

	private void setMockLocationProviderOutOfService(){
		if (parser != null){
			parser.setMockLocationProviderOutOfService();
		}
	}

	public boolean addNmeaListener(NmeaListener listener){
		if (!nmeaListeners.contains(listener)){
			nmeaListeners.add(listener);
		}
		return true;
	}

	public void removeNmeaListener(NmeaListener listener){
		nmeaListeners.remove(listener);
	}

	private void notifyNmeaSentence(final String nmeaSentence){
		if (enabled){
			final String recognizedSentence = parser.parseNmeaSentence(nmeaSentence);
			final long timestamp = System.currentTimeMillis();
			if (recognizedSentence != null){
				Log.e("BT test", "NMEA : "+timestamp+" "+recognizedSentence);
				synchronized(nmeaListeners) {
					for(final NmeaListener listener : nmeaListeners){
						notificationPool.execute(new Runnable(){
							@Override
							public void run() {
								listener.onNmeaReceived(timestamp, recognizedSentence);
							}					 
						});
					}
				}
			}
		}
	}	
}
