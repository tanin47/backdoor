<script lang="ts">

import {type Database} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import {trackEvent} from "./common/tracker";

export let onDeleted: (database: Database) => Promise<void>

let modal: HTMLDialogElement;

let database_: Database | null = null
let isLoading = false
let errors: string[] = []

export function open(database: Database): void {
  database_ = database;
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
  if (database_ === null) {
    return
  }

  isLoading = true

  try {
    const json = await post('/api/delete-data-source', {
      database: database_.nickname,
    })

    trackEvent('data-source-deleted')
    await onDeleted(database_)
    modal.close()
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  }
}

</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !max-w-[480px] !w-auto flex flex-col gap-4">
    {#if database_}
      <div>Are you sure you want to remove: <code>{database_.nickname}</code>?</div>
      <div class="text-sm">
        This will only remove the data source connection from Backdoor. It doesn't destroy nor modify the data source.
      </div>
      <ErrorPanel {errors}/>
      <div class="flex items-center justify-end gap-2">
        <Button {isLoading} class="btn btn-warning" onClick={async () => {submit()}} dataTestId="submit-button">
          Remove
        </Button>
        <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
      </div>
    {/if}
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
