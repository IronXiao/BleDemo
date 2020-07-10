package com.ironxiao.bleclient;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.ScanResult;

public interface ScanProcess {
    public void onFound(ScanResult result);

    public void onConnect(BluetoothGatt gatt);

    public void onDisConnect(BluetoothGatt gatt);

    public void onServiceFound(BluetoothGatt gatt);
}
