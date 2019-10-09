package com.shawn.bledemo.bleutil;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 作者：created by @author{ YSH } on 2018/12/27
 * 描述：
 * 修改备注：
 */
public class BleService extends Service {

    public static final String ACTION_GATT_CONNECTED =
            "com.zhd.cjy.ble.ACTION_GATT_CONNECTED";
    public static final String ACTION_GATT_DISCONNECTED =
            "com.zhd.cjy.ble.ACTION_GATT_DISCONNECTED";
    public static final String ACTION_GATT_CONNECTING =
            "com.zhd.cjy.ble.ACTION_GATT_CONNECTING";
    public static final String ACTION_GATT_SERVICES_DISCOVERED =
            "com.zhd.cjy.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public static final String ACTION_GATT_SERVICES_FAIL =
            "com.zhd.cjy.ble.ACTION_GATT_SERVICES_FAIL";
    public static final String ACTION_DATA_AVAILABLE =
            "com.zhd.cjy.ble.ACTION_DATA_AVAILABLE";
    public static final String EXTRA_DATA =
            "com.zhd.cjy.ble.EXTRA_DATA";
    public static final String BLE_CHARACTERISTIC_ID =
            "com.zhd.cjy.ble.characteristic";

    private static final String TAG = BleService.class.getSimpleName();
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private final IBinder mBinder = new LocalBinder();
    private int mConnectState = STATE_DISCONNECTED;
    /**
     * 最大收发帧(BLE ATT 默认最大值为512)
     */
    private int mMTU = 512;
    /**
     * 最大收发帧是否大于
     */
    private boolean isGreater20 = false;
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction = null;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectState = STATE_DISCONNECTED;
//                if (gatt.connect()) {
//                    broadcastUpdate(ACTION_GATT_CONNECTING);
//                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                gatt.close();
            }
            if (!TextUtils.isEmpty(intentAction)) {
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                mConnectState = STATE_CONNECTED;
            } else {
                broadcastUpdate(ACTION_GATT_SERVICES_FAIL);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                gatt.requestMtu(mMTU);
            } else {
                mMTU = 20;
                isGreater20 = false;
            }

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            } else {
                Log.w(TAG, "onCharacteristicRead: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            boolean b = status == BluetoothGatt.GATT_SUCCESS;
            Log.d(TAG, "onCharacteristicWrite: " + b);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            Log.d(TAG, "onCharacteristicChanged: " + characteristic.getValue());
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorRead: ");
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.d(TAG, "onDescriptorWrite: ");
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            /*if(gatt.connect()){
                broadcastUpdate(ACTION_GAT_RSSI);
                Log.d(TAG, "onReadRemoteRssi: "+rssi);
            }*/
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.d(TAG, "onMtuChanged: " + mtu);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (mtu > 100) {
                    mMTU = mtu / 3 * 2;
                } else {
                    mMTU = mtu - 3;
                }
                isGreater20 = mMTU > 20;
            } else {
                mMTU = mtu - 3;
                gatt.requestMtu(mMTU);
            }
        }


    };
    private List<BluetoothGatt> mGattList = new ArrayList<>();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    public boolean getGreater20() {
        return isGreater20;
    }

    public int getMTU() {
        return mMTU;
//        return 20;
    }

    public boolean connectDevice(BluetoothDevice device) {
        BluetoothGatt gatt = getBluetoothGatt(device);
        if (gatt != null) {
            if (gatt.connect()) {
                mConnectState = STATE_CONNECTING;
                broadcastUpdate(ACTION_GATT_CONNECTING);
            } else {
                return false;
            }
            return true;
        }
        gatt = device.connectGatt(this, false, mGattCallback);
        mGattList.add(gatt);
        return true;
    }

    /**
     * 断开连接
     */
    public void disconnect(BluetoothDevice device) {
        synchronized (this) {
            BluetoothGatt gatt = this.getBluetoothGatt(device);
            if (gatt == null) {
                Log.w(TAG, "BluetoothGatt 不能断开连接");
                return;
            }
            gatt.disconnect();
            mGattList.remove(gatt);
            mConnectState = STATE_DISCONNECTED;
        }
    }

    public void clear() {
        for (BluetoothGatt gatt : mGattList) {
            if (gatt != null) {
                gatt.close();
            }
        }
        mGattList.clear();
        mConnectState = STATE_DISCONNECTED;
    }

    public boolean isConnected() {
        return mConnectState == STATE_CONNECTED;
    }

    public List<BluetoothGattService> getSupportedGattServices(BluetoothDevice device) {
        BluetoothGatt gatt = getBluetoothGatt(device);
        if (gatt == null) {
            return null;
        }
        return gatt.getServices();
    }

    public void writeValue(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
        BluetoothGatt gatt = getBluetoothGatt(device);
        if (gatt == null) {
            return;
        }
        gatt.writeCharacteristic(characteristic);
    }

    public void readValue(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
        BluetoothGatt gatt = getBluetoothGatt(device);
        if (gatt == null) {
            return;
        }
        gatt.readCharacteristic(characteristic);
    }

    public boolean setCharacteristicNotification(BluetoothDevice device,
                                                 BluetoothGattCharacteristic characteristic, boolean enable) {
        BluetoothGatt gatt = getBluetoothGatt(device);
        if (gatt == null) {
            return false;
        }

        BluetoothGattDescriptor localGattDescriptor;
        UUID localUUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB");
        localGattDescriptor = characteristic.getDescriptor(localUUID);
        if (enable) {
            byte[] arrayOfByte = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
            localGattDescriptor.setValue(arrayOfByte);
        } else {
            byte[] arrayOfByte = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
            localGattDescriptor.setValue(arrayOfByte);
        }
        gatt.setCharacteristicNotification(characteristic, enable);
        boolean b = gatt.writeDescriptor(localGattDescriptor);
        return b;
    }

    /**
     * 发送数据到广播
     *
     * @param intentAction
     */
    private void broadcastUpdate(final String intentAction) {
        final Intent intent = new Intent(intentAction);
        sendBroadcast(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean requestMTU(BluetoothDevice device) {
        BluetoothGatt gatt = getBluetoothGatt(device);
        return gatt.requestMtu(mMTU);
    }


    private void broadcastUpdate(String action, BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            intent.putExtra(EXTRA_DATA, characteristic.getValue());
            intent.putExtra(BLE_CHARACTERISTIC_ID, characteristic.getUuid().toString());
        }
        sendBroadcast(intent);
    }

    private BluetoothGatt getBluetoothGatt(BluetoothDevice device) {
        BluetoothGatt gatt = null;
        for (BluetoothGatt temp : mGattList) {
            if (temp.getDevice().getAddress().equals(device.getAddress())) {
                gatt = temp;
            }
        }
        return gatt;
    }

    public class LocalBinder extends Binder {
        public BleService getService() {
            return BleService.this;
        }
    }
}
