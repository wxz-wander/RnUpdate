package com.wxz.rnupdatedemo.rn;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.facebook.infer.annotation.Assertions;
import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import io.reactivex.android.schedulers.AndroidSchedulers;

import io.reactivex.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * rn 检查更新的帮助类
 * 使用步骤：
 * 1、创建实例
 * 2、调用 update方法
 * <p>
 * 存储目录设计
 * cache/rn/moduleId/bundle/index.android.bundle
 * cache/rn/moduleId/bundle/index.android.bundle.meta
 * cache/rn/moduleId/bundle/drawable-hdpi
 * cache/rn/moduleId/version.txt
 */
public class RnUpdateHelper {
    private static final String TAG = "RnUpdateHelper";
    public static final String MODULE_WAYBILL = "85";
    public static final String ASSET_BUNDLE_NAME = "wbBundle.zip";
    public static final String ASSET_BUNDLE_VERSION = "wbversion.txt";
    private static final String RN_DIR = "rn";
    private static final String BUNDLE_DIR = "bundle";
    private static final String VERSION_NAME = "version.txt";
    private static final String TEMP_NAME = "temp.zip";
    private static final int MSG_START_COPY = 10;
    private static final int MSG_COPY_SUCCESS = 11;
    private static final int MSG_START_REQUEST = 12;
    private static final int MSG_DOWNLOAD_SUCCESS = 13;
    private static final int MSG_DOWNLOAD_FAILED = 14;
    private static final int MSG_UPDATE_SUCCESS = 15;
    private final Context mContext;
    /**
     * rn 检查更新
     * -1 检查更新失败
     * 0 用户不更新
     * -2 下载更新失败
     * 1 不需要更新
     * 2 下载更新成功
     */
    public static final int RESULT_CHECK_FAILED = -1;
    public static final int RESULT_USER_CANCEL = 0;
    public static final int RESULT_UPDATE_FAILED = -2;
    public static final int RESULT_NO_UPDATED = 1;
    public static final int RESULT_UPDATE_SUCCESS = 2;
    /**
     * RN module id ,唯一标示
     */
    private String mModuleId;
    /**
     * assets 目录下面的本地bundle的文件名
     */
    private String mAssetsBundleName;
    /**
     * assets 目录下面的存储本地bundle的版本信息文件名
     */
    private String mAssetsVersionName;
    private OnRnUpdateListener mListener;
    private final InnerHandler handler;
    private UpdateBean updateBean;

    private class InnerHandler extends Handler {
        private WeakReference<Context> weakReference;

