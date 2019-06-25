/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothchat;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.ble.BLEAdvertisingActivity;
import com.example.android.ble.BLECentralHelper;
import com.example.android.ble.BLECentralChatEvents;
import com.example.android.ble.BLEDiscoveringActivity;
import com.example.android.ble.BLEMode;
import com.example.android.ble.BLEPeripheralChatEvents;
import com.example.android.ble.BLEPeripheralHelper;
import com.example.android.common.logger.Log;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "BluetoothChatFragment";
    private BLEMode mBleMode = BLEMode.CENTRAL;

    // Intent request codes
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int BLE_REQUEST_CONNECT_DEVICE = 11;
    private static final int BLE_REQUEST_DEVICE_CONNECTING = 12;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT wasbuffer
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.in);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);
        mSendButton = (Button) view.findViewById(R.id.button_send);
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click eventsbuffer
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    sendMessageViaBLE(message);
                }
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessageViaBLE(message);
            }
            return true;
        }
    };

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * Some helper methods for the mHandler messaging mechanism
     **/

    private void showIncomingMessage(String msg) {
        mHandler.obtainMessage(Constants.MESSAGE_READ, msg.length(), -1, msg.getBytes())
                .sendToTarget();
    }

    private void showOutgoingMessage(String msg) {
        mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, msg.getBytes())
                .sendToTarget();
    }

    private void showInfo(String info) {
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, info);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        Log.i(TAG, info);
    }

    private void showStatus(int status) {
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, status, -1)
                .sendToTarget();
    }

    private void showConnectedName(String name) {
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, name);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(BluetoothChatService.STATE_CONNECTED);
    }

    private void setState(int newState) {
        switch (newState) {
            case BluetoothChatService.STATE_CONNECTED:
                if (getActivity() != null){
                    setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                    //mConversationArrayAdapter.clear();
                }
                break;
            case BluetoothChatService.STATE_CONNECTING:
                setStatus(R.string.title_connecting);
                break;
            case BluetoothChatService.STATE_LISTEN:
            case BluetoothChatService.STATE_NONE:
                setStatus(R.string.title_not_connected);
                break;
        }
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            Log.d("DEBUGGER", String.valueOf(activity));
            if (activity != null) {
                switch (msg.what) {
                    case Constants.MESSAGE_STATE_CHANGE:
                        setState(msg.arg1);
                        break;
                    case Constants.MESSAGE_WRITE:
                        byte[] writeBuf = (byte[]) msg.obj;
                        // construct a string from the buffer
                        String writeMessage = new String(writeBuf);
                        mConversationArrayAdapter.add("Me:  " + writeMessage);
                        break;
                    case Constants.MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;
                        // construct a string from the valid bytes in the buffer
                        String readMessage = new String(readBuf, 0, msg.arg1);
                        mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                        //answerBack(readMessage);
                        break;
                    case Constants.MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                        if (null != activity) {
                            Toast.makeText(activity, "Connected to "
                                    + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                        }
                        break;
                    case Constants.MESSAGE_TOAST:
                        if (null != activity) {
                            Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                    Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        }
    };


    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
                break;
            case BLE_REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    connectBleDevice(data);
                }
                break;
            case BLE_REQUEST_DEVICE_CONNECTING:
                if (resultCode == Activity.RESULT_OK) {
                    bleDeviceConnecting(data);
                }
                break;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
            case R.id.ble_advertise: {
                startAdvertising();
                return true;
            }
            case R.id.ble_discover: {
                startScanning();
                return true;
            }

        }
        return false;
    }


    /**
     *
     *****************************
     * Bluetooth LE Specific code.
     *****************************
     *
     */

    /**
     * Starts BLE Advertisement
     */
    private void startAdvertising() {
        Intent advertisementIntent = new Intent(getContext(), BLEAdvertisingActivity.class);
        startActivityForResult(advertisementIntent, BLE_REQUEST_DEVICE_CONNECTING);
    }

    private void startScanning() {
        Intent scanningIntent = new Intent(getActivity(), BLEDiscoveringActivity.class);
        startActivityForResult(scanningIntent, BLE_REQUEST_CONNECT_DEVICE);
    }

    private synchronized void sendMessageViaBLE(String message) {
        // Check that there's actually something to send
        if (message.length() > 0) {
            if (mBleMode == BLEMode.PERIPHERAL) {
                BLEPeripheralHelper.getInstance().send(message);
            } else if (mBleMode == BLEMode.CENTRAL) {
                BLECentralHelper.getInstance().send(message);
            }
            showOutgoingMessage(message);
            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }
    /**
     * [Central device role]
     * <p>
     * Connect to a Peripheral device
     *
     * @param data
     */
    private void connectBleDevice(Intent data) {
        mBleMode = BLEMode.CENTRAL;
        if (mChatService != null)
            mChatService.stop();
        mConnectedDeviceName = data.getExtras().getString(BLEDiscoveringActivity.EXTRA_DEVICE_NAME);
        String address = data.getExtras().getString(BLEDiscoveringActivity.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        showStatus(BluetoothChatService.STATE_CONNECTING);
        BLECentralHelper.getInstance().connect(this.getContext(), device, mBLEChatEvents);
    }

    private BLECentralChatEvents mBLEChatEvents = new BLECentralChatEvents() {
        private Object mLock = new Object();

        @Override
        public void onVersion(String version) {
            synchronized (mLock) {
                showInfo("Version: " + version);
            }
        }

        @Override
        public void onDescription(String description) {
            synchronized (mLock) {
                showIncomingMessage("Description: " + description);
            }
        }

        @Override
        public void onMessage(String msg) {
            synchronized (mLock) {
                processIncomingMsg(msg);
            }
        }

        @Override
        public void onInfo(String info) {
            synchronized (mLock) {
                showInfo(info);
            }
        }

        @Override
        public void onConnect() {
            synchronized (mLock) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
//                        BLECentralHelper.getInstance().changeMtu(256);
                        sendMessageViaBLE("/name testName");
                        sendMessageViaBLE("testing");
                        showConnectedName(mConnectedDeviceName);
                        showStatus(BluetoothChatService.STATE_CONNECTED);
                    }
                }, 2000);
            }
        }

        @Override
        public void onDisconnect() {
            synchronized (mLock) {
                Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.TOAST, new String("[!] Disconnected"));
                msg.setData(bundle);
                mHandler.sendMessage(msg);

                showStatus(BluetoothChatService.STATE_NONE);
            }
        }

        @Override
        public void onConnectionError(String error) {
            synchronized (mLock) {
                showStatus(BluetoothChatService.STATE_NONE);
                showInfo("[!] Error : " + error);

            }
        }
    };


    /**
     * [Peripheral device role]
     * <p>
     * A Central device is connecting to us
     *
     * @param data
     */
    private void bleDeviceConnecting(Intent data) {
        mBleMode = BLEMode.PERIPHERAL;
        if (mChatService != null)
            mChatService.stop();
        //showConnectedName(data.getExtras().getString(BLEAdvertisingActivity.EXTRA_CLIENT_NAME));
        showStatus(BluetoothChatService.STATE_CONNECTED);
        BLEPeripheralHelper.getInstance().register(mBlePeripheralChatEvents);
    }

    private BLEPeripheralChatEvents mBlePeripheralChatEvents = new BLEPeripheralChatEvents() {
        private Object mLock = new Object();

        @Override
        public void onClientDisconnect(BluetoothDevice device) {
            synchronized (mLock) {
                showInfo(device.getName() + " disconnected");
                setStatus(R.string.title_not_connected);
            }
        }

        @Override
        public void onMessage(String msg) {
            synchronized (mLock) {
                processIncomingMsg(msg);
            }
        }

        @Override
        public void onInfo(String info) {
            synchronized (mLock) {
                showInfo(info);
            }
        }

        @Override
        public void onConnectionError(String error) {
            synchronized (mLock) {
                showInfo("[!] Error : " + error);
            }
        }
    };

    private void processIncomingMsg(String msg) {
        if (msg.startsWith("/")) {
            String[] tokens = msg.split(" ", 2);
            if (tokens[0].compareTo("/name") == 0) {
                showConnectedName(tokens[1]);
            }
        } else {
            showIncomingMessage(msg);
        }
    }
}