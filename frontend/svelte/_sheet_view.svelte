<script lang="ts">
import ErrorPanel from "./common/form/_error_panel.svelte";
import type {Sheet, Sort, SortDirection} from "./common/models";
import VirtualTable, {type Item} from 'svelte-virtual-table-by-tanin';
import EditModal from './_edit_modal.svelte';
import InsertModal from './_insert_modal.svelte';
import DeleteModal from './_delete_modal.svelte';
import DropTableModal from './_drop_table_modal.svelte';
import RenameTableModal from './_rename_table_modal.svelte';
import DeleteQueryModal from './_delete_query_modal.svelte';
import RenameQueryModal from './_rename_query_modal.svelte';
import ExportModal from './_export_modal.svelte';
import FilterModal from './_filter_modal.svelte';
import Button from './common/_button.svelte'

import 'codemirror/addon/display/placeholder'
import 'codemirror/lib/codemirror.css'
import 'codemirror/theme/pastel-on-dark.css'
import 'codemirror/addon/edit/matchbrackets'
import 'codemirror/addon/hint/show-hint'
import 'codemirror/addon/hint/show-hint.css'
import 'codemirror/addon/hint/sql-hint'
import 'codemirror/addon/hint/anyword-hint'
import 'codemirror/addon/comment/comment'
import {post} from "./common/form";
import {openFileDialog, PARADIGM} from "./common/globals";

export let sheet: Sheet | null
export let onTableDropped: (database: string, table: string) => void
export let onTableRenamed: (database: string, previousName: string, newName: string) => void
export let onQueryDropped: (sheet: Sheet) => void
export let onQueryRenamed: (sheet: Sheet, previousName: string, newName: string) => void

let MIN_NUMBER_COLUMN_WIDTH = 24; // 2 characters
let numberColumnWidth: number = 0;
let columnWidths: number[] = []

let virtualTableItems: Item[] = []

$: if (sheet) {
  resetColumnWidths()
  for (let index = 0;index < sheet.columns.length; index++) {
    const column = sheet.columns[index];

    if (!sheet.getColumnWidth(index)) {
      sheet.setColumnWidth(index, getColumnWidth(column.maxCharacterLength))
    }

    setColumnWidth(index, sheet.getColumnWidth(index))
  }

  setNumberColumnWidth(sheet.numberColumnWidth === 0 ? MIN_NUMBER_COLUMN_WIDTH : sheet.numberColumnWidth)

  virtualTableItems.length = sheet.rows.length
  for (let index = 0; index < sheet.rows.length; index++) {
    sheet.setRowHeight(index, getRowHeight(sheet.rows[index]));

    virtualTableItems[index] = {
      rowHeight: sheet.getRowHeight(index),
      values: [...sheet.rows[index]]
    }
  }
  virtualTableItems = virtualTableItems;

  virtualListUpdate++
}

function resetColumnWidths() {
  columnWidths = []
}

function setNumberColumnWidth(value: number) {
  numberColumnWidth = value;
}

function setColumnWidth(index: number, value: number) {
  columnWidths[index] = value;
}

function getCssStyle(element: HTMLElement, prop: string): string {
  return window.getComputedStyle(element, null).getPropertyValue(prop);
}

function getCanvasFont(el: HTMLElement): string {
  const fontWeight = getCssStyle(el, 'font-weight')!;
  const fontSize = getCssStyle(el, 'font-size')!;
  const fontFamily = getCssStyle(el, 'font-family')!;

  return `${fontWeight} ${fontSize} ${fontFamily}`;
}

let canvas =  document.createElement("canvas")

let dummyElem: HTMLElement | null = null
let font = ''
const context = canvas.getContext("2d");

$: if (dummyElem) {
  font = getCanvasFont(dummyElem);
  context!.font = font;
}

function getColumnWidth(textLength: number) {
  let text = ''
  for (let i = 0; i < textLength; i++) {
    text += 'a'
  }

  const metrics = context!.measureText(text);
  // +8 for padding, + 1 for the right border, +12 for filter icon, +4 for gap, +14 for editing icon, +4 for gap
  return metrics.width + 8 + 1 + 12 + 4 + 14 + 4;
}

