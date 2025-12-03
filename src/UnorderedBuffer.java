import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class UnorderedBuffer implements CSProcess {
    private final int id;
    private final One2OneChannel inputChannel;
    private final One2OneChannel readyChannel;
    private final Any2AnyChannel outputChannel;
    private final AtomicBoolean done;
    private final boolean print;
    
    public UnorderedBuffer(int id, One2OneChannel inputChannel, One2OneChannel readyChannel,
                            Any2AnyChannel outputChannel, AtomicBoolean done, boolean print) {
        this.id = id;
        this.inputChannel = inputChannel;
        this.readyChannel = readyChannel;
        this.outputChannel = outputChannel;
        this.done = done;
        this.print = print;
    }
    
    @Override
    public void run() {
        while (!done.get()) {
            readyChannel.out().write("READY");
            Integer item = (Integer) inputChannel.in().read();
            if (done.get()) break;
            
            outputChannel.out().write(item);
            if (print) System.out.println("Buffer[" + id + "]: " + item);
        }
        if (print) System.out.println("Buffer[" + id + "]: Done");
    }
}