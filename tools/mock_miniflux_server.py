import argparse
import copy
import html
import json
import math
import struct
import threading
import time
import zlib
from datetime import datetime, timedelta, timezone
from functools import lru_cache
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit


FEEDS = {
    11: {
        "title": "Pracownia Jutra",
        "site_url": "/sources/pracownia-jutra",
        "feed_url": "/feeds/pracownia-jutra.xml",
    },
    12: {
        "title": "Miasto Pieszo",
        "site_url": "/sources/miasto-pieszo",
        "feed_url": "/feeds/miasto-pieszo.xml",
    },
    13: {
        "title": "Atlas Natury",
        "site_url": "/sources/atlas-natury",
        "feed_url": "/feeds/atlas-natury.xml",
    },
}

ARTICLE_FIXTURES = [
    {
        "id": 1012,
        "feed_id": 11,
        "title": "Miasto można ochłodzić, zanim nadejdą upały",
        "author": "Natalia Wrona",
        "hours_ago": 1,
        "status": "unread",
        "artwork": "city",
        "caption": "Poranny pomiar temperatury na dachu biblioteki.",
        "preview": "Zespół badaczy sprawdził, jak drzewa, jasne dachy i niewielkie zbiorniki wody wpływają na temperaturę w gęstej zabudowie.",
        "paragraphs": [
            "Największa różnica nie pojawiła się na szerokim placu ani przy nowym parku. Czujniki wskazały ją na zwykłej ulicy, gdzie cień z kilku starych drzew utrzymywał się przez większość popołudnia.",
            "Przez całe lato niewielkie stacje pomiarowe rejestrowały temperaturę, wilgotność i nasłonecznienie. Dane zestawiono z mapą materiałów użytych na dachach, chodnikach i elewacjach.",
            "Wyniki pokazują, że chłodzenie miasta nie zależy od jednego wielkiego projektu. Najlepsze efekty daje połączenie zieleni, przepuszczalnej nawierzchni i miejsc, w których można zatrzymać wodę po deszczu.",
            "Autorzy udostępnili mapę pomiarów mieszkańcom. Można na niej porównać dwie sąsiednie ulice i zobaczyć, jak zmienia się temperatura w kolejnych godzinach dnia.",
        ],
    },
    {
        "id": 1011,
        "feed_id": 12,
        "title": "Na tej ulicy pierwszeństwo ma cień",
        "author": "Michał Bury",
        "hours_ago": 4,
        "status": "unread",
        "artwork": "architecture",
        "caption": "Podwórko po przebudowie, widok od strony bramy.",
        "preview": "Projektanci zaczęli od spaceru z mieszkańcami. Dopiero potem powstał plan nowych nasadzeń, ławek i przejść między podwórkami.",
        "paragraphs": [
            "Zmiana zaczęła się od prostego pytania: w którym miejscu chcecie usiąść w upalny dzień? Mieszkańcy wskazali skrawek podwórka, który na planie wyglądał jak przestrzeń techniczna.",
            "Dziś rosną tam drzewa, a przy wejściach pojawiły się ławki. Nawierzchnię podzielono tak, by deszczówka mogła wsiąkać przy korzeniach zamiast spływać do kanalizacji.",
            "Najważniejsza okazała się kolejność decyzji. Najpierw określono codzienne potrzeby, potem wyznaczono ruch pieszy, a dopiero na końcu dobrano małą architekturę.",
        ],
    },
    {
        "id": 1010,
        "feed_id": 13,
        "title": "Łąki na dachach pomagają owadom znaleźć drogę",
        "author": "Lena Stępień",
        "hours_ago": 9,
        "status": "read",
        "artwork": "garden",
        "caption": "Kwitnące rośliny na dachu budynku badawczego.",
        "preview": "Kilka niewielkich zielonych powierzchni może połączyć odizolowane siedliska. W nowym badaniu sprawdzono, które rośliny przyciągają najwięcej zapylaczy.",
        "paragraphs": [
            "Badacze porównali dachy o różnej wysokości i składzie roślinnym. Najwięcej owadów odwiedzało miejsca, w których przez cały sezon kwitły różne gatunki.",
            "Ważna była także odległość od zielonych skwerów na poziomie ulicy. Dachy położone bliżej istniejących siedlisk częściej stawały się przystankiem na trasie owadów.",
            "Zespół przygotował listę roślin odpornych na wiatr i krótkie okresy suszy. Ma pomóc zarządcom budynków planować nasadzenia bez kosztownego systemu nawadniania.",
        ],
    },
    {
        "id": 1009,
        "feed_id": 11,
        "title": "Nowy materiał izolacyjny powstaje z włókien roślinnych",
        "author": "Tomasz Lis",
        "hours_ago": 27,
        "status": "unread",
        "artwork": "lab",
        "caption": "Próbki włókien przed testem przewodzenia ciepła.",
        "preview": "Zespół z małego laboratorium opracował płyty, które można naprawić bez wymiany całej warstwy ściany.",
        "paragraphs": [
            "Nowa mieszanka wykorzystuje włókna pozostałe po przetwarzaniu roślin. Łączy je spoiwo, które można rozdzielić podczas naprawy lub recyklingu.",
            "Próbki przechodzą teraz cykle wilgotności i temperatury. Badacze sprawdzają, czy po kilku sezonach zachowają ten sam kształt i właściwości izolacyjne.",
            "Projektanci budynków zwracają uwagę na jeszcze jedną zaletę: płyty można dociąć zwykłymi narzędziami, a uszkodzony fragment wymienić miejscowo.",
        ],
    },
    {
        "id": 1008,
        "feed_id": 12,
        "title": "Czy spokojniejszy transport może być też szybszy?",
        "author": "Oskar Nowak",
        "hours_ago": 32,
        "status": "read",
        "artwork": None,
        "caption": "",
        "preview": "W pilotażowym projekcie zmieniono sygnalizację na sześciu skrzyżowaniach. Piesi i rowerzyści zyskali bardziej przewidywalne przejścia.",
        "paragraphs": [
            "Zamiast zwiększać prędkość pojedynczych pojazdów, projektanci skrócili czas oczekiwania na światłach i usunęli kilka nieczytelnych objazdów.",
            "Po miesiącu obserwacji większość podróży w centrum trwała tyle samo lub krócej. Zmniejszyła się za to liczba sytuacji, w których piesi przebiegali przez skrzyżowanie pod koniec cyklu.",
            "Miasto opublikuje dane z kolejnego etapu i zdecyduje, które zmiany zostaną na stałe.",
        ],
    },
    {
        "id": 1007,
        "feed_id": 13,
        "title": "Niewielkie mokradła zatrzymują wodę blisko miejsca, gdzie spadła",
        "author": "Joanna Wójcik",
        "hours_ago": 52,
        "status": "unread",
        "artwork": "water",
        "caption": "Płytki zbiornik po nocnym deszczu.",
        "preview": "W kilku wsiach odtworzono małe zbiorniki i rowy porośnięte roślinami. Monitoring ma pokazać, jak wpływają na lokalne podtopienia.",
        "paragraphs": [
            "Po intensywnym deszczu woda trafia do płytkich zagłębień, gdzie powoli wsiąka w grunt. Taki układ nie zastąpi dużej infrastruktury, ale może zmniejszyć obciążenie kanalizacji.",
            "Mieszkańcy razem z hydrologami zaznaczyli miejsca, w których woda regularnie zalewa ścieżki i ogrody. Część rowów odtworzono bez betonowych koryt.",
            "Wiosną zespół porówna pomiary z mapami sprzed przebudowy i sprawdzi, czy woda utrzymuje się dłużej również w czasie suszy.",
        ],
    },
    {
        "id": 1006,
        "feed_id": 11,
        "title": "Projektowanie cyfrowych usług zaczyna się od dobrego pytania",
        "author": "Iga Pawlik",
        "hours_ago": 75,
        "status": "unread",
        "artwork": "culture",
        "caption": "Warsztat projektowy w lokalnej bibliotece.",
        "preview": "Zamiast kolejnego zestawu funkcji zespół przez tydzień obserwował, jak mieszkańcy załatwiają sprawy bez telefonu.",
        "paragraphs": [
            "Pierwszy prototyp nie miał jeszcze kolorów ani ikon. Była w nim tylko lista kroków, które trzeba przejść, by znaleźć odpowiednią usługę.",
            "Rozmowy ujawniły, że ludzie nie potrzebowali dodatkowego panelu. Chcieli wiedzieć, jaki dokument przygotować i ile czasu zajmie wizyta.",
            "Zespół zmienił kolejność informacji i dopiero wtedy zaprojektował ekran. Liczba elementów spadła, a użytkownicy rzadziej wracali do poprzednich kroków.",
        ],
    },
    {
        "id": 1005,
        "feed_id": 12,
        "title": "Biblioteka otwiera się na podwórko",
        "author": "Michał Bury",
        "hours_ago": 105,
        "status": "read",
        "artwork": "architecture",
        "caption": "Czytelnia z wejściem od strony ogrodu.",
        "preview": "Nowe wejście skraca drogę z przystanku i łączy czytelnię z ogrodem sąsiedzkim.",
        "paragraphs": [
            "Największą zmianą nie była nowa sala, lecz drzwi prowadzące na podwórko. Czytelnia działa teraz jak część spacerowej trasy przez dzielnicę.",
            "W środku pozostawiono materiały z poprzedniego wyposażenia. Stare drewniane blaty przerobiono na długi stół do spotkań i pracy.",
            "Projekt powstał po serii otwartych konsultacji i będzie oceniany przez pierwszy rok działania.",
        ],
    },
    {
        "id": 1004,
        "feed_id": 13,
        "title": "Jesienne migracje widać także z miejskiego balkonu",
        "author": "Lena Stępień",
        "hours_ago": 145,
        "status": "unread",
        "artwork": "garden",
        "caption": "Ptaki odpoczywające na zielonym dachu.",
        "preview": "Obserwatorzy z kilku dzielnic przesyłają krótkie notatki o przelotach. Zebrane dane pomagają wskazać miejsca odpoczynku.",
        "paragraphs": [
            "Do udziału wystarczy kilka minut i notatka z datą. Każde zgłoszenie przechodzi kontrolę, a mapa pokazuje wyłącznie informacje potrzebne do obserwacji sezonowych zmian.",
            "Najwięcej obserwacji pochodzi z parków, ale w tym roku pojawiło się więcej zgłoszeń z zielonych dachów i balkonów.",
            "Organizatorzy przygotowali krótką instrukcję, która pomaga odróżnić kilka podobnych gatunków bez specjalistycznego sprzętu.",
        ],
    },
    {
        "id": 1003,
        "feed_id": 11,
        "title": "Warsztat naprawczy, który mieści się w dawnej portierni",
        "author": "Tomasz Lis",
        "hours_ago": 190,
        "status": "read",
        "artwork": None,
        "caption": "",
        "preview": "Sąsiedzi przynoszą małe urządzenia, a wolontariusze pomagają znaleźć części i narzędzia.",
        "paragraphs": [
            "Najpierw miała to być jednorazowa sobota napraw. Po trzech miesiącach dawna portiernia otwiera się co tydzień.",
            "Na miejscu można pożyczyć podstawowe narzędzia, skonsultować usterkę i sprawdzić, czy część da się wymienić bez kupowania nowego urządzenia.",
            "Wolontariusze podkreślają, że równie ważne jak naprawy są rozmowy i wymiana umiejętności między sąsiadami.",
        ],
    },
    {
        "id": 1002,
        "feed_id": 12,
        "title": "Krótsza droga do szkoły nie zawsze jest najbezpieczniejsza",
        "author": "Oskar Nowak",
        "hours_ago": 250,
        "status": "unread",
        "artwork": "city",
        "caption": "Przejście szkolne po porannym szczycie.",
        "preview": "Rodzice i dzieci wspólnie przeszli codzienne trasy. Z mapy powstał plan drobnych zmian w oznakowaniu i oświetleniu.",
        "paragraphs": [
            "Uczestnicy zaznaczali miejsca, w których trudno zobaczyć nadjeżdżający rower albo przejść przez ruchliwą ulicę.",
            "Część zmian można wykonać szybko: przesunąć znak, przyciąć krzewy lub poprawić oświetlenie. Inne wymagają przebudowy skrzyżowań.",
            "Szkoła udostępni plan tras, aby po semestrze porównać go z nowymi obserwacjami.",
        ],
    },
    {
        "id": 1001,
        "feed_id": 13,
        "title": "Ścieżka przy rzece odzyskuje naturalny brzeg",
        "author": "Joanna Wójcik",
        "hours_ago": 330,
        "status": "read",
        "artwork": "water",
        "caption": "Nowa roślinność przy ścieżce nad rzeką.",
        "preview": "W miejscu betonowego umocnienia pojawiły się łagodne zejścia, rośliny i punkty obserwacji wody.",
        "paragraphs": [
            "Remont objął krótki fragment brzegu przy parku. Ścieżka nadal jest dostępna, a kilka miejsc pozwala podejść bliżej wody bez niszczenia roślin.",
            "Poziom rzeki i stan roślin będą monitorowane przez lokalną szkołę i zespół hydrologów.",
            "Jeśli eksperyment się sprawdzi, podobne rozwiązania pojawią się w dwóch kolejnych punktach miasta.",
        ],
    },
]


