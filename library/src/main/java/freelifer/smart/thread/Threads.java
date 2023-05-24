package freelifer.smart.thread;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Ziv on 2021/9/29.
 */
public class Threads {

    private static ExecutorService io = null;

    public static ExecutorService io() {
        if (io == null) {
//            io = Executors.newCachedThreadPool(new DefaultThreadFactory());
//            Executors.newFixedThreadPool(10, new DefaultThreadFactory());
            io = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new DefaultThreadFactory());
        }
        return io;
    }

    private static final class DefaultThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final ThreadGroup group;

        DefaultThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "smart-io-pool-thread-";
        }

        @Override
        public java.lang.Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != java.lang.Thread.NORM_PRIORITY) {
                t.setPriority(java.lang.Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
