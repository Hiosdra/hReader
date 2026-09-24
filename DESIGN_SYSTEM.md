# System wizualny hReader

Wersja robocza 1.3 · zakres: motyw ciemny

## Zasady

- Spokojny, edytorialny wygląd: ciepły grafit, piaskowy akcent i stonowane kolory stanów.
- Treść artykułu jest ważniejsza niż chrom aplikacji; ekran ma jedną główną akcję.
- Kolory są semantyczne. Stan jest czytelny także bez koloru.
- Lista artykułów pozostaje płaska. Karty grupują ustawienia lub powiązane stany.
- Odstępy, typografia i kształty pochodzą ze wspólnego zestawu tokenów Material 3.

## Kolory

Wartości odpowiadają `DarkColorScheme` w `presentation/theme/Color.kt`.

| Rola | Kolor |
| --- | --- |
| Tło | `#181611` |
| Powierzchnia niska | `#221F19` |
| Powierzchnia grupy | `#2C2722` |
| Powierzchnia podbita | `#35312A` |
| Tekst główny | `#E7E4DE` |
| Tekst pomocniczy | `#CBC5BB` |
| Akcent | `#E0C295` |
| Akcent pomocniczy | `#D4C2AE` |
| Sukces / ostrzeżenie / błąd | `#A4D388` / `#E7C358` / `#FFB3A3` |
| Obrys subtelny | `#4B4640` |

Tekst na kolorowej powierzchni używa pasującej roli `on*`. Sukces, ostrzeżenie i błąd oznaczamy również tekstem lub ikoną; `error` rezerwujemy dla rzeczywistych błędów.

## Typografia

Domyślny krój systemowy i skala Material 3. Używamy `Bold` oszczędnie; tytuły opierają się na `Medium` lub `SemiBold`.

| Rola | Styl |
| --- | --- |
| Tytuł artykułu | `headlineSmall`, 24/32 sp, SemiBold |
| Tytuł widoku | `titleLarge`, 22/28 sp, SemiBold |
| Tytuł wiersza lub grupy | `titleMedium`, 16/24 sp, Medium |
| Mały nagłówek | `titleSmall`, 14/20 sp, SemiBold |
| Treść artykułu | `bodyLarge`, 16/24 sp |
| Podgląd i opis | `bodyMedium`, 14/20 sp |
| Tekst pomocniczy | `bodySmall`, 13/18 sp |
| Metadane / etykieta | `labelMedium`, 12/16 sp / `labelSmall`, 11/16 sp |

Ustawienia czytania artykułu pozostają pod kontrolą preferencji użytkownika.

## Odstępy i kształty

`HReaderSpacing` udostępnia siatkę 4 dp: `space1`–`space6` oraz `space8` = 4, 8, 12, 16, 20, 24 i 32 dp. Margines ekranu wynosi 16 dp; niezależne sekcje rozdziela 24 dp.

Kształty Material 3: 4, 8, 12, 16 i 28 dp; pełne zaokrąglenie dla filtrów i krótkich statusów. Cień nie jest domyślnym separatorem. Nie zagnieżdżamy kart.

## Komponenty

- **Lista:** filtry „Nieprzeczytane” i „Wszystkie” tworzą jedną grupę. Zachowujemy nagłówki dat i lekkie separatory.
- **Wiersz artykułu:** źródło i czas, tytuł do dwóch linii, podgląd do dwóch linii. Miniatura ma 80 dp i kadrowanie `Crop`.
- **Stan odczytu:** nieprzeczytany tytuł jest SemiBold i ma znacznik akcentu; przeczytany pozostaje czytelny. Kontrolka stanu ma osobny cel 48 dp.
- **Czytnik:** tytuł, źródło i metadane tworzą zwarty nagłówek. Oryginał, udostępnianie i narzędzia zewnętrzne są w menu paska.
- **AI:** podsumowanie pozostaje zwinięte, dopóki nie ma wyniku. Informacja o wysłaniu treści i potwierdzenie są widoczne przed wysłaniem.
- **Grafiki:** gdy URL grafiki nagłówkowej występuje już w HTML artykułu, nie pokazujemy drugiej kopii. Style aplikacji nie nadpisują HTML źródła.
- **Ustawienia:** karty grupują temat; zwinięta grupa pokazuje tytuł i podsumowanie do dwóch linii. Kontrolki pozostają na tym samym tle.
- **Stany i akcje:** pusty wynik, brak subskrypcji i błąd mają odrębny tekst i właściwą następną akcję. Akcent wypełniony oznacza akcję główną, tonalny — pomocniczą, tekstowy — trzecią. Destrukcyjne akcje wymagają potwierdzenia, jeśli skutek jest trudny do odwrócenia.

## Dostępność i ruch

- Cele dotykowe mają co najmniej 48 × 48 dp; stan nie zależy wyłącznie od koloru.
- Tekst podstawowy ma kontrast co najmniej 4,5:1. Nazwy akcji i statusy zachowują czytelność przy powiększeniu tekstu.
- Lista i czytnik sprawdzone na emulatorze API 37 przy skali tekstu 130%.
- `MotionDuration`: 120 ms dla wyjścia, 140 ms dla szybkiej zmiany, 180 ms dla standardowej. Respektujemy wyłączone animacje systemowe.

## Trzy perspektywy przeglądu

- **Produkt:** lista pokazuje temat, źródło i stan; filtr, otwarcie artykułu i zmiana stanu pozostają czytelne.
- **UX:** krótsze podglądy i płaskie wiersze ułatwiają skanowanie, a zwarty nagłówek szybciej odsłania treść.
- **Inżynieria:** korzystamy z Material 3 i Paging 3 bez nowych zależności; nie zmieniamy modeli ani synchronizacji, a HTML serwera zostaje odrębny.

## Zakres i walidacja

Wersja 1.4 obejmuje tokeny oraz główne wzorce listy i czytnika. Następny krok to zastosowanie ich do ustawień, pustych stanów, błędów i trybu offline. Przepływ z przykładowymi artykułami sprawdzono na emulatorze API 37 z lokalnym [Miniflux mockiem](docs/local-testing.md).
