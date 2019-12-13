package com.wxz.rnupdatedemo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.wxz.rnupdatedemo.databinding.ActivityMainBinding;
import com.wxz.rnupdatedemo.rn.RnUpdateHelper;

import static com.wxz.rnupdatedemo.rn.RnUpdateHelper.ASSET_BUNDLE_NAME;
import static com.wxz.rnupdatedemo.rn.RnUpdateHelper.ASSET_BUNDLE_VERSION;
import static com.wxz.rnupdatedemo.rn.RnUpdateHelper.MODULE_WAYBILL;

public class MainActivity extends AppCompatActivity implements RnUpdateHelper.OnRnUpdateListener {
    private static final String TAG = "MainActivity";


    private ActivityMainBinding mainBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mainBinding.tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gotoRN();
            }
        });
    }

    private void gotoRN() {
        RnUpdateHelper helper = new RnUpdateHelper(this);
        helper.setRnUpdateListener(this);
        helper.dispatchModule(MODULE_WAYBILL, ASSET_BUNDLE_NAME, ASSET_BUNDLE_VERSION);
    }

    @Override
    public void onUpdateResult(int result) {
        Log.d(TAG, "onUpdateResult: " + result);
        startActivity(new Intent(this, RnPageActivity.class));
    }
}
