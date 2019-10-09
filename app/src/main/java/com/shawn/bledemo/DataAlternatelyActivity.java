package com.shawn.bledemo;

import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.shawn.bledemo.bleutil.OnDataReceiveListener;

/**
 * 作者：create by @author{ YSH } on 2019/10/9
 * 描述: 数据交互
 * 修改备注:
 */
public class DataAlternatelyActivity extends BaseActivity implements OnDataReceiveListener {

    private TextView mTvData;
    private EditText mEtContent;
    private Button mBtnSend;


    @Override
    protected int getLayoutId() {
        return R.layout.activity_data_alternately;
    }

    @Override
    protected void initView() {
        mTvData = findViewById(R.id.tv_data);
        mEtContent = findViewById(R.id.et_content);
        mBtnSend = findViewById(R.id.btn_send);
        mTvData.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    @Override
    protected void initData() {

    }

    @Override
    protected void initListener() {
        mBleManager.setOnDataReceiveListener(this);

        mBtnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData();
            }
        });
    }

    @Override
    protected void connected() {

    }

    private void sendData() {
        String data = mEtContent.getText().toString().trim();
        mBleManager.writeValue(UUID_SERVICE_BLE, UUID_CHARACTERISTIC_BLE, data.getBytes());
        mTvData.append("\n\n发送:" + data);
    }

    @Override
    public void onDataReceive(byte[] data) {
        String newData = new String(data);
        mTvData.append("\n\n接收:" + newData);
    }

    @Override
    public void onReceiveFail(String msg) {

    }
}
