import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TestTablicaDispatcher {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          TABLICA DISPATCHER - KONFIGURACJA                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");
        
        System.out.print("Podaj liczbę producentów (P): ");
        int numProducers = scanner.nextInt();
        
        System.out.print("Podaj liczbę buforów (B): ");
        int numBuffers = scanner.nextInt();
        
        System.out.print("Podaj liczbę konsumentów (K): ");
        int numConsumers = scanner.nextInt();
        
        System.out.print("Podaj czas wykonywania w sekundach: ");
        int durationSeconds = scanner.nextInt();
        
        System.out.print("Wyświetlać logi? (t/n): ");
        String printChoice = scanner.next();
        boolean print = printChoice.equalsIgnoreCase("t");
        
        scanner.close();
        
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          TABLICA DISPATCHER - START                              ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Producenci: %-3d  Bufory: %-3d  Konsumenci: %-3d                  ║%n",
            numProducers, numBuffers, numConsumers);
        System.out.printf("║  Czas wykonywania: %d sekund                                      ║%n", 
            durationSeconds);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");
        
        // Statystyki buforów
        AtomicInteger[] bufferStats = new AtomicInteger[numBuffers];
        for (int i = 0; i < numBuffers; i++) {
            bufferStats[i] = new AtomicInteger(0);
        }
        
        // Flaga zakończenia i liczniki
        AtomicBoolean stopFlag = new AtomicBoolean(false);
        AtomicInteger totalProduced = new AtomicInteger(0);
        AtomicInteger totalConsumed = new AtomicInteger(0);
        
        // Wątek pokazujący pozostały czas
        Thread progressThread = new Thread(() -> {
            for (int i = durationSeconds; i > 0; i--) {
                if (stopFlag.get()) break;
                System.out.println(">>> Pozostało: " + i + " sekund | Wyprodukowano: " + 
                    totalProduced.get() + " | Skonsumowano: " + totalConsumed.get() + " <<<");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        
        long startTime = System.nanoTime();
        
        progressThread.start();
        
        // Uruchom system
        TablicaDispatcher system = new TablicaDispatcher(
            numProducers, numBuffers, numConsumers, 
            durationSeconds, print, bufferStats, 
            stopFlag, totalProduced, totalConsumed);
        system.run();
        
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        
        // Zatrzymaj wątek postępu
        stopFlag.set(true);
        progressThread.interrupt();
        
        // Wyniki
        printResults(elapsed, totalProduced.get(), totalConsumed.get(), bufferStats, numBuffers);
    }
    
    private static void printResults(long elapsed, int produced, int consumed, 
                                      AtomicInteger[] bufferStats, int numBuffers) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                      WYNIKI                                      ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Rzeczywisty czas wykonania: %-6d ms                           ║%n", elapsed);
        System.out.printf("║  Wyprodukowano elementów:    %-6d                             ║%n", produced);
        System.out.printf("║  Skonsumowano elementów:     %-6d                             ║%n", consumed);
        
        double throughput = (elapsed > 0) ? (consumed / (elapsed / 1000.0)) : 0;
        System.out.printf("║  Przepustowość:              %-6.0f elem/sek                     ║%n", throughput);
        
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║                  STATYSTYKI BUFORÓW                              ║");
        System.out.println("╠══════════┬───────────────┬────────────────╣");
        System.out.println("║  Bufor   │  Przekazane   │    Procent     ║");
        System.out.println("╠══════════┼───────────────┼────────────────╣");
        
        int total = 0;
        for (int i = 0; i < numBuffers; i++) {
            total += bufferStats[i].get();
        }
        
        for (int i = 0; i < numBuffers; i++) {
            int count = bufferStats[i].get();
            double percent = (total > 0) ? (count * 100.0 / total) : 0;
            System.out.printf("║    %-2d    │      %6d   │    %5.1f%%      ║%n", 
                i, count, percent);
        }
        
        System.out.println("╠══════════┼───────────────┼────────────────╣");
        System.out.printf("║   SUMA   │      %6d   │    100.0%%     ║%n", total);
        System.out.println("╚══════════╧───────────────╧────────────────╝");
        
        // Analiza równomierności
        double avgPerBuffer = (numBuffers > 0) ? ((double) total / numBuffers) : 0;
        double variance = 0;
        for (int i = 0; i < numBuffers; i++) {
            double diff = bufferStats[i].get() - avgPerBuffer;
            variance += diff * diff;
        }
        double stdDev = (numBuffers > 0) ? Math.sqrt(variance / numBuffers) : 0;
        double coefficient = (avgPerBuffer > 0) ? (stdDev / avgPerBuffer * 100) : 0;
        
        System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  ANALIZA RÓWNOMIERNOŚCI                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Średnia na bufor:           %-8.1f                            ║%n", avgPerBuffer);
        System.out.printf("║  Odchylenie standardowe:     %-8.1f                            ║%n", stdDev);
        System.out.printf("║  Współczynnik zmienności:    %-6.1f%%                             ║%n", coefficient);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        
        String rating;
        if (coefficient < 10) {
            rating = "✓ DOSKONAŁE równoważenie obciążenia";
        } else if (coefficient < 25) {
            rating = "✓ DOBRE równoważenie obciążenia";
        } else if (coefficient < 50) {
            rating = "~ ŚREDNIE równoważenie obciążenia";
        } else {
            rating = "✗ SŁABE równoważenie obciążenia";
        }
        System.out.printf("║  %-50s              ║%n", rating);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    }
}