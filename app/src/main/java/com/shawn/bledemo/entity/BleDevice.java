package com.shawn.bledemo.entity;

/**
 * 作者：create by @author{ YSH } on 2019/10/8
 * 描述:
 * 修改备注:
 */
public class BleDevice {

    private String name;

    private String address;

    private int rssi;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }
}
