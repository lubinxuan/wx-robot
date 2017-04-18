package me.robin.wx.robot.frame.util;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class WxUtil {
    public static String randomDeviceId() {
        return "e" + random(15);
    }

    public static String random(int length) {
        String random = "";
        for (int i = 0; i < length; i++) {
            random += Math.round(Math.random() * 9);
        }
        return random;
    }
}
