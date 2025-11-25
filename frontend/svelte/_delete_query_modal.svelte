<script lang="ts">

import {Sheet} from "./common/models"
import {type FetchError} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import {trackEvent} from "./common/tracker";

export let sheet: Sheet
export let onDropped: () => void

let modal: HTMLDialogElement;

let useCascade: boolean = false

let isLoading = false
let errors: string[] = []

export function open(): void {
  if (sheet.type !== 'query') {
    throw new Error(`Expected query. Got ${sheet.type}`)
  }
  isLoading = false
  errors = []

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
    modal.close()
    onDropped()
    trackEvent('query-deleted')
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  }
}

</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !min-w-[480px] !w-auto !max-w-none flex flex-col gap-4">
    <div>Are you sure you want to delete this Query?</div>
    <div class="text-sm">
      Name: <code>{sheet.name}</code>
    </div>
    <ErrorPanel {errors}/>
    <div class="flex items-center justify-end gap-2">
      <Button {isLoading} class="btn btn-warning" onClick={async () => {submit()}} dataTestId="submit-button">
        Delete
      </Button>
      <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
