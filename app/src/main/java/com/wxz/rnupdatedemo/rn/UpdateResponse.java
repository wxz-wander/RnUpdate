package com.wxz.rnupdatedemo.rn;

public class UpdateResponse {

    /**
     * code : 0
     * data : {"downloadUrl":"http://xxx-shenzhen.aliyuncs.com/webapp/rn/xxx_v7.11_apkpure.com.apk","updateType":"1","md5":"xxxxxxxxxxxxxxx","modulaVersion":"3","remark":"6p7x"}
     * msg : 需要更新
     * update : true
     */

    private String code;
    private UpdateBean data;
    private String msg;


    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public UpdateBean getData() {
        return data;
    }

    public void setData(UpdateBean data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }


}
