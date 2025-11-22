<script lang="ts">

import {type FetchError, invokeOnEnter, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import 'altcha'

export let onAdded: () => Promise<void>

let modal: HTMLDialogElement;
let mainInput: HTMLInputElement;

let isLoading = false
let errors: string[] = []

let form = {
  url: '',
  nickname: '',
  username: '',
  password: '',
}

export function open(): void {
  isLoading = false
  errors = []

  form = {
    url: '',
    nickname: '',
    username: '',
    password: '',
  }

  modal.showModal()

  void focusOnInput()
}

async function focusOnInput(): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, 100))
  mainInput?.blur()
  await new Promise(resolve => setTimeout(resolve, 10))
  mainInput?.focus()
  mainInput?.select()
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
    const json = await post('/api/add-data-source', form)

    await onAdded()
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
  <div class="modal-box !min-w-[480px] !w-auto flex flex-col gap-4" onkeydown={invokeOnEnter(submit)}>
    <div class="text-neutral-content text-sm">
      Add a new data source
    </div>
    <span class="label">Database URL</span>
    <input
      type="text"
      class="input w-full"
      placeholder="URL"
      data-test-id="url"
      bind:this={mainInput}
      bind:value={form.url}
    />
    <div class="text-neutral-content text-xs">
      A JDBC or Postgres URL is accepted. Credentials specified within the URL are also accepted.
    </div>
    <span class="label">Nickname</span>
    <input
      type="text"
      class="input w-full"
      placeholder="Nickname"
      data-test-id="nickname"
      bind:value={form.nickname}
    />
    <div class="text-neutral-content text-xs">
      The nickname of the database will be shown in the left panel.
    </div>
    <span class="label">Credentials (optional)</span>
    <div class="flex gap-2 items-center justify-between">
      <input
        type="text"
        class="input w-full"
        placeholder="Username"
        data-test-id="username"
        bind:value={form.username}
      />
      <input
        type="password"
        class="input w-full"
        placeholder="Password"
        data-test-id="password"
        bind:value={form.password}
      />
    </div>
    <div class="text-neutral-content text-xs">
      If the URL doesn't contain the credentials, you can specify them here.
    </div>
    <ErrorPanel {errors}/>
    <Button {isLoading} onClick={submit} dataTestId="submit-button">Connect DataSource</Button>
    <div class="text-warning text-xs">
      The data source added here will be for you only. The credentials will be stored on your machine.
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
