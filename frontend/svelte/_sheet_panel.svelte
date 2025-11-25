<script lang="ts">
import {post} from "./common/form";
import {type Query, Sheet, type Table} from "./common/models";
import SheetView from './_sheet_view.svelte'
import {generateName} from "./common/globals";
import {trackEvent} from "./common/tracker";

export let onSheetSelected: (sheet: Sheet) => void
export let onTableDropped: (database: string, table: string) => void
export let onTableRenamed: (database: string, previousName: string, newName: string) => void
export let onQueryDropped: (sheet: Sheet) => void
export let onQueryRenamed: (sheet: Sheet, previousName: string, newName: string) => void

let sheets: Sheet[] = []
let selectedSheet: Sheet | null = null
let shouldBlinkSelectedSheet: Sheet | null = null

let blinkTimeout: NodeJS.Timeout | null = null

function triggerBlinking(sheet: Sheet): void {
  shouldBlinkSelectedSheet = sheet

  if (blinkTimeout) {
    clearTimeout(blinkTimeout)
    blinkTimeout = null
  }

  blinkTimeout = setTimeout(() => {
    shouldBlinkSelectedSheet = null
  }, 600)
}

export function addOrUpdateSheet(sheet: Sheet) {
  const found = sheets.find(s => s.database === sheet.database && s.name === sheet.name && s.type === sheet.type);

  if (found) {
    found.load(sheet, false)
  } else {
    if (sheet.type === 'execute' && sheet.name === '') {
      sheet.name = generateName('e_', sheets.map(s => s.name))
    }
    sheets.push(sheet)
  }
  selectedSheet = sheet
  sheets = sheets
  trackEvent('sheet-loaded', {shownRowCount: sheet.rows.length, totalRowCount: sheet.stats.numberOfRows})
}

export async function openTable(table: Table): Promise<void> {
  const found = sheets.find(s => s.database === table.database && s.name === table.name && s.type === 'table')

  if (found) {
    if (selectedSheet === found) {
      triggerBlinking(found)
    }

    selectedSheet = found
    return
  }

  try {
    const json = await post('/api/load-table', {
      database: table.database,
      name: table.name,
      sorts: [],
      filters: [],
      offset: 0
    })

    const newSheet = new Sheet(json.sheet)
    sheets.push(newSheet)
    sheets = sheets

    trackEvent('table-loaded', {shownRowCount: newSheet.rows.length, totalRowCount: newSheet.stats.numberOfRows})

    selectedSheet = newSheet
  } catch (e) {
    // errors = (e as FetchError).messages
    console.log(e)
  } finally {
    // isLoading = false
  }
}

export async function openQuery(query: Query): Promise<void> {
  const found = sheets.find(s => s.database === query.database && s.name === query.name)

  if (found) {
    if (selectedSheet?.name === found.name) {
      triggerBlinking(found)
    }

    selectedSheet = found
    return
  }

  try {
    const json = await post('/api/load-query', {
      database: query.database,
      name: query.name,
      sql: query.sql,
      sorts: [],
      filters: [],
      offset: 0
    })

    const newSheet = new Sheet(json.sheet)
    sheets.push(newSheet)
    sheets = sheets

    selectedSheet = newSheet
  } catch (e) {
    // errors = (e as FetchError).messages
    console.log(e)
  } finally {
    // isLoading = false
  }
}

function deleteSheetByName(database: string, name: string) {
  const foundIndex = sheets.findIndex(s => s.database === database && s.name === name)

  if (foundIndex > -1) {
    sheets.splice(foundIndex, 1)
    sheets = sheets

    if (selectedSheet?.name === name) {
      if (sheets.length === 0) {
        selectedSheet = null
      } else if (foundIndex >= sheets.length) {
        selectedSheet = sheets[sheets.length - 1]
      } else {
        selectedSheet = sheets[foundIndex];
      }
    }
  }
}

</script>

<div class="flex flex-col gap-0 items-stretch grow w-full overflow-hidden bg-secondary-content">
  {#if sheets.length > 0}
    <div
      class="flex gap-1 p-1 flex-wrap items-center bg-primary-content border-b border-neutral text-xs"
      data-test-id="sheet-tabs"
    >
      {#each sheets as sheet, index (index)}
        <div
          class="px-1 font-mono cursor-pointer flex gap-1 items-center border rounded {sheet === selectedSheet ? 'border-neutral-content' : 'border-neutral text-gray-500'}"
          data-test-id="sheet-tab"
          data-test-value={sheet.name}
          class:blink={shouldBlinkSelectedSheet === sheet && sheet === selectedSheet}
          onclick={() => {
            selectedSheet = sheet
            onSheetSelected(sheet)
          }}
          onmousedown={(ev) => {
            if (ev.button === 1) {
              void deleteSheetByName(sheet.database, sheet.name)
            }
          }}
        >
          {#if sheet.type === 'table'}
            <i class="ph ph-table"></i>
          {:else if sheet.type === 'query'}
            <i class="ph ph-file-sql"></i>
          {:else if sheet.type === 'execute'}
            <i class="ph ph-play"></i>
          {/if}
          <span>{sheet.name}</span>
          <i
            class="ph ph-x {sheet === selectedSheet ? 'text-neutral-content' : ''} hover:text-white"
            onclick={(ev) => {
              ev.stopPropagation()
              void deleteSheetByName(sheet.database, sheet.name)
            }}
          ></i>
        </div>
      {/each}
    </div>
  {/if}
  <SheetView
    sheet={selectedSheet}
    onTableDropped={(database, table) => {
      deleteSheetByName(database,table)
      onTableDropped(database, table)
    }}
    onTableRenamed={(database, previousName, newName) => {
      sheets = sheets
      onTableRenamed(database, previousName, newName)
    }}
    onQueryDropped={(sheet) => {
      deleteSheetByName(sheet.database, sheet.name)
      onQueryDropped(sheet)
    }}
    onQueryRenamed={(sheet, previousName, newName) => {
      onQueryRenamed(sheet, previousName, newName)
      sheets = sheets
    }}
  />
</div>

<style lang="scss">
@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}

.blink {
  animation: blink 0.3s ease-in-out infinite;
}
</style>
