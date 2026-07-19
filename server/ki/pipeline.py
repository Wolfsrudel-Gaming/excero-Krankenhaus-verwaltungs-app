"""Analyse-Pipeline: Foto → Bildklassifikation → OCR → Felder → Abgleich.

Der Abgleich vergleicht die erkannten Werte mit den Zimmer-Stammdaten
(Tabelle rooms). Ergebnis-Status:
  - uebereinstimmung: alle erkannten Felder passen zu den Stammdaten
  - abweichung:       mindestens ein Feld weicht ab (Monteur soll prüfen)
  - unlesbar:         OCR fand keinen verwertbaren Text (lieber ehrlich als falsch)
"""

import os
import re

from PIL import Image, ImageOps

import db
from models.klassifikator import Klassifikator
from models.feldnetz import FeldExtraktor

FILES_DIR = os.environ.get("FILES_DIR", "/data/files")

_reader = None  # EasyOCR lazy laden (Start des Containers bleibt schnell)


def _ocr_reader():
    global _reader
    if _reader is None:
        import easyocr
        _reader = easyocr.Reader(["de", "en"], gpu=False, verbose=False)
    return _reader


klassifikator = Klassifikator()
feld_extraktor = FeldExtraktor()


def modell_version() -> str:
    return f"klass:{klassifikator.version}|feld:{feld_extraktor.version}"


def _normalisiere(wert: str) -> str:
    """Vergleichsnormierung: Großschreibung, keine Trennzeichen."""
    return re.sub(r"[\s\-_./]", "", str(wert or "")).upper()


def _lade_bild(pfad: str, max_seite: int = 1600):
    """EXIF-Rotation anwenden und für die OCR auf handliche Größe bringen."""
    img = Image.open(pfad)
    img = ImageOps.exif_transpose(img).convert("RGB")
    if max(img.size) > max_seite:
        img.thumbnail((max_seite, max_seite))
    return img


def ocr_zeilen(pfad: str):
    """OCR ausführen; Zeilen mit Text, Konfidenz und relativer Position."""
    import numpy as np
    img = _lade_bild(pfad)
    breite, hoehe = img.size
    roh = _ocr_reader().readtext(np.array(img), detail=1, paragraph=False)
    zeilen = []
    for box, text, conf in roh:
        xs = [p[0] for p in box]
        ys = [p[1] for p in box]
        zeilen.append({
            "text": str(text).strip(),
            "conf": round(float(conf), 3),
            "rel_x": round(min(xs) / breite, 3),
            "rel_y": round(min(ys) / hoehe, 3),
        })
    zeilen.sort(key=lambda z: (z["rel_y"], z["rel_x"]))
    return zeilen


def _abgleich(felder, zimmer, tv_typ_erkannt):
    """Erkannte Werte gegen Stammdaten prüfen."""
    ergebnis = {}
    if not zimmer:
        return ergebnis
    paare = [
        ("seriennummer", zimmer.get("seriennummer", "")),
        ("freenet_id", zimmer.get("freenet_id", "")),
        ("gueltig_bis", zimmer.get("gueltig_bis", "")),
    ]
    for feld, stammwert in paare:
        erkannt = felder.get(feld, {}).get("wert", "")
        if not erkannt:
            continue
        passt = _normalisiere(erkannt) == _normalisiere(stammwert) if stammwert else None
        ergebnis[feld] = {
            "erkannt": erkannt,
            "stammdaten": stammwert,
            "passt": passt,
        }
    if tv_typ_erkannt:
        stamm_tv = zimmer.get("tv_typ", "")
        ergebnis["tv_typ"] = {
            "erkannt": tv_typ_erkannt,
            "stammdaten": stamm_tv,
            "passt": _normalisiere(tv_typ_erkannt) == _normalisiere(stamm_tv) if stamm_tv else None,
        }
    return ergebnis


def _auto_labels(pfad, felder, abgleich, bildtyp):
    """Selbst-Matching: Übereinstimmungen mit Stammdaten sind sichere Labels."""
    db.setze_auto_label(pfad, "bildtyp", bildtyp)
    for feld, a in abgleich.items():
        if a.get("passt") is True:
            db.setze_auto_label(pfad, feld, a["stammdaten"])


def analysiere_foto(pfad_rel: str, room_id: str):
    """Vollständige Analyse eines Fotos. Liefert (bildtyp, felder, abgleich, status)."""
    voll = os.path.join(FILES_DIR, pfad_rel)
    if not os.path.exists(voll):
        return "", {}, {}, "fehler", "Datei nicht gefunden"

    bildtyp, bt_konf, tv_typ, tv_konf = klassifikator.klassifiziere(voll)

    zeilen = ocr_zeilen(voll)
    # Übersichtsfotos zeigen das laufende TV-Bild – dort erkannte Zahlen sind
    # Zufallstreffer (Senderlogos, Laufschriften). Felder nur aus Menü-/
    # Geräte-Nahaufnahmen extrahieren.
    felder = feld_extraktor.extrahiere(zeilen) if bildtyp != "uebersicht" else {}
    felder["_ocr"] = zeilen             # Rohtext fürs spätere Training aufheben
    felder["_bildtyp_konfidenz"] = round(bt_konf, 3)
    if tv_typ and tv_konf >= 0.6:
        felder["tv_typ"] = {"wert": tv_typ, "konfidenz": round(tv_konf, 3), "quelle": "netz"}

    zimmer = db.hole_zimmer(room_id) if room_id else None
    abgleich = _abgleich(felder, zimmer, felder.get("tv_typ", {}).get("wert", ""))

    inhaltsfelder = [k for k in felder if not k.startswith("_")]
    if not inhaltsfelder and not zeilen:
        status = "unlesbar"
    elif not inhaltsfelder:
        # Text erkannt, aber keine verwertbaren Felder (typisch: Übersichtsfoto)
        status = "uebereinstimmung" if bildtyp == "uebersicht" else "unlesbar"
    elif any(a.get("passt") is False for a in abgleich.values()):
        status = "abweichung"
    else:
        status = "uebereinstimmung"

    _auto_labels(pfad_rel, felder, abgleich, bildtyp)
    return bildtyp, felder, abgleich, status, ""


def room_id_aus_pfad(pfad_rel: str) -> str:
    """Fotopfade der App: <roomId>/<JJJJMMTT>/<datei>. Sonderordner (_...) ignorieren."""
    erster = pfad_rel.split("/")[0]
    return "" if erster.startswith("_") else erster
