package com.shawn.bledemo;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.shawn.bledemo.adapter.DevicesAdapter;
import com.shawn.bledemo.bleutil.OnSearchDevicesListener;
import com.shawn.bledemo.entity.BleDevice;

public class MainActivity extends BaseActivity implements DevicesAdapter.OnDeviceClickListener, OnSearchDevicesListener {


    private RecyclerView mRvDevice;

    private DevicesAdapter mAdapter;
    private TextView mTvSearch;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void initView() {
        mRvDevice = findViewById(R.id.rv_device);
        mRvDevice.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mTvSearch = findViewById(R.id.tv_search);
    }

    @Override
    protected void initData() {
        mAdapter = new DevicesAdapter(this);
        mRvDevice.setAdapter(mAdapter);

        checkBluetooth();
    }

    private void checkBluetooth() {
        // 用以下方式来判断设备是否支持BLE，从而选择性的禁用BLE相关特性
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //设备不支持蓝牙
            new AlertDialog.Builder(this)
                    .setMessage("该设备不支持蓝牙")
                    .setCancelable(false)
                    .show();
        }

        if (!mBleManager.isOpenBt()) {
            new AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("打开蓝牙")
                    .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mBleManager.openBt();
                            dialog.dismiss();
                        }
                    })
                    .show();
        }
    }

    @Override
    protected void initListener() {
        mAdapter.setOnDeviceClickListener(this);
        mBleManager.setOnSearchDeviceListener(this);

        mTvSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
                    } else {
                        mBleManager.scanLeDevice(true);
                    }
                } else {
                    mBleManager.scanLeDevice(true);
                }
            }
        });

    }

    @Override
    protected void connected() {
        Intent intent = new Intent(this, DataAlternatelyActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDeviceClick(BleDevice device) {
        mBleManager.scanLeDevice(false);
        mBleManager.connectGatt(device.getAddress());
    }

    @Override
    public void onNewDeviceFound(BluetoothDevice device, int rssi) {
        BleDevice bleDevice = new BleDevice();
        bleDevice.setName(device.getName());
        bleDevice.setAddress(device.getAddress());
        bleDevice.setRssi(rssi);
        mAdapter.addDevice(bleDevice);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mBleManager.scanLeDevice(true);
            } else {
                Toast.makeText(this, "无权限无法搜索蓝牙设备", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
