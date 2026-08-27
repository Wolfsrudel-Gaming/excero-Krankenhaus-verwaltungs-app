"""Eigenes CNN für Bildtyp- und TV-Typ-Erkennung.

Backbone: MobileNetV3-Small (vortrainiert, eingefroren) – darauf zwei eigene,
mit den KKH-eigenen Fotos trainierte Köpfe:
  - Bildtyp:  menue (CI-/Geräte-Menü am Bildschirm), geraet (TV/Etikett nah),
              uebersicht (Zimmer-Übersichtsfoto)
  - TV-Typ:   dynamische Klassenliste aus den Stammdaten (Lenco, LG, ...)

Solange noch kein trainiertes Modell existiert, greift eine Heuristik über
den Dateinamen (App benennt Fotos ..._fern_... / ..._nah_...), damit die
Pipeline vom ersten Tag an funktioniert.
"""

import json
import os

import torch
import torch.nn as nn
from PIL import Image
from torchvision import transforms
from torchvision.models import mobilenet_v3_small, MobileNet_V3_Small_Weights

MODEL_DIR = os.environ.get("KI_MODEL_DIR", "/data/ki-models")
BILDTYPEN = ["menue", "geraet", "uebersicht"]

_transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
])


class KlassifikatorNetz(nn.Module):
    """Eingefrorener Backbone + zwei trainierbare Köpfe."""

    def __init__(self, anzahl_tv_typen: int):
        super().__init__()
        backbone = mobilenet_v3_small(weights=MobileNet_V3_Small_Weights.DEFAULT)
        self.features = backbone.features
        self.pool = nn.AdaptiveAvgPool2d(1)
        for p in self.features.parameters():
            p.requires_grad = False
        dim = 576  # Ausgabekanäle von MobileNetV3-Small
        self.kopf_bildtyp = nn.Sequential(
            nn.Linear(dim, 128), nn.ReLU(), nn.Dropout(0.3),
            nn.Linear(128, len(BILDTYPEN)),
        )
        self.kopf_tvtyp = nn.Sequential(
            nn.Linear(dim, 128), nn.ReLU(), nn.Dropout(0.3),
            nn.Linear(128, max(anzahl_tv_typen, 2)),
        )

    def forward(self, x):
        f = self.pool(self.features(x)).flatten(1)
        return self.kopf_bildtyp(f), self.kopf_tvtyp(f)

    def embed(self, x):
        return self.pool(self.features(x)).flatten(1)


class Klassifikator:
    """Lädt das aktuellste trainierte Modell; fällt sonst auf Heuristik zurück."""

    def __init__(self):
        self.netz = None
        self.tv_typen = []
        self.version = "heuristik"
        self._lade_aktuelles_modell()

    def _lade_aktuelles_modell(self):
        meta_pfad = os.path.join(MODEL_DIR, "klassifikator_aktuell.json")
        if not os.path.exists(meta_pfad):
            return
        try:
            with open(meta_pfad) as f:
                meta = json.load(f)
            self.tv_typen = meta["tv_typen"]
            netz = KlassifikatorNetz(len(self.tv_typen))
            netz.load_state_dict(torch.load(
                os.path.join(MODEL_DIR, meta["datei"]),
                map_location="cpu", weights_only=True))
            netz.eval()
            self.netz = netz
            self.version = meta["version"]
        except Exception as e:  # defektes Modell darf die Pipeline nicht stoppen
            print(f"Klassifikator-Modell nicht ladbar ({e}) – Heuristik aktiv")

    def klassifiziere(self, bild_pfad: str):
        """Liefert (bildtyp, konfidenz, tv_typ, tv_konfidenz)."""
        if self.netz is not None:
            try:
                img = Image.open(bild_pfad).convert("RGB")
                x = _transform(img).unsqueeze(0)
                with torch.no_grad():
                    logit_bt, logit_tv = self.netz(x)
                    p_bt = torch.softmax(logit_bt, dim=1)[0]
                    p_tv = torch.softmax(logit_tv, dim=1)[0]
                bt_idx = int(p_bt.argmax())
                tv_idx = int(p_tv.argmax())
                tv_typ = self.tv_typen[tv_idx] if tv_idx < len(self.tv_typen) else ""
                return (BILDTYPEN[bt_idx], float(p_bt[bt_idx]),
                        tv_typ, float(p_tv[tv_idx]))
            except Exception as e:
                print(f"Klassifikation fehlgeschlagen: {e}")
        # Heuristik: App-Dateinamen tragen die Aufnahmeart im Namen
        name = os.path.basename(bild_pfad).lower()
        if "_nah_" in name:
            return "menue", 0.5, "", 0.0
        if "_fern_" in name:
            return "uebersicht", 0.5, "", 0.0
        return "geraet", 0.34, "", 0.0
