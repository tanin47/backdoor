<script lang="ts">

import {type Filter, type FilterOperator, Sheet} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import {trackEvent} from "./common/tracker";

export let sheet: Sheet
export let onDeleted: (rowIndex: number) => void

let modal: HTMLDialogElement;

let primaryKeys: Array<Filter> = []
let rowIndex_: number | null = null

let isLoading = false
let errors: string[] = []

export function open(rowValues: any[], rowIndex: number): void {
  isLoading = false
  errors = []

  rowIndex_ = rowIndex
  primaryKeys = sheet.columns
    .map((column, index) => {
      if (column.isPrimaryKey) {
        const value = rowValues[index];
        return {
          name: column.name,
          value: '' + value,
          operator: (value === null ? 'IS_NULL' : 'EQUAL') as FilterOperator
        }
      } else {
        return null
      }
    })
    .filter(s => s !== null)


  modal.showModal()
}

export function close(): void {
  if (isLoading) {
    return
  }
  modal.close()
}

async function submit(): Promise<void> {
  if (primaryKeys.length === 0 || rowIndex_ === null) {
    return
  }

  isLoading = true

  try {
    const json = await post('/api/delete-row', {
      database: sheet.database,
      table: sheet.name,
      primaryKeys
    })

    modal.close()
    onDeleted(rowIndex_)
    trackEvent('row-deleted')
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
    {:else if primaryKeys.length === 0}
      <div>Unable to delete the row</div>
      <div class="text-sm">
        Because the Table doesn't have a primary key column.
      </div>
      <div class="flex items-center justify-end gap-2">
        <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
      </div>
    {:else}
      <div>Are you sure you want to delete this row?</div>
      <div class="text-sm flex gap-2 items-center flex-wrap max-w-[600px]">
        <span>Primary key:</span>
        {#each primaryKeys as pair (pair.name)}
          <code>
            {pair.name} = {pair.operator === 'IS_NULL' ? 'NULL' : pair.value}
          </code>
        {/each}
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
