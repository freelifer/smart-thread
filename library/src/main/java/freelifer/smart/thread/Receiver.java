package freelifer.smart.thread;

/**
 * @author Ziv on 2021/9/30.
 */
public interface Receiver<T> {

    void run(T t);
}
