package com.ironxiao.bleclient2;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;

import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanSettings;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.polidea.rxandroidble2.RxBleClient;


import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "XL_BLE_CLIENT";

    private static final int REQUEST_ENABLE_BT = 0x0;
    private static final int REQUEST_PERMISSIONS_ACCESS_LOCATION = 0x1;


    private static final int MANUFACTURE_ID = 0xAAA;
    private static final String MANUFACTURE = "Nologic";

    private static final UUID UUID_SETUP = UUID.fromString( "00001ff9-0000-1000-8000-00805f9b34fb" );
    private static final UUID UUID_REQUEST = UUID.fromString( "00001ffa-0000-1000-8000-00805f9b34fb" );


    private static final ScanSettings scanSettings = new ScanSettings.Builder().setScanMode( ScanSettings.SCAN_MODE_LOW_POWER ).build();
    private static final ScanFilter scanFilter = new ScanFilter.Builder().setServiceUuid( new ParcelUuid( UUID_SETUP ) ).setManufacturerData( MANUFACTURE_ID, MANUFACTURE.getBytes() ).build();


    private RxBleClient rxBleClient;

    private Disposable flowDisposable;

    private Disposable scanSubscription;

    private Disposable connectDisposable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate( savedInstanceState );
        setContentView( R.layout.activity_main );
        rxBleClient = RxBleClient.create( this );

        flowDisposable = rxBleClient.observeStateChanges()
                .switchMap( state -> { // switchMap makes sure that if the state will change the rxBleClient.scanBleDevices() will dispose and thus end the scan
                    switch (state) {

                        case READY:
                            // everything should work
                            //return rxBleClient.scanBleDevices();
                        case BLUETOOTH_NOT_AVAILABLE:
                            // basically no functionality will work here
                        case LOCATION_PERMISSION_NOT_GRANTED:
                            // scanning and connecting will not work
                        case BLUETOOTH_NOT_ENABLED:
                            // scanning and connecting will not work
                        case LOCATION_SERVICES_NOT_ENABLED:
                            // scanning will not work
                        default:
                            return Observable.empty();
                    }
                } )
                .subscribe(
                        rxBleScanResult -> {
                            // Process scan result here.
                        },
                        throwable -> {
                            // Handle an error here.
                        }
                );

    }

    public void onButtonClick(View view) {
        if (!hasBleFeature()) {
            toast( "BLE not supported !" );
            return;
        }
        //Check bluetooth enabled
        if (!btEnabled()) {
            toast( "BT not opened,please enable BT first !" );
            Intent enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
            startActivityForResult( enableBtIntent, REQUEST_ENABLE_BT );
            return;
        }

        if (!accessLocationAllowed()) {
            grantAccessLocationPermission();
            return;
        }

        scanSubscription = rxBleClient.scanBleDevices( scanSettings,
                scanFilter )
                .subscribe(
                        scanResult -> {
                            log( "OnSuccess: " + scanResult.toString() );
                            scanSubscription.dispose();
                            connectDisposable = scanResult.getBleDevice().establishConnection( false )
                                    .subscribe(
                                            rxBleConnection -> {
                                                rxBleConnection.writeCharacteristic( UUID_REQUEST, "test".getBytes() );
                                            },
                                            throwable -> {
                                                log( "senMsg error: " + throwable );
                                            }
                                    );
                        },
                        throwable -> {
                            log( "error" + throwable );
                        }

                );

// When done, just dispose.


    }

    private boolean hasBleFeature() {
        return getPackageManager().hasSystemFeature( PackageManager.FEATURE_BLUETOOTH_LE );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanSubscription != null)
            scanSubscription.dispose();
        if (connectDisposable != null)
            connectDisposable.dispose();
        flowDisposable.dispose();
    }

    private void log(String log) {
        Log.d( TAG, log );
    }

    private void toast(final String showStr) {
        runOnUiThread( new Runnable() {
            @Override
            public void run() {
                Toast.makeText( MainActivity.this, showStr, Toast.LENGTH_SHORT ).show();
            }
        } );
    }

    private boolean btEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter == null || bluetoothAdapter.isEnabled();
    }

    private boolean osVersionLaterThanP() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.P;
    }

    private void grantAccessLocationPermission() {
        if (accessLocationAllowed()) return;
        if (ActivityCompat.shouldShowRequestPermissionRationale( this,
                Manifest.permission.ACCESS_COARSE_LOCATION )) {
            //TODO further action
        } else {
            if (!osVersionLaterThanP())
                ActivityCompat.requestPermissions( this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_PERMISSIONS_ACCESS_LOCATION );
            else
                ActivityCompat.requestPermissions( this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_PERMISSIONS_ACCESS_LOCATION );
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS_ACCESS_LOCATION) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toast( "location permission granted!" );
            } else {
                toast( "Can not scan device without permission!" );
            }
        }
    }

    private boolean accessLocationAllowed() {
        if (!osVersionLaterThanP()) {
            return ContextCompat.checkSelfPermission( this,
                    Manifest.permission.ACCESS_COARSE_LOCATION )
                    == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission( this,
                    Manifest.permission.ACCESS_FINE_LOCATION )
                    == PackageManager.PERMISSION_GRANTED;

        }
        return ContextCompat.checkSelfPermission( this,
                Manifest.permission.ACCESS_FINE_LOCATION )
                == PackageManager.PERMISSION_GRANTED;

    }


}