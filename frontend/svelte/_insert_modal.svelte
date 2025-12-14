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
let inserted = false

interface Value {
  isNull: boolean
  useDefaultValue: boolean
  value: string
}

let form: { [key: string]: Value } = {}

function resetForm(sheet: Sheet) {
  form = {}
  sheet.columns.forEach(column => {
    form[column.name] = {
      isNull: column.isNullable,
      useDefaultValue: column.hasDefaultValue,
      value: ''
    }
  })
}

$: resetForm(sheet)

export function open(): void {
  isLoading = false
  errors = []
  inserted = false

  resetForm(sheet)

  modal.showModal()
}

export function close(): void {
  if (isLoading) {
    return
  }
  modal.close()
}

async function submit() {
  isLoading = true

  try {
    const json = await post('/api/insert-row', {
      database: sheet.database,
      table: sheet.name,
      row: form
    })

    inserted = true
    trackEvent('row-inserted')
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  } finally {
    isLoading = false
  }
}

function determinePlaceholder(value: Value): string {
  if (value.isNull) {
    return 'Will set to null'
  }

  if (value.useDefaultValue) {
    return 'Will use the default value'
  }

  return value.value
}

</script>

<dialog
  bind:this={modal}
  class="modal2"
  data-test-id="edit-modal"
>
  <div class="modal-box !max-w-4/5 w-1/2 !min-w-[480px] max-h-4/5 overflow-y-scroll flex flex-col gap-4">
    {#if inserted}
      <div class="flex flex-row justify-center items-center p-2 gap-2">
        <i class="ph ph-check-circle text-3xl text-success"></i>
        <div class="text-success text-lg">
          The row has been inserted successfully.
        </div>
      </div>
      <div class="flex items-center gap-4 justify-center">
        <button type="button" class="btn btn-info" data-test-id="insert-more-button" onclick={open}>Insert more</button>
        <button type="button" class="btn btn-neutral" data-test-id="close-button" onclick={close}>Close</button>
      </div>
    {:else}
      <div>
        Insert a row into <code>{sheet.name}</code>
      </div>
      {#each sheet.columns as column, index (index)}
        <div class="flex gap-2 items-start text-sm">
          <div
            class="{column.type === 'STRING' ? 'pt-[7px]' : 'pt-[5px]'} text-right min-w-[100px] max-w-[100px] w-[100px] overflow-hidden text-ellipsis whitespace-nowrap"
          >
            {#if column.isPrimaryKey}
              <i class="ph ph-key" data-tippy-content="Primary key"></i>
            {/if}
            <span>{column.name}</span>
          </div>
          <div class="grow">
            {#if column.type === 'STRING'}
              <textarea
                class="textarea textarea-sm w-full min-h-[34px] h-[34px] whitespace-nowrap overflow-auto"
                placeholder={determinePlaceholder(form[column.name])}
                bind:value={form[column.name].value}
                disabled={form[column.name].isNull || form[column.name].useDefaultValue}
                data-test-id="insert-field"
                data-test-value={column.name}
              ></textarea>
            {:else}
              <input
                type="text"
                class="input input-sm w-full"
                placeholder={determinePlaceholder(form[column.name])}
                bind:value={form[column.name].value}
                disabled={form[column.name].isNull || form[column.name].useDefaultValue}
                data-test-id="insert-field"
                data-test-value={column.name}
              />
            {/if}
          </div>
          {#if column.hasDefaultValue}
            <div class="pt-1">
              <button
                class="cursor-pointer btn btn-outline btn-xs {form[column.name].useDefaultValue ? 'btn-secondary' : 'btn-neutral'}"
                onclick={() => {
                  if (form[column.name].useDefaultValue) {
                    form[column.name].useDefaultValue = false
                  } else{
                    form[column.name].useDefaultValue = true
                    form[column.name].value = ''
                  }
                  form = form
                }}
                data-test-id="default-toggle-button"
                data-test-value={column.name}
              >
                <i class="ph ph-check-square text-sm"></i>
                Default
              </button>
            </div>
          {:else if column.isNullable}
            <div class="pt-1">
              <button
                class="cursor-pointer btn btn-outline btn-xs {form[column.name].isNull ? 'btn-accent' : 'btn-neutral'}"
                onclick={() => {
                  if (form[column.name].isNull) {
                    form[column.name].isNull = false
                  } else{
                    form[column.name].isNull = true
                    form[column.name].value = ''
                  }
                  form = form
                }}
                data-test-id="null-toggle-button"
                data-test-value={column.name}
              >
                <i class="ph ph-check-square text-sm"></i>
                Null
              </button>
            </div>
          {/if}
        </div>
      {/each}
      <ErrorPanel {errors}/>
      <div class="flex items-center justify-between mt-2">
        <Button {isLoading} class="btn btn-secondary" onClick={async () => {submit()}} dataTestId="submit-button">
          Insert
        </Button>
        <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
      </div>
    {/if}
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
