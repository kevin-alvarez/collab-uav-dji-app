package com.rcc.cudapp;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class FlightPlanActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flight_plan);
        overridePendingTransition(R.anim.enter_slide_in, R.anim.enter_slide_out);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.exit_slide_in, R.anim.exit_slide_out);
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
