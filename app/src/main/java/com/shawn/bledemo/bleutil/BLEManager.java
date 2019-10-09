package com.shawn.bledemo.bleutil;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 作者：created by @author{ YSH } on 2018/12/26
 * 描述：BLE介绍 https://www.race604.com/android-ble-in-action/
 * 修改备注：
 */
public class BLEManager implements BluetoothAdapter.LeScanCallback {

    private static final int HANDLER_SEND_CMD = 1000;
    private static final int HANDLER_TIME_OUT = 1001;
    private static final int HANDLER_HEART = 1002;

    private static final int TIMEOUT_MILLISECOND = 30000;
    private static final int HEART_INTERVAL = 1000 * 60;

    private static volatile BLEManager mBleManager;
    private Context mContext;
    private BluetoothAdapter mBtAdapter;
    private BluetoothDevice mDevice;
    private BluetoothLeScanner mScanner;
    private BleService mBleService;
    private Intent serviceIntent;

    /**
     * 标记返回的数据解析是否走协议
     */
    private boolean isProtocolMode = true;
    /**
     * 标记是否主动关闭蓝牙连接
     */
    private boolean isActiveClose = false;

    private String mAddress;
    private AtomicBoolean isFirmwareLock = new AtomicBoolean(false);

    private OnSearchDevicesListener onDeviceListener;
    private OnDataReceiveListener onDataReceiveListener;
    private OnBleStateListener onBleStateListener;

