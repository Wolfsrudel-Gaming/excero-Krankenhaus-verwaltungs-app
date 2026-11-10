"""Vorfilter für die Fotoanalyse – bewusst ohne torch/OCR-Abhängigkeiten.

Zwei Dinge dürfen nie als Messwert durchgehen:
  - das von der App eingebrannte Wasserzeichen unten links
    ("<Station> · Zimmer <Nr>" und darunter "TT.MM.JJJJ HH:MM" = Aufnahmezeit)
  - Text auf Fernbedienungsfotos (dort steht weder Seriennummer noch
    Freenet-ID noch Gültigkeit des Fernsehers)
"""

import re

_RE_DATUM = re.compile(r"(\d{1,2})[.,:/-](\d{1,2})[.,:/-](20\d{2})")
_RE_ISO = re.compile(r"(20\d{2})-(\d{2})-(\d{2})")
_RE_STEMPEL_DATUM = re.compile(r"^\W*\d{1,2}[.,:/-]\d{1,2}[.,:/-]20\d{2}")
_RE_STEMPEL_ORT = re.compile(r"^\W*\S{1,8}\s*\W?\s*Zimmer\b", re.IGNORECASE)
_RE_ORDNER = re.compile(r"(?:^|/)(20\d{2})(\d{2})(\d{2})(?:/|$)")

# Das Band liegt je nach Seitenverhältnis in den unteren ~8–20 % des Bildes,
# die Schrift beginnt ~1,5 % vom linken Rand.
_BAND_AB_Y = 0.72
_BAND_BIS_X = 0.12


def ist_fernbedienung(pfad_rel: str) -> bool:
    """Die App schreibt das Foto-Feld in den Dateinamen: …_fernbedienung_<zeit>.jpg."""
    return "_fernbedienung_" in pfad_rel.rsplit("/", 1)[-1].lower()


def aufnahmedatum(pfad_rel: str) -> str:
    """ISO-Datum aus dem Tagesordner <roomId>/<JJJJMMTT>/… ('' wenn keiner)."""
    m = _RE_ORDNER.search(pfad_rel)
    return f"{m.group(1)}-{m.group(2)}-{m.group(3)}" if m else ""


def daten_in(text: str):
    """Alle Datumsangaben eines Textes als ISO-Strings."""
    gefunden = {f"{j}-{int(mo):02d}-{int(t):02d}" for t, mo, j in _RE_DATUM.findall(text)}
    gefunden |= {f"{j}-{mo}-{t}" for j, mo, t in _RE_ISO.findall(text)}
    return gefunden


def _ist_stempelzeile(zeile) -> bool:
    if zeile.get("rel_y", 0) < _BAND_AB_Y or zeile.get("rel_x", 1) > _BAND_BIS_X:
        return False
    text = zeile.get("text", "")
    return bool(_RE_STEMPEL_DATUM.search(text) or _RE_STEMPEL_ORT.search(text))


def trenne_wasserzeichen(zeilen):
    """Teilt OCR-Zeilen in (inhalt, wasserzeichen).

    Sobald unten links eine Stempelzeile erkannt ist, gehört alles ab ihrer
    Höhe zum Band – so fällt auch eine von der OCR abgetrennte Uhrzeit weg.
    """
    anker = [z["rel_y"] for z in zeilen if _ist_stempelzeile(z)]
    if not anker:
        return list(zeilen), []
    grenze = min(anker) - 0.01
    inhalt = [z for z in zeilen if z.get("rel_y", 0) < grenze]
    stempel = [z for z in zeilen if z.get("rel_y", 0) >= grenze]
    return inhalt, stempel


def stempel_daten(zeilen, pfad_rel: str = ""):
    """ISO-Daten, die nur Aufnahmezeitpunkt sind (Wasserzeichen + Tagesordner)."""
    _, stempel = trenne_wasserzeichen(zeilen)
    daten = set()
    for z in stempel:
        daten |= daten_in(z.get("text", ""))
    tag = aufnahmedatum(pfad_rel)
    if tag:
        daten.add(tag)
    return daten
