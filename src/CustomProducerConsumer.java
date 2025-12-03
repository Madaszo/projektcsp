import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomProducerConsumer implements CSProcess {
    private final int bufferSize;
    private final int itemCount;
    private final int numProducers;
    private final int numConsumers;
    private final boolean print;
    
    public CustomProducerConsumer(int bufferSize, int itemCount, int numProducers, 
                                   int numConsumers, boolean print) {
        this.bufferSize = bufferSize;
        this.itemCount = itemCount;
        this.numProducers = numProducers;
        this.numConsumers = numConsumers;
        this.print = print;
    }
    
    @Override
    public void run() {
        // Wspólny kanał producent -> bufor (Any2Any)
        Any2AnyChannel toBufferChannel = Channel.any2any();
        // Wspólny kanał bufor -> konsument (Any2Any)  
        Any2AnyChannel fromBufferChannel = Channel.any2any();
        
        // Kanały do dispatchera producentów
        Any2OneChannel producerDispatcherRequest = Channel.any2one();
        One2OneChannel[] producerResponseChannels = new One2OneChannel[numProducers];
        for (int i = 0; i < numProducers; i++) {
            producerResponseChannels[i] = Channel.one2one();
        }
        
        // Kanały do dispatchera konsumentów
        Any2OneChannel consumerDispatcherRequest = Channel.any2one();
        One2OneChannel[] consumerResponseChannels = new One2OneChannel[numConsumers];
        for (int i = 0; i < numConsumers; i++) {
            consumerResponseChannels[i] = Channel.one2one();
        }
        
        // Liczniki atomowe
        AtomicInteger itemCounter = new AtomicInteger(0);
        AtomicInteger receivedCounter = new AtomicInteger(0);
        AtomicBoolean done = new AtomicBoolean(false);
        
        // Procesy: P producentów + K konsumentów + N buforów + 2 dispatchery
        CSProcess[] processes = new CSProcess[numProducers + numConsumers + bufferSize + 2];
        int idx = 0;
        
        // Producenci
        for (int p = 0; p < numProducers; p++) {
            processes[idx++] = new LocalWeightProducer(p, toBufferChannel, 
                producerDispatcherRequest, producerResponseChannels[p],
                bufferSize, itemCount, itemCounter, done, print);
        }
        
        // Konsumenci
        for (int c = 0; c < numConsumers; c++) {
            processes[idx++] = new LocalWeightConsumer(c, fromBufferChannel,
                consumerDispatcherRequest, consumerResponseChannels[c],
                bufferSize, itemCount, receivedCounter, done, print);
        }
        
        // Bufory
        for (int i = 0; i < bufferSize; i++) {
            processes[idx++] = new SimpleBuffer(i, toBufferChannel, 
                fromBufferChannel, done, print);
        }
        
        // Dispatcher dla producentów
        processes[idx++] = new WeightDispatcher("Producer", producerDispatcherRequest, 
            producerResponseChannels, bufferSize, numProducers, done, print);
        
        // Dispatcher dla konsumentów
        processes[idx++] = new WeightDispatcher("Consumer", consumerDispatcherRequest,
            consumerResponseChannels, bufferSize, numConsumers, done, print);
        
        new Parallel(processes).run();
    }
}

// ========== WIADOMOŚCI ==========

class WeightRequest {
    final int processId;
    final int lastBufferUsed;
    final long waitTimeNanos;
    final boolean isTermination;
    
    public WeightRequest(int processId, int lastBufferUsed, long waitTimeNanos) {
        this.processId = processId;
        this.lastBufferUsed = lastBufferUsed;
        this.waitTimeNanos = waitTimeNanos;
        this.isTermination = false;
    }
    
    public WeightRequest(int processId) {
        this.processId = processId;
        this.lastBufferUsed = -1;
        this.waitTimeNanos = 0;
        this.isTermination = true;
    }
}

class WeightResponse {
    final double[] weights;
    
    public WeightResponse(double[] weights) {
        this.weights = weights.clone();
    }
}

// ========== PRODUCENT Z LOKALNĄ TABLICĄ WAG ==========

class LocalWeightProducer implements CSProcess {
    private final int id;
    private final Any2AnyChannel toBufferChannel;
    private final Any2OneChannel dispatcherRequest;
    private final One2OneChannel myResponseChannel;
    private final int bufferSize;
    private final int totalItems;
    private final AtomicInteger itemCounter;
    private final AtomicBoolean done;
    private final boolean print;
    private final java.util.Random random;
    
    private double[] localWeights;
    private static final int UPDATE_FREQUENCY = 10;
    
