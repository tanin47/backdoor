import type {SqlHistoryEntry} from "../common/models";

const LOCAL_STORAGE_KEY = "sql_history_entries";

function getEntries(): SqlHistoryEntry[] {
  const raw: string | null = localStorage.getItem(LOCAL_STORAGE_KEY);
  if (!raw) {
    return []
  }

  let entries: SqlHistoryEntry[] = [];
  try {
    entries = JSON.parse(raw);
  } catch (e) {
    console.warn(`Unable to parse the ${LOCAL_STORAGE_KEY} from LocalStorage as a JSON: ${raw}. Resetting...`, e);
  }
  return entries;
}

export function saveNewSqlHistoryEntry(sql: string, database: string): void {
  const entries = getEntries();

  entries.unshift({
    sql,
    database,
    executedAt: new Date().getTime()
  })
  localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(entries));
}

export function getSqlHistoryEntries(keyword: string | null): SqlHistoryEntry[] {
  const entries = getEntries();

  if (!keyword) {
    return entries;
  }

  const sanitized = keyword.toLocaleLowerCase().trim()

  return entries
    .filter(entry => entry.sql.toLocaleLowerCase().includes(sanitized))
}
