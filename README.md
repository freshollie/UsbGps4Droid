# UsbGps4Droid

Builds
------
Download the latest APKs from [here](/app/build/outputs/apk/)

About
-----
Usb GPS for Android, this is my own implimentation of USB GPS, updated to work properly on android 6.0.1.

I have patched this app to work mainly for implementation in an in car android tablet.

My implementation doesn't require selecting the USB device, it will select the device (https://www.amazon.co.uk/GlobalSat-BU-353-S4-USB-Receiver-Black/dp/B008200LHW) and automatically connect.

Current Development
-------------------

This current build will work on devices up to android marshmallow.

The user is required to set the app is a mock location provider in the android developer settings.

I also renamed several classes in this build and created


Usage
-----

The best implementation is to start the service in the background.

```bash
am startservice -a org.broeuschmeul.android.gps.usb.provider.nmea.intent.action.START_GPS_PROVIDER
```

Be sure to call this only once the GPS device is connected as the app will not wait for it to be connected.

The service will automatically close itself when the USB device is disconnected, IE the car has turned off.


Contributing
-----------

To help with development simply clone the repository, create your branch and then import the project into android studio (File -> New -> Import Project)

It should build perfectly, providing you have the correct SDKs (Currently API 23 required)
