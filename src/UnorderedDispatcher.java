import org.jcsp.lang.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class UnorderedDispatcher implements CSProcess {
    private final Any2AnyChannel inputChannel;
    private final One2OneChannel[] toBufferChannels;
    private final One2OneChannel[] readyChannels;
    private final AtomicBoolean done;
    private final boolean print;
    
    public UnorderedDispatcher(Any2AnyChannel inputChannel,
                                One2OneChannel[] toBufferChannels,
                                One2OneChannel[] readyChannels,
                                AtomicBoolean done, boolean print) {
        this.inputChannel = inputChannel;
        this.toBufferChannels = toBufferChannels;
        this.readyChannels = readyChannels;
        this.done = done;
        this.print = print;
    }
    
    @Override
    public void run() {
        int bufferSize = toBufferChannels.length;
        
        AltingChannelInput[] altChannels = new AltingChannelInput[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            altChannels[i] = readyChannels[i].in();
        }
        Alternative alt = new Alternative(altChannels);
        
        while (!done.get()) {
            Object obj = inputChannel.in().read();
            if (done.get() || obj == null) break;
            
            Integer item = (Integer) obj;
            
            // Poczekaj na gotowy bufor
            int bufferIndex = alt.fairSelect();
            altChannels[bufferIndex].read();
            
            toBufferChannels[bufferIndex].out().write(item);
            if (print) System.out.println("Dispatcher: " + item + " -> buffer " + bufferIndex);
        }
        
        // Wyślij sygnał zakończenia do wszystkich buforów
        for (int i = 0; i < bufferSize; i++) {
            try {
                // Odbierz READY jeśli czeka
                if (alt.priSelect() >= 0) {
                    altChannels[alt.priSelect()].read();
                }
            } catch (Exception e) {}
            toBufferChannels[i].out().write(-1); // POISON
        }
        
        if (print) System.out.println("Dispatcher: Done");
    }
}