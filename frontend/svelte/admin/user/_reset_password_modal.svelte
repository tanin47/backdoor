<script lang="ts">

import {type FetchError, post} from "../../common/form"
import {trackEvent} from "../../common/tracker";
import ErrorPanel from "../../common/form/_error_panel.svelte";
import Button from "../../common/_button.svelte";
import type {DynamicUser} from "../../common/models";

export let onPasswordReset: () => Promise<void>

let modal: HTMLDialogElement;
let textInput: HTMLInputElement | null
let succeeded: boolean = false;

let dbUser_: DynamicUser | null = null
let form = {
  tempPassword: ''
}

let isLoading = false
let errors: string[] = []

export function open(user: DynamicUser): void {
  isLoading = false
  errors = []
  succeeded = false
  dbUser_ = user

  form = {
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
  if (dbUser_ == null) {
    return
  }

  isLoading = true

  try {
    const json = await post('/admin/user/set-temp-password', {
      id: dbUser_.id,
      tempPassword: form.tempPassword
    })

    trackEvent('db-user-password-reset')
    await onPasswordReset()
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
        {#if dbUser_}
          <code>{dbUser_.username}</code>
        {/if}
        is:
      </div>
      <code class="!text-2xl font-bold !p-4">
        {form.tempPassword}
      </code>
      <div class="text-sm">
        Please instruct the user to login using the temporary password promptly. The temporary password will expire in
        24 hours.
      </div>
      <div class="flex items-center justify-between mt-2">
        <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
      </div>
    {:else}
      <span class="text-lg">
        Generate temporary password for
        {#if dbUser_}
          <code>{dbUser_.username}</code>
        {/if}
      </span>
      <input
        type="text"
        data-test-id="password"
        bind:this={textInput}
        class="input input-sm w-full"
        disabled={isLoading}
        bind:value={form.tempPassword}
        autocorrect="off"
        data-1p-ignore
      />
      <div class="text-xs text-neutral-content">
        The temporary password will expire within 24 hours. The user will be required to set a new password after
        logging in using the temporary password.
      </div>
      <ErrorPanel {errors}/>
      <div class="flex items-center justify-between mt-2">
        <Button {isLoading} class="btn btn-warning" onClick={async () => {submit()}} dataTestId="submit-button">
          Set the temporary password
        </Button>
        <button type="button" class="btn btn-neutral" disabled={isLoading} onclick={close}>Cancel</button>
      </div>
    {/if}
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
