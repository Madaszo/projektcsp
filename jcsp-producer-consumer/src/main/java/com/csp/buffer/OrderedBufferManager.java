import jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Buffer implements CSProcess {
    private final AltingChannelInput<Object> in;
    private final AltingChannelOutput<Object> out;
    private final Object[] buffer;
    private final int size;
    private final AtomicInteger count = new AtomicInteger(0);
    private int inIndex = 0;
    private int outIndex = 0;

    public Buffer(AltingChannelInput<Object> in, AltingChannelOutput<Object> out, int size) {
        this.in = in;
        this.out = out;
        this.size = size;
        this.buffer = new Object[size];
    }

    public void run() {
        while (true) {
            // Wait for a producer to produce an item
            Object item = in.read();
            buffer[inIndex] = item;
            inIndex = (inIndex + 1) % size;
            count.incrementAndGet();
            // Notify a consumer
            out.write(item);
        }
    }
}