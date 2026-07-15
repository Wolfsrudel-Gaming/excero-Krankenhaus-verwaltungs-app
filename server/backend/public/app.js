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
    $('#login-pw').focus();
  }
  async function zeigeApp() {
    $('#login-view').hidden = true;
    $('#main-view').hidden = false;
    route();
  }

  $('#login-btn').addEventListener('click', anmelden);
  $('#login-pw').addEventListener('keydown', (e) => { if (e.key === 'Enter') anmelden(); });
  async function anmelden() {
    const fehler = $('#login-fehler');
    fehler.hidden = true;
    try {
      await api('login', { method: 'POST', body: { password: $('#login-pw').value } });
      $('#login-pw').value = '';
      zeigeApp();
    } catch (e) {
      fehler.textContent = e.message;
      fehler.hidden = false;
    }
  }
  $('#logout-btn').addEventListener('click', async () => {
    await api('logout', { method: 'POST' }).catch(() => {});
    zeigeLogin();
  });

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
      else if (teile[0] === 'stundenzettel') await zeigeStundenzettel();
      else await zeigeDashboard();
    } catch (e) {
      if (e.message !== 'Nicht angemeldet') {
        content().innerHTML = `<div class="card fehler">Fehler: ${esc(e.message)}</div>`;
      }
    }
  }

  // ---------- Dashboard ----------
  async function zeigeDashboard() {
    const d = await api('web/overview');
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
        <h3>Letzte Prüfungen</h3>
        ${d.letztePruefungen.length === 0 ? '<div class="hinweis">Noch keine Prüfungen synchronisiert. Sobald die App synchronisiert, erscheinen die Daten hier.</div>' : `
        <table><thead><tr><th>Datum</th><th>Zimmer</th><th>Ergebnis</th><th>Arbeiten</th></tr></thead><tbody>
          ${d.letztePruefungen.map((p) => pruefungsZeile(p)).join('')}
        </tbody></table>`}
      </div>`;
    bindePruefungsZeilen();
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
  async function zeigeStundenzettel() {
    const { zettel } = await api('web/stundenzettel');
    content().innerHTML = `
      <div class="seitentitel">Stundenzettel</div>
      <div class="seitensub">${zettel.length} synchronisierte Leistungsnachweise</div>
      <div class="card">
        ${zettel.length === 0 ? '<div class="hinweis">Noch keine Stundenzettel synchronisiert.</div>' : `
        <table><thead><tr><th>Auftrag</th><th>Station</th><th>Zeitraum ab</th><th>Datum</th><th>Stunden</th><th>Anfahrt</th><th>Techniker</th></tr></thead>
        <tbody>${zettel.map((z) => `<tr>
          <td><b>${esc(z.auftragsnummer || '–')}</b></td>
          <td>${esc(z.station)}</td>
          <td>${deDatum(z.zeitraum_start)}</td>
          <td>${esc(z.datum || '–')}</td>
          <td>${z.stunden ? esc(z.stunden) + ' Std.' : '<span class="badge warn">offen</span>'}</td>
          <td>${z.anfahrt ? esc(z.anfahrt) + ' Std.' : '–'}</td>
          <td>${esc(z.techniker || '–')}</td>
        </tr>`).join('')}</tbody></table>`}
      </div>`;
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
  api('web/me').then(zeigeApp).catch(() => {});
})();
