# System wizualny hReader

Wersja robocza 1.3 · zakres: motyw ciemny

## Cel produktu

hReader pomaga szybko przejrzeć nowe artykuły, wybrać jeden i wygodnie go przeczytać. Interfejs ma wspierać ten przepływ:

1. Rozpoznać nowe i przeczytane pozycje.
2. Ocenić temat na podstawie źródła, tytułu i krótkiego podglądu.
3. Otworzyć artykuł lub wrócić do listy bez utraty kontekstu.
4. Zrozumieć stan synchronizacji i pobierania offline.

Lista i czytnik są głównym doświadczeniem. Konfiguracja serwera, synchronizacja, tryb offline, ustawienia czytania i AI mają być łatwe do znalezienia, ale nie powinny dominować codziennej pracy.

### Kryteria dobrego wdrożenia

- Stan przeczytania jest rozpoznawalny bez polegania wyłącznie na kolorze.
- Artykuł można otworzyć jednym dotknięciem, a jego stan przeczytania zmienić osobną, łatwo trafialną kontrolką.
- Błąd synchronizacji, pusty wynik i brak subskrypcji prowadzą do właściwej następnej akcji.
- Ustawienia da się przeskanować po tytułach i krótkich, stabilnych podsumowaniach.
- Elementy interfejsu używają wspólnych tokenów i zachowują miejsce na treść.

## Kierunek wizualny

Spokojny interfejs edytorialny: ciepły grafit, piaskowy akcent, taupe i stonowana zieleń. Zachowujemy obecny charakter palety. Poprawiamy rytm, hierarchię i konsekwencję powierzchni.

- Treść artykułu ma najwyższy priorytet wizualny.
- Na ekranie jest jeden główny akcent działania.
- Kolor sygnalizuje rolę lub stan.
- Powierzchnie odróżniają się tonem; cień nie jest domyślnym separatorem.
- Komponenty korzystają ze wspólnych tokenów typografii, kształtu i odstępu.
- Karta grupuje powiązane ustawienia. Pojedyncza pozycja listy artykułów zachowuje płaski, redakcyjny rytm.

Zakres tej wersji obejmuje wyłącznie motyw ciemny. Nie dodajemy wariantu jasnego ani dynamicznych kolorów systemowych.

## Walidacja emulatorowa

Propozycję zweryfikowano 24 września 2026 r. na emulatorze API 37 z lokalnym Miniflux mockiem. Sprawdzono listę, czytnik przed przewinięciem i po przewinięciu oraz zawijanie treści przy skali tekstu 130%. Aplikacja pobrała kanały, artykuły, pełne treści i ilustracje przez zwykły interfejs backendu.

Mock znajduje się w `tools/mock_miniflux_server.py`. Wiąże się tylko z loopbackiem; emulator korzysta z przekierowania portu ADB, a adres serwera i adresy zasobów artykułów muszą wskazywać ten sam host. Taki układ pozwala też sprawdzić, że zabezpieczenia aplikacji dopuszczają tylko host skonfigurowanego backendu.

Aby odtworzyć przepływ: uruchom skrypt, wykonaj `adb reverse tcp:8765 tcp:8765`, a w ustawieniach Miniflux wpisz `http://127.0.0.1:8765` i token `hreader-demo-token`. Zapisz serwer, a następnie użyj „Testuj połączenie”; aplikacja ufa lokalnemu hostowi dopiero po zapisaniu go w konfiguracji. Test zwraca trzy subskrypcje.

## Kolory

Kolory pochodzą z obecnego `DarkColorScheme`. Nazwy określają semantyczną rolę, a nie konkretny ekran.

| Rola | Wartość | Zastosowanie |
| --- | --- | --- |
| Tło | `#181611` | Główne tło ekranu |
| Powierzchnia niska | `#221F19` | Delikatne rozdzielenie większych obszarów |
| Powierzchnia grupy | `#2C2722` | Karty ustawień, menu i powierzchnie pomocnicze |
| Powierzchnia podbita | `#35312A` | Zaznaczenie lub element wysunięty |
| Tekst główny | `#E7E4DE` | Tytuły i podstawowa treść interfejsu |
| Tekst pomocniczy | `#CBC5BB` | Opisy, podsumowania i metadane |
| Akcent | `#E0C295` | Główne akcje i wybrane elementy |
| Akcent pomocniczy | `#D4C2AE` | Drugorzędne elementy o ciepłym tonie |
| Sukces | `#A4D388` | Sukces i wysoka wiarygodność |
| Ostrzeżenie | `#E7C358` | Stan wymagający uwagi |
| Błąd | `#FFB3A3` | Błąd i destrukcyjna informacja |
| Obrys subtelny | `#4B4640` | Separator lub granica potrzebna do zrozumienia układu |

