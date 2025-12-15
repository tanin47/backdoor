<script lang="ts">

import type {DbUser} from "../../common/models";
import {type FetchError, post} from "../../common/form"
import Button from '../../common/_button.svelte'
import ErrorPanel from "../../common/form/_error_panel.svelte"
import {trackEvent} from "../../common/tracker";

export let onDeleted: () => Promise<void>

let modal: HTMLDialogElement;

let dbUser_: DbUser | null = null
let isLoading = false
let errors: string[] = []

export function open(user: DbUser): void {
  dbUser_ = user;
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
  if (dbUser_ === null) {
    return
  }

  isLoading = true

  try {
    const json = await post('/admin/user/delete', {
      id: dbUser_.id,
    })

    trackEvent('db-user-deleted')
    await onDeleted()
    modal.close()
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
  }
}

</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !min-w-[480px] !w-auto !max-w-none flex flex-col gap-4">
    <div>
      Are you sure you want to delete
      <code>
        {#if dbUser_}{dbUser_.username}{/if}
      </code>?
    </div>
    <ErrorPanel {errors}/>
    <div class="flex items-center justify-end gap-2">
      <Button {isLoading} class="btn btn-error" onClick={async () => {submit()}} dataTestId="submit-button">
        Delete
      </Button>
      <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
