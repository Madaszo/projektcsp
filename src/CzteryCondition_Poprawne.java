import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CzteryCondition_Poprawne {

    static class Buffer {
        private int value = 0;
        private final int capacity;
        private int totalConsumed = 0;
        private final int stopAfter;
        private final long stopTimeMillis;
        private final boolean useTimeLimit;
        private final boolean silentMode; // tryb cichy dla trybu wsadowego

        private final Lock lock = new ReentrantLock(true);
        private final Condition notFull = lock.newCondition();
        private final Condition notEmpty = lock.newCondition();
        private final Condition pierwszyProd = lock.newCondition();
        private final Condition pierwszyConsume = lock.newCondition();

        private boolean producerFirst = false;
        private boolean consumerFirst = false;

        private final Map<String, Integer> producedCount = new HashMap<>();
        private final Map<String, Integer> consumedCount = new HashMap<>();

        // Konstruktor z limitem konsumpcji
        public Buffer(int capacity, int stopAfter) {
            this(capacity, stopAfter, false);
        }

        public Buffer(int capacity, int stopAfter, boolean silentMode) {
            this.capacity = capacity;
            this.stopAfter = stopAfter;
            this.stopTimeMillis = 0;
            this.useTimeLimit = false;
            this.silentMode = silentMode;
        }

        // Konstruktor z limitem czasowym
        public Buffer(int capacity, long durationSeconds) {
            this(capacity, durationSeconds, false);
        }

        public Buffer(int capacity, long durationSeconds, boolean silentMode) {
            this.capacity = capacity;
            this.stopAfter = Integer.MAX_VALUE;
            this.stopTimeMillis = System.currentTimeMillis() + (durationSeconds * 1000);
            this.useTimeLimit = true;
            this.silentMode = silentMode;
        }

        public void produce(int amount) throws InterruptedException {
            lock.lock();
            try {
                // czekaj, aż będzie Twoja kolej
                while (producerFirst) {
                    notFull.await();
                }
                producerFirst = true;

                // czekaj, aż będzie miejsce
                while (value + amount > capacity && !shouldStop()) {
                    pierwszyProd.await();
                }

                if (shouldStop()) return;

                value += amount;
                producedCount.merge(Thread.currentThread().getName(), 1, Integer::sum);

                if (!silentMode) {
                    System.out.println(Thread.currentThread().getName() +
                            " produced " + amount + " → buffer = " + value);
                }

                producerFirst = false;
                notFull.signal();
                pierwszyConsume.signal();
            } finally {
                lock.unlock();
            }
        }

        public void consume(int amount) throws InterruptedException {
            lock.lock();
            try {
                // czekaj, aż będzie Twoja kolej
                while (consumerFirst) {
                    notEmpty.await();
                }
                consumerFirst = true;

                // czekaj, aż będzie co zjeść
                while (value < amount && !shouldStop()) {
                    pierwszyConsume.await();
                }

                if (shouldStop()) return;

                value -= amount;
                totalConsumed++;
                consumedCount.merge(Thread.currentThread().getName(), 1, Integer::sum);

                if (!silentMode) {
                    System.out.println(Thread.currentThread().getName() +
                            " consumed " + amount + " → buffer = " + value +
                            " | totalConsumed = " + totalConsumed);
                }

                consumerFirst = false;
                notEmpty.signal();
                pierwszyProd.signal();
            } finally {
                lock.unlock();
            }
        }

        public boolean shouldStop() {
            if (useTimeLimit) {
                return System.currentTimeMillis() >= stopTimeMillis;
            } else {
                return totalConsumed >= stopAfter;
            }
        }

        public int getTotalConsumed() {
            return totalConsumed;
        }

        public void resetStats() {
            lock.lock();
            try {
                totalConsumed = 0;
                producedCount.clear();
                consumedCount.clear();
            } finally {
                lock.unlock();
            }
        }

        public void printStats() {
            System.out.println("\n=== STATYSTYKI ===");
            System.out.println("Łącznie skonsumowano: " + totalConsumed);
            System.out.println("Stan bufora na koniec: " + value);
            System.out.println("Produkowanie:");
            producedCount.forEach((k, v) -> System.out.println("  " + k + ": " + v));
            System.out.println("Konsumowanie:");
            consumedCount.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        }
    }

    private static void runBatchMode(String inputFile, String outputFile) {
        try {
            List<Long> durations = new ArrayList<>();
            
            // Wczytaj dane z pliku
            try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
                String line = br.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    for (String part : parts) {
                        durations.add(Long.parseLong(part));
                    }
                }
            }

            if (durations.isEmpty()) {
                System.err.println("Plik wejściowy jest pusty!");
                return;
            }

            // Parametry domyślne dla trybu wsadowego
            int numProducers = 2;
            int numConsumers = 2;
            int bufferCapacity = 10;
            int maxChunk = Math.max(1, bufferCapacity / 2);

            List<Integer> results = new ArrayList<>();

            // Uruchom symulację dla każdego przedziału czasowego
            for (int idx = 0; idx < durations.size(); idx++) {
                long duration = durations.get(idx);

                Buffer buffer = new Buffer(bufferCapacity, duration, true); // tryb cichy
                List<Thread> threads = new ArrayList<>();

                // Producer BIG
                // Thread producerBig = new Thread(() -> {
                //     try {
                //         while (!buffer.shouldStop()) {
                //             buffer.produce(maxChunk);
                //         }
                //     } catch (InterruptedException e) {
                //         Thread.currentThread().interrupt();
                //     }
                // }, "Producer_BIG");
                // threads.add(producerBig);
                // producerBig.start();

                // Producers
                for (int i = 0; i < numProducers; i++) {
                    Thread producer = new Thread(() -> {
                        try {
                            while (!buffer.shouldStop()) {
                                buffer.produce(1);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, "Producer-" + i);
                    threads.add(producer);
                    producer.start();
                }

                // Consumer BIG
                Thread consumerBig = new Thread(() -> {
                    try {
                        while (!buffer.shouldStop()) {
                            buffer.consume(maxChunk);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "Consumer_BIG");
                threads.add(consumerBig);
                consumerBig.start();

                // Consumers
                for (int i = 0; i < numConsumers; i++) {
                    Thread consumer = new Thread(() -> {
                        try {
                            while (!buffer.shouldStop()) {
                                int amount = 1 + new Random().nextInt(Math.max(1, maxChunk / 2));
                                buffer.consume(amount);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }, "Consumer-" + i);
                    threads.add(consumer);
                    consumer.start();
                }

                // Czekaj na zakończenie czasu
                try {
                    Thread.sleep(duration * 1000 + 500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Przerwij wszystkie wątki
                for (Thread t : threads) {
                    t.interrupt();
                }

                // Zbierz wynik
                int consumed = buffer.getTotalConsumed();
                results.add(consumed);
            }

            // Zapisz wyniki do pliku
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
                for (int i = 0; i < results.size(); i++) {
                    if (i > 0) bw.write(" ");
                    bw.write(String.valueOf(results.get(i)));
                }
                bw.newLine();
            }

            System.out.println("Wyniki zapisano do: " + outputFile);

        } catch (IOException e) {
            System.err.println("Błąd IO: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.println("Wybierz tryb:");
        System.out.println("1 - Tryb interaktywny");
        System.out.println("2 - Tryb wsadowy (z pliku)");
        System.out.print("Wybór: ");
        int mode = sc.nextInt();
        sc.nextLine(); // consume newline

        if (mode == 2) {
            System.out.print("Podaj nazwę pliku wejściowego: ");
            String inputFile = sc.nextLine().trim();
            
            System.out.print("Podaj nazwę pliku wyjściowego: ");
            String outputFile = sc.nextLine().trim();
            
            runBatchMode(inputFile, outputFile);
            System.exit(0);
            return;
        }

        // TRYB INTERAKTYWNY
        System.out.print("Producenci: ");
        int numProducers = sc.nextInt();

        System.out.print("Konsumenci: ");
        int numConsumers = sc.nextInt();

        System.out.print("Pojemność bufora: ");
        int bufferCapacity = sc.nextInt();

        System.out.println("\nWybierz tryb zakończenia:");
        System.out.println("1 - Po określonej liczbie konsumpcji");
        System.out.println("2 - Po określonym czasie (sekundy)");
        System.out.print("Wybór: ");
        int choice = sc.nextInt();

        Buffer buffer;
        if (choice == 1) {
            System.out.print("Stop po ilu konsumpcjach: ");
            int stopAfter = sc.nextInt();
            buffer = new Buffer(bufferCapacity, stopAfter);
        } else {
            System.out.print("Czas działania (sekundy): ");
            long duration = sc.nextLong();
            buffer = new Buffer(bufferCapacity, duration);
        }

        int maxChunk = Math.max(1, bufferCapacity / 2);

        // --- PRODUCERS ---
//        Thread producerBig = new Thread(() -> {
//            try {
//                while (!buffer.shouldStop()) {
//                    buffer.produce(maxChunk);
//                }
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        }, "Producer_BIG");
//        producerBig.start();

        for (int i = 0; i < numProducers; i++) {
            Thread producer = new Thread(() -> {
                try {
                    while (!buffer.shouldStop()) {
                        int amount = 1;
                        buffer.produce(amount);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Producer-" + i);
            producer.start();
        }

        // --- CONSUMERS ---
        Thread consumerBig = new Thread(() -> {
            try {
                while (!buffer.shouldStop()) {
                    buffer.consume(maxChunk);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer_BIG");
        consumerBig.start();

        for (int i = 0; i < numConsumers; i++) {
            Thread consumer = new Thread(() -> {
                try {
                    while (!buffer.shouldStop()) {
                        int amount = 1 + new Random().nextInt(Math.max(1, maxChunk / 2));
                        buffer.consume(amount);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Consumer-" + i);
            consumer.start();
        }

        // --- MONITOR ---
        new Thread(() -> {
            while (!buffer.shouldStop()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
            
            buffer.printStats();
            System.out.println("=== KONIEC ===");
            System.exit(0);
        }).start();
    }
}
