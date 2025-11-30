import type {SqlHistoryEntry} from "../common/models";
import {PARADIGM} from "../common/globals";
import * as webEngine from "./web_engine";
import * as desktopEngine from "./desktop_engine";

export async function saveNewSqlHistoryEntry(sql: string, database: string): Promise<void> {
  switch (PARADIGM) {
    case 'WEB':
      webEngine.saveNewSqlHistoryEntry(sql, database);
      return;
    case 'DESKTOP':
      await desktopEngine.saveNewSqlHistoryEntry(sql, database);
      return;
    case 'CORE':
      // do nothing
      return;
    default:
      throw new Error();
  }
}

export async function getSqlHistoryEntries(keyword: string | null): Promise<SqlHistoryEntry[]> {
  switch (PARADIGM) {
    case 'WEB':
      return webEngine.getSqlHistoryEntries(keyword);
    case 'DESKTOP':
      return desktopEngine.getSqlHistoryEntries(keyword);
    case 'CORE':
      // do nothing
      return [];
    default:
      throw new Error();
  }

}
