package com.example.ottylab.bitzenyminer;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.github.anastr.speedviewlib.TubeSpeedometer;
import com.github.anastr.speedviewlib.components.Section;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BitZenyMiner";
    private static final int LOG_LINES = 1000;
    private Button settingsBtn;
    private TextView textViewLog, tvHashrate, accuTemp;
    private EditText userAddress;
    private float hashrateMax = 1;
    private int batteryTemp;
    Button btnScanBarcode;

    @Override
    public void onResume() {
        super.onResume();
        // This registers messageReceiver to receive messages.
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiverHashrate, new IntentFilter("my-message"));
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiverLogs, new IntentFilter("my-log"));
    }

    // Handling the received Intents for the "hashrateConfirmed" event
    private BroadcastReceiver messageReceiverHashrate = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Extract data included in the Intent
            double hashRateReceived = intent.getDoubleExtra("hashrateConfirmed", 0);

            TubeSpeedometer meterHashrate = findViewById(R.id.meter_hashrate);
            meterHashrate.makeSections(1, getResources().getColor(R.color.c_blue), Section.Style.SQUARE);

            // will set the highest value as maximum hashrate
            if (hashrateMax < ((float) hashRateReceived)) {
                hashrateMax = ((float) hashRateReceived);
                meterHashrate.setMaxSpeed(hashrateMax);
            }

            meterHashrate.speedTo((float) hashRateReceived);

            // set hashrate to string
            tvHashrate.setText(String.valueOf(Math.round(hashRateReceived)));

            // show accu temp
            accuTemp.setText(String.valueOf(batteryTemp));
        }
    };

    // Handling the received Intents for the "hashrateConfirmed" event
    private BroadcastReceiver messageReceiverLogs = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Extract data included in the Intent
            String receivedMessage = intent.getStringExtra("logmessage");
            textViewLog.setText(receivedMessage);
        }
    };

    @Override
    protected void onPause() {
        // Unregister since the activity is not visible
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiverHashrate);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiverLogs);
        super.onPause();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            // TODO Extract the data returned from the child Activity.
            String returnValue = data.getStringExtra("some_key");
            userAddress.setText(returnValue);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // provide barcode scanner
        btnScanBarcode = findViewById(R.id.btnScanBarcode);
        btnScanBarcode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ScannedBarcodeActivity.class));
            }
        });


        // provide text edit for mining address
        userAddress = (EditText) findViewById(R.id.editTextUser);
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref",MODE_PRIVATE);
        String tdcAddress = sharedPreferences.getString("user", "");
        userAddress.setText(tdcAddress);

        userAddress.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                sharedPreferences.edit().putString("user", s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {

            }
        });

        // provide textViewLog
        textViewLog = (TextView) findViewById(R.id.textViewLog);
        textViewLog.setMovementMethod(new ScrollingMovementMethod());

        tvHashrate = findViewById(R.id.hashrate);

        accuTemp = findViewById(R.id.accuTemp);

        // enable speed o meter for cores
        TubeSpeedometer meterCores = findViewById(R.id.meter_cores);
        meterCores.makeSections(1, getResources().getColor(R.color.c_yellow), Section.Style.SQUARE);
        meterCores.speedTo(0, 1);

        // Hashrate
        TubeSpeedometer meterHashrate = findViewById(R.id.meter_hashrate);
        meterHashrate.makeSections(1, getResources().getColor(R.color.c_blue), Section.Style.SQUARE);
        meterCores.setMaxSpeed(1);
        meterHashrate.speedTo(0);

        // default hashrate to string
        TextView tvHashrate = findViewById(R.id.hashrate);
        tvHashrate.setText("-");

        // Foreground Service
        if(!foregroundServiceRunning()) {
            Intent serviceIntentForeground = new Intent(this, MiningForeGroundService.class);
            startForegroundService(serviceIntentForeground);
            Toast.makeText(getApplicationContext(), "Miner was started as foreground service", Toast.LENGTH_SHORT).show();
        }

        // activate bazzery temp check
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        MainActivity.this.registerReceiver(broadcastreceiver,intentfilter);

        // activate settings button
        // initializing our button.
        settingsBtn = findViewById(R.id.idBtnSettings);

        // adding on click listener for our button.
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // opening a new intent to open settings activity.
                Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(i);
            }
        });
    }

    public boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for(ActivityManager.RunningServiceInfo service: activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if(MiningForeGroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }

        Toast.makeText(getApplicationContext(), "Miner is running as foreground Service", Toast.LENGTH_SHORT).show();

        return false;
    }

    private BroadcastReceiver broadcastreceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            batteryTemp = (int)(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0))/10;
        }
    };

}