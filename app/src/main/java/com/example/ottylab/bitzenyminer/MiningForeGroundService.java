package com.example.ottylab.bitzenyminer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.WorkSource;
import android.preference.PreferenceManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.ottylab.bitzenymininglibrary.BitZenyMiningLibrary;

import java.lang.ref.WeakReference;

class miningHidden extends AppCompatActivity {
    public BitZenyMiningLibrary miner;
    private static JNICallbackHandler sHandler;
    public String logMessage = "Not mining";
    public double hashrateConfirmed = 0;

    private class JNICallbackHandler extends Handler {
        private final WeakReference<miningHidden> activity;

        public JNICallbackHandler(miningHidden activity) {
            this.activity = new WeakReference<miningHidden>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            logMessage = msg.getData().getString("log");

            // get accepted hashes with last accepted share
            if (msg.getData().getString("log").contains("yay!!!")) {
                String[] subStrings = msg.getData().getString("log").split(",");
                if (subStrings.length == 2) {
                    // set hashrate to speed o meter
                    int end = subStrings[1].indexOf("h");
                    String hashValue = subStrings[1].substring(1, end-1);
                    hashrateConfirmed = Double.parseDouble(hashValue);
                }
            }
        }
    }

    public void prepare(){
        sHandler = new JNICallbackHandler(this);
        miner = new BitZenyMiningLibrary(sHandler);
    }
}

public class MiningForeGroundService extends Service {

    private int BatteryTemp;

