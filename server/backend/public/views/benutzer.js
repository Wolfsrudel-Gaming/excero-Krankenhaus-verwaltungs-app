/* ── Benutzer-View ──────────────────────────────────────────────────── */
async function viewBenutzer() {
  const area = document.getElementById('content-area');
  area.innerHTML = `
    <div class="page-header"><span class="page-title">Benutzer</span></div>
    <div class="toolbar"><button class="btn btn-primary" id="bu-neu">+ Benutzer</button></div>
    <div class="grid-wrap"><div id="bu-grid"></div></div>`;

  async function lade() {
    const data = await api('/kkh/api/web/users');
    if (!data) return;
    new DataGrid('bu-grid', {
      columns: [
        { key: 'id', label: 'ID', num: true },
        { key: 'username', label: 'Benutzername' },
        { key: 'created_at', label: 'Erstellt', render: (r) =>
          escH(new Date(r.created_at).toLocaleString('de-DE')) },
      ],
      rows: data.users || [],
      actions: (r) => `<button class="btn btn-danger btn-sm" onclick="loescheBenutzer(${r.id},'${escH(r.username)}')">Löschen</button>`,
    });
  }
  await lade();
  document.getElementById('bu-neu').addEventListener('click', async () => {
    const res = await modal('Neuer Benutzer', `
      <div class="form-grid">
        <div class="form-group"><label class="form-label">Benutzername <span class="req">*</span></label>
          <input class="form-control" id="bu-user" autocapitalize="none"></div>
        <div class="form-group"><label class="form-label">Passwort <span class="req">*</span></label>
          <input type="password" class="form-control" id="bu-pw"></div>
        <div class="form-group"><label class="form-label">Passwort wiederholen</label>
          <input type="password" class="form-control" id="bu-pw2"></div>
      </div>`,
      [{ label: 'Abbrechen', value: null }, { label: 'Anlegen', cls: 'btn-primary', value: 'ok' }]);
    if (res !== 'ok') return;
    const pw = document.getElementById('bu-pw')?.value;
    const pw2 = document.getElementById('bu-pw2')?.value;
    if (pw !== pw2) { toast('Passwörter stimmen nicht überein', 'err'); return; }
    try {
      await api('/kkh/api/web/users', { method: 'POST', body: {
        username: document.getElementById('bu-user')?.value?.trim(),
        password: pw,
      }});
      toast('Benutzer angelegt');
      lade();
    } catch (e) { toast(e.message, 'err'); }
  });
}

window.loescheBenutzer = async function(id, name) {
  if (!(await confirm(`Benutzer "${name}" wirklich löschen?`))) return;
  try {
    await api(`/kkh/api/web/users/${id}`, { method: 'DELETE' });
    toast('Benutzer gelöscht');
    viewBenutzer();
  } catch (e) { toast(e.message, 'err'); }
};
