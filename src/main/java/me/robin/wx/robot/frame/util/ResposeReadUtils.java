package me.robin.wx.robot.frame.util;

import okhttp3.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class ResposeReadUtils {
    public static String read(Response response) throws IOException {
        if ("deflate".equalsIgnoreCase(response.header("Content-Encoding"))) {
            return tranInflaterInputStream(response.body().bytes());
        } else {
            return response.body().string();
        }
    }

    private static boolean isZLibHeader(byte[] bytes) {
        //deal with java stupidity : convert to signed int before comparison
        char byte1 = (char) (bytes[0] & 0xFF);
        char byte2 = (char) (bytes[1] & 0xFF);

        return byte1 == 0x78 && (byte2 == 0x01 || byte2 == 0x9c || byte2 == 0xDA);
    }

    private static String tranInflaterInputStream(byte[] encBytes) throws IOException {
        Inflater inflater = new Inflater(true);
        boolean isZLibHeader = isZLibHeader(encBytes);
        inflater.setInput(encBytes, isZLibHeader ? 2 : 0, isZLibHeader ? encBytes.length - 2 : encBytes.length);
        byte[] buf = new byte[4096];
        int readBytes = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        do {
            try {
                readBytes = inflater.inflate(buf);
                if (readBytes > 0) {
                    bos.write(buf, 0, readBytes);
                }
            } catch (DataFormatException e) {
                //handle error
            }
        } while (readBytes > 0);
        inflater.end();
        return bos.toString();
    }
}
