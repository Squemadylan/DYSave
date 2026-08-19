package com.douyin.downloader;

/**
 * Shizuku UserService 接口定义。
 */
interface IUserService {
    /** Shizuku 服务端要求的销毁方法 */
    void destroy();

    /** 列出目标 App 的 shared_prefs 目录下的 XML 文件名 */
    String listSharedPrefs(String pkg);

    /** 读取目标 App 的指定 SharedPrefs XML 文件内容 */
    String readSharedPrefsFile(String pkg, String file);
}
