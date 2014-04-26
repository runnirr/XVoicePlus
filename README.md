XVoicePlus
==========
# Installation
* Install Google Voice from the play store if you don't have it already
* Install the XVoice+ apk from the Xposed Repo (http://repo.xposed.info/module/com.runnirr.xvoiceplus)
* Enable XVoice+ in Xposed
* Run the XVoice+ app and select your Google Voice account
* Disable "Text Notifications" in the Google Voice app settings to avoid double notifications
* Reboot your phone to enable the Xposed module
Note: It my take a few minutes after rebooting before your messages start showing in your sms app of choice

# How it works
## Sending
The VoicePlusService runs on startup and listens for a NEW_OUTGOING_SMS so that it knows when a message is being sent. Cyanogenmod made this a system-wide intent, but it is not available on all other roms. Instead, I made my own intent.
I hook all outgoing sms messages through the SmsManager and stops them from being sent. Instead I send my custom NEW_OUTGING_SMS intent that is picked up by the VoicePlusService. Then the handling of sending the message via Google Voice is the same logic used in the Cyanogenmod app.
## Receiving
Receiving message is done by hooking into the Google Voice app's PushNotificationReceiver. This means that we know of the message as soon as the Google Voice app does. Conveniently, this notification happens even when notifications in Google Voice are turned off, so you don't need to briefly see the Google Voice notification, have it disappear, and then see the sms notification.
Once we see we have an incoming Google Voice message, we broadcast another custom intent that the VoicePlusService is also listening for.The service then handles the message and broadcasts a system wide RECEIVED_SMS intent that is used by all sms applications.

# Side Effects
Because of the way some system apps are being overwrote, I expect that some things will break.
* There will be no way to send sms via your carrier any app that uses SmsManager to send texts will be sent via Google Voice.
* The app grants itself permission for BROADCAST_SMS which does not appear in the list of permission since it is a system level permission.
* On 4.4+ the app grants itself access to write to the SMS history

# Support
http://forum.xda-developers.com/showthread.php?t=2598889

# Source
https://github.com/runnirr/XVoicePlus/
