# smart http client

非常简单http请求客户端，可以直接copy代码到项目中，没有任何其他库的依赖

* 同步接口，需要自己实现线程池

* HttpClient 纯HttpURLConnection实现
* HttpOrOkHttpClient 兼容OkHttp实现，优先使用OkHttp，如果没有，则使用android系统的HttpURLConnection



支持http方法

* get请求
* post请求



# 开始使用

例子

```kotlin
Thread {
    Log.i("xxxx", "http start")
    val req = HttpOrOkHttpClient.HttpRequest()
    req.url = "http://ip-api.com/json?lang=zh-CN"

    val client = HttpOrOkHttpClient.of()
  			// 统计数据接口：上传记录http的响应时间
        .setStatistics { url, code, time ->
            Log.i("xxxx", "http setStatistics $url $code $time")
        }
    val response = client.execute(req).runCatching {
        Log.i("xxxx", "http execute body")
        body().string()
    }.toString()
    Log.i("xxxx", "http result $response")
}.start()
```



### HttpRequest对象

```java
public static class HttpOrOkHttpClient.HttpRequest {

  // 设置http请求url
  void setUrl(String url);

  // 设置http的请求方法 默认GET
  void setMethod(int method);

  // 设置http的请求头
  void setHeader(Map<String, String> header);

  // 设置http的请求体
  void setBody(byte[] body);

  // 设置http的超时时间 默认10s
  void setTimeoutMs(int timeoutMs);
}
```



### Method对象

http的方法对象

```java
public interface HttpOrOkHttpClient.Method {
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
```



### HttpResponse对象

HttpResponse里面含有body的InputStream对象 请一定要close 防止泄露

```java
// http状态码 或 -1(系统Http Exception)
int getStatusCode();

// body 内容 InputStream对象
InputStream getContent()；
  
// body 内容 HttpResponseBody对象
HttpResponseBody body()

public static class HttpResponseBody implements Closeable {
  // 返回String body内容 注意：此接口会关闭InputStream
  String string() throws IOException;
  
  // 返回byte[] body内容 注意：此接口会关闭InputStream
  byte[] bytes() throws IOException;
  
	// 关闭body InputStream流
  void close() throws IOException
}

```

