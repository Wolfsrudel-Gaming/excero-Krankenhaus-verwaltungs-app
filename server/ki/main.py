"""KI-Service für die KKH-Fotoerkennung – FastAPI.

Nur intern im Docker-Netz erreichbar (kein Port nach außen); das
Node-Backend spricht diesen Service für Status/Training an, die
Analyse-Warteschlange läuft direkt über die gemeinsame PostgreSQL.
"""

import os

from fastapi import FastAPI
from pydantic import BaseModel

import db
import worker

app = FastAPI(title="KKH KI-Service", docs_url=None, redoc_url=None)


@app.on_event("startup")
def startup():
    db.init_schema()
    _stelle_fehlende_fotos_ein()
    worker.starte_worker()
    print("KI-Service bereit – Worker läuft")


def _stelle_fehlende_fotos_ein():
    """Beim Start alle Fotos einreihen, die noch keine Analyse haben.

    Fängt sowohl den Erst-Bootstrap ab als auch Fotos, die hochgeladen
    wurden, während der KI-Container nicht lief.
    """
    files_dir = os.environ.get("FILES_DIR", "/data/files")
    neu = 0
    for wurzel, _dirs, dateien in os.walk(files_dir):
        for name in dateien:
            if not name.lower().endswith((".jpg", ".jpeg", ".png")):
                continue
            rel = os.path.relpath(os.path.join(wurzel, name), files_dir).replace(os.sep, "/")
            # Signaturen werden grundsätzlich NICHT analysiert (Datenschutz)
            if "_signaturen" in rel:
                continue
            zeilen = db.execute(
                """INSERT INTO foto_analysen (pfad, room_id)
                   VALUES (%s, %s) ON CONFLICT (pfad) DO NOTHING""",
                (rel, rel.split("/")[0] if not rel.startswith("_") else ""))
            neu += zeilen
    if neu:
        print(f"{neu} Foto(s) zur Analyse eingereiht")


@app.get("/status")
def status():
    zaehler = {r["status"]: r["anzahl"] for r in db.query(
        "SELECT status, count(*)::int AS anzahl FROM foto_analysen GROUP BY status")}
    modelle = db.query(
        """SELECT DISTINCT ON (name) name, version, genauigkeit, anzahl_labels, trainiert_am
           FROM ki_modelle ORDER BY name, trainiert_am DESC""")
    labels = db.query("SELECT count(*)::int AS n FROM ki_labels")
    import pipeline
    return {
        "warteschlange": zaehler,
        "modelle": [dict(m, trainiert_am=str(m["trainiert_am"])) for m in modelle],
        "modell_version_aktiv": pipeline.modell_version(),
        "labels_gesamt": labels[0]["n"] if labels else 0,
        "letztes_training": worker.letztes_training,
    }


class AnalyseAnfrage(BaseModel):
    pfad: str
    room_id: str = ""


@app.post("/analyse")
def analyse_einreihen(anfrage: AnalyseAnfrage):
    """Einzelnes Foto (erneut) in die Warteschlange stellen."""
    if "_signaturen" in anfrage.pfad:
        return {"ok": False, "error": "Signaturen werden nicht analysiert"}
    db.execute(
        """INSERT INTO foto_analysen (pfad, room_id, status)
           VALUES (%s, %s, 'wartet')
           ON CONFLICT (pfad) DO UPDATE SET status='wartet', fehler=''""",
        (anfrage.pfad, anfrage.room_id))
    return {"ok": True}


@app.post("/train")
def train():
    """Training sofort anstoßen (z. B. nach vielen Bestätigungen im Web)."""
    return worker.trainiere_jetzt()
