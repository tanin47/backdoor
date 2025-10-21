<script lang="ts">

import {Sheet} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"

export let sheet: Sheet
export let onRenamed: (newName: string) => void

let modal: HTMLDialogElement;
let input: HTMLInputElement | null

let newName: string = ''

let isLoading = false
let errors: string[] = []

export function open(): void {
  if (sheet.type !== 'table') {
    throw new Error(`Expected table, got ${sheet.type}`)
  }
  isLoading = false
  errors = []

  newName = sheet.name

  modal.showModal()

  void focusOnInput()
}

async function focusOnInput(): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, 100))
  input!.blur()
  await new Promise(resolve => setTimeout(resolve, 10))
  input!.focus()
  input!.select()
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
    const json = await post('/api/rename-table', {
      table: sheet.name,
      newName
    })

    modal.close()
    onRenamed(newName)
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  }
}

</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box flex flex-col gap-4">
    <span class="text-lg">Rename the table</span>
    <input
      data-test-id="new-name"
      bind:this={input}
      type="text"
      class="input w-full"
      disabled={isLoading}
      bind:value={newName}
    >
    <ErrorPanel {errors}/>
    <div class="flex items-center justify-between mt-2">
      <Button {isLoading} class="btn btn-warning" onClick={async () => {submit()}} dataTestId="submit-button">Rename
      </Button>
      <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
