<script lang="ts">

import {type Column, Sheet} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import {trackEvent} from "./common/tracker";

export let sheet: Sheet
export let onSheetUpdated: (newSheet: Sheet) => void

let modal: HTMLDialogElement;
let textarea: HTMLTextAreaElement | null

let specifiedValue: any
let currentColumn: Column | null
let mode: 'no_filter' | 'null' | 'specified_value' = 'specified_value'

let isLoading = false
let errors: string[] = []

$: if (mode === 'specified_value') {
  try {
    void focusOnTextarea()
  } catch (e) {
  }
}

export function open(column: Column): void {
  isLoading = false
  errors = []

  const foundIndex = sheet.filters.findIndex(s => s.name === column.name)

  if (foundIndex === -1) {
    mode = 'no_filter'
    specifiedValue = ''
  } else {
    specifiedValue = sheet.filters[foundIndex].value ?? ''

    if (specifiedValue === null) {
      mode = 'null'
    } else {
      mode = 'specified_value'
    }
  }
  currentColumn = column

  modal.showModal()
  void focusOnTextarea()
}

async function focusOnTextarea(): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, 100))
  textarea!.blur()
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
  if (!currentColumn) {
    return
  }

  isLoading = true

  const newFilters = sheet.filters;
  const foundIndex = newFilters.findIndex(f => f.name === currentColumn!.name)

  if (foundIndex >= 0) {
    newFilters.splice(foundIndex, 1)
  }

  if (mode === 'no_filter') {
    // do nothing
  } else if (mode === 'null') {
    newFilters.push({name: currentColumn.name, value: null})
  } else if (mode === 'specified_value') {
    newFilters.push({name: currentColumn.name, value: specifiedValue})
  }

  try {
    const json = await post(`/api/load-${sheet.type}`, {
      database: sheet.database,
      name: sheet.name,
      sql: sheet.sql,
      sorts: sheet.sorts,
      filters: newFilters,
      offset: 0
    })

    modal.close()
    onSheetUpdated(json.sheet)
    trackEvent('column-filtered')
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  }
}

</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !min-w-[480px] !w-auto !max-w-none flex flex-col gap-4">
    {#if currentColumn}
      <span class="text-lg">{currentColumn.name}</span>
    {/if}
    <div class="flex gap-2 items-center text-sm">
      <label class="flex gap-2 items-center cursor-pointer">
        <input
          type="checkbox"
          class="checkbox checkbox-xs"
          checked={mode === 'no_filter'}
          disabled={isLoading}
          onclick={() => {mode = 'no_filter'}}
        />
        <span>No filter for this column</span>
      </label>
    </div>
    {#if currentColumn && currentColumn.isNullable}
      <div class="flex gap-2 items-center text-sm">
        <label class="flex gap-2 items-center cursor-pointer">
          <input
            type="checkbox"
            class="checkbox checkbox-xs"
            checked={mode === 'null'}
            disabled={isLoading}
            onclick={() => {mode = 'null'}}
          />
          <span>Filter for <code>null</code></span>
        </label>
      </div>
    {/if}
    <div class="flex gap-2 items-center text-sm">
      <label class="flex gap-2 items-center cursor-pointer">
        <input
          data-test-id="specified-value-checkbox"
          type="checkbox"
          class="checkbox checkbox-xs"
          checked={mode === 'specified_value'}
          disabled={isLoading}
          onclick={() => {mode = 'specified_value'}}
        />
        <span>Use the specified value below:</span>
      </label>
    </div>
    <textarea
      data-test-id="specified-value-input"
      bind:this={textarea}
      class="textarea resize w-[480px]"
      disabled={isLoading || mode !== 'specified_value'}
      bind:value={specifiedValue}
      autocorrect="off"
    ></textarea>
    <ErrorPanel {errors}/>
    <div class="flex items-center justify-between mt-2">
      <Button {isLoading} class="btn btn-secondary" onClick={async () => {submit()}} dataTestId="submit-button">
        Update Filter
      </Button>
      <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
