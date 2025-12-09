import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TestTablicaDispatcher {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║          TABLICA DISPATCHER - KONFIGURACJA                       ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");
        
        System.out.print("Wczytać dane z pliku? (t/n): ");
        String fileChoice = scanner.next();
        
        List<TestConfig> configs = new ArrayList<>();
        boolean print;
        String outputFile = null;
        boolean appendToFile = false;
        
        if (fileChoice.equalsIgnoreCase("t")) {
            System.out.print("Podaj nazwę pliku wejściowego: ");
            String inputFile = scanner.next();
            
            try {
                BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                String line;
                int lineNumber = 0;
                
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    line = line.trim();
                    
                    // Pomiń puste linie i komentarze
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    String[] parts = line.split("\\s+");
                    
                    if (parts.length < 4) {
                        System.err.println("Ostrzeżenie: Linia " + lineNumber + 
                            " - nieprawidłowy format (oczekiwano: P B K t)");
                        continue;
                    }
                    
                    try {
                        int p = Integer.parseInt(parts[0]);
                        int b = Integer.parseInt(parts[1]);
                        int k = Integer.parseInt(parts[2]);
                        int t = Integer.parseInt(parts[3]);
                        
                        configs.add(new TestConfig(p, b, k, t));
                        
                    } catch (NumberFormatException e) {
                        System.err.println("Ostrzeżenie: Linia " + lineNumber + 
                            " - nieprawidłowe liczby");
                        continue;
                    }
                }
                
                reader.close();
                
                if (configs.isEmpty()) {
                    System.err.println("Błąd: Brak poprawnych konfiguracji w pliku");
                    scanner.close();
                    return;
                }
                
                System.out.println("✓ Wczytano " + configs.size() + " konfiguracji z pliku:");
                for (int i = 0; i < configs.size(); i++) {
                    TestConfig cfg = configs.get(i);
                    System.out.println("  " + (i+1) + ". P=" + cfg.numProducers + 
                        " B=" + cfg.numBuffers + " K=" + cfg.numConsumers + 
                        " t=" + cfg.durationSeconds);
                }
                
            } catch (FileNotFoundException e) {
                System.err.println("Błąd: Nie znaleziono pliku " + inputFile);
                scanner.close();
                return;
            } catch (IOException e) {
                System.err.println("Błąd odczytu pliku: " + e.getMessage());
                scanner.close();
                return;
            }
            
            System.out.print("\nWyświetlać logi? (t/n): ");
            String printChoice = scanner.next();
            print = printChoice.equalsIgnoreCase("t");
            
            System.out.print("Zapisać wyniki do pliku? (t/n): ");
            String saveChoice = scanner.next();
            if (saveChoice.equalsIgnoreCase("t")) {
                System.out.print("Podaj nazwę pliku wyjściowego: ");
                outputFile = scanner.next();
                
                if (configs.size() > 1) {
                    System.out.print("Dołączyć wszystkie wyniki do jednego pliku? (t/n): ");
                    String appendChoice = scanner.next();
                    appendToFile = appendChoice.equalsIgnoreCase("t");
                }
            }
            
        } else {
            // Tryb ręczny - jedna konfiguracja
            System.out.print("Podaj liczbę producentów (P): ");
            int numProducers = scanner.nextInt();
            
            System.out.print("Podaj liczbę buforów (B): ");
            int numBuffers = scanner.nextInt();
            
            System.out.print("Podaj liczbę konsumentów (K): ");
            int numConsumers = scanner.nextInt();
            
            System.out.print("Podaj czas wykonywania w sekundach: ");
            int durationSeconds = scanner.nextInt();
            
            configs.add(new TestConfig(numProducers, numBuffers, numConsumers, durationSeconds));
            
            System.out.print("Wyświetlać logi? (t/n): ");
            String printChoice = scanner.next();
            print = printChoice.equalsIgnoreCase("t");
            
            System.out.print("Zapisać wyniki do pliku? (t/n): ");
            String saveChoice = scanner.next();
            if (saveChoice.equalsIgnoreCase("t")) {
                System.out.print("Podaj nazwę pliku wyjściowego: ");
                outputFile = scanner.next();
            }
        }
        
        scanner.close();
        
        // Wykonaj testy dla wszystkich konfiguracji
        List<String> allResults = new ArrayList<>();
        
        for (int configIdx = 0; configIdx < configs.size(); configIdx++) {
            TestConfig config = configs.get(configIdx);
            
            System.out.println("\n╔══════════════════════════════════════════════════════════════════╗");
            if (configs.size() > 1) {
                System.out.println("║          TEST " + (configIdx + 1) + "/" + configs.size() + 
                    "                                                    ║");
            } else {
                System.out.println("║          TABLICA DISPATCHER - START                              ║");
            }
            System.out.println("╠══════════════════════════════════════════════════════════════════╣");
            System.out.printf("║  Producenci: %-3d  Bufory: %-3d  Konsumenci: %-3d                  ║%n",
                config.numProducers, config.numBuffers, config.numConsumers);
            System.out.printf("║  Czas wykonywania: %d sekund                                      ║%n", 
                config.durationSeconds);
            System.out.println("╚══════════════════════════════════════════════════════════════════╝\n");
            
            // Statystyki buforów
            AtomicInteger[] bufferStats = new AtomicInteger[config.numBuffers];
            for (int i = 0; i < config.numBuffers; i++) {
                bufferStats[i] = new AtomicInteger(0);
            }
            
            // Flaga zakończenia i liczniki
            AtomicBoolean stopFlag = new AtomicBoolean(false);
            AtomicInteger totalProduced = new AtomicInteger(0);
            AtomicInteger totalConsumed = new AtomicInteger(0);
            
            // Wątek pokazujący pozostały czas
            Thread progressThread = new Thread(() -> {
                for (int i = config.durationSeconds; i > 0; i--) {
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
                config.numProducers, config.numBuffers, config.numConsumers, 
                config.durationSeconds, print, bufferStats, 
                stopFlag, totalProduced, totalConsumed);
            system.run();
            
            long elapsed = (System.nanoTime() - startTime) / 1_000_000;
            
            // Zatrzymaj wątek postępu
            stopFlag.set(true);
            progressThread.interrupt();
            
            // Wyniki
            String results = generateResults(elapsed, totalProduced.get(), totalConsumed.get(), 
                                              bufferStats, config.numBuffers, config.numProducers, 
                                              config.numConsumers, config.durationSeconds, 
                                              configIdx + 1, configs.size());
            
            System.out.println(results);
            allResults.add(results);
            
            // Krótka przerwa między testami
            if (configIdx < configs.size() - 1) {
                System.out.println("\n>>> Czekam 2 sekundy przed następnym testem...\n");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Zapis wyników do pliku
        if (outputFile != null) {
            try {
                if (appendToFile && configs.size() > 1) {
                    // Wszystkie wyniki w jednym pliku
                    BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
                    
                    writer.write("╔══════════════════════════════════════════════════════════════════╗\n");
                    writer.write("║          TABLICA DISPATCHER - ZBIORCZE WYNIKI                    ║\n");
                    writer.write("║          Liczba testów: " + configs.size() + "                                          ║\n");
                    writer.write("╚══════════════════════════════════════════════════════════════════╝\n\n");
                    
                    for (String result : allResults) {
                        writer.write(result);
                        writer.write("\n\n" + "=".repeat(70) + "\n\n");
                    }
                    
                    // Podsumowanie CSV
                    writer.write("\n=== ZBIORCZE DANE CSV ===\n");
                    writer.write("Test,P,B,K,t,czas_ms,wyprodukowano,skonsumowano,przepustowosc,srednia,odchylenie,wsp_zmiennosci\n");
                    
                    for (int i = 0; i < configs.size(); i++) {
                        // Wyciągnij dane CSV z każdego wyniku
                        String result = allResults.get(i);
                        String[] lines = result.split("\n");
                        for (String line : lines) {
                            if (line.matches("^\\d+,\\d+,\\d+,\\d+,.*")) {
                                writer.write((i+1) + "," + line + "\n");
                                break;
                            }
                        }
                    }
                    
                    writer.close();
                    System.out.println("\n✓ Wszystkie wyniki zapisano do pliku: " + outputFile);
                    
                } else {
                    // Oddzielne pliki dla każdej konfiguracji
                    for (int i = 0; i < configs.size(); i++) {
                        String filename;
                        if (configs.size() > 1) {
                            // Dodaj numer testu do nazwy pliku
                            int dotIdx = outputFile.lastIndexOf('.');
                            if (dotIdx > 0) {
                                filename = outputFile.substring(0, dotIdx) + "_test" + (i+1) + 
                                    outputFile.substring(dotIdx);
                            } else {
                                filename = outputFile + "_test" + (i+1);
                            }
                        } else {
                            filename = outputFile;
                        }
                        
                        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
                        writer.write(allResults.get(i));
                        writer.close();
                        System.out.println("✓ Wyniki testu " + (i+1) + " zapisano do: " + filename);
                    }
                }
                
            } catch (IOException e) {
                System.err.println("Błąd zapisu do pliku: " + e.getMessage());
            }
        }
    }
    
    private static String generateResults(long elapsed, int produced, int consumed, 
                                           AtomicInteger[] bufferStats, int numBuffers,
                                           int numProducers, int numConsumers, 
                                           int durationSeconds, int testNumber, int totalTests) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n╔══════════════════════════════════════════════════════════════════╗\n");
        if (totalTests > 1) {
            sb.append(String.format("║                      WYNIKI TESTU %d/%d                            ║%n", 
                testNumber, totalTests));
        } else {
            sb.append("║                      WYNIKI                                      ║\n");
        }
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Konfiguracja:                                                   ║%n"));
        sb.append(String.format("║    Producenci: %-3d  Bufory: %-3d  Konsumenci: %-3d            ║%n",
            numProducers, numBuffers, numConsumers));
        sb.append(String.format("║    Czas wykonywania: %d sekund                                    ║%n", 
            durationSeconds));
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Rzeczywisty czas wykonania: %-6d ms                           ║%n", elapsed));
        sb.append(String.format("║  Wyprodukowano elementów:    %-6d                             ║%n", produced));
        sb.append(String.format("║  Skonsumowano elementów:     %-6d                             ║%n", consumed));
        
        double throughput = (elapsed > 0) ? (consumed / (elapsed / 1000.0)) : 0;
        sb.append(String.format("║  Przepustowość:              %-6.0f elem/sek                     ║%n", throughput));
        
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append("║                  STATYSTYKI BUFORÓW                              ║\n");
        sb.append("╠══════════┬───────────────┬────────────────╣\n");
        sb.append("║  Bufor   │  Przekazane   │    Procent     ║\n");
        sb.append("╠══════════┼───────────────┼────────────────╣\n");
        
        int total = 0;
        for (int i = 0; i < numBuffers; i++) {
            total += bufferStats[i].get();
        }
        
        for (int i = 0; i < numBuffers; i++) {
            int count = bufferStats[i].get();
            double percent = (total > 0) ? (count * 100.0 / total) : 0;
            sb.append(String.format("║    %-2d    │      %6d   │    %5.1f%%      ║%n", 
                i, count, percent));
        }
        
        sb.append("╠══════════┼───────────────┼────────────────╣\n");
        sb.append(String.format("║   SUMA   │      %6d   │    100.0%%     ║%n", total));
        sb.append("╚══════════╧───────────────╧────────────────╝\n");
        
        // Analiza równomierności
        double avgPerBuffer = (numBuffers > 0) ? ((double) total / numBuffers) : 0;
        double variance = 0;
        for (int i = 0; i < numBuffers; i++) {
            double diff = bufferStats[i].get() - avgPerBuffer;
            variance += diff * diff;
        }
        double stdDev = (numBuffers > 0) ? Math.sqrt(variance / numBuffers) : 0;
        double coefficient = (avgPerBuffer > 0) ? (stdDev / avgPerBuffer * 100) : 0;
        
        sb.append("\n╔══════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                  ANALIZA RÓWNOMIERNOŚCI                          ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Średnia na bufor:           %-8.1f                            ║%n", avgPerBuffer));
        sb.append(String.format("║  Odchylenie standardowe:     %-8.1f                            ║%n", stdDev));
        sb.append(String.format("║  Współczynnik zmienności:    %-6.1f%%                             ║%n", coefficient));
        sb.append("╠══════════════════════════════════════════════════════════════════╣\n");
        
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
        sb.append(String.format("║  %-50s              ║%n", rating));
        sb.append("╚══════════════════════════════════════════════════════════════════╝\n");
        
        // Dodatkowe statystyki w formacie CSV na końcu
        sb.append("\n=== DANE CSV (do analizy) ===\n");
        sb.append(String.format("P,B,K,t,czas_ms,wyprodukowano,skonsumowano,przepustowosc,srednia,odchylenie,wsp_zmiennosci%n"));
        sb.append(String.format("%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f%n",
            numProducers, numBuffers, numConsumers, durationSeconds, elapsed, 
            produced, consumed, throughput, avgPerBuffer, stdDev, coefficient));
        
        sb.append("\n=== ROZKŁAD OBCIĄŻENIA BUFORÓW (CSV) ===\n");
        sb.append("Bufor,Przekazane,Procent\n");
        for (int i = 0; i < numBuffers; i++) {
            int count = bufferStats[i].get();
            double percent = (total > 0) ? (count * 100.0 / total) : 0;
            sb.append(String.format("%d,%d,%.2f%n", i, count, percent));
        }
        
        return sb.toString();
    }
}

// Klasa pomocnicza do przechowywania konfiguracji testu
class TestConfig {
    final int numProducers;
    final int numBuffers;
    final int numConsumers;
    final int durationSeconds;
    
    TestConfig(int numProducers, int numBuffers, int numConsumers, int durationSeconds) {
        this.numProducers = numProducers;
        this.numBuffers = numBuffers;
        this.numConsumers = numConsumers;
        this.durationSeconds = durationSeconds;
    }
}