package org.broeuschmeul.android.gps.usb.provider.util;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by Freshollie on 14/12/2016.
 */

public class SuperuserManager {
    private Boolean permission = false;

    public static int MAX_THREADS = 10;

    public static String TAG = SuperuserManager.class.getSimpleName();

    private int numThreads = 0;

    private static SuperuserManager INSTANCE = new SuperuserManager();

    public interface permissionListener {
        void onGranted();
        void onDenied();
    }

    private SuperuserManager() {
    }

    public static SuperuserManager getInstance() {
        return INSTANCE;
    }

    private boolean execute(final String command) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su -c " + command);
            int result;

            try {
                result = process.waitFor();

                if(result != 0){ //error executing command
                    Log.d(TAG, "result code : " + result);
                    String line;
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                    try {
                        while ((line = bufferedReader.readLine()) != null){
                            Log.d(TAG, "Error: " + line);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    process.destroy();
                    return true;
                }
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        if (process != null) {
            process.destroy();
        }

        return false;
    }

    /**
     * Attempts to execute the command in a thread. If there is a thread available.
     * Returns false if no threads available
     * @param command
     * @return
     */
    public boolean asyncExecute(final String command) {
        if (numThreads < MAX_THREADS) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    execute(command);
                    numThreads--;
                }
            }).start();

            numThreads++;
            return true;
        } else {
            return false;
        }
    }

    public void request(final permissionListener permissionListener) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Requesting SU permission");
                if (execute("ls")) {
                    permission = true;
                    permissionListener.onGranted();
                } else {
                    permission = false;
                    permissionListener.onDenied();
                }
            }
        }).start();
    }

    public boolean hasPermission() {
        return permission;
    }

}