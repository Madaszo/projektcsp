import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class OrderedConsumer implements CSProcess {
    private final int id;
    private final Any2AnyChannel inputChannel;
    private final int totalItems;
    private final AtomicInteger receivedCounter;
    private final AtomicBoolean done;
    private final boolean print;
    
    public OrderedConsumer(int id, Any2AnyChannel inputChannel, int totalItems,
                            AtomicInteger receivedCounter, AtomicBoolean done, boolean print) {
        this.id = id;
        this.inputChannel = inputChannel;
        this.totalItems = totalItems;
        this.receivedCounter = receivedCounter;
        this.done = done;
        this.print = print;
    }
    
    @Override
    public void run() {
        while (!done.get()) {
            Integer item = (Integer) inputChannel.in().read();
            if (done.get()) break;
            
            int count = receivedCounter.incrementAndGet();
            if (print) System.out.println("OrderedConsumer[" + id + "]: " + item + " (total: " + count + ")");
            
            if (count >= totalItems) {
                done.set(true);
                break;
            }
        }
        if (print) System.out.println("OrderedConsumer[" + id + "]: Done");
    }
}