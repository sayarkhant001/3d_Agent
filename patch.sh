#!/bin/bash
sed -i 's/FirebaseMessaging.getInstance().subscribeToTopic("3d_alerts")/try { FirebaseMessaging.getInstance().subscribeToTopic("3d_alerts") } catch (e: Exception) { e.printStackTrace() }/g' app/src/main/java/com/example/ui/NotificationPermissionHandler.kt
