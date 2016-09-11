# UsbGps4Droid
Usb GPS for Android, this is my own implimentation of USB GPS, updated to work properly on android 5.0.

I have patched this app to work mainly for implementation in an in car android tablet.

As I do not fully understand the old source code, I have had to brute force remove stuff such as debug lines which could not be disabled.

My implementation doesn't require selecting the USB device, it will select the device (https://www.amazon.co.uk/GlobalSat-BU-353-S4-USB-Receiver-Black/dp/B008200LHW) and automatically connect.

I fixed the issue with lollipop changing the way mocklocation worked, and a few other errors which were causing the device to crash while in use.

Usage
-----

The best implementation is to start the service in the background.

```bash
am startservice -a org.broeuschmeul.android.gps.usb.provider.nmea.intent.action.START_GPS_PROVIDER
```

Be sure to call this only once the GPS device is connected as the app will not wait for it to be connected.

The service will automatically close itself when the USB device is disconnected, IE the car has turned off.