ARTWORK_PALETTES = {
    "city": ((31, 51, 64), (176, 117, 83)),
    "architecture": ((74, 70, 61), (202, 161, 113)),
    "garden": ((43, 71, 53), (177, 167, 111)),
    "lab": ((42, 47, 69), (159, 125, 104)),
    "water": ((34, 57, 72), (83, 128, 129)),
    "culture": ((102, 62, 50), (221, 181, 130)),
}

IMAGE_WIDTH = 480
IMAGE_HEIGHT = 320
STATE_LOCK = threading.RLock()


def make_state(base_url):
    now = datetime.now(timezone.utc).replace(microsecond=0)
    feeds = {
        feed_id: {
            "id": feed_id,
            "title": value["title"],
            "site_url": f"{base_url}{value['site_url']}",
            "feed_url": f"{base_url}{value['feed_url']}",
        }
        for feed_id, value in FEEDS.items()
    }
    entries = {}
    for fixture in ARTICLE_FIXTURES:
        published_at = now - timedelta(hours=fixture["hours_ago"])
        image_url = (
            f"{base_url}/assets/{fixture['artwork']}.png"
            if fixture["artwork"]
            else None
        )
        paragraphs = "".join(
            f"<p>{html.escape(paragraph)}</p>" for paragraph in fixture["paragraphs"]
        )
        image_html = (
            f'<figure class="hero"><img src="{image_url}" alt="{html.escape(fixture["caption"])}">'
            f"<figcaption>{html.escape(fixture['caption'])}</figcaption></figure>"
            if image_url
            else ""
        )
        entries[fixture["id"]] = {
            **fixture,
            "published_at": published_at.isoformat(),
            "changed_at": now.timestamp(),
            "url": f"{base_url}/articles/{fixture['id']}",
            "image_url": image_url,
            "image_html": image_html,
            "body_html": paragraphs,
            "excerpt_html": f"{image_html}<p>{html.escape(fixture['preview'])}</p>",
            "full_html": f"{image_html}{paragraphs}",
            "reading_time": max(2, round(sum(map(len, fixture["paragraphs"])) / 950)),
        }
    return feeds, entries


