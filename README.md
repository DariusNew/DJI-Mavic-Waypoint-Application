# DJI-Mavic-Waypoint-Application

DJI App to run a waypoint application with Android Phone using a TCP connection to the console and protobuf as the communcation protocol. 

Troubleshooting Information: 

If the app does not run, check gradle settings and check if there are any android dependancies that you need to download for android        studios. 

Application will crash on the first startup after initial installation to allow for User to give permission for the application to use certain phone functions. Opening it a second time should allow the application to work normally.

If application continues to crash, check that the Async Task for the TCP connection is not attempting to send and listen for a message at the same time. This will cause multiple functions trying to run on the same thread. Instead, you can add an extra thread to allow the application to both send and listen for messages.

To add in a different protobuf file, add in the protobuf file to app/main/src/proto. Then rebuild the gradle and check generated proto. If there are still errors, check out https://proandroiddev.com/how-to-setup-your-android-app-to-use-protobuf-96132340de5c and follow the steps available there.

In the mainactivity class, the TCP client has two main overriden functions: DoInBackground and OnProgressUpdate. Do in background runs as a background thread and on progress update helps to push anything from the background thread up to the main UI thread. Edit this to change the waypoints being set by the protobuf information recieved from the computer. 

Last but not least, line 818 in MainActivity should be edited depending on the total size of the information being sent by the new protobuf file. 
