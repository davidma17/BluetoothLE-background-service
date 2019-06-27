package com.example.android.ble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.example.android.common.logger.Log;

import java.util.Timer;
import java.util.TimerTask;

public class RestartedActivity {
    public static final String TAG = "RestartedActivity";

    class CentralSayHello extends TimerTask {
        public void run() {
            Log.d("RESTARTDEBUGGER", "Central reached TimerTask!");
            if(BLECentralHelper.getInstance() == null || BLECentralHelper.getInstance().getmConnectedGatt() == null){
                Log.d("RESTARTDEBUGGER", "Central instance is null!");
            } else {
                BLECentralHelper.getInstance().send("Hi from Central!");
            }
        }
    }

    protected void centralReconnect(){
        if(BLECentralHelper.getInstance().getmConnectedGatt() != null){
            Context con = BLECentralHelper.getInstance().getContext();
            BluetoothDevice dev = BLECentralHelper.getInstance().getDev();
            BLECentralChatEvents ev = BLECentralHelper.getInstance().getCCE();

            BLECentralHelper.getInstance().connect(con, dev, ev);
            Timer timer = new Timer();
            timer.schedule(new CentralSayHello(), 5000, 2000);
        } else {
            Log.i(TAG, "Uhh.. ConnectedGATT is null");
        }
    }

    class PeripheralSayHello extends TimerTask {
        public void run() {
            Log.d("RESTARTDEBUGGER", "Peripheral reached TimerTask!");
            if(BLEPeripheralHelper.getInstance() == null){
                Log.d("RESTARTDEBUGGER", "Peripheral instance is null!");
            } else {
                BLEPeripheralHelper.getInstance().send("Hi from Peripheral!");
            }
        }
    }

    protected void peripheralReconnect(){
        Log.i(TAG, "PeripheralReconnect called!");
        if(BLEPeripheralHelper.getInstance().getConnectedDevices() != null && !BLEPeripheralHelper.getInstance().getConnectedDevices().isEmpty()){
                Timer timer = new Timer();
                timer.schedule(new PeripheralSayHello(), 5000, 2000);
        } else {
            Log.i(TAG, "Uhh.. BLEPeripheralHelper is null");
        }
    }



}