def png_chunk(kind, payload):
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def fill_rectangle(pixels, left, top, right, bottom, color):
    left = max(0, min(IMAGE_WIDTH, left))
    right = max(0, min(IMAGE_WIDTH, right))
    top = max(0, min(IMAGE_HEIGHT, top))
    bottom = max(0, min(IMAGE_HEIGHT, bottom))
    row = bytes(color) * max(0, right - left)
    for y in range(top, bottom):
        offset = (y * IMAGE_WIDTH + left) * 3
        pixels[offset : offset + len(row)] = row


def fill_circle(pixels, center_x, center_y, radius, color):
    for y in range(max(0, center_y - radius), min(IMAGE_HEIGHT, center_y + radius)):
        distance_y = y - center_y
        span = int(math.sqrt(max(0, radius * radius - distance_y * distance_y)))
        fill_rectangle(pixels, center_x - span, y, center_x + span, y + 1, color)


def draw_city(pixels):
    fill_circle(pixels, 350, 86, 44, (235, 199, 139))
    fill_rectangle(pixels, 0, 210, 112, 320, (37, 49, 51))
    fill_rectangle(pixels, 88, 168, 204, 320, (48, 56, 53))
    fill_rectangle(pixels, 184, 195, 292, 320, (39, 49, 48))
    fill_rectangle(pixels, 278, 142, 385, 320, (52, 57, 51))
    fill_rectangle(pixels, 370, 187, 480, 320, (39, 50, 52))
    for x in (16, 45, 116, 148, 213, 248, 302, 335, 396, 432):
        for y in (205, 238, 271):
            fill_rectangle(pixels, x, y, x + 11, y + 13, (226, 185, 116))


