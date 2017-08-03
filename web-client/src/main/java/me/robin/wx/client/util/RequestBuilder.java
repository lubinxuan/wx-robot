package me.robin.wx.client.util;

import com.alibaba.fastjson.JSON;
import lombok.Data;
import okhttp3.*;
import okio.BufferedSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Lubin.Xuan on 2017-08-02.
 * {desc}
 */
public class RequestBuilder {

    private static final RequestBody EMPTY = new RequestBody() {
        @Override
        public MediaType contentType() {
            return MultipartBody.FORM;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {

        }
    };

    private static final MediaType JSON_TYPE = MediaType.parse("application/json");

    public enum Method {
        POST, GET
    }

    private String url;

    private Method method = Method.GET;

    private Map<String, String> queryParam;

    private Map<String, String> postParam;

    private Map<String, Object> jsonData;

    private Map<String, String> headerMap;

    private List<FilePart> filePartList;

    private RequestBuilder() {
    }

    public RequestBuilder url(String url) {
        this.url = url;
        return this;
    }

    public RequestBuilder post() {
        this.method = Method.POST;
        return this;
    }

    public RequestBuilder method(Method method) {
        this.method = method;
        return this;
    }

    public RequestBuilder query(String param, Object paramValue) {
        if (null != paramValue) {
            if (null == this.queryParam) {
                this.queryParam = new HashMap<>();
            }
            this.queryParam.put(param, String.valueOf(paramValue));
        }
        return this;
    }

    public RequestBuilder post(String param, Object paramValue) {
        if (null != paramValue) {
            if (null == this.postParam) {
                this.postParam = new HashMap<>();
            }
            this.postParam.put(param, String.valueOf(paramValue));
        }
        return this;
    }

    public RequestBuilder json(String param, Object paramValue) {
        if (null == this.jsonData) {
            this.jsonData = new HashMap<>();
        }
        if (null != paramValue) {
            this.jsonData.put(param, paramValue);
        }
        return this;
    }

    public RequestBuilder file(String fieldName, String fileName, byte[] bytes, String contentType) {
        if (null == this.filePartList) {
            this.filePartList = new ArrayList<>();
        }
        FilePart filePart = new FilePart();
        filePart.setContentType(contentType);
        filePart.setData(bytes);
        filePart.setFieldName(fieldName);
        filePart.setFileName(fileName);
        this.filePartList.add(filePart);
        return this;
    }

    public RequestBuilder header(String header, Object headerValue) {
        if (null == headerValue) {
            return this;
        }
        if (null == this.headerMap) {
            this.headerMap = new HashMap<>();
        }
        this.headerMap.put(header, String.valueOf(headerValue));
        return this;
    }

    public Response execute(OkHttpClient okHttpClient) throws IOException {
        return okHttpClient.newCall(request()).execute();
    }

    public void execute(OkHttpClient okHttpClient, Callback callback) {
        okHttpClient.newCall(request()).enqueue(callback);
    }

    public Request request() {
        Request.Builder req = new Request.Builder();
        if (null == queryParam || queryParam.isEmpty()) {
            req.url(url);
        } else {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
            for (Map.Entry<String, String> entry : queryParam.entrySet()) {
                urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
            }
            req.url(urlBuilder.build());
        }

        RequestBody requestBody = null;

        if (null != jsonData) {
            requestBody = RequestBody.create(JSON_TYPE, JSON.toJSONString(jsonData));
        } else if (null == filePartList || filePartList.isEmpty()) {
            if (null != postParam && !postParam.isEmpty()) {
                FormBody.Builder builder = new FormBody.Builder();
                for (Map.Entry<String, String> entry : postParam.entrySet()) {
                    builder.add(entry.getKey(), entry.getValue());
                }
                requestBody = builder.build();
            }
        } else {
            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            for (FilePart filePart : filePartList) {
                builder.addFormDataPart(filePart.fieldName, filePart.fileName, RequestBody.create(MediaType.parse(filePart.contentType), filePart.data));
            }
            if (null != postParam && !postParam.isEmpty()) {
                for (Map.Entry<String, String> entry : postParam.entrySet()) {
                    builder.addFormDataPart(entry.getKey(), entry.getValue());
                }
            }
            requestBody = builder.build();
        }


        if (method.equals(Method.POST) && null == requestBody) {
            requestBody = EMPTY;
        }

        if (null != requestBody) {
            req.post(requestBody);
        }

        return req.build();

    }

    public static RequestBuilder api(String url) {
        return new RequestBuilder().url(url);
    }

    @Data
    static class FilePart {
        private byte[] data;
        private String fieldName;
        private String fileName;
        private String contentType;
    }
}
