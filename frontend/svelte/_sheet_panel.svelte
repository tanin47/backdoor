<script lang="ts">
import {post} from "./common/form";
import {type Query, Sheet} from "./common/models";
import SheetView from './_sheet_view.svelte'
import {generateName} from "./common/globals";

export let onTableDropped: (table: string) => void
export let onTableRenamed: (previousName: string, newName: string) => void
export let onQueryDropped: (queryName: string) => void
export let onQueryRenamed: (previousName: string, newName: string) => void

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
  const found = sheets.find(s => s.name === sheet.name && s.type === sheet.type);

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
}

export async function openTable(table: string): Promise<void> {
  const found = sheets.find(s => s.name === table && s.type === 'table')

  if (found) {
    if (selectedSheet?.name === found.name) {
      triggerBlinking(found)
    }

    selectedSheet = found
    return
  }

  try {
    const json = await post('/api/load-table', {
      name: table,
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

export async function openQuery(query: Query): Promise<void> {
  const found = sheets.find(s => s.name === query.name)

  if (found) {
    if (selectedSheet?.name === found.name) {
      triggerBlinking(found)
    }

    selectedSheet = found
    return
  }

  try {
    const json = await post('/api/load-query', {
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

function deleteSheetByName(name: string) {
  const foundIndex = sheets.findIndex(s => s.name === name)

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
          onclick={() => { selectedSheet = sheet }}
          onmousedown={(ev) => {
            if (ev.button === 1) {
              void deleteSheetByName(sheet.name)
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
              void deleteSheetByName(sheet.name)
            }}
          ></i>
        </div>
      {/each}
    </div>
  {/if}
  <SheetView
    sheet={selectedSheet}
    onTableDropped={(table) => {
      deleteSheetByName(table)
      onTableDropped(table)
    }}
    onTableRenamed={(previousName, newName) => {
      sheets = sheets
      onTableRenamed(previousName, newName)
    }}
    onQueryDropped={(queryName) => {
      deleteSheetByName(queryName)
      onQueryDropped(queryName)
    }}
    onQueryRenamed={(previousName, newName) => {
      sheets = sheets
      onQueryRenamed(previousName, newName)
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