    public LocalWeightProducer(int id, Any2AnyChannel toBufferChannel,
                                Any2OneChannel dispatcherRequest,
                                One2OneChannel myResponseChannel,
                                int bufferSize, int totalItems, 
                                AtomicInteger itemCounter,
                                AtomicBoolean done, boolean print) {
        this.id = id;
        this.toBufferChannel = toBufferChannel;
        this.dispatcherRequest = dispatcherRequest;
        this.myResponseChannel = myResponseChannel;
        this.bufferSize = bufferSize;
        this.totalItems = totalItems;
        this.itemCounter = itemCounter;
        this.done = done;
        this.print = print;
        this.random = new java.util.Random(id * 1000 + System.nanoTime());
        
        this.localWeights = new double[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            localWeights[i] = 1.0;
        }
    }
    
    @Override
    public void run() {
        int localCount = 0;
        int lastBufferUsed = 0;
        long lastWaitTime = 0;
        
        while (!done.get()) {
            int itemNum = itemCounter.getAndIncrement();
            
            if (itemNum >= totalItems) {
                // Powiadom dispatcher o zakończeniu
                dispatcherRequest.out().write(new WeightRequest(id));
                if (print) System.out.println("Producer[" + id + "]: Finished");
                break;
            }
            
            localCount++;
            
            // Co UPDATE_FREQUENCY elementów - idź do dispatchera po nowe wagi
            if (localCount % UPDATE_FREQUENCY == 0) {
                dispatcherRequest.out().write(new WeightRequest(id, lastBufferUsed, lastWaitTime));
                WeightResponse response = (WeightResponse) myResponseChannel.in().read();
                localWeights = response.weights;
                
                if (print) System.out.println("Producer[" + id + "]: Updated weights");
            }
            
            // Wybierz "logiczny bufor" na podstawie wag
            int bufferIndex = selectByWeight();
            lastBufferUsed = bufferIndex;
            
            // Wyślij do wspólnego kanału
            long startTime = System.nanoTime();
            toBufferChannel.out().write(itemNum + 1);
            lastWaitTime = System.nanoTime() - startTime;
            
            if (print) System.out.println("Producer[" + id + "]: " + (itemNum + 1));
        }
    }
    
    private int selectByWeight() {
        double totalWeight = 0;
        for (double w : localWeights) totalWeight += w;
        
        double r = random.nextDouble() * totalWeight;
        double cumulative = 0;
        
        for (int i = 0; i < localWeights.length; i++) {
            cumulative += localWeights[i];
            if (r <= cumulative) {
                return i;
            }
        }
        return localWeights.length - 1;
    }
}

// ========== KONSUMENT Z LOKALNĄ TABLICĄ WAG ==========

class LocalWeightConsumer implements CSProcess {
    private final int id;
    private final Any2AnyChannel fromBufferChannel;
    private final Any2OneChannel dispatcherRequest;
    private final One2OneChannel myResponseChannel;
    private final int bufferSize;
    private final int totalItems;
    private final AtomicInteger receivedCounter;
    private final AtomicBoolean done;
    private final boolean print;
    private final java.util.Random random;
    
    private double[] localWeights;
    private static final int UPDATE_FREQUENCY = 10;
    
    public LocalWeightConsumer(int id, Any2AnyChannel fromBufferChannel,
                                Any2OneChannel dispatcherRequest,
                                One2OneChannel myResponseChannel,
                                int bufferSize, int totalItems,
                                AtomicInteger receivedCounter,
                                AtomicBoolean done, boolean print) {
        this.id = id;
        this.fromBufferChannel = fromBufferChannel;
        this.dispatcherRequest = dispatcherRequest;
        this.myResponseChannel = myResponseChannel;
        this.bufferSize = bufferSize;
        this.totalItems = totalItems;
        this.receivedCounter = receivedCounter;
        this.done = done;
        this.print = print;
        this.random = new java.util.Random(id * 2000 + System.nanoTime());
        
        this.localWeights = new double[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            localWeights[i] = 1.0;
        }
    }
    
