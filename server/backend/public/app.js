/* KKH TV-Wartung – Weboberfläche (Single-Page-App, Hash-Routing) */
(() => {
  const $ = (sel, el = document) => el.querySelector(sel);
  const content = () => $('#content');

  // ---------- API ----------
  async function api(pfad, optionen = {}) {
    const res = await fetch(`api/${pfad}`, {
      headers: optionen.body ? { 'Content-Type': 'application/json' } : {},
      ...optionen,
      body: optionen.body ? JSON.stringify(optionen.body) : undefined,
    });
    if (res.status === 401) { zeigeLogin(); throw new Error('Nicht angemeldet'); }
    const daten = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(daten.error || `Fehler ${res.status}`);
    return daten;
  }

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const deDatum = (iso) => {
    if (!iso) return '–';
    const [j, m, t] = String(iso).slice(0, 10).split('-');
    return (t && m && j) ? `${t}.${m}.${j}` : iso;
  };
  const dateiUrl = (pfad) => `api/web/file?path=${encodeURIComponent(pfad)}`;

  function freenetBadge(gueltigBis) {
    if (!gueltigBis) return '<span class="badge grau">kein Datum</span>';
    const heute = new Date().toISOString().slice(0, 10);
    const bald = new Date(Date.now() + 92 * 864e5).toISOString().slice(0, 10);
    if (gueltigBis < heute) return `<span class="badge err">abgelaufen ${deDatum(gueltigBis)}</span>`;
    if (gueltigBis <= bald) return `<span class="badge warn">bis ${deDatum(gueltigBis)}</span>`;
    return `<span class="badge ok">bis ${deDatum(gueltigBis)}</span>`;
  }

  // Stationen wie in der App sortieren: A2, B2, A3, B3 …, Rest ans Ende
  function stationsSchluessel(s) {
    const m = /^([AB])(\d+)$/.exec(String(s).trim().toUpperCase());
    if (m) return [0, Number(m[2]) * 2 + (m[1] === 'A' ? 0 : 1), ''];
    return [1, 0, String(s).toUpperCase()];
  }
  const stationsVergleich = (a, b) => {
    const [x1, y1, z1] = stationsSchluessel(a), [x2, y2, z2] = stationsSchluessel(b);
    return x1 - x2 || y1 - y2 || z1.localeCompare(z2);
  };

  // ---------- Login ----------
  function zeigeLogin() {
    $('#main-view').hidden = true;
    $('#login-view').hidden = false;
    ($('#login-user').value ? $('#login-pw') : $('#login-user')).focus();
  }
  let aktuellerBenutzer = '';
  async function zeigeApp(me) {
    if (me && me.username) aktuellerBenutzer = me.username;
    const chip = $('#aktueller-benutzer');
    if (chip) chip.textContent = aktuellerBenutzer ? `angemeldet: ${aktuellerBenutzer}` : '';
    $('#login-view').hidden = true;
    $('#main-view').hidden = false;
    route();
  }

  $('#login-btn').addEventListener('click', anmelden);
  $('#login-user').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('#login-pw').focus(); });
  $('#login-pw').addEventListener('keydown', (e) => { if (e.key === 'Enter') anmelden(); });
  async function anmelden() {
    const fehler = $('#login-fehler');
    fehler.hidden = true;
    try {
      const r = await api('login', { method: 'POST', body: {
        username: $('#login-user').value, password: $('#login-pw').value } });
      $('#login-pw').value = '';
      zeigeApp(r);
    } catch (e) {
      fehler.textContent = e.message;
      fehler.hidden = false;
    }
  }
  $('#logout-btn').addEventListener('click', async () => {
    await api('logout', { method: 'POST' }).catch(() => {});
    aktuellerBenutzer = '';
    zeigeLogin();
  });
  $('#pw-aendern-btn').addEventListener('click', eigenesPasswortModal);

  function eigenesPasswortModal() {
    modal(`
      <h3>Mein Passwort ändern</h3>
      <div class="felder">
        <div class="feld"><label>Aktuelles Passwort</label>
          <input id="pw-alt" type="password" autocomplete="current-password"></div>
        <div class="feld"><label>Neues Passwort (min. 4 Zeichen)</label>
          <input id="pw-neu" type="password" autocomplete="new-password"></div>
        <div class="feld"><label>Neues Passwort wiederholen</label>
          <input id="pw-neu2" type="password" autocomplete="new-password"></div>
      </div>
      <div id="pw-fehler" class="fehler" hidden></div>
      <div class="modal-aktionen">
        <button class="btn outline" data-schliessen>Abbrechen</button>
        <button class="btn primary" id="pw-speichern">Ändern</button>
      </div>`);
    $('#pw-speichern').addEventListener('click', async () => {
      const fehler = $('#pw-fehler');
      fehler.hidden = true;
      if ($('#pw-neu').value !== $('#pw-neu2').value) {
        fehler.textContent = 'Die neuen Passwörter stimmen nicht überein.';
        fehler.hidden = false; return;
      }
      try {
        await api('web/change-password', { method: 'POST', body: {
          aktuell: $('#pw-alt').value, neu: $('#pw-neu').value } });
        schliesseModal();
        meldung('Passwort geändert.');
      } catch (e) {
        fehler.textContent = e.message; fehler.hidden = false;
      }
    });
  }

  // ---------- Routing ----------
  window.addEventListener('hashchange', route);
  async function route() {
    const hash = location.hash || '#/dashboard';
    document.querySelectorAll('[data-nav]').forEach((a) => {
      a.classList.toggle('aktiv', hash.startsWith(`#/${a.dataset.nav}`));
    });
    const teile = hash.slice(2).split('/');
    try {
      if (teile[0] === 'zimmer' && teile[1]) await zeigeZimmerDetail(decodeURIComponent(teile[1]));
      else if (teile[0] === 'zimmer') await zeigeZimmer();
      else if (teile[0] === 'pruefungen') await zeigePruefungen();
      else if (teile[0] === 'stundenzettel' && teile[1] === 'neu') await zeigeStundenzettelNeu();
      else if (teile[0] === 'stundenzettel' && teile[1] && teile[2]) {
        await zeigeStundenzettelDetail(decodeURIComponent(teile[1]), decodeURIComponent(teile[2]));
      } else if (teile[0] === 'stundenzettel') await zeigeStundenzettel();
      else if (teile[0] === 'mitarbeiter') await zeigeMitarbeiter();
      else if (teile[0] === 'benutzer') await zeigeBenutzer();
      else await zeigeDashboard();
    } catch (e) {
      if (e.message !== 'Nicht angemeldet') {
        content().innerHTML = `<div class="card fehler">Fehler: ${esc(e.message)}</div>`;
      }
    }
  }

  // ---------- Dashboard ----------
  async function zeigeDashboard() {
    const [d, app] = await Promise.all([api('web/overview'), api('web/app-info')]);
    const ad = app.daten || {};
    const stand = [ad.zimmerStand, ad.pruefungenStand].filter(Boolean).sort().pop();
    content().innerHTML = `
      <div class="seitentitel">Dashboard</div>
      <div class="seitensub">Überblick über die TV-Wartung im Kinderkrankenhaus</div>
      <div class="kpi-grid">
        <div class="kpi"><div class="wert">${d.zimmerAktiv}</div><div class="label">aktive Zimmer (${d.zimmerGesamt} gesamt)</div></div>
        <div class="kpi"><div class="wert">${d.pruefungen7}</div><div class="label">Prüfungen letzte 7 Tage</div></div>
        <div class="kpi"><div class="wert">${d.pruefungen30}</div><div class="label">Prüfungen letzte 30 Tage</div></div>
        <div class="kpi warn"><div class="wert">${d.freenetBald}</div><div class="label">Freenet läuft in &lt; 3 Monaten ab</div></div>
        <div class="kpi err"><div class="wert">${d.freenetAbgelaufen}</div><div class="label">Freenet abgelaufen</div></div>
      </div>

      <div class="card">
        <h3>App-Anbindung (Server-Synchronisation)</h3>
        <div class="app-info-grid">
          <div>
            <div class="feld"><label>Server-URL (in der App eintragen)</label>
              <div class="code-zeile"><code id="ai-url">${esc(app.serverUrl)}</code>
                <button class="btn outline klein" id="ai-url-copy">Kopieren</button></div></div>
            <div class="feld"><label>API-Schlüssel (in der App eintragen)</label>
              <div class="code-zeile"><code id="ai-key" data-key="${esc(app.apiKey)}">••••••••••••••••</code>
                <button class="btn outline klein" id="ai-key-zeigen">Anzeigen</button>
                <button class="btn outline klein" id="ai-key-copy">Kopieren</button></div></div>
            <div class="hinweis">In der App: Einstellungen → Server-Synchronisation → URL und Schlüssel
              eintragen, „Automatisch synchronisieren" aktivieren, dann „Jetzt synchronisieren".</div>
          </div>
          <div>
            <table class="daten-tabelle"><tbody>
              <tr><td>Zimmer (synchronisiert)</td><td><b>${ad.zimmer}</b> (${ad.zimmerAktiv} aktiv)</td></tr>
              <tr><td>Prüfberichte</td><td><b>${ad.pruefberichte}</b></td></tr>
              <tr><td>Stundenzettel</td><td><b>${ad.stundenzettel}</b></td></tr>
              <tr><td>Fotos &amp; PDFs</td><td><b>${ad.dateien}</b> Dateien (${ad.dateienMb} MB)</td></tr>
              <tr><td>Letzter Datenstand</td><td><b>${stand ? deDatum(stand) : 'noch keine Sync-Daten'}</b></td></tr>
            </tbody></table>
          </div>
        </div>
      </div>

      <div class="card">
        <h3>Letzte Prüfungen</h3>
        ${d.letztePruefungen.length === 0 ? '<div class="hinweis">Noch keine Prüfungen synchronisiert. Sobald die App synchronisiert, erscheinen die Daten hier.</div>' : `
        <table><thead><tr><th>Datum</th><th>Zimmer</th><th>Ergebnis</th><th>Arbeiten</th></tr></thead><tbody>
          ${d.letztePruefungen.map((p) => pruefungsZeile(p)).join('')}
        </tbody></table>`}
      </div>`;
    bindePruefungsZeilen();

    const kopiere = async (text, btn) => {
      try { await navigator.clipboard.writeText(text); } catch { /* Clipboard evtl. gesperrt */ }
      const alt = btn.textContent;
      btn.textContent = 'Kopiert ✓';
      setTimeout(() => { btn.textContent = alt; }, 1500);
    };
    $('#ai-url-copy').addEventListener('click', (e) => kopiere($('#ai-url').textContent, e.target));
    $('#ai-key-copy').addEventListener('click', (e) => kopiere($('#ai-key').dataset.key, e.target));
    $('#ai-key-zeigen').addEventListener('click', (e) => {
      const el = $('#ai-key');
      const versteckt = el.textContent.startsWith('•');
      el.textContent = versteckt ? el.dataset.key : '••••••••••••••••';
      e.target.textContent = versteckt ? 'Verbergen' : 'Anzeigen';
    });
  }

  function pruefungsZeile(p) {
    const daten = p.daten || {};
    const nio = (daten.punkte || []).filter((x) => x.ergebnis === false).length;
    return `<tr class="klickbar" data-zimmer="${esc(p.room_id)}">
      <td>${deDatum(p.datum)}</td>
      <td><b>${esc(p.station || '')} / ${esc(p.zimmer || p.room_id)}</b></td>
      <td>${nio === 0 ? '<span class="badge ok">alles i.O.</span>' : `<span class="badge err">${nio}× n.i.O.</span>`}</td>
      <td>${(daten.arbeiten || []).map((a) => `<span class="chip">${esc(a)}</span>`).join(' ') || '–'}</td>
    </tr>`;
  }
  function bindePruefungsZeilen() {
    document.querySelectorAll('tr[data-zimmer]').forEach((tr) => {
      tr.addEventListener('click', () => { location.hash = `#/zimmer/${encodeURIComponent(tr.dataset.zimmer)}`; });
    });
  }

  // ---------- Zimmer ----------
  async function zeigeZimmer() {
    const { rooms } = await api('web/rooms');
    const stationen = [...new Set(rooms.map((r) => r.station))].sort(stationsVergleich);
    content().innerHTML = `
      <div class="seitentitel">Zimmer &amp; Stationen</div>
      <div class="seitensub">${rooms.filter((r) => !r.inaktiv).length} aktive Zimmer in ${stationen.length} Stationen</div>
      <div class="werkzeuge">
        <input type="search" id="suche" placeholder="Zimmer, Station, Seriennummer, Freenet-ID suchen …">
        <label><input type="checkbox" id="zeige-inaktive"> inaktive anzeigen</label>
        <button class="btn primary" id="neu-btn">+ Neues Zimmer</button>
      </div>
      <div id="zimmer-liste"></div>`;

    function render() {
      const q = $('#suche').value.toLowerCase();
      const mitInaktiven = $('#zeige-inaktive').checked;
      const gefiltert = rooms.filter((r) =>
        (mitInaktiven || !r.inaktiv) &&
        (!q || [r.id, r.station, r.zimmer, r.seriennummer, r.freenetId]
          .some((f) => String(f).toLowerCase().includes(q))));
      $('#zimmer-liste').innerHTML = stationen.map((st) => {
        const zimmer = gefiltert.filter((r) => r.station === st);
        if (zimmer.length === 0) return '';
        return `<div class="stations-titel">Station ${esc(st)}</div>
          <div class="zimmer-grid">${zimmer.map((r) => `
            <div class="zimmer-card ${r.inaktiv ? 'inaktiv' : ''}" data-id="${esc(r.id)}">
              <div class="zname">Zimmer ${esc(r.zimmer)} ${r.inaktiv ? '<span class="badge grau">INAKTIV</span>' : ''}</div>
              <div class="zinfo">${esc(r.tvTyp || 'TV-Typ unbekannt')} · SN ${esc(r.seriennummer || '–')}<br>
                letzte Prüfung: ${deDatum(r.letztePruefung)}</div>
              ${freenetBadge(r.gueltigBis)}
            </div>`).join('')}
          </div>`;
      }).join('') || '<div class="card hinweis">Keine Treffer.</div>';
      document.querySelectorAll('.zimmer-card').forEach((c) => {
        c.addEventListener('click', () => { location.hash = `#/zimmer/${encodeURIComponent(c.dataset.id)}`; });
      });
    }
    $('#suche').addEventListener('input', render);
    $('#zeige-inaktive').addEventListener('change', render);
    $('#neu-btn').addEventListener('click', () => neuesZimmerModal(stationen));
    render();
  }

  function neuesZimmerModal(stationen) {
    modal(`
      <h3>Neues Zimmer anlegen</h3>
      <div class="felder">
        <div class="feld"><label>Station (neuer Name legt eine Station an)</label>
          <input id="m-station" list="stationsliste">
          <datalist id="stationsliste">${stationen.map((s) => `<option value="${esc(s)}">`).join('')}</datalist></div>
        <div class="feld"><label>Zimmer (z. B. 01a, 05, SZ)</label><input id="m-zimmer"></div>
        <div class="feld"><label>TV-Typ (optional)</label><input id="m-tvtyp"></div>
        <div class="feld"><label>TV Seriennummer (optional)</label><input id="m-sn"></div>
        <div class="feld"><label>Freenet-ID (optional)</label><input id="m-fid"></div>
        <div class="feld"><label>Freenet gültig bis (JJJJ-MM-TT, optional)</label><input id="m-gueltig" type="date"></div>
      </div>
      <div id="m-fehler" class="fehler" hidden></div>
      <div class="modal-aktionen">
        <button class="btn outline" data-schliessen>Abbrechen</button>
        <button class="btn primary" id="m-anlegen">Anlegen</button>
      </div>`);
    $('#m-anlegen').addEventListener('click', async () => {
      try {
        const { id } = await api('web/rooms', { method: 'POST', body: {
          station: $('#m-station').value, zimmer: $('#m-zimmer').value,
          tvTyp: $('#m-tvtyp').value, seriennummer: $('#m-sn').value,
          freenetId: $('#m-fid').value, gueltigBis: $('#m-gueltig').value,
        }});
        schliesseModal();
        location.hash = `#/zimmer/${encodeURIComponent(id)}`;
      } catch (e) {
        $('#m-fehler').textContent = e.message;
        $('#m-fehler').hidden = false;
      }
    });
  }

  // ---------- Zimmerdetail ----------
  async function zeigeZimmerDetail(id) {
    const { room, inspections, files } = await api(`web/rooms/${encodeURIComponent(id)}`);
    const fotos = files.filter((f) => /\.(jpe?g|png)$/i.test(f.path));
    const pdfs = files.filter((f) => /\.pdf$/i.test(f.path));
    content().innerHTML = `
      <div class="detail-kopf">
        <a href="#/zimmer">← Zimmer</a>
        <h2>Station ${esc(room.station)} · Zimmer ${esc(room.zimmer)}</h2>
        ${room.inaktiv ? '<span class="badge grau">INAKTIV</span>' : ''}
        ${freenetBadge(room.gueltigBis)}
      </div>
      <div class="seitensub">letzte Prüfung: ${deDatum(room.letztePruefung)} · ${inspections.length} Prüfberichte · ${fotos.length} Fotos</div>

      <div class="card">
        <h3>Stammdaten</h3>
        <div class="felder">
          <div class="feld"><label>TV-Typ</label><input id="d-tvtyp" value="${esc(room.tvTyp)}"></div>
          <div class="feld"><label>TV Seriennummer</label><input id="d-sn" value="${esc(room.seriennummer)}"></div>
          <div class="feld"><label>Freenet-ID</label><input id="d-fid" value="${esc(room.freenetId)}"></div>
          <div class="feld"><label>Freenet gültig bis</label><input id="d-gueltig" type="date" value="${esc(room.gueltigBis)}"></div>
        </div>
        <div class="modal-aktionen" style="justify-content:flex-start">
          <button class="btn primary klein" id="d-speichern">Speichern</button>
          <button class="btn outline klein" id="d-inaktiv">${room.inaktiv ? 'Reaktivieren' : 'Inaktiv setzen'}</button>
          <span id="d-status" class="hinweis"></span>
        </div>
      </div>

      <div class="card"><h3>Prüfberichte</h3>
        ${inspections.length === 0 ? '<div class="hinweis">Noch keine Prüfberichte synchronisiert.</div>'
          : inspections.map((i) => pruefungsBlock(i)).join('')}
      </div>

      ${pdfs.length ? `<div class="card"><h3>PDF-Dokumente</h3><div class="pdf-liste">
        ${pdfs.map((f) => `<a href="${dateiUrl(f.path)}" target="_blank">📄 ${esc(f.path.split('/').pop())}</a>`).join('')}
      </div></div>` : ''}

      <div class="card"><h3>Fotos (${fotos.length})</h3>
        ${fotos.length === 0 ? '<div class="hinweis">Keine Fotos synchronisiert.</div>' : `
        <div class="foto-grid">${fotos.map((f) => `
          <a href="${dateiUrl(f.path)}" target="_blank">
            <img loading="lazy" src="${dateiUrl(f.path)}" alt="">
            <div class="datei-label">${esc(f.path.split('/').slice(1).join(' / '))}</div>
          </a>`).join('')}
        </div>`}
      </div>

      <div class="card"><h3>Lebenslauf</h3>
        <div class="lebenslauf">${esc([...room.lebenslauf.split('\n')].reverse().join('\n')) || '–'}</div>
      </div>`;

    $('#d-speichern').addEventListener('click', async () => {
      await api(`web/rooms/${encodeURIComponent(id)}`, { method: 'PATCH', body: {
        tvTyp: $('#d-tvtyp').value, seriennummer: $('#d-sn').value,
        freenetId: $('#d-fid').value, gueltigBis: $('#d-gueltig').value,
      }});
      $('#d-status').textContent = 'Gespeichert – wird beim nächsten Sync an die App übertragen.';
    });
    $('#d-inaktiv').addEventListener('click', async () => {
      await api(`web/rooms/${encodeURIComponent(id)}`, { method: 'PATCH', body: { inaktiv: !room.inaktiv } });
      zeigeZimmerDetail(id);
    });
  }

  function pruefungsBlock(i) {
    const d = i.daten || {};
    const punkte = d.punkte || [];
    const nio = punkte.filter((p) => p.ergebnis === false).length;
    return `<details class="pruefung-block">
      <summary>${deDatum(i.datum)} — ${nio === 0 ? '<span class="badge ok">alles i.O.</span>' : `<span class="badge err">${nio}× n.i.O.</span>`}</summary>
      <table class="punkte-tabelle"><tbody>
        ${punkte.map((p) => `<tr>
          <td>${esc(p.titel)}</td>
          <td>${p.ergebnis === true ? '<span class="badge ok">i.O.</span>' : p.ergebnis === false ? '<span class="badge err">n.i.O.</span>' : '<span class="badge grau">–</span>'}</td>
          <td>${esc(p.bemerkung || '')}</td>
        </tr>`).join('')}
      </tbody></table>
      ${(d.arbeiten || []).length ? `<div class="chip-zeile">${d.arbeiten.map((a) => `<span class="chip">${esc(a)}</span>`).join('')}</div>` : ''}
      ${d.bemerkungen ? `<p class="hinweis" style="margin-top:8px">Bemerkungen: ${esc(d.bemerkungen)}</p>` : ''}
    </details>`;
  }

  // ---------- Prüfungen ----------
  async function zeigePruefungen() {
    const { inspections } = await api('web/inspections?limit=500');
    const stationen = [...new Set(inspections.map((i) => i.station).filter(Boolean))].sort(stationsVergleich);
    content().innerHTML = `
      <div class="seitentitel">Prüfungen</div>
      <div class="seitensub">${inspections.length} synchronisierte Prüfberichte</div>
      <div class="werkzeuge">
        <select id="f-station"><option value="">Alle Stationen</option>
          ${stationen.map((s) => `<option>${esc(s)}</option>`).join('')}</select>
        <input type="date" id="f-von"> bis <input type="date" id="f-bis">
      </div>
      <div class="card" id="p-liste"></div>`;
    function render() {
      const st = $('#f-station').value, von = $('#f-von').value, bis = $('#f-bis').value;
      const gefiltert = inspections.filter((i) =>
        (!st || i.station === st) && (!von || i.datum >= von) && (!bis || i.datum <= bis));
      $('#p-liste').innerHTML = gefiltert.length === 0 ? '<div class="hinweis">Keine Prüfungen im Zeitraum.</div>' : `
        <table><thead><tr><th>Datum</th><th>Zimmer</th><th>Ergebnis</th><th>Arbeiten</th></tr></thead>
        <tbody>${gefiltert.map((p) => pruefungsZeile(p)).join('')}</tbody></table>`;
      bindePruefungsZeilen();
    }
    ['f-station', 'f-von', 'f-bis'].forEach((f) => $(`#${f}`).addEventListener('change', render));
    render();
  }

  // ---------- Stundenzettel ----------
  function parseStd(s) {
    const n = Number(String(s || '').replace(',', '.'));
    return Number.isFinite(n) ? n : 0;
  }
  function fmtStd(n) {
    if (!n) return '–';
    const s = (Math.round(n * 100) / 100).toString().replace('.', ',');
    return `${s} Std.`;
  }

  async function zeigeStundenzettel() {
    const [{ zettel }, rooms] = await Promise.all([
      api('web/stundenzettel'),
      api('web/rooms').then((r) => r.rooms).catch(() => []),
    ]);
    const stationen = [...new Set([
      ...zettel.map((z) => z.station),
      ...rooms.map((r) => r.station),
    ])].sort(stationsVergleich);

    content().innerHTML = `
      <div class="seitentitel">Stundenzettel</div>
      <div class="seitensub">${zettel.length} Leistungsnachweise · bearbeiten und speichern, Sync zur App über last-write-wins</div>
      <div class="werkzeuge">
        <select id="f-st"><option value="">Alle Stationen</option>
          ${stationen.map((s) => `<option value="${esc(s)}">${esc(s)}</option>`).join('')}</select>
        <input type="search" id="f-suche" placeholder="Auftrag, Techniker suchen …">
        <a class="btn outline" href="#/mitarbeiter">Mitarbeiter</a>
        <button class="btn primary" id="neu-zettel">+ Neuer Stundenzettel</button>
      </div>
      <div class="card" id="z-liste"></div>`;

    function render() {
      const st = $('#f-st').value;
      const q = ($('#f-suche').value || '').toLowerCase();
      const gefiltert = zettel.filter((z) =>
        (!st || z.station === st) &&
        (!q || [z.auftragsnummer, z.techniker, z.station, z.datum]
          .some((f) => String(f || '').toLowerCase().includes(q))));
      if (gefiltert.length === 0) {
        $('#z-liste').innerHTML = '<div class="hinweis">Keine Stundenzettel gefunden.</div>';
        return;
      }
      $('#z-liste').innerHTML = `
        <table><thead><tr>
          <th>Auftrag</th><th>Station</th><th>Zeitraum ab</th><th>Datum</th>
          <th>Stunden (Kopf)</th><th>Team</th><th>Techniker</th>
        </tr></thead>
        <tbody>${gefiltert.map((z) => {
          const team = z.team || {};
          const teamStd = team.stunden || 0;
          const kopfOffen = !z.stunden && team.anzahl === 0;
          const hash = `#/stundenzettel/${encodeURIComponent(z.station)}/${encodeURIComponent(z.zeitraum_start)}`;
          return `<tr class="klickbar" data-href="${hash}">
            <td><b>${esc(z.auftragsnummer || '–')}</b></td>
            <td>${esc(z.station)}</td>
            <td>${deDatum(z.zeitraum_start)}</td>
            <td>${esc(z.datum || '–')}</td>
            <td>${kopfOffen ? '<span class="badge warn">offen</span>'
              : (z.stunden ? esc(z.stunden) + ' Std.' : '–')}</td>
            <td>${team.anzahl
              ? `<span class="badge ok">${team.anzahl}</span> ${fmtStd(teamStd)}`
              : '<span class="badge grau">kein Team</span>'}</td>
            <td>${esc(z.techniker || '–')}</td>
          </tr>`;
        }).join('')}</tbody></table>`;
      document.querySelectorAll('#z-liste tr[data-href]').forEach((tr) => {
        tr.addEventListener('click', () => { location.hash = tr.dataset.href; });
      });
    }
    $('#f-st').addEventListener('change', render);
    $('#f-suche').addEventListener('input', render);
    $('#neu-zettel').addEventListener('click', () => { location.hash = '#/stundenzettel/neu'; });
    render();
  }

  async function zeigeStundenzettelNeu() {
    const [{ rooms }, nr] = await Promise.all([
      api('web/rooms'),
      api('web/stundenzettel/next-nr'),
    ]);
    const stationen = [...new Set(rooms.map((r) => r.station))].sort(stationsVergleich);
    const heute = new Date().toISOString().slice(0, 10);
    const heuteDe = deDatum(heute);

    content().innerHTML = `
      <div class="detail-kopf">
        <a href="#/stundenzettel">← Stundenzettel</a>
        <h2>Neuer Stundenzettel</h2>
      </div>
      <div class="seitensub">Ein Leistungsnachweis pro Station und Anfahrt / Prüfzeitraum</div>
      <div class="card">
        <div class="felder">
          <div class="feld"><label>Station *</label>
            <input id="nz-station" list="nz-stationsliste" autocomplete="off">
            <datalist id="nz-stationsliste">${stationen.map((s) => `<option value="${esc(s)}">`).join('')}</datalist>
          </div>
          <div class="feld"><label>Zeitraum-Beginn * (JJJJ-MM-TT)</label>
            <input id="nz-zeitraum" type="date" value="${heute}"></div>
          <div class="feld"><label>Auftragsnummer</label>
            <input id="nz-auftrag" value="${esc(nr.auftragsnummer)}"></div>
          <div class="feld"><label>Leistungsdatum (Anzeige)</label>
            <input id="nz-datum" value="${esc(heuteDe)}" placeholder="TT.MM.JJJJ"></div>
          <div class="feld"><label>Stunden (Kopf, optional)</label>
            <input id="nz-stunden" placeholder="z. B. 3,5"></div>
          <div class="feld"><label>Anfahrt (Kopf, optional)</label>
            <input id="nz-anfahrt" placeholder="z. B. 1"></div>
          <div class="feld"><label>Techniker / Dienstleister</label>
            <input id="nz-techniker" placeholder="Name"></div>
        </div>
        <div id="nz-fehler" class="fehler" hidden style="margin-top:12px"></div>
        <div class="modal-aktionen" style="justify-content:flex-start">
          <button class="btn primary" id="nz-speichern">Anlegen &amp; öffnen</button>
          <a class="btn outline" href="#/stundenzettel">Abbrechen</a>
        </div>
      </div>`;

    $('#nz-speichern').addEventListener('click', async () => {
      const fehler = $('#nz-fehler');
      fehler.hidden = true;
      try {
        const station = $('#nz-station').value.trim();
        const zeitraum = $('#nz-zeitraum').value;
        await api('web/stundenzettel', { method: 'PUT', body: {
          station, zeitraum_start: zeitraum,
          auftragsnummer: $('#nz-auftrag').value,
          datum: $('#nz-datum').value,
          stunden: $('#nz-stunden').value,
          anfahrt: $('#nz-anfahrt').value,
          techniker: $('#nz-techniker').value,
        }});
        meldung('Stundenzettel angelegt.');
        location.hash = `#/stundenzettel/${encodeURIComponent(station)}/${encodeURIComponent(zeitraum)}`;
      } catch (e) {
        fehler.textContent = e.message; fehler.hidden = false;
      }
    });
  }

  async function zeigeStundenzettelDetail(station, zeitraum) {
    const d = await api(`web/stundenzettel/detail?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraum)}`);
    const z = d.zettel;
    let eintraege = d.eintraege.slice();
    const maNamen = d.mitarbeiter.map((m) => m.name);

    function teamSumme() {
      return eintraege.reduce((a, e) => ({
        stunden: a.stunden + parseStd(e.stunden),
        anfahrt: a.anfahrt + parseStd(e.anfahrt),
      }), { stunden: 0, anfahrt: 0 });
    }

    function renderTeam() {
      const sum = teamSumme();
      $('#team-tabelle').innerHTML = eintraege.length === 0
        ? '<div class="hinweis">Noch keine Team-Zeilen. Mitarbeiter hinzufügen oder in der App erfassen.</div>'
        : `<table><thead><tr><th>Mitarbeiter</th><th>Stunden</th><th>Anfahrt</th><th></th></tr></thead>
          <tbody>${eintraege.map((e, i) => `
            <tr>
              <td><b>${esc(e.mitarbeiter)}</b></td>
              <td><input class="klein-input" data-field="stunden" data-i="${i}" value="${esc(e.stunden)}"></td>
              <td><input class="klein-input" data-field="anfahrt" data-i="${i}" value="${esc(e.anfahrt)}"></td>
              <td><button class="btn danger klein" data-del="${i}">Entfernen</button></td>
            </tr>`).join('')}
            <tr class="summenzeile"><td><b>Summe Team</b></td>
              <td><b>${fmtStd(sum.stunden)}</b></td>
              <td><b>${fmtStd(sum.anfahrt)}</b></td><td></td></tr>
          </tbody></table>`;
      document.querySelectorAll('#team-tabelle [data-field]').forEach((inp) => {
        inp.addEventListener('change', () => {
          eintraege[Number(inp.dataset.i)][inp.dataset.field] = inp.value;
          renderTeam();
        });
      });
      document.querySelectorAll('#team-tabelle [data-del]').forEach((btn) => {
        btn.addEventListener('click', async () => {
          const e = eintraege[Number(btn.dataset.del)];
          if (!confirm(`Zeile „${e.mitarbeiter}" entfernen?`)) return;
          try {
            await api(`web/zettel-eintraege?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraum)}&mitarbeiter=${encodeURIComponent(e.mitarbeiter)}`,
              { method: 'DELETE' });
            eintraege = eintraege.filter((x) => x.mitarbeiter !== e.mitarbeiter);
            meldung('Team-Zeile entfernt.');
            renderTeam();
          } catch (err) { meldung(err.message, true); }
        });
      });
    }

    content().innerHTML = `
      <div class="detail-kopf">
        <a href="#/stundenzettel">← Stundenzettel</a>
        <h2>Station ${esc(z.station)} · ${esc(z.auftragsnummer || 'ohne Auftrag')}</h2>
      </div>
      <div class="seitensub">Zeitraum ab ${deDatum(z.zeitraum_start)}${d.zeitraumEnde ? ` bis ${deDatum(d.zeitraumEnde)}` : ' (offen)'} · ${d.inspections.length} Prüfungen im Zeitraum</div>

      <div class="card">
        <h3>Kopfdaten</h3>
        <div class="felder">
          <div class="feld"><label>Auftragsnummer</label>
            <input id="z-auftrag" value="${esc(z.auftragsnummer)}"></div>
          <div class="feld"><label>Leistungsdatum</label>
            <input id="z-datum" value="${esc(z.datum)}" placeholder="TT.MM.JJJJ"></div>
          <div class="feld"><label>Stunden (Kopf / Legacy)</label>
            <input id="z-stunden" value="${esc(z.stunden)}" placeholder="z. B. 3,5"></div>
          <div class="feld"><label>Anfahrt (Kopf / Legacy)</label>
            <input id="z-anfahrt" value="${esc(z.anfahrt)}"></div>
          <div class="feld"><label>Techniker / Dienstleister</label>
            <input id="z-techniker" value="${esc(z.techniker)}"></div>
          <div class="feld"><label>Station</label>
            <input value="${esc(z.station)}" disabled></div>
          <div class="feld"><label>Zeitraum-Beginn</label>
            <input value="${esc(z.zeitraum_start)}" disabled></div>
        </div>
        <div class="modal-aktionen" style="justify-content:flex-start;margin-top:16px">
          <button class="btn primary" id="z-speichern">Kopfdaten speichern</button>
          <button class="btn danger" id="z-loeschen">Stundenzettel löschen</button>
        </div>
      </div>

      <div class="card">
        <h3>Team-Zeilen <span class="seitensub" style="display:inline;margin:0">(eine Zeile je Mitarbeiter)</span></h3>
        <div class="werkzeuge" style="margin-bottom:12px">
          <input id="te-ma" list="te-maliste" placeholder="Mitarbeiter" autocomplete="off">
          <datalist id="te-maliste">${maNamen.map((n) => `<option value="${esc(n)}">`).join('')}</datalist>
          <input id="te-std" class="klein-input" placeholder="Stunden" style="width:90px">
          <input id="te-anf" class="klein-input" placeholder="Anfahrt" style="width:90px">
          <button class="btn primary klein" id="te-add">Zeile speichern</button>
          <a class="btn outline klein" href="#/mitarbeiter">Mitarbeiter verwalten</a>
        </div>
        <div id="team-tabelle"></div>
        <div class="modal-aktionen" style="justify-content:flex-start;margin-top:12px">
          <button class="btn outline" id="te-alle-speichern">Alle Team-Zeilen speichern</button>
        </div>
      </div>

      <div class="card">
        <h3>Leistungen im Zeitraum</h3>
        ${d.inspections.length === 0
          ? '<div class="hinweis">Keine Prüfungen für diese Station im Zeitraum.</div>'
          : `<table><thead><tr><th>Datum</th><th>Zimmer</th><th>Prüfer</th><th>Arbeiten</th></tr></thead>
            <tbody>${d.inspections.map((i) => `<tr>
              <td>${deDatum(i.datum)}</td>
              <td><a href="#/zimmer/${encodeURIComponent(i.roomId)}">${esc(i.zimmer || i.roomId)}</a></td>
              <td>${esc(i.mitarbeiter || '–')}</td>
              <td>${(i.arbeiten || []).length
                ? `<div class="chip-zeile">${i.arbeiten.map((a) => `<span class="chip">${esc(a)}</span>`).join('')}</div>`
                : '<span class="hinweis">–</span>'}</td>
            </tr>`).join('')}</tbody></table>`}
      </div>`;

    renderTeam();

    $('#z-speichern').addEventListener('click', async () => {
      try {
        await api('web/stundenzettel', { method: 'PUT', body: {
          station, zeitraum_start: zeitraum,
          auftragsnummer: $('#z-auftrag').value,
          datum: $('#z-datum').value,
          stunden: $('#z-stunden').value,
          anfahrt: $('#z-anfahrt').value,
          techniker: $('#z-techniker').value,
        }});
        meldung('Kopfdaten gespeichert – Sync zur App beim nächsten Abgleich.');
      } catch (e) { meldung(e.message, true); }
    });

    $('#z-loeschen').addEventListener('click', async () => {
      if (!confirm(`Stundenzettel Station ${station} (${zeitraum}) inkl. Team-Zeilen wirklich löschen?`)) return;
      try {
        await api(`web/stundenzettel?station=${encodeURIComponent(station)}&zeitraum=${encodeURIComponent(zeitraum)}`,
          { method: 'DELETE' });
        meldung('Stundenzettel gelöscht.');
        location.hash = '#/stundenzettel';
      } catch (e) { meldung(e.message, true); }
    });

    $('#te-add').addEventListener('click', async () => {
      const mitarbeiter = $('#te-ma').value.trim();
      if (!mitarbeiter) { meldung('Mitarbeitername angeben.', true); return; }
      try {
        await api('web/zettel-eintraege', { method: 'PUT', body: {
          station, zeitraum_start: zeitraum, mitarbeiter,
          stunden: $('#te-std').value, anfahrt: $('#te-anf').value,
        }});
        const idx = eintraege.findIndex((e) => e.mitarbeiter === mitarbeiter);
        const neu = { station, zeitraum_start: zeitraum, mitarbeiter,
          stunden: $('#te-std').value, anfahrt: $('#te-anf').value };
        if (idx >= 0) eintraege[idx] = neu; else eintraege.push(neu);
        eintraege.sort((a, b) => a.mitarbeiter.localeCompare(b.mitarbeiter, 'de'));
        $('#te-ma').value = ''; $('#te-std').value = ''; $('#te-anf').value = '';
        meldung('Team-Zeile gespeichert.');
        renderTeam();
      } catch (e) { meldung(e.message, true); }
    });

    $('#te-alle-speichern').addEventListener('click', async () => {
      try {
        await api('web/zettel-eintraege', { method: 'PUT', body: {
          eintraege: eintraege.map((e) => ({
            station, zeitraum_start: zeitraum, mitarbeiter: e.mitarbeiter,
            stunden: e.stunden, anfahrt: e.anfahrt,
          })),
        }});
        meldung(`${eintraege.length} Team-Zeilen gespeichert.`);
      } catch (e) { meldung(e.message, true); }
    });
  }

  // ---------- Mitarbeiter ----------
  async function zeigeMitarbeiter() {
    const { mitarbeiter } = await api('web/mitarbeiter');
    content().innerHTML = `
      <div class="seitentitel">Mitarbeiter</div>
      <div class="seitensub">Liste für Geräte-Einrichtung und Team-Stundenzettel · Sync zur App</div>
      <div class="werkzeuge">
        <input id="ma-name" placeholder="Name (z. B. Max Mustermann)" autocomplete="off">
        <button class="btn primary" id="ma-add">+ Hinzufügen</button>
        <a class="btn outline" href="#/stundenzettel">← zu Stundenzetteln</a>
      </div>
      <div class="card">
        ${mitarbeiter.length === 0 ? '<div class="hinweis">Noch keine Mitarbeiter angelegt.</div>' : `
        <table><thead><tr><th>Name</th><th>Status</th><th></th></tr></thead>
        <tbody>${mitarbeiter.map((m) => `
          <tr>
            <td><b>${esc(m.name)}</b></td>
            <td>${m.aktiv ? '<span class="badge ok">aktiv</span>' : '<span class="badge grau">inaktiv</span>'}</td>
            <td class="aktionen-zelle">
              <button class="btn outline klein" data-toggle="${esc(m.name)}" data-aktiv="${m.aktiv ? '1' : '0'}">
                ${m.aktiv ? 'Deaktivieren' : 'Reaktivieren'}
              </button>
            </td>
          </tr>`).join('')}</tbody></table>`}
      </div>`;

    $('#ma-add').addEventListener('click', async () => {
      const name = $('#ma-name').value.trim();
      if (!name) return;
      try {
        await api('web/mitarbeiter', { method: 'POST', body: { name } });
        meldung(`„${name}" hinzugefügt.`);
        zeigeMitarbeiter();
      } catch (e) { meldung(e.message, true); }
    });
    document.querySelectorAll('[data-toggle]').forEach((b) => b.addEventListener('click', async () => {
      try {
        await api(`web/mitarbeiter/${encodeURIComponent(b.dataset.toggle)}`, {
          method: 'PATCH', body: { aktiv: b.dataset.aktiv !== '1' },
        });
        zeigeMitarbeiter();
      } catch (e) { meldung(e.message, true); }
    }));
  }

  // ---------- Benutzer (Admin) ----------
  async function zeigeBenutzer() {
    const { users, aktuell } = await api('web/users');
    aktuellerBenutzer = aktuell || aktuellerBenutzer;
    content().innerHTML = `
      <div class="seitentitel">Benutzer</div>
      <div class="seitensub">${users.length} Benutzer · alle haben vollen Zugriff auf die Weboberfläche</div>
      <div class="werkzeuge">
        <button class="btn primary" id="neu-benutzer-btn">+ Neuer Benutzer</button>
      </div>
      <div class="card">
        <table><thead><tr><th>Benutzername</th><th>Angelegt</th><th>Zuletzt geändert</th><th></th></tr></thead>
        <tbody>${users.map((u) => `
          <tr>
            <td><b>${esc(u.username)}</b>${u.username === aktuell ? ' <span class="badge ok">Sie</span>' : ''}</td>
            <td>${deDatum(u.created_at)}</td>
            <td>${deDatum(u.updated_at)}</td>
            <td class="aktionen-zelle">
              <button class="btn outline klein" data-pw="${u.id}" data-name="${esc(u.username)}">Passwort zurücksetzen</button>
              ${u.username === aktuell ? '' : `<button class="btn danger klein" data-del="${u.id}" data-name="${esc(u.username)}">Löschen</button>`}
            </td>
          </tr>`).join('')}</tbody></table>
      </div>`;

    $('#neu-benutzer-btn').addEventListener('click', neuerBenutzerModal);
    document.querySelectorAll('[data-pw]').forEach((b) => b.addEventListener('click',
      () => passwortResetModal(b.dataset.pw, b.dataset.name)));
    document.querySelectorAll('[data-del]').forEach((b) => b.addEventListener('click',
      () => benutzerLoeschen(b.dataset.del, b.dataset.name)));
  }

  function neuerBenutzerModal() {
    modal(`
      <h3>Neuen Benutzer anlegen</h3>
      <div class="felder">
        <div class="feld"><label>Benutzername</label>
          <input id="nb-user" autocapitalize="none" autocomplete="off"></div>
        <div class="feld"><label>Passwort (min. 4 Zeichen)</label>
          <input id="nb-pw" type="password" autocomplete="new-password"></div>
      </div>
      <div id="nb-fehler" class="fehler" hidden></div>
      <div class="modal-aktionen">
        <button class="btn outline" data-schliessen>Abbrechen</button>
        <button class="btn primary" id="nb-anlegen">Anlegen</button>
      </div>`);
    $('#nb-anlegen').addEventListener('click', async () => {
      try {
        await api('web/users', { method: 'POST', body: {
          username: $('#nb-user').value, password: $('#nb-pw').value } });
        schliesseModal();
        meldung('Benutzer angelegt.');
        zeigeBenutzer();
      } catch (e) {
        $('#nb-fehler').textContent = e.message; $('#nb-fehler').hidden = false;
      }
    });
  }

  function passwortResetModal(id, name) {
    modal(`
      <h3>Passwort zurücksetzen</h3>
      <div class="seitensub">für Benutzer <b>${esc(name)}</b></div>
      <div class="felder">
        <div class="feld"><label>Neues Passwort (min. 4 Zeichen)</label>
          <input id="rp-pw" type="password" autocomplete="new-password"></div>
      </div>
      <div id="rp-fehler" class="fehler" hidden></div>
      <div class="modal-aktionen">
        <button class="btn outline" data-schliessen>Abbrechen</button>
        <button class="btn primary" id="rp-speichern">Passwort setzen</button>
      </div>`);
    $('#rp-speichern').addEventListener('click', async () => {
      try {
        await api(`web/users/${id}`, { method: 'PATCH', body: { password: $('#rp-pw').value } });
        schliesseModal();
        meldung('Passwort gesetzt.');
      } catch (e) {
        $('#rp-fehler').textContent = e.message; $('#rp-fehler').hidden = false;
      }
    });
  }

  async function benutzerLoeschen(id, name) {
    if (!confirm(`Benutzer „${name}" wirklich löschen?`)) return;
    try {
      await api(`web/users/${id}`, { method: 'DELETE' });
      meldung('Benutzer gelöscht.');
      zeigeBenutzer();
    } catch (e) {
      meldung(e.message, true);
    }
  }

  // ---------- Toast-Meldung ----------
  function meldung(text, istFehler = false) {
    const t = document.createElement('div');
    t.className = `toast ${istFehler ? 'toast-fehler' : ''}`;
    t.textContent = text;
    document.body.appendChild(t);
    setTimeout(() => t.classList.add('sichtbar'), 10);
    setTimeout(() => { t.classList.remove('sichtbar'); setTimeout(() => t.remove(), 300); }, 3000);
  }

  // ---------- Modal ----------
  function modal(html) {
    schliesseModal();
    const wrap = document.createElement('div');
    wrap.className = 'modal-hintergrund';
    wrap.id = 'modal';
    wrap.innerHTML = `<div class="modal">${html}</div>`;
    wrap.addEventListener('click', (e) => { if (e.target === wrap) schliesseModal(); });
    document.body.appendChild(wrap);
    wrap.querySelectorAll('[data-schliessen]').forEach((b) => b.addEventListener('click', schliesseModal));
  }
  function schliesseModal() { $('#modal')?.remove(); }

  // ---------- Start ----------
  api('web/me').then((me) => zeigeApp(me)).catch(() => zeigeLogin());
})();
