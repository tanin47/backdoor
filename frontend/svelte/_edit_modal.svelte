<script lang="ts">

import {type Column, Sheet} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"

export let sheet: Sheet
export let onUpdated: (column: Column, newValue: any, newCharacterLength: number, rowIndex: number) => void

let modal: HTMLDialogElement;
let textarea: HTMLTextAreaElement | null

let primaryKeys: Array<{ name: string, value: any }> = []
let rowIndex_: number | null = null
let currentValue: any
let currentColumn: Column | null
let setToNull: boolean = false

let isLoading = false
let errors: string[] = []

$: if (!setToNull) {
  try {
    void focusOnTextarea()
  } catch (e) {
  }
}

export function open(value: any, column: Column, rowValues: any[], rowIndex: number): void {
  isLoading = false
  errors = []

  if (value === null) {
    setToNull = true
    currentValue = ''
  } else {
    setToNull = false
    currentValue = '' + value
  }
  currentColumn = column

  primaryKeys = sheet.columns
    .map((column, index) => {
      if (column.isPrimaryKey) {
        const value = rowValues[index];
        return {name: column.name, value: value === null ? null : ('' + value)}
      } else {
        return null
      }
    })
    .filter(s => s !== null)
  rowIndex_ = rowIndex

  modal.showModal()

  void focusOnTextarea()
}

async function focusOnTextarea(): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, 100))
  textarea?.blur()
  await new Promise(resolve => setTimeout(resolve, 10))
  textarea?.focus()
  textarea?.select()
}

export function close(): void {
  if (isLoading) {
    return
  }
  modal.close()
}

async function submit() {
  if (primaryKeys.length === 0 || rowIndex_ === null) {
    return
  }

  isLoading = true

  try {
    const json = await post('/api/edit-field', {
      database: sheet.database,
      table: sheet.name,
      primaryKeys,
      column: currentColumn!.name,
      value: currentValue,
      setToNull,
    })

    modal.close()
    onUpdated(currentColumn!, json.newValue, json.newCharacterLength, rowIndex_)
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  }
}

</script>

<dialog
  bind:this={modal}
  class="modal2"
  data-test-id="edit-modal"
>
  <div class="modal-box !min-w-[480px] !w-auto !max-w-none flex flex-col gap-4">
    {#if sheet.type !== 'table'}
      <div>Unable to edit the field</div>
      <div class="text-sm">
        Because this is not a Table.
      </div>
      <div class="flex items-center justify-end gap-2">
        <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
      </div>
    {:else if primaryKeys.length === 0}
      <div>Unable to edit the field</div>
      <div class="text-sm">
        Because the table doesn't have a primary key column.
      </div>
      <div class="flex items-center justify-end gap-2">
        <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
      </div>
    {:else}
      {#if currentColumn}
        <span class="text-lg">{currentColumn.name}</span>
      {/if}
      {#if currentColumn && currentColumn.isNullable}
        <div class="flex gap-2 items-center text-sm">
          <label class="flex gap-2 items-center cursor-pointer">
            <input
              data-test-id="set-to-null"
              type="checkbox"
              class="checkbox checkbox-xs"
              bind:checked={setToNull}
              disabled={isLoading}
            />
            <span>Set to <code>null</code></span>
          </label>
        </div>
      {/if}
      {#if setToNull}
        <span class="text-xs text-accent">The value below will not be used because the value will be set to null.</span>
      {/if}
      <textarea
        data-test-id="new-value"
        bind:this={textarea}
        class="textarea resize w-[480px]"
        disabled={isLoading || setToNull}
        bind:value={currentValue}
        autocorrect="off"
      ></textarea>
      <ErrorPanel {errors}/>
      <div class="flex items-center justify-between mt-2">
        <Button {isLoading} class="btn btn-secondary" onClick={async () => {submit()}} dataTestId="submit-button">
          Save
        </Button>
        <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
      </div>
    {/if}
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