    @Override
    public void run() {
        int localCount = 0;
        int lastBufferUsed = 0;
        long lastWaitTime = 0;
        
        while (!done.get()) {
            // Sprawdź czy już skonsumowaliśmy wszystko
            int currentReceived = receivedCounter.get();
            if (currentReceived >= totalItems) {
                done.set(true);
                dispatcherRequest.out().write(new WeightRequest(id));
                if (print) System.out.println("Consumer[" + id + "]: All items consumed, done");
                break;
            }
            
            localCount++;
            
            // Co UPDATE_FREQUENCY elementów - idź do dispatchera po nowe wagi
            if (localCount % UPDATE_FREQUENCY == 0) {
                dispatcherRequest.out().write(new WeightRequest(id, lastBufferUsed, lastWaitTime));
                WeightResponse response = (WeightResponse) myResponseChannel.in().read();
                localWeights = response.weights;
                
                if (print) System.out.println("Consumer[" + id + "]: Updated weights");
            }
            
            // Wybierz "logiczny bufor"
            int bufferIndex = selectByWeight();
            lastBufferUsed = bufferIndex;
            
            // Odbierz ze wspólnego kanału
            long startTime = System.nanoTime();
            Object obj = fromBufferChannel.in().read();
            lastWaitTime = System.nanoTime() - startTime;
            
            if (obj == null) continue;
            Integer item = (Integer) obj;
            
            int count = receivedCounter.incrementAndGet();
            if (print) System.out.println("Consumer[" + id + "]: " + item + " (total: " + count + ")");
            
            // Sprawdź czy to ostatni element
            if (count >= totalItems) {
                done.set(true);
                dispatcherRequest.out().write(new WeightRequest(id));
                if (print) System.out.println("Consumer[" + id + "]: Last item consumed, done");
                break;
            }
        }
        
        // Powiadom dispatcher jeśli jeszcze nie
        if (!done.get()) {
            dispatcherRequest.out().write(new WeightRequest(id));
        }
        if (print) System.out.println("Consumer[" + id + "]: Exiting");
    }
    
    private int selectByWeight() {
        double totalWeight = 0;
        for (double w : localWeights) totalWeight += w;
        
        double r = random.nextDouble() * totalWeight;
        double cumulative = 0;
        
        for (int i = 0; i < localWeights.length; i++) {
            cumulative += localWeights[i];
            if (r <= cumulative) {
                return i;
            }
        }
        return localWeights.length - 1;
    }
}

// ========== BUFOR ==========

class SimpleBuffer implements CSProcess {
    private final int id;
    private final Any2AnyChannel inputChannel;
    private final Any2AnyChannel outputChannel;
    private final AtomicBoolean done;
    private final boolean print;
    
    public SimpleBuffer(int id, Any2AnyChannel inputChannel, 
                         Any2AnyChannel outputChannel, AtomicBoolean done, boolean print) {
        this.id = id;
        this.inputChannel = inputChannel;
        this.outputChannel = outputChannel;
        this.done = done;
        this.print = print;
    }
    
    @Override
    public void run() {
        while (!done.get()) {
            Object obj = inputChannel.in().read();
            if (obj == null || done.get()) break;
            
            Integer item = (Integer) obj;
            outputChannel.out().write(item);
            
            if (print) System.out.println("Buffer[" + id + "]: " + item);
        }
        if (print) System.out.println("Buffer[" + id + "]: Done");
    }
}

// ========== DISPATCHER WAG ==========

class WeightDispatcher implements CSProcess {
    private final String name;
    private final Any2OneChannel requestChannel;
    private final One2OneChannel[] responseChannels;
    private final int bufferSize;
    private final int numClients;
    private final AtomicBoolean done;
    private final boolean print;
    
    private final double[] globalWeights;
    
    public WeightDispatcher(String name, Any2OneChannel requestChannel,
                             One2OneChannel[] responseChannels,
                             int bufferSize, int numClients, 
                             AtomicBoolean done, boolean print) {
        this.name = name;
        this.requestChannel = requestChannel;
        this.responseChannels = responseChannels;
        this.bufferSize = bufferSize;
        this.numClients = numClients;
        this.done = done;
        this.print = print;
        
        this.globalWeights = new double[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            globalWeights[i] = 1.0;
        }
    }
    
    @Override
    public void run() {
        int terminationCount = 0;
        
        while (terminationCount < numClients && !done.get()) {
            WeightRequest request = (WeightRequest) requestChannel.in().read();
            
            if (request.isTermination) {
                terminationCount++;
                if (print) System.out.println(name + "Dispatcher: Term " + 
                    request.processId + " (" + terminationCount + "/" + numClients + ")");
            } else {
                // Aktualizuj wagę
                double newWeight = 1.0 / (1.0 + request.waitTimeNanos / 1_000_000.0);
                updateWeight(request.lastBufferUsed, newWeight);
                
                if (print) System.out.println(name + "Dispatcher: Update from " + request.processId);
                
                // Wyślij wagi do klienta
                responseChannels[request.processId].out().write(new WeightResponse(globalWeights));
            }
        }
        
        if (print) System.out.println(name + "Dispatcher: Done");
    }
    
    private void updateWeight(int index, double newWeight) {
        globalWeights[index] = 0.7 * globalWeights[index] + 0.3 * newWeight;
        
        double sum = 0;
        for (double w : globalWeights) sum += w;
        if (sum > 0) {
            for (int i = 0; i < bufferSize; i++) {
                globalWeights[i] = globalWeights[i] / sum * bufferSize;
            }
        }
    }
}