package com.shawn.bledemo.bleutil;

/**
 * 作者：create by @author{ YSH } on 2019/10/8
 * 描述:
 * 修改备注:
 */
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
