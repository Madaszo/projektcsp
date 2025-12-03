import jcsp.lang.*;

class Producer implements CSProcess {
    private final AltingChannelOutput<Object> out;
    private final int itemsToProduce;

    public Producer(AltingChannelOutput<Object> out, int itemsToProduce) {
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
    private final AltingChannelInput<Object> in;

    public Consumer(AltingChannelInput<Object> in) {
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
    private final AltingChannelInput<Object> in;
    private final AltingChannelOutput<Object> out;
    private final Object[] buffer;
    private int count = 0;
    private int inIndex = 0;

    public Buffer(AltingChannelInput<Object> in, AltingChannelOutput<Object> out, int size) {
        this.in = in;
        this.out = out;
        this.buffer = new Object[size];
    }

    public void run() {
        while (true) {
            Object item = in.read();
            buffer[inIndex] = item;
            inIndex = (inIndex + 1) % buffer.length;
            count++;
            if (count > 0) {
                out.write(buffer[(inIndex - count + buffer.length) % buffer.length]);
                count--;
            }
        }
    }
}