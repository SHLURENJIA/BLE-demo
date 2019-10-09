package com.shawn.bledemo.bleutil;

import android.bluetooth.BluetoothDevice;

/**
 * 作者：create by @author{ YSH } on 2019/10/8
 * 描述: 搜索蓝牙设备返回监听
 * 修改备注:
 */
public interface OnSearchDevicesListener {
    void onNewDeviceFound(BluetoothDevice device, int rssi);
}
