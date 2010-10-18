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
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.broeuschmeul.android.gps.nmea.util.NmeaParser;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Criteria;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.GpsStatus.NmeaListener;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.PrintStreamPrinter;

public class BlueetoothGpsManager {

	private class ConnectedThread extends Thread {
		    private final InputStream in;
		    private final OutputStream out;
		    private final PrintStream out2;
	
		    public ConnectedThread(BluetoothSocket socket) {
		        InputStream tmpIn = null;
		        OutputStream tmpOut = null;
		        PrintStream tmpOut2 = null;
		        try {
		        	tmpIn = socket.getInputStream();
		        	tmpOut = socket.getOutputStream();
		        	if (tmpOut != null){
		        		tmpOut2 = new PrintStream(tmpOut, false, "US-ASCII");
		        	}
		        } catch (IOException e) {
		        	Log.e("BT test", "error while getting socket streams", e);
		        }	
		        in = tmpIn;
		        out = tmpOut;
		        out2 = tmpOut2;
		    }
	
		    public void run() {
		        try {
		        	BufferedReader reader = new BufferedReader(new InputStreamReader(in,"US-ASCII"));
		        	String s;
					while((enabled && (s = reader.readLine()) != null)){
						Log.e("BT test", "data: "+System.currentTimeMillis()+" "+s + "xxx");
						notifyNmeaSentence(s+"\r\n");
//						parser.parseNmeaSentence(s);
//	//					writer.println(s);
//						addNMEAString(s);
//						nmeaSentenceHandler.ob
					}
				} catch (IOException e) {
		        	Log.e("BT test", "error while getting data", e);
				} finally {
					disable();
				}
		    }

	        /**
	         * Write to the connected OutStream.
	         * @param buffer  The bytes to write
	         */
	        public void write(byte[] buffer) {
	            try {
	                out.write(buffer);
	            } catch (IOException e) {
//	                Log.e(TAG, "Exception during write", e);
	            }
	        }
	        /**
	         * Write to the connected OutStream.
	         * @param buffer  The data to write
	         */
	        public void write(String buffer) {
	            try {
	                out2.print(buffer);
	                out.flush();
	            } catch (IOException e) {
//	                Log.e(TAG, "Exception during write", e);
	            }
	        }
		}

	private Service callingService;
	private BluetoothDevice gpsDevice;
	private BluetoothSocket gpsSocket;
	private String gpsDeviceAddress;
	private NmeaParser parser = new NmeaParser(10f);
	private boolean enabled = false;
	private ExecutorService notificationPool;
	private List<NmeaListener> nmeaListeners = Collections.synchronizedList(new LinkedList<NmeaListener>()); 
	private LocationManager locationManager;
	private SharedPreferences sharedPreferences;
//	private boolean mockGpsEnabled = true;
//	private String mockLocationProvider = LocationManager.GPS_PROVIDER;
	private ConnectedThread connectedThread;

//	private Handler nmeaSentenceHandler = new Handler();

	public BlueetoothGpsManager(Service callingService, String deviceAddress) {
		this.gpsDeviceAddress = deviceAddress;
		this.callingService = callingService;
		locationManager = (LocationManager)callingService.getSystemService(Context.LOCATION_SERVICE);
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(callingService);
		parser.setLocationManager(locationManager);	
	}
	
	/**
	 * @return true if the bluetooth GPS is enabled
	 */
	public synchronized boolean isEnabled() {
		return enabled;
	}

