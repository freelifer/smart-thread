package freelifer.smart.http;

import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Http请求客户端
 * 使用系统默认HttpURLConnection或OkHttp
 *
 * @author freelifer on 2021/3/5.
 * @version 1.1.0
 */
public class HttpOrOkHttpClient {
    /**
     * The default socket timeout in milliseconds
     */
    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String ENCODING_GZIP = "gzip";
    private IHttp http;
    private Statistics statistics;

    public HttpOrOkHttpClient() {
        try {
            Class<?> cls = OkHttpClient.class;
            http = new OkHttpClientDelegate();
        } catch (Throwable e) {
            http = new HttpConnectionDelegate();
        }
    }

    public static HttpOrOkHttpClient of() {
        return new HttpOrOkHttpClient();
    }

    public static HttpRequest ofHttpRequest() {
        return new HttpRequest();
    }

    public static HttpJsonRequest ofHttpJsonRequest() {
        return new HttpJsonRequest();
    }

    public HttpOrOkHttpClient setStatistics(Statistics statistics) {
        this.statistics = statistics;
        return this;
    }

    public HttpResponse execute(HttpRequest request) throws IOException {
        // start req
        long time = System.currentTimeMillis();
        HttpResponse response = null;
        try {
            response = http.execute(request);
        } catch (Exception e) {
            Log.e("HttpOrOkHttpClient", "HttpOrOkHttpClient.execite error", e);
            String error = getErrorInfoFromException(e);
            response = new HttpResponse(-1, null, error.length(), new ByteArrayInputStream(error.getBytes()));
        }

        if (statistics != null) {
            statistics.count(request.getUrl(), response.getStatusCode(), (System.currentTimeMillis() - time));
        }

        // end req
        return response;
    }

    private interface IHttp {
        /**
         * http 请求执行
         *
         * @param request 请求包装对象
         * @return 响应对象
         * @throws IOException 异常
         */
        HttpResponse execute(HttpRequest request) throws IOException;
    }

    private static class OkHttpClientDelegate implements IHttp {
        private OkHttpClient okHttpClient;

        public OkHttpClientDelegate() {
            okHttpClient = new OkHttpClient();
        }

        @Override
        public HttpResponse execute(HttpRequest request) throws IOException {
            Request.Builder builder = new Request.Builder();
            builder.url(request.getUrl());
            Map<String, String> headers = request.getHeader();
            if (headers != null && !headers.isEmpty()) {
                for (String key : headers.keySet()) {
                    String value = headers.get(key);
                    // fix: key、value 不能为null
                    if (key != null && value != null) {
                        builder.addHeader(key, value);
                    }
                }
            }

            setConnectionParametersForRequest(builder, request);
            Request req = builder.build();
            Response response = okHttpClient.newCall(req).execute();
            int responseCode = response.code();
            if (responseCode == -1) {
                // -1 is returned by getResponseCode() if the response code could not be retrieved.
                // Signal to the caller that something was wrong with the connection.
                throw new IOException("Could not retrieve response code from OkHttp3.");
            }

            if (!hasResponseBody(request.getMethod(), responseCode)) {
                return new HttpResponse(responseCode, toHeaders(response));
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return new HttpResponse(
                        responseCode,
                        toHeaders(response),
                        0,
                        null);
            }
            return new HttpResponse(
                    responseCode,
                    null,
                    responseBody.contentLength(),
                    responseBody.byteStream());
        }

        private void setConnectionParametersForRequest(Request.Builder builder, HttpRequest request) throws IOException {
            switch (request.getMethod()) {
//            case Method.DEPRECATED_GET_OR_POST:
                // This is the deprecated way that needs to be handled for backwards compatibility.
                // If the request's post body is null, then the assumption is that the request is
                // GET.  Otherwise, it is assumed that the request is a POST.
//                byte[] postBody = request.getPostBody();
//                if (postBody != null) {
//                    connection.setRequestMethod("POST");
//                    addBody(connection, request, postBody);
//                }
//                break;
                case Method.GET:
                    // Not necessary to set the request method because connection defaults to GET but
                    // being explicit here.
                    builder.get();
                    break;
                case Method.DELETE:
                    builder.delete();
                    break;
                case Method.POST:
                    builder.post(getBodyIfExists(request));
                    break;
                case Method.PUT:
                    builder.put(getBodyIfExists(request));
                    break;
                case Method.HEAD:
                    builder.head();
                    break;
                case Method.PATCH:
                    builder.patch(getBodyIfExists(request));
                    break;
                default:
                    throw new IllegalStateException("Unknown method type.");
            }
        }

