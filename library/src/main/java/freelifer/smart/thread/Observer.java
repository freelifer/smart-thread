package freelifer.smart.thread;

import java.util.concurrent.ExecutorService;

/**
 * @author Ziv on 2021/12/15.
 */
public abstract class Observer<T> {
    private Observable<T> observable;
    private Error error;
    private ExecutorService executorService = Threads.io();

    /**
     * 耗时处理逻辑
     *
     * @return 数据
     * @throws Exception
     */
    public abstract T run() throws Exception;

    public void setObservable(Observable<T> observable) {
        this.observable = observable;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public void start() {
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    T t = Observer.this.run();
                    if (Observer.this.observable != null) {
                        Observer.this.observable.run(t);
                    }
                } catch (Exception e) {
                    if (error != null) {
                        error.run(e);
                    }
                }
            }
        });
    }

}
