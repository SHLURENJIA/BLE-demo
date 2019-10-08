package com.zhd.intelligentcollector.utils;

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
import android.util.Log;

import com.zhd.intelligentcollector.api.Api;
import com.zhd.intelligentcollector.api.CJYConstant;
import com.zhd.intelligentcollector.ble.BleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 作者：created by @author{ YSH } on 2018/12/26
 * 描述：BLE介绍 https://www.race604.com/android-ble-in-action/
 * 修改备注：
 * [x]1、使用ScanFilter。减少唤醒系统次数；(筛选失败，ServiceUUID 可能不正确)
 * [o]2、关于丢包率，10米能否保证在1%。该模块MTU好像是247,但是实际上蓝牙模块110左右就进行分包，手机发送尚未尝试
 * [√]3、ScanSetting 可以配置ScanMode，参数设置间隔越短。
 * [√]4、心跳
 * [√] 5、设置 MTU
 * [x] 第二次重连后接受到数据不完整
 */
public class BLEManager implements BluetoothAdapter.LeScanCallback, ProtocolManager.OnSpliceDataListener {

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
    private ProtocolManager mProtocolManager;
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
    private OnWriteFirmwareListener onWriteFirmwareListener;

    private Timer timer = new Timer();
    private TimerTask mTimerTask = null;
    private volatile boolean mIsHeart = false;
    /**
     * 超时任务
     */
    private Runnable TimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            mHandler.sendEmptyMessage(HANDLER_TIME_OUT);
        }
    };
    /**
     * 定时心跳
     */
    private Runnable HeartRunnable = new Runnable() {
        @Override
        public void run() {
            mHandler.sendEmptyMessage(HANDLER_HEART);
            Log.d("HEART", "heart runnable: ");
        }
    };
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_SEND_CMD:

                    break;
                case HANDLER_TIME_OUT:
                    if (onDataReceiveListener != null) {
                        onDataReceiveListener.onReceiveFail("数据超时无返回");
                    }
                    break;
                case HANDLER_HEART:
                    Log.d("HEART", "send heart: ");
                    byte[] bytes = ProtocolManager.Msg2Protocol(Api.HEART, ProtocolManager.CMD_DATA_PASSTHROUGH);
                    mIsHeart = true;
                    writeValue(CJYConstant.UUID_SERVICE_BLE, CJYConstant.UUID_CHARACTERISTIC_BLE, bytes);
                    break;
                default:
                    break;
            }
        }
    };
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
//            String uuid = intent.getStringExtra(BleService.BLE_CHARACTERISTIC_ID);
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
                //停止心跳
                stopHeartTask();
            } else if (BleService.ACTION_GATT_CONNECTING.equals(intent.getAction())) {
                if (onBleStateListener != null) {
                    onBleStateListener.onConnecting();
                }
            } else if (BleService.ACTION_GATT_SERVICES_DISCOVERED.equals(intent.getAction())) {
                if (onBleStateListener != null) {
                    onBleStateListener.onServiceDiscovered();
                }
                //开启心跳定时任务
                startHeartTask();
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
                    if (isProtocolMode) {
                        mProtocolManager.spliceData(data);
                    } else {
                        if (onDataReceiveListener != null) {
                            onDataReceiveListener.onDataReceive(data);
                        }
                    }
                    Log.d("BLEManager", "onReceive: " + Tools.byte2Hex(data));
//                    Toast.makeText(context, "" + Tools.byte2Hex(data), Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
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
    private AtomicBoolean mWait = new AtomicBoolean(false);

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

    public void setOnWriteFirmwareListener(OnWriteFirmwareListener listener) {
        onWriteFirmwareListener = listener;
    }

    public void setSN(String sn) {
        if (mProtocolManager == null) {
            mProtocolManager = new ProtocolManager(sn);
            mProtocolManager.setOnSpliceDataListener(this);
        }
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
        stopHeartTask();
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
        stopHeartTask();
    }

    public void clearProtocolCache() {
        mProtocolManager.cacheClear();
    }

    /**
     * @param mode :
     *             true - zhd;
     *             false - non protocol
     */
    public void setProtocolMode(boolean mode) {
        isProtocolMode = mode;
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
                        Log.d("writeValue", "writeValue: " + Tools.byte2Hex(sendValue));
                        length -= sendLength;
                        index += sendLength;
                    }
                }
            }
        }
    }

    public void writeValue(String serviceUuid, String characteristicUuid, int mtu, byte[] value) {
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
                        if (length > mtu) {
                            sendLength = mtu;
                        } else {
                            sendLength = length;
                        }
                        byte sendValue[] = new byte[sendLength];
                        System.arraycopy(value, index, sendValue, 0, sendLength);

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

    public void writeFirmware(String serviceUuid, String characteristicUuid, byte[] value) {
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
                    int max = 20;
                    if (mBleService.getGreater20()) {
                        max = mBleService.getMTU() >= 60 ? 60 : mBleService.getMTU();
                    }
                    int length = value.length;
                    int index = 0;
                    int sendLength = 0;
                    while (length > 0 && isFirmwareLock.get()) {
                        if (length > max) {
                            sendLength = max;
                        } else {
                            sendLength = length;
                        }
                        try {
                            Thread.sleep(40);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        byte sendValue[] = new byte[sendLength];
                        System.arraycopy(value, index, sendValue, 0, sendLength);
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        characteristic.setValue(sendValue);
                        characteristic
                                .setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        this.writeValue(characteristic);
                        Log.d("writeValue", "writeValue: " + Tools.byte2Hex(sendValue));
                        Log.d("writeValue", "writeSize: " + sendLength);
                        length -= sendLength;
                        index += sendLength;
                        if (onWriteFirmwareListener != null) {
                            onWriteFirmwareListener.onProgressUpdate(sendLength);
                        }
                    }
                }
            }
        }
    }

    public void setWait(boolean wait) {
        mWait.set(wait);
    }

    public void writeFirmware(String serviceUuid, String characteristicUuid, int mtu, long time, long ackLength, byte[] value) {
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
                    int max = 20;
                    if (mBleService.getGreater20()) {
                        max = mtu >= 20 ? mtu : 20;
                    }
                    int length = value.length;
                    int index = 0;
                    int sendLength = 0;
                    while (length > 0 && isFirmwareLock.get()) {
                        while (mWait.get()) {
                            try {
                                Thread.sleep(200);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if (length > max) {
                            sendLength = max;
                        } else {
                            sendLength = length;
                        }
                        byte sendValue[] = new byte[sendLength];
                        System.arraycopy(value, index, sendValue, 0, sendLength);
                        try {
                            Thread.sleep(time);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        characteristic.setValue(sendValue);
                        characteristic
                                .setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                        this.writeValue(characteristic);
                        Log.d("writeValue", "writeValue: " + Tools.byte2Hex(sendValue));
                        Log.d("FirmwareLog", "writeValue: " + index + " :" + Tools.byte2Hex(sendValue));
                        length -= sendLength;
                        index += sendLength;
                        if (onWriteFirmwareListener != null) {
                            int progress = index / value.length * 100;
                            onWriteFirmwareListener.onProgressUpdate(progress);
                            if (index == value.length) {
                                onWriteFirmwareListener.onUpdateFinish();
                            }
                        }
                        if (index % ackLength == 0) {
                            //4k停止，等待ack
                            mWait.set(true);
                        }
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
        if (!mIsHeart) {
            //如果不是心跳包
            mHandler.removeCallbacks(HeartRunnable);
            //30秒后发送超时通知
            mHandler.postDelayed(TimeoutRunnable, TIMEOUT_MILLISECOND);
            Log.d("HEART", "remove heart: ");
        } else {
            mIsHeart = false;//重置心跳标记
            Log.d("HEART", "write heart: ");
        }

//        Log.d("writeValue", "charaterUUID write is success  : "
//                + characteristic.getUuid().toString());
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

    @Override
    public void onSpliceData(byte[] data) {
        mHandler.removeCallbacks(TimeoutRunnable);
        String content = ProtocolManager.analysisBytes2Msg(data);
        if (onDataReceiveListener != null) {
            //错误码方式是无区域限制上报，可以抽到Base里面
/*            if (content.contains("\"TYPE\":\"ERR\"")) {
                //返回错误码
                int errorCode = 404;
                onDataReceiveListener.onReceiveFail(content);
            } else {
                onDataReceiveListener.onDataReceive(data);
            }*/
            onDataReceiveListener.onDataReceive(data);
        }
    }

    @Override
    public void onSpliceFail(String errorMsg) {
        if (onDataReceiveListener != null) {
            onDataReceiveListener.onReceiveFail(errorMsg);
        }
    }

    /**
     * 启动心跳定时任务
     */
    public void startHeartTask() {
        if (mTimerTask == null) {
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    mHandler.postDelayed(HeartRunnable, HEART_INTERVAL);
                }
            };
        }
        timer.schedule(mTimerTask, 10000, HEART_INTERVAL);
    }

    /**
     * 关闭心跳任务
     */
    public void stopHeartTask() {
        if (mTimerTask != null) {
            mTimerTask.cancel();
            mTimerTask = null;
        }
    }


    /**
     * 搜索蓝牙设备监听
     */
    public interface OnSearchDevicesListener {
        void onNewDeviceFound(BluetoothDevice device, int rssi);
    }

    public interface OnDataReceiveListener {
        void onDataReceive(byte[] data);

        void onReceiveFail(String msg);
    }

    public interface OnBleStateListener {
        void onConnected();

        void onConnecting();

        void onDisconnected();

        void onServiceDiscovered();

        /**
         * 蓝牙被关闭了
         */
        void onBleClose();

    }

    /**
     * 固件升级监听
     */
    public interface OnWriteFirmwareListener {
        void onProgressUpdate(int sendLength);

        void onUpdateFinish();
    }
}