        private RequestBody getBodyIfExists(HttpRequest request) {
            byte[] body = request.getBody();
            if (body != null) {
                return RequestBody.create(MediaType.parse(request.getBodyContentType()), new String(request.getBody()));
            } else {
//            return okhttp3.internal.Util.EMPTY_REQUEST;
                return RequestBody.create(null, "");
            }
        }

        private Map<String, List<String>> toHeaders(Response response) {
            Headers headers = response.headers();
            if (headers == null || headers.size() <= 0) {
                return Collections.emptyMap();
            }
            Map<String, List<String>> map = new HashMap<>(headers.size());
            for (String key : headers.names()) {
                map.put(key, headers.values(key));
            }
            return map;
        }
    }

    private static class HttpConnectionDelegate implements IHttp {
        @Override
        public HttpResponse execute(HttpRequest request) throws IOException {
            HttpURLConnection conn = openConnection(request);
            boolean keepConnectionOpen = false;
            try {
                Map<String, String> headers = request.getHeader();
                if (headers != null && !headers.isEmpty()) {
                    for (String key : headers.keySet()) {
                        String value = headers.get(key);
                        if (key != null && value != null) {
                            conn.setRequestProperty(key, value);
                        }
                    }
                }

                // 设置method和body参数
                setConnectionParametersForRequest(conn, request);
                int responseCode = conn.getResponseCode();
                if (responseCode == -1) {
                    // -1 is returned by getResponseCode() if the response code could not be retrieved.
                    // Signal to the caller that something was wrong with the connection.
                    throw new IOException("Could not retrieve response code from HttpUrlConnection.");
                }

                if (!hasResponseBody(request.getMethod(), responseCode)) {
                    return new HttpResponse(responseCode, conn.getHeaderFields());
                }

                // Need to keep the connection open until the stream is consumed by the caller. Wrap the
                // stream such that close() will disconnect the connection.
                keepConnectionOpen = true;
                return new HttpResponse(
                        responseCode,
                        conn.getHeaderFields(),
                        conn.getContentLength(),
                        createInputStream(conn));
            } finally {
                if (!keepConnectionOpen) {
                    conn.disconnect();
                }
            }
        }

        private HttpURLConnection openConnection(HttpRequest request) throws IOException {
            URL parsedUrl = new URL(request.getUrl());
            HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();

            // Workaround for the M release HttpURLConnection not observing the
            // HttpURLConnection.setFollowRedirects() property.
            // https://code.google.com/p/android/issues/detail?id=194495
            // connection.setInstanceFollowRedirects(HttpURLConnection.getFollowRedirects());
            connection.setInstanceFollowRedirects(false);

            int timeoutMs = request.getTimeoutMs();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setUseCaches(false);
            connection.setDoInput(true);

            // use caller-provided custom SslSocketFactory, if any, for HTTPS
//            if ("https".equals(parsedUrl.getProtocol()) && sslSocketFactory != null) {
//                ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
//            }

            return connection;
        }

        void setConnectionParametersForRequest(HttpURLConnection connection, HttpRequest request) throws IOException {
            switch (request.getMethod()) {
//            case Method.DEPRECATED_GET_OR_POST:
                // This is the deprecated way that needs to be handled for backwards compatibility.
                // If the request's post body is null, then the assumption is that the request is
                // GET.  Otherwise, it is assumed that the request is a POST.
//                byte[] postBody = request.getPostBody();
//                if (postBody != null) {
//                    connection.setRequestMethod("POST");
//                    addBody(connection, request, postBody);
//                }
//                break;
                case Method.GET:
                    // Not necessary to set the request method because connection defaults to GET but
                    // being explicit here.
                    connection.setRequestMethod("GET");
                    break;
                case Method.DELETE:
                    connection.setRequestMethod("DELETE");
                    break;
                case Method.POST:
                    connection.setRequestMethod("POST");
                    addBodyIfExists(connection, request);
                    break;
                case Method.PUT:
                    connection.setRequestMethod("PUT");
                    addBodyIfExists(connection, request);
                    break;
                case Method.HEAD:
                    connection.setRequestMethod("HEAD");
                    break;
                case Method.OPTIONS:
                    connection.setRequestMethod("OPTIONS");
                    break;
                case Method.TRACE:
                    connection.setRequestMethod("TRACE");
                    break;
                case Method.PATCH:
                    connection.setRequestMethod("PATCH");
                    addBodyIfExists(connection, request);
                    break;
                default:
                    throw new IllegalStateException("Unknown method type.");
            }
        }

        private void addBodyIfExists(HttpURLConnection connection, HttpRequest request)
                throws IOException {
            byte[] body = request.getBody();
            if (body != null) {
                addBody(connection, request, body);
            }
        }

        private void addBody(HttpURLConnection connection, HttpRequest request, byte[] body)
                throws IOException {
            // Prepare output. There is no need to set Content-Length explicitly,
            // since this is handled by HttpURLConnection using the size of the prepared
            // output stream.
            connection.setDoOutput(true);
            // Set the content-type unless it was already set (by Request#getHeaders).
            if (!connection.getRequestProperties().containsKey(HEADER_CONTENT_TYPE)) {
                connection.setRequestProperty(HEADER_CONTENT_TYPE, request.getBodyContentType());
            }
            BufferedOutputStream out = new BufferedOutputStream(createOutputStream(connection));
            out.write(body);
//        DataOutputStream out =  new DataOutputStream(createOutputStream(connection));
//        out.write(body);
            out.close();
        }

        protected InputStream createInputStream(HttpURLConnection connection) {
            return new UrlConnectionInputStream(connection);
        }

        private OutputStream createOutputStream(HttpURLConnection connection) throws IOException {
            return connection.getOutputStream();
        }

    }

    private static final int HTTP_CONTINUE = 100;

