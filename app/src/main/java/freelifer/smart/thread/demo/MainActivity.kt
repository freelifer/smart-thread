package freelifer.smart.thread.demo

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import freelifer.smart.thread.Scheduler
import freelifer.smart.thread.Threads
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Thread {
            while (true) {
                Thread.sleep(500)
                Log.i("kzhu", "pool size.... ${Threads.io()}")
            }
        }.start()

        Scheduler.of()
            .setIoRunnable {
                Log.i("kzhu", "xxx run start.... ${java.lang.Thread.currentThread().id}")
                java.lang.Thread.sleep(2000)
                11
            }
            .then { t -> Log.i("kzhu", "xxxxxxx.... $t ${java.lang.Thread.currentThread().id}") }
            .start()

        Thread {
            val a = Threads.io()
            a.execute {
                Log.i("kzhu", "first run start.... ${java.lang.Thread.currentThread().id}")
                java.lang.Thread.sleep(2000)
                Log.i("kzhu", "first run end....${java.lang.Thread.currentThread().id}")
            }
            a.execute {
                Log.i("kzhu", "second run start....${java.lang.Thread.currentThread().id}")
                java.lang.Thread.sleep(4000)
                Log.i("kzhu", "second run end....${java.lang.Thread.currentThread().id}")
            }
            java.lang.Thread.sleep(3000)

            a.execute {
                Log.i("kzhu", "third run start.... ${java.lang.Thread.currentThread().id}")
                java.lang.Thread.sleep(2000)
                Log.i("kzhu", "third run end....${java.lang.Thread.currentThread().id}")
            }
        }.start()

        val executor = Executors.newFixedThreadPool(2);
        val synchronousQueue = SynchronousQueue<Object>();

        val producer = {
            while (true) {
                val obj = Object();
                try {
                    val tmp = synchronousQueue.offer(obj)
                    Log.i("kzhu", "producer....${tmp}")
                } catch (ex: InterruptedException) {
                    Log.i("kzhu", "producer....${ex}")
                }
                java.lang.Thread.sleep(2000)
            }
        }

        val consumer = {
            while (true) {
                try {
                    val obj = synchronousQueue.take();
                    Log.i("kzhu", "consumer....${obj}")
                } catch (ex: InterruptedException) {
                }
            }
        }

        executor.submit(producer);
        executor.submit(consumer);
        executor.awaitTermination(50000, TimeUnit.MILLISECONDS)
    }
}