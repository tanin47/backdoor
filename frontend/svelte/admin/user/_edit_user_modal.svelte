<script lang="ts">

import {type FetchError, post} from "../../common/form"
import {trackEvent} from "../../common/tracker";
import ErrorPanel from "../../common/form/_error_panel.svelte";
import Button from "../../common/_button.svelte";
import type {DbUser} from "../../common/models";

export let onUpdated: () => Promise<void>

let modal: HTMLDialogElement;
let textInput: HTMLInputElement | null

let dbUser_: DbUser | null = null
let form = {
  username: ''
}

let isLoading = false
let errors: string[] = []

export function open(user: DbUser): void {
  isLoading = false
  errors = []

  dbUser_ = user;
  form = {
    username: user.username
  }

  modal.showModal()
  void focusOnTextarea()
}

async function focusOnTextarea(): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, 100))
  textInput?.blur()
  await new Promise(resolve => setTimeout(resolve, 10))
  textInput?.focus()
  textInput?.select()
}

export function close(): void {
  if (isLoading) {
    return
  }
  modal.close()
}

async function submit() {
  if (dbUser_ == null) {
    return
  }

  isLoading = true

  try {
    const json = await post('/admin/user/update', {
      id: dbUser_.id,
      username: form.username,
    })

    trackEvent('db-user-edited')
    await onUpdated()
    modal.close()
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
  <div class="modal-box w-[480px] flex flex-col gap-4">
    <span class="text-lg">Username</span>
    <input
      type="text"
      data-test-id="username"
      bind:this={textInput}
      class="input input-sm w-full"
      disabled={isLoading}
      bind:value={form.username}
      autocorrect="off"
      data-1p-ignore
    />
    <ErrorPanel {errors}/>
    <div class="flex items-center justify-between mt-2">
      <Button {isLoading} class="btn btn-secondary" onClick={async () => {submit()}} dataTestId="submit-button">
        Save
      </Button>
      <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
