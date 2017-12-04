

# UsbGps4Droid - A USB GPS provider for Android 

<img align="right" alt="App icon" src="app-icon.png" height="300px">

UsbGps4Droid is a USB GPS provider application for the Android operating system,
providing GPS support for devices back to android 3.1


[Download latest release](../../releases)



## About
The application provides location updates to Android which allows devices without 
an internal GPS to still use applications which require GPS (Google Maps navigation).

I use this USB Gps with my Android Tablet Headunit, because the internal GPS does 
not work reliably. I have a [main controller](https://github.com/freshollie/AndroidHeadunitController/) 
which automatically starts this application's background service when my car starts

The application is designed to work with any SiRF USB GPS device, however it has been 
tested as working with [GlobalSat BU-353-S4](http://usglobalsat.com/p-688-bu-353-s4.aspx) and
[a device based on the UBLOX LEA-M8P chip](../../issues/7).

## Features
- Android 6.0+ Permission handling
- Ability to pick which USB device is the GPS
- Automatic start on reboot
- User interface with readings from the USB GPS and a log showing NMEA data coming from the GPS
- Abilty to sync android device time with GPS time (Requires root)
- Support for any SiRF USB GPS device

## Usage

### USB Permissions Popup
**Unless your ROM is modified then Android will ask permission to use your USB GPS device 
everytime you reconnect it**

If your device is rooted, then you can follow [this tutorial](https://stackoverflow.com/a/30563253/1741602) 
in order to surpress the USB permission popup system wide and grant permission everytime automatically.

If your device is not rooted, then there is nothing I can do. This is unfortunately a limitation of 
the android operating system. If this is for an embedded system I highly recommend rooting the device
and following the tutoial.

### Connecting a USB GPS to your device
- A USB GPS device can connect to an Android device's charging port, with a USB OTG adapter, if your device supports 
[USB OTG](https://en.wikipedia.org/wiki/USB_On-The-Go)

- If your device has normal USB ports (Raspberry PI, computer...) then an OTG adapter is not required.

### Disclaimer
This app has only been tested on 2 of my devices, running Android 5.1 and 6.0. Any issues with 
this app on your device, please create a  [github issue](../../issues) and I will resolve the 
problem as soon as possible.

### Service behaviour 
The application works with any SiRF USB GPS receiver. The application's background service can 
be set to start when the device turns on. Currently the application does not automatically start
when the GPS device is plugged into the Android device, but this might be fixed in the future.

(Intents to start the background service using an intent will be open soon)

For now the background service can be manually started in the background via shell as root. 
(Which can be done through tasker)

```bash
am startservice -a org.broeuschmeul.android.gps.usb.provider.driver.usbgpsproviderservice.intent.action.START_GPS_PROVIDER
```

The background service will automatically close itself when the USB device is disconnected for too long.

## Change log
The current updates include:

### 2.1.3
- Fixed endpoint check algorithm to work for devices with multiple interfaces or only 1 endpoint
- Updated NMEA parser to work with all types of NMEA

### 2.1.2
- Changed transfer request timeout
- Added GPS time to info activity

### 2.1.1
- Fixed bug which would hang the service if the device was not responding the transfer requests

### 2.1
- Added option to start service on bootup
- Added option to set the system clock to the gps clock (10 seconds after connecting the clock will be set)
- Other code fixes

### 2.0
- Added information interface which shows the log of information being received from the GPS device.
- Updated device selection settings
- Fix to algorithm to pick the correct device from the USB device list
- Fix to connection baud rate settings
- Updated NMEA recording to be enabled to start when service starts.
- Fixed auto-reconnecting
- Dialog popups in app for problems
- Compatibility down to android 3.1

## Contributing

Any contributions welcome. Please fork this repository and create a pull request notifying your changes and why.

## Screenshots

### Landscape tablet
<p align="center">
    <img src="screenshots/main_screen_tablet_api_23.png" align="center" alt="Main interface marshmallow" width="800"/>
</p>

#### Android 3.1 interface
<p align="center">
    <img src="screenshots/main_screen_tablet_api16.png" align="center" alt="Main interface honeycomb" width="800"/>
</p>

### Portrait
<p align="center">
    <img src="screenshots/main_screen_tablet_portrait_api_23.png" align="center" alt="Main interface portait" width="400"/>
</p>

### Device selection
<p align="center">
    <img src="screenshots/options_device_choice.png" align="center" alt="Device choice settings" width="800"/>
</p>

## Credits
Originally written by Herbert von Broeuschmeul, and I have taken over maintaining this 
Project (Which was originally written in 2011). You can find his orginal project at 
the top of this fork.

The Herbert's unmaintained version does not work on the newer android operating systems, 
and has several bugs with the main usb algorithm.

## License

`GPL v3`