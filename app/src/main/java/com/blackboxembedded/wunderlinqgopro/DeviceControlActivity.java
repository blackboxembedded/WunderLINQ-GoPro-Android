/*
WunderLINQ Client Application
Copyright (C) 2020  Keith Conger, Black Box Embedded, LLC

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.blackboxembedded.wunderlinqgopro;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import java.util.List;

public class DeviceControlActivity extends AppCompatActivity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;

    private boolean mConnected = false;

    private BluetoothGattCharacteristic characteristic;
    private static BluetoothGattCharacteristic commandCharacteristic;
    public static BluetoothGattCharacteristic commandResponseCharacteristic;
    private static BluetoothGattCharacteristic queryCharacteristic;
    public static BluetoothGattCharacteristic queryResponseCharacteristic;

    private Button shutterBtn;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
                updateUIElements();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                checkGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.d(TAG,"DATA_AVAILABLE");
                Bundle bd = intent.getExtras();
                if(bd != null){
                    if(bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) != null) {
                        if (bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE).contains(GattAttributes.GOPRO_COMMANDRESPONSE_CHARACTERISTIC)) {
                            byte[] data = bd.getByteArray(BluetoothLeService.EXTRA_BYTE_VALUE);
                            String characteristicValue = Utils.ByteArraytoHex(data) + " ";
                            Log.d(TAG, "UUID: " + bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) + " DATA: " + characteristicValue);
                        } else if (bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE).contains(GattAttributes.GOPRO_QUERYRESPONSE_CHARACTERISTIC)) {
                            byte[] data = bd.getByteArray(BluetoothLeService.EXTRA_BYTE_VALUE);
                            String characteristicValue = Utils.ByteArraytoHex(data) + " ";
                            Log.d(TAG, "UUID: " + bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) + " DATA: " + characteristicValue);
                        }
                    }
                }
            } else if(BluetoothLeService.ACTION_WRITE_SUCCESS.equals(action)){
                Log.d(TAG,"Write Success Received");
                //BluetoothLeService.readCharacteristic(characteristic);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_control_activity);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        getSupportActionBar().setTitle(mDeviceName);

        shutterBtn = (Button) findViewById(R.id.shutterBtn);
        shutterBtn.setOnClickListener(mClickListener);

        shutterBtn.setVisibility(View.INVISIBLE);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    @Override
    protected void onResume() {
        Log.d(TAG, "In onResume");
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
        }
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                //Toggle Shutter
                //TODO
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                //Scroll through modes
                //TODO
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                //Scroll through modes
                //TODO
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                //Open Camera Preview
                //TODO
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                String wunderLINQApp = "wunderlinq://";
                Intent intent = new
                        Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(Uri.parse(wunderLINQApp));
                startActivity(intent);
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }
    private View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch(v.getId()) {
                case R.id.shutterBtn:
                    //Toggle Shutter
                    //TODO
                    break;
            }
        }
    };

    private void setCommand(byte[] command){
        byte[] fullcommand = new byte[command.length + 1];
        System.arraycopy(new byte[]{(byte) command.length}, 0, fullcommand, 0, 1);
        System.arraycopy(command, 0, fullcommand, 1, command.length);
        queryCharacteristic.setValue(fullcommand);
        BluetoothLeService.writeCharacteristic(queryCharacteristic);
    }

    private void requestCameraStatus(){
        byte[] command = {0x05,0x13,0x08,0x11,0x37,0x60};
        queryCharacteristic.setValue(command);
        BluetoothLeService.writeCharacteristic(queryCharacteristic);
    }

    private void checkGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            Log.d(TAG,"Found Service: " + gattService.getUuid().toString());
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                Log.d(TAG,"Found Characteristic: " + gattCharacteristic.getUuid().toString());
                String uuid = gattCharacteristic.getUuid().toString();
                if (uuid.contains(GattAttributes.GOPRO_COMMAND_CHARACTERISTIC)){
                    Log.d(TAG,"Found GoPro Command Characteristic");
                    commandCharacteristic = gattCharacteristic;
                }
                if (uuid.contains(GattAttributes.GOPRO_COMMANDRESPONSE_CHARACTERISTIC)) {
                    Log.d(TAG,"Found GoPro Command Response Characteristic");
                    int charaProp = gattCharacteristic.getProperties();
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // If there is an active notification on a characteristic, clear
                        // it first so it doesn't update the data field on the user interface.
                        if (commandResponseCharacteristic != null) {
                            mBluetoothLeService.setCharacteristicNotification(
                                    commandResponseCharacteristic, false);
                            commandResponseCharacteristic = null;
                        }
                        BluetoothLeService.readCharacteristic(gattCharacteristic);
                    }
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        commandResponseCharacteristic = gattCharacteristic;
                        mBluetoothLeService.setCharacteristicNotification(
                                gattCharacteristic, true);
                    }
                }
                if (uuid.contains(GattAttributes.GOPRO_QUERY_CHARACTERISTIC)){
                    Log.d(TAG,"Found GoPro Query Characteristic");
                    queryCharacteristic = gattCharacteristic;
                }
                if (uuid.contains(GattAttributes.GOPRO_QUERYRESPONSE_CHARACTERISTIC)) {
                    Log.d(TAG,"Found GoPro Query Response Characteristic");
                    int charaProp = gattCharacteristic.getProperties();
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // If there is an active notification on a characteristic, clear
                        // it first so it doesn't update the data field on the user interface.
                        if (queryResponseCharacteristic != null) {
                            mBluetoothLeService.setCharacteristicNotification(
                                    queryResponseCharacteristic, false);
                            queryResponseCharacteristic = null;
                        }
                        BluetoothLeService.readCharacteristic(gattCharacteristic);
                    }
                    if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                        queryResponseCharacteristic = gattCharacteristic;
                        mBluetoothLeService.setCharacteristicNotification(
                                gattCharacteristic, true);
                    }
                }
            }
        }
        if (queryCharacteristic != null) {
            requestCameraStatus();
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_WRITE_SUCCESS);
        return intentFilter;
    }

    private void updateUIElements(){
        if(characteristic != null){
            shutterBtn.setEnabled(true);
        } else {
            shutterBtn.setEnabled(false);
        }
    }


}
