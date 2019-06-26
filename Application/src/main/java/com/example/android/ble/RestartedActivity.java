package com.example.android.ble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.example.android.common.logger.Log;

import java.util.Timer;
import java.util.TimerTask;

public class RestartedActivity {
    public static final String TAG = "RestartedActivity";
    private boolean centralIsRunning = false;
    private boolean peripheralIsRunning = false;

    protected void logTest(){
        Log.i(TAG, "accessed restarted activity!");
    }

    class CentralSayHello extends TimerTask {
        public void run() {
//            if(!peripheralIsRunning){
//                peripheralReconnect();
//            }
            Log.d("RESTARTDEBUGGER", "Central reached TimerTask!");
            Log.d("RESTARTDEBUGGER", "Peripheral? " + peripheralIsRunning);
            if(BLECentralHelper.getInstance() == null || BLECentralHelper.getInstance().getmConnectedGatt() == null){
                Log.d("RESTARTDEBUGGER", "Central instance is null!");
            } else {
                BLECentralHelper.getInstance().send("Hi from Central!");
            }
        }
    }

    protected void centralReconnect(){
        if(BLECentralHelper.getInstance().getmConnectedGatt() != null){
            if(!centralIsRunning){
                Log.i(TAG, "ConnectedGATT is: " + BLECentralHelper.getInstance().getmConnectedGatt().toString());
                Context con = BLECentralHelper.getInstance().getContext();
                BluetoothDevice dev = BLECentralHelper.getInstance().getDev();
                BLECentralChatEvents ev = BLECentralHelper.getInstance().getCCE();

                BLECentralHelper.getInstance().connect(con, dev, ev);
                Timer timer = new Timer();
                timer.schedule(new CentralSayHello(), 5000, 2000);
                centralIsRunning = true;
            } else {
                Log.i(TAG, "Central is already running!");
            }
        } else {
            Log.i(TAG, "Uhh.. ConnectedGATT is null");
        }
    }

//    class CentralReconnecter extends TimerTask {
//        public void run() {
//            if(!centralIsRunning){centralReconnect();}
//        }
//    }
//
//    protected void shouldCentralReconnect(){
//        Timer timer = new Timer();
//        timer.schedule(new CentralReconnecter(), 0, 1000);
//    }

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
        if(BLEPeripheralHelper.getInstance().getConnectedDevices() != null && !BLEPeripheralHelper.getInstance().getConnectedDevices().isEmpty()){
            if(!peripheralIsRunning){
                Timer timer = new Timer();
                timer.schedule(new PeripheralSayHello(), 5000, 2000);
                peripheralIsRunning = true;
            } else {
                Log.i(TAG, "Peripheral is already running!");
            }
        } else {
            Log.i(TAG, "Uhh.. BLEPeripheralHelper is null");
        }
    }

//    class PeripheralReconnecter extends TimerTask {
//        public void run() {
//            if(!peripheralIsRunning){peripheralReconnect();}
//        }
//    }
//
//    protected void shouldPeripheralReconnect(){
//        Timer timer = new Timer();
//        timer.schedule(new PeripheralReconnecter(), 0, 1000);
//    }

    public boolean getCentralRunning(){
        return centralIsRunning;
    }

    public boolean getPeripheralRunning(){
        return peripheralIsRunning;
    }
}

