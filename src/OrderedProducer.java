import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class OrderedProducer implements CSProcess {
    private final int id;
    private final Any2AnyChannel outputChannel;
    private final int totalItems;
    private final AtomicInteger itemCounter;
    private final AtomicBoolean done;
    private final boolean print;
    
    public OrderedProducer(int id, Any2AnyChannel outputChannel, int totalItems,
                            AtomicInteger itemCounter, AtomicBoolean done, boolean print) {
        this.id = id;
        this.outputChannel = outputChannel;
        this.totalItems = totalItems;
        this.itemCounter = itemCounter;
        this.done = done;
        this.print = print;
    }
    
    @Override
    public void run() {
        while (!done.get()) {
            int itemNum = itemCounter.getAndIncrement();
            
            if (itemNum >= totalItems) {
                if (print) System.out.println("OrderedProducer[" + id + "]: Done");
                break;
            }
            
            outputChannel.out().write(itemNum + 1);
            if (print) System.out.println("OrderedProducer[" + id + "]: " + (itemNum + 1));
        }
    }
}