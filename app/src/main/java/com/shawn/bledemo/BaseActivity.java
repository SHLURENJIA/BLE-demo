package com.shawn.bledemo;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;
import android.widget.Toast;

import com.shawn.bledemo.bleutil.BLEManager;
import com.shawn.bledemo.bleutil.OnBleStateListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者：created by @author{ YSH } on 2018/12/21
 * 描述：
 * 修改备注：
 */
public abstract class BaseActivity extends AppCompatActivity implements OnBleStateListener {
    //打开串口UUID
    public static final String UUID_SERVICE_SERIAL = "ffe0";
    public static final String UUID_CHARACTERISTIC_SERIAL = "ffe4";
    //数据交互UUID
    public static final String UUID_SERVICE_BLE = "ffe5";
    public static final String UUID_CHARACTERISTIC_BLE = "ffe9";

    private static List<AppCompatActivity> mActivityList = new ArrayList<>();
    protected String TAG = this.getClass().getSimpleName();

    protected BLEManager mBleManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(getLayoutId());
        mActivityList.add(this);
        mBleManager = BLEManager.getInstance(getApplicationContext());
        mBleManager.setOnBleStateListener(this);

        initView();
        initData();
        initListener();
    }

    protected abstract int getLayoutId();

    protected abstract void initView();

    protected abstract void initData();

    protected abstract void initListener();

    /**
     * 连接成功，发现服务。连接结束。
     */
    protected abstract void connected();

    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    public void onDisconnected() {
        Toast.makeText(this, "重连中", Toast.LENGTH_SHORT).show();
        mBleManager.reconnect();
    }

    @Override
    public void onConnecting() {
        Toast.makeText(this, "正在连接", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnected() {
        Toast.makeText(this, "连接服务", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onServiceDiscovered() {
        Toast.makeText(this, "连接完成", Toast.LENGTH_SHORT).show();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //打开通知
        mBleManager.setNotification(UUID_SERVICE_SERIAL, UUID_CHARACTERISTIC_SERIAL, true);
        connected();
    }

    @Override
    public void onBleClose() {

    }

    @Override
    protected void onDestroy() {
        mBleManager.removeMessage();
        mActivityList.remove(this);
        super.onDestroy();
    }

    protected void exitApp() {
        if (mActivityList == null) {
            return;
        }
        for (int i = mActivityList.size() - 1; i >= 0; i--) {
            mActivityList.get(i).finish();
        }
    }
}
