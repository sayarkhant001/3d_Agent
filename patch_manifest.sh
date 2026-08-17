#!/bin/bash
sed -i '/<uses-permission android:name="android.permission.INTERNET" \/>/a \
    <uses-permission android:name="android.permission.BLUETOOTH" />\
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />\
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />\
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />\
' app/src/main/AndroidManifest.xml
