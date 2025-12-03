import jcsp.lang.*;
import jcsp.util.*;

public class Buffer implements CSProcess {
    private final AltingChannelInput<Object> input;
    private final AltingChannelOutput<Object> output;
    private final Object[] buffer;
    private int count = 0;
    private final int size;

    public Buffer(AltingChannelInput<Object> input, AltingChannelOutput<Object> output, int size) {
        this.input = input;
        this.output = output;
        this.size = size;
        this.buffer = new Object[size];
    }

    public void run() {
        while (true) {
            Object item = input.read();
            if (count < size) {
                buffer[count++] = item; // Add item to buffer
            }
            if (count > 0) {
                output.write(buffer[--count]); // Remove item from buffer
            }
        }
    }
}