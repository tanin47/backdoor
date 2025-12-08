export type DatabaseType = 'postgres' | 'clickhouse' | 'sqlite'

export interface DatabaseAdHocInfo {
  url: string;
  username: string;
  password: string;
}

export interface Database {
  nickname: string;
  isAdHoc: boolean;
  tables: string[];
  adHocInfo: DatabaseAdHocInfo | null;
  status: 'loaded' | 'unloaded'
}

export interface Table {
  database: string;
  name: string;
}

export interface Column {
  name: string;
  type: string;
  rawType: string;
  maxCharacterLength: number;
  isPrimaryKey: boolean;
  isNullable: boolean;
}

export interface Stats {
  numberOfRows: number;
}

export interface Query {
  database: string;
  name: string;
  sql: string;
}

export interface SqlHistoryEntry {
  sql: string;
  database: string;
  executedAt: number;
}

export type SheetType = 'table' | 'query' | 'execute';

export type SortDirection = 'asc' | 'desc';

export interface Sort {
  name: string;
  direction: SortDirection;
}

export interface Filter {
  name: string;
  value: string | null;
}

interface BaseSheet {
  database: string;
  name: string;
  sql: string;
  type: SheetType;
  columns: Column[];
  filters: Filter[];
  sorts: Sort[];
  stats: Stats;
  rows: any[][];
}

export class Sheet implements BaseSheet {
  database: string = '';
  name: string = '';
  sql: string = '';
  type: SheetType = 'query';
  columns: Column[] = [];
  stats: Stats = {numberOfRows: 0};
  rows: any[][] = [];
  sorts: Sort[] = [];
  filters: Filter[] = [];
  errors: string[] = [];
  #columnWidths: number[] = [];
  #rowHeights: number[] = [];
  numberColumnWidth: number = 0;
  deletedRowIndices: Set<number> = new Set();
  scrollLeft: number = 0;
  scrollTop: number = 0;

  constructor(args: BaseSheet) {
    this.load(args, false)
  }

  getPrimaryKeyColumnIndex(): number | null {
    const index = this.columns.findIndex(c => c.isPrimaryKey)
    return index === -1 ? null : index;
  }

  getColumnWidth(index: number): number {
    if (this.#columnWidths.length <= index) {
      this.#columnWidths.length = index;
    }
    return this.#columnWidths[index]
  }

  setColumnWidth(index: number, value: number): void {
    this.#columnWidths[index] = value
  }

  getRowHeight(index: number): number {
    if (this.#rowHeights.length <= index) {
      this.#rowHeights.length = index;
    }
    return this.#rowHeights[index]
  }

  setNumberColumnWidth(value: number): void {
    this.numberColumnWidth = value
  }

  setRowHeight(index: number, value: number): void {
    this.#rowHeights[index] = value
  }

  setScrollLeft(value: number): void {
    this.scrollLeft = value
  }

  setScrollTop(value: number): void {
    this.scrollTop = value
  }

  load(newSheet: BaseSheet, appendRows: boolean): void {
    this.database = newSheet.database;
    this.name = newSheet.name;
    this.sql = newSheet.sql;
    this.type = newSheet.type;
    this.columns = newSheet.columns;
    this.filters = newSheet.filters;
    this.sorts = newSheet.sorts;
    this.stats = newSheet.stats;
    if (appendRows) {
      this.rows = this.rows.concat(newSheet.rows);
    } else {
      this.rows = newSheet.rows;
    }
    this.deletedRowIndices.clear();
  }
}
