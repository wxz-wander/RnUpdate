package com.wxz.rnupdatedemo.rn;

public final class VersionNameUtils {
    /**
     * 比较versionName
     * @return true 表示新版本版本更高
     */
    public static boolean compareVersionName(String oldVersion, String newVersion) {
        String[] version1Array = oldVersion.split("\\.");
        String[] version2Array = newVersion.split("\\.");
        if (version1Array.length < version2Array.length) {
            StringBuilder sb = new StringBuilder(oldVersion);
            for (int i = 0; i < version2Array.length - version1Array.length; i++) {
                sb.append(".0");
            }
            version1Array = sb.toString().split("\\.");
        }
        if (version1Array.length > version2Array.length) {
            StringBuilder sb = new StringBuilder(newVersion);
            for (int i = 0; i < version1Array.length - version2Array.length; i++) {
                sb.append(".0");
            }
            version2Array = sb.toString().split("\\.");
        }
        try {
            for (int i = 0; i < version2Array.length; i++) {
                int old = Integer.parseInt(version1Array[i]);
                int ne = Integer.parseInt(version2Array[i]);
                if (ne > old) {
                    return true;
                } else if (ne < old) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
        return false;
    }
}
