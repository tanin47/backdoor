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
import type {Query} from "./common/models";

export let onRunSql: (sql: string) => Promise<void>
export let onUnselectingQuery: () => void
export let selectedQuery: Query | null

let editorTextarea: HTMLTextAreaElement

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
        'Cmd-/': 'toggleComment'
      }
    },
  );
}

async function submit(): Promise<void> {
  isLoading = true
  codeMirrorInstance!.setOption('readOnly', true)

  try {
    await onRunSql(codeMirrorInstance!.getValue())
  } catch (e) {
    console.log(e)
  } finally {
    isLoading = false
    codeMirrorInstance!.setOption('readOnly', false)
  }
}
</script>

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
<div class="flex items-center gap-2 justify-between">
  <div class="flex gap-2 items-center text-xs p-1">
    <Button
      {isLoading}
      class="btn btn-xs btn-ghost flex items-center gap-2 px-2"
      onClick={async () => { await submit() }}
      dataTestId="run-sql-button"
    >
      <i class="ph ph-play"></i>
      <span>
        {#if selectedQuery}
          Re-run SQL
        {:else}
          Run SQL
        {/if}
      </span>
    </Button>
  </div>
</div>
<div class="grow border-t border-neutral relative">
  <div class="absolute top-0 left-0 right-0 bottom-0">
    <textarea bind:this={editorTextarea} placeholder="Compose a beautiful and useful SQL..."></textarea>
  </div>
</div>

<style>

</style>
