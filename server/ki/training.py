"""Training der eigenen Netze aus der Label-Tabelle (ki_labels).

Wird nächtlich vom Worker angestoßen (und manuell über POST /train).
Trainiert nur, wenn genug Labels vorhanden sind; jedes Modell wird
versioniert unter /data/ki-models/ abgelegt und die Pipeline lädt
danach automatisch die neue Version.
"""

import json
import os
import random
from datetime import datetime

import torch
import torch.nn as nn
from PIL import Image

import db
from models.klassifikator import KlassifikatorNetz, BILDTYPEN, MODEL_DIR, _transform
from models.feldnetz import FeldNetz, FELD_KLASSEN, merkmale_fuer_training

FILES_DIR = os.environ.get("FILES_DIR", "/data/files")
MIN_LABELS_KLASSIFIKATOR = 20
MIN_LABELS_FELDNETZ = 15


def _version() -> str:
    return datetime.now().strftime("%Y%m%d-%H%M%S")


def _speichere(netz, name, version, genauigkeit, anzahl, extra_meta=None):
    os.makedirs(MODEL_DIR, exist_ok=True)
    datei = f"{name}_{version}.pt"
    torch.save(netz.state_dict(), os.path.join(MODEL_DIR, datei))
    meta = {"datei": datei, "version": version, **(extra_meta or {})}
    with open(os.path.join(MODEL_DIR, f"{name}_aktuell.json"), "w") as f:
        json.dump(meta, f)
    db.execute(
        "INSERT INTO ki_modelle (name, version, genauigkeit, anzahl_labels) VALUES (%s,%s,%s,%s)",
        (name, version, genauigkeit, anzahl))
    print(f"Modell gespeichert: {datei} (Genauigkeit {genauigkeit:.2%}, {anzahl} Labels)")


def _train_eval_split(daten, anteil=0.85):
    random.shuffle(daten)
    schnitt = max(1, int(len(daten) * anteil))
    return daten[:schnitt], daten[schnitt:] or daten[:1]


def trainiere_klassifikator():
    """Bildtyp + TV-Typ aus gelabelten Fotos lernen."""
    bildtyp_labels = db.query("SELECT pfad, wert FROM ki_labels WHERE feld='bildtyp'")
    tv_labels = db.query("SELECT pfad, wert FROM ki_labels WHERE feld='tv_typ'")
    tv_map = {r["pfad"]: r["wert"] for r in tv_labels}
    # TV-Typen zusätzlich aus den Stammdaten (stabile Klassenliste)
    tv_typen = sorted({r["tv_typ"] for r in db.query(
        "SELECT DISTINCT tv_typ FROM rooms WHERE tv_typ <> ''")} |
        {v for v in tv_map.values() if v})
    if len(bildtyp_labels) < MIN_LABELS_KLASSIFIKATOR:
        print(f"Klassifikator: nur {len(bildtyp_labels)} Bildtyp-Labels "
              f"(min. {MIN_LABELS_KLASSIFIKATOR}) – Training übersprungen")
        return False

    daten = []
    for r in bildtyp_labels:
        voll = os.path.join(FILES_DIR, r["pfad"])
        if not os.path.exists(voll) or r["wert"] not in BILDTYPEN:
            continue
        tv = tv_map.get(r["pfad"], "")
        tv_idx = tv_typen.index(tv) if tv in tv_typen else -1
        daten.append((voll, BILDTYPEN.index(r["wert"]), tv_idx))
    if len(daten) < MIN_LABELS_KLASSIFIKATOR:
        print("Klassifikator: zu wenige nutzbare Fotos – Training übersprungen")
        return False

    train, evaluierung = _train_eval_split(daten)
    netz = KlassifikatorNetz(len(tv_typen))
    opt = torch.optim.Adam(
        [p for p in netz.parameters() if p.requires_grad], lr=1e-3)
    ce = nn.CrossEntropyLoss()

    def lade(voll):
        return _transform(Image.open(voll).convert("RGB"))

    netz.train()
    for epoche in range(12):
        random.shuffle(train)
        verlust_summe = 0.0
        for i in range(0, len(train), 8):
            batch = train[i:i + 8]
            x = torch.stack([lade(b[0]) for b in batch])
            y_bt = torch.tensor([b[1] for b in batch])
            logit_bt, logit_tv = netz(x)
            verlust = ce(logit_bt, y_bt)
            # TV-Typ nur für Fotos mit TV-Label mitlernen
            tv_maske = [j for j, b in enumerate(batch) if b[2] >= 0]
            if tv_maske:
                y_tv = torch.tensor([batch[j][2] for j in tv_maske])
                verlust = verlust + ce(logit_tv[tv_maske], y_tv)
            opt.zero_grad()
            verlust.backward()
            opt.step()
            verlust_summe += float(verlust)
        print(f"Klassifikator Epoche {epoche + 1}: Verlust {verlust_summe:.3f}")

    netz.eval()
    richtig = 0
    with torch.no_grad():
        for voll, y_bt, _ in evaluierung:
            logit_bt, _ = netz(lade(voll).unsqueeze(0))
            if int(logit_bt.argmax()) == y_bt:
                richtig += 1
    genauigkeit = richtig / len(evaluierung)
    _speichere(netz, "klassifikator", _version(), genauigkeit, len(daten),
               {"tv_typen": tv_typen})
    return True


