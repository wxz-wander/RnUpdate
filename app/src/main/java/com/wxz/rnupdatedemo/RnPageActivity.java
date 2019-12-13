package com.wxz.rnupdatedemo;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.LifecycleState;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactRootView;
import com.facebook.react.modules.core.DefaultHardwareBackBtnHandler;
import com.facebook.react.uimanager.UIImplementationProvider;
import com.wxz.rnupdatedemo.rn.RnUpdateHelper;

import java.io.File;

public class RnPageActivity extends AppCompatActivity implements DefaultHardwareBackBtnHandler {
    @Override
    public void invokeDefaultOnBackPressed() {
        super.onBackPressed();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ReactRootView reactRootView = new ReactRootView(this);
        ReactInstanceManager.Builder builder = ReactInstanceManager.builder()
                .setApplication(getApplication())
                .setJSMainModuleName(getJSMainModuleName())
                .setUIImplementationProvider(getUIImplementationProvider())
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED);
        String jsBundleFile = getJSBundleFile();
        if (jsBundleFile != null) {
            builder.setJSBundleFile(jsBundleFile);
        } else {
            builder.setBundleAssetName(Assertions.assertNotNull(getBundleAssetName()));
        }
        ReactInstanceManager reactInstanceManager = builder.build();
        reactRootView.startReactApplication(reactInstanceManager, getMainComponentName(), null);
        setContentView(reactRootView);
    }

    /**
     * cache/rn/moduleId/bundle/index.android.bundle
     */
    private String getJSBundleFile() {
        String jsBundleFile = getApplicationContext().getCacheDir() + File.separator + "rn" + File.separator + RnUpdateHelper.MODULE_WAYBILL + File.separator + "bundle/index.wallbill.bundle";
        Log.d("rn", "getJSBundleFile: " + jsBundleFile);
        File file = new File(jsBundleFile);
        return file.exists() ? jsBundleFile : null;
    }

    private UIImplementationProvider getUIImplementationProvider() {
        return new UIImplementationProvider();
    }


    private String getJSMainModuleName() {
        return "index.android";
    }

    private String getBundleAssetName() {
        return "index.wallbill.bundle";
    }

    private String getMainComponentName() {

        return "RnDemo";
    }
}