function getCellHeight(text: string) {
  const lineHeight = parseFloat(getCssStyle(dummyElem!, 'line-height'));
  let height = lineHeight;

  for (let i = 0; i < text.length; i++) {
    if (text.charAt(i) === '\n') {
      height += lineHeight;
    }
  }
  return height + 8 + 1; // +8 for padding, + 1 for bottom-border
}

function getRowHeight(values: any[]) {
  let height = 0

  for (let i = 0; i < values.length; i++) {
    const cellHeight = typeof values[i] === 'string'
      ? getCellHeight(values[i])
      : getCellHeight('a');
    if (cellHeight > height) {
      height = cellHeight;
    }
  }

  return height;
}

let insertModal: InsertModal
let editModal: EditModal
let deleteModal: DeleteModal
let renameQueryModal: RenameQueryModal
let renameTableModal: RenameTableModal
let deleteQueryModal: DeleteQueryModal
let dropTableModal: DropTableModal
let filterModal: FilterModal
let exportModal: ExportModal
let virtualListUpdate: number = 0

let isLoading = false

async function loadMore(): Promise<void> {
  if (!sheet) { return }
  if (sheet.type !== 'query' && sheet.type !== 'table') { return } // not a select. Can't load more.
  if (sheet.rows.length === sheet.stats.numberOfRows) { return; } // no more items
  isLoading = true

  try {
    const json = await post(`/api/load-${sheet.type}`, {
      database: sheet.database,
      name: sheet.name,
      sql: sheet.sql,
      filters: sheet.filters,
      sorts: sheet.sorts,
      offset: sheet.rows.length
    })

    sheet.load(json.sheet, true)
    sheet = sheet
  } catch (e) {

  } finally {
    isLoading = false
  }
}

async function loadDataWithNewSorts(newSorts: Sort[]): Promise<void> {
  if (!sheet) { return }

  isLoading = true

  try {
    const json = await post(`/api/load-${sheet.type}`, {
      database: sheet.database,
      name: sheet.name,
      sql: sheet.sql,
      filters: sheet.filters,
      sorts: newSorts,
      offset: 0,
    })

    sheet.load(json.sheet, false)
    sheet = sheet
  } catch (e) {

  } finally {
    isLoading = false
  }
}

async function exportCsv(): Promise<void> {
  if (!sheet) { return }

  try {
    const resp = await openFileDialog(true)

    if (!resp.filePath) {
      return
    }

    exportModal.open(resp.filePath);
  } catch (e) {
  }
}

async function addSort(column: string, direction: SortDirection) {
  if (!sheet) { return }
  const newSorts = [...sheet.sorts]
  const foundIndex = newSorts.findIndex(s => s.name === column)

  if (foundIndex > -1) {
    newSorts.splice(foundIndex, 1)
  }

  newSorts.push({name: column, direction: direction})

  await loadDataWithNewSorts(newSorts)
}

async function removeSort(column: string) {
  if (!sheet) { return }
  const newSorts = [...sheet.sorts]
  const foundIndex = newSorts.findIndex(s => s.name === column)

  if (foundIndex > -1) {
    newSorts.splice(foundIndex, 1)
  }
  await loadDataWithNewSorts(newSorts)
}

let isResizingColumnIndex: number | 'number_column' | null = null

function startResizeNumberColumn() {
  isResizingColumnIndex = 'number_column';
  document.body.style.userSelect = 'none';
}

function startResize(columnIndex: number) {
  isResizingColumnIndex = columnIndex;
  document.body.style.userSelect = 'none';
}

function stopResize() {
  isResizingColumnIndex = null;
  document.body.style.userSelect = '';
}

function handleResize(event: MouseEvent) {
  if (!sheet) { return }
  if (isResizingColumnIndex === 'number_column') {
    const newWidth = Math.max(MIN_NUMBER_COLUMN_WIDTH, numberColumnWidth + event.movementX);
    sheet.setNumberColumnWidth(newWidth);
    numberColumnWidth = newWidth;
  } else if (isResizingColumnIndex !== null) {
    const newWidth = Math.max(40, sheet.getColumnWidth(isResizingColumnIndex) + event.movementX);
    sheet.setColumnWidth(isResizingColumnIndex, newWidth);
    columnWidths[isResizingColumnIndex] = newWidth;
  }
}
</script>

