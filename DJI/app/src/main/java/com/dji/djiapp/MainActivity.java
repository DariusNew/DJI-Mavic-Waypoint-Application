package com.dji.djiapp;

import dji.common.product.Model;
import dji.sdk.camera.VideoFeeder;
import mcs.WaypointOuterClass;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.support.v4.app.FragmentActivity;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointAction;
import dji.common.mission.waypoint.WaypointActionType;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.model.LocationCoordinate2D;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.common.util.CommonCallbacks;


public class MainActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapClickListener, CompoundButton.OnCheckedChangeListener, View.OnClickListener, GoogleMap.OnMyLocationButtonClickListener {

    private static final String TAG = MainActivity.class.getName();

    private ArrayList<LatLng> dronePos = new ArrayList<>();
    Polyline line;
    private GoogleMap gMap;

    private double droneLocationLat, droneLocationLng;
    private float droneHeading;
    private Marker droneMarker = null;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();
    private boolean isStart = false;
    private boolean isHome = false;

    public static WaypointMission.Builder waypointMissionBuilder;
    private FlightController mFlightController;
    private WaypointMissionOperator instance;
    private Marker homeMarker = null;
    private LatLng homePos = null;

    private List<Waypoint> waypointList = new ArrayList<>();
    private int missionIndex = 0;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;
    private float mSpeed = 10.0f;

    private ToggleButton missionSwitch;
    private Button clear;
    private Button ready;
    private Button mUpdate;
    private Button recieve;

    Handler mHandler;
    HandlerThread mHandlerThread;
    private boolean isConnected = false;

    BufferedReader in;
    private TcpClient mTcpClient;

    protected VideoFeeder.VideoDataCallback mReceivedVideoDataCallBack = null;

    private static final String[] REQUIRED_PERMISSION_LIST = new String[]{
            Manifest.permission.VIBRATE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
    };
    private List<String> missingPermission = new ArrayList<>();
    private AtomicBoolean isRegistrationInProgress = new AtomicBoolean(false);
    private static final int REQUEST_PERMISSION_CODE = 12345;

//*/////////////////////
//* WHEN THE APP IS FIRST OPENED
//*/////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkAndRequestPermissions();

//*/////////////////////
//* USER INTERFACE SETUP
//*/////////////////////

        setContentView(R.layout.activity_main);
        missionSwitch = (ToggleButton) findViewById(R.id.missionSwitch);
        missionSwitch.setOnCheckedChangeListener(this);
        clear = (Button) findViewById(R.id.clear);
        clear.setOnClickListener(this);
        ready = (Button) findViewById(R.id.ready);
        ready.setOnClickListener(this);
        mUpdate = (Button) findViewById(R.id.update);
        mUpdate.setOnClickListener(this);
        recieve = (Button) findViewById(R.id.recieve);
        recieve.setOnClickListener(this);

//*/////////////////////
//* GOOGLE MAP SETUP
//*/////////////////////

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

//*/////////////////////
//* LISTENER FOR THE WAYPOINT APPLICATION
//*/////////////////////

        IntentFilter filter = new IntentFilter();
        filter.addAction(DJIDemoApplication.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReciever, filter);
        addListener();
    }

//*/////////////////////
//* REQUEST FOR PHONE PERMISSIONS
//*/////////////////////

    private void checkAndRequestPermissions() {
        // Check for permissions
        for (String eachPermission : REQUIRED_PERMISSION_LIST) {
            if (ContextCompat.checkSelfPermission(this, eachPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermission.add(eachPermission);
            }
        }
        // Request for missing permissions
        if (!missingPermission.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    missingPermission.toArray(new String[missingPermission.size()]),
                    REQUEST_PERMISSION_CODE);
        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Check for granted permission and remove from missing list
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (int i = grantResults.length - 1; i >= 0; i--) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    missingPermission.remove(permissions[i]);
                }
            }
        }
        // If there is enough permission, we will start the registration
        if (missingPermission.isEmpty()) {
            startSDKRegistration();
        } else {
            showToast("Missing permissions!!!");
        }
    }

