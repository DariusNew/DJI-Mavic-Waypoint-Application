package com.dji.djiapp;

import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import mcs.WaypointOuterClass;

public class TcpClient {

    private static final String SERVER_IP = "192.168.1.31";
    //private static final String SERVER_IP = "172.29.131.159";
    public static final int SERVER_PORT = 443;
    public static final String TAG = "TcpClient";
    private String incomingMessage, command;
    private OnMessageReceived listener = null;
    // while this is true, the server will continue running
    private boolean mRun = false;

    Socket socket;

    BufferedReader mBufferIn;
    PrintWriter mBufferOut;

    /**
     *  constructor of class. onMessageReceived listens for the messages received from server
     */
    public TcpClient(OnMessageReceived listener){
        this.listener = listener;
    }
    /**
     * Sends the message entered by client to the server
     *
     * @param message text entered by client
     */
    public void sendMessage(String message) {

        if (mBufferOut != null && !mBufferOut.checkError()) {
            mBufferOut.println(message);
            mBufferOut.flush(); //write to a data stream
            Log.d(TAG,message + " sent");
        }
    }

    /**
     * The run() function starts a thread. TCP connection is not allowed to be in the same thread as the UI(causes app crash)
     */
    public void run() {

        mRun = true;

            try {
                InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
                //create a socket to make connection to server
                socket = new Socket(serverAddr, SERVER_PORT);
                Log.d(TAG, "Socket created");
                while(mRun) {
                    try {
                        mBufferOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        //mBufferIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        this.sendMessage("Waiting for Waypoint");
                        try {
                            InputStream is = socket.getInputStream();
                            byte[] buffer = new byte[100];
                            int read = is.read(buffer);
                            if (read != -1) {
                                listener.messageReceived(buffer);
                                String msg = new String(buffer);
                                Log.d(TAG, msg);
                                this.sendMessage("Waiting for Waypoint");
                            }

                        } catch (Exception e) {
                            Log.d(TAG, "Input Stream Error");
                        }

                    } catch (Exception e) {
                        Log.d(TAG, "ERROR");
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "S: Error", e);
                stopClient();
            }

    }

    public void stopClient() {
        Log.d(TAG, "stopClient");

        mRun = false;

        if (mBufferOut != null) {
            mBufferOut.flush();
            mBufferOut.close();
        }

        mBufferOut = null;
    }
    /**Declare the interface. The method messageReceived(String message) will must be implemented in the MainActivity
     *class at on asynckTask doInBackground
     */
    public interface OnMessageReceived {
        void messageReceived(byte[] b);
    }

}
