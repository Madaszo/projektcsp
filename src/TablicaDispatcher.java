import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * System Producent-Konsument z tablicami wag i dispatcherami.
 * Działa przez określony czas przy użyciu timeoutów.
 */
public class TablicaDispatcher implements CSProcess {
    
    private final int numProducers;
    private final int numBuffers;
    private final int numConsumers;
    private final int durationSeconds;
    private final boolean print;
    private final AtomicInteger[] bufferStats;
    private final AtomicBoolean stopFlag;
    private final AtomicInteger totalProduced;
    private final AtomicInteger totalConsumed;
    private final long stopTimeMillis;
    
    public TablicaDispatcher(int numProducers, int numBuffers, int numConsumers, 
                              int durationSeconds, boolean print) {
        this(numProducers, numBuffers, numConsumers, durationSeconds, print, 
             null, new AtomicBoolean(false), new AtomicInteger(0), new AtomicInteger(0));
    }
    
    public TablicaDispatcher(int numProducers, int numBuffers, int numConsumers, 
                              int durationSeconds, boolean print, AtomicInteger[] bufferStats,
                              AtomicBoolean stopFlag, AtomicInteger totalProduced, 
                              AtomicInteger totalConsumed) {
        this.numProducers = numProducers;
        this.numBuffers = numBuffers;
        this.numConsumers = numConsumers;
        this.durationSeconds = durationSeconds;
        this.print = print;
        this.bufferStats = bufferStats;
        this.stopFlag = stopFlag;
        this.totalProduced = totalProduced;
        this.totalConsumed = totalConsumed;
        this.stopTimeMillis = System.currentTimeMillis() + (durationSeconds * 1000L);
    }
    
