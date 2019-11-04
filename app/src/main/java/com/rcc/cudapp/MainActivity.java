package com.rcc.cudapp;

import android.content.DialogInterface;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import dji.sdk.sdkmanager.DJISDKManager;


public class MainActivity extends BaseActivity {

    private static final String TAG = MainActivity.class.getName();
    private Handler mHandler;

    // Components
    TextView mVersionText;
    Button mFlightPlanButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        // Init Components
        this.mFlightPlanButton = findViewById(R.id.toFlightPlanButton);
        this.mVersionText = findViewById(R.id.versionText);

        // DJI SDK
        mHandler = new Handler(Looper.getMainLooper());

        // Init Components Content
        this.mFlightPlanButton.setText("Flight Plan");

        this.mFlightPlanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mVersionText.setText(DJISDKManager.getInstance().getSDKVersion());
                //Intent intent = new Intent(MainActivity.this, FlightPlanActivity.class);
                //startActivity(intent);
            }
        });
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
}