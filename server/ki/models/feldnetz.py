"""Feld-Zuordnungsnetz: Welche OCR-Zeile ist Seriennummer, Freenet-ID, Datum?

Kleines eigenes MLP über handgebaute Merkmale jeder erkannten Textzeile
(Zeichenklassen-Statistik, Position im Bild, Kontext der Nachbarzeilen).
Harte Format-Regeln dienen als Sicherheitsnetz und liefern in der
Bootstrap-Phase (bevor genug Labels existieren) die Vorhersage allein.

Bekannte Formate aus den KKH-Stammdaten:
  - Freenet-ID:     genau 11 Ziffern (z. B. 70517201599)
  - Seriennummer:   9-14 Ziffern ODER alphanumerisch >= 8 Zeichen
                    (z. B. 170325586, NCCMBT1012111004330, 910MAKR09853)
  - Gültig-bis:     Datum TT.MM.JJJJ oder JJJJ-MM-TT
"""

import json
import os
import re

import torch
import torch.nn as nn

MODEL_DIR = os.environ.get("KI_MODEL_DIR", "/data/ki-models")
FELD_KLASSEN = ["irrelevant", "seriennummer", "freenet_id", "gueltig_bis"]

# Kontext-Schlüsselwörter, wie sie in CI-/Geräte-Menüs auftauchen
_KEYWORDS = {
    "seriennummer": ["serial", "serien", "s/n", "sn:", "seriennr"],
    "freenet_id": ["freenet", "tv-id", "tvid", "ci-modul", "smartcard", "karte", "card"],
    "gueltig_bis": ["gültig", "gueltig", "valid", "ablauf", "expiry", "laufzeit",
                    "freigeschaltet"],
}

# Zeilen mit diesen Wörtern sind KEINE Kandidaten für das jeweilige Feld
# (im CI-Menü stehen z. B. Irdeto-ID und Versionsnummern, die sonst als
# Seriennummer fehlinterpretiert würden)
_NEGATIV = {
    "seriennummer": ["irdeto", "version", "loader", "cam-", "freenet", "tv-id"],
    "freenet_id": ["irdeto", "seriennummer", "version", "loader"],
    "gueltig_bis": [],
}

RE_DATUM = re.compile(r"\b(\d{1,2})[./-](\d{1,2})[./-](20\d{2})\b|\b(20\d{2})-(\d{2})-(\d{2})\b")
RE_11_ZIFFERN = re.compile(r"\b\d{11}\b")
RE_9_14_ZIFFERN = re.compile(r"\b\d{9,14}\b")
RE_ALNUM_SN = re.compile(r"\b(?=[A-Z0-9]{8,20}\b)(?=\w*[A-Z])(?=\w*\d)[A-Z0-9]+\b")

FEATURE_DIM = 16


def _merkmale(zeile, kontext_davor: str):
    """Merkmalvektor einer OCR-Zeile: Statistik + Position + Kontext."""
    text = zeile["text"]
    t = text.strip()
    laenge = max(len(t), 1)
    ziffern = sum(c.isdigit() for c in t)
    buchst = sum(c.isalpha() for c in t)
    davor = kontext_davor.lower()
    tl = t.lower()

    def kw(feld):
        woerter = _KEYWORDS[feld]
        return float(any(w in tl for w in woerter) or any(w in davor for w in woerter))

    return [
        min(laenge / 30.0, 1.0),
        ziffern / laenge,
        buchst / laenge,
        float(bool(RE_11_ZIFFERN.search(t))),
        float(bool(RE_9_14_ZIFFERN.search(t))),
        float(bool(RE_ALNUM_SN.search(t.upper()))),
        float(bool(RE_DATUM.search(t))),
        kw("seriennummer"),
        kw("freenet_id"),
        kw("gueltig_bis"),
        zeile.get("rel_x", 0.5),
        zeile.get("rel_y", 0.5),
        zeile.get("conf", 0.0),
        float(t.isupper() if t else False),
        float(":" in t or "=" in t),
        float(len(t.split()) == 1),
    ]


class FeldNetz(nn.Module):
    def __init__(self):
        super().__init__()
        self.mlp = nn.Sequential(
            nn.Linear(FEATURE_DIM, 64), nn.ReLU(), nn.Dropout(0.2),
            nn.Linear(64, 32), nn.ReLU(),
            nn.Linear(32, len(FELD_KLASSEN)),
        )

    def forward(self, x):
        return self.mlp(x)


