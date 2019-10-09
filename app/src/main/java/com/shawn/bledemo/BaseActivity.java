package com.shawn.bledemo;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

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

    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    public void onDisconnected() {
        if (!((Activity) this).isFinishing()) {

            mBleManager.reconnect();
        }
    }

    @Override
    public void onConnecting() {
        if (!((Activity) this).isFinishing()) {

        }
    }

    @Override
    public void onConnected() {
        if (!((Activity) this).isFinishing()) {

        }
    }

    @Override
    public void onServiceDiscovered() {
        if (!((Activity) this).isFinishing()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //打开通知
//            mBleManager.setNotification(CJYConstant.UUID_SERVICE_SERIAL, CJYConstant.UUID_CHARACTERISTIC_SERIAL, true);

        }
    }

    @Override
    public void onBleClose() {
        if (!((Activity) this).isFinishing()) {

        }
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
