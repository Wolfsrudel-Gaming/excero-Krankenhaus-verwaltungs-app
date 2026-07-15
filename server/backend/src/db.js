const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

const pool = new Pool({
  host: process.env.PGHOST || 'db',
  port: Number(process.env.PGPORT || 5432),
  user: process.env.PGUSER || 'kkh',
  password: process.env.PGPASSWORD,
  database: process.env.PGDATABASE || 'kkh',
});

async function init() {
  const schema = fs.readFileSync(path.join(__dirname, 'schema.sql'), 'utf8');
  // Warten, bis die Datenbank erreichbar ist (Containerstart)
  for (let versuch = 1; ; versuch++) {
    try {
      await pool.query(schema);
      console.log('Datenbankschema bereit.');
      return;
    } catch (e) {
      if (versuch >= 30) throw e;
      console.log(`Warte auf Datenbank (${versuch}/30) …`);
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
}

module.exports = { pool, init };