Tekst na kolorowej powierzchni używa odpowiedniej pary `on*` z `ColorScheme`. Błąd, sukces i ostrzeżenie otrzymują tekst lub ikonę obok koloru. `error` służy wyłącznie rzeczywistym błędom; zwykły pusty wynik używa `onSurface` i `onSurfaceVariant`.

## Typografia

Używamy domyślnego kroju systemowego i istniejącej skali Material 3. Hierarchię tworzymy rozmiarem i umiarkowaną zmianą grubości.

| Rola | Styl Material 3 | Użycie |
| --- | --- | --- |
| Nagłówek artykułu | `headlineSmall`, 24/32 sp, SemiBold | Tytuł nad treścią artykułu |
| Nagłówek widoku | `titleLarge`, 22/28 sp, SemiBold | Tytuł dialogu lub ważnego widoku |
| Pasek, grupa, tytuł artykułu na liście | `titleMedium`, 16/24 sp, Medium | Nieprzeczytany wpis może użyć SemiBold |
| Mały nagłówek | `titleSmall`, 14/20 sp, SemiBold | Tytuł komunikatu lub dialogu |
| Treść podstawowa | `bodyLarge`, 16/24 sp, Normal | Ważny opis i tekst czytelnika |
| Podgląd / opis ustawienia | `bodyMedium`, 14/20 sp, Normal | Podgląd artykułu i opisy |
| Tekst pomocniczy | `bodySmall`, 13/18 sp, Normal | Krótkie wyjaśnienia |
| Metadane | `labelMedium`, 12/16 sp, Medium | Źródło, godzina, status |
| Etykieta drugorzędna | `labelSmall`, 11/16 sp, Medium | Używać oszczędnie |

Nieprzeczytany tytuł wyróżnia grubość i znacznik stanu. Przeczytanej treści nie przygaszamy tak mocno, by traciła czytelność. Istniejąca `Typography` często używa `Bold`; w nowym systemie zostawiamy tę wagę dla mocnych nagłówków, a tytuły wierszy i grup ustawień opieramy głównie na `Medium` lub `SemiBold`. Ustawienia artykułu w czytniku pozostają pod kontrolą preferencji czytelnika; system opisuje przede wszystkim chrom interfejsu.

## Odstępy

Siatka bazuje na wielokrotnościach 4 dp. Wartości odstępów udostępnia jeden obiekt `HReaderSpacing`, zamiast lokalnych, powtarzanych stałych.

| Token | Wartość | Typowe użycie |
| --- | ---: | --- |
| `space1` | 4 dp | Małe powiązane elementy |
| `space2` | 8 dp | Ikona i etykieta, bliskie wiersze |
| `space3` | 12 dp | Zawartość zwartego wiersza |
| `space4` | 16 dp | Margines ekranu i padding komponentu |
| `space5` | 20 dp | Odstęp przed wyróżnioną treścią |
| `space6` | 24 dp | Odstęp między niezależnymi sekcjami |
| `space8` | 32 dp | Początek lub koniec większego bloku treści |

Margines ekranu wynosi 16 dp. Grupy ustawień rozdziela 16 dp; 24 dp stosujemy, gdy zmienia się temat sekcji. Gęstość listy wynika z długości podglądu i odstępów między wierszami, nie z pomniejszania celów dotykowych.

## Kształty i powierzchnie

Wartości odpowiadają `Shapes` w `presentation/theme/Shape.kt`.

| Token Material 3 | Wartość | Użycie |
| --- | ---: | --- |
| `extraSmall` | 4 dp | Mały znacznik |
| `small` | 8 dp | Miniatura, małe pole |
| `medium` | 12 dp | Kontrolka i karta zwartej grupy |
| `large` | 16 dp | Duży dialog lub wyróżniona grupa |
| `extraLarge` | 28 dp | Arkusz modalny |
| pigułka | pełne zaokrąglenie | Filtr i krótki status |

Karty ustawień nie mają mocnego cienia ani kart potomnych. Lista artykułów używa wspólnego tła, separatora o niskim kontraście i bez cienia dla każdego wiersza. Zaznaczenie dostaje wyraźny stan powierzchni.

## Wzorce ekranów i komponentów

### Główny ekran

- Pasek aplikacji pokazuje tytuł i liczbę nieprzeczytanych bez nadawania liczbie większej wagi niż tytułowi.
- Przegląd subskrypcji, wyszukiwanie i odświeżanie pozostają łatwo dostępne. Rzadziej używane akcje trafiają do menu.
- Filtry „Nieprzeczytane” i „Wszystkie” są jedną grupą wyboru. Ich zaznaczenie wynika z kształtu i powierzchni.
- Lista zachowuje nagłówki dat i ich przyklejenie podczas przewijania; nagłówek daty jest wizualnie lżejszy niż artykuł.
- Wiersze pozostają płaskie. Nie używamy jednocześnie karty, cienia, separatora i dużego odstępu dla każdej pozycji.