<svelte:window
  on:mousemove={handleResize}
  on:mouseup={stopResize}
/>

<div class="font-mono text-xs hidden" bind:this={dummyElem}></div>
{#if sheet}
  {#if sheet.type === 'execute'}
    <div class="flex flex-row gap-2 p-2 items-baseline bg-base-300 border-b border-neutral">
      <span class="text-xs underline">SQL:</span>
      <div class="font-mono text-xs whitespace-pre">{sheet.sql}</div>
    </div>
  {:else}
    <div class="flex flex-row gap-4 items-center justify-between text-xs px-2 py-1 bg-base-300 border-b border-neutral whitespace-nowrap">
      <div class="flex gap-2 items-baseline">
        <div data-test-id="sheet-stats">
          Count: {sheet.stats.numberOfRows}
          {#if sheet.rows.length === sheet.stats.numberOfRows}
            (Show all)
          {:else}
            (Show {sheet.rows.length} rows)
          {/if}
        </div>
        {#if sheet.type === 'table'}
          <Button class="btn btn-xs btn-ghost text-success p-0" onClick={async () => {insertModal.open()}} dataTestId="insert-row-button">Insert</Button>
        {/if}
        <Button
          class="btn btn-xs btn-ghost text-info p-0"
          onClick={async () => {
            if (sheet) {
              void loadDataWithNewSorts(sheet.sorts)
            }
          }}
          dataTestId="refresh-button"
        >Refresh</Button>
        {#if PARADIGM === 'DESKTOP'}
          <Button
            class="btn btn-xs btn-ghost text-primary p-0"
            onClick={async () => {
              if (sheet) {
                void exportCsv()
              }
            }}
            dataTestId="export-button"
          >Export</Button>
        {/if}
      </div>
      <div class="flex gap-2 items-baseline">
        <div>[{sheet.database}]</div>
        {#if sheet.type === 'table'}
          <Button class="btn btn-xs btn-ghost text-warning p-0" onClick={async () => {renameTableModal.open()}} dataTestId="rename-table-button">Rename</Button>
          <Button class="btn btn-xs btn-ghost text-error p-0" onClick={async () => {dropTableModal.open()}} dataTestId="drop-table-button">Drop</Button>
        {:else if sheet.type === 'query'}
          <Button class="btn btn-xs btn-ghost text-info p-0" onClick={async () => {renameQueryModal.open()}} dataTestId="rename-query-button">Rename</Button>
          <Button class="btn btn-xs btn-ghost text-warning p-0" onClick={async () => {deleteQueryModal.open()}} dataTestId="delete-query-button">Delete</Button>
        {/if}
      </div>
    </div>
  {/if}
{/if}
<div
  class="flex flex-col grow w-full items-stretch overflow-hidden relative"
  data-test-id="sheet-view-content"
>
  {#if sheet === null}
    <div class="p-6 text-neutral italic h-full w-full flex justify-center items-center">
      Select a table on the left panel or write your first SQL in the bottom panel.
    </div>
  {:else if sheet.errors.length > 0}
    <ErrorPanel errors={sheet.errors} />
  {:else}
    {#if isLoading}
      <div class="absolute top-0 left-0 right-0 bottom-0 p-2 flex items-center justify-center z-50">
        <div class="absolute top-0 left-0 right-0 bottom-0 bg-black opacity-70"></div>
        <progress class="progress w-1/2 z-50"></progress>
      </div>
    {/if}
    {@const primaryKeyColumnIndex = sheet.getPrimaryKeyColumnIndex()}
    {@const totalWidth = columnWidths.reduce((sum, width) => sum + width, numberColumnWidth)}
    {#key virtualListUpdate}
      <VirtualTable
        let:item
        let:index={rowIndex}
        items={virtualTableItems}
        initialScrollLeft={sheet.scrollLeft}
        initialScrollTop={sheet.scrollTop}
        onBottomReached={() => {
          void loadMore()
        }}
        onScrolled={(scrollLeft, scrollTop) => {
          if (!sheet) return
          sheet.setScrollLeft(scrollLeft)
          sheet.setScrollTop(scrollTop)
        }}
      >
        {@const isDeleted = sheet.deletedRowIndices.has(rowIndex)}
        <div
          slot="header"
          class="flex items-stretch font-mono text-xs bg-base-300"
          style="width: {totalWidth}px;min-width: {totalWidth}px;max-width: {totalWidth}px;"
        >
          <div
            class="p-1 box-border border-e border-b border-neutral overflow-hidden sticky left-0  bg-base-300 z-20"
            style="width: {numberColumnWidth}px; min-width: {numberColumnWidth}px; max-width: {numberColumnWidth}px;"
            data-test-id="sheet-view-colum-header-number"
          >&nbsp;</div>
          <div
            class="absolute top-0 bottom-0 w-[6px] cursor-col-resize z-50"
            style="left: {numberColumnWidth-3}px"
            onmousedown={() => {startResizeNumberColumn()}}
          ></div>
          {#each sheet.columns as column, colIndex (column.name)}
            {@const cumulativeWidth = columnWidths.slice(0, colIndex + 1).reduce((sum, width) => sum + width, numberColumnWidth)}
            {@const sort = sheet.sorts.find(s => s.name === column.name)}
            <!-- the resizing bar has to be outside in order to be on top of a border -->
            <div
              class="absolute top-0 bottom-0 w-[6px] cursor-col-resize z-50"
              style="left: {cumulativeWidth-3}px"
              onmousedown={() => {startResize(colIndex)}}
            ></div>
            <div
              class="p-1 box-border border-e border-b border-neutral flex items-center justify-between z-0"
              style="width: {columnWidths[colIndex]}px; min-width: {columnWidths[colIndex]}px; max-width: {columnWidths[colIndex]}px;"
              data-test-id="sheet-view-column-header"
              data-test-value={sheet.columns[colIndex].name}
            >
              <div
                class="grow overflow-hidden text-ellipsis"
                class:underline={column.isPrimaryKey}
              >{column.name}</div>
              {#if sheet.type === "table" || sheet.type === "query"}
                <div class="flex items-center gap-1">
                  <i
                    class="{sheet.filters.findIndex(s => s.name === column.name) > -1 ? 'text-success' : ''} ph ph-funnel cursor-pointer"
                    onclick={() => {void filterModal.open(column)}}
                    data-test-id="filter-button"
                  ></i>
                  {#if sort && sort.direction === 'asc'}
                    <i class="ph ph-caret-up cursor-pointer text-success" data-test-id="sort-button" data-test-value="asc" onclick={() => {void addSort(column.name, 'desc')}}></i>
                  {:else if sort && sort.direction === 'desc'}
                    <i class="ph ph-caret-down cursor-pointer text-success" data-test-id="sort-button" data-test-value="desc" onclick={() => {void removeSort(column.name)}}></i>
                  {:else}
                    <i class="ph ph-caret-up-down cursor-pointer" data-test-id="sort-button" data-test-value="none" onclick={() => {void addSort(column.name, 'asc')}}></i>
                  {/if}
                </div>
              {/if}
            </div>
          {/each}
        </div>
        <div
          class="group inline-flex items-stretch font-mono text-xs box-border border-b border-neutral text-gray-300 hover:text-gray-50 hover:bg-info-content"
          style="height: {item.rowHeight}px;min-height: {item.rowHeight}px;max-height: {item.rowHeight}px;"
          data-test-id="sheet-view-row"
          data-test-value={primaryKeyColumnIndex === null ? '' : item.values[primaryKeyColumnIndex]}
        >
          <div
            class="p-1 box-border border-e border-neutral flex items-baseline justify-end sticky left-0 bg-base-300 z-10"
            style="width: {numberColumnWidth}px; min-width: {numberColumnWidth}px; max-width: {numberColumnWidth}px;"
            data-test-id="sheet-view-number-col"
          >
            {#if !isDeleted}
              <div class="overflow-hidden text-ellipsis {sheet.type === 'table' ? 'group-hover:hidden' : ''} text-right w-full">
                {rowIndex + 1}
              </div>
              {#if sheet.type === 'table'}
                <i
                  class="ph ph-trash cursor-pointer pt-[2px] hidden group-hover:inline-block"
                  onclick={() => deleteModal.open(item.values, rowIndex)}
                  data-test-id="delete-row-button"
                ></i>
              {/if}
            {:else}
              <i
                class="ph ph-x-circle pt-[2px] text-error"
               data-test-id="already-deleted-label"
              ></i>
            {/if}
          </div>
          {#each item.values as value, colIndex (colIndex)}
            <div
              class="p-1 box-border border-e border-neutral flex items-baseline gap-1 whitespace-pre {isDeleted ? 'text-error line-through' : ''}"
              style="width: {columnWidths[colIndex]}px; min-width: {columnWidths[colIndex]}px; max-width: {columnWidths[colIndex]}px;"
              data-test-id="sheet-column-value"
              data-test-value={sheet.columns[colIndex].name}
            >
              {#if sheet.type === 'table'}
                <i
                  data-test-id="edit-field-button"
                  class="ph ph-pencil-simple {isDeleted ? '' : 'cursor-pointer hover:text-white'} pt-[2px]"
                  onclick={() => {
                      if (!isDeleted && sheet) {
                        editModal.open(value, sheet.columns[colIndex], item.values, rowIndex)
                      }
                    }}
                ></i>
              {/if}
              {#if value === null}
                <span class="italic opacity-50 overflow-hidden text-ellipsis">null</span>
              {:else}
                <span class="overflow-hidden text-ellipsis">{value}</span>
              {/if}
            </div>
          {/each}
        </div>
      </VirtualTable>
    {/key}
  {/if}
</div>

{#if sheet}
  <InsertModal
    bind:this={insertModal}
    {sheet}
  ></InsertModal>
  <EditModal
    bind:this={editModal}
    {sheet}
    onUpdated={(column, newValue, newCharacterlength, rowIndex) => {
      if (!sheet) { return }
      const columnIndex = sheet.columns.findIndex((c) => c.name === column.name)

      if (rowIndex >= 0) {
        sheet.rows[rowIndex][columnIndex] = newValue
        sheet.columns[columnIndex].maxCharacterLength = Math.max(sheet.columns[columnIndex].maxCharacterLength, newCharacterlength)
        sheet.setColumnWidth(columnIndex, Math.max(sheet.getColumnWidth(columnIndex), getColumnWidth(newCharacterlength)))
        virtualListUpdate++
      }
    }}
  />
  <DeleteModal
    bind:this={deleteModal}
    {sheet}
    onDeleted={(rowIndex) => {
      if (!sheet) { return }
      sheet.deletedRowIndices.add(rowIndex);
      virtualListUpdate++
    }}
  />
  <DropTableModal
    bind:this={dropTableModal}
    {sheet}
    onDropped={() => {
      if (!sheet) { return }
      onTableDropped(sheet.database, sheet.name)
    }}
  />
  <RenameTableModal
    bind:this={renameTableModal}
    {sheet}
    onRenamed={(newName) => {
      if (!sheet) { return }
      onTableRenamed(sheet.database, sheet.name, newName)
      sheet.name = newName
    }}
  />
  <DeleteQueryModal
    bind:this={deleteQueryModal}
    {sheet}
    onDropped={() => {
      if (!sheet) { return }
      onQueryDropped(sheet)
    }}
  />
  <RenameQueryModal
    bind:this={renameQueryModal}
    {sheet}
    onRenamed={(newName) => {
      if (!sheet) { return }
      onQueryRenamed(sheet, sheet.name, newName)
      sheet.name = newName
    }}
  />
  <FilterModal
    bind:this={filterModal}
    {sheet}
    onSheetUpdated={(newSheet) => {
      if (!sheet) { return }
      sheet.load(newSheet, false)
      sheet = sheet
      virtualListUpdate++
    }}
  />
  <ExportModal
    {sheet}
    bind:this={exportModal}
  />
{/if}


<style lang="scss">
</style>
