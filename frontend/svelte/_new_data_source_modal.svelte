<script lang="ts">

import {type FetchError, invokeOnEnter, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import 'altcha'
import {trackEvent} from "./common/tracker";
import {openFileDialog, PARADIGM} from "./common/globals";
import type {DatabaseType} from "./common/models";

export let onAdded: (nickname: string) => Promise<void>

let modal: HTMLDialogElement;
let mainInput: HTMLInputElement;

let isLoading = false
let errors: string[] = []
let databaseType: DatabaseType;

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

    await onAdded(form.nickname)
    modal.close()
    trackEvent('new-data-source-added')
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
    <input
      type="text"
      class="input input-sm w-full"
      placeholder="Nickname"
      data-test-id="nickname"
      bind:value={form.nickname}
      bind:this={mainInput}
      autocorrect="off"
    />
    <select class="select select-sm w-full" bind:value={databaseType}>
      <option value="postgres">Postgres</option>
      <option value="clickhouse">ClickHouse</option>
      {#if PARADIGM === 'DESKTOP'}
        <option value="sqlite">SQLite</option>
      {/if}
    </select>
    {#if databaseType === 'sqlite'}
      <div class="flex flex-col gap-2 items-center w-full">
        <div class="flex flex-row gap-2 items-center w-full">
          <button
            class="btn btn-sm btn-outline"
            onclick={async () => {
              const resp = await openFileDialog(true)
              form.url = `jdbc:sqlite:${resp.filePath}`
            }}
          >
            Create a SQLite file
          </button>
          <button
            class="btn btn-sm btn-outline"
            onclick={async () => {
              const resp = await openFileDialog(false)
              form.url = `jdbc:sqlite:${resp.filePath}`
            }}
          >
            Select a SQLite file
          </button>
        </div>
        <input
          type="text"
          placeholder="No file selected"
          class="input input-sm w-full"
          readonly
          disabled
          value={form.url}/>
      </div>
    {:else}
      <input
        type="text"
        class="input input-sm w-full"
        placeholder="Database URL"
        data-test-id="url"
        bind:value={form.url}
        autocorrect="off"
      />
      <div class="text-neutral-content text-xs leading-loose">
        {#if databaseType === "postgres"}
          A JDBC URL (starting with <code>jdbc:postgres://</code> or <code>jdbc:postgresql://</code>) or Postgres URL
          (starting with <code>postgres://</code> or <code>postgresql://</code>) is
          accepted.
        {:else if databaseType === "clickhouse"}
          A JDBC URL (starting with <code>jdbc:ch://</code>) is accepted.
        {/if}
      </div>
      <div class="flex gap-2 items-center justify-between">
        <input
          type="text"
          class="input input-sm w-full"
          placeholder="Username"
          data-test-id="username"
          bind:value={form.username}
          autocorrect="off"
        />
        <input
          type="password"
          class="input input-sm w-full"
          placeholder="Password"
          data-test-id="password"
          bind:value={form.password}
          autocorrect="off"
        />
      </div>
      <div class="text-neutral-content text-xs">
        If the URL doesn't contain the credentials, you can specify them here.
      </div>
    {/if}
    <ErrorPanel {errors}/>
    <Button class="btn btn-neutral btn-sm" {isLoading} onClick={submit} dataTestId="submit-button">Connect Data Source
    </Button>
    <div class="text-warning text-xs">
      The data source added here will be for you only. The credentials will be stored on your machine.
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