    /**
     * 超时任务
     */
    private Runnable TimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            mHandler.sendEmptyMessage(HANDLER_TIME_OUT);
        }
    };

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_TIME_OUT:
                    if (onDataReceiveListener != null) {
                        onDataReceiveListener.onReceiveFail("数据超时无返回");
                    }
                    break;
                default:
                    break;
            }
        }
    };
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BleService.ACTION_GATT_CONNECTED.equals(intent.getAction())) {
                //连接成功
                if (onBleStateListener != null) {
                    onBleStateListener.onConnected();
                }
            } else if (BleService.ACTION_GATT_DISCONNECTED.equals(intent.getAction())) {
                //断开
                if (onBleStateListener != null) {
                    onBleStateListener.onDisconnected();
                }

            } else if (BleService.ACTION_GATT_CONNECTING.equals(intent.getAction())) {
                if (onBleStateListener != null) {
                    onBleStateListener.onConnecting();
                }
            } else if (BleService.ACTION_GATT_SERVICES_DISCOVERED.equals(intent.getAction())) {
                if (onBleStateListener != null) {
                    onBleStateListener.onServiceDiscovered();
                }
                mAddress = mDevice.getAddress();//连接成功后记录地址
            } else if (BleService.ACTION_DATA_AVAILABLE.equals(intent.getAction())) {
                //有数据
                byte[] data = intent.getByteArrayExtra(BleService.EXTRA_DATA);
                if (data == null) {
                    //没有数据
                } else {
                    mHandler.removeCallbacks(TimeoutRunnable);
                    //重新发送超时通知
                    mHandler.postDelayed(TimeoutRunnable, TIMEOUT_MILLISECOND);
                    if (onDataReceiveListener != null) {
                        onDataReceiveListener.onDataReceive(data);
                    }
                }
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback mScanCallback = new ScanCallback() {

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            if (onDeviceListener != null) {
                result.getScanRecord();//这玩意可能是广播，获取到的是一串byte[]数组
                onDeviceListener.onNewDeviceFound(result.getDevice(), result.getRssi());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
        }
    };
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((BleService.LocalBinder) service).getService();
            if (onBleStateListener != null) {
                onBleStateListener.onConnecting();
            }
            mBleService.connectDevice(mDevice);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (onBleStateListener != null) {
                onBleStateListener.onDisconnected();
            }
        }
    };

    private BLEManager(Context context) {
        mContext = context;
        final BluetoothManager bluetoothManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = bluetoothManager.getAdapter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mScanner = mBtAdapter.getBluetoothLeScanner();
        }
        this.registerReceiver();
    }

    public static BLEManager getInstance(Context context) {
        if (mBleManager == null) {
            synchronized (BLEManager.class) {
                if (mBleManager == null) {
                    mBleManager = new BLEManager(context);
                }
            }
        }
        return mBleManager;
    }

    public void setOnSearchDeviceListener(OnSearchDevicesListener deviceListener) {
        this.onDeviceListener = deviceListener;
    }

    public void setOnDataReceiveListener(OnDataReceiveListener listener) {
        this.onDataReceiveListener = listener;
    }

    public void setOnBleStateListener(OnBleStateListener mBleStateListener) {
        this.onBleStateListener = mBleStateListener;
    }

    public boolean isActiveClose() {
        return isActiveClose;
    }

    public void setActiveClose(boolean activeClose) {
        isActiveClose = activeClose;
    }

    /**
     * 固件升级锁，解锁的时候将取消固件升级
     *
     * @param lock
     */
    public void setFirmwareLock(boolean lock) {
        isFirmwareLock.set(lock);
    }

    /**
     * 开启蓝牙
     * test pass
     */
    public boolean openBt() {
        if (!mBtAdapter.isEnabled()) {
            return mBtAdapter.enable();
        }
        return true;
    }

    public boolean isOpenBt() {
        return mBtAdapter.isEnabled();
    }

    public void disConnect() {
        if (mBleService != null) {
            mBleService.disconnect(mDevice);
        }
    }

    public boolean reconnect() {
        if (mBleService != null) {
            mBleService.clear();
            if (!isActiveClose) {
                mBleService.connectDevice(mDevice);
            }
        }
        return false;
    }

    public void clear() {
        if (mBleService != null) {
            mBleService.clear();
        }
    }

    /**
     * 移除消息队列
     */
    public void removeMessage() {
        mHandler.removeCallbacksAndMessages(null);
    }

    public void scanLeDevice(boolean scannable) {
        if (!isOpenBt()) {
            //获取蓝牙未开启
            if (onBleStateListener != null) {
                onBleStateListener.onBleClose();
            }
            return;
        }
        if (scannable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (mScanner == null) {
                    mScanner = mBtAdapter.getBluetoothLeScanner();
                }

                List<ScanFilter> filters = new ArrayList<>();
                ScanFilter filter = new ScanFilter.Builder()
//                        .setServiceUuid(ParcelUuid.fromString("0000FFE0-0000-1000-8000-00805f9b34fb"))
                        .setManufacturerData(18002, new byte[]{0x18, 0x11})
                        .build();
                filters.add(filter);
                ScanSettings scanSettings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                        .build();
                mScanner.startScan(filters, scanSettings, mScanCallback);
            } else {
                mBtAdapter.startLeScan(this);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mScanner.stopScan(mScanCallback);
            } else {
                mBtAdapter.stopLeScan(this);
            }
        }
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (onDeviceListener != null) {
            onDeviceListener.onNewDeviceFound(device, rssi);
        }
    }

    //刷新、重复刷新、重复点击
    public void connectGatt(String mac) {
        scanLeDevice(false);
        mDevice = mBtAdapter.getRemoteDevice(mac);
        isActiveClose = false;
        if (serviceIntent == null) {
            serviceIntent = new Intent(mContext, BleService.class);
            mContext.bindService(serviceIntent, mServiceConnection, Service.BIND_AUTO_CREATE);
        } else {
            //因设备Service启动较慢，对mBleService进行了判空
            if (mBleService != null && mBleService.isConnected() && mAddress.equals(mDevice.getAddress())) {
                if (onBleStateListener != null) {
                    //已经连接过了可以直接跳转
                    onBleStateListener.onServiceDiscovered();
                }
            } else if (mBleService != null) {
                //已经连接过但是设备不同
                mBleService.connectDevice(mDevice);
            }
        }

    }

    public void writeValue(String serviceUuid, String characteristicUuid, byte[] value) {
        if (mBleService == null || mDevice == null) {
            return;
        }
        List<BluetoothGattService> supportedGattServices = mBleService.getSupportedGattServices(mDevice);
        if (supportedGattServices == null) {
            return;
        }
        for (BluetoothGattService bluetoothGattService : supportedGattServices) {
            String gattServiceUUID = Long.toHexString(
                    bluetoothGattService.getUuid().getMostSignificantBits())
                    .substring(0, 4);
            for (BluetoothGattCharacteristic characteristic : bluetoothGattService.getCharacteristics()) {
                String gattCharacteristicUUID = Long.toHexString(
                        characteristic.getUuid().getMostSignificantBits())
                        .substring(0, 4);
                if (gattServiceUUID.equals(serviceUuid)
                        && gattCharacteristicUUID.equals(characteristicUuid)) {
                    int length = value.length;
                    int index = 0;
                    int sendLength = 0;
                    while (length > 0) {
                        if (length > mBleService.getMTU()) {
                            sendLength = mBleService.getMTU();
                        } else {
                            sendLength = length;
                        }
                        byte sendValue[] = new byte[sendLength];
                        System.arraycopy(value, index, sendValue, 0, sendLength);
                        try {
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        characteristic.setValue(sendValue);
                        characteristic
                                .setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        this.writeValue(characteristic);
                        length -= sendLength;
                        index += sendLength;
                    }
                }
            }
        }
    }


    /**
     * 根据特征值写入数据
     *
     * @param characteristic
     */
    public void writeValue(BluetoothGattCharacteristic characteristic) {
        mBleService.writeValue(mDevice, characteristic);
    }

    public void readValue(String serviceUUID, String characteristicUUID) {
        for (BluetoothGattService service : mBleService.getSupportedGattServices(mDevice)) {
            String gattServiceUUID = Long.toHexString(
                    service.getUuid().getMostSignificantBits())
                    .substring(0, 4);
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                String gattCharacteristicUUID = Long.toHexString(
                        characteristic.getUuid().getMostSignificantBits())
                        .substring(0, 4);
                if (gattServiceUUID.equals(serviceUUID) && gattCharacteristicUUID.equals(characteristicUUID)) {
                    readValue(characteristic);
                }
            }
        }
    }

    private void readValue(BluetoothGattCharacteristic characteristic) {
        mBleService.readValue(mDevice, characteristic);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void requestMTU() {
        mBleService.requestMTU(mDevice);
    }

    public void setNotification(String serviceUUID, String characteristicUUID, boolean enable) {
        if (mBleService == null || mBleService.getSupportedGattServices(mDevice) == null) {
            return;
        }
        for (BluetoothGattService service : mBleService.getSupportedGattServices(mDevice)) {
            String gattServiceUUID = Long.toHexString(
                    service.getUuid().getMostSignificantBits())
                    .substring(0, 4);
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                String gattCharacteristicUUID = Long.toHexString(
                        characteristic.getUuid().getMostSignificantBits())
                        .substring(0, 4);
                if (gattServiceUUID.equals(serviceUUID)
                        && gattCharacteristicUUID.equals(characteristicUUID)) {
                    setNotification(characteristic, enable);
                }
            }
        }
    }

    private void setNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        mBleService.setCharacteristicNotification(mDevice, characteristic, enable);
    }

    private void registerReceiver() {
        mContext.registerReceiver(mGattUpdateReceiver, mBleIntentFilter());
    }

    private void unregisterReceiver() {
        mContext.unregisterReceiver(mGattUpdateReceiver);
    }

    private IntentFilter mBleIntentFilter() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(BleService.ACTION_DATA_AVAILABLE);
        filter.addAction(BleService.ACTION_GATT_CONNECTED);
        filter.addAction(BleService.ACTION_GATT_DISCONNECTED);
        filter.addAction(BleService.ACTION_GATT_SERVICES_DISCOVERED);
        return filter;
    }
}
