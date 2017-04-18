package me.robin.wx.robot;

import me.robin.wx.robot.frame.Server;

import java.util.concurrent.TimeUnit;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class Robot {
    static {
        System.setProperty("jsse.enableSNIExtension", "false");
    }

    public static void main(String[] args) throws InterruptedException {
        new Server("wx782c26e4c19acffb").start();
        TimeUnit.HOURS.sleep(1);
    }
}