### Wiersz artykułu

- Wiersz jest jedną pełnoszeroką, dotykalną pozycją. Subtelny separator oddziela artykuły; nie tworzymy osobnej podniesionej karty dla każdego.
- Kolejność informacji: źródło i czas, tytuł, krótki podgląd.
- Tytuł zajmuje do dwóch linii. Podgląd maksymalnie dwie linie; dłuższy tekst nie może wypierać kolejnych pozycji z ekranu.
- Miniatura ma 80 dp, wspólny promień i kadrowanie `Crop`. Gradient pojawia się tylko wtedy, gdy tekst jest nakładany na obraz.
- Treść wiersza używa pełnej szerokości między miniaturą i kontrolką statusu. Poziomą gęstość odzyskujemy krótkim podglądem, nie zmniejszaniem tekstu.
- Nieprzeczytany artykuł używa SemiBold i małego znacznika w kolorze akcentu. Przeczytany używa Normal i nadal zachowuje pełną czytelność.
- Kontrolka zmiany stanu ma własny cel dotykowy 48 dp i nie otwiera artykułu.
- Wiersz, separator i kontrolka zachowują czytelne stany fokusu, zaznaczenia i naciśnięcia.

### Pusty wynik, synchronizacja i błędy

- Brak subskrypcji, brak artykułów i błąd pobierania to różne stany z różnym tekstem.
- Każdy stan wyjaśnia sytuację i wyróżnia jedną główną następną akcję. Działania pomocnicze są tekstowe.
- Błąd używa semantycznego koloru błędu i zapewnia ponowienie; pusty stan używa spokojnych kolorów tekstu.
- Wczytywanie zachowuje docelowy rytm listy, aby ograniczyć skoki układu.
- Główny ekran pokazuje najwyżej jeden globalny baner. Aktywny postęp pobierania offline ma pierwszeństwo, następnie brak połączenia; ostrzeżenie AI pozostaje przy funkcji lub ustawieniu, kiedy zajmuje miejsce.

### Czytnik artykułu

- Tytuł, źródło i metadane tworzą jedną zwartą hierarchię nad treścią.
- Treść artykułu zaczyna się możliwie wysoko. Status AI, wyjaśnienie prywatności i czynności zewnętrzne nie powinny razem zajmować pierwszej połowy ekranu.
- Podsumowanie AI pozostaje zwinięte, dopóki nie ma wyniku. Informację o wysłaniu treści do dostawcy pokazujemy przy świadomym uruchomieniu funkcji.
- Oryginał, udostępnianie i narzędzia zewnętrzne pozostają w menu paska. Nie powtarzamy dużych przycisków nad treścią artykułu.
- Podsumowanie AI i sygnały wiarygodności pozostają dostępnymi, pomocniczymi akcjami. Informacja o wysłaniu tekstu do dostawcy jest czytelna, a świadome potwierdzenie następuje przed wysłaniem.
- Aplikacja styluje własny chrom. HTML z serwera renderuje się w odrębnym WebView i zachowuje semantykę oraz potrzebne style źródła.

### Ustawienia

- Karty grupują ustawienia według celu. Serwer i synchronizacja są łatwe do znalezienia na początku ekranu.
- Zwinięta grupa pokazuje tytuł i podsumowanie do dwóch linii. Szczegóły pozostają wewnątrz po rozwinięciu.
- Zawartość grupy rozwija się na tym samym tle; nie dodajemy kolejnych kart dla kontrolek.
- Tytuły, opisy, pola, przełączniki i strzałki mają stałe wyrównanie oraz odstępy.
- Grupy wieloopcyjne zaczynają zwinięte. Ustawienie samodzielne może być widoczne bez dodatkowego rozwijania.

### Przyciski i komunikaty

- Wypełniony akcent oznacza jedną główną akcję.
- Przycisk tonalny służy akcji pomocniczej; tekstowy lub ikona reprezentuje akcję trzeciorzędną.
- Akcja destrukcyjna używa koloru błędu i prosi o potwierdzenie, jeśli skutek jest trudny do odwrócenia.
- Sukces, ostrzeżenie i błąd mają spójny układ ikony, krótkiego tekstu i odpowiedniej powierzchni.

## Dostępność i ruch