def trainiere_feldnetz():
    """Feld-Zuordnung aus gelabelten Analysen lernen.

    Labels sind Feld→Wert je Foto; die Trainingszeilen entstehen, indem der
    Label-Wert in den gespeicherten OCR-Zeilen (foto_analysen.felder->_ocr)
    wiedergefunden wird. Alle übrigen Zeilen des Fotos sind 'irrelevant'.
    """
    labels = db.query(
        "SELECT pfad, feld, wert FROM ki_labels WHERE feld = ANY(%s)",
        (FELD_KLASSEN[1:],))
    if len(labels) < MIN_LABELS_FELDNETZ:
        print(f"Feldnetz: nur {len(labels)} Labels (min. {MIN_LABELS_FELDNETZ}) "
              "– Training übersprungen")
        return False

    labels_pro_pfad = {}
    for r in labels:
        labels_pro_pfad.setdefault(r["pfad"], {})[r["feld"]] = r["wert"]

    def norm(s):
        import re as _re
        return _re.sub(r"[\s\-_./]", "", str(s or "")).upper()

    X, Y = [], []
    for pfad, feld_werte in labels_pro_pfad.items():
        rows = db.query("SELECT felder FROM foto_analysen WHERE pfad=%s", (pfad,))
        if not rows:
            continue
        zeilen = (rows[0]["felder"] or {}).get("_ocr") or []
        if not zeilen:
            continue
        feats = merkmale_fuer_training(zeilen)
        for i, z in enumerate(zeilen):
            klasse = 0  # irrelevant
            for feld, wert in feld_werte.items():
                if wert and norm(wert) in norm(z["text"]):
                    klasse = FELD_KLASSEN.index(feld)
                    break
            X.append(feats[i])
            Y.append(klasse)

    if len(X) < 30 or len(set(Y)) < 2:
        print("Feldnetz: zu wenig verwertbare Trainingszeilen – übersprungen")
        return False

    paare = list(zip(X, Y))
    train, evaluierung = _train_eval_split(paare)
    netz = FeldNetz()
    # Klassen-Ungleichgewicht ausgleichen (viele irrelevante Zeilen)
    anzahl = [max(1, sum(1 for _, y in train if y == k)) for k in range(len(FELD_KLASSEN))]
    gewichte = torch.tensor([len(train) / a for a in anzahl], dtype=torch.float32)
    ce = nn.CrossEntropyLoss(weight=gewichte)
    opt = torch.optim.Adam(netz.parameters(), lr=1e-3)

    x_t = torch.tensor([p[0] for p in train], dtype=torch.float32)
    y_t = torch.tensor([p[1] for p in train])
    netz.train()
    for epoche in range(200):
        opt.zero_grad()
        verlust = ce(netz(x_t), y_t)
        verlust.backward()
        opt.step()

    netz.eval()
    with torch.no_grad():
        x_e = torch.tensor([p[0] for p in evaluierung], dtype=torch.float32)
        y_e = torch.tensor([p[1] for p in evaluierung])
        pred = netz(x_e).argmax(dim=1)
        genauigkeit = float((pred == y_e).float().mean())
    _speichere(netz, "feldnetz", _version(), genauigkeit, len(labels))
    return True


def trainiere_alles():
    ergebnisse = {
        "klassifikator": trainiere_klassifikator(),
        "feldnetz": trainiere_feldnetz(),
    }
    return ergebnisse


if __name__ == "__main__":
    db.init_schema()
    print(trainiere_alles())
