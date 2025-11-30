import type {SqlHistoryEntry} from "../common/models";
import {post} from "../common/form";

export async function saveNewSqlHistoryEntry(sql: string, database: string): Promise<void> {
  try {
    const _json = await post('/api/save-sql-history-entry', {
      sql,
      database,
      executedAt: new Date().getTime(),
    })
  } catch (e) {
    console.warn(`Failed to save sql history entry: ${e}`);
    // ignore
  }
}

export async function getSqlHistoryEntries(keyword: string | null): Promise<SqlHistoryEntry[]> {
  try {
    const json = await post('/api/get-sql-history-entries', {
      keyword,
    })

    return json.entries;
  } catch (e) {
    console.warn(`Failed to get the sql history entries: ${e}`);
    // ignore
    return [];
  }
}