//*/////////////////////
//* DJI SDK REGISTRATION
//*/////////////////////

    private void startSDKRegistration() {
        if (isRegistrationInProgress.compareAndSet(false, true)) {
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    showToast("registering, pls wait...");
                    DJISDKManager.getInstance().registerApp(getApplicationContext(), new DJISDKManager.SDKManagerCallback() {
                        @Override
                        public void onRegister(DJIError djiError) {
                            if (djiError == DJISDKError.REGISTRATION_SUCCESS) {
                                DJILog.e("App registration", DJISDKError.REGISTRATION_SUCCESS.getDescription());
                                DJISDKManager.getInstance().startConnectionToProduct();
                                showToast("Register Success");
                            } else {
                                showToast("Register sdk fails, check network is available");
                            }
                            Log.v(TAG, djiError.getDescription());
                        }

                        @Override
                        public void onProductChange(BaseProduct oldProduct, BaseProduct newProduct) {
                            Log.d(TAG, String.format("onProductChanged oldProduct:%s, newProduct:%s", oldProduct, newProduct));
                        }
                    });
                }
            });
        }
    }

//*/////////////////////
//* DJI WAYPOINT MANAGER SETUP
//*/////////////////////

    protected BroadcastReceiver mReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange() {
        refreshSDK();
        initFlightController();
        updateCamera(new LatLng(droneLocationLat, droneLocationLng));
    }

    private void refreshSDK() {
        BaseProduct mProduct = DJIDemoApplication.getProductInstance();
        if (null != mProduct && mProduct.isConnected()) showToast("Connected");
    }

    private void initFlightController() {
        BaseProduct product = DJIDemoApplication.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mFlightController = ((Aircraft) product).getFlightController();
            }
        }
        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    droneLocationLat = flightControllerState.getAircraftLocation().getLatitude();
                    droneLocationLng = flightControllerState.getAircraftLocation().getLongitude();
                    droneHeading = mFlightController.getCompass().getHeading();
                }
            });
        }
    }

    private void addListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().addListener(eventNotificationListener);
        }
    }

    private void removeListener() {
        if (getWaypointMissionOperator() != null) {
            getWaypointMissionOperator().removeListener(eventNotificationListener);
        }
    }

    private WaypointMissionOperatorListener eventNotificationListener = new WaypointMissionOperatorListener() {
        @Override
        public void onDownloadUpdate(WaypointMissionDownloadEvent downloadEvent) {

        }

        @Override
        public void onUploadUpdate(WaypointMissionUploadEvent uploadEvent) {

        }

        @Override
        public void onExecutionUpdate(WaypointMissionExecutionEvent executionEvent) {
            if (executionEvent != null) {
                int waypointIndex = executionEvent.getProgress().targetWaypointIndex;
                missionIndex = waypointIndex;
            }
        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            showToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
        }
    };

    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null) {
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

//*/////////////////////
//* DJI WAYPOINT MANAGER FUNCTIONS
//*/////////////////////

    private void configWaypoint(LatLng point, float speed, float altitude, int loiter) {
        Log.d(TAG, Double.toString(point.latitude) + Double.toString(point.longitude));
        Waypoint newWaypoint = new Waypoint(point.latitude, point.longitude, altitude);
        newWaypoint.speed = speed;
        newWaypoint.altitude = altitude;
        WaypointAction action = new WaypointAction(WaypointActionType.STAY, loiter);
        newWaypoint.addAction(action);
        waypointList.add(newWaypoint);
        waypointMissionBuilder = new WaypointMission.Builder();
        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());

        waypointMissionBuilder.finishedAction(mFinishedAction)
                .headingMode(mHeadingMode)
                .autoFlightSpeed(mSpeed)
                .maxFlightSpeed(15f)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        if (waypointMissionBuilder.getWaypointList().size() > 1) {
            DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        }
    }

    private void uploadMission() {
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) showToast("Mission Ready");
                else showToast("Error: " + djiError.getDescription());
            }
        });
    }

    private void setHome() {
        if (mFlightController != null) {
            mFlightController.setHomeLocationUsingAircraftCurrentLocation(new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError != null) showToast("Error: " + djiError.getDescription());
                }
            });
        }
    }

    private void startMission() {
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                showToast("Mission Start: " + (djiError == null ? "Successful" : djiError.getDescription()));
            }
        });
        isStart = true;
    }

    private void stopMission() {
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                showToast("Mission Stop: " + (djiError == null ? "Successful" : djiError.getDescription()));
            }
        });
        isStart = false;
    }

    private void updateMission() {
        for (int i = missionIndex - 1; i >= 0; i--) {
            waypointList.remove(i);
            waypointMissionBuilder = new WaypointMission.Builder();
            waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());

            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(15f)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

            if (waypointMissionBuilder.getWaypointList().size() > 1) {
                DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
                if (error == null) showToast("Mission updated");
                else showToast("Mission failed to update " + error.getDescription());
            }
            Log.d(TAG, "Index Iteration: " + Integer.toString(i));
        }
    }

