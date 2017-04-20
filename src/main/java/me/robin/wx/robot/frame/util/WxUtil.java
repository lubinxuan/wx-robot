package me.robin.wx.robot.frame.util;

import com.alibaba.fastjson.JSON;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import sun.misc.BASE64Decoder;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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

    public static byte[] fromBase64Img(String imgStr) {
        if (imgStr == null) //图像数据为空
            return null;
        BASE64Decoder decoder = new BASE64Decoder();
        try {
            //Base64解码
            byte[] b = decoder.decodeBuffer(imgStr);
            for (int i = 0; i < b.length; ++i) {
                if (b[i] < 0) {//调整异常数据
                    b[i] += 256;
                }
            }
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getValueFromXml(String xml, String element) {
        return StringUtils.substringBetween(xml, "<" + element + ">", "</" + element + ">");
    }

    public static void jsonRequest(Object data, Consumer<RequestBody> consumer) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json;charset=utf-8"), JSON.toJSONString(data));
        consumer.accept(requestBody);
    }

    public static String revertXml(String content) {
        content = StringUtils.replace(content, "&lt;", "<");
        return StringUtils.replace(content, "&gt;", ">");
    }
}
