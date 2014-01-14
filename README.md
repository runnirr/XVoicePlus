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

# My Testing 
I only have a Verizon Galaxy Nexus to test this on. Luckily this can run 4.0.4 through 4.4.x. I'll update this as I get a chance to try new aosp versions.
## 4.0.4 
Using Google Hangouts -

Receiving messages works.

Sending messages does not work - message is never sent

## 4.1.1 
Using Google Hangouts -

Receiving messages works.

Sending messages does not work - message is never sent


## 4.2.2 
Using Google Hangouts -

Receiving messages works.

Sending messages works.

## 4.4.2 
Using Google Hangouts -

Receiving messages works.

Sending messages works.

**Note: In order for messages sent on other devices to show up, you must give "write sms" permissions through AppOps to XVoice+**

# Side Effects
Because of the way some system apps are being overwrote, I expect that some things will break.
* There will be no way to send sms via your carrier any app that uses SmsManager to send texts will be sent via Google Voice.
* All sms are assumed to be in GSM format. A pdu is needed to broadcast that we received an sms. This means that any incoming sms that is in CDMA format will likely cause things to crash. The fake messages still work on a CDMA phone (or atleast on my Galaxy Nexus they do)
* The app grants itself permission for BROADCAST_SMS which does not appear in the list of permission since it is a system level permission.

# Support
http://forum.xda-developers.com/showthread.php?t=2598889

# Source
https://github.com/runnirr/XVoicePlus/