//*/////////////////////
//* SETTING UP THE GOOGLE MAPS
//*/////////////////////

    @SuppressLint("MissingPermission")
    private void setUpMap() {
        gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        gMap.setOnMapClickListener(this);
        gMap.getUiSettings().setMapToolbarEnabled(false);
        gMap.setMyLocationEnabled(true);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }
    }

//*/////////////////////
//* GOOGLE MAPS FUNCTIONS TO UPDATE DRONE LOCATION
//*/////////////////////

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void updateDroneLocation(double lat, double lng) {
        if (checkGpsCoordination(lat, lng)) {
            LatLng pos = new LatLng(lat, lng);
            dronePos.add(pos);
            droneHeading += 180f;

            //Create MarkerOptions object
            final MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(pos);
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            markerOptions.alpha(0.7f);
            markerOptions.flat(true);
            markerOptions.anchor(0.5f, 0.5f);
            markerOptions.rotation(droneHeading);

            if (droneMarker != null) droneMarker.remove();
            droneMarker = gMap.addMarker(markerOptions);
            drawLine();
        }
    }

    private void drawLine() {
        PolylineOptions lineOptions = new PolylineOptions().width(5.0f).color(Color.WHITE).geodesic(true);
        for (int i = 0; i < dronePos.size(); i++) {
            LatLng point = dronePos.get(i);
            lineOptions.add(point);
        }
        if (line != null) line.remove();
        line = gMap.addPolyline(lineOptions);
    }

    private void updateCamera(LatLng pos) {
        if (pos != null) gMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15.0f));
    }

    private void markWaypoint(LatLng point) {
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        markerOptions.alpha(0.7f);
        int index = waypointList.size();
        markerOptions.title(Integer.toString(index));
        Marker marker = gMap.addMarker(markerOptions);
        marker.showInfoWindow();
        mMarkers.put(mMarkers.size(), marker);
        Log.d(TAG, "Waypoint Marked");
    }

    private void markHome() {
        mFlightController.getHomeLocation(new CommonCallbacks.CompletionCallbackWith<LocationCoordinate2D>() {
            @Override
            public void onSuccess(LocationCoordinate2D locationCoordinate2D) {
                showToast("Success");
                homePos = new LatLng(locationCoordinate2D.getLatitude(), locationCoordinate2D.getLongitude());
            }

            @Override
            public void onFailure(DJIError djiError) {
                if (djiError != null) showToast("Error: " + djiError.getDescription());
            }
        });
        if (homePos != null) {
            MarkerOptions markerOptions = new MarkerOptions();
            markerOptions.position(homePos);
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
            markerOptions.alpha(0.7f);

            if (homeMarker != null) homeMarker.remove();
            homeMarker = gMap.addMarker(markerOptions);
            isHome = true;
        }
    }

    @Override
    public void onMapClick(LatLng point) {
        configWaypoint(point, mSpeed, 20.0f, 1000);
        markWaypoint(point);
    }

    @Override
    public boolean onMyLocationButtonClick() {
        return false;
    }

//*/////////////////////
//* USER INTERFACE BUTTONS AND SWITCHES
//*/////////////////////

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.clear: {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }
                });
                break;
            }
            case R.id.ready: {
//                setHome();
//                uploadMission();
                showToast(Double.toString(droneLocationLat) + " " + Double.toString(droneLocationLng));
                break;
            }
            case R.id.update: {
//                updateMission();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }
                });
                waypointList.clear();
                waypointMissionBuilder.waypointList(waypointList);
                dronePos.clear();
                updateDroneLocation(droneLocationLat, droneLocationLng);
                mMarkers.clear();
                missionIndex = 0;
                break;
            }
            case R.id.recieve: {
                new DroneAsyncTask().execute();
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) startMission();
        else if (isChecked == false) stopMission();
    }

//*/////////////////////
//* LAT LONG TO UTM CONVERSION
//*/////////////////////

