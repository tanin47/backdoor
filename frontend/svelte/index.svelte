<script lang="ts">
import TableMenuList from "./_table_menu_list.svelte";
import SheetPanel from "./_sheet_panel.svelte";
import {FetchError, post} from "./common/form";
import {onMount} from "svelte";
import EditorPanel from "./_editor_panel.svelte";
import {type Query, Sheet} from "./common/models";
import {generateName, IS_LOCAL_DEV, TARGET_HOSTNAME} from "./common/globals";
import ErrorModal from "./common/_error_modal.svelte"

let sheetPanel: SheetPanel;

let selectedQuery: Query | null = null

let errorModal: ErrorModal;

let isLoading = false
let tables: string[] = []
let queries: Query[] = []

async function loadQueryables(): Promise<void> {
  isLoading = true
  try {
    const json = await post('/api/get-relations', {})

    tables = json.tables;
  } catch (e) {
    errorModal.open(
      (e as FetchError).messages,
      'We cannot access your database. Please confirm that the database URL is correct. You can use `psql URL` to verify that your database URL is correct.'
    );
  } finally {
    isLoading = false
  }
}

onMount(() => {
  void loadQueryables()
})


let leftNavWidth = 200;
let editorPanelHeight: number = 200;

type ResizeMode = 'leftnav' | 'editorpanel' | null
let resizeMode: ResizeMode = null

function startResize(mode: ResizeMode) {
  resizeMode = mode;
  document.body.style.userSelect = 'none';
}

function stopResize() {
  resizeMode = null;
  document.body.style.userSelect = '';
}

function handleResize(event: MouseEvent) {
  switch (resizeMode) {
    case 'leftnav':
      leftNavWidth = Math.min(Math.max(event.clientX, 100), window.innerWidth - 100);
      break;
    case 'editorpanel':
      editorPanelHeight = Math.min(Math.max(window.innerHeight - event.clientY, 43), window.innerHeight - 100);
    case null:
      // do nothing
  }
}

async function addOrUpdateQuery(query: Query) {
  const foundIndex = queries.findIndex(v => v.name === query.name);

  if (foundIndex === -1) {
    queries.push(query)
    queries.sort((a, b) => a.name.localeCompare(b.name))
  } else {
    queries.splice(foundIndex, 1, query)
  }
  selectedQuery = query
  queries = queries
  await sheetPanel.openQuery(query)
}

export async function runSql(sql: string): Promise<void> {
  try {
    const json = await post('/api/load-query', {
      name: selectedQuery?.name ?? generateName('q_', queries.map(v => v.name)),
      sql,
      filters: [],
      sorts: [],
      offset: 0
    })

    const sheet = json.sheet;

    sheetPanel.addOrUpdateSheet(new Sheet(json.sheet))

    if (sheet.type === 'query') {
      await addOrUpdateQuery({name: json.sheet.name, sql: json.sheet.sql})
    } else {
      selectedQuery = null
    }
  } catch (e) {
    errorModal.open((e as FetchError).messages);
  }
}
</script>

<svelte:window on:mousemove={handleResize} on:mouseup={stopResize}/>

<ErrorModal bind:this={errorModal} />

<div class="relative h-full w-full flex flex-row items-stretch">
  {#if isLoading}
    <div
      class="flex flex-col items-center justify-center gap-6 bg-primary-content w-full h-full"
    >
      <span class="loading loading-spinner loading-xl text-primary"></span>
      <div class="text-primary text-xl">Loading {TARGET_HOSTNAME}...</div>
    </div>
  {:else}
    <div
      class="flex flex-col justify-between bg-primary-content border-e-2 border-gray-600 relative"
      style="width: {leftNavWidth}px; min-width: {leftNavWidth}px; max-width: {leftNavWidth}px;"
    >
      <span
        class="absolute top-0 bottom-0 right-[-3px] w-[6px] z-50 cursor-col-resize"
        onmousedown={() => {startResize('leftnav')}}
      >
        &nbsp;
      </span>
      <TableMenuList
        {tables}
        {queries}
        {selectedQuery}
        onTableClicked={async (table) => {await sheetPanel.openTable(table)}}
        onQueryClicked={async (query) => {
          if (selectedQuery === query) {
            selectedQuery = null;
          } else {
            selectedQuery = query;
          }
          await sheetPanel.openQuery(query)
        }}
      />
      <a
        href="https://github.com/tanin47/backdoor"
        target="_blank"
        rel="noopener noreferrer"
        class="flex flex-row items-center p-2 gap-1 bg-black  text-gray-400 text-[11px]"
      >
        {#if IS_LOCAL_DEV}
          <div class="text-accent">[dev]</div>
        {/if}
        <i class="ph ph-door-open"></i>
        <div class="whitespace-nowrap overflow-hidden text-ellipsis">Powered by Backdoor</div>
      </a>
    </div>
    <div class="flex flex-col items-stretch h-full w-full overflow-hidden">
      <SheetPanel
        bind:this={sheetPanel}
        onTableDropped={(droppedTable) => {
          tables = tables.filter(v => v !== droppedTable)
        }}
        onTableRenamed={(previousName, newName) => {
          const foundIndex = tables.findIndex((t) => t === previousName)

          if (foundIndex > -1) {
            tables[foundIndex] = newName
          }
        }}
        onQueryDropped={(dropped) => {
          queries = queries.filter(v => v.name !== dropped)
          if (selectedQuery?.name === dropped) { selectedQuery = null }
        }}
        onQueryRenamed={(previousName, newName) => {
          const found = queries.find((v) => v.name === previousName)

          if (found) {
            found.name = newName
            queries = queries
            selectedQuery = selectedQuery
          }
        }}
      />
      <div
        class="relative flex flex-col items-stretch border-t-2 border-gray-600 bg-accent-content"
        style="height: {editorPanelHeight}px;min-height: {editorPanelHeight}px;max-height: {editorPanelHeight}px;};"
      >
        <span
          class="absolute left-0 right-0 top-[-3px] h-[6px] z-50 cursor-row-resize"
          onmousedown={() => {startResize('editorpanel')}}
        >
          &nbsp;
        </span>
        <EditorPanel
          {selectedQuery}
          onRunSql={async (sql) => { await runSql(sql) }}
          onUnselectingQuery={() => { selectedQuery = null }}
        />
      </div>
    </div>
  {/if}
</div>

<style lang="scss">
</style>
