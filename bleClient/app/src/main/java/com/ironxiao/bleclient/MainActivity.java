package com.ironxiao.bleclient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements ScanProcess {
    private static final String TAG = "XL_BLE_CLIENT";

    private static final int REQUEST_ENABLE_BT = 0x0;
    private static final int REQUEST_PERMISSIONS_ACCESS_LOCATION = 0x1;

    private static final int MANUFACTURE_ID = 0xAAA;
    private static final String MANUFACTURE = "Nologic";

    private static final UUID UUID_SERVER = UUID.fromString("00001ff9-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_WRITE = UUID.fromString("00001ffa-0000-1000-8000-00805f9b34fb");
    private static final UUID UUID_WRITE_CONTENT = UUID.fromString("00001ffb-0000-1000-8000-00805f9b34fb");

    private BluetoothAdapter bluetoothAdapter;

    private BluetoothGatt targetBluetoothGatt = null;

    private ScanResult targetScanResult;

    private BluetoothLeScanner bluetoothLeScanner;

    private BluetoothManager bluetoothManager;

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            log("onScanResult: " + callbackType + ", result: " + result + ", device: " + result.getDevice().getName());
            MainActivity.this.onFound(result);
        }

    };

    private static final ArrayList<ScanFilter> scanFilters = new ArrayList<>();

    static {
        scanFilters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID_SERVER)).setManufacturerData(MANUFACTURE_ID, MANUFACTURE.getBytes()).build());
    }

    private static final ScanSettings scanSettings = new ScanSettings.Builder().setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();

    private final BluetoothGattCallback bluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            log("onConnectionStateChange:" + gatt.getDevice() + ", status: " + status + ", newState: " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                MainActivity.this.onConnect(gatt);
            }
            if (status != BluetoothGatt.GATT_SUCCESS) {
                (new Throwable(TAG)).printStackTrace();
                MainActivity.this.onDisConnect(gatt);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            log("onServicesDiscovered:" + gatt.getDevice() + ", status: " + status);
            //  if (status == BluetoothGatt.GATT_SUCCESS) {
            BluetoothGattService service = gatt.getService(UUID_SERVER);
            if (service != null) {
                MainActivity.this.onServiceFound(gatt, service);
            }
            // }
        }


        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (BluetoothGatt.GATT_SUCCESS == status) {
                log("send msg success!");
            } else {
                log("send msg fail!");
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!hasBleFeature()) {
            toast("This device does not support ble!!!");
            return;
        }
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        bluetoothManager = (BluetoothManager) this.getSystemService(BLUETOOTH_SERVICE);
    }

    public void startBleSan(View view) {
        if (!hasBleFeature()) {
            toast("This device does not support ble!!!");
            return;
        }
        if (!btEnabled()) {
            toast("BT not opened, please enable BT first !");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        if (((Button) view).getText().equals(getString(R.string.start_ble_scan))) {
            scanLeDeviceCommon(true);
        } else {
            scanLeDeviceCommon(false);
        }
    }

    private void scanLeDeviceCommon(final boolean enable) {
        if (enable) {
            if (!accessLocationAllowed()) {
                toast("Pls grant location permission first, otherwise cannot search device!");
                grantAccessLocationPermission();
                return;
            }
            log("Start LeScan !");
            ((Button) findViewById(R.id.scan)).setText(R.string.stop_ble_scan);
            bluetoothLeScanner.startScan(scanFilters, scanSettings, leScanCallback);
        } else {
            ((Button) findViewById(R.id.scan)).setText(R.string.start_ble_scan);
            bluetoothLeScanner.stopScan(leScanCallback);
            log("Stop LeScan !");
        }
    }

    private boolean btEnabled() {
        return bluetoothAdapter == null || bluetoothAdapter.isEnabled();
    }

    private boolean hasBleFeature() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    private void toast(String showStr) {
        Toast.makeText(MainActivity.this, showStr, Toast.LENGTH_SHORT).show();
    }

    private static void log(String log) {
        Log.d(TAG, log);
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

    private boolean accessLocationAllowed() {
        if (!osVersionLaterThanP()) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;

        }
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

    }

    private boolean osVersionLaterThanP() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.P;
    }

    private void grantAccessLocationPermission() {
        if (accessLocationAllowed()) return;
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)) {
            //TODO further action
        } else {
            if (!osVersionLaterThanP())
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_PERMISSIONS_ACCESS_LOCATION);
            else
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSIONS_ACCESS_LOCATION);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_ACCESS_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast("location permission granted!");
            } else {
                toast("Can not scan device without permission!");
            }
        }
    }

    private void connectBleDevice(ScanResult scanResultToConnect) {
        if (scanResultToConnect == null)
            return;
        if (targetBluetoothGatt != null) {
            if (targetBluetoothGatt.connect()) {
                //Already connected this device
                if (targetBluetoothGatt.getDevice().getAddress().equals(scanResultToConnect.getDevice().getAddress()))
                    return;
                else {
                    //connected to other device
                    targetBluetoothGatt.disconnect();
                }
            }
        }
        log("real connect");
        targetBluetoothGatt = scanResultToConnect.getDevice().connectGatt(getApplicationContext(), false, bluetoothGattCallback, BluetoothDevice.TRANSPORT_LE);
    }


    public void sendMsg(View view) {
        if (targetBluetoothGatt == null) {
            log("write test msg 0");
            return;
        }

        BluetoothGattService gattService = targetBluetoothGatt.getService(UUID_SERVER);
        if (gattService == null) {
            log("write test msg 1");
            return;
        }

        BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(UUID_WRITE);
        if (characteristic == null) {
            log("write test msg 2");
            return;
        }
        characteristic.setValue("WIFI:S:goke_ofice;T:WPA;P:goke!wifi;H:false;;");
        targetBluetoothGatt.writeCharacteristic(characteristic);
    }

    @Override
    public void onFound(final ScanResult result) {
        log("onFound:" + result.getDevice());
        canMsg = false;
        if (targetScanResult == null)
            targetScanResult = result;
        scanLeDeviceCommon(false);
        connectBleDevice(targetScanResult);


    }

    @Override
    public void onConnect(BluetoothGatt gatt) {
        log("onConnect: " + gatt.getDevice());
        //       gatt.requestMtu(247);
        targetBluetoothGatt = gatt;
        gatt.requestMtu(512);
        gatt.discoverServices();
    }

    @Override
    public void onDisConnect(BluetoothGatt gatt) {
        log("onDisconnect");
        canMsg = false;
        targetBluetoothGatt.disconnect();
        targetBluetoothGatt = null;
        targetScanResult = null;
    }

    private boolean canMsg;

    @Override
    public void onServiceFound(BluetoothGatt gatt, BluetoothGattService service) {
        BluetoothGattCharacteristic bluetoothGattCharacteristic = service.getCharacteristic(UUID_WRITE);
        if (bluetoothGattCharacteristic != null) {
            gatt.setCharacteristicNotification(bluetoothGattCharacteristic, true);
//            gatt.readCharacteristic(bluetoothGattCharacteristic);
//
//            for (BluetoothGattDescriptor bluetoothGattDescriptor : bluetoothGattCharacteristic.getDescriptors()) {
//                bluetoothGattDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//                gatt.writeDescriptor(bluetoothGattDescriptor);
//                canMsg = true;
//            }
        }
        log("onService found");
    }
}