    private void sendHashrate(double hashRate) {
        // The string "my-message" will be used to filer the intent
        Intent intent = new Intent("my-message");
        // Adding some data
        intent.putExtra("hashrateConfirmed", hashRate);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendLogs(String value) {
        Intent intent = new Intent("my-log");
        // Adding some data
        intent.putExtra("logmessage", value);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private boolean isBatteryCharging() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                chargePlug == BatteryManager.BATTERY_PLUGGED_USB ||
                chargePlug == BatteryManager.BATTERY_PLUGGED_AC;

        return isCharging;
    }

    private float getBatteryPercentage()
    {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return (int)(level / (float)scale * 100);
    }

    String tdcAddressProv = "";
    String miningPoolAddress = "";
    boolean mobileDataAvoid = true;
    boolean batteryForMining = false;
    Integer cpuCoresSelected = 1;
    Integer cpuCoresMax = 1;
    Integer batteryLevelMin = 50;
    Integer batteryTempMax = 45;

    boolean getSettingValues(){
        boolean someThingChanged = false;

        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref",MODE_PRIVATE);
        String tdcAddress = sharedPreferences.getString("user", "");
        if (tdcAddressProv != tdcAddress) {
            tdcAddressProv = tdcAddress;
            someThingChanged = true;
        }

        String miningPool = PreferenceManager.getDefaultSharedPreferences(this).getString("mining_pool_selected", "0");
        if (miningPool.contains("4") && miningPoolAddress != "stratum+tcp://191.33.253.162:9585"){
            miningPoolAddress = "stratum+tcp://191.33.253.162:9585";
            someThingChanged = true;
        }
        if (miningPool.contains("3") && miningPoolAddress != "stratum+tcp://us.mining4people.com:3341"){
            miningPoolAddress = "stratum+tcp://us.mining4people.com:3341";
            someThingChanged = true;
        }
        if (miningPool.contains("2") && miningPoolAddress != "stratum+tcp://eu-stratum.phalanxmine.com:6235"){
            miningPoolAddress = "stratum+tcp://eu-stratum.phalanxmine.com:6235";
            someThingChanged = true;
        }
        if (miningPool.contains("1") && miningPoolAddress != "stratum+tcp://pool.isotopec.org:7530"){
            miningPoolAddress = "stratum+tcp://pool.isotopec.org:7530";
            someThingChanged = true;
        }
        if (miningPool.contains("0") && miningPoolAddress != "209.126.6.239:7652"){
            miningPoolAddress = "stratum+tcp://209.126.6.239:7652";
            someThingChanged = true;
        }

        boolean accuForMining = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("accu_for_mining", false);
        if(batteryForMining != accuForMining){
            batteryForMining = accuForMining;
            someThingChanged = true;
        }

        boolean avoidMobileData = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("limit_data_usage_selected", false);
        if(mobileDataAvoid != avoidMobileData){
            mobileDataAvoid = avoidMobileData;
            someThingChanged = true;
        }

        boolean useHalfCpuPower = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefs_second_cpu_thread", false);
        int numberCPUs = Runtime.getRuntime().availableProcessors();
        if (numberCPUs == 0){
            numberCPUs = 2;
        }
        cpuCoresMax = numberCPUs;
        if (!useHalfCpuPower){
            int newValue = (int)(numberCPUs * 0.25);
            if (newValue == 0) {
                newValue = 1;
            }
            if (newValue != cpuCoresSelected){
                someThingChanged = true;
                cpuCoresSelected = newValue;
            }
        }
        if (useHalfCpuPower){
            int newValue = (int)(numberCPUs * 0.5);
            if (newValue == 0) {
                newValue = 1;
            }
            if (newValue != cpuCoresSelected){
                someThingChanged = true;
                cpuCoresSelected = newValue;
            }
        }

        String batteryLevelSelected = PreferenceManager.getDefaultSharedPreferences(this).getString("battery_level_min_selected", "0");
        if (batteryLevelSelected.contains("0") && batteryLevelMin != 50){
            batteryLevelMin = 50;
            someThingChanged = true;
        }
        if (batteryLevelSelected.contains("1") && batteryLevelMin != 70){
            batteryLevelMin = 70;
            someThingChanged = true;
        }
        if (batteryLevelSelected.contains("2") && batteryLevelMin != 80){
            batteryLevelMin = 80;
            someThingChanged = true;
        }
        if (batteryLevelSelected.contains("3") && batteryLevelMin != 90){
            batteryLevelMin = 90;
            someThingChanged = true;
        }
        if (batteryLevelSelected.contains("4") && batteryLevelMin != 95){
            batteryLevelMin = 95;
            someThingChanged = true;
        }

        String batteryTempMaxSelected = PreferenceManager.getDefaultSharedPreferences(this).getString("battery_temp_max_selected", "1");
        if (batteryTempMaxSelected.contains("0") && batteryTempMax != 40){
            batteryTempMax = 40;
            someThingChanged = true;
        }
        if (batteryTempMaxSelected.contains("1") && batteryTempMax != 45){
            batteryTempMax = 45;
            someThingChanged = true;
        }
        if (batteryTempMaxSelected.contains("2") && batteryTempMax != 50){
            batteryTempMax = 50;
            someThingChanged = true;
        }

        return someThingChanged;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // activate bazzery temp check
        IntentFilter intentfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        MiningForeGroundService.this.registerReceiver(broadcastreceiver,intentfilter);

        // setup mining
        miningHidden miningLibary = new miningHidden();
        miningLibary.prepare();

        final String CHANNELID = "TidecoinMiner";
        NotificationChannel channel = new NotificationChannel(
                CHANNELID,
                CHANNELID,
                NotificationManager.IMPORTANCE_LOW);

        getSettingValues();

        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        Notification.Builder notification = new Notification.Builder(this, CHANNELID)
                .setContentText("Open App for statistics.")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setSubText("used cores: " + cpuCoresSelected)
                .setUsesChronometer(true)
                .setContentTitle("TDC Miner")
                .setOngoing(true);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mining:Tidecoin");
        wakeLock.setReferenceCounted(true);
        wakeLock.setWorkSource(new WorkSource());

        startForeground(427642, notification.build());

        new Thread(
                new Runnable() {
                    @Override
                    public void run() {
                        while (true) {

                            // get setting values
                            boolean somethingChanged = getSettingValues();
                            try {
                                Thread.sleep(1000); // please leave delay at this point, because device needs to load settings from storage.
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            // stop mining if there is a change inside settings
                            if(somethingChanged){
                                if (miningLibary.miner.isMiningRunning()){
                                    miningLibary.miner.stopMining();
                                }
                                if (wakeLock.isHeld()){
                                    wakeLock.release();
                                }
                                sendHashrate(0);
                                sendLogs("[STATUS] Stopped, because settings changed");
                            }

                            if (BatteryTemp > batteryTempMax){
                                if (miningLibary.miner.isMiningRunning()){
                                    miningLibary.miner.stopMining();
                                }
                                if (wakeLock.isHeld()){
                                    wakeLock.release();
                                }
                                sendHashrate(0);
                                sendLogs("[STATUS] Wait cooling battery");
                            }

                            if(getBatteryPercentage() < batteryLevelMin){
                                if (miningLibary.miner.isMiningRunning()){
                                    miningLibary.miner.stopMining();
                                }
                                if (wakeLock.isHeld()){
                                    wakeLock.release();
                                }
                                sendHashrate(0);
                                sendLogs("[STATUS] Wait battery for charging to set level");
                            }

                            if(!isBatteryCharging() && !batteryForMining){
                                if (miningLibary.miner.isMiningRunning()){
                                    miningLibary.miner.stopMining();
                                }
                                if (wakeLock.isHeld()){
                                    wakeLock.release();
                                }
                                sendHashrate(0);
                                sendLogs("[STATUS] Wait for charging device");
                            }

                            ConnectivityManager cm = (ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
                            NetworkInfo nInfo = cm.getActiveNetworkInfo();
                            boolean isWiFi = false;
                            if(nInfo != null){
                                // do not remove. Avoid null exception!
                                isWiFi = nInfo.getType() == ConnectivityManager.TYPE_WIFI;
                            }
                            if(!isWiFi && mobileDataAvoid){
                                if (miningLibary.miner.isMiningRunning()){
                                    miningLibary.miner.stopMining();
                                }
                                if (wakeLock.isHeld()){
                                    wakeLock.release();
                                }
                                sendHashrate(0);
                                sendLogs("[STATUS] Wait for Wifi Connection");
                            }


                            if (tdcAddressProv == null || tdcAddressProv == ""){
                                sendLogs("[STATUS] Device is not mining\nPlease provide your TDC - Address.");
                            }

                            boolean deviceIsCharging = isBatteryCharging();
                            if (batteryForMining){
                                deviceIsCharging = true;
                            }
                            if(tdcAddressProv != null && tdcAddressProv != "" && !mobileDataAvoid && BatteryTemp < batteryTempMax && getBatteryPercentage() > batteryLevelMin && deviceIsCharging && !miningLibary.miner.isMiningRunning()){
                                wakeLock.acquire(1440*60*1000L /*one day*/);
                                BitZenyMiningLibrary.Algorithm algorithm = BitZenyMiningLibrary.Algorithm.YESPOWER;
                                if (wakeLock.isHeld()){
                                    miningLibary.miner.startMining(
                                            (String)miningPoolAddress,
                                            (String)tdcAddressProv + ".TideMine-App",
                                            (String)"c=TDC",
                                            (int)cpuCoresSelected,
                                            algorithm);
                                    sendLogs("[STATUS] Mining started");
                                }else{
                                    sendLogs("[STATUS] Mining NOT started, will retry...");
                                }
                            }

                            if(tdcAddressProv != null && tdcAddressProv != "" && mobileDataAvoid && isWiFi && BatteryTemp < batteryTempMax && getBatteryPercentage() > batteryLevelMin && deviceIsCharging && !miningLibary.miner.isMiningRunning()){
                                wakeLock.acquire(1440*60*1000L /*one day*/);
                                BitZenyMiningLibrary.Algorithm algorithm = BitZenyMiningLibrary.Algorithm.YESPOWER;
                                if (wakeLock.isHeld()){
                                    miningLibary.miner.startMining(
                                            (String)miningPoolAddress,
                                            (String)tdcAddressProv + ".TideMine-App",
                                            (String)"c=TDC",
                                            (int)cpuCoresSelected,
                                            algorithm);
                                    sendLogs("[STATUS] Mining was started");
                                }else{
                                    sendLogs("[STATUS] Mining NOT started, will retry...");
                                }
                            }

                            if(miningLibary.miner.isMiningRunning()){
                                sendHashrate(miningLibary.hashrateConfirmed);
                                sendLogs("[STATUS] Device is Mining\nLOG:" + miningLibary.logMessage + "\nPool: " + miningPoolAddress + "\nTDC Address: " + tdcAddressProv + "\nCPU Cores: " + cpuCoresSelected + "/" + cpuCoresMax);
                            }else{
                                sendHashrate(0);

                                if (wakeLock.isHeld()){
                                    wakeLock.release();
                                }
                                try {
                                    Thread.sleep(10000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
        ).start();

        return START_STICKY;
    }


    private BroadcastReceiver broadcastreceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BatteryTemp = (int)(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0))/10;
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
