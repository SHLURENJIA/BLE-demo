package com.shawn.bledemo.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.shawn.bledemo.R;
import com.shawn.bledemo.entity.BleDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者：create by @author{ YSH } on 2019/10/8
 * 描述:
 * 修改备注:
 */
public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.DeviceHolder> {

    private List<BleDevice> mDeviceList = new ArrayList<>();
    private Context mContext;
    private OnDeviceClickListener onDeviceClickListener;

    public DevicesAdapter(Context mContext) {
        this.mContext = mContext;
    }

    public void setOnDeviceClickListener(OnDeviceClickListener onDeviceClickListener) {
        this.onDeviceClickListener = onDeviceClickListener;
    }

    /**
     * 添加设备
     * 如果没有存在则添加，如果已存在则更新rssi
     */
    public void addDevice(BleDevice device) {
        boolean isNewDevice = true;
        for (int i = 0; i < mDeviceList.size(); i++) {
            if (device.getAddress().equals(mDeviceList.get(i).getAddress())) {
                //地址相同
                isNewDevice = false;
                mDeviceList.get(i).setRssi(device.getRssi());
                break;
            }
        }
        if (isNewDevice) {
            mDeviceList.add(device);
        }
    }

    @NonNull
    @Override
    public DeviceHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_devices, viewGroup, false);
        return new DeviceHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceHolder holder, int i) {
        final BleDevice device = mDeviceList.get(i);
        holder.tvMac.setText(device.getAddress());
        holder.tvName.setText(device.getName());
        holder.tvRssi.setText(String.format("rssi: %d", device.getRssi()));

        if (onDeviceClickListener != null) {
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeviceClickListener.onDeviceClick(device);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return mDeviceList.size();
    }

    public interface OnDeviceClickListener {
        void onDeviceClick(BleDevice device);
    }

    class DeviceHolder extends RecyclerView.ViewHolder {
        private TextView tvName;
        private TextView tvMac;
        private TextView tvRssi;

        public DeviceHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_name);
            tvMac = itemView.findViewById(R.id.tv_mac);
            tvRssi = itemView.findViewById(R.id.tv_rssi);
        }
    }

}
