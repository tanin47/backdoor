<script lang="ts">
import Button from './common/_button.svelte'
import CodeMirror, {type EditorFromTextArea} from "codemirror";

import 'codemirror/addon/display/placeholder'
import 'codemirror/lib/codemirror.css'
import 'codemirror/theme/pastel-on-dark.css'
import 'codemirror/addon/edit/matchbrackets'
import 'codemirror/addon/hint/show-hint'
import 'codemirror/addon/hint/show-hint.css'
import 'codemirror/addon/hint/sql-hint'
import 'codemirror/addon/hint/anyword-hint'
import 'codemirror/addon/comment/comment'
import type {Database, Query} from "./common/models";
import HistoryModal from "./_history_modal.svelte";

export let onRunSql: (database: string, sql: string) => Promise<void>
export let onUnselectingQuery: () => void
export let selectedDatabaseName: string | null = null
export let databases: Database[]
export let selectedQuery: Query | null

let editorTextarea: HTMLTextAreaElement
let historyModal: HistoryModal
let validDatabases: Database[] = []

$: {
  validDatabases = databases.filter(d => d.status === 'loaded')
}

$: if (validDatabases.length > 0 && !selectedDatabaseName) {
  selectedDatabaseName = validDatabases[0].nickname
}

let isLoading: boolean = false

let codeMirrorInstance: EditorFromTextArea | null = null

$: if (codeMirrorInstance) {
  if (selectedQuery) {
    codeMirrorInstance.setValue(selectedQuery.sql);
  }
}

$: if (editorTextarea && codeMirrorInstance === null) {
  codeMirrorInstance = CodeMirror.fromTextArea(
    editorTextarea,
    {
      lineNumbers: true,
      theme: 'pastel-on-dark',
      mode: 'text/x-sql',
      indentWithTabs: false,
      smartIndent: true,
      matchBrackets: true,
      dragDrop: false,
      tabSize: 2,
      extraKeys: {
        'Ctrl-Space': 'autocomplete',
        'Cmd-Space': 'autocomplete',
        'Ctrl-/': 'toggleComment',
        'Cmd-/': 'toggleComment',
        'Cmd-Enter': () => {
          submit()
        },
        'Ctrl-Enter': () => {
          submit()
        },
      },
    },
  );

  // @ts-expect-error untyped testing var
  window.CODE_MIRROR_FOR_TESTING = codeMirrorInstance;
}

async function submit(): Promise<void> {
  const databaseName = selectedQuery?.database ?? selectedDatabaseName
  if (!databaseName) {
    return
  }

  isLoading = true
  codeMirrorInstance!.setOption('readOnly', true)

  try {
    await onRunSql(databaseName, codeMirrorInstance!.getValue())
  } catch (e) {
    console.log(e)
  } finally {
    isLoading = false
    codeMirrorInstance!.setOption('readOnly', false)
  }
}
</script>

<HistoryModal
  bind:this={historyModal}
  onSetEditorPanel={(sql) => {
    if (codeMirrorInstance) {
      codeMirrorInstance.setValue(sql)
    }
    historyModal.close()
  }}
/>
{#if selectedQuery}
  <div class="flex gap-2 items-center text-xs p-2 bg-info-content border-b border-neutral"
       data-test-id="update-sql-label">
    <span>You are modifying the SQL of</span>
    <div
      class="px-1 font-mono cursor-pointer flex gap-1 items-center border rounded border-neutral-content"
    >
      <i class="ph ph-file-sql"></i> {selectedQuery.name}
    </div>
    <div class="underline cursor-pointer ms-2" data-test-id="make-new-sql-button"
         onclick={() => {onUnselectingQuery()}}>
      Click here to make a new SQL
    </div>
  </div>
{/if}
<div class="flex items-center gap-0 justify-between">
  <div class="flex gap-2 items-center text-xs p-1">
    <Button
      {isLoading}
      class="btn btn-xs btn-ghost flex items-center gap-2 px-2 group"
      onClick={async () => { await submit() }}
      dataTestId="run-sql-button"
      disabled={validDatabases.length === 0}
    >
      <i class="ph ph-play"></i>
      <span>
        {#if selectedQuery}
          Re-run SQL
        {:else}
          Run SQL
        {/if}
        <span class="text-xs text-gray-400 group-disabled:opacity-20 tracking-[1px] ps-1">⌘⏎</span>
      </span>
    </Button>
    {#if validDatabases.length > 0}
      <div class="pb-[1px] flex items-center gap-0">
        {#if selectedQuery}
          <span class="text-xs">[{selectedQuery.database}]</span>
        {:else}
          <select class="select-sm outline-0 hover:bg-base-200 rounded py-1 cursor-pointer"
                  bind:value={selectedDatabaseName}
                  data-test-id="run-sql-database-select">
            {#each validDatabases as database (database.nickname)}
              <option value={database.nickname}>{database.nickname}</option>
            {/each}
          </select>
        {/if}
      </div>
    {/if}
  </div>
  <div class="flex items-center gap-1">
    <Button
      class="btn btn-xs btn-ghost flex items-center gap-2 px-2"
      onClick={async () => { await historyModal.open() }}
      dataTestId="history-button"
    >
      <i class="ph ph-clock-counter-clockwise"></i>
      <span>History</span>
    </Button>
  </div>
</div>
<div class="grow border-t border-neutral relative">
  <div class="absolute top-0 left-0 right-0 bottom-0" class:invisible={validDatabases.length === 0}>
    <textarea bind:this={editorTextarea} placeholder="Compose a beautiful SQL..."></textarea>
  </div>
  {#if validDatabases.length === 0}
    <div class="absolute top-0 left-0 right-0 bottom-0 p-2 flex items-center justify-center">
      <div class="italic text-neutral">
        Connect at least one data source on the left panel to start writing a SQL.
      </div>
    </div>
  {/if}
</div>

<style>
</style>
