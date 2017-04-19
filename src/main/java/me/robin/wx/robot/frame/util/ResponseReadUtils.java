package me.robin.wx.robot.frame.util;

import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

/**
 * Created by xuanlubin on 2017/4/18.
 */
public class ResponseReadUtils {

    private static final Logger logger = LoggerFactory.getLogger(ResponseReadUtils.class);

    public static String read(Response response) throws IOException {
        String contentEncoding = response.header("Content-Encoding");
        if ("deflate".equalsIgnoreCase(contentEncoding)) {
            return tranInflaterInputStream(response.body().bytes());
        } else if ("gzip".equalsIgnoreCase(contentEncoding)) {
            return uncompressGzip(response.body().bytes());
        } else {
            return response.body().string();
        }
    }

    public static String uncompressGzip(byte[] b) {
        if (b == null || b.length == 0) {
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream(b.length);
        ByteArrayInputStream in = new ByteArrayInputStream(b);

        try {
            GZIPInputStream gunzip = new GZIPInputStream(in);
            byte[] buffer = new byte[256];
            int n;
            while ((n = gunzip.read(buffer)) >= 0) {
                bos.write(buffer, 0, n);
            }
        } catch (IOException e) {
            logger.warn("Gzip 解压异常:{}", e.getMessage());
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        return bos.toString();
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