        public InnerHandler(Context context) {
            weakReference = new WeakReference<>(context);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: " + msg.toString());
            switch (msg.what) {
                case MSG_COPY_SUCCESS://复制成功，就需要判断本地的
                    sendEmptyMessage(MSG_START_REQUEST);
                    break;

                case MSG_START_COPY:
                    copyBundleFromAssetsToSD();
                    break;

                case MSG_START_REQUEST:
                    requestUpdate();
                    break;

                case MSG_DOWNLOAD_FAILED:
                    AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.onUpdateResult(RESULT_UPDATE_FAILED);
                            }
                        }
                    });
                    break;
                case MSG_DOWNLOAD_SUCCESS:
                    unzipTempFileAndSaveVersion();
                    break;
                case MSG_UPDATE_SUCCESS:
                    AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) {
                                mListener.onUpdateResult(RESULT_UPDATE_SUCCESS);
                            }
                        }
                    });
                    break;
            }
        }
    }

    /**
     * 第一步
     */
    public RnUpdateHelper(Context context) {
        this.mContext = context;
        handler = new InnerHandler(context);
    }

    /**
     * 第二步
     */
    public void setRnUpdateListener(OnRnUpdateListener listener) {
        this.mListener = listener;
    }

    /**
     * 第三步：进入模块
     */
    public void dispatchModule(String moduleId, String assetsBundleName, String assetsVersionName) {
        Log.d(TAG, "dispatchModule: ");
        this.mModuleId = Assertions.assertNotNull(moduleId);
        this.mAssetsBundleName = assetsBundleName;
        this.mAssetsVersionName = assetsVersionName;
//        checkAndCreateModuleDirectory();
        //判断需不需要复制bundle文件
        if (needCopy()) {
            handler.sendEmptyMessage(MSG_START_COPY);
        } else {
            handler.sendEmptyMessage(MSG_COPY_SUCCESS);
        }
    }

    private boolean needCopy() {
        //判断本地bundle文件是否存在，不存在，就复制assets目录下的bundle zip到cache中,若存在就请求网络检查rn更新
        String bundlePath = mContext.getCacheDir() + File.separator + RN_DIR + File.separator + mModuleId + File.separator + BUNDLE_DIR;
        File file = new File(bundlePath);
        if (!file.exists() || file.listFiles() == null) {
            return true;
        }
        //判断assets 的bundle是不是比cache里面的版本高，如果高，就复制asset到cache中
        String assetsBundleVersion = readAssetsBundleVersion();
        String localModuleVersion = readLocalModuleVersion();
        boolean newVersion = VersionNameUtils.compareVersionName(assetsBundleVersion, localModuleVersion);
        // newVersion为true，说明cache里面版本是新的，不需要复制assets中的bundle到cache中
        return !newVersion;
    }


    /**
     * 解压文件，并保存版本信息
     */
    private void unzipTempFileAndSaveVersion() {
        Schedulers.io().scheduleDirect(new Runnable() {
            @Override
            public void run() {
                FileInputStream inputStream = null;
                BufferedWriter bufferedWriter = null;
                try {
                    backupModule();
                    inputStream = new FileInputStream(getBundleTempPath());
                    unZipFile(inputStream, getOrCreateBundleDir().getAbsolutePath());
                    /**
                     * 这里之所以需要这一个步骤是因为，bundle资源文件压缩成一个压缩文件的时候，会多一个目录层级，比如压缩包是wbBundle.zip ，解压缩
                     * 后生成目录 cache/rn/moduleId/bundle/wbBundle/index.android.bundle,这里其实就多了一层目录wbBundle，和我设计的bundle
                     * 目录下就是具体的bundle资源相悖，所以，就将需要删掉wbBundle这一层
                     * */
                    copySingleChildDir2Parent(getOrCreateBundleDir());
//                    unZipFile(new File(getBundleTempPath()), getOrCreateBundleDir().getAbsolutePath(), true);
                    File versionFile = getOrCreateBundleVersionFile();
                    bufferedWriter = new BufferedWriter(new FileWriter(versionFile));
                    bufferedWriter.write(updateBean.getModulaVersion());
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                    bufferedWriter.close();
                    deleteDir(new File(getBundleTempPath()));
                    deleteBackFile();
                    handler.sendEmptyMessage(MSG_UPDATE_SUCCESS);
                } catch (IOException e) {
                    Log.e(TAG, "unzipTempFileAndSaveVersion: ", e);
                    restoreBackup();
                    handler.sendEmptyMessage(MSG_DOWNLOAD_FAILED);
                } finally {
                    closeStream(inputStream);
                    closeStream(bufferedWriter);
                }
            }
        });

    }

    private void deleteBackFile() {
        try {
            File file = new File(getOrCreateRnDir(), mModuleId + "bak");
            deleteDir(file);
        } catch (IOException e) {
            Log.e(TAG, "deleteBackFile: ", e);
        }
    }

    /**
     * 请求网络检查更新
     */
    private void requestUpdate() {
        Log.d(TAG, "requestUpdate: ");
        showLoading();
        String version = readLocalModuleVersion();
        HashMap<String, String> body = new HashMap<>(5);
        body.put("phone", "13620000000");
        body.put("appType", "0");
        body.put("modulaId", mModuleId);
        body.put("modulaVersion", version);
        body.put("appVersion", "1.60");
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), new Gson().toJson(body));
        Request request = new Request.Builder()
                .url("http://xxxx/rest")//todo 这里替换成真是的路由
                .header("method", "xxxx")
                .post(requestBody)
                .build();
        getClient().newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "requestUpdate onFailure: " + e.getMessage());
                        AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
                            @Override
                            public void run() {
                                hideLoading();
                                if (mListener != null) {
                                    mListener.onUpdateResult(RESULT_CHECK_FAILED);
                                }
                            }
                        });

                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String string = response.body().string();
                        Log.d(TAG, "onResponse: " + string);
                        AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
                            @Override
                            public void run() {
                                hideLoading();
                            }
                        });
                        parseNetResponse(string);
                    }
                });

    }

    private void showLoading() {
        //todo 显示loading动画
        Toast.makeText(mContext, "请求网络中...", Toast.LENGTH_LONG).show();
    }

    private void hideLoading() {
        //todo 关闭loading动画
        Toast.makeText(mContext, "请求网络结束", Toast.LENGTH_LONG).show();
    }

    private void parseNetResponse(String body) {
        Log.d(TAG, "parseNetResponse: " + body);
        UpdateResponse updateResponse = new Gson().fromJson(body, UpdateResponse.class);
        if (updateResponse == null || updateResponse.getData() == null || !updateResponse.getData().isUpdate()) {
            AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
                @Override
                public void run() {
                    if (mListener != null) {
                        mListener.onUpdateResult(RESULT_NO_UPDATED);
                    }
                }
            });
            return;
        }
        this.updateBean = updateResponse.getData();
        noticeUserUpdate();

    }

    private void noticeUserUpdate() {
        if ("0".equals(updateBean.getUpdateType())) {
            hotfix();
            return;
        }
        AndroidSchedulers.mainThread().scheduleDirect(new Runnable() {
            @Override
            public void run() {
                showUpdateDialog();
            }
        });

    }

    private void showUpdateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle("版本更新")
                .setMessage(updateBean.getRemark())
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        hotfix();
                        dialog.dismiss();
                    }
                });
        builder.setCancelable(!"2".equals(updateBean.getUpdateType()));
        //非强制更新
        if ("1".equals(updateBean.getUpdateType())) {
            builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (mListener != null) {
                        mListener.onUpdateResult(RESULT_USER_CANCEL);
                    }
                    dialog.dismiss();
                }
            });
        }
        Dialog materialDialog = builder.create();
        materialDialog.show();
    }

    private String getBundleTempPath() {
        return getOrCreateModuleDir().getAbsolutePath() + TEMP_NAME;
    }

    /***
     * 下载更新module的
     * */
    private void hotfix() {
        Schedulers.io().scheduleDirect(new Runnable() {
            @Override
            public void run() {
                BufferedSink sink = null;
                try {
                    File tempFile = new File(getBundleTempPath());
                    OkHttpClient client = new OkHttpClient();
                    Request request = new Request.Builder().url(updateBean.getDownloadUrl()).build();
                    Response response = client.newCall(request).execute();
                    if (response.code() != 200) {
                        throw new Error("Server return code" + response.code());
                    }

                    ResponseBody body = response.body();
                    long contentLength = body.contentLength();
                    BufferedSource source = body.source();

                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    tempFile.createNewFile();
                    sink = Okio.buffer(Okio.sink(tempFile));


                    long bytesRead = 0;
                    long totalRead = 0;

                    while ((bytesRead = source.read(sink.buffer(), 1024)) != -1) {

                        totalRead += bytesRead;
                    }
                    if (totalRead != contentLength) {
                        throw new Error("Unexpected eof while reading apk");
                    }

                    sink.writeAll(source);
                    sink.close();
                    handler.sendEmptyMessage(MSG_DOWNLOAD_SUCCESS);
                } catch (IOException e) {
                    Log.e(TAG, "run: ", e);
                    handler.sendEmptyMessage(MSG_DOWNLOAD_FAILED);
                } finally {
                    closeStream(sink);
                }

            }
        });
    }

    public OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(30 * 1000, TimeUnit.MILLISECONDS)
                .readTimeout(30 * 1000, TimeUnit.MILLISECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request();

                        Request.Builder builder = request
                                .newBuilder()
                                .header("Content-type", "application/json; charset=UTF-8")
                                .addHeader("format", "JSON")
                                .addHeader("token", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.1871991B076D39CB0370653CB0F78BA6.eyJzY29wZSI6WyJhcHAiXSwiZXhwIjoyNjc4NDAwMDAwLCJjbGllbnRfaWQiOiIxODEwMTcxMzgwNiIsImp0aSI6IjA3NDE4MjdiLTU5MDctNDI3YS04NjQ3LWZkZTI2MWY5ZjE5ZiJ9.HaVgfS9TlIMYAdpp9JuRPu3XL6HxxtWjuNQWpgdnbjA")
                                .addHeader("appkey", "1111");
                        Request build = builder
                                .build();
                        return chain.proceed(build);
                    }
                })
                .writeTimeout(30 * 1000, TimeUnit.MILLISECONDS)
                .build();
    }


    /**
     * 复制本地assets目录的bundle zip包到sd cache卡中
     */
    private void copyBundleFromAssetsToSD() {
        Schedulers.io().scheduleDirect(new Runnable() {
            @Override
            public void run() {
                backupModule();
                copyAssetsBundle2CacheBundleDir();
                File versionFile = getOrCreateBundleVersionFile();
                copyAssetsFile(mAssetsVersionName, versionFile);
                deleteBackFile();
                handler.sendEmptyMessage(MSG_COPY_SUCCESS);
            }
        });
    }

    private void copyAssetsFile(String assetsFile, File destFile) {
        BufferedInputStream bufferedReader = null;
        BufferedOutputStream bufferedWriter = null;
        byte[] buffer = new byte[1024];
        try {
            bufferedReader = new BufferedInputStream(mContext.getAssets().open(assetsFile));
            bufferedWriter = new BufferedOutputStream(new FileOutputStream(destFile));
            int length;
            while ((length = bufferedReader.read(buffer)) != -1) {
                bufferedWriter.write(buffer, 0, length);
            }
            bufferedWriter.flush();
        } catch (IOException e) {
            Log.e(TAG, "copyAssetsVersionFile: ", e);
        } finally {
            closeStream(bufferedReader);
            closeStream(bufferedWriter);
        }
    }

    private String readAssetsBundleVersion() {
        BufferedReader bufferedReader = null;
        String assetsVersion = "0";
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(mContext.getAssets().open(mAssetsVersionName)));
            assetsVersion = bufferedReader.readLine();
        } catch (Exception e) {
            Log.d(TAG, "getAssetsBundleVersion: read assets " + mAssetsVersionName + " exception ", e);
        } finally {
            closeStream(bufferedReader);
        }
        return assetsVersion;
    }

    private void closeStream(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }


    /**
     * 复制assets 目录下的zip bundle包到cache下的bundle目录下，如果复制失败，就需要删除bundle目录
     * 需要注意的是，asset的复制的文件，需要是一个zip包
     */
    private void copyAssetsBundle2CacheBundleDir() {
        InputStream inputStream = null;
        try {
            inputStream = mContext.getAssets().open(mAssetsBundleName);
            unZipFile(inputStream, getOrCreateBundleDir().getAbsolutePath());
            copySingleChildDir2Parent(getOrCreateBundleDir());
        } catch (IOException e) {
            Log.d(TAG, "copyBundleFromAssetsToSD: " + e.getMessage());
            restoreBackup();
        } finally {
            closeStream(inputStream);
        }
    }

    /**
     * 将子目录中的文件复制到当前目录中
     *
     */
    private void copySingleChildDir2Parent(File file) {
        if (!file.exists()) return;
        if (!file.isDirectory()) return;
        try {
            File[] files = file.listFiles();
            if (files != null && files.length == 1) {
                File child = files[0];
                if (child.isDirectory()) {
                    copyDirectory(child, getOrCreateBundleDir());
                    deleteDir(child);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "copySingleChildDir2Parent: ", e);
        }
    }

    private void copyDirectory(File src, File dest) {
        if (!dest.exists()) {
            dest.mkdirs();
        }
        File[] files = src.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                copyFile(file, new File(dest, file.getName()));
            }
            if (file.isDirectory()) {
                copyDirectory(file, new File(dest, file.getName()));
            }
        }
    }

    private boolean copyFile(File src, File dest) {
        boolean result = false;
        if ((src == null) || (dest == null)) {
            return result;
        }

        FileChannel srcChannel = null;
        FileChannel dstChannel = null;
        try {
            if (!dest.exists()) {
                dest.createNewFile();
            }
            srcChannel = new FileInputStream(src).getChannel();
            dstChannel = new FileOutputStream(dest).getChannel();
            srcChannel.transferTo(0, srcChannel.size(), dstChannel);
            result = true;
            srcChannel.close();
            dstChannel.close();
        } catch (IOException e) {
            Log.e(TAG, "copyFiles: ", e);
        } finally {
            closeStream(srcChannel);
            closeStream(dstChannel);
        }
        return result;
    }

    private void restoreBackup() {
        deleteModule();
        renameBackupFile();
    }

    private void deleteModule() {
        try {
            File moduleDir = getOrCreateModuleDir();
            deleteDir(moduleDir);
        } catch (IOException e) {
            Log.e(TAG, "deleteModule: ", e);
        }
    }

    private void backupModule() {
        File file = new File(getOrCreateRnDir(), mModuleId + "bak");
        File file1 = new File(getOrCreateRnDir(), mModuleId);
        if (file1.exists()) {
            file1.renameTo(file);
        }
    }

    private void renameBackupFile() {
        File rnDir = getOrCreateRnDir();
        File file = new File(rnDir, mModuleId + "bak");
        File file1 = new File(rnDir, mModuleId);
        if (file.exists()) {
            file.renameTo(file1);
        }
    }

    public File getOrCreateRnDir() {
        File cacheDir = mContext.getCacheDir();
        File file = new File(cacheDir, RN_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public File getOrCreateModuleDir() {
        File rnDir = getOrCreateRnDir();
        File file = new File(rnDir, mModuleId);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public File getOrCreateBundleDir() {
        File rnDir = getOrCreateModuleDir();
        File file = new File(rnDir, BUNDLE_DIR);
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    public File getOrCreateBundleVersionFile() {
        File file = null;
        try {
            File moduleDir = getOrCreateModuleDir();
            file = new File(moduleDir, VERSION_NAME);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            Log.e(TAG, "getBundleVersionFile: create version file fail " + e.getMessage());
        }
        return file;
    }

    private void deleteDir(File dir) throws IOException {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            for (File file : files) {
                String name = file.getName();
                if (".".equals(name) || "..".equals(name)) {
                    continue;
                }
                deleteDir(file);
            }
        }
        if (dir.exists() && !dir.delete()) {
            throw new IOException("fail delete file " + dir);
        }
    }

    /**
     * 解压zip 文件到指定的目录下面
     */
    private boolean unZipFile(InputStream inputStream, String bundlePath) throws IOException {
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(inputStream));
        ZipEntry zipEntry;
        while ((zipEntry = zis.getNextEntry()) != null) {
            String name = zipEntry.getName();
            if (".DS_Store".equals(name) || "".equals(name) || name.contains("../")) {
                //防止目录携带有mac 压缩文件的隐藏目录，导致升级出错,或者不安全的文件
                continue;
            }
            File file = new File(bundlePath, name);
            if (zipEntry.isDirectory()) {
                file.mkdirs();
                continue;
            }
            copyZipContentFile(zis, file);
        }
        return true;
    }

    private boolean unZipFile(File zipFile, String bundleFolder, boolean skipParent) {
        File[] files = zipFile.listFiles();
        ZipFile zFile = null;
        try {
            zFile = new ZipFile(zipFile);
            Enumeration<? extends ZipEntry> entries = zFile.entries();
            ZipEntry zipEntry = null;
            byte[] buffer = new byte[1024];
            while (entries.hasMoreElements()) {
                String name = zipEntry.getName();
                zipEntry = entries.nextElement();
                if (".DS_Store".equals(name) || "".equals(name) || name.contains("../")) {
                    //防止目录携带有mac 压缩文件的隐藏目录，导致升级出错,或者不安全的文件
                    continue;
                }
                if (zipEntry.isDirectory()) {
                    File file = new File(bundleFolder, name);
                    file.mkdirs();
                    continue;
                }
                OutputStream os = new BufferedOutputStream(new FileOutputStream(new File(bundleFolder, name)));
                InputStream is = new BufferedInputStream(zFile.getInputStream(zipEntry));
                int readLen = 0;
                while ((readLen = is.read(buffer, 0, 1024)) != -1) {
                    os.write(buffer, 0, readLen);
                }
                is.close();
                os.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            closeStream(zFile);
        }
    }

    private void copyZipContentFile(ZipInputStream zis, File file) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(file);
        byte[] buffer = new byte[1024];
        int length = 0;
        while ((length = zis.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.flush();
        outputStream.close();
    }

    /**
     * 获取本地的module的版本version
     */
    private String readLocalModuleVersion() {
        String localVersion = "0";
        String versionFilePath = getOrCreateModuleDir().getAbsolutePath() + File.separator
                + VERSION_NAME;
        File file = new File(versionFilePath);
        if (file.exists()) {
            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                localVersion = bufferedReader.readLine();
            } catch (Exception e) {
                Log.d(TAG, "loadLocalModuleVersion: read local version.txt exception " + e.getMessage());
            } finally {
                closeStream(bufferedReader);
            }
        }
        return localVersion;
    }

    /**
     * 检查创建rn 目录
     * 存储目录设计
     * cache/rn/moduleId/bundle/index.android.bundle
     * cache/rn/moduleId/bundle/index.android.bundle.meta
     * cache/rn/moduleId/bundle/drawable-hdpi
     * cache/rn/moduleId/version.txt
     */
    private void checkAndCreateModuleDirectory() {
        File cacheDir = mContext.getCacheDir();
        File rnDir = new File(cacheDir, RN_DIR);
        if (!rnDir.exists()) {
            rnDir.mkdir();
        }

        File moduleDir = new File(rnDir, mModuleId);
        if (!moduleDir.exists()) {
            moduleDir.mkdir();
        }
    }

    public interface OnRnUpdateListener {
        /**
         * rn 检查更新
         * -1 检查更新失败
         * 0 用户不更新
         * -2 下载更新失败
         * 1 不需要更新
         * 2 下载更新成功
         */
        void onUpdateResult(int result);
    }
}
