import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OrderedBuffer implements CSProcess {
    private final int id;
    private final ChannelInput inputChannel;
    private final ChannelOutput outputChannel;
    private final AtomicBoolean done;
    private final boolean print;
    
    public OrderedBuffer(int id, ChannelInput inputChannel, ChannelOutput outputChannel,
                          AtomicBoolean done, boolean print) {
        this.id = id;
        this.inputChannel = inputChannel;
        this.outputChannel = outputChannel;
        this.done = done;
        this.print = print;
    }
    
    @Override
    public void run() {
        while (!done.get()) {
            Object obj = inputChannel.read();
            if (done.get()) break;
            
            outputChannel.write(obj);
            if (print) System.out.println("OrderedBuffer[" + id + "]: " + obj);
        }
        if (print) System.out.println("OrderedBuffer[" + id + "]: Done");
    }
}