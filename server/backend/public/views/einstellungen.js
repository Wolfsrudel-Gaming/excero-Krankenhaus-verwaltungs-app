/* global toast, api */
'use strict';

async function viewEinstellungen() {
  const ca = document.getElementById('content-area');
  ca.innerHTML = '<div class="loading">Lade Einstellungen…</div>';

  const d = await api('/kkh/api/web/einstellungen');
  const ein = d.einstellungen || {};
  const smtp = ein.smtp || {};
  const hd   = ein.hidrive || {};

  ca.innerHTML = `
    <div class="view-header"><h1>Einstellungen</h1></div>

    <div class="settings-section">
      <h3>📧 SMTP (E-Mail-Versand)</h3>
      <div class="form-grid" style="grid-template-columns:1fr 1fr;max-width:700px">
        <div class="form-group"><label class="form-label">SMTP-Host</label>
          <input class="form-control" id="sm-host" value="${smtp.host || ''}" placeholder="mail.example.com"></div>
        <div class="form-group"><label class="form-label">Port</label>
          <input class="form-control" type="number" id="sm-port" value="${smtp.port || 587}"></div>
        <div class="form-group"><label class="form-label">Benutzername</label>
          <input class="form-control" id="sm-user" value="${smtp.user || ''}"></div>
        <div class="form-group"><label class="form-label">Passwort</label>
          <input class="form-control" type="password" id="sm-pass" value="${smtp.passwort || ''}" placeholder="${smtp.passwort ? '••••••••' : 'Kein Passwort'}"></div>
        <div class="form-group"><label class="form-label">Absender (Von-Adresse)</label>
          <input class="form-control" id="sm-abs" value="${smtp.absender || ''}" placeholder="noreply@example.com"></div>
        <div class="form-group"><label class="form-label">TLS (Port 465)</label>
          <select class="form-control" id="sm-sec">
            <option value="false" ${!smtp.secure ? 'selected':''}>STARTTLS (Standard)</option>
            <option value="true" ${smtp.secure ? 'selected':''}>SSL/TLS (Port 465)</option>
          </select></div>
      </div>
      <button class="btn btn-primary btn-sm" id="smtp-save">Speichern</button>
      <button class="btn btn-ghost btn-sm" id="smtp-test" style="margin-left:8px">Verbindung testen</button>
    </div>

    <div class="settings-section" style="margin-top:24px">
      <h3>☁️ HiDrive (WebDAV)</h3>
      <p class="form-hint">Strato HiDrive – WebDAV-URL: <code>https://webdav.hidrive.strato.com/users/&lt;benutzername&gt;/</code></p>
      <div class="form-grid" style="grid-template-columns:1fr 1fr;max-width:700px">
        <div class="form-group" style="grid-column:1/-1"><label class="form-label">WebDAV-URL</label>
          <input class="form-control" id="hd-url" value="${hd.url || ''}" placeholder="https://webdav.hidrive.strato.com/users/..."></div>
        <div class="form-group"><label class="form-label">Benutzername</label>
          <input class="form-control" id="hd-user" value="${hd.user || ''}"></div>
        <div class="form-group"><label class="form-label">Passwort</label>
          <input class="form-control" type="password" id="hd-pass" value="${hd.passwort || ''}" placeholder="${hd.passwort ? '••••••••' : 'Kein Passwort'}"></div>
      </div>
      <button class="btn btn-primary btn-sm" id="hd-save">Speichern</button>
      <button class="btn btn-ghost btn-sm" id="hd-test" style="margin-left:8px">Verbindung testen</button>
    </div>`;

  document.getElementById('smtp-save').addEventListener('click', async () => {
    try {
      await api('/kkh/api/web/einstellungen/smtp', { method: 'PUT', body: {
        host: document.getElementById('sm-host')?.value,
        port: Number(document.getElementById('sm-port')?.value) || 587,
        user: document.getElementById('sm-user')?.value,
        passwort: document.getElementById('sm-pass')?.value,
        absender: document.getElementById('sm-abs')?.value,
        secure: document.getElementById('sm-sec')?.value === 'true',
      }});
      toast('SMTP gespeichert');
    } catch (e) { toast(e.message, 'err'); }
  });

  document.getElementById('smtp-test').addEventListener('click', async () => {
    toast('SMTP-Test: Bitte E-Mail-Konfiguration prüfen (Testversand noch nicht implementiert)', 'warn');
  });

  document.getElementById('hd-save').addEventListener('click', async () => {
    try {
      await api('/kkh/api/web/einstellungen/hidrive', { method: 'PUT', body: {
        url: document.getElementById('hd-url')?.value,
        user: document.getElementById('hd-user')?.value,
        passwort: document.getElementById('hd-pass')?.value,
      }});
      toast('HiDrive gespeichert');
    } catch (e) { toast(e.message, 'err'); }
  });

  document.getElementById('hd-test').addEventListener('click', async () => {
    try {
      await api('/kkh/api/web/hidrive/list?pfad=/');
      toast('HiDrive: Verbindung erfolgreich!');
    } catch (e) { toast(`HiDrive-Fehler: ${e.message}`, 'err'); }
  });
}