    @Override
    public void run() {
        // Użyjemy ArrayBlockingQueue zamiast kanałów JCSP dla lepszej kontroli timeoutów
        ArrayBlockingQueue<Integer>[] bufferQueues = new ArrayBlockingQueue[numBuffers];
        for (int i = 0; i < numBuffers; i++) {
            bufferQueues[i] = new ArrayBlockingQueue<>(1000);
        }
        
        // Kanały do dispatcherów (można zostawić JCSP)
        Any2OneChannel producerDispatcherIn = Channel.any2one();
        One2OneChannel[] producerDispatcherOut = new One2OneChannel[numProducers];
        for (int i = 0; i < numProducers; i++) {
            producerDispatcherOut[i] = Channel.one2one();
        }
        
        Any2OneChannel consumerDispatcherIn = Channel.any2one();
        One2OneChannel[] consumerDispatcherOut = new One2OneChannel[numConsumers];
        for (int i = 0; i < numConsumers; i++) {
            consumerDispatcherOut[i] = Channel.one2one();
        }
        
        AtomicInteger itemCounter = new AtomicInteger(0);
        AtomicInteger activeProducers = new AtomicInteger(numProducers);
        AtomicInteger activeConsumers = new AtomicInteger(numConsumers);
        
        CSProcess[] processes = new CSProcess[numProducers + numConsumers + 2];
        int idx = 0;
        
        // --- PRODUCENCI ---
        for (int p = 0; p < numProducers; p++) {
            final int pid = p;
            final One2OneChannel myDispatcherResponse = producerDispatcherOut[p];
            final ArrayBlockingQueue<Integer>[] queues = bufferQueues;
            final long endTime = stopTimeMillis;
            
            processes[idx++] = () -> {
                Random rand = new Random(pid * 1000 + System.nanoTime());
                CSTimer timer = new CSTimer();
                
                double[] weights = new double[numBuffers + 1];
                long[] waitTimes = new long[numBuffers + 1];
                
                for (int i = 0; i <= numBuffers; i++) {
                    weights[i] = 1.0;
                    waitTimes[i] = 0;
                }
                
                while (System.currentTimeMillis() < endTime) {
                    int itemNum = itemCounter.getAndIncrement();
                    int target = selectByWeight(weights, rand);
                    
                    if (target == numBuffers) {
                        // === DISPATCHER ===
                        try {
                            long startTime = System.nanoTime();
                            
                            producerDispatcherIn.out().write(
                                new WeightTableRequest(pid, waitTimes.clone(), false));
                            
                            // Timeout na odpowiedź
                            Alternative alt = new Alternative(new Guard[]{
                                myDispatcherResponse.in(),
                                timer
                            });
                            
                            long timeout = timer.read() + 100; // 100ms timeout
                            timer.setAlarm(timeout);
                            
                            int selected = alt.priSelect();
                            if (selected == 0) {
                                WeightTableResponse response = 
                                    (WeightTableResponse) myDispatcherResponse.in().read();
                                
                                long elapsed = System.nanoTime() - startTime;
                                waitTimes[numBuffers] = elapsed;
                                
                                updateWeightsFromResponse(weights, response.aggregatedWaitTimes);
                                
                                if (print) System.out.println("Producer[" + pid + "]: Synced");
                            } else {
                                // Timeout - pomiń synchronizację
                                if (print) System.out.println("Producer[" + pid + "]: Sync timeout");
                            }
                            
                        } catch (Exception e) {
                            // Ignoruj błędy synchronizacji
                        }
                        
                    } else {
                        // === BUFOR ===
                        long startTime = System.nanoTime();
                        
                        try {
                            boolean success = queues[target].offer(itemNum + 1, 50, 
                                java.util.concurrent.TimeUnit.MILLISECONDS);
                            
                            if (success) {
                                totalProduced.incrementAndGet();
                                long elapsed = System.nanoTime() - startTime;
                                waitTimes[target] = elapsed;
                                updateWeight(weights, target, elapsed);
                                
                                if (print) System.out.println("Producer[" + pid + "]: " + 
                                    (itemNum + 1) + " -> Buffer[" + target + "]");
                            } else {
                                // Timeout - zwiększ wagę innych buforów
                                weights[target] *= 0.9;
                            }
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    
                    // Sprawdź czas
                    if (System.currentTimeMillis() >= endTime) break;
                }
                
                // Zakończenie
                int remaining = activeProducers.decrementAndGet();
                if (remaining == 0) {
                    // Ostatni producent wysyła POISON
                    for (int b = 0; b < numBuffers; b++) {
                        try {
                            queues[b].offer(-1, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    if (print) System.out.println("Producer[" + pid + "]: Sent POISON");
                }
                
                try {
                    producerDispatcherIn.out().write(new WeightTableRequest(pid, null, true));
                } catch (Exception e) {
                    // Ignoruj
                }
                
                if (print) System.out.println("Producer[" + pid + "]: Finished");
            };
        }
        
        // --- KONSUMENCI ---
        for (int c = 0; c < numConsumers; c++) {
            final int cid = c;
            final One2OneChannel myDispatcherResponse = consumerDispatcherOut[c];
            final ArrayBlockingQueue<Integer>[] queues = bufferQueues;
            final long endTime = stopTimeMillis;
            
            processes[idx++] = () -> {
                Random rand = new Random(cid * 2000 + System.nanoTime());
                CSTimer timer = new CSTimer();
                
                double[] weights = new double[numBuffers + 1];
                long[] waitTimes = new long[numBuffers + 1];
                boolean[] bufferAlive = new boolean[numBuffers];
                
                for (int i = 0; i <= numBuffers; i++) {
                    weights[i] = 1.0;
                    waitTimes[i] = 0;
                }
                for (int i = 0; i < numBuffers; i++) {
                    bufferAlive[i] = true;
                }
                
                int deadBuffers = 0;
                
                while (deadBuffers < numBuffers && activeProducers.get() > 0) {
                    int target = selectByWeightAlive(weights, rand, bufferAlive, numBuffers);
                    
                    if (target == numBuffers) {
                        // === DISPATCHER ===
                        try {
                            long startTime = System.nanoTime();
                            
                            consumerDispatcherIn.out().write(
                                new WeightTableRequest(cid, waitTimes.clone(), false));
                            
                            Alternative alt = new Alternative(new Guard[]{
                                myDispatcherResponse.in(),
                                timer
                            });
                            
                            long timeout = timer.read() + 100;
                            timer.setAlarm(timeout);
                            
                            int selected = alt.priSelect();
                            if (selected == 0) {
                                WeightTableResponse response = 
                                    (WeightTableResponse) myDispatcherResponse.in().read();
                                
                                long elapsed = System.nanoTime() - startTime;
                                waitTimes[numBuffers] = elapsed;
                                
                                updateWeightsFromResponse(weights, response.aggregatedWaitTimes);
                                
                                if (print) System.out.println("Consumer[" + cid + "]: Synced");
                            }
                        } catch (Exception e) {
                            // Ignoruj
                        }
                        
                    } else {
                        // === BUFOR ===
                        if (!bufferAlive[target]) continue;
                        
                        long startTime = System.nanoTime();
                        
                        try {
                            Integer item = queues[target].poll(50, 
                                java.util.concurrent.TimeUnit.MILLISECONDS);
                            
                            if (item != null) {
                                long elapsed = System.nanoTime() - startTime;
                                waitTimes[target] = elapsed;
                                
                                if (item == -1) {
                                    bufferAlive[target] = false;
                                    weights[target] = 0.0;
                                    deadBuffers++;
                                    
                                    if (bufferStats != null) {
                                        if (print) System.out.println("Consumer[" + cid + 
                                            "]: Buffer[" + target + "] done, total: " + 
                                            bufferStats[target].get());
                                    }
                                    
                                    if (print) System.out.println("Consumer[" + cid + 
                                        "]: POISON from Buffer[" + target + "]");
                                } else {
                                    totalConsumed.incrementAndGet();
                                    if (bufferStats != null) {
                                        bufferStats[target].incrementAndGet();
                                    }
                                    updateWeight(weights, target, elapsed);
                                    
                                    if (print) System.out.println("Consumer[" + cid + "]: " + 
                                        item + " <- Buffer[" + target + "]");
                                }
                            } else {
                                // Timeout - zmniejsz wagę
                                weights[target] *= 0.95;
                            }
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
                
                activeConsumers.decrementAndGet();
                
                try {
                    consumerDispatcherIn.out().write(new WeightTableRequest(cid, null, true));
                } catch (Exception e) {
                    // Ignoruj
                }
                
                if (print) System.out.println("Consumer[" + cid + "]: Finished");
            };
        }
        
        // --- DISPATCHER PRODUCENTÓW ---
        processes[idx++] = () -> {
            long[] aggregatedWaitTimes = new long[numBuffers + 1];
            int[] counts = new int[numBuffers + 1];
            
            int terminationCount = 0;
            CSTimer timer = new CSTimer();
            
            while (terminationCount < numProducers) {
                Alternative alt = new Alternative(new Guard[]{
                    producerDispatcherIn.in(),
                    timer
                });
                
                long timeout = timer.read() + 1000; // 1s timeout
                timer.setAlarm(timeout);
                
                int selected = alt.priSelect();
                if (selected == 1) {
                    // Timeout - sprawdź czy wszyscy producenci skończyli
                    if (activeProducers.get() == 0) break;
                    continue;
                }
                
                WeightTableRequest request = 
                    (WeightTableRequest) producerDispatcherIn.in().read();
                
                if (request.isTermination) {
                    terminationCount++;
                    if (print) System.out.println("ProducerDispatcher: Term " + 
                        request.processId + " (" + terminationCount + "/" + numProducers + ")");
                    continue;
                }
                
                if (request.waitTimes != null) {
                    for (int i = 0; i < request.waitTimes.length && i < numBuffers + 1; i++) {
                        if (request.waitTimes[i] > 0) {
                            aggregatedWaitTimes[i] = 
                                (aggregatedWaitTimes[i] * counts[i] + request.waitTimes[i]) 
                                / (counts[i] + 1);
                            counts[i]++;
                        }
                    }
                }
                
                try {
                    producerDispatcherOut[request.processId].out().write(
                        new WeightTableResponse(aggregatedWaitTimes.clone()));
                } catch (Exception e) {
                    // Ignoruj
                }
                
                if (print) System.out.println("ProducerDispatcher: Synced Producer[" + 
                    request.processId + "]");
            }
            
            if (print) System.out.println("ProducerDispatcher: Done");
        };
        
        // --- DISPATCHER KONSUMENTÓW ---
        processes[idx++] = () -> {
            long[] aggregatedWaitTimes = new long[numBuffers + 1];
            int[] counts = new int[numBuffers + 1];
            
            int terminationCount = 0;
            CSTimer timer = new CSTimer();
            
            while (terminationCount < numConsumers) {
                Alternative alt = new Alternative(new Guard[]{
                    consumerDispatcherIn.in(),
                    timer
                });
                
                long timeout = timer.read() + 1000;
                timer.setAlarm(timeout);
                
                int selected = alt.priSelect();
                if (selected == 1) {
                    // Timeout
                    if (activeConsumers.get() == 0) break;
                    continue;
                }
                
                WeightTableRequest request = 
                    (WeightTableRequest) consumerDispatcherIn.in().read();
                
                if (request.isTermination) {
                    terminationCount++;
                    if (print) System.out.println("ConsumerDispatcher: Term " + 
                        request.processId + " (" + terminationCount + "/" + numConsumers + ")");
                    continue;
                }
                
                if (request.waitTimes != null) {
                    for (int i = 0; i < request.waitTimes.length && i < numBuffers + 1; i++) {
                        if (request.waitTimes[i] > 0) {
                            aggregatedWaitTimes[i] = 
                                (aggregatedWaitTimes[i] * counts[i] + request.waitTimes[i]) 
                                / (counts[i] + 1);
                            counts[i]++;
                        }
                    }
                }
                
                try {
                    consumerDispatcherOut[request.processId].out().write(
                        new WeightTableResponse(aggregatedWaitTimes.clone()));
                } catch (Exception e) {
                    // Ignoruj
                }
                
                if (print) System.out.println("ConsumerDispatcher: Synced Consumer[" + 
                    request.processId + "]");
            }
            
            if (print) System.out.println("ConsumerDispatcher: Done");
        };
        
        new Parallel(processes).run();
        
        stopFlag.set(true);
    }
    
    // === METODY POMOCNICZE ===
    
    private static int selectByWeight(double[] weights, Random rand) {
        double total = 0;
        for (double w : weights) total += w;
        
        if (total <= 0) return 0;
        
        double r = rand.nextDouble() * total;
        double cumulative = 0;
        
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (r <= cumulative) {
                return i;
            }
        }
        return weights.length - 1;
    }
    
    private static int selectByWeightAlive(double[] weights, Random rand, 
                                            boolean[] alive, int numBuffers) {
        double total = 0;
        
        for (int i = 0; i < numBuffers; i++) {
            if (alive[i]) total += weights[i];
        }
        total += weights[numBuffers];
        
        if (total <= 0) return numBuffers;
        
        double r = rand.nextDouble() * total;
        double cumulative = 0;
        
        for (int i = 0; i < numBuffers; i++) {
            if (alive[i]) {
                cumulative += weights[i];
                if (r <= cumulative) return i;
            }
        }
        
        return numBuffers;
    }
    
    private static void updateWeight(double[] weights, int index, long waitTimeNanos) {
        double newWeight = 1.0 / (1.0 + waitTimeNanos / 1_000_000.0);
        weights[index] = 0.7 * weights[index] + 0.3 * newWeight;
        if (weights[index] < 0.01) weights[index] = 0.01;
    }
    
    private static void updateWeightsFromResponse(double[] weights, long[] aggregatedWaitTimes) {
        for (int i = 0; i < weights.length && i < aggregatedWaitTimes.length; i++) {
            if (aggregatedWaitTimes[i] > 0) {
                double newWeight = 1.0 / (1.0 + aggregatedWaitTimes[i] / 1_000_000.0);
                weights[i] = 0.5 * weights[i] + 0.5 * newWeight;
            }
        }
        
        double sum = 0;
        for (double w : weights) sum += w;
        if (sum > 0) {
            for (int i = 0; i < weights.length; i++) {
                weights[i] = weights[i] / sum * weights.length;
            }
        }
    }
}

// === KLASY WIADOMOŚCI ===

class WeightTableRequest {
    final int processId;
    final long[] waitTimes;
    final boolean isTermination;
    
    WeightTableRequest(int processId, long[] waitTimes, boolean isTermination) {
        this.processId = processId;
        this.waitTimes = waitTimes;
        this.isTermination = isTermination;
    }
}

class WeightTableResponse {
    final long[] aggregatedWaitTimes;
    
    WeightTableResponse(long[] aggregatedWaitTimes) {
        this.aggregatedWaitTimes = aggregatedWaitTimes;
    }
}