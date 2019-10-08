package com.shawn.bledemo;

import android.support.v7.widget.RecyclerView;

public class MainActivity extends BaseActivity {
    private RecyclerView mRvDevice;


    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected void initView() {
        mRvDevice = findViewById(R.id.rv_device);
    }

    @Override
    protected void initData() {

    }

    @Override
    protected void initListener() {

    }
}
