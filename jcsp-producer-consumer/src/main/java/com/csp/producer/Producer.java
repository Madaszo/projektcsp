import jcsp.lang.*;
import jcsp.util.*;

class Producer implements CSProcess {
    private final ChannelOutput<Integer> out;
    private final int itemsToProduce;

    public Producer(ChannelOutput<Integer> out, int itemsToProduce) {
        this.out = out;
        this.itemsToProduce = itemsToProduce;
    }

    public void run() {
        for (int i = 0; i < itemsToProduce; i++) {
            out.write(i);
            System.out.println("Produced: " + i);
        }
    }
}

class Consumer implements CSProcess {
    private final ChannelInput<Integer> in;

    public Consumer(ChannelInput<Integer> in) {
        this.in = in;
    }

    public void run() {
        while (true) {
            int item = in.read();
            System.out.println("Consumed: " + item);
        }
    }
}

class Buffer implements CSProcess {
    private final ChannelInput<Integer> in;
    private final ChannelOutput<Integer> out;
    private final int size;
    private final int[] buffer;
    private int count = 0;

    public Buffer(ChannelInput<Integer> in, ChannelOutput<Integer> out, int size) {
        this.in = in;
        this.out = out;
        this.size = size;
        this.buffer = new int[size];
    }

    public void run() {
        while (true) {
            if (count < size) {
                int item = in.read();
                buffer[count++] = item;
                out.write(item);
            }
        }
    }
}

public class Main {
    public static void main(String[] args) {
        int bufferSize = 5;
        int itemsToProduce = 10;

        One2OneChannel<Integer> channel = Channel.one2one();

        CSProcess[] processes = {
            new Producer(channel.out(), itemsToProduce),
            new Buffer(channel.in(), channel.out(), bufferSize),
            new Consumer(channel.in())
        };

        Parallel parallel = new Parallel(processes);
        parallel.run();
    }
}