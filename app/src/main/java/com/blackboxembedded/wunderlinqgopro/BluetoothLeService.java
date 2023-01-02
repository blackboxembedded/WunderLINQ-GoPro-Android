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

import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;


import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a WunderLINQ Bluetooth LE device.
 */
public class BluetoothLeService extends Service {

    List<UUID> notifyingCharacteristics = new ArrayList<>();


    private final static String TAG = "BLE";

    int mStartMode;       // indicates how to behave if the service is killed
    boolean mAllowRebind; // indicates whether onRebind should be used

    private static final Queue<Runnable> commandQueue = new ConcurrentLinkedQueue<>();
    private static Handler bleHandler = new Handler();
    private static volatile boolean commandQueueBusy = false;
    private static boolean isRetrying;
    private static int nrTries;
    // Maximum number of retries of commands
    private static final int MAX_TRIES = 2;

    public enum WriteType {
        WITH_RESPONSE,
        WITHOUT_RESPONSE,
        SIGNED
    }

    private static BluetoothGattCharacteristic commandCharacteristic;
    private static BluetoothGattCharacteristic commandResponseCharacteristic;
    private static BluetoothGattCharacteristic queryCharacteristic;
    private static BluetoothGattCharacteristic queryResponseCharacteristic;

    /**
     * GATT Status constants
     */
    public final static String ACTION_GATT_CONNECTED =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_CONNECTING =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_CONNECTING";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_GATT_CHARS_DISCOVERED =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_CHARS_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.blackboxembedded.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_GATT_CHARACTERISTIC_ERROR =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_CHARACTERISTIC_ERROR";
    public final static String ACTION_GATT_SERVICE_DISCOVERY_UNSUCCESSFUL =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_SERVICE_DISCOVERY_UNSUCCESSFUL";
    public final static String ACTION_WRITE_FAILED =
            "android.bluetooth.device.action.ACTION_WRITE_FAILED";
    public final static String ACTION_WRITE_SUCCESS =
            "android.bluetooth.device.action.ACTION_WRITE_SUCCESS";
    private final static String ACTION_GATT_DISCONNECTING =
            "com.blackboxembedded.bluetooth.le.ACTION_GATT_DISCONNECTING";
    public final static String ACTION_NOTFICATION_ENABLED =
            "com.blackboxembedded.bluetooth.le.ACTION_NOTFICATION_ENABLED";
    public static final String EXTRA_BYTE_VALUE = "com.blackboxembedded.wunderlinq.backgroundservices." +
            "EXTRA_BYTE_VALUE";
    public static final String EXTRA_BYTE_UUID_VALUE = "com.blackboxembedded.wunderlinq.backgroundservices." +
            "EXTRA_BYTE_UUID_VALUE";

    /**
     * Connection status Constants
     */
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_DISCONNECTING = 4;
    private static final int STATE_BONDED = 5;

    /**
     * BluetoothAdapter for handling connections
     */
    public static BluetoothAdapter mBluetoothAdapter;
    public static BluetoothGatt mBluetoothGatt;

    public static int mConnectionState = STATE_DISCONNECTED;
    /**
     * Device address
     */
    private static String mBluetoothDeviceAddress;
    private static String mBluetoothDeviceName;

    /**
     * Flag to check the mBound status
     */
    public boolean mBound;

    /**
     * BlueTooth manager for handling connections
     */
    private BluetoothManager mBluetoothManager;

    private final IBinder mBinder = new LocalBinder();

    /**
     * Local binder class
     */
    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    public BluetoothLeService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        // The service is being created
        // Initializing the service
        if (!initialize()) {
            Log.d(TAG, "Service not initialized");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The service is starting, due to a call to startService()
        Log.d(TAG, "onStartCommand()");
        return mStartMode;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        // A client is binding to the service with bindService()
        mBound = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        // All clients have unbound with unbindService()
        mBound = false;
        return mAllowRebind;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind()");
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }

    @Override
    public void onDestroy() {
        // The service is no longer used and is being destroyed
    }

