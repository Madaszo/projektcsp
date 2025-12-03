import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final int[] BUFFER_SIZES = {3, 5, 10, 20};
    private static final int[] ITEMS_COUNTS = {100, 1000, 10000, 50000};
    private static final int WARMUP_RUNS = 3;
    private static final int TEST_RUNS = 5;
    
    private static final int PRODUCER_MULTIPLIER = 2;
    private static final int CONSUMER_MULTIPLIER = 2;
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║        PRODUCENT-KONSUMENT Z ROZPROSZONYM BUFOREM - JCSP                     ║");
        System.out.println("║        Porównanie wydajności różnych implementacji                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝\n");
        
        System.out.println("Rozgrzewka JVM...");
        warmup();
        System.out.println("Rozgrzewka zakończona.\n");
        
        System.out.println("┌─────────────┬───────────┬─────────────────┬─────────────────┐");
        System.out.println("│ Bufor (N)   │ Elementy  │ Wariant A (ms)  │ Wariant B (ms)  │");
        System.out.println("├─────────────┼───────────┼─────────────────┼─────────────────┤");
        
        StringBuilder csvResults = new StringBuilder();
        csvResults.append("BufferSize,Items,Producers,Consumers,VariantA_avg,VariantA_std,VariantB_avg,VariantB_std\n");
        
        for (int bufferSize : BUFFER_SIZES) {
            for (int itemCount : ITEMS_COUNTS) {
                int numProducers = Math.max(4, bufferSize * PRODUCER_MULTIPLIER);
                int numConsumers = Math.max(4, bufferSize * CONSUMER_MULTIPLIER);
                
                TestResults resultsA = runTests(() -> testUnordered(bufferSize, itemCount, numProducers, numConsumers));
                TestResults resultsB = runTests(() -> testOrdered(bufferSize, itemCount, numProducers, numConsumers));
                
                System.out.printf("│ %11d │ %9d │ %7.2f ± %5.2f │ %7.2f ± %5.2f │%n",
                    bufferSize, itemCount,
                    resultsA.avg, resultsA.std,
                    resultsB.avg, resultsB.std);
                
                csvResults.append(String.format(java.util.Locale.US, "%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f%n",
                    bufferSize, itemCount, numProducers, numConsumers,
                    resultsA.avg, resultsA.std,
                    resultsB.avg, resultsB.std));
            }
            System.out.println("├─────────────┼───────────┼─────────────────┼─────────────────┤");
        }
        
        System.out.println("└─────────────┴───────────┴─────────────────┴─────────────────┘");
        
        int testBufferSize = 5;
        int testItemCount = 10000;
        
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║  SZCZEGÓŁOWA ANALIZA (N=%d, elementy=%d)                                   ║%n", testBufferSize, testItemCount);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝\n");
        
        detailedAnalysis(testBufferSize, testItemCount);
        
        try {
            java.nio.file.Files.writeString(
                java.nio.file.Path.of("wyniki_pomiarow.csv"), 
                csvResults.toString()
            );
            System.out.println("\nWyniki zapisano do: wyniki_pomiarow.csv");
        } catch (Exception e) {
            System.out.println("Nie udało się zapisać CSV: " + e.getMessage());
        }
        
        printConclusions();
    }
    
    private static void warmup() {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            testUnordered(3, 50, 2, 2);
            testOrdered(3, 50, 2, 2);
        }
    }
    
    private static TestResults runTests(java.util.function.Supplier<Long> test) {
        long[] times = new long[TEST_RUNS];
        for (int i = 0; i < TEST_RUNS; i++) {
            times[i] = test.get();
        }
        return new TestResults(times);
    }
    
    private static void detailedAnalysis(int bufferSize, int itemCount) {
        int numProducers = Math.max(4, bufferSize * PRODUCER_MULTIPLIER);
        int numConsumers = Math.max(4, bufferSize * CONSUMER_MULTIPLIER);
        
        System.out.println("Konfiguracja:");
        System.out.printf("  Bufory: %d, Producenci: %d, Konsumenci: %d%n", bufferSize, numProducers, numConsumers);
        System.out.println("Wykonuję " + TEST_RUNS + " pomiarów dla każdego wariantu...\n");
        
        long[] timesA = new long[TEST_RUNS];
        long[] timesB = new long[TEST_RUNS];
        
        for (int i = 0; i < TEST_RUNS; i++) {
            timesA[i] = testUnordered(bufferSize, itemCount, numProducers, numConsumers);
            timesB[i] = testOrdered(bufferSize, itemCount, numProducers, numConsumers);
        }
        
        TestResults resA = new TestResults(timesA);
        TestResults resB = new TestResults(timesB);
        
        System.out.println("Wariant A (bez kolejności - dispatcher round-robin):");
        System.out.printf("  Średnia: %.2f ms, Odch. std: %.2f ms, Min: %d ms, Max: %d ms%n",
            resA.avg, resA.std, resA.min, resA.max);
        
        System.out.println("\nWariant B (z kolejnością - pipeline):");
        System.out.printf("  Średnia: %.2f ms, Odch. std: %.2f ms, Min: %d ms, Max: %d ms%n",
            resB.avg, resB.std, resB.min, resB.max);
        
        System.out.println("\n--- Porównanie względne ---");
        System.out.printf("Wariant B vs A: %.1f%% %s%n", 
            Math.abs(resB.avg - resA.avg) / resA.avg * 100,
            resB.avg > resA.avg ? "wolniejszy" : "szybszy");
    }
    
    private static void printConclusions() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                WNIOSKI                                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println("""
            
            1. WARIANT A (bez kolejności - centralny dispatcher):
               - Producenci wysyłają do wspólnego kanału Any2One
               - Dispatcher rozdziela round-robin do buforów
               - Konsumenci odbierają ze wspólnego kanału Any2Any
               - Brak gwarancji kolejności FIFO
               
            2. WARIANT B (z kolejnością - pipeline):
               - Producenci wysyłają do pierwszego bufora (Any2One)
               - Bufory połączone w łańcuch (One2One)
               - Konsumenci odbierają z ostatniego (One2Any)
               - Zachowuje kolejność w pipeline
               
            3. OBSERWACJE:
               - Wariant A lepszy przy dużej liczbie buforów
               - Wariant B ma przewidywalną latencję
               - Pipeline tworzy wąskie gardło na pierwszym buforze
            """);
    }
    
    // ========== WARIANT A: BEZ KOLEJNOŚCI ==========
    
    private static long testUnordered(int bufferSize, int itemCount, int numProducers, int numConsumers) {
        long startTime = System.nanoTime();
        
        Any2OneChannel toDispatcher = Channel.any2one();
        One2OneChannel[] toBuffers = new One2OneChannel[bufferSize];
        Any2AnyChannel toConsumers = Channel.any2any();
        
        for (int i = 0; i < bufferSize; i++) {
            toBuffers[i] = Channel.one2one();
        }
        
        AtomicInteger itemCounter = new AtomicInteger(0);
        AtomicBoolean allConsumed = new AtomicBoolean(false);
        AtomicInteger consumedCount = new AtomicInteger(0);
        
        CSProcess[] processes = new CSProcess[numProducers + 1 + bufferSize + numConsumers];
        int idx = 0;
        
        // Producenci - wysyłają tylko dane, bez POISON
        for (int p = 0; p < numProducers; p++) {
            processes[idx++] = () -> {
                while (true) {
                    int itemNum = itemCounter.getAndIncrement();
                    if (itemNum >= itemCount) {
                        return;
                    }
                    toDispatcher.out().write(itemNum + 1);
                }
            };
        }
        
        // Dispatcher - rozdziela dane i kończy gdy wszystko wysłane
        final int totalItems = itemCount;
        processes[idx++] = () -> {
            int nextBuffer = 0;
            int dispatched = 0;
            
            while (dispatched < totalItems) {
                Integer item = (Integer) toDispatcher.in().read();
                toBuffers[nextBuffer].out().write(item);
                nextBuffer = (nextBuffer + 1) % bufferSize;
                dispatched++;
            }
            
            // Wyślij POISON do wszystkich buforów
            for (int i = 0; i < bufferSize; i++) {
                toBuffers[i].out().write(-1);
            }
        };
        
        // Bufory - przekazują dane i POISON
        for (int i = 0; i < bufferSize; i++) {
            final One2OneChannel myInput = toBuffers[i];
            processes[idx++] = () -> {
                while (true) {
                    Integer item = (Integer) myInput.in().read();
                    toConsumers.out().write(item);
                    if (item == -1) return;
                }
            };
        }
        
        // Konsumenci - zliczają skonsumowane i kończą po POISON
        final int total = itemCount;
        final int bufSize = bufferSize;
        for (int c = 0; c < numConsumers; c++) {
            processes[idx++] = () -> {
                while (true) {
                    Integer item = (Integer) toConsumers.in().read();
                    if (item == -1) {
                        // Odbieramy POISON od jednego bufora
                        return;
                    }
                    consumedCount.incrementAndGet();
                }
            };
        }
        
        new Parallel(processes).run();
        
        return (System.nanoTime() - startTime) / 1_000_000;
    }
    
    // ========== WARIANT B: Z KOLEJNOŚCIĄ (PIPELINE) ==========
    
    private static long testOrdered(int bufferSize, int itemCount, int numProducers, int numConsumers) {
        long startTime = System.nanoTime();
        
        Any2OneChannel toFirstBuffer = Channel.any2one();
        One2OneChannel[] pipeChannels = new One2OneChannel[bufferSize - 1];
        for (int i = 0; i < bufferSize - 1; i++) {
            pipeChannels[i] = Channel.one2one();
        }
        One2AnyChannel toConsumers = Channel.one2any();
        
        AtomicInteger itemCounter = new AtomicInteger(0);
        AtomicInteger consumedCount = new AtomicInteger(0);
        
        CSProcess[] processes = new CSProcess[numProducers + bufferSize + numConsumers];
        int idx = 0;
        
        // Producenci
        for (int p = 0; p < numProducers; p++) {
            processes[idx++] = () -> {
                while (true) {
                    int itemNum = itemCounter.getAndIncrement();
                    if (itemNum >= itemCount) {
                        return;
                    }
                    toFirstBuffer.out().write(itemNum + 1);
                }
            };
        }
        
        // Pierwszy bufor - zbiera od producentów, wysyła dalej
        final int totalItems = itemCount;
        if (bufferSize > 0) {
            final ChannelOutput firstOut = (bufferSize == 1) ? toConsumers.out() : pipeChannels[0].out();
            processes[idx++] = () -> {
                int received = 0;
                while (received < totalItems) {
                    Integer item = (Integer) toFirstBuffer.in().read();
                    firstOut.write(item);
                    received++;
                }
                firstOut.write(-1); // POISON
            };
        }
        
        // Środkowe bufory
        for (int i = 1; i < bufferSize - 1; i++) {
            final ChannelInput in = pipeChannels[i - 1].in();
            final ChannelOutput out = pipeChannels[i].out();
            processes[idx++] = () -> {
                while (true) {
                    Integer item = (Integer) in.read();
                    out.write(item);
                    if (item == -1) return;
                }
            };
        }
        
        // Ostatni bufor (jeśli > 1 bufor)
        if (bufferSize > 1) {
            final ChannelInput lastIn = pipeChannels[bufferSize - 2].in();
            processes[idx++] = () -> {
                while (true) {
                    Integer item = (Integer) lastIn.read();
                    toConsumers.out().write(item);
                    if (item == -1) return;
                }
            };
        }
        
        // Konsumenci
        for (int c = 0; c < numConsumers; c++) {
            processes[idx++] = () -> {
                while (true) {
                    Integer item = (Integer) toConsumers.in().read();
                    if (item == -1) {
                        return;
                    }
                    consumedCount.incrementAndGet();
                }
            };
        }
        
        new Parallel(processes).run();
        
        return (System.nanoTime() - startTime) / 1_000_000;
    }
    
    static class TestResults {
        double avg;
        double std;
        long min;
        long max;
        
        TestResults(long[] times) {
            long sum = 0;
            min = Long.MAX_VALUE;
            max = Long.MIN_VALUE;
            
            for (long t : times) {
                sum += t;
                if (t < min) min = t;
                if (t > max) max = t;
            }
            avg = (double) sum / times.length;
            
            double sumSq = 0;
            for (long t : times) {
                sumSq += (t - avg) * (t - avg);
            }
            std = Math.sqrt(sumSq / times.length);
        }
    }
}