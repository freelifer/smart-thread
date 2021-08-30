package freelifer.smart.http

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread {
            Log.i("xxxx", "http start")
            val req = HttpOrOkHttpClient.HttpRequest()
            req.url = "http://ip-api.com/json?lang=zh-CN"

            val client = HttpOrOkHttpClient.of()
                .setStatistics { url, code, time ->
                    Log.i("xxxx", "http statistics $url $code $time")
                }
            val response = client.execute(req).runCatching {
                Log.i("xxxx", "http execute body")
                body().string()
            }.toString()
            Log.i("xxxx", "http result $response")
        }.start()
    }
}