def _regel_extraktion(text: str):
    """Harte Format-Regeln – liefern Kandidatenwerte aus einer Zeile.

    Zeilen, die laut Negativ-Wörtern sicher NICHT zum Feld gehören
    (z. B. 'Irdeto-ID: …' ist keine Seriennummer), werden ausgelassen.
    """
    treffer = {}
    tl = text.lower()

    def erlaubt(feld):
        return not any(n in tl for n in _NEGATIV[feld])

    m = RE_11_ZIFFERN.search(text)
    if m and erlaubt("freenet_id"):
        treffer["freenet_id"] = m.group(0)
    m = RE_DATUM.search(text)
    if m:
        if m.group(1):
            treffer["gueltig_bis"] = f"{m.group(3)}-{int(m.group(2)):02d}-{int(m.group(1)):02d}"
        else:
            treffer["gueltig_bis"] = f"{m.group(4)}-{m.group(5)}-{m.group(6)}"
    if erlaubt("seriennummer"):
        m = RE_ALNUM_SN.search(text.upper())
        if m:
            treffer["seriennummer"] = m.group(0)
        else:
            # Nur-Ziffern-SN – aber nicht dieselbe Zahl wie eine erkannte Freenet-ID
            for z in RE_9_14_ZIFFERN.findall(text):
                if z != treffer.get("freenet_id"):
                    treffer.setdefault("seriennummer", z)
    return treffer


class FeldExtraktor:
    """Kombiniert das trainierte Netz mit den Format-Regeln."""

    def __init__(self):
        self.netz = None
        self.version = "regeln"
        self._lade_aktuelles_modell()

    def _lade_aktuelles_modell(self):
        meta_pfad = os.path.join(MODEL_DIR, "feldnetz_aktuell.json")
        if not os.path.exists(meta_pfad):
            return
        try:
            with open(meta_pfad) as f:
                meta = json.load(f)
            netz = FeldNetz()
            netz.load_state_dict(torch.load(
                os.path.join(MODEL_DIR, meta["datei"]),
                map_location="cpu", weights_only=True))
            netz.eval()
            self.netz = netz
            self.version = meta["version"]
        except Exception as e:
            print(f"Feldnetz nicht ladbar ({e}) – Regeln aktiv")

    def extrahiere(self, ocr_zeilen):
        """ocr_zeilen: [{text, conf, rel_x, rel_y}] → {feld: {wert, konfidenz}}."""
        ergebnis = {}

        # 1) Netz-Vorhersage je Zeile (falls trainiert)
        if self.netz is not None and ocr_zeilen:
            feats = []
            for i, z in enumerate(ocr_zeilen):
                davor = ocr_zeilen[i - 1]["text"] if i > 0 else ""
                feats.append(_merkmale(z, davor))
            with torch.no_grad():
                probs = torch.softmax(self.netz(torch.tensor(feats, dtype=torch.float32)), dim=1)
            for i, z in enumerate(ocr_zeilen):
                klasse = int(probs[i].argmax())
                konf = float(probs[i][klasse])
                feld = FELD_KLASSEN[klasse]
                if feld == "irrelevant" or konf < 0.55:
                    continue
                kandidaten = _regel_extraktion(z["text"])
                wert = kandidaten.get(feld) or z["text"].strip()
                if feld not in ergebnis or konf > ergebnis[feld]["konfidenz"]:
                    ergebnis[feld] = {"wert": wert, "konfidenz": round(konf, 3), "quelle": "netz"}

        # 2) Regel-Sicherheitsnetz: fehlende Felder über Formate füllen.
        #    Kandidaten mit Schlüsselwort-Kontext schlagen kontextlose Treffer.
        for i, z in enumerate(ocr_zeilen):
            for feld, wert in _regel_extraktion(z["text"]).items():
                davor = ocr_zeilen[i - 1]["text"].lower() if i > 0 else ""
                kontext = any(w in (z["text"].lower() + " " + davor)
                              for w in _KEYWORDS.get(feld, []))
                konf = 0.8 if kontext else 0.5
                bisher = ergebnis.get(feld)
                if bisher is None or (bisher["quelle"] == "regel" and konf > bisher["konfidenz"]):
                    ergebnis[feld] = {"wert": wert, "konfidenz": konf, "quelle": "regel"}

        return ergebnis


def merkmale_fuer_training(ocr_zeilen):
    """Öffentliche Hilfe fürs Training: Merkmalvektoren aller Zeilen."""
    feats = []
    for i, z in enumerate(ocr_zeilen):
        davor = ocr_zeilen[i - 1]["text"] if i > 0 else ""
        feats.append(_merkmale(z, davor))
    return feats
