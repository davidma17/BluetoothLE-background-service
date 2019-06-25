package com.example.android.ble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.example.android.common.logger.Log;

import java.util.Timer;
import java.util.TimerTask;

public class RestartedActivity {
    public static final String TAG = "RestartedActivity";

    protected void logTest(){
        Log.i(TAG, "accessed restarted activity!");
    }

    class SayHello extends TimerTask {
        public void run() {
            Log.d("RESTARTDEBUGGER", "Reached TimerTask!");
            if(BLECentralHelper.getInstance() == null){
                Log.d("RESTARTDEBUGGER", "Instance is null!");
            } else {
                BLECentralHelper.getInstance().send("Hello!");
            }
        }
    }

    protected void reconnect(){
        if(BLECentralHelper.getInstance().getmConnectedGatt() != null){
            Log.i(TAG, "ConnectedGATT is: " + BLECentralHelper.getInstance().getmConnectedGatt().toString());
            Context con = BLECentralHelper.getInstance().getContext();
            BluetoothDevice dev = BLECentralHelper.getInstance().getDev();
            BLECentralChatEvents ev = BLECentralHelper.getInstance().getCCE();

            BLECentralHelper.getInstance().connect(con, dev, ev);

            Timer timer = new Timer();
            timer.schedule(new SayHello(), 10000, 2000);
        } else {
            Log.i(TAG, "Uhh.. ConnectedGATT is null");
        }
    }
}

