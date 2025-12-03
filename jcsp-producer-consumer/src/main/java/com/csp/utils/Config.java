import jcsp.lang.*;
import jcsp.util.*;

public class Buffer {
    private final AltingChannelInput<Object>[] inputs;
    private final AltingChannelOutput<Object>[] outputs;
    private final Object[] buffer;
    private int count = 0;
    private final int size;

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
        while (count == size) {
            // Wait for space in the buffer
        }
        buffer[count] = item;
        outputs[count].write(item);
        count++;
    }

    public Object consume() {
        while (count == 0) {
            // Wait for items in the buffer
        }
        count--;
        return inputs[count].read();
    }
}