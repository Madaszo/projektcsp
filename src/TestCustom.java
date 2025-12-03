import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestCustom {
    public static void main(String[] args) {
        int bufferSize = 5;
        int itemCount = 100;
        int numProducers = 10;
        int numConsumers = 10;
        boolean print = true; // włącz wypisywanie
        
        System.out.println("=== TEST CUSTOM PRODUCER-CONSUMER ===");
        System.out.printf("Bufory: %d, Elementy: %d, Producenci: %d, Konsumenci: %d%n%n",
            bufferSize, itemCount, numProducers, numConsumers);
        
        long startTime = System.nanoTime();
        
        CustomProducerConsumer custom = new CustomProducerConsumer(
            bufferSize, itemCount, numProducers, numConsumers, print);
        custom.run();
        
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        
        System.out.println("\n=== ZAKOŃCZONO ===");
        System.out.printf("Czas wykonania: %d ms%n", elapsed);
    }
}