- Cele dotykowe mają co najmniej 48 × 48 dp.
- Tekst podstawowy ma kontrast co najmniej 4,5:1; granica komponentu lub ikona niefunkcyjna nie zastępuje etykiety.
- Stan przeczytania, wybór, ładowanie i błąd są dostępne dla czytnika ekranu i rozpoznawalne bez samego koloru.
- Powiększenie tekstu systemowego nie ucina nazw akcji ani statusów.
- Na emulatorze API 37 sprawdzono listę i czytnik przy skali tekstu 130%; opisy oraz treść zawijają się, a tytuł źródła jest ponownie widoczny w nagłówku artykułu.
- Używamy wspólnego `MotionDuration`: 120 ms dla wyjścia, 140 ms dla szybkiej zmiany i 180 ms dla zmiany standardowej. Animacje respektują wyłączone animacje systemowe.

## Trzy iteracje propozycji

### 1. Perspektywa product managera

Działająca lista potwierdza, że użytkownik musi równocześnie rozpoznać źródło, temat, stan przeczytania i dostępność pełnej treści. Utrzymujemy licznik nieprzeczytanych, filtrowanie oraz niezależną kontrolkę statusu. Szukanie, kanały i synchronizacja zostają dostępne z paska, ale nie konkurują z otwarciem artykułu. Pusty wynik i błąd synchronizacji nadal wymagają odmiennych kolejnych kroków.

### 2. Perspektywa UX designera

Zrzuty z prawdziwymi wpisami pokazały, że obecne wiersze zajmują dużo wysokości przez długie podglądy i mocne powierzchnie kart. W czytniku tytuł, informacja o AI, ostrzeżenie o wysyłce i kilka akcji odsuwają treść artykułu w dół. Iteracja UX ogranicza podgląd do dwóch linii, zachowuje miniaturę 80 dp, spłaszcza rytm listy i zwija elementy AI oraz akcje zewnętrzne.

### 3. Perspektywa senior software engineera

System pozostaje w `presentation/theme`: semantyczne role Material 3, typografia, kształty i wspólny obiekt odstępów. Nie dodajemy zależności ani kroju pisma. Lista nadal używa Paging 3 i `LazyColumn`; lista artykułów nie może być wczytywana w całości. Treść Miniflux jest pobierana przez `fetch-content`, a zasoby mocka korzystają z hosta bazowego artykułu. Skórka dotyczy chromu aplikacji i nie nadpisuje globalnie HTML serwera.

### Wynik iteracji

Propozycja łączy szybką ocenę artykułów z krótszą drogą do czytania: płaska lista, krótsze podglądy, widoczny status bez nadmiernie dużych kart oraz czytnik z ograniczoną liczbą elementów przed treścią. Karty zostają tam, gdzie grupują ustawienia lub stan pomocniczy. Wszystkie decyzje pozostają w ramach jednego ciemnego motywu i obecnego stosu Compose/Material 3.

## Iteracja wdrożeniowa

1. **Product manager:** lista pokazuje stan, źródło i temat bez przewijania długich podglądów; podstawowa droga nadal prowadzi do otwarcia artykułu. Akcje zewnętrzne są dostępne z menu czytnika, ale nie odpychają treści.
2. **UX designer:** zrzuty obecnej aplikacji ujawniły ciężkie, podniesione karty i cztery linie podglądu na liście oraz kilka przycisków przed treścią czytnika. Wdrożenie spłaszcza wiersze, ogranicza podgląd do dwóch linii, zmniejsza miniaturę i skraca nagłówek czytnika.
3. **Senior software engineer:** zmiany korzystają z istniejącego Material 3 i Paging 3, nie dodają bibliotek i nie zmieniają modelu artykułu ani przepływu synchronizacji. Stan odczytu i osobny cel dotykowy checkboxa pozostają bez zmian. Zewnętrzne akcje są nadal dostępne z paska. Jeśli adres grafiki wprowadzającej powtarza się w HTML artykułu, nagłówek pomija kopię i pozostawia oryginalną grafikę w treści.

## Kolejność wdrażania

1. **Wykonane w v1.3:** zachować semantyczną paletę ciemną, ujednolicić wagi typografii, dodać tokeny odstępów i zastosować je do filtra, listy oraz czytnika.
2. **Sprawdzone na emulatorze:** lista z danymi, czytnik na początku i po przewinięciu, duży tekst systemowy oraz brak powtórzonej grafiki nagłówkowej.
3. **Następnie:** dopracować puste wyniki, błędy, ładowanie, tryb offline i postęp pobierania.
4. **Potem:** ujednolicić grupy ustawień, pola, arkusze i dialogi oraz sprawdzić ich układ zwinięty i rozwinięty.

Nowy komponent najpierw korzysta z istniejących tokenów Material 3. Własny token dodajemy, gdy rozwiązuje powtarzającą się potrzebę.
