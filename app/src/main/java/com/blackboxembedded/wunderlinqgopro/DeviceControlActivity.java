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

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.net.wifi.WifiManager;
import android.widget.ProgressBar;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class DeviceControlActivity extends AppCompatActivity implements View.OnTouchListener  {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private String mDeviceName;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;

    private CameraStatus cameraStatus;

    private View view;
    private ProgressBar progressBar;
    private ImageView modeImageView;
    private Button shutterButton;
    PopUpClass popUpClass;

    private int highlightColor;

    private GestureDetectorListener gestureDetector;

    private String SSID;
    private String password;
    private WifiManager wifiManager;
    ConnectivityManager connectivityManager;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected()");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress, mDeviceName);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_NOTFICATION_ENABLED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_SERVICE_DISCONNECTED);
        return intentFilter;
    }

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
            } else if (BluetoothLeService.ACTION_SERVICE_DISCONNECTED.equals(action)){
                finish();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                finish();
            } else if (BluetoothLeService.ACTION_NOTFICATION_ENABLED.equals(action)) {
                mBluetoothLeService.requestCameraStatus();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Bundle bd = intent.getExtras();
                if(bd != null){
                    if(bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) != null) {
                        if (bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE).contains(GattAttributes.GOPRO_COMMANDRESPONSE_CHARACTERISTIC)) {
                            byte[] data = bd.getByteArray(BluetoothLeService.EXTRA_BYTE_VALUE);
                            String characteristicValue = Utils.ByteArraytoHex(data) + " ";
                            Log.d(TAG, "UUID: " + bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) + " DATA: " + characteristicValue);
                            if(data[2] == 0x00 ){
                                if (data[1] == 0x17) {
                                    mBluetoothLeService.requestWiFiSettings();
                                } else {
                                    updateUIElements();
                                }
                            } else {
                                mBluetoothLeService.requestCameraStatus();
                            }
                        } else if (bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE).contains(GattAttributes.GOPRO_QUERYRESPONSE_CHARACTERISTIC)) {
                            byte[] data = bd.getByteArray(BluetoothLeService.EXTRA_BYTE_VALUE);
                            String characteristicValue = Utils.ByteArraytoHex(data) + " ";
                            Log.d(TAG, "UUID: " + bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) + " DATA: " + characteristicValue);
                            if (data.length > 17) {
                                cameraStatus = new CameraStatus();
                                cameraStatus.busy = (data[5] == 0x01);
                                cameraStatus.mode = data[17];
                                cameraStatus.previewAvailable = (data[11] == 0x01);
                                cameraStatus.wifiEnabled = (data[8] == 0x01);
                                updateUIElements();
                            } else {
                                mBluetoothLeService.requestCameraStatus();
                            }
                        } else if (bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE).contains(GattAttributes.GOPRO_WIFI_SSID_CHARACTERISTIC)) {
                            byte[] data = bd.getByteArray(BluetoothLeService.EXTRA_BYTE_VALUE);
                            String characteristicValue = Utils.ByteArraytoHex(data) + " ";
                            Log.d(TAG, "UUID: " + bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) + " DATA: " + characteristicValue);
                            SSID = new String(data);
                            Log.d(TAG,"WIFI SSID: " + SSID);
                            if ((SSID != null) && (password != null)){
                                connectToWifi(SSID,password);
                            }
                        } else if (bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE).contains(GattAttributes.GOPRO_WIFI_PASSWORD_CHARACTERISTIC)) {
                            byte[] data = bd.getByteArray(BluetoothLeService.EXTRA_BYTE_VALUE);
                            String characteristicValue = Utils.ByteArraytoHex(data) + " ";
                            Log.d(TAG, "UUID: " + bd.getString(BluetoothLeService.EXTRA_BYTE_UUID_VALUE) + " DATA: " + characteristicValue);
                            password = new String(data);
                            Log.d(TAG,"WIFI Password: " + password);
                            if ((SSID != null) && (password != null)){
                                connectToWifi(SSID,password);
                            }
                        }
                    }
                }

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
        view = findViewById(R.id.controlLayOut);
        progressBar = findViewById(R.id.progress_loader);
        modeImageView = findViewById(R.id.modeIV);
        shutterButton = findViewById(R.id.shutterBtn);
        shutterButton.setOnClickListener(mClickListener);
        shutterButton.setVisibility(View.INVISIBLE);

        gestureDetector = new GestureDetectorListener(this) {

            @Override
            public void onSwipeUp() {
                upKey();
            }

            @Override
            public void onSwipeDown() {
                downKey();
            }

            @Override
            public void onSwipeLeft() {
                rightKey();
            }

            @Override
            public void onSwipeRight() {
                leftKey();
            }
        };
        view.setOnTouchListener(this);

        highlightColor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getInt("prefHighlightColor", R.color.colorAccent);
        shutterButton.setBackgroundColor(highlightColor);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        if (mBluetoothLeService != null) {
            mBluetoothLeService.connect(mDeviceAddress, mDeviceName);
        }

    }

    @Override
    protected void onResume() {
        Log.d(TAG,"onResume()");
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

    }

    @Override
    protected void onPause() {
        Log.d(TAG,"onPause()");
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG,"onDestroy()");
        super.onDestroy();
        if (mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
        }
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateUIElements();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        gestureDetector.onTouch(v, event);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                //Toggle Shutter
                toggleShutter();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                //Scroll through modes
                nextMode();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                //Scroll through modes
                previousMode();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                leftKey();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                //Open Camera Preview
                rightKey();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                String wunderLINQApp = "wunderlinq://datagrid";
                Intent intent = new
                        Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(Uri.parse(wunderLINQApp));
                try {
                    startActivity(intent);
                } catch ( ActivityNotFoundException ex  ) {
                }
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
                    toggleShutter();
                    break;
            }
        }
    };

    private void updateUIElements(){
        if (cameraStatus != null) {
            switch (cameraStatus.mode) {
                case (byte)0xE8:
                    //Video
                    modeImageView.setImageResource(R.drawable.ic_video_camera);
                    if (cameraStatus.busy){
                        shutterButton.setText(R.string.task_title_stop_record);
                    } else {
                        shutterButton.setText(R.string.task_title_start_record);
                    }
                    break;
                case (byte)0xE9:
                    //Photo
                    modeImageView.setImageResource(R.drawable.ic_camera);
                    shutterButton.setText(R.string.task_title_photo);
                    break;
                case (byte)0xEA:
                    //Timelapse
                    modeImageView.setImageResource(R.drawable.timelapse);
                    if (cameraStatus.busy){
                        shutterButton.setText(R.string.task_title_stop_timelapse);
                    } else {
                        shutterButton.setText(R.string.task_title_start_timelapse);
                    }
                    break;
                default:
                    modeImageView.setImageResource(0);
                    Log.e(TAG,"Unknown mode: " + cameraStatus.mode);
                    break;
            }
            progressBar.setVisibility(View.INVISIBLE);
            modeImageView.setVisibility(View.VISIBLE);
            shutterButton.setVisibility(View.VISIBLE);
        } else {
            modeImageView.setImageResource(0);
            modeImageView.setVisibility(View.INVISIBLE);
            shutterButton.setVisibility(View.INVISIBLE);
            mBluetoothLeService.requestCameraStatus();
        }
    }

    private void upKey(){
        nextMode();
    }

    private void downKey(){
        previousMode();
    }

    private void leftKey(){ finish(); }

    private void rightKey(){ enableWifi(); }

    private void toggleShutter(){
        if (cameraStatus != null){
            byte[] command;
            if (cameraStatus.busy){
                command = new byte[]{0x01, 0x01, 0x00};
                cameraStatus.busy = false;
            } else {
                command = new byte[]{0x01, 0x01, 0x01};
                cameraStatus.busy = true;
            }
            mBluetoothLeService.setCommand(command);
        } else {
            mBluetoothLeService.requestCameraStatus();
        }
    }

    private void nextMode() {
        //Next Camera Mode
        if (cameraStatus != null){
            if (cameraStatus.mode == (byte)0xEA) {
                cameraStatus.mode = (byte)0xE8;
            } else {
                cameraStatus.mode = (byte)(cameraStatus.mode + 0x01);
            }
            byte[] command = new byte[]{0x3E,0x02,0x03,cameraStatus.mode};
            mBluetoothLeService.setCommand(command);
        } else {
            mBluetoothLeService.requestCameraStatus();
        }
    }

    private void previousMode() {
        //Previous Camera Mode
        if (cameraStatus != null){
            if (cameraStatus.mode == (byte)0xE8) {
                cameraStatus.mode = (byte)0xEA;
            } else {
                cameraStatus.mode = (byte)(cameraStatus.mode - 0x01);
            }
            byte[] command = new byte[]{0x3E,0x02,0x03,cameraStatus.mode};
            mBluetoothLeService.setCommand(command);
        } else {
            mBluetoothLeService.requestCameraStatus();
        }
    }

    private void enableWifi() {
        if (cameraStatus != null){
            if (!cameraStatus.busy) {
                byte[] command = new byte[]{0x17, 0x01, 0x01};
                mBluetoothLeService.setCommand(command);
            }
        } else {
            mBluetoothLeService.requestCameraStatus();
        }
    }

    public void disconnectFromWifi(){
        //Unregistering network callback instance supplied to requestNetwork call disconnects phone from the connected network
        connectivityManager.unregisterNetworkCallback(networkCallback);
    }

    /**
     * Connect to the specified wifi network.
     *
     * @param ssid     - The wifi network SSID
     * @param password - the wifi password
     */
    private void connectToWifi(String ssid, String password) {
        Log.d(TAG,"connectToWifi()");
        progressBar.setVisibility(View.VISIBLE);
        modeImageView.setVisibility(View.INVISIBLE);
        shutterButton.setVisibility(View.INVISIBLE);
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            try {
                WifiConfiguration wifiConfig = new WifiConfiguration();
                wifiConfig.SSID = "\"" + ssid + "\"";
                wifiConfig.preSharedKey = "\"" + password + "\"";
                int netId = wifiManager.addNetwork(wifiConfig);
                wifiManager.disconnect();
                wifiManager.enableNetwork(netId, true);
                wifiManager.reconnect();

            } catch ( Exception e) {
                e.printStackTrace();
            }
        } else {
            WifiNetworkSpecifier wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build();

            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(wifiNetworkSpecifier)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();

            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.requestNetwork(networkRequest, networkCallback);
        }
    }

    private ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            Log.e(TAG,"onAvailable");
            connectivityManager.bindProcessToNetwork(network);
            enablePreview();
        }

        @Override
        public void onLosing(@NonNull Network network, int maxMsToLive) {
            super.onLosing(network, maxMsToLive);
            Log.e(TAG,"onLosing");
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            Log.e(TAG, "losing active connection");
            connectivityManager.bindProcessToNetwork(null);
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        @Override
        public void onUnavailable() {
            super.onUnavailable();
            Log.e(TAG,"onUnavailable");
        }
    };

    private void enablePreview(){
        URL url = null;
        try {
            url = new URL("http://10.5.5.9:8080/gopro/camera/stream/start");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            int data = in.read();
            while (data != -1) {
                char current = (char) data;
                data = in.read();
                System.out.print(current);
            }
            Log.d(TAG, "Response Code: " + urlConnection.getResponseCode());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            urlConnection.disconnect();
        }
        startPreview();
    }

    private void startPreview() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Stuff that updates the UI
                progressBar.setVisibility(View.INVISIBLE);
                modeImageView.setVisibility(View.VISIBLE);
                shutterButton.setVisibility(View.VISIBLE);
                popUpClass = new PopUpClass();
                popUpClass.showPopupWindow(view);
            }
        });
    }
}