//    private double checkDistance(LatLng pt1, LatLng pt2){
//        double x = (pt2.longitude - pt1.longitude)*Math.cos((pt1.latitude+pt2.latitude)/2);
//        double y = pt2.latitude - pt1.latitude;
//        return 6371e3*Math.sqrt(x*x + y*y);
//    }

//    private char getLetter (LatLng point){
//        char letter;
//        double lat = point.latitude;
//        if (lat<-72)
//            letter='C';
//        else if (lat<-64)
//            letter='D';
//        else if (lat<-56)
//            letter='E';
//        else if (lat<-48)
//            letter='F';
//        else if (lat<-40)
//            letter='G';
//        else if (lat<-32)
//            letter='H';
//        else if (lat<-24)
//            letter='J';
//        else if (lat<-16)
//            letter='K';
//        else if (lat<-8)
//            letter='L';
//        else if (lat<0)
//            letter='M';
//        else if (lat<8)
//            letter='N';
//        else if (lat<16)
//            letter='P';
//        else if (lat<24)
//            letter='Q';
//        else if (lat<32)
//            letter='R';
//        else if (lat<40)
//            letter='S';
//        else if (lat<48)
//            letter='T';
//        else if (lat<56)
//            letter='U';
//        else if (lat<64)
//            letter='V';
//        else if (lat<72)
//            letter='W';
//        else
//            letter='X';
//        return letter;
//    }
//
//    private int getZone (LatLng point){
//        int zone;
//        zone = (int) Math.floor(point.longitude/6 + 31);
//        return zone;
//    }
//
//    private double getEasting (LatLng point){
//        double Easting;
//        double Lat = point.latitude;
//        double Lon = point.longitude;
//        int Zone = getZone(point);
//        Easting=0.5*Math.log((1+Math.cos(Lat*Math.PI/180)*Math.sin(Lon*Math.PI/180-(6*Zone-183)*Math.PI/180))/(1-Math.cos(Lat*Math.PI/180)*Math.sin(Lon*Math.PI/180-(6*Zone-183)*Math.PI/180)))*0.9996*6399593.62/Math.pow((1+Math.pow(0.0820944379, 2)*Math.pow(Math.cos(Lat*Math.PI/180), 2)), 0.5)*(1+ Math.pow(0.0820944379,2)/2*Math.pow((0.5*Math.log((1+Math.cos(Lat*Math.PI/180)*Math.sin(Lon*Math.PI/180-(6*Zone-183)*Math.PI/180))/(1-Math.cos(Lat*Math.PI/180)*Math.sin(Lon*Math.PI/180-(6*Zone-183)*Math.PI/180)))),2)*Math.pow(Math.cos(Lat*Math.PI/180),2)/3)+500000;
//        Easting=Math.round(Easting*100)*0.01;
//        return Easting;
//    }
//
//    private double getNorthing (LatLng point){
//        double Northing;
//        double Lat = point.latitude;
//        double Lon = point.longitude;
//        int Zone = getZone(point);
//        char Letter = getLetter(point);
//        Northing = (Math.atan(Math.tan(Lat*Math.PI/180)/Math.cos((Lon*Math.PI/180-(6*Zone -183)*Math.PI/180)))-Lat*Math.PI/180)*0.9996*6399593.625/Math.sqrt(1+0.006739496742*Math.pow(Math.cos(Lat*Math.PI/180),2))*(1+0.006739496742/2*Math.pow(0.5*Math.log((1+Math.cos(Lat*Math.PI/180)*Math.sin((Lon*Math.PI/180-(6*Zone -183)*Math.PI/180)))/(1-Math.cos(Lat*Math.PI/180)*Math.sin((Lon*Math.PI/180-(6*Zone -183)*Math.PI/180)))),2)*Math.pow(Math.cos(Lat*Math.PI/180),2))+0.9996*6399593.625*(Lat*Math.PI/180-0.005054622556*(Lat*Math.PI/180+Math.sin(2*Lat*Math.PI/180)/2)+4.258201531e-05*(3*(Lat*Math.PI/180+Math.sin(2*Lat*Math.PI/180)/2)+Math.sin(2*Lat*Math.PI/180)*Math.pow(Math.cos(Lat*Math.PI/180),2))/4-1.674057895e-07*(5*(3*(Lat*Math.PI/180+Math.sin(2*Lat*Math.PI/180)/2)+Math.sin(2*Lat*Math.PI/180)*Math.pow(Math.cos(Lat*Math.PI/180),2))/4+Math.sin(2*Lat*Math.PI/180)*Math.pow(Math.cos(Lat*Math.PI/180),2)*Math.pow(Math.cos(Lat*Math.PI/180),2))/3);
//        if (Letter<'M') Northing = Northing + 10000000;
//        Northing=Math.round(Northing*100)*0.01;
//        return Northing;
//    }

