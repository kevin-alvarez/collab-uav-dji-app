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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.blikoon.qrcodescanner.QrCodeActivity;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dji.common.battery.BatteryState;
import dji.common.camera.SystemState;
import dji.common.flightcontroller.FlightControllerState;
import dji.sdk.base.BaseProduct;
import dji.sdk.battery.Battery;
import dji.sdk.camera.Camera;
import dji.sdk.flightcontroller.FlightController;
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



        // Init Components Content
        //updateComponentsStatus();
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

    private void initSocket() {
        try {
            this.mSocket = IO.socket(this.nodeURL);
            this.mSocket.on(Socket.EVENT_CONNECT, onConnect);
            this.mSocket.on(Socket.EVENT_DISCONNECT, onDisconnect);
            this.mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
            this.mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectTimeout);
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

    private void setStatusCallbacks() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && product.isConnected()) {
            product.getCamera().setSystemStateCallback(new SystemState.Callback() {
                @Override
                public void onUpdate(@NonNull SystemState systemState) {
                    showToast("Update Camera");
                    if (systemState.hasError()) {
                        cameraStatus.setText("ERROR");

                    } else {
                        cameraStatus.setText("OK");
                    }
                }
            });
            product.getBattery().setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    showToast("Update Battery");
                    batteryLevel.setText(batteryState.getChargeRemainingInPercent() + "%");
                }
            });
        }
    }

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
            mAircraft = null;
            updateTextView(UAVconn, "✗");
            mSocket.disconnect(); // Al perder coneccion del producto eliminar conexion con ROS
        }
    }

    private void initFlightController() {
        mFlightController = mAircraft.getFlightController();

        if (mFlightController != null) {
            debugToast("FlightController is NOT null");
            mFlightController.setStateCallback(new FlightControllerState.Callback() {

                @Override
                public void onUpdate(FlightControllerState djiFlightControllerCurrentState) {
                    droneLocationLat = djiFlightControllerCurrentState.getAircraftLocation().getLatitude();
                    droneLocationLng = djiFlightControllerCurrentState.getAircraftLocation().getLongitude();
                    updateDroneLocation();

                    boolean isMotorsOn = djiFlightControllerCurrentState.areMotorsOn();
                    if (isMotorsOn) {
                        updateTextView(motorStatus, "ON");
                    }else {
                        updateTextView(motorStatus, "OFF");
                    }
                }
            });
        }
    }

    private void initBattery() {
        mBattery = mAircraft.getBattery();

        if (mBattery != null) {
            debugToast("Battery NOT null");
            mBattery.setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    int batteryLevelValue = batteryState.getChargeRemainingInPercent();
                    updateTextView(batteryLevel, String.valueOf(batteryLevelValue)+"%");
                    // Es posible hacer la llamada al servidor de ROS desde acá.
                }
            });
        }
    }

    private void initCamera() {
        mCamera = mAircraft.getCamera();

        if (mCamera != null) {
            debugToast("Camera NOT null");
            mCamera.setSystemStateCallback(new SystemState.Callback() {
                @Override
                public void onUpdate(@NonNull SystemState systemState) {
                    boolean cameraError = systemState.hasError();
                    if(cameraError) {
                       updateTextView(cameraStatus, "ERR");
                    } else {
                       updateTextView(cameraStatus, "OK");
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

    private void cameraUpdate(){
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        gMap.clear();
                    }
                });
                break;
            }
            case R.id.config:{
                showSettingDialog();
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
            markWaypoint(point);
        }else{
            setResultToToast("Cannot add waypoint");
        }
    }

    private void markWaypoint(LatLng point){
        //Create MarkerOptions object
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(point);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
        Marker marker = gMap.addMarker(markerOptions);
        mMarkers.put(mMarkers.size(), marker);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (gMap == null) {
            gMap = googleMap;
            setUpMap();
        }

        LatLng shenzhen = new LatLng(22.5362, 113.9454);
        gMap.addMarker(new MarkerOptions().position(shenzhen).title("Marker in Shenzhen"));
        gMap.moveCamera(CameraUpdateFactory.newLatLng(shenzhen));
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
                // TODO Auto-generated method stub
                Log.d(TAG, "Select Speed finish");
            }
        });

        actionAfterFinished_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                Log.d(TAG, "Select action action");
            }
        });

        heading_RG.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // TODO Auto-generated method stub
                Log.d(TAG, "Select heading finish");
            }
        });

        new AlertDialog.Builder(this)
                .setTitle("")
                .setView(wayPointSettings)
                .setPositiveButton("Finish",new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface dialog, int id) {
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