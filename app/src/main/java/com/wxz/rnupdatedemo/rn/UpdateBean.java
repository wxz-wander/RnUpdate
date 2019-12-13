package com.wxz.rnupdatedemo.rn;

public class UpdateBean {

    /**
     * downloadUrl : http://xxx-shenzhen.aliyuncs.com/webapp/rn/xxx_v7.11_apkpure.com.apk
     * updateType : 1
     * md5 : xxxxxxxxxxxxxxx
     * modulaVersion : 3
     * remark : ja89
     */

    private String downloadUrl;
    private String updateType;
    private String md5;
    private String modulaVersion;
    private String remark;
    private boolean update;

    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getUpdateType() {
        return updateType;
    }

    public void setUpdateType(String updateType) {
        this.updateType = updateType;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getModulaVersion() {
        return modulaVersion;
    }

    public void setModulaVersion(String modulaVersion) {
        this.modulaVersion = modulaVersion;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
