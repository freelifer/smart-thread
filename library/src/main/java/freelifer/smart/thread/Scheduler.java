package freelifer.smart.thread;

import java.util.concurrent.ExecutorService;

/**
 * 创建Observer
 * @author Ziv on 2021/9/30.
 */
public class Scheduler<T> {

    private Receiver<T> thenReceiver = null;
    private ExecutorService executorService = Threads.io();
    private IoRunnable<T> ioRunnable = null;

    public static Scheduler of() {
        return new Scheduler();
    }



    public Scheduler<T> setIoRunnable(IoRunnable<T> runnable) {
        this.ioRunnable = runnable;
        return this;
    }

    public Scheduler<T> then(Receiver<T> runnable) {
        this.thenReceiver = runnable;
        return this;
    }

    public void start() {
        if (ioRunnable == null) {
            throw new NullPointerException("Scheduler ioRunnable == null");
        }
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                T t = ioRunnable.run();
                if (thenReceiver != null) {
                    thenReceiver.run(t);
                }
            }
        });
    }

}
