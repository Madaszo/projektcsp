import jcsp.lang.*;
import java.util.Random;

class Producer implements CSProcess {
    private final ChannelOutput<Object> out;
    private final int itemsToProduce;

    public Producer(ChannelOutput<Object> out, int itemsToProduce) {
        this.out = out;
        this.itemsToProduce = itemsToProduce;
    }

    public void run() {
        Random rand = new Random();
        for (int i = 0; i < itemsToProduce; i++) {
            Object item = rand.nextInt(100); // Produce a random item
            out.write(item);
            System.out.println("Produced: " + item);
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
                System.out.println("Buffer: " + count + " items");
            } else {
                out.write(buffer[--count]); // Consume from buffer
            }
        }
    }
}

public class ProducerConsumerScenarioA {
    public static void main(String[] args) {
        final int BUFFER_SIZE = 5;
        final int ITEMS_TO_PRODUCE = 20;

        One2OneChannel<Object> channel = Channel.one2one();

        CSProcess[] processes = {
            new Producer(channel.out(), ITEMS_TO_PRODUCE),
            new Consumer(channel.in()),
            new Buffer(channel.in(), channel.out(), BUFFER_SIZE)
        };

        Parallel parallel = new Parallel(processes);
        parallel.run();
    }
}