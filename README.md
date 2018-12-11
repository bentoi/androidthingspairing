# Pairing application for Android Things

This application allows pairing with another device on Android Things devices.

## Building

Run `gradlew build` to build the application. Android tools must be in your
PATH.

## Running

Install and run the application as follow:

```
adb -d install -t -r build/outputs/apk/debug/androidthingspairing-debug.apk
adb -d shell am start com.zeroc.androidthingspairing/.MainActivity
```

After launching your Android Things device should be discoverable over
Bluetooth. The PIN code to pair with it is `0000`.

To pair with a host running Linux with BlueZ 5.0, you can run the following on 
the Linux side:
```
$ bluetoothctl
[bluetooth] agent on
[bluetooth] default-agent
[bluetooth] scan on
<wait for the device to be discovered>
[bluetooth] pair <Bluetooth Address of discovered Android Things device>
Request PIN code
Enter PIN code: 0000
```