    /**
     * Initializes a reference to the local BlueTooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        return mBluetoothAdapter != null;
    }

    /**
     * Implements callback methods for GATT events that the app cares about. For
     * example,connection change and services discovered.
     */
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            String intentAction;
            // GATT Server connected
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                synchronized (mGattCallback) {
                    mConnectionState = STATE_CONNECTED;
                }
                broadcastConnectionUpdate(intentAction);

                gatt.requestMtu(512);

                String dataLog = "GATT Connected: [" + mBluetoothDeviceName + "|" + mBluetoothDeviceAddress + "] " +
                        "Connection established";
                Log.d(TAG,dataLog);
            }
            // GATT Server disconnected
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                synchronized (mGattCallback) {
                    mConnectionState = STATE_DISCONNECTED;
                }
                broadcastConnectionUpdate(intentAction);
                String dataLog = "[" + mBluetoothDeviceName + "|" + mBluetoothDeviceAddress + "] " +
                        "Disconnected";
                Log.d(TAG,dataLog);
            }
            // GATT Server Connecting
            if (newState == BluetoothProfile.STATE_CONNECTING) {
                intentAction = ACTION_GATT_CONNECTING;
                synchronized (mGattCallback) {
                    mConnectionState = STATE_CONNECTING;
                }
                broadcastConnectionUpdate(intentAction);
                String dataLog = "[" + mBluetoothDeviceName + "|" + mBluetoothDeviceAddress + "] " +
                        "Connection establishing";
                Log.d(TAG,dataLog);
            }
            // GATT Server disconnected
            else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                intentAction = ACTION_GATT_DISCONNECTING;
                synchronized (mGattCallback) {
                    mConnectionState = STATE_DISCONNECTING;
                }
                broadcastConnectionUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            // GATT Services discovered
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG,"GATT: Services Discovered");
                broadcastConnectionUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                checkGattServices(getSupportedGattServices());
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION ||
                    status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                bondDevice();
                broadcastConnectionUpdate(ACTION_GATT_SERVICE_DISCOVERY_UNSUCCESSFUL);
            } else {
                broadcastConnectionUpdate(ACTION_GATT_SERVICE_DISCOVERY_UNSUCCESSFUL);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                     int status) {
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            // Do some checks first
            final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
            if(status!= BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, String.format("ERROR: Write descriptor failed characteristic: %s", parentCharacteristic.getUuid()));
            }

            // Check if this was the Client Configuration Descriptor
            if(descriptor.getUuid().equals(UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG))) {
                if(status==BluetoothGatt.GATT_SUCCESS) {
                    // Check if we were turning notify on or off
                    byte[] value = descriptor.getValue();
                    if (value != null) {
                        if (value[0] != 0) {
                            // Notify set to on, add it to the set of notifying characteristics
                            notifyingCharacteristics.add(parentCharacteristic.getUuid());
                        }
                    } else {
                        // Notify was turned off, so remove it from the set of notifying characteristics
                        notifyingCharacteristics.remove(parentCharacteristic.getUuid());
                    }
                }
                // This was a setNotify operation
            } else {
            // This was a normal descriptor write....
            }
            broadcastConnectionUpdate(ACTION_NOTFICATION_ENABLED);
            completedCommand();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic
                characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Intent intent = new Intent(ACTION_WRITE_SUCCESS);
                Bundle mBundle = new Bundle();
                // Putting the byte value read for GATT Db
                final byte[] data = characteristic.getValue();
                mBundle.putByteArray(EXTRA_BYTE_VALUE,
                        data);
                mBundle.putString(EXTRA_BYTE_UUID_VALUE,
                        characteristic.getUuid().toString());
                mBundle.putString("ACTION_WRITE_SUCCESS",
                        "" + status);
                intent.putExtras(mBundle);

                sendBroadcast(intent);
                completedCommand();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            // Perform some checks on the status field
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, String.format(Locale.ENGLISH,"ERROR: Read failed for characteristic: %s, status %d", characteristic.getUuid(), status));
                completedCommand();
                return;
            }

            broadcastNotifyUpdate(characteristic);
            completedCommand();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastNotifyUpdate(characteristic);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG,"New MTU: " + mtu);
            }
            discoverServices();
        }
    };


    private void broadcastConnectionUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastNotifyUpdate(final BluetoothGattCharacteristic characteristic) {
        Bundle mBundle = new Bundle();
        // Putting the byte value read for GATT Db
        final byte[] data = characteristic.getValue();

        mBundle.putByteArray(EXTRA_BYTE_VALUE,
                data);
        mBundle.putString(EXTRA_BYTE_UUID_VALUE,
                characteristic.getUuid().toString());

        if (characteristic.getUuid().equals(UUIDDatabase.UUID_GOPRO_COMMANDRESPONSE_CHARACTERISTIC)) {
            final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
            intent.putExtras(mBundle);
            sendBroadcast(intent);
        } else if (characteristic.getUuid().equals(UUIDDatabase.UUID_GOPRO_QUERYRESPONSE_CHARACTERISTIC)) {
            final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
            intent.putExtras(mBundle);
            sendBroadcast(intent);
        } else {
            /*
             * Sending the broad cast so that it can be received on registered
             * receivers
             */
            final Intent intent = new Intent(ACTION_DATA_AVAILABLE);
            intent.putExtras(mBundle);
            sendBroadcast(intent);
        }
    }

    /**
     * Connects to the GATT server hosted on the BlueTooth LE device.
     *
     * @param address The device address of the destination device.
     * connection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void connect(final String address, final String devicename) {
        if (mBluetoothAdapter == null || address == null) {
            return;
        }

        BluetoothDevice device = mBluetoothAdapter
                .getRemoteDevice(address);
        if (device == null) {
            return;
        }

        // We want to directly connect to the device, so we are setting the
        // autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothDeviceAddress = address;
        mBluetoothDeviceName = devicename;

        String dataLog = "[" + devicename + "|" + address + "] " +
                "Connection request sent";
        Log.d(TAG, dataLog);
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The
     * disconnection result is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public static void disconnect() {
        if (mBluetoothAdapter != null || mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            String dataLog = "[" + mBluetoothDeviceName + "|" + mBluetoothDeviceAddress + "] " +
                    "Disconnection request sent";
            Log.d(TAG, dataLog);
            close();
        }
    }

    public static void discoverServices() {
        if (mBluetoothAdapter != null || mBluetoothGatt != null) {
            mBluetoothGatt.discoverServices();
            String dataLog = "[" + mBluetoothDeviceName + "|" + mBluetoothDeviceAddress + "] " +
                    "Service discovery request sent";
            Log.d(TAG, dataLog);
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure
     * resources are released properly.
     */
    public static void close() {
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read
     * result is reported asynchronously through the
     * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public static boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if(mBluetoothGatt == null) {
            Log.e(TAG, "ERROR: Gatt is 'null', ignoring read request");
            return false;
        }

        // Check if characteristic is valid
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring read request");
            return false;
        }

        // Check if this characteristic actually has READ property
        if((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0 ) {
            Log.e(TAG, "ERROR: Characteristic cannot be read");
            return false;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (!mBluetoothGatt.readCharacteristic(characteristic)) {
                    Log.e(TAG, String.format("ERROR: readCharacteristic failed for characteristic: %s", characteristic.getUuid()));
                    completedCommand();
                } else {
                    Log.d(TAG, String.format("Reading characteristic <%s>", characteristic.getUuid()));
                    nrTries++;
                }
            }
        });

        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue read characteristic command");
        }
        return result;
    }

    public static boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic, final byte[] value, final WriteType writeType) {

        if (!isConnected()) {
            Log.d(TAG, "Hardware Not Connected");
            return false;
        }

        // Copy the value to avoid race conditions
        final byte[] bytesToWrite = copyOf(value);

        // Check if this characteristic actually supports this writeType
        int writeProperty;
        final int writeTypeInternal;
        switch (writeType) {
            case WITH_RESPONSE:
                writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE;
                writeTypeInternal = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
                break;
            case WITHOUT_RESPONSE:
                writeProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
                writeTypeInternal = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
                break;
            case SIGNED:
                writeProperty = BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE;
                writeTypeInternal = BluetoothGattCharacteristic.WRITE_TYPE_SIGNED;
                break;
            default:
                writeProperty = 0;
                writeTypeInternal = 0;
                break;
        }
        if ((characteristic.getProperties() & writeProperty) == 0) {
            Log.d(TAG, "Characteristic does not support writeType");
            return false;
        }

        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                if (isConnected()) {
                    characteristic.setWriteType(writeTypeInternal);
                    characteristic.setValue(bytesToWrite);
                    if (!mBluetoothGatt.writeCharacteristic(characteristic)) {
                        Log.d(TAG, String.format("writeCharacteristic failed for characteristic: %s", characteristic.getUuid()));
                        completedCommand();
                    } else {
                        Log.d(TAG, String.format("Writing <%s> to characteristic <%s>", Utils.ByteArraytoHex(bytesToWrite), characteristic.getUuid()));
                        nrTries++;
                    }
                } else {
                    completedCommand();
                }
            }
        });

        if (result) {
            nextCommand();
        } else {
            Log.d(TAG, "Could not enqueue write characteristic command");
        }
        return result;
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This
     * should be invoked only after {@code BluetoothGatt#discoverServices()}
     * completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public static List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null)
            return null;

        return mBluetoothGatt.getServices();
    }

    public static void bondDevice() {
        try {
            Class class1 = Class.forName("android.bluetooth.BluetoothDevice");
            Method createBondMethod = class1.getMethod("createBond");
            Boolean returnValue = (Boolean) createBondMethod.invoke(mBluetoothGatt.getDevice());
            Log.d(TAG,"Pair initates status-->" + returnValue);
        } catch (Exception e) {
            Log.d(TAG,"Exception Pair" + e.getMessage());
        }
    }

    public boolean setNotify(BluetoothGattCharacteristic characteristic, final boolean enable) {
        // Check if characteristic is valid
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring setNotify request");
            return false;
        }

        // Get the CCC Descriptor for the characteristic
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(GattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        if(descriptor == null) {
            Log.e(TAG, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characteristic.getUuid()));
            return false;
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        byte[] value;
        int properties = characteristic.getProperties();
        if ((properties & PROPERTY_NOTIFY) > 0) {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((properties & PROPERTY_INDICATE) > 0) {
            value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            Log.e(TAG, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.getUuid()));
            return false;
        }
        final byte[] finalValue = enable ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        // Queue Runnable to turn on/off the notification now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // First set notification for Gatt object
                if(!mBluetoothGatt.setCharacteristicNotification(descriptor.getCharacteristic(), enable)) {
                    Log.e(TAG, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", descriptor.getUuid()));
                }

                // Then write to descriptor
                descriptor.setValue(finalValue);
                boolean result;
                result = mBluetoothGatt.writeDescriptor(descriptor);
                if(!result) {
                    Log.e(TAG, String.format("ERROR: writeDescriptor failed for descriptor: %s", descriptor.getUuid()));
                    completedCommand();
                } else {
                    nrTries++;
                }
            }
        });

        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue write command");
        }

        return result;
    }

    public boolean isNotifying(BluetoothGattCharacteristic characteristic) {
        return notifyingCharacteristics.contains(characteristic.getUuid());
    }

    private void checkGattServices(List<BluetoothGattService> gattServices) {
        List<BluetoothGattCharacteristic> gattCharacteristics;
        if (gattServices == null) return;
        String uuid;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            if (UUIDDatabase.UUID_GOPRO_CONTROL_SERVICE.equals(gattService.getUuid())){
                uuid = gattService.getUuid().toString();
                Log.d(TAG,"GoPro Control Service Found: " + uuid);
                gattCharacteristics = gattService.getCharacteristics();
                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    uuid = gattCharacteristic.getUuid().toString();
                    Log.d(TAG,"Characteristic Found: " + uuid);
                    if (UUID.fromString(GattAttributes.GOPRO_COMMAND_CHARACTERISTIC).equals(gattCharacteristic.getUuid())) {
                        Log.d(TAG,"GoPro Command Characteristic Found: " + uuid);
                        commandCharacteristic = gattCharacteristic;
                    }
                    if (UUID.fromString(GattAttributes.GOPRO_COMMANDRESPONSE_CHARACTERISTIC).equals(gattCharacteristic.getUuid())) {
                        Log.d(TAG,"GoPro Command/Response Characteristic Found: " + uuid);
                        commandResponseCharacteristic = gattCharacteristic;
                        setNotify(commandResponseCharacteristic,true);
                    }
                    if (UUID.fromString(GattAttributes.GOPRO_QUERY_CHARACTERISTIC).equals(gattCharacteristic.getUuid())){
                        Log.d(TAG,"GoPro Query Characteristic Found: " + uuid);
                        queryCharacteristic = gattCharacteristic;
                    }
                    if (UUID.fromString(GattAttributes.GOPRO_QUERYRESPONSE_CHARACTERISTIC).equals(gattCharacteristic.getUuid())){
                        Log.d(TAG,"GoPro Query/Response Characteristic Found: " + uuid);
                        queryResponseCharacteristic = gattCharacteristic;
                        setNotify(queryResponseCharacteristic,true);
                    }
                }
            }
        }
        broadcastConnectionUpdate(ACTION_GATT_CHARS_DISCOVERED);
    }

    public static boolean isConnected() {
        return mBluetoothGatt != null && mConnectionState == BluetoothProfile.STATE_CONNECTED;
    }

    private static byte[] copyOf(byte[] source) {
        return (source == null) ? new byte[0] : Arrays.copyOf(source, source.length);
    }

    private static void nextCommand() {
        Log.d(TAG, "nextCommand() ");
        // If there is still a command being executed then bail out
        if(commandQueueBusy) {
            return;
        }

        // Check if we still have a valid gatt object
        if (mBluetoothGatt == null) {
            Log.d(TAG, "ERROR: GATT is 'null' for peripheral, clearing command queue");
            commandQueue.clear();
            commandQueueBusy = false;
            return;
        }

        // Execute the next command in the queue
        if (commandQueue.size() > 0) {
            final Runnable bluetoothCommand = commandQueue.peek();
            commandQueueBusy = true;
            nrTries = 0;

            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        bluetoothCommand.run();
                    } catch (Exception ex) {
                        Log.d(TAG, "ERROR: Command exception for device");
                    }
                }
            });
        }
    }

    private static void completedCommand() {
        commandQueueBusy = false;
        isRetrying = false;
        commandQueue.poll();
        nextCommand();
    }

    private static void retryCommand() {
        commandQueueBusy = false;
        Runnable currentCommand = commandQueue.peek();
        if(currentCommand != null) {
            if (nrTries >= MAX_TRIES) {
                // Max retries reached, give up on this one and proceed
                Log.v(TAG, "Max number of tries reached");
                commandQueue.poll();
            } else {
                isRetrying = true;
            }
        }
        nextCommand();
    }

    // GoPro Commands
    public void setCommand(byte[] command){
        if (commandCharacteristic != null) {
            if (!isNotifying(commandResponseCharacteristic)) {
                setNotify(commandResponseCharacteristic,true);
            } else {
                byte[] fullcommand = new byte[command.length + 1];
                System.arraycopy(new byte[]{(byte) command.length}, 0, fullcommand, 0, 1);
                System.arraycopy(command, 0, fullcommand, 1, command.length);
                Log.d(TAG, Utils.ByteArraytoHex(fullcommand));
                writeCharacteristic(commandCharacteristic, fullcommand, WriteType.WITH_RESPONSE);
            }
        }
    }

    public void requestCameraStatus(){
        if (queryCharacteristic != null) {
            if (!isNotifying(queryResponseCharacteristic)) {
                setNotify(queryResponseCharacteristic,true);
            } else {
                byte[] command = {0x05, 0x13, 0x08, 0x11, 0x37, 0x60};
                writeCharacteristic(queryCharacteristic, command, WriteType.WITH_RESPONSE);
            }
        }
    }
}