def draw_architecture(pixels):
    fill_rectangle(pixels, 40, 91, 438, 320, (69, 69, 59))
    fill_rectangle(pixels, 72, 66, 406, 106, (186, 145, 99))
    fill_rectangle(pixels, 89, 116, 391, 320, (116, 97, 70))
    fill_rectangle(pixels, 205, 193, 282, 320, (45, 50, 45))
    for x in (112, 160, 300, 348):
        fill_rectangle(pixels, x, 133, x + 23, 175, (229, 191, 131))
        fill_rectangle(pixels, x, 212, x + 23, 254, (229, 191, 131))
    fill_rectangle(pixels, 0, 292, 480, 320, (43, 51, 44))


def draw_garden(pixels):
    fill_circle(pixels, 370, 89, 51, (225, 199, 131))
    fill_rectangle(pixels, 0, 246, 480, 320, (42, 62, 45))
    fill_rectangle(pixels, 228, 143, 248, 292, (88, 67, 48))
    for center_x, center_y, radius, color in (
        (180, 161, 73, (89, 117, 69)),
        (258, 139, 84, (69, 101, 61)),
        (320, 177, 68, (107, 129, 74)),
        (121, 211, 56, (124, 132, 78)),
    ):
        fill_circle(pixels, center_x, center_y, radius, color)
    for x, y, width in ((54, 245, 18), (97, 222, 14), (366, 247, 16), (414, 225, 14)):
        fill_rectangle(pixels, x, y, x + width, y + 42, (106, 82, 47))


