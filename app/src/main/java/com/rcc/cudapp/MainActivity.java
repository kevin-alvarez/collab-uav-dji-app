package com.rcc.cudapp;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.blikoon.qrcodescanner.QrCodeActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;

import dji.sdk.sdkmanager.DJISDKManager;
import dji.thirdparty.afinal.core.AsyncTask;
import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;



public class MainActivity extends BaseActivity {

    private static final String TAG = MainActivity.class.getName();
    private Handler mHandler;
    private Socket mSocket;
    private String nodeURL;
    private int uavID;
    private Boolean isConnected = false;

    private static final int REQUEST_CODE_QR_SCAN = 101;

    // Components
    TextView mVersionText;
    TextView mSyncStatus;
    Button mFlightPlanButton;
    Button mSyncButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        // Init Components
        this.mFlightPlanButton = findViewById(R.id.toFlightPlanButton);
        this.mSyncButton = findViewById(R.id.syncButton);
        this.mVersionText = findViewById(R.id.versionText);
        this.mSyncStatus = findViewById(R.id.syncStatusText);

        // DJI SDK
        mHandler = new Handler(Looper.getMainLooper());

        // Init Components Content
        this.mSyncStatus.setText("No UAV connected...");
        this.mFlightPlanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVersionText.setText(DJISDKManager.getInstance().getSDKVersion());
                Intent intent = new Intent(MainActivity.this, FlightPlanActivity.class);
                startActivity(intent);
            }
        });
        this.mSyncButton.setOnClickListener(syncEventButton);
        //sendRequest();
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
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if(isConnected) {
                    mSyncButton.setText("Desync UAV");
                    mSyncButton.setOnClickListener(desyncEventButton);
                    mSyncStatus.setText("UAV "+uavID+" is connected!");
                }else {
                    mSyncButton.setText("Sync UAV");
                    mSyncButton.setOnClickListener(syncEventButton);
                    mSyncStatus.setText("No UAV connected...");
                }
            }
        });
    }

    // Socket
    private Emitter.Listener onConnect =  new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            if(!isConnected) {
                //mSocket.emit("join");
                isConnected = true;
                updateSyncButton();
                Log.i(TAG, "UAV id: "+uavID+" connected");
            }
        }
    };


    private Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            //mSocket.emit("left", uavID);
            isConnected = false;
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
        }catch(URISyntaxException e){
            Log.e(TAG, "Incorrect URL syntax (Scanned from QR code)");
        }
    }

    private void sendRequest() {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                OkHttpClient cliente = new OkHttpClient();
                HttpUrl urlBuilder = HttpUrl.parse(nodeURL);

                Request req = new Request.Builder().url(urlBuilder).build();
                try (Response response = cliente.newCall(req).execute()){
                    if(!response.isSuccessful()) throw new IOException("Failed: "+response);
                    Log.i(TAG, response.body().string());
                }catch(IOException e){
                    e.printStackTrace();
                }
            }
        });
    }

}