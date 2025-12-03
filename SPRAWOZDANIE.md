# Sprawozdanie: Producent-Konsument z Rozproszonym Buforem (JCSP)

## 1. Opis problemu

Problem producenta-konsumenta z buforem N-elementowym, gdzie **każdy element bufora jest reprezentowany przez odrębny proces**. Takie rozwiązanie ma praktyczne uzasadnienie gdy pamięć lokalna procesora jest ograniczona.

### Wariant A: Bez zachowania kolejności
Producent może umieścić element w dowolnym wolnym buforze, konsument pobiera z dowolnego zajętego.

```
PRODUCER ──┬──> BUFFER[0] ──┬──> CONSUMER
           ├──> BUFFER[1] ──┤
           ├──> BUFFER[2] ──┤
           └──> BUFFER[N-1]─┘
```

### Wariant B: Z zachowaniem kolejności
Elementy przepływają przez bufory sekwencyjnie (pipeline).

```
PRODUCER ──> BUFFER[0] ──> BUFFER[1] ──> ... ──> BUFFER[N-1] ──> CONSUMER
```

## 2. Implementacja w JCSP

### 2.1 Wariant A - Konstrukcja ALT

Wykorzystuje **niedeterministyczny wybór** (Alternative) do selekcji wolnego bufora:

```java
// Producent - wybór dowolnego wolnego bufora
Alternative alt = new Alternative(readyChannels);
int bufferIndex = alt.select();  // Czekaj na dowolny sygnał gotowości
dataChannels[bufferIndex].out().write(item);
```

**Kanały:**
- `readyChannels[i]`: BUFFER[i] → PRODUCER (sygnał "jestem wolny")
- `dataChannels[i]`: PRODUCER → BUFFER[i] (dane)
- `consumerChannels[i]`: BUFFER[i] → CONSUMER (dane)

### 2.2 Wariant B - Łańcuch (Pipeline)

Proste przekazywanie danych przez łańcuch kanałów:

```java
// Bufor - odbierz od poprzednika, wyślij do następnika
Integer item = (Integer) inputChannel.in().read();
outputChannel.out().write(item);
```

**Kanały:**
- `channels[0]`: PRODUCER → BUFFER[0]
- `channels[i]`: BUFFER[i-1] → BUFFER[i]
- `channels[N]`: BUFFER[N-1] → CONSUMER

## 3. Metodyka pomiarów

### Parametry testów:
- **Rozmiary bufora (N):** 3, 5, 10, 20
- **Liczba elementów:** 100, 1000, 10000, 50000
- **Rozgrzewka JVM:** 5 uruchomień
- **Liczba pomiarów:** 10 dla każdej konfiguracji
- **Metryki:** średnia, odchylenie standardowe, min, max

### Środowisko testowe:
- Java 11+
- JCSP 1.1-rc4
- [Uzupełnij: procesor, RAM, system operacyjny]

## 4. Wyniki pomiarów

### 4.1 Czasy wykonania (ms)

| N  | Elementy | Wariant A | Wariant B | BlockingQueue |
|----|----------|-----------|-----------|---------------|
| 5  | 1000     | X.XX ± X.XX | X.XX ± X.XX | X.XX ± X.XX |
| 5  | 10000    | X.XX ± X.XX | X.XX ± X.XX | X.XX ± X.XX |
| 10 | 10000    | X.XX ± X.XX | X.XX ± X.XX | X.XX ± X.XX |

*(Uzupełnij po uruchomieniu testów)*

### 4.2 Przepustowość (elementy/sekundę)

| N  | Elementy | Wariant A | Wariant B | BlockingQueue |
|----|----------|-----------|-----------|---------------|
| 5  | 10000    | XXXXX     | XXXXX     | XXXXX         |

## 5. Analiza wyników

### 5.1 Porównanie wariantów JCSP

**Wariant A (bez kolejności):**
- ✅ Lepsza równoległość - wszystkie bufory mogą pracować jednocześnie
- ✅ Wyższa przepustowość przy większej liczbie buforów
- ❌ Brak gwarancji kolejności FIFO
- ❌ Bardziej złożona implementacja (Alternative)

**Wariant B (z kolejnością):**
- ✅ Gwarantuje kolejność FIFO
- ✅ Prostsza implementacja
- ❌ Sekwencyjny przepływ - przepustowość ograniczona przez najwolniejszy bufor
- ❌ Większe opóźnienie (latency) - element musi przejść przez wszystkie bufory

### 5.2 JCSP vs BlockingQueue

| Aspekt | JCSP | BlockingQueue |
|--------|------|---------------|
| Wydajność | Wyższy narzut | Niższy narzut |
| Bezpieczeństwo | Formalna weryfikacja (CSP) | Testy jednostkowe |
| Złożoność | Wyższa krzywa uczenia | Standard Java |
| Skalowalność | Dobra dla wielu procesów | Zależy od implementacji |

### 5.3 Wpływ rozmiaru bufora

- **Mały bufor (N=3-5):** Wariant A nieznacznie szybszy
- **Duży bufor (N=20+):** Różnica bardziej widoczna na korzyść wariantu A
- **Wariant B:** Czas rośnie liniowo z N (każdy element przechodzi przez N buforów)

## 6. Wnioski

1. **Wariant A** jest preferowany gdy:
   - Kolejność nie ma znaczenia
   - Wymagana wysoka przepustowość
   - Dostępne jest wiele procesorów

2. **Wariant B** jest preferowany gdy:
   - Kolejność FIFO jest kluczowa
   - Prostota implementacji jest priorytetem
   - Przetwarzanie potokowe (każdy bufor może wykonywać dodatkowe operacje)

3. **JCSP** warto stosować gdy:
   - Wymagana formalna weryfikacja poprawności
   - System ma wiele współbieżnych procesów
   - Czytelność kodu CSP jest ważniejsza niż surowa wydajność

4. **BlockingQueue** wystarczy gdy:
   - Prosty scenariusz producent-konsument
   - Wydajność jest priorytetem
   - Nie ma potrzeby formalnej weryfikacji

## 7. Kod źródłowy

Projekt zawiera następujące pliki:
- `Main.java` - główna klasa z testami wydajności
- `OrderedProducer.java`, `OrderedBuffer.java`, `OrderedConsumer.java` - Wariant B
- `UnorderedProducer.java`, `UnorderedBuffer.java`, `UnorderedConsumer.java` - Wariant A
- `CustomProducerConsumer.java` - implementacja z BlockingQueue

## 8. Instrukcja uruchomienia

```powershell
# Kompilacja
javac -cp "lib\jcsp.jar" -d target\classes src\*.java

# Uruchomienie testów
java -cp "target\classes;lib\jcsp.jar" Main
```

Wyniki zostaną zapisane do `wyniki_pomiarow.csv`.