def draw_lab(pixels):
    fill_circle(pixels, 247, 160, 101, (213, 178, 126))
    fill_circle(pixels, 247, 160, 66, (80, 109, 98))
    fill_circle(pixels, 247, 160, 27, (231, 214, 171))
    fill_rectangle(pixels, 52, 76, 68, 244, (221, 175, 113))
    fill_rectangle(pixels, 83, 121, 99, 244, (129, 150, 130))
    fill_rectangle(pixels, 381, 52, 397, 244, (129, 150, 130))
    fill_rectangle(pixels, 413, 103, 429, 244, (221, 175, 113))
    fill_rectangle(pixels, 34, 255, 445, 270, (50, 58, 61))


def draw_water(pixels):
    fill_circle(pixels, 361, 83, 39, (232, 199, 135))
    fill_rectangle(pixels, 0, 169, 480, 320, (46, 91, 98))
    fill_rectangle(pixels, 0, 184, 480, 195, (91, 139, 132))
    fill_rectangle(pixels, 0, 225, 480, 234, (83, 128, 123))
    fill_rectangle(pixels, 0, 273, 480, 280, (74, 116, 115))
    fill_rectangle(pixels, 0, 135, 117, 170, (53, 74, 72))
    fill_rectangle(pixels, 87, 113, 252, 170, (43, 66, 69))
    fill_rectangle(pixels, 224, 142, 480, 170, (51, 73, 74))


def draw_culture(pixels):
    fill_circle(pixels, 119, 104, 64, (230, 195, 131))
    fill_rectangle(pixels, 197, 67, 425, 282, (98, 61, 51))
    fill_rectangle(pixels, 229, 99, 394, 250, (202, 141, 104))
    fill_rectangle(pixels, 260, 129, 363, 250, (62, 57, 50))
    fill_rectangle(pixels, 0, 281, 480, 320, (48, 54, 48))
    fill_rectangle(pixels, 68, 249, 178, 264, (183, 140, 87))


@lru_cache(maxsize=16)
def artwork_png(name):
    top, bottom = ARTWORK_PALETTES[name]
    pixels = bytearray(IMAGE_WIDTH * IMAGE_HEIGHT * 3)
    for y in range(IMAGE_HEIGHT):
        ratio = y / max(1, IMAGE_HEIGHT - 1)
        color = tuple(round(first + (last - first) * ratio) for first, last in zip(top, bottom))
        start = y * IMAGE_WIDTH * 3
        pixels[start : start + IMAGE_WIDTH * 3] = bytes(color) * IMAGE_WIDTH

    draw_functions = {
        "city": draw_city,
        "architecture": draw_architecture,
        "garden": draw_garden,
        "lab": draw_lab,
        "water": draw_water,
        "culture": draw_culture,
    }
    draw_functions[name](pixels)
    scanlines = b"".join(
        b"\x00" + pixels[y * IMAGE_WIDTH * 3 : (y + 1) * IMAGE_WIDTH * 3]
        for y in range(IMAGE_HEIGHT)
    )
    header = struct.pack(">IIBBBBB", IMAGE_WIDTH, IMAGE_HEIGHT, 8, 2, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + png_chunk(b"IHDR", header)
        + png_chunk(b"IDAT", zlib.compress(scanlines, 8))
        + png_chunk(b"IEND", b"")
    )


