"""Hintergrund-Worker: arbeitet die Foto-Warteschlange ab und trainiert nachts.

Läuft als Thread im FastAPI-Prozess. Bewusst nur EIN Analyse-Thread,
damit der Server (16 Kerne, viele andere Projekte) nicht belastet wird –
OMP_NUM_THREADS begrenzt zusätzlich die OCR-Parallelität.
"""

import threading
import time
import traceback
from datetime import datetime

import db

_training_laeuft = threading.Lock()
letzter_fehler = ""
letztes_training = ""


def _arbeite_einen_job() -> bool:
    import pipeline
    job = db.hole_naechsten_job()
    if not job:
        return False
    try:
        room_id = job["room_id"] or pipeline.room_id_aus_pfad(job["pfad"])
        bildtyp, felder, abgleich, status, fehler = pipeline.analysiere_foto(
            job["pfad"], room_id)
        if room_id and room_id != job["room_id"]:
            db.execute("UPDATE foto_analysen SET room_id=%s WHERE id=%s",
                       (room_id, job["id"]))
        db.speichere_ergebnis(job["id"], bildtyp, felder, abgleich, status,
                              pipeline.modell_version(), fehler)
        print(f"Analysiert: {job['pfad']} → {status}")
    except Exception as e:
        traceback.print_exc()
        db.speichere_ergebnis(job["id"], "", {}, {}, "fehler", "", str(e)[:500])
    return True


def _worker_schleife():
    global letzter_fehler
    while True:
        try:
            if not _arbeite_einen_job():
                time.sleep(10)
        except Exception as e:
            letzter_fehler = str(e)
            traceback.print_exc()
            time.sleep(30)


def trainiere_jetzt():
    """Training anstoßen (blockiert, wenn bereits eines läuft)."""
    global letztes_training
    if not _training_laeuft.acquire(blocking=False):
        return {"status": "läuft bereits"}
    try:
        import importlib
        import training
        import pipeline
        ergebnis = training.trainiere_alles()
        # Neue Modelle in die laufende Pipeline laden
        pipeline.klassifikator._lade_aktuelles_modell()
        pipeline.feld_extraktor._lade_aktuelles_modell()
        letztes_training = datetime.now().isoformat(timespec="seconds")
        return {"status": "fertig", "ergebnis": ergebnis}
    finally:
        _training_laeuft.release()


def _training_schleife():
    """Nächtliches Training um ca. 03:30 Uhr."""
    while True:
        jetzt = datetime.now()
        if jetzt.hour == 3 and 30 <= jetzt.minute < 40:
            try:
                print("Nächtliches Training startet…")
                print(trainiere_jetzt())
            except Exception:
                traceback.print_exc()
            time.sleep(700)  # nicht zweimal in demselben Fenster
        time.sleep(300)


def starte_worker():
    threading.Thread(target=_worker_schleife, daemon=True, name="ki-worker").start()
    threading.Thread(target=_training_schleife, daemon=True, name="ki-training").start()