//*/////////////////////
//* UTM TO LAT LONG CONVERSION
//*/////////////////////

//    private LatLng getLatLng(String UTM){
//        String[] parts = UTM.split(" ");
//        int Zone = Integer.parseInt(parts[0]);
//        char Letter = parts[1].toUpperCase(Locale.ENGLISH).charAt(0);
//        double Easting = Double.parseDouble(parts[2]);
//        double Northing = Double.parseDouble(parts[3]);
//        double Hem;
//        if (Letter>'M')
//            Hem='N';
//        else
//            Hem='S';
//        double north;
//        if (Hem == 'S')
//            north = Northing - 10000000;
//        else
//            north = Northing;
//        double latitude = (north/6366197.724/0.9996+(1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)-0.006739496742*Math.sin(north/6366197.724/0.9996)*Math.cos(north/6366197.724/0.9996)*(Math.atan(Math.cos(Math.atan(( Math.exp((Easting - 500000) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting - 500000) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3))-Math.exp(-(Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*( 1 -  0.006739496742*Math.pow((Easting - 500000) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3)))/2/Math.cos((north-0.9996*6399593.625*(north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996 )/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996)))*Math.tan((north-0.9996*6399593.625*(north/6366197.724/0.9996 - 0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996 )*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996))-north/6366197.724/0.9996)*3/2)*(Math.atan(Math.cos(Math.atan((Math.exp((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3))-Math.exp(-(Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3)))/2/Math.cos((north-0.9996*6399593.625*(north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996)))*Math.tan((north-0.9996*6399593.625*(north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3))/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996))-north/6366197.724/0.9996))*180/Math.PI;
//        latitude=Math.round(latitude*10000000);
//        latitude=latitude/10000000;
//        double longitude = Math.atan((Math.exp((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3))-Math.exp(-(Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2)/3)))/2/Math.cos((north-0.9996*6399593.625*( north/6366197.724/0.9996-0.006739496742*3/4*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.pow(0.006739496742*3/4,2)*5/3*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2* north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4-Math.pow(0.006739496742*3/4,3)*35/27*(5*(3*(north/6366197.724/0.9996+Math.sin(2*north/6366197.724/0.9996)/2)+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/4+Math.sin(2*north/6366197.724/0.9996)*Math.pow(Math.cos(north/6366197.724/0.9996),2)*Math.pow(Math.cos(north/6366197.724/0.9996),2))/3)) / (0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2))))*(1-0.006739496742*Math.pow((Easting-500000)/(0.9996*6399593.625/Math.sqrt((1+0.006739496742*Math.pow(Math.cos(north/6366197.724/0.9996),2)))),2)/2*Math.pow(Math.cos(north/6366197.724/0.9996),2))+north/6366197.724/0.9996))*180/Math.PI+Zone*6-183;
//        longitude=Math.round(longitude*10000000);
//        longitude=longitude/10000000;
//        LatLng point = new LatLng (latitude, longitude);
//        return point;
//    }

//*/////////////////////
//* NORMAL APPLICATION FUNCTIONS
//*/////////////////////

    @Override
    protected void onResume() {
        Log.e(TAG, "On Resume");
        super.onResume();
        initFlightController();
        //initVideo();
        handler.postDelayed(update, 1000);
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "On Pause");
        handler.removeCallbacks(update);
//        if (mTcpClient != null) new DisconnectTask().execute();
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "On Stop");
        super.onStop();
    }

    public void onReturn(View view) {
        Log.e(TAG, "On Return");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "On Destroy");
        unregisterReceiver(mReciever);
        removeListener();
        super.onDestroy();
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

