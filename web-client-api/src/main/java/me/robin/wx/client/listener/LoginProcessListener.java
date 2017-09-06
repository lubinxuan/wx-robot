package me.robin.wx.client.listener;

/**
 * Created by Lubin.Xuan on 2017-08-21.
 * {desc}
 */
public interface LoginProcessListener {
    /**
     * 成功获取二维码后调用
     *
     * @param url 二维码地址
     */
    default void onQrUrlSuccess(String url) {
    }

    /**
     * 等待扫码过程中状态更新
     *
     * @param retCode 登录状态码
     * @param message 提示信息
     */
    default void updateScanStatus(int retCode,String message) {
    }

    /**
     * @param uin 登录成功获取到的UIN
     * @return 返回true继续登录, 否则停止登录状态
     */
    default boolean onUinSuccess(String uin) {
        return true;
    }

    /**
     * 成功获取到通讯录后调用
     */
    default void onContactSuccess() {
    }
}