	public synchronized void enable() {
		if (! enabled){
			final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	        if (bluetoothAdapter == null) {
	            // Device does not support Bluetooth
	        	Log.e("BT test", "Device does not support Bluetooth");
	        } else if (!bluetoothAdapter.isEnabled()) {
//	        	    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//	        	    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
	        	Log.e("BT test", "Bluetooth is not enabled");
	        } else if (Settings.Secure.getInt(callingService.getContentResolver(),Settings.Secure.ALLOW_MOCK_LOCATION, 0)==0){
	        	Log.e("BT test", "Mock location provider OFF");
	        } else if ( (! locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER))
	        		// && (sharedPreferences.getBoolean(BluetoothGpsProviderService.PREF_REPLACE_STD_GPS, true))
	        			) {
	        	Log.e("BT test", "GPS location provider OFF");
	        } else {
	    		BluetoothDevice gpsDevice = bluetoothAdapter.getRemoteDevice(gpsDeviceAddress);
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
////	    				mockGpsEnabled = locationManager.isProviderEnabled(mockLocationProvider);
//	    				LocationProvider prov = locationManager.getProvider(mockLocationProvider);
//	    				Log.e("BT test", "Mock power: "+prov.getPowerRequirement()+" "+prov.getAccuracy()+" "+locationManager.isProviderEnabled(mockLocationProvider));
//	    				locationManager.addTestProvider(mockLocationProvider, false, true,false, false, 
//	    						true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
////	    				locationManager.setTestProviderEnabled(mockLocationProvider, true);
//	    				prov = locationManager.getProvider(mockLocationProvider);
//	    				Log.e("BT test", "Mock power: "+prov.getPowerRequirement()+" "+prov.getAccuracy()+" "+locationManager.isProviderEnabled(mockLocationProvider));

	    				Runnable connectThread = new Runnable() {							
							@Override
							public void run() {
								// Cancel discovery because it will slow down the connection
								bluetoothAdapter.cancelDiscovery();
						        try {
						            // Connect the device through the socket. This will block
						            // until it succeeds or throws an exception
						        	gpsSocket.connect();
				    				connectedThread = new ConnectedThread(gpsSocket);
				    				connectedThread.start();
//				    				String command = callingService.getString(R.string.sirf_gll_on);
//				    				String sentence = String.format((Locale)null,"$%s*%X\r\n", command, parser.computeChecksum(command)); 
//				    				String command1 = callingService.getString(R.string.sirf_gll_off);
//				    				String sentence1 = String.format((Locale)null,"$%s*%X\r\n", command1, parser.computeChecksum(command1)); 
//				    				String command2 = callingService.getString(R.string.sirf_vtg_off);
//				    				String sentence2 = String.format((Locale)null,"$%s*%X\r\n", command2, parser.computeChecksum(command2)); 
//				    				try {
//										Thread.sleep(5000);
//									} catch (InterruptedException e) {
//										// TODO Auto-generated catch block
//										e.printStackTrace();
//									}
//				    				Log.e("BT test", "sending NMEA sentence: "+"$PSRF105,1*3E\r\n");
//				    				connectedThread.write("$PSRF105,1*3E\r\n");	    								    				
//				    				Log.e("BT test", "sending NMEA sentence: "+sentence1);
//				    				connectedThread.write(sentence1);	    								    				
//				    				Log.e("BT test", "sending NMEA sentence: "+sentence2);
//				    				connectedThread.write(sentence2);	    								    				
						        } catch (IOException connectException) {
						            // Unable to connect; close everything and get out
						        	Log.e("BT test", "error while connecting to socket", connectException);
									disable();
//						        	callingService.stopSelf();
						        }
							}
						};
						this.enabled = true;
						notificationPool = Executors.newSingleThreadExecutor();
						notificationPool.execute(connectThread);
//						enableMockLocationProvider(LocationManager.GPS_PROVIDER);
	    			}
	    		}
	        }
		}
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
////			locationManager.setTestProviderEnabled(mockLocationProvider, mockGpsEnabled);
//			LocationProvider prov = locationManager.getProvider(mockLocationProvider);
//			Log.e("BT test", "Mock power: "+prov.getPowerRequirement()+" "+prov.getAccuracy()+" "+locationManager.isProviderEnabled(mockLocationProvider));
//			locationManager.clearTestProviderEnabled(mockLocationProvider);
//			prov = locationManager.getProvider(mockLocationProvider);
//			Log.e("BT test", "Mock power: "+prov.getPowerRequirement()+" "+prov.getAccuracy()+" "+locationManager.isProviderEnabled(mockLocationProvider));
//			locationManager.clearTestProviderStatus(mockLocationProvider);
//			locationManager.removeTestProvider(mockLocationProvider);
//			prov = locationManager.getProvider(mockLocationProvider);
//			Log.e("BT test", "Mock power: "+prov.getPowerRequirement()+" "+prov.getAccuracy()+" "+locationManager.isProviderEnabled(mockLocationProvider));
//			Log.e("BT test", "removed mock GPS");
			
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
			parser.parseNmeaSentence(nmeaSentence);
			final long timestamp = System.currentTimeMillis();
			synchronized(nmeaListeners) {
				for(final NmeaListener listener : nmeaListeners){
					notificationPool.execute(new Runnable(){
						@Override
						public void run() {
							listener.onNmeaReceived(timestamp, nmeaSentence);
						}					 
					});
				}
			}
		}
	}		
}
