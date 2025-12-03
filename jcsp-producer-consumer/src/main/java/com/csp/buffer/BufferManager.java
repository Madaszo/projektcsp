import jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Buffer {
    private final AltingChannelInput<Object>[] inputs;
    private final AltingChannelOutput<Object>[] outputs;
    private final Object[] buffer;
    private final int size;
    private final AtomicInteger count = new AtomicInteger(0);
    private int in = 0;
    private int out = 0;

    public Buffer(int size) {
        this.size = size;
        this.buffer = new Object[size];
        this.inputs = new AltingChannelInput[size];
        this.outputs = new AltingChannelOutput[size];
        for (int i = 0; i < size; i++) {
            Channel<Object> channel = Channel.one2one();
            inputs[i] = channel.in();
            outputs[i] = channel.out();
        }
    }

    public void produce(Object item) {
        while (count.get() == size) {
            // Wait until there is space in the buffer
        }
        buffer[in] = item;
        outputs[in].write(item);
        in = (in + 1) % size;
        count.incrementAndGet();
    }

    public Object consume() {
        while (count.get() == 0) {
            // Wait until there is an item to consume
        }
        Object item = inputs[out].read();
        out = (out + 1) % size;
        count.decrementAndGet();
        return item;
    }
}