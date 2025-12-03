import jcsp.lang.*;
import jcsp.util.*;

class Producer implements CSProcess {
    private final ChannelOutput<Object> out;
    private final int itemsToProduce;

    public Producer(ChannelOutput<Object> out, int itemsToProduce) {
        this.out = out;
        this.itemsToProduce = itemsToProduce;
    }

    public void run() {
        for (int i = 0; i < itemsToProduce; i++) {
            out.write("Item " + i);
            System.out.println("Produced: Item " + i);
        }
    }
}

class Consumer implements CSProcess {
    private final ChannelInput<Object> in;

    public Consumer(ChannelInput<Object> in) {
        this.in = in;
    }

    public void run() {
        while (true) {
            Object item = in.read();
            System.out.println("Consumed: " + item);
        }
    }
}

class Buffer implements CSProcess {
    private final ChannelInput<Object> in;
    private final ChannelOutput<Object> out;
    private final int bufferSize;
    private final Object[] buffer;
    private int count = 0;

    public Buffer(ChannelInput<Object> in, ChannelOutput<Object> out, int bufferSize) {
        this.in = in;
        this.out = out;
        this.bufferSize = bufferSize;
        this.buffer = new Object[bufferSize];
    }

    public void run() {
        while (true) {
            if (count < bufferSize) {
                Object item = in.read();
                buffer[count++] = item;
                out.write(item);
            }
        }
    }
}

public class Main {
    public static void main(String[] args) {
        final int BUFFER_SIZE = 5;
        final int ITEMS_TO_PRODUCE = 10;

        One2OneChannel<Object> channel = Channel.one2one();

        CSProcess[] processes = {
            new Buffer(channel.in(), channel.out()),
            new Producer(channel.out(), ITEMS_TO_PRODUCE),
            new Consumer(channel.in())
        };

        Parallel parallel = new Parallel(processes);
        parallel.run();
    }
}