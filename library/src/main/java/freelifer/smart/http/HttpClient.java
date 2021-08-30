package freelifer.smart.http;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * @author freelifer on 2021/3/5.
 */
public class HttpClient {
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String ENCODING_GZIP = "gzip";
    private SSLSocketFactory sslSocketFactory;

    public HttpClient() {
    }

    public static boolean isInputStreamGZIPCompressed(PushbackInputStream inputStream) throws IOException {
        if (inputStream == null) {
            return false;
        } else {
            byte[] signature = new byte[2];
            int count = 0;

            int readCount;
            try {
                while (count < 2) {
                    readCount = inputStream.read(signature, count, 2 - count);
                    if (readCount < 0) {
                        boolean var4 = false;
                        return var4;
                    }

                    count += readCount;
                }
            } finally {
                inputStream.unread(signature, 0, count);
            }

            readCount = signature[0] & 255 | signature[1] << 8 & '\uff00';
            return 35615 == readCount;
        }
    }

    public HttpResponse execute(HttpRequest request) throws IOException {
        HttpURLConnection conn = openConnection(request);
        boolean keepConnectionOpen = false;
        try {
            Map<String, String> headers = request.getHeader();
            if (headers != null && !headers.isEmpty()) {
                for (String headerName : headers.keySet()) {
                    conn.setRequestProperty(headerName, headers.get(headerName));
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
        if ("https".equals(parsedUrl.getProtocol()) && sslSocketFactory != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
        }

        return connection;
    }

    private static final int HTTP_CONTINUE = 100;

    private static boolean hasResponseBody(int requestMethod, int responseCode) {
        return requestMethod != Method.HEAD
                && !(HTTP_CONTINUE <= responseCode && responseCode < HttpURLConnection.HTTP_OK)
                && responseCode != HttpURLConnection.HTTP_NO_CONTENT
                && responseCode != HttpURLConnection.HTTP_NOT_MODIFIED;
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

    /**
     * Initializes an {@link InputStream} from the given {@link HttpURLConnection}.
     *
     * @param connection
     * @return an HttpEntity populated with data from <code>connection</code>.
     */
    private static InputStream inputStreamFromConnection(HttpURLConnection connection) {
        InputStream inputStream;
        try {
            inputStream = connection.getInputStream();
        } catch (IOException ioe) {
            inputStream = connection.getErrorStream();
        }
        return inputStream;
    }

    public static void closeQuietly(Closeable closeable) {
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
        /**
         * The default socket timeout in milliseconds
         */
        public static final int DEFAULT_TIMEOUT_MS = 10000;

        private String url;
        private int method = Method.GET;
        private Map<String, String> header;
        private byte[] body;
        private int timeoutMs = DEFAULT_TIMEOUT_MS;

        public void setUrl(String url) {
            this.url = url;
        }

        public void setMethod(int method) {
            this.method = method;
        }

        public void setHeader(Map<String, String> header) {
            this.header = header;
        }

        public void setBody(byte[] body) {
            this.body = body;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
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
        private final int contentLength;
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
        public HttpResponse(int statusCode, Map<String, List<String>> headers, int contentLength, InputStream content) {
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
        public final int getContentLength() {
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
            inputStream.close();
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

    static class UrlConnectionInputStream extends FilterInputStream {
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
}
