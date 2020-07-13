package com.ironxiao.bleserver;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.UUID;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "XL_BLE_SERVER";

    //We are Nologic Inc.
    private static final int MANUFACTURE_ID = 0xAAA;
    private static final String MANUFACTURE = "Nologic";


    private static final int REQUEST_ENABLE_BT = 0x0;
    private static final UUID UUID_SERVER = UUID.fromString("00001ff9-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_WRITE = UUID.fromString("00001ffa-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_WRITE_CONTENT = UUID.fromString("00001ffb-0000-1000-8000-00805f9b34fb");


    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;

    private BluetoothGattServer bluetoothGattServer;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private static final BluetoothGattService bluetoothGattService = new BluetoothGattService(UUID_SERVER, SERVICE_TYPE_PRIMARY);

    private static final BluetoothGattCharacteristic bluetoothGattCharacteristic = new BluetoothGattCharacteristic(UUID_WRITE,
            PROPERTY_WRITE | PROPERTY_READ | PROPERTY_WRITE_NO_RESPONSE,
            PERMISSION_READ | PERMISSION_WRITE);


    static {
        bluetoothGattCharacteristic.addDescriptor(new BluetoothGattDescriptor(UUID_WRITE_CONTENT, PERMISSION_READ | PERMISSION_WRITE));
        bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic);
    }


    private final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            log("onConnectionStateChange:" + device.getName() + ", status: " + status + ", newState: " + newState);
            toast("onConnectionStateChange" + device.getName() + ", newState:" + newState);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            log("onCharacteristicReadRequest: " + device.getName() + ", requestId: " + requestId + ", offset: " + offset + ", characteristic: " + characteristic);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            toast("onCharacteristicWriteRequest: " + new String(value));
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            toast("onDescriptorWriteRequest: " + new String(value));
        }
    };

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            toast("Start ble ad success !");
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            toast("Start ble ad fail !");
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
    }

    public void startBleAd(View view) {
        //Check has feature ble
        if (!hasBleFeature()) {
            toast("BLE not supported !");
            return;
        }
        //Check bluetooth enabled
        if (!btEnabled()) {
            toast("BT not opened,please enable BT first !");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }

        if (getResources().getString(R.string.start_server).contentEquals(((Button) view).getText())) {
            startBleAd();
            ((Button) view).setText(R.string.stop_server);
        } else {
            stopBleAd(false);
            ((Button) view).setText(R.string.start_server);
        }


    }

    private AdvertiseSettings getAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
        settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        settingsBuilder.setConnectable(true);
        settingsBuilder.setTimeout(0);
        settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        return settingsBuilder.build();
    }

    private AdvertiseData getAdvertiseData() {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeDeviceName(true);
        dataBuilder.setIncludeTxPowerLevel(true);
        dataBuilder.addManufacturerData(MANUFACTURE_ID, MANUFACTURE.getBytes());
        dataBuilder.addServiceUuid(new ParcelUuid(UUID_SERVER));
        return dataBuilder.build();
    }


    private boolean btEnabled() {
        return bluetoothAdapter == null || bluetoothAdapter.isEnabled();
    }

    private boolean hasBleFeature() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopBleAd(true);
    }

    private void stopBleAd(boolean destroy) {
        if (bluetoothGattServer != null) {
            bluetoothGattServer.clearServices();
            bluetoothGattServer.close();
            if (destroy)
                bluetoothGattServer = null;
        }

        if (bluetoothLeAdvertiser != null) {
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
            if (destroy)
                bluetoothLeAdvertiser = null;
        }
    }

    private void startBleAd() {
        //Start GattServer for other's command
        if (bluetoothGattServer == null)
            bluetoothGattServer = bluetoothManager.openGattServer(this, bluetoothGattServerCallback);
        bluetoothGattServer.addService(bluetoothGattService);

        //Start BLE Advertise for other's search
        if (bluetoothLeAdvertiser == null)
            bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothLeAdvertiser.startAdvertising(getAdvertiseSettings(), getAdvertiseData(), advertiseCallback);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (btEnabled())
                toast("BT enabled success !");
            else
                toast("BT enabled fail !");
        }
    }


    private void toast(final String showStr) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, showStr, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void log(String log) {
        Log.d(TAG, log);
    }

}