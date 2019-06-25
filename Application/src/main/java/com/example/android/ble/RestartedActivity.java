package com.example.android.ble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.example.android.common.logger.Log;

public class RestartedActivity {
    public static final String TAG = "RestartedActivity";

    protected void logTest(){
        Log.i(TAG, "accessed restarted activity!");
    }

    protected void reconnect(){
        if(BLECentralHelper.getInstance().getmConnectedGatt() != null){
            Log.i(TAG, "ConnectedGATT is: " + BLECentralHelper.getInstance().getmConnectedGatt().toString());
            Context con = BLECentralHelper.getInstance().getContext();
            BluetoothDevice dev = BLECentralHelper.getInstance().getDev();
            BLECentralChatEvents ev = BLECentralHelper.getInstance().getCCE();

            BLECentralHelper.getInstance().connect(con, dev, ev);
        } else {
            Log.i(TAG, "Uhh.. ConnectedGATT is null");
        }
    }
}

