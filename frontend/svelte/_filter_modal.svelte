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
let mode: 'no_filter' | 'null' | 'not_null' | 'specified_value' = 'specified_value'
let shouldTrim = true

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
    const filter = sheet.filters[foundIndex]

    if (filter.operator === 'IS_NULL') {
      mode = 'null'
    } else if (filter.operator === 'IS_NOT_NULL') {
      mode = 'not_null'
    } else if (filter.operator === 'EQUAL') {
      mode = 'specified_value'
    } else {
      throw new Error(`Unrecognized column '${filter.operator}'`)
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

  const newFilters = [...sheet.filters];
  const foundIndex = newFilters.findIndex(f => f.name === currentColumn!.name)

  if (foundIndex >= 0) {
    newFilters.splice(foundIndex, 1)
  }

  if (mode === 'no_filter') {
    // do nothing
  } else if (mode === 'null') {
    newFilters.push({name: currentColumn.name, value: '', operator: 'IS_NULL'})
  } else if (mode === 'not_null') {
    newFilters.push({name: currentColumn.name, value: '', operator: 'IS_NOT_NULL'})
  } else if (mode === 'specified_value') {
    newFilters.push({
      name: currentColumn.name,
      value: shouldTrim ? specifiedValue.trim() : specifiedValue,
      operator: 'EQUAL'
    })
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
    json.sheet.filters = newFilters
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
            data-test-id="null-checkbox"
            checked={mode === 'null'}
            disabled={isLoading}
            onclick={() => {mode = 'null'}}
          />
          <span>Filter for <code>null</code></span>
        </label>
      </div>
      <div class="flex gap-2 items-center text-sm">
        <label class="flex gap-2 items-center cursor-pointer">
          <input
            type="checkbox"
            class="checkbox checkbox-xs"
            data-test-id="not-null-checkbox"
            checked={mode === 'not_null'}
            disabled={isLoading}
            onclick={() => {mode = 'not_null'}}
          />
          <span>Filter for <code>not null</code></span>
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
    <div class="relative">
      {#if mode !== 'specified_value'}
        <div
          class="absolute left-0 top-0 bottom-0 right-0 z-50"
          data-test-id="disabled-new-value-overlay"
          onclick={() => {
              if (mode !== 'specified_value') {
                mode = 'specified_value'
              }
            }}
        ></div>
      {/if}
      <textarea
        data-test-id="specified-value-input"
        bind:this={textarea}
        class="textarea resize w-[480px]"
        disabled={isLoading || mode !== 'specified_value'}
        bind:value={specifiedValue}
        autocorrect="off"
      ></textarea>
    </div>
    {#if mode === 'specified_value' && currentColumn}
      {#if currentColumn.type === 'STRING'}
        <label class="flex gap-2 items-center cursor-pointer">
          <input
            data-test-id="trim-value"
            type="checkbox"
            class="checkbox checkbox-xs"
            bind:checked={shouldTrim}
            disabled={isLoading}
          />
          <span class="text-xs">Trim whitespaces</span>
        </label>
      {/if}
    {/if}
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
