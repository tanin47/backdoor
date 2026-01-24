<script lang="ts">

import {Sheet} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import {trackEvent} from "./common/tracker";

export let sheet: Sheet

let modal: HTMLDialogElement;

let isLoading = false
let errors: string[] = []
let exportedFilePath_: string = ''

export function open(exportedFilePath: string): void {
  isLoading = true
  errors = []
  exportedFilePath_ = exportedFilePath
  void submit()
  modal.showModal()
}

export function close(): void {
  if (isLoading) {
    return
  }
  modal.close()
}

async function submit(): Promise<void> {
  isLoading = true

  try {
    const _json = await post('/api/export-file', {
      path: exportedFilePath_,
      database: sheet.database,
      sql: sheet.sql,
      filters: sheet.filters,
      sorts: sheet.sorts,
    })

    trackEvent('file-exported')
  } catch (e) {
    errors = (e as FetchError).messages
  } finally {
    isLoading = false
  }
}

</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !min-w-[480px] !w-auto !max-w-none flex flex-col gap-4">
    {#if isLoading}
      <div>Exporting to a file</div>
    {:else if errors && errors.length > 0}
      <div class="text-error">Exporting failed!</div>
    {:else}
      <div class="text-success">Exporting succeeding.</div>
    {/if}
    <div class="text-sm">
      Output file: <code>{exportedFilePath_}</code>
    </div>
    <ErrorPanel {errors}/>
    <div class="flex items-center justify-end gap-2">
      <Button
        class="btn btn-neutral"
        isLoading={isLoading}
        onClick={async () => { void close() }}
      >
        {#if isLoading}
          Exporting...
        {:else}
          Close
        {/if}
      </Button>
    </div>
  </div>
  {#if !isLoading}
    <div class="modal-backdrop" onclick={close}>
    </div>
  {/if}
</dialog>