//*/////////////////////
//* HANDLER THREAD TO UPDATE DRONE LOCATION AT 1 HZ
//*/////////////////////

    Runnable update = new Runnable() {
        @Override
        public void run() {
            updateDroneLocation(droneLocationLat, droneLocationLng);
            if (!isHome && isStart) markHome();
            handler.postDelayed(update, 1000);
            new UpdateAsyncTask().execute();
        }
    };

    Handler handler = new Handler();

    public class UpdateAsyncTask extends AsyncTask<byte[], String, Void> {
        private static final String TAG = "UpdateAsyncTask";

        @Override
        protected Void doInBackground (byte[]... params) {

            String droneLat = Double.toString(droneLocationLat);
            String droneLng = Double.toString(droneLocationLng);
            String droneHead = Float.toString(droneHeading);

            if(isConnected) {
                mTcpClient.sendMessage(droneLat + "," + droneLng + "," + droneHead);
                Log.d(TAG, droneLat + "," + droneLng + "," + droneHead);
            }

            return null;
        }
    }

//*/////////////////////
//* ASYNC TASK TO RUN TCP CLIENT IN THE BACKGROUND THREAD
//*/////////////////////

    public class DroneAsyncTask extends AsyncTask<String, String, TcpClient> {
        private static final String TAG = "DroneAsyncTask";

        @Override
        protected TcpClient doInBackground(String... params) {

            try {
                mTcpClient = new TcpClient(new TcpClient.OnMessageReceived() {
                    @Override
                    public void messageReceived(byte[] b) {
                        Log.d(TAG, "Waypoint received");
                        byte[] buffer = new byte [40];
                        for (int i = 0; i < buffer.length; i++){
                            buffer[i] = b[i];
                        }
                        isConnected = true;

                        try{
                            WaypointOuterClass.Waypoint waypoint = WaypointOuterClass.Waypoint.parseFrom(buffer);
                            float speed = (float) waypoint.getSpeed();
                            if (speed >= 15.0) speed = 15f;
                            double longtitude = (double) waypoint.getLongtitude();
                            double latitude = (double) waypoint.getLatitude();
                            float altitude = (float) waypoint.getAltitude();
                            if (altitude >= 100) altitude = 100f;
                            int loiter = (int) waypoint.getLoiter();
                            String msg = "Mark " + Double.toString(latitude) + " " + Double.toString(longtitude) + " " + Float.toString(altitude) + " " + Float.toString(speed) + " " + Integer.toString(loiter);
                            publishProgress(msg);
                            Log.d(TAG, "Waypoint added");
                            mTcpClient.sendMessage("Waiting for Waypoint");
                        } catch (InvalidProtocolBufferException e){
                            e.printStackTrace();
                            String msg = new String(b);
                            publishProgress(msg);
                        }
                    }
                });
            } catch (NullPointerException e) {
                Log.d(TAG, "Caught nullpoint exception");
                e.printStackTrace();
            }
            mTcpClient.run();
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            String msg = values[0];
            String[] words = msg.split("\\s");
            Log.d(TAG, words[0]);
            if (words[0].equals("WP")){
                Log.d(TAG, "Start config wp");
                double latitude = Double.parseDouble(words[1]);
                double longtitude = Double.parseDouble(words[2]);
                float altitude = Float.parseFloat(words[3]);
                float speed = Float.parseFloat(words[4]);
                int loiter = Integer.parseInt(words[5]);
                if (loiter == 1000) loiter = 0;
                if (speed >= 15.0) speed = 15f;
                if (altitude >= 100) altitude = 100f;
                Log.d(TAG, words[1] + " " + words[2] + " " + words[3] + " " + words[4] + " " + words[5]);
                if (checkGpsCoordination(latitude, longtitude)) {
                    LatLng pos = new LatLng(latitude, longtitude);
                    markWaypoint(pos);
                    configWaypoint(pos, speed, altitude, loiter);
                }
            } else if (words[0].equals("Ready")){
                setHome();
                uploadMission();
            } else if (words[0].equals("Stop")){
                stopMission();
            } else if (words[0].equals("Start")){
                startMission();
            } else if (words[0].equals("Update")){
                updateMission();
            } else if (words[0].equals("Mark")){
                double latitude = Double.parseDouble(words[1]);
                double longtitude = Double.parseDouble(words[2]);
                float altitude = Float.parseFloat(words[3]);
                float speed = Float.parseFloat(words[4]);
                int loiter = Integer.parseInt(words[5]);
                if (checkGpsCoordination(latitude, longtitude)) {
                    LatLng pos = new LatLng(latitude, longtitude);
                    markWaypoint(pos);
                    configWaypoint(pos, speed, altitude, loiter);
                }
            }
        }
    }
}
