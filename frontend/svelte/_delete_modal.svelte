<script lang="ts">

import {Sheet} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"

export let sheet: Sheet
export let onDeleted: (primaryKeyValue: string) => void

let modal: HTMLDialogElement;

let primaryKeyValue: any
let primaryKeyColumnIndex: number | null = null;

let isLoading = false
let errors: string[] = []

export function open(values: any[]): void {
  isLoading = false
  errors = []

  primaryKeyColumnIndex = sheet.getPrimaryKeyColumnIndex()
  primaryKeyValue = primaryKeyColumnIndex !== null ? values[primaryKeyColumnIndex] : null

  modal.showModal()
}

export function close(): void {
  if (isLoading) {
    return
  }
  modal.close()
}

async function submit(): Promise<void> {
  if (primaryKeyColumnIndex === null) {
    return
  }

  isLoading = true

  try {
    const json = await post('/api/delete-row', {
      table: sheet.name,
      primaryKeyColumn: sheet.columns[primaryKeyColumnIndex].name,
      primaryKeyValue: '' + primaryKeyValue,
    })

    modal.close()
    onDeleted(primaryKeyValue)
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  }
}

</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !min-w-[480px] !w-auto !max-w-none flex flex-col gap-4">
    {#if sheet.type !== 'table'}
      <div>Unable to delete the row</div>
      <div class="text-sm">
        Because this is not a Table.
      </div>
      <div class="flex items-center justify-end gap-2">
        <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
      </div>
    {:else if primaryKeyValue === null || primaryKeyColumnIndex === null}
      <div>Unable to delete the row</div>
      <div class="text-sm">
        Because the Table doesn't have a primary key column.
      </div>
      <div class="flex items-center justify-end gap-2">
        <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
      </div>
    {:else}
      <div>Are you sure you want to delete this row?</div>
      <div class="text-sm">
        Primary key:
        <code>
          {sheet.columns[primaryKeyColumnIndex].name} = {primaryKeyValue}
        </code>
      </div>
      <ErrorPanel {errors}/>
      <div class="flex items-center justify-end gap-2">
        <Button {isLoading} class="btn btn-error" onClick={async () => {submit()}} dataTestId="submit-button">
          Delete
        </Button>
        <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
      </div>
    {/if}
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