    private static String getErrorInfoFromException(Exception e) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            return "\r\n" + sw.toString() + "\r\n";
        } catch (Exception e2) {
            return "bad getErrorInfoFromException";
        }
    }

    /**
     * 判断是否有响应体数据
     * [100, 200) 204和304 没有响应体数据
     *
     * @param requestMethod 请求方法
     * @param responseCode  响应码
     * @return true 有响应体
     */
    private static boolean hasResponseBody(int requestMethod, int responseCode) {
        return requestMethod != Method.HEAD
                && !(HTTP_CONTINUE <= responseCode && responseCode < HttpURLConnection.HTTP_OK)
                && responseCode != HttpURLConnection.HTTP_NO_CONTENT
                && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED;
    }

    private static InputStream inputStreamFromConnection(HttpURLConnection connection) {
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException ioe) {
            inputStream = connection.getErrorStream();
        }
        return inputStream;
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            if (closeable == null) {
                return;
            }
            closeable.close();
        } catch (Exception e) {
            // ignore
        }
    }

    public static class HttpRequest {

        private String url;
        private int method = Method.GET;
        private Map<String, String> header;
        private byte[] body;
        private int timeoutMs = DEFAULT_TIMEOUT_MS;

        public HttpRequest setUrl(String url) {
            this.url = url;
            return this;
        }

        public HttpRequest setMethod(int method) {
            this.method = method;
            return this;
        }

        public HttpRequest setHeader(Map<String, String> header) {
            if (header == null) {
                throw new NullPointerException("HttpRequest setHeader Not allowed Null.");
            }
            if (header.isEmpty()) {
                // key 不为null 不为empty
                // value 不为null
                for (String key : header.keySet()) {
                    if (key == null) {
                        throw new NullPointerException("HttpRequest setHeader key Not allowed Null.");
                    }
                    if (key.isEmpty()) {
                        throw new NullPointerException("HttpRequest setHeader key Not allowed Empty.");
                    }
                    String value = header.get(key);
                    if (value == null) {
                        throw new NullPointerException("HttpRequest setHeader value Not allowed Null.");
                    }
                }
            }

            this.header = header;
            return this;
        }

        public HttpRequest setBody(byte[] body) {
            this.body = body;
            return this;
        }

        public HttpRequest setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public String getUrl() {
            return url;
        }

        public int getMethod() {
            return method;
        }

        public Map<String, String> getHeader() {
            return header;
        }

        public byte[] getBody() {
            return body;
        }

        public String getBodyContentType() {
            return "application/x-www-form-urlencoded; charset=UTF-8";
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }
    }

    public static class HttpJsonRequest extends HttpRequest {
        private static final String PROTOCOL_CONTENT_TYPE = "application/json; charset=utf-8";

        @Override
        public String getBodyContentType() {
            return PROTOCOL_CONTENT_TYPE;
        }
    }

    public final static class HttpResponse {

        private final int statusCode;
        private final Map<String, List<String>> headers;
        private final long contentLength;
        private final InputStream content;

        /**
         * Construct a new HttpResponse for an empty response body.
         *
         * @param statusCode the HTTP status code of the response
         * @param headers    the response headers
         */
        public HttpResponse(int statusCode, Map<String, List<String>> headers) {
            this(statusCode, headers, /* contentLength= */ -1, /* content= */ null);
        }

        /**
         * Construct a new HttpResponse.
         *
         * @param statusCode    the HTTP status code of the response
         * @param headers       the response headers
         * @param contentLength the length of the response content. Ignored if there is no content.
         * @param content       an {@link InputStream} of the response content. May be null to indicate that
         *                      the response has no content.
         */
        public HttpResponse(int statusCode, Map<String, List<String>> headers, long contentLength, InputStream content) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.contentLength = contentLength;
            this.content = content;
        }

        /**
         * Returns the HTTP status code of the response.
         */
        public final int getStatusCode() {
            return statusCode;
        }

        /**
         * Returns the response headers. Must not be mutated directly.
         */
        public final Map<String, List<String>> getHeaders() {
            return headers;
        }

        /**
         * Returns the length of the content. Only valid if {@link #getContent} is non-null.
         */
        public final long getContentLength() {
            return contentLength;
        }

        /**
         * Returns an {@link InputStream} of the response content. May be null to indicate that the
         * response has no content.
         */
        public final InputStream getContent() {
            if (content != null) {
                return content;
            } else {
                return null;
            }
        }

        public final HttpResponseBody body() {
            return new HttpResponseBody(getContent());
        }
    }

    public static class HttpResponseBody implements Closeable {
        private InputStream inputStream;

        @Override
        public void close() throws IOException {
            closeQuietly(inputStream);
        }

        public HttpResponseBody(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        public String string() throws IOException {
            if (inputStream == null) {
                return "";
            }
            ByteArrayOutputStream outputStream = null;
            try {
                //创建字节数组输出流 ，用来输出读取到的内容
                outputStream = toByteArrayOutputStream(inputStream);

                //返回字符串结果
                return outputStream.toString("UTF-8");
            } finally {
                // 关闭输入流和输出流
                closeQuietly(outputStream);
                closeQuietly(inputStream);
            }
        }

        public byte[] bytes() throws IOException {
            if (inputStream == null) {
                return new byte[0];
            }
            ByteArrayOutputStream outputStream = null;
            try {
                //创建字节数组输出流 ，用来输出读取到的内容
                outputStream = toByteArrayOutputStream(inputStream);

                //返回字符串结果
                return outputStream.toByteArray();
            } finally {
                // 关闭输入流和输出流
                closeQuietly(outputStream);
                closeQuietly(inputStream);
            }
        }

        private ByteArrayOutputStream toByteArrayOutputStream(InputStream in) throws IOException {
            //创建字节数组输出流 ，用来输出读取到的内容
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            //创建读取缓存,大小为1024
            byte[] buffer = new byte[1024];
            //每次读取长度
            int len = 0;
            //开始读取输入流中的文件
            while ((len = in.read(buffer)) != -1) { //当等于-1说明没有数据可以读取了
                byteArrayOutputStream.write(buffer, 0, len); // 把读取的内容写入到输出流中
            }
            return byteArrayOutputStream;
        }
    }

    private static class UrlConnectionInputStream extends FilterInputStream {
        private final HttpURLConnection mConnection;

        UrlConnectionInputStream(HttpURLConnection connection) {
            super(inputStreamFromConnection(connection));
            mConnection = connection;
        }

        @Override
        public void close() throws IOException {
            super.close();
            mConnection.disconnect();
        }
    }

    /**
     * Supported request methods.
     */
    public interface Method {
        int DEPRECATED_GET_OR_POST = -1;
        int GET = 0;
        int POST = 1;
        int PUT = 2;
        int DELETE = 3;
        int HEAD = 4;
        int OPTIONS = 5;
        int TRACE = 6;
        int PATCH = 7;
    }

    public interface Statistics {
        /**
         * 计数
         */
        void count(String uri, int code, long time);
    }
}