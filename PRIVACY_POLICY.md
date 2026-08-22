# Polityka prywatności hReader

Ostatnia aktualizacja: 22 sierpnia 2026 r.

## Wydawca i kontakt

Wydawcą aplikacji hReader jest Oskar Drozda.

Punkt kontaktu w sprawach prywatności: [profil GitHub wydawcy](https://github.com/Hiosdra).

## Zakres polityki

hReader jest aplikacją kliencką do samodzielnie utrzymywanego czytnika RSS. Aplikacja nie
prowadzi własnego konta użytkownika, nie ma własnego backendu wydawcy i nie sprzedaje danych.
Nie używamy reklam, analityki behawioralnej ani dodatkowych trackerów.

Ta polityka opisuje dane, do których hReader uzyskuje dostęp, które przechowuje lokalnie lub
przesyła w celu wykonania funkcji wybranych przez użytkownika. Informacje przetwarzane przez
Google Play podczas instalacji, aktualizacji lub obsługi aplikacji są przetwarzane przez Google
zgodnie z [polityką prywatności Google](https://policies.google.com/privacy). hReader nie otrzymuje
tych danych od Google.

## Dane przechowywane na urządzeniu

hReader może przechowywać lokalnie:

- adres i typ wybranego serwera FreshRSS lub Miniflux;
- nazwę użytkownika FreshRSS oraz hasło API lub token API Miniflux;
- opcjonalny klucz API OpenRouter;
- ustawienia aplikacji, w tym ustawienie raportowania awarii;
- zsynchronizowane kanały, tytuły, autorów, daty, linki, treść artykułów, obrazy oraz stan
  przeczytania i oznaczenia artykułów;
- wygenerowane lokalnie wyniki podsumowań i analiz wiarygodności.

Powyższe dane są przechowywane w prywatnym obszarze aplikacji. Plik zawierający dane logowania
i klucz OpenRouter jest wyłączony z kopii zapasowych i transferu urządzenia. Dane lokalne można
usunąć z poziomu ustawień aplikacji albo przez wyczyszczenie danych aplikacji lub jej
odinstalowanie.

## Połączenia z serwerami RSS

Użytkownik sam wskazuje instancję FreshRSS albo Miniflux, z którą łączy się hReader. Aplikacja
przesyła do tej instancji dane wymagane przez wybrany protokół, w szczególności dane logowania
lub token, żądania synchronizacji, listę subskrypcji, metadane i treści artykułów oraz zmiany
stanu przeczytania i oznaczenia.

Dokumentacja używanych protokołów:

- [FreshRSS Google Reader API](https://freshrss.github.io/FreshRSS/en/developers/06_GoogleReader_API.html);
- [Miniflux API](https://miniflux.app/docs/api.html).

Operator skonfigurowanej instancji FreshRSS lub Miniflux może przetwarzać i rejestrować te dane,
adres IP, czas żądania oraz inne informacje wynikające z konfiguracji serwera. Oskar Drozda nie
kontroluje retencji ani logów tej instancji. W sprawie danych przechowywanych przez serwer należy
skontaktować się z jego operatorem.

## OpenRouter

Funkcje podsumowania i analizy wiarygodności są opcjonalne. Po zapisaniu własnego klucza API
OpenRouter i uruchomieniu jednej z tych funkcji hReader wysyła bezpośrednio do OpenRouter:

- tytuł i tekst artykułu dla podsumowania;
- tekst artykułu oraz ograniczone metadane, takie jak tytuł, autor, nazwa kanału, domena
  wydawcy i data publikacji, dla analizy wiarygodności;
- wybrany identyfikator modelu i dane techniczne żądania.

OpenRouter może przekazać żądanie do wybranego dostawcy modelu zgodnie ze swoimi warunkami
i polityką prywatności. Szczegóły znajdują się w
[polityce prywatności OpenRouter](https://openrouter.ai/privacy). hReader nie otrzymuje klucza
API OpenRouter poza lokalnym urządzeniem i nie wysyła treści artykułów do OpenRouter bez
uruchomienia funkcji AI przez użytkownika.

Lista dostępnych modeli OpenRouter może być pobierana przy otwieraniu ustawień AI lub po ręcznym
odświeżeniu. To żądanie nie zawiera treści artykułów, ale serwery OpenRouter mogą widzieć
standardowe dane połączenia, takie jak adres IP i czas żądania. Jeżeli użytkownik wybierze lokalny
model Gemma, tytuł i treść artykułu są przetwarzane na urządzeniu i nie są wysyłane do OpenRouter
ani do dostawcy zewnętrznego. Sam model Gemma jest pobierany z Hugging Face; ten transfer dotyczy
pliku modelu, a nie treści artykułów.

## Raporty awarii i diagnostyka Sentry

Raportowanie awarii i diagnostyki Sentry jest włączone domyślnie, gdy aplikacja ma skonfigurowany
serwer Sentry. Użytkownik może wyłączyć je w dowolnym momencie w sekcji ustawień prywatności
i diagnostyki. Po wyłączeniu hReader nie wysyła kolejnych raportów.

Włączone raportowanie może obejmować informacje techniczne potrzebne do diagnozy, takie jak
wyjątek, stos wywołań, wersja aplikacji, wersja Androida, model urządzenia i oznaczenie
komponentu aplikacji. hReader wyłącza domyślne PII, zrzuty ekranu, hierarchię widoku, sesje,
NDK, automatyczne breadcrumbs i automatyczne śledzenie sesji. Sentry przetwarza dane zgodnie
z [własną polityką prywatności](https://sentry.io/privacy/).

## Inne połączenia z siecią

hReader może bezpośrednio pobierać kanały, strony artykułów, obrazy i zasoby potrzebne do
wyświetlenia treści z adresów znajdujących się w konfiguracji lub w dostarczonych artykułach.
Może również pobierać modele TTS z ich źródeł, gdy użytkownik włączy tę funkcję, oraz model Gemma
z Hugging Face. Serwery tych źródeł mogą widzieć standardowe dane żądania sieciowego, takie jak
adres IP i czas połączenia, zgodnie z własnymi zasadami. hReader nie tworzy z tych danych własnego
profilu użytkownika.

## Usługi otwierania artykułów przez serwisy zewnętrzne

Funkcja wyboru serwisu do otwierania artykułu z pominięciem paywalla jest opcjonalna. Po jej użyciu
hReader otwiera adres wybranego serwisu, przekazując mu adres oryginalnego artykułu. Dostępne
serwisy to: Smry.ai, RemovePaywall.com, RemovePaywalls.com, PaywallBuster, Archive.ph, Wayback
Machine, Archive Buttons oraz Bypass Paywall Reader. Są to niezależne serwisy zewnętrzne, a nie
backend wydawcy hReader. Ich operatorzy mogą otrzymać adres artykułu, adres IP, czas żądania oraz
inne standardowe dane przeglądarki i stosują własne zasady prywatności oraz retencji.

Poza skonfigurowanym serwerem RSS, opcjonalnym OpenRouterem, opcjonalnym Sentry oraz
połączeniami koniecznymi do pobrania żądanej treści hReader nie komunikuje się z żadnym
własnym backendem wydawcy i nie zbiera dodatkowych danych.

## Udostępnianie danych

Nie sprzedajemy danych i nie udostępniamy ich w celach reklamowych. Dane są przekazywane
wyłącznie:

1. do serwera FreshRSS lub Miniflux wskazanego przez użytkownika;
2. do OpenRoutera, gdy użytkownik korzysta z funkcji AI;
3. do Sentry, gdy raportowanie diagnostyczne jest włączone;
4. do serwerów źródłowych, gdy jest to konieczne do pobrania żądanej treści lub modelu;
5. do wybranego serwisu zewnętrznego, gdy użytkownik korzysta z funkcji otwierania artykułu przez
   serwis z pominięciem paywalla.

## Okres przechowywania i usuwanie

Dane lokalne pozostają na urządzeniu do czasu ich usunięcia przez użytkownika z ustawień,
wyczyszczenia danych aplikacji lub odinstalowania hReader. Dane przechowywane przez serwer RSS,
OpenRouter, Sentry, serwis zewnętrzny albo serwer źródłowy podlegają zasadom ich operatorów.
Wyłączenie Sentry zatrzymuje przyszłe wysyłanie, ale nie usuwa automatycznie zdarzeń już
dostarczonych do Sentry.

W sprawach dotyczących danych lokalnych lub niniejszej polityki można skontaktować się przez
[GitHub wydawcy](https://github.com/Hiosdra). Usunięcie danych z zewnętrznego serwera wymaga
kontaktu z jego operatorem.

## Bezpieczeństwo

hReader korzysta z prywatnego magazynu aplikacji i wyklucza sekrety z kopii zapasowych oraz
transferu urządzenia. W wydaniu produkcyjnym aplikacja nie zezwala na komunikację HTTP bez
szyfrowania; wyjątek dla HTTP jest dostępny wyłącznie w wariancie debug do lokalnych serwerów
testowych. Użytkownik odpowiada za bezpieczeństwo i konfigurację własnej instancji RSS oraz
za ochronę klucza OpenRouter.

## Zmiany polityki

Polityka może zostać zaktualizowana, gdy zmieni się sposób działania aplikacji lub wymagania
prawne. Aktualna wersja jest publikowana razem z kodem źródłowym hReader.
