package me.robin.wx.client.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import me.robin.wx.client.model.LoginUser;
import me.robin.wx.client.model.WxGroup;
import me.robin.wx.client.model.WxUser;
import me.robin.wx.client.service.ContactService;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.misc.BASE64Decoder;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class WxUtil {

    private static final Logger logger = LoggerFactory.getLogger(WxUtil.class);

    public static WxUser parse(JSONObject contact) {
        String userName = contact.getString("UserName");
        Class<? extends WxUser> type = WxUser.class;
        if (StringUtils.startsWith(userName, "@@")) {
            type = WxGroup.class;
        }
        return contact.toJavaObject(type);
    }

    public static String randomDeviceId() {
        return "e" + random(15);
    }

    public static String random(int length) {
        StringBuilder random = new StringBuilder();
        for (int i = 0; i < length; i++) {
            random.append(Math.round(Math.random() * 9));
        }
        return random.toString();
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

    public static String revertXml(String content) {
        content = StringUtils.replace(content, "&lt;", "<");
        return StringUtils.replace(content, "&gt;", ">");
    }

    private static final File wxTmpFileDir = new File("./wx_tmp");

    public static void saveImg(byte[] data, String savePath) {
        if (null != data && data.length > 0) {
            try {
                FileUtils.writeByteArrayToFile(new File(wxTmpFileDir, "header/" + savePath), data);
            } catch (IOException e) {
                logger.warn("保存头像文件异常:{}", e.getMessage());
            }
        }
    }

    public static byte[] getHeaderImg(String path) {
        File file = new File(wxTmpFileDir, "header/" + path);
        if (file.exists() && file.isFile()) {
            try {
                return FileUtils.readFileToByteArray(file);
            } catch (IOException e) {
                logger.warn("读取头像文件异常:{}", e.getMessage());
            }
        }
        return null;
    }

    public static void deleteImgTmpDir(String parentPath) {
        try {
            FileUtils.deleteDirectory(new File(wxTmpFileDir, "header/" + parentPath));
        } catch (IOException e) {
            logger.warn("头像文件临时目录删除异常:{}", e.getMessage());
        }
    }

    public static void deleteTmp() {
        try {
            FileUtils.deleteDirectory(wxTmpFileDir);
        } catch (IOException e) {
            logger.warn("头像文件临时目录删除异常:{}", e.getMessage());
        }
    }
}