def make_article_page(entry, feed_title):
    title = html.escape(entry["title"])
    author = html.escape(entry["author"])
    date = html.escape(entry["published_at"][:10])
    hero = entry["image_html"]
    paragraphs = entry["body_html"]
    return f"""<!doctype html>
<html lang="pl">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="dark">
<title>{title}</title>
<style>
:root {{ color-scheme: dark; background: #181611; color: #e7e4de; }}
* {{ box-sizing: border-box; }}
body {{ margin: 0 auto; max-width: 760px; padding: 28px 22px 96px; background: #181611; color: #e7e4de; font: 18px/1.72 Georgia, 'Times New Roman', serif; }}
.masthead {{ display: flex; align-items: center; gap: 10px; margin: 4px 0 38px; color: #e0c295; font: 700 12px/1.2 sans-serif; letter-spacing: .12em; text-transform: uppercase; }}
.mark {{ width: 28px; height: 28px; border: 1px solid #e0c295; border-radius: 50%; display: grid; place-items: center; font: 600 14px/1 sans-serif; }}
.category {{ margin: 0 0 12px; color: #a4d388; font: 600 12px/1.4 sans-serif; letter-spacing: .1em; text-transform: uppercase; }}
h1 {{ margin: 0 0 18px; font-size: clamp(32px, 8vw, 46px); line-height: 1.12; letter-spacing: -.035em; }}
.dek {{ margin: 0 0 20px; color: #cbc5bb; font: 19px/1.5 sans-serif; }}
.byline {{ margin: 0 0 25px; color: #969087; font: 13px/1.5 sans-serif; }}
.hero {{ margin: 0 -22px 25px; }}
.hero img {{ display: block; width: 100%; max-height: 430px; object-fit: cover; }}
figcaption {{ padding: 8px 22px 0; color: #969087; font: 12px/1.4 sans-serif; }}
p {{ margin: 0 0 1.2em; }}
h2 {{ margin: 1.7em 0 .55em; color: #f2e0c6; font: 600 24px/1.24 sans-serif; letter-spacing: -.02em; }}
blockquote {{ margin: 1.5em 0; padding: 0 0 0 18px; border-left: 3px solid #e0c295; color: #d4c2ae; }}
.endmark {{ width: 48px; height: 2px; margin-top: 36px; background: #e0c295; }}
</style>
</head>
<body>
<header class="masthead"><span class="mark">h</span><span>{html.escape(feed_title)}</span></header>
<article>
<p class="category">Reportaż · miasto i technologia</p>
<h1>{title}</h1>
<p class="dek">{html.escape(entry['preview'])}</p>
<p class="byline">{author} · {date} · około {entry['reading_time']} min czytania</p>
{hero}
{paragraphs}
<h2>Co pokazują obserwacje</h2>
<p>Każde miejsce ma inny rytm i inne ograniczenia. Dlatego przed kolejną zmianą zespół wraca do pomiarów i rozmawia z osobami, które korzystają z tej przestrzeni na co dzień.</p>
<blockquote>Najlepszy projekt zostawia miejsce na to, co mieszkańcy odkryją dopiero po kilku tygodniach.</blockquote>
<p>To dopiero początek pracy. Kolejny etap pozwoli porównać wyniki w innych porach roku i sprawdzić, które rozwiązania można powtórzyć w sąsiednich dzielnicach.</p>
<div class="endmark"></div>
</article>
</body>
</html>"""


