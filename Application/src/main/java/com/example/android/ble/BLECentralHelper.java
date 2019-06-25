package com.example.android.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * This class will manage the Bluetooth LE Central stuff
 * A Central BLE device is a device which can connect to another Peripheral device that is
 * advertising services. So it's basically a client.
 *
 * It communicates with the outer space via events like:
 * * BLEDiscoverCallback
 * * BLECentralChatEvents
 *
 * Use init() no initialize the class
 *
 */
public class BLECentralHelper {

    private static final String TAG = "BLECentralHelper";
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mConnectedGatt;
    private BLEDiscoverCallback mBleDiscoveryCallback;
    private BLECentralChatEvents mBleChatEvents;

    private Handler mHandler = new Handler();

    private Context mContext;

    private static BLECentralHelper instance = new BLECentralHelper();
    private BLECentralHelper(){}
    public static BLECentralHelper getInstance(){
        if(instance == null){
            synchronized (BLECentralHelper.class){
                if(instance == null){
                    instance = new BLECentralHelper();
                }
            }
        }
        return instance;
    }

    public void init(Context context, BLEDiscoverCallback bleCallback){
        mContext = context;
        mBleDiscoveryCallback = bleCallback;
        if( context == null){
            mBleDiscoveryCallback.onInitFailure("Invalid Context!");
            return;
        }
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            mBleDiscoveryCallback.onInitFailure("Bluetooth not supported in this device!!");
            return;
        }

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            mBleDiscoveryCallback.onInitFailure("Bluetooth LE is not supported in this devices!!");
            return;
        }


        mBleDiscoveryCallback.onInitSuccess();
    }

    /**
     * This is a passive action, it will listen advertisements from other peripheral devices
     */
    public void startScan() {

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BLEChatProfile.SERVICE_UUID))
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
    }

    public void stopScan() {
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult");
            processResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onBatchScanResults: "+results.size()+" results");
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "LE Scan Failed: "+errorCode);
        }

        private void processResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            Log.i(TAG, "New LE Device: " + device.getName() + " @ " + result.getRssi());
            mBleDiscoveryCallback.onScanResult(device, result.getRssi());
        }
    };

    /*
     * Connect to a Bluetooth device
     *
     * @param context
     * @param device
     * @param events
     */
    Context con;
    BluetoothDevice dev;
    BLECentralChatEvents ev;

    public void connect(Context context, BluetoothDevice device, BLECentralChatEvents events){
        con = context;
        dev = device;
        ev = events;
        mBleChatEvents = events;
        mConnectedGatt = device.connectGatt(context, false, mGattCallback);
    }

    public Context getContext(){
        return con;
    }

    public BluetoothDevice getDev(){
        return dev;
    }

    public BLECentralChatEvents getCCE(){
        return ev;
    }


    public BluetoothGattCallback mGattCallback = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange "
                    +BLEChatProfile.getStatusDescription(status)+" "
                    +BLEChatProfile.getStateDescription(newState));

            if(status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mBleChatEvents.onDisconnect();
                        }
                    });

                }
            }else{
                final int finalStatus = status;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onConnectionError("Connection state error! : Error = " + finalStatus);
                    }
                });

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered:");

            for (BluetoothGattService service : gatt.getServices()) {
                Log.d(TAG, "Service: "+service.getUuid());
                if (BLEChatProfile.SERVICE_UUID.equals(service.getUuid())) {
                    gatt.readCharacteristic(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_VERSION_UUID));
                    gatt.readCharacteristic(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_DESC_UUID));
                    gatt.setCharacteristicNotification(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID), true);
                    gatt.setCharacteristicNotification(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_RFCOMM_TRANSFER_UUID), true);
                    gatt.setCharacteristicNotification(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_BLE_TRANSFER_UUID), true);
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBleChatEvents.onConnect();
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID.equals(characteristic.getUuid())) {
                final String msg = characteristic.getStringValue(0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onMessage(msg);
                    }
                });

                //Register for further updates as notifications
                gatt.setCharacteristicNotification(characteristic, true);
            }
            if (BLEChatProfile.CHARACTERISTIC_VERSION_UUID.equals(characteristic.getUuid())) {
                final String version = characteristic.getStringValue(0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onVersion(version);
                    }
                });

                //Register for further updates as notifications
                gatt.setCharacteristicNotification(characteristic, true);
            }
            if (BLEChatProfile.CHARACTERISTIC_DESC_UUID.equals(characteristic.getUuid())) {
                final String description = characteristic.getStringValue(0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onDescription(description);
                    }
                });

                //Register for further updates as notifications
                gatt.setCharacteristicNotification(characteristic, true);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG, "Notification of message characteristic changed on server.");
            if (BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID.equals(characteristic.getUuid())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onMessage(characteristic.getStringValue(0));
                    }
                });
            } else if (BLEChatProfile.CHARACTERISTIC_BLE_TRANSFER_UUID.equals(characteristic.getUuid())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onInfo("BLE_TRANS Charac changed!!");
                    }
                });
            }
        }
    }; //End BluetoothGattCallback

    public void send(byte[] data) {
        final BluetoothGattCharacteristic characteristic = mConnectedGatt
                .getService(BLEChatProfile.SERVICE_UUID)
                .getCharacteristic(BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID);

        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        characteristic.setValue(data);
        if(!mConnectedGatt.writeCharacteristic(characteristic)){
            mBleChatEvents.onConnectionError("Couldn't send data!!");
        }
    }

    public void send(String msg){
        send(msg.getBytes());
    }

    public BluetoothGatt getmConnectedGatt(){return mConnectedGatt;}
}
