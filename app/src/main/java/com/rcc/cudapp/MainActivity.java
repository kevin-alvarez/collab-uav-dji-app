package com.rcc.cudapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.blikoon.qrcodescanner.QrCodeActivity;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.battery.BatteryState;
import dji.common.camera.SystemState;
import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.mission.waypoint.Waypoint;
import dji.common.mission.waypoint.WaypointMission;
import dji.common.mission.waypoint.WaypointMissionDownloadEvent;
import dji.common.mission.waypoint.WaypointMissionExecutionEvent;
import dji.common.mission.waypoint.WaypointMissionFinishedAction;
import dji.common.mission.waypoint.WaypointMissionFlightPathMode;
import dji.common.mission.waypoint.WaypointMissionHeadingMode;
import dji.common.mission.waypoint.WaypointMissionUploadEvent;
import dji.common.util.CommonCallbacks;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.mission.waypoint.WaypointMissionOperator;
import dji.sdk.mission.waypoint.WaypointMissionOperatorListener;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends BaseActivity implements View.OnClickListener, GoogleMap.OnMapClickListener, OnMapReadyCallback {

    private static final String TAG = MainActivity.class.getName();
    public static final String FLAG_CONNECTION_CHANGE = "dji_sdk_connection_change";
    private static final int REQUEST_CODE_QR_SCAN = 101;

    // Connection resources
    private Socket mSocket;
    private String nodeURL;
    private int uavID;
    private Boolean isSocketConnected = false;

    // DJI UAV Components
    private BaseProduct mProduct;
    private Aircraft mAircraft;
    private FlightController mFlightController;
    private Battery mBattery;
    private Camera mCamera;

    // UAV mission variables
    private float mAltitude = 100.0f;
    private float mSpeed = 10.0f;

    private List<Waypoint> waypointList = new ArrayList<>();

    public static WaypointMission.Builder waypointMissionBuilder;
    private WaypointMissionOperator instance;
    private WaypointMissionFinishedAction mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
    private WaypointMissionHeadingMode mHeadingMode = WaypointMissionHeadingMode.AUTO;


    // Map
    private GoogleMap gMap;
    private double droneLocationLat = 181, droneLocationLng = 181;
    private Marker droneMarker = null;

    private boolean isAdd = false;
    private final Map<Integer, Marker> mMarkers = new ConcurrentHashMap<Integer, Marker>();

    // Components (Binarios String -- ✗  ✓)
    private TextView ROSsync, UAVconn, cameraStatus, motorStatus, batteryLevel, missionProgress;
    private Button locate, add, clear, config, upload, start, stop;
    Button syncButton;
    Button sendDataButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // DJI SDK
        this.mProduct = DJISDKManager.getInstance().getProduct();
        IntentFilter filter = new IntentFilter();
        filter.addAction(FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

        // Init Components
        this.ROSsync = findViewById(R.id.ROSsync);
        this.UAVconn = findViewById(R.id.UAVconn);
        this.cameraStatus = findViewById(R.id.cameraStatus);
        this.motorStatus = findViewById(R.id.motorStatus);
        this.batteryLevel = findViewById(R.id.batteryLevel);
        this.missionProgress = findViewById(R.id.missionProgress);
        this.syncButton = findViewById(R.id.syncButton);
        this.sendDataButton = findViewById(R.id.sendDataButton);

        this.locate = findViewById(R.id.locate);
        this.add = findViewById(R.id.add);
        this.clear = findViewById(R.id.clear);
        this.config = findViewById(R.id.config);
        this.upload= findViewById(R.id.upload);
        this.start = findViewById(R.id.start);
        this.stop = findViewById(R.id.stop);

        locate.setOnClickListener(this);
        add.setOnClickListener(this);
        clear.setOnClickListener(this);
        config.setOnClickListener(this);
        upload.setOnClickListener(this);
        start.setOnClickListener(this);
        stop.setOnClickListener(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Add mission listener
        addListener();

        // Init Components Content
        this.ROSsync.setText("✗");
        this.UAVconn.setText("✗");
        this.cameraStatus.setText("-");
        this.motorStatus.setText("-");
        this.batteryLevel.setText("-%");
        this.missionProgress.setText("-%");
        this.syncButton.setOnClickListener(syncEventButton);
        this.sendDataButton.setOnClickListener(sendDataEventButton);
        this.sendDataButton.setVisibility(View.INVISIBLE);
        showToast("Please connect the Aircraft");
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("Closing App")
            .setMessage("Are you sure you want to close the App?")
            .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finishAffinity();
                }
            })
            .setNegativeButton("No", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        // Destroy mission listener
        removeListener();
    }

    // QR
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode != Activity.RESULT_OK) {
            if(data==null)
                return;
            //Getting the passed result
            String result = data.getStringExtra("com.blikoon.qrcodescanner.error_decoding_image");
            if( result!=null) {
                AlertDialog alertDialog = new AlertDialog.Builder(this).create();
                alertDialog.setTitle("Scan Error");
                alertDialog.setMessage("QR could not be scanned");
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
            }
            return;
        }
        if(requestCode == REQUEST_CODE_QR_SCAN) {
            if(data==null)
                return;
            //Getting the passed result
            String result = data.getStringExtra("com.blikoon.qrcodescanner.got_qr_scan_relult");
            try {
                JSONObject QRResult = new JSONObject(result);
                this.nodeURL = QRResult.getString("node_url");
                this.uavID = QRResult.getInt("uav_id");
            }catch(JSONException e){
                Log.e(TAG, "Error parsing QR result: "+e.toString());
            }
            initSocket();
        }
    }

    // Sync Button Events
    private View.OnClickListener syncEventButton = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent i = new Intent(MainActivity.this, QrCodeActivity.class);
            startActivityForResult(i, REQUEST_CODE_QR_SCAN);
        }
    };

    private View.OnClickListener desyncEventButton = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            mSocket.disconnect();
        }
    };

    private void updateSyncButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(isSocketConnected) {
                    syncButton.setText("Desync UAV");
                    syncButton.setOnClickListener(desyncEventButton);
                    ROSsync.setText("✓");
                    showToast("ROS UAV "+uavID+" is connected!");
                    sendDataButton.setVisibility(View.VISIBLE);
                }else {
                    syncButton.setText("Sync UAV");
                    syncButton.setOnClickListener(syncEventButton);
                    ROSsync.setText("✗");
                    showToast("ROS UAV disconnected...");
                    sendDataButton.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    // Socket
    private Emitter.Listener onConnect =  new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if(!isSocketConnected) {
                isSocketConnected = true;
                updateSyncButton();
                Log.i(TAG, "UAV id: "+uavID+" connected");
            }
        }
    };


    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            isSocketConnected = false;
            updateSyncButton();
            Log.i(TAG, "UAV id: "+uavID+" disconnected");
        }
    };

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "Error Connecting");
        }
    };

    private Emitter.Listener onConnectTimeout = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.i(TAG, "Connection timeout");
        }
    };

    private Emitter.Listener newMission = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            // Recibir y actualizar lista de waypoints
            String waypointsData = (String) args[0];
            if (waypointsData.isEmpty()) {
                showToast("No more missions to do, going home");
                if (isSocketConnected) mSocket.emit("missionwaypoints", "");
                mFlightController.startGoHome(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                    }
                });
            } else {
                // Limpiar plan de vuelo
                clearWaypointMission();
                // Crear nuevo plan de vuelo de acuerdo a mission obtenida de ROS
                List<String> waypoints = Arrays.asList(waypointsData.split("\\s*;\\s*"));
                for (String wp : waypoints) {
                    String[] data = wp.split("\\s*,\\s*");
                    double latitude = Double.parseDouble(data[0]);
                    double longitude = Double.parseDouble(data[1]);
                    float altitude = Float.parseFloat(data[2]);
                    Waypoint mWaypoint = new Waypoint(latitude, longitude, altitude);
                    markWaypoint(new LatLng(latitude, longitude), BitmapDescriptorFactory.HUE_RED);
                    if (waypointMissionBuilder != null) {
                        waypointList.add(mWaypoint);
                        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                    }else {
                        waypointMissionBuilder = new WaypointMission.Builder();
                        waypointList.add(mWaypoint);
                        waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
                    }
                }
                DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
                if (error == null) {
                    if (isSocketConnected) mSocket.emit("missionwaypoints", waypointsData);
                    setResultToToast("loadWaypoint succeeded");
                } else {
                    setResultToToast("loadWaypoint failed " + error.getDescription());
                }
                uploadWayPointMission();
                showToast("Ready to start new mission");
            }
        }
    };

    private void initSocket() {
        try {
            this.mSocket = IO.socket(this.nodeURL);
            this.mSocket.on(Socket.EVENT_CONNECT, onConnect);
            this.mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            this.mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            this.mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectTimeout);
            this.mSocket.on("newmission", newMission);
            this.mSocket.connect();
        } catch(URISyntaxException e){
            Log.e(TAG, "Incorrect URL syntax (Scanned from QR code)");
        }
    }

    private View.OnClickListener sendDataEventButton = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            //mSocket.emit("missionprogress", Integer.toString(missionProgressValue));
            //mSocket.emit("camerastatus", Integer.toString(cameraStatusValue));
            //mSocket.emit("motorstatus", Integer.toString(motorStatusValue));
            //mSocket.emit("batterylevel", Integer.toString(batteryLevelValue));
        }
    };

    // DJI Components
    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            onProductConnectionChange();
        }
    };

    private void onProductConnectionChange() {
        updateAircraftObject();
        initFlightController();
        initBattery();
        initCamera();
    }

    private void updateAircraftObject() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && product.isConnected()) {
            if (product instanceof Aircraft) {
                mAircraft = (Aircraft) product;
                updateTextView(UAVconn, "✓");
            }
        } else {
            updateTextView(UAVconn, "✗");
            updateTextView(cameraStatus, "-");
            updateTextView(motorStatus, "-");
            updateTextView(batteryLevel, "-%");
            updateTextView(missionProgress, "-%");
            if (isSocketConnected) {
                updateTextView(ROSsync, "✗");
                mSocket.disconnect(); // Al perder conexion del producto eliminar conexion con ROS
            }
        }
    }

    private void initFlightController() {
        mFlightController = mAircraft.getFlightController();

        if (mFlightController != null) {
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    updateDroneLocation();

                    if (djiFlightControllerCurrentState.isLandingConfirmationNeeded()) {
                        // Podría tener una confirmación del aterrizaje (complicado de hacer con dialogs)
                        mFlightController.confirmLanding(new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                showToast("Aircraft Landing");
                            }
                        });
                    }

                    boolean areMotorsOn = djiFlightControllerCurrentState.areMotorsOn();
                    if (areMotorsOn) {
                        updateTextView(motorStatus, "ON");
                        if (isSocketConnected) mSocket.emit("motorstatus", "0");
                    }else {
                        updateTextView(motorStatus, "OFF");
                        if (isSocketConnected) mSocket.emit("motorstatus", "1");
                    }
                }
            });
        }
    }

    private void initBattery() {
        mBattery = mAircraft.getBattery();

        if (mBattery != null) {
            mBattery.setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    int batteryLevelValue = batteryState.getChargeRemainingInPercent();
                    updateTextView(batteryLevel, String.valueOf(batteryLevelValue)+"%");
                    if (isSocketConnected) mSocket.emit("batterylevel", String.valueOf(batteryLevelValue));
                }
            });
        }
    }

    private void initCamera() {
        mCamera = mAircraft.getCamera();

        if (mCamera != null) {
            mCamera.setSystemStateCallback(new SystemState.Callback() {
                @Override
                public void onUpdate(@NonNull SystemState systemState) {
                    boolean cameraError = systemState.hasError();
                    if(cameraError) {
                       updateTextView(cameraStatus, "ERR");
                        if (isSocketConnected) mSocket.emit("camerastatus", "1");
                    } else {
                       updateTextView(cameraStatus, "OK");
                        if (isSocketConnected) mSocket.emit("camerastatus", "0");
                    }
                }
            });
        }
    }


    public static boolean checkGpsCoordinates(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void updateDroneLocation(){
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        //Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.mipmap.drone_icon_map));

        //debugToast(pos.toString()); // Debug gps position
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordinates(droneLocationLat, droneLocationLng)) {
                    droneMarker = gMap.addMarker(markerOptions);
                }
            }
        });
    }

    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLocationLat, droneLocationLng);
        float zoomlevel = (float) 18.0;
        CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(pos, zoomlevel);
        gMap.moveCamera(cu);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.locate:{
                updateDroneLocation();
                cameraUpdate();
                break;
            }
            case R.id.add:{
                enableDisableAdd();
                break;
            }
            case R.id.clear:{
                clearWaypointMission();
                break;
            }
            case R.id.config:{
                showSettingDialog();
                break;
            }
            case R.id.upload:{
                uploadWayPointMission();
                break;
            }
            case R.id.start:{
                startWaypointMission();
                break;
            }
            case R.id.stop:{
                stopWaypointMission();
                break;
            }
            default:
                break;
        }
    }

    private void enableDisableAdd(){
        if (isAdd == false) {
            isAdd = true;
            add.setText("Exit");
        }else{
            isAdd = false;
            add.setText("Add");
        }
    }

    @Override
    public void onMapClick(LatLng point) {
        if (isAdd == true){
            markWaypoint(point, BitmapDescriptorFactory.HUE_BLUE);
            Waypoint mWaypoint = new Waypoint(point.latitude, point.longitude, mAltitude);
            //Add Waypoints to Waypoint arraylist;
            if (waypointMissionBuilder != null) {
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }else
            {
                waypointMissionBuilder = new WaypointMission.Builder();
                waypointList.add(mWaypoint);
                waypointMissionBuilder.waypointList(waypointList).waypointCount(waypointList.size());
            }
        }else{
            setResultToToast("Cannot add waypoint");
        }
    }

    private void markWaypoint(LatLng point, float pointDescriptor){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(pointDescriptor));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Marker marker = gMap.addMarker(markerOptions);
                mMarkers.put(mMarkers.size(), marker);
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }
        LatLng santiago = new LatLng(-33.447487, -70.673676);
        //gMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(santiago));

    }

    private void setUpMap() {
        gMap.setOnMapClickListener(this);// add the listener for click for a map object
    }

    private void showSettingDialog(){
        LinearLayout wayPointSettings = (LinearLayout)getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

        final TextView wpAltitude_TV = wayPointSettings.findViewById(R.id.altitude);
        RadioGroup speed_RG = wayPointSettings.findViewById(R.id.speed);
        RadioGroup actionAfterFinished_RG = wayPointSettings.findViewById(R.id.actionAfterFinished);
        RadioGroup heading_RG = wayPointSettings.findViewById(R.id.heading);

        speed_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.lowSpeed){
                    mSpeed = 3.0f;
                } else if (checkedId == R.id.MidSpeed){
                    mSpeed = 5.0f;
                } else if (checkedId == R.id.HighSpeed){
                    mSpeed = 10.0f;
                }
            }
        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.finishNone){
                    mFinishedAction = WaypointMissionFinishedAction.NO_ACTION;
                } else if (checkedId == R.id.finishGoHome){
                    mFinishedAction = WaypointMissionFinishedAction.GO_HOME;
                } else if (checkedId == R.id.finishAutoLanding){
                    mFinishedAction = WaypointMissionFinishedAction.AUTO_LAND;
                } else if (checkedId == R.id.finishToFirst){
                    mFinishedAction = WaypointMissionFinishedAction.GO_FIRST_WAYPOINT;
                }
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.headingNext) {
                    mHeadingMode = WaypointMissionHeadingMode.AUTO;
                } else if (checkedId == R.id.headingInitDirec) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_INITIAL_DIRECTION;
                } else if (checkedId == R.id.headingRC) {
                    mHeadingMode = WaypointMissionHeadingMode.CONTROL_BY_REMOTE_CONTROLLER;
                } else if (checkedId == R.id.headingWP) {
                    mHeadingMode = WaypointMissionHeadingMode.USING_WAYPOINT_HEADING;
                }
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
                        String altitudeString = wpAltitude_TV.getText().toString();
                        mAltitude = Integer.parseInt(nulltoIntegerDefault(altitudeString));
                        Log.e(TAG,"altitude "+mAltitude);
                        Log.e(TAG,"speed "+mSpeed);
                        Log.e(TAG, "mFinishedAction "+mFinishedAction);
                        Log.e(TAG, "mHeadingMode "+mHeadingMode);
                        configWayPointMission();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }

    String nulltoIntegerDefault(String value){
        if(!isIntValue(value)) value="0";
        return value;
    }

    boolean isIntValue(String val)
    {
        try {
            val=val.replace(" ","");
            Integer.parseInt(val);
        } catch (Exception e) {return false;}
        return true;
    }

    // Mission methods
    public WaypointMissionOperator getWaypointMissionOperator() {
        if (instance == null) {
            if (DJISDKManager.getInstance().getMissionControl() != null){
                instance = DJISDKManager.getInstance().getMissionControl().getWaypointMissionOperator();
            }
        }
        return instance;
    }

    private void configWayPointMission(){
        ArrayList<String> waypoints = new ArrayList<>();
        mFinishedAction = WaypointMissionFinishedAction.NO_ACTION; // Siempre sin accion al terminar

        if (waypointMissionBuilder == null){

            waypointMissionBuilder = new WaypointMission.Builder().finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }else {
            waypointMissionBuilder.finishedAction(mFinishedAction)
                    .headingMode(mHeadingMode)
                    .autoFlightSpeed(mSpeed)
                    .maxFlightSpeed(mSpeed)
                    .flightPathMode(WaypointMissionFlightPathMode.NORMAL);

        }

        if (waypointMissionBuilder.getWaypointList().size() > 0){

            for (int i=0;i < waypointMissionBuilder.getWaypointList().size();i++){
                waypointMissionBuilder.getWaypointList().get(i).altitude = mAltitude;
                waypoints.add(String.format("%.8f,%.8f,%.2f", waypointMissionBuilder.getWaypointList().get(i).coordinate.getLatitude(), waypointMissionBuilder.getWaypointList().get(i).coordinate.getLongitude(), mAltitude));
            }
        }

        DJIError error = getWaypointMissionOperator().loadMission(waypointMissionBuilder.build());
        if (error == null) {
            String waypointsData = TextUtils.join(";", waypoints);
            if (isSocketConnected) mSocket.emit("missionwaypoints", waypointsData);
            setResultToToast("loadWaypoint succeeded");
        } else {
            setResultToToast("loadWaypoint failed " + error.getDescription());
        }

    }

    private void clearWaypointMission() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                gMap.clear();
            }
        });
        waypointList.clear();
        waypointMissionBuilder.waypointList(waypointList);
        updateDroneLocation();
    }

    //Add Listener for WaypointMissionOperator
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
            float progress = ((float) (executionEvent.getProgress().targetWaypointIndex) / executionEvent.getProgress().totalWaypointCount) * 100;
            updateTextView(missionProgress, String.format("%.1f", progress)+"%");
            if (isSocketConnected) {
                mSocket.emit("missionprogress", String.format("%.1f", progress));
                mSocket.emit("actualwaypoint", String.format("%d", executionEvent.getProgress().targetWaypointIndex));
            }


            Waypoint wp = waypointList.get(executionEvent.getProgress().targetWaypointIndex);
            double lat = wp.coordinate.getLatitude();
            double lon = wp.coordinate.getLongitude();
            float alt = wp.altitude;
            debugToast(String.format("%.2f, %.2f, %.2f", lat, lon, alt));
        }

        @Override
        public void onExecutionStart() {

        }

        @Override
        public void onExecutionFinish(@Nullable final DJIError error) {
            setResultToToast("Execution finished: " + (error == null ? "Success!" : error.getDescription()));
            if (error == null) {
                updateTextView(missionProgress, "100%");
                if (isSocketConnected) mSocket.emit("missionprogress", "100");
                if (isSocketConnected) mSocket.emit("requestnewmission");
            }
        }
    };

    private void uploadWayPointMission(){
        getWaypointMissionOperator().uploadMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                if (error == null) {
                    setResultToToast("Mission upload successfully!");
                } else {
                    setResultToToast("Mission upload failed, error: " + error.getDescription() + " retrying...");
                    getWaypointMissionOperator().retryUploadMission(null);
                }
            }
        });
    }

    private void startWaypointMission(){
        getWaypointMissionOperator().startMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Start: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    private void stopWaypointMission(){
        getWaypointMissionOperator().stopMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError error) {
                setResultToToast("Mission Stop: " + (error == null ? "Successfully" : error.getDescription()));
            }
        });

    }

    // UI Help methods
    private void updateTextView(TextView v, String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                v.setText(text);
            }
        });
    }

    private void setResultToToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void debugToast(String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "Debug: "+toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showToast(final String toastMsg) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
            }
        });
    }
}
