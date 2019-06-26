package com.example.android.ble;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.example.android.bluetoothchat.MainActivity;
import com.example.android.common.logger.Log;

import java.util.Timer;
import java.util.TimerTask;

public class MyService extends Service {
    public int counter = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1, new Notification());
        makeToast("onCreate!");
    }

    public void makeToast(String msg){
        Toast.makeText(getApplicationContext(),msg,Toast.LENGTH_SHORT).show();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        startTimer();
        makeToast("onStartCommand!");

        RestartedActivity ra = new RestartedActivity();
//        Timer raTimer = new Timer();
//        TimerTask raTimerTask = new TimerTask() {
//            public void run() {
//                Log.i("raTimerTask", "confirmation");
//                ra.centralReconnect();
//                ra.peripheralReconnect();
//            }
//        };
//        raTimer.schedule(raTimerTask, 0, 1000);

//        if(!firstTime) {
//            while(!ra.getCentralRunning()){
//                Log.i("raCentral", "Central trying to connect");
//                ra.centralReconnect();
//            }
//
//            while(!ra.getPeripheralRunning()){
//                Log.i("raPeripheral", "Peripheral trying to connect");
//                ra.peripheralReconnect();
//            }
//        }

        ra.centralReconnect();
        if(!ra.getPeripheralRunning()){
            ra.peripheralReconnect();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stoptimertask();

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartservice");
        broadcastIntent.setClass(this, Restarter.class);
        this.sendBroadcast(broadcastIntent);
    }

    private Timer timer;
    private TimerTask timerTask;
    public void startTimer() {
        timer = new Timer();
        timerTask = new TimerTask() {
            public void run() {
                Log.i("Count", "=========  "+ (counter++));
            }
        };
        timer.schedule(timerTask, 1000, 1000); //
    }

    public void stoptimertask() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}