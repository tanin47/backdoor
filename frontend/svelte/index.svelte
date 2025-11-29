<script lang="ts">
import TableMenuList from "./_table_menu_list.svelte";
import SheetPanel from "./_sheet_panel.svelte";
import {FetchError, post} from "./common/form";
import {onMount} from "svelte";
import EditorPanel from "./_editor_panel.svelte";
import {type Database, type Query, Sheet} from "./common/models";
import {generateName, IS_LOCAL_DEV, PARADIGM} from "./common/globals";
import ErrorModal from "./common/_error_modal.svelte"
import NewDataSourceModal from "./_new_data_source_modal.svelte"
import DeleteDataSourceModal from "./_delete_data_source_modal.svelte"
import AdditionalLoginModal from "./_additional_login_modal.svelte";
import {trackEvent} from "./common/tracker";

let sheetPanel: SheetPanel;

let selectedQuery: Query | null = null
let selectedDatabase: Database | null = null

let errorModal: ErrorModal;
let newDataSourceModal: NewDataSourceModal;
let deleteDataSourceModal: DeleteDataSourceModal;
let additionalLoginModal: AdditionalLoginModal;

let isLoading = false
let databases: Database[] = []
let queries: Query[] = []

async function load(): Promise<void> {
  try {
    const json = await post('/api/get-relations', {})

    databases = json.databases;
    trackEvent('databases-loaded', {count: databases.length, totalTableCount: databases.reduce((sum, b) => (sum + (b.tables?.length ?? 0)), 0)})
  } catch (e) {
    console.error(e)
    errorModal.open(
      (e as FetchError).messages,
      'We cannot access your database. Please confirm that the database URL is correct. You can use `psql URL` to verify that your database URL is correct.'
    );
  } finally {
    isLoading = false
  }
}

onMount(() => {
  trackEvent('landing-index')
  isLoading = true
  void load()
})


let leftNavWidth = 200;
let editorPanelHeight: number = 200;

type ResizeMode = 'leftnav' | 'editorpanel' | null
let resizeMode: ResizeMode = null

function startResize(mode: ResizeMode) {
  resizeMode = mode;
  document.body.classList.add('resizing');
}

function stopResize() {
  resizeMode = null;
  document.body.classList.remove('resizing');
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
  const foundIndex = queries.findIndex(v => v.database === query.database && v.name === query.name);

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

export async function runSql(database: string, sql: string): Promise<void> {
  try {
    const json = await post('/api/load-query', {
      database,
      name: selectedQuery?.name ?? generateName('q_', queries.map(v => v.name)),
      sql,
      filters: [],
      sorts: [],
      offset: 0
    })

    const sheet = json.sheet;

    sheetPanel.addOrUpdateSheet(new Sheet(json.sheet))

    if (sheet.type === 'query') {
      await addOrUpdateQuery({database: json.sheet.database, name: json.sheet.name, sql: json.sheet.sql})
    } else {
      selectedQuery = null
    }
    trackEvent('sql-run')
  } catch (e) {
    errorModal.open((e as FetchError).messages);
  }
}
</script>

<svelte:window on:mousemove={handleResize} on:mouseup={stopResize}/>

<ErrorModal bind:this={errorModal} />
<NewDataSourceModal bind:this={newDataSourceModal} onAdded={async () => {await load()}} />
<DeleteDataSourceModal bind:this={deleteDataSourceModal} onDeleted={async () => {await load()}} />
<AdditionalLoginModal bind:this={additionalLoginModal} onLoggedIn={async () => {await load()}} />

<div class="relative h-full w-full flex flex-row items-stretch">
  {#if isLoading}
    <div
      class="flex flex-col items-center justify-center gap-6 bg-primary-content w-full h-full"
    >
      <span class="loading loading-spinner loading-xl text-primary"></span>
      <div class="text-primary text-xl">Loading...</div>
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
      <div class="flex flex-col">
        <a
          href="https://github.com/tanin47/backdoor"
          target="_blank"
          rel="noopener noreferrer"
          class="flex flex-row items-center p-2 gap-1 bg-black  text-gray-400 text-sm"
        >
          {#if IS_LOCAL_DEV}
            <div class="text-accent">[dev]</div>
          {/if}
          <i class="ph ph-door-open"></i>
          <div class="whitespace-nowrap overflow-hidden text-ellipsis">Backdoor</div>
        </a>
        <div
          class="flex flex-row items-center p-2 gap-2 bg-base-300  text-gray-400 text-sm cursor-pointer"
          data-test-id="add-new-data-source-button"
          onclick={() => {newDataSourceModal.open()}}
        >
          <i class="ph ph-plus-circle text-sm"></i>
          <span class="overflow-hidden text-ellipsis font-mono text-xs whitespace-nowrap">Add Data Source</span>
        </div>
      </div>
      <div class="grow-1 overflow-y-auto">
        {#each databases as database, index (index)}
          <TableMenuList
            {database}
            {queries}
            {selectedQuery}
            onTableClicked={async (table) => {
              selectedDatabase = database
              await sheetPanel.openTable(table)
            }}
            onQueryClicked={async (query) => {
              selectedDatabase = database
              if (selectedQuery === query) {
                selectedQuery = null;
              } else {
                selectedQuery = query;
              }
              await sheetPanel.openQuery(query)
            }}
            onLoggingIn={() => { additionalLoginModal.open(database) }}
            onDeleting={() => {deleteDataSourceModal.open(database)}}
          />
        {/each}
      </div>
      {#if PARADIGM === 'WEB'}
        <a
          href="/logout"
          class="flex flex-row items-center p-2 gap-2 bg-base-300  text-gray-400 text-sm"
          data-test-id="logout-button"
        >
          <i class="ph ph-sign-out"></i>
          <span>Log out</span>
        </a>
      {/if}
    </div>
    <div class="flex flex-col items-stretch h-full w-full overflow-hidden">
      <SheetPanel
        bind:this={sheetPanel}
        onSheetSelected={(sheet) => {
          selectedDatabase = databases.find(s => s.name === sheet.database) ?? null
        }}
        onTableDropped={(databaseName, droppedTable) => {
          for (const database of databases) {
            if (database.name === databaseName) {
              database.tables = database.tables.filter(v => v !== droppedTable)
            }
          }
           databases = databases
        }}
        onTableRenamed={(databaseName, previousName, newName) => {
           for (const database of databases) {
            if (database.name === databaseName) {
              const foundIndex = database.tables.findIndex((t) => t === previousName)

              if (foundIndex > -1) {
                database.tables[foundIndex] = newName
              }
            }
          }

           databases = databases
        }}
        onQueryDropped={(sheet) => {
          queries = queries.filter(v => !(v.database === sheet.database && v.name === sheet.name))
          if (selectedQuery?.database === sheet.database && selectedQuery?.name === sheet.name) { selectedQuery = null }
        }}
        onQueryRenamed={(sheet, previousName, newName) => {
          const found = queries.find((v) => v.database === sheet.database  && v.name === previousName)

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
          selectedDatabaseName={selectedDatabase?.name ?? null}
          {selectedQuery}
          {databases}
          onRunSql={async (database, sql) => { await runSql(database, sql) }}
          onUnselectingQuery={() => { selectedQuery = null }}
        />
      </div>
    </div>
  {/if}
</div>

<style lang="scss">
</style>