class MockMinifluxHandler(BaseHTTPRequestHandler):
    server_version = "hReaderMockMiniflux/1.0"

    def do_GET(self):
        request = urlsplit(self.path)
        if request.path == "/healthz":
            return self.send_json(HTTPStatus.OK, {"status": "ok", "name": "hReader mock Miniflux"})
        if request.path.startswith("/v1/") and not self.authorized():
            return self.send_json(HTTPStatus.UNAUTHORIZED, {"error_message": "Invalid mock API token"})
        if request.path == "/v1/feeds":
            with STATE_LOCK:
                return self.send_json(HTTPStatus.OK, [copy.deepcopy(feed) for feed in self.server.feeds.values()])
        if request.path == "/v1/feeds/counters":
            return self.send_json(HTTPStatus.OK, self.feed_counters())
        if request.path == "/v1/entries":
            return self.send_json(HTTPStatus.OK, self.entry_page(parse_qs(request.query)))
        if request.path.startswith("/v1/entries/") and request.path.endswith("/fetch-content"):
            entry_id = self.entry_id_from_path(request.path, "/fetch-content")
            entry = self.server.entries.get(entry_id)
            if entry is None:
                return self.send_json(HTTPStatus.NOT_FOUND, {"error_message": "Entry not found"})
            feed_title = self.server.feeds.get(entry["feed_id"], {}).get("title", "hReader Demo")
            return self.send_json(HTTPStatus.OK, {"content": make_article_page(entry, feed_title)})
        if request.path.startswith("/articles/"):
            entry_id = self.entry_id_from_path(request.path, "")
            entry = self.server.entries.get(entry_id)
            if entry is None:
                return self.send_text(HTTPStatus.NOT_FOUND, "Article not found", "text/plain; charset=utf-8")
            feed_title = self.server.feeds.get(entry["feed_id"], {}).get("title", "hReader Demo")
            return self.send_text(HTTPStatus.OK, make_article_page(entry, feed_title), "text/html; charset=utf-8")
        if request.path.startswith("/assets/") and request.path.endswith(".png"):
            name = request.path.removeprefix("/assets/").removesuffix(".png")
            if name not in ARTWORK_PALETTES:
                return self.send_text(HTTPStatus.NOT_FOUND, "Image not found", "text/plain; charset=utf-8")
            return self.send_bytes(HTTPStatus.OK, artwork_png(name), "image/png")
        if request.path == "/":
            return self.send_json(HTTPStatus.OK, {"name": "hReader mock Miniflux", "api": "/v1/"})
        return self.send_json(HTTPStatus.NOT_FOUND, {"error_message": "Not found"})

    def do_PUT(self):
        request = urlsplit(self.path)
        if not request.path.startswith("/v1/") or not self.authorized():
            return self.send_json(HTTPStatus.UNAUTHORIZED, {"error_message": "Invalid mock API token"})
        body = self.read_json()
        if body is None:
            return self.send_json(HTTPStatus.BAD_REQUEST, {"error_message": "Invalid JSON body"})
        if request.path == "/v1/entries":
            entry_ids = body.get("entry_ids", [])
            status = body.get("status")
            if status not in {"read", "unread"} or not isinstance(entry_ids, list):
                return self.send_json(HTTPStatus.BAD_REQUEST, {"error_message": "Invalid entry status update"})
            changed_at = time.time()
            with STATE_LOCK:
                for entry_id in entry_ids:
                    entry = self.server.entries.get(int(entry_id))
                    if entry is not None:
                        entry["status"] = status
                        entry["changed_at"] = changed_at
            return self.send_empty(HTTPStatus.NO_CONTENT)
        if request.path.startswith("/v1/feeds/"):
            feed_id = self.entry_id_from_path(request.path, "")
            with STATE_LOCK:
                feed = self.server.feeds.get(feed_id)
                if feed is None:
                    return self.send_json(HTTPStatus.NOT_FOUND, {"error_message": "Feed not found"})
                feed["title"] = str(body.get("title", feed["title"]))
                return self.send_json(HTTPStatus.OK, copy.deepcopy(feed))
        return self.send_json(HTTPStatus.NOT_FOUND, {"error_message": "Not found"})

    def do_POST(self):
        request = urlsplit(self.path)
        if not request.path.startswith("/v1/") or not self.authorized():
            return self.send_json(HTTPStatus.UNAUTHORIZED, {"error_message": "Invalid mock API token"})
        body = self.read_json()
        if body is None:
            return self.send_json(HTTPStatus.BAD_REQUEST, {"error_message": "Invalid JSON body"})
        if request.path == "/v1/discover":
            feed_url = str(body.get("url", "")).strip()
            if not feed_url:
                return self.send_json(HTTPStatus.BAD_REQUEST, {"error_message": "Feed URL is required"})
            title = feed_url.rstrip("/").split("/")[-1] or "Odkryty kanał"
            return self.send_json(
                HTTPStatus.OK,
                [{"url": feed_url, "title": title.replace("-", " ").title(), "type": "rss"}],
            )
        if request.path == "/v1/feeds":
            feed_url = str(body.get("feed_url", "")).strip()
            if not feed_url:
                return self.send_json(HTTPStatus.BAD_REQUEST, {"error_message": "Feed URL is required"})
            with STATE_LOCK:
                feed_id = max(self.server.feeds, default=10) + 1
                title = feed_url.rstrip("/").split("/")[-1].replace("-", " ").title()
                self.server.feeds[feed_id] = {
                    "id": feed_id,
                    "title": title or f"Kanał {feed_id}",
                    "site_url": feed_url,
                    "feed_url": feed_url,
                }
            return self.send_empty(HTTPStatus.CREATED)
        return self.send_json(HTTPStatus.NOT_FOUND, {"error_message": "Not found"})

    def do_DELETE(self):
        request = urlsplit(self.path)
        if not request.path.startswith("/v1/") or not self.authorized():
            return self.send_json(HTTPStatus.UNAUTHORIZED, {"error_message": "Invalid mock API token"})
        if request.path.startswith("/v1/feeds/"):
            feed_id = self.entry_id_from_path(request.path, "")
            with STATE_LOCK:
                self.server.feeds.pop(feed_id, None)
                removed = [entry_id for entry_id, entry in self.server.entries.items() if entry["feed_id"] == feed_id]
                for entry_id in removed:
                    self.server.entries.pop(entry_id, None)
            return self.send_empty(HTTPStatus.NO_CONTENT)
        return self.send_json(HTTPStatus.NOT_FOUND, {"error_message": "Not found"})

    def authorized(self):
        return self.headers.get("X-Auth-Token", "") == self.server.token

    def read_json(self):
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 65536:
                return None
            value = json.loads(self.rfile.read(length))
            return value if isinstance(value, dict) else None
        except (ValueError, json.JSONDecodeError):
            return None

    def entry_page(self, query):
        statuses = set(query.get("status", []))
        order = query.get("order", ["id"])[0]
        direction = query.get("direction", ["desc"])[0]
        limit = self.integer_query(query, "limit", 100, 1, 500)
        after_id = self.integer_query(query, "after_entry_id", None)
        before_id = self.integer_query(query, "before_entry_id", None)
        changed_after = self.integer_query(query, "changed_after", None)
        with STATE_LOCK:
            entries = [entry for entry in self.server.entries.values() if not statuses or entry["status"] in statuses]
            if changed_after is not None:
                entries = [entry for entry in entries if entry["changed_at"] >= changed_after]
            if after_id is not None:
                entries = [entry for entry in entries if entry["id"] > after_id]
            if before_id is not None:
                entries = [entry for entry in entries if entry["id"] < before_id]
            entries.sort(key=lambda entry: entry[order] if order in entry else entry["id"])
            if direction == "desc":
                entries.reverse()
            total = len(entries)
            selected = entries[:limit]
            return {"total": total, "entries": [self.entry_payload(entry) for entry in selected]}

    def entry_payload(self, entry):
        feed = copy.deepcopy(self.server.feeds.get(entry["feed_id"], {}))
        enclosures = (
            [{"url": entry["image_url"], "mime_type": "image/png"}]
            if entry["image_url"]
            else []
        )
        return {
            "id": entry["id"],
            "title": entry["title"],
            "author": entry["author"],
            "url": entry["url"],
            "published_at": entry["published_at"],
            "content": entry["excerpt_html"],
            "feed": feed,
            "reading_time": entry["reading_time"],
            "enclosures": enclosures,
            "status": entry["status"],
        }

    def feed_counters(self):
        with STATE_LOCK:
            reads = {str(feed_id): 0 for feed_id in self.server.feeds}
            unreads = {str(feed_id): 0 for feed_id in self.server.feeds}
            for entry in self.server.entries.values():
                if entry["feed_id"] not in self.server.feeds:
                    continue
                counters = reads if entry["status"] == "read" else unreads
                counters[str(entry["feed_id"])] += 1
            return {"reads": reads, "unreads": unreads}

    def integer_query(self, query, key, default, minimum=None, maximum=None):
        try:
            value = int(query[key][0]) if key in query else default
        except (TypeError, ValueError):
            return default
        if minimum is not None:
            value = max(minimum, value)
        if maximum is not None:
            value = min(maximum, value)
        return value

    def entry_id_from_path(self, path, suffix):
        value = path.removeprefix("/v1/entries/").removeprefix("/v1/feeds/")
        if suffix:
            value = value.removesuffix(suffix)
        try:
            return int(value.strip("/"))
        except ValueError:
            return -1

    def send_json(self, status, value):
        body = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_bytes(status, body, "application/json; charset=utf-8")

    def send_text(self, status, value, content_type):
        self.send_bytes(status, value.encode("utf-8"), content_type)

    def send_empty(self, status):
        self.send_response(status)
        self.send_header("Content-Length", "0")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()

    def send_bytes(self, status, body, content_type):
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(body)


def main():
    parser = argparse.ArgumentParser(description="Local Miniflux API fixture for hReader emulator reviews.")
    parser.add_argument("--bind", default="127.0.0.1", help="Host interface to bind; defaults to loopback only.")
    parser.add_argument("--port", type=int, default=8765, help="Local HTTP port.")
    parser.add_argument("--token", default="hreader-demo-token", help="Mock API token entered in hReader settings.")
    parser.add_argument(
        "--emulator-host",
        default="127.0.0.1",
        help="Host embedded in article and image URLs; use 127.0.0.1 with adb reverse.",
    )
    arguments = parser.parse_args()
    base_url = f"http://{arguments.emulator_host}:{arguments.port}"
    feeds, entries = make_state(base_url)
    server = ThreadingHTTPServer((arguments.bind, arguments.port), MockMinifluxHandler)
    server.feeds = feeds
    server.entries = entries
    server.token = arguments.token
    print(f"Mock Miniflux API: {base_url}/v1/", flush=True)
    print(f"API token: {arguments.token}", flush=True)
    print(f"Host bind: {arguments.bind}:{arguments.port}; {len(feeds)} feeds, {len(entries)} articles", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
