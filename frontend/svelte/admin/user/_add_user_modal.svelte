<script lang="ts">

import {type FetchError, post} from "../../common/form"
import {trackEvent} from "../../common/tracker";
import ErrorPanel from "../../common/form/_error_panel.svelte";
import Button from "../../common/_button.svelte";

export let onAdded: () => Promise<void>

let modal: HTMLDialogElement;
let textInput: HTMLInputElement | null
let succeeded: boolean = false;

let form = {
  username: '',
  tempPassword: ''
}

let isLoading = false
let errors: string[] = []

export function open(): void {
  isLoading = false
  errors = []
  succeeded = false

  form = {
    username: '',
    tempPassword: Math.random().toString(36).substring(2, 12),
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
  isLoading = true

  try {
    const json = await post('/admin/user/create', form)

    trackEvent('db-user-created')
    await onAdded()
    succeeded = true
  } catch (e) {
    errors = (e as FetchError).messages
  } finally {
    isLoading = false
  }
}

</script>

<dialog
  bind:this={modal}
  class="modal2"
  data-test-id="edit-modal"
>
  <div class="modal-box w-[480px] flex flex-col gap-4">
    {#if succeeded}
      <div class="text-sm">
        The temporary password for
        <code>{form.username}</code>
        is:
      </div>
      <code class="!text-2xl font-bold !p-4">
        {form.tempPassword}
      </code>
      <div class="text-sm text-neutral-content">
        Please instruct the user to login using the temporary password promptly. The temporary password will expire in
        24 hours.
      </div>
      <div class="flex items-center justify-between mt-2">
        <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
      </div>
    {:else}
      <span class="text-lg">
        Add a new user
      </span>
      <span>Username</span>
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
      <span>Temporary password</span>
      <input
        type="text"
        data-test-id="password"
        class="input input-sm w-full"
        disabled={isLoading}
        bind:value={form.tempPassword}
        autocorrect="off"
        data-1p-ignore
      />
      <div class="text-xs">
        The temporary password will expire in 24 hours. The user will be required to set a new password after
        logging in using the temporary password.
      </div>
      <ErrorPanel {errors}/>
      <div class="flex items-center justify-between mt-2">
        <Button {isLoading} class="btn btn-secondary" onClick={async () => {submit()}} dataTestId="submit-button">
          Create new user
        </Button>
        <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
      </div>
    {/if}
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
