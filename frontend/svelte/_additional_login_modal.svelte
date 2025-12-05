<script lang="ts">

import {type Database} from "./common/models"
import {type FetchError, invokeOnEnter, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import 'altcha'
import {trackEvent} from "./common/tracker";

export let onLoggedIn: () => Promise<void>

let modal: HTMLDialogElement;
let usernameInput: HTMLInputElement;
let altcha: any

let database_: Database | null = null
let isLoading = false
let errors: string[] = []

let form = {
  username: '',
  password: '',
  altcha: ''
}

export function open(database: Database): void {
  isLoading = false
  errors = []

  database_ = database
  form = {
    username: '',
    password: '',
    altcha: ''
  }

  modal.showModal()

  void focusOnInput()
}

async function focusOnInput(): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, 100))
  usernameInput?.blur()
  await new Promise(resolve => setTimeout(resolve, 10))
  usernameInput?.focus()
  usernameInput?.select()
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
    const json = await post('/api/login-additional', {
      database: database_!.nickname,
      ...form
    })

    await onLoggedIn()
    modal.close()
    trackEvent('data-source-logged-in')
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
    altcha.reset()
  }
}

</script>

<dialog
  bind:this={modal}
  class="modal2"
  data-test-id="edit-modal"
>
  <div class="modal-box !max-w-[480px] !w-auto flex flex-col gap-4" onkeydown={invokeOnEnter(submit)}>
    <div class="text-neutral-content text-sm">
      Login to the database: <span class="font-bold">{database_?.nickname}</span>
    </div>
    <input
      type="text"
      class="input w-full"
      placeholder="Username"
      data-test-id="username"
      bind:this={usernameInput}
      bind:value={form.username}
      autocorrect="off"
    />
    <input
      type="password"
      class="input w-full"
      placeholder="Password"
      data-test-id="password"
      bind:value={form.password}
      autocorrect="off"
    />
    <altcha-widget
      bind:this={altcha}
      style="--altcha-max-width:100%"
      challengeurl="/altcha"
      onstatechange={(ev) => {
          const { payload, state } = ev.detail
          if (state === 'verified' && payload) {
            form.altcha = payload;
          } else {
            form.altcha = '';
          }
        }}
    ></altcha-widget>
    <ErrorPanel {errors}/>
    <Button {isLoading} onClick={submit} dataTestId="submit-button">Login</Button>
    <div class="text-neutral-content text-sm">
      If you don't have username and password, please contact your administrator.
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
