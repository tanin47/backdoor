<script lang="ts">
import Button from './common/_button.svelte'
import {FetchError, invokeOnEnter, post} from "./common/form";
import ErrorPanel from './common/form/_error_panel.svelte';
import {onMount} from "svelte";
import {trackEvent} from "./common/tracker";

let isLoading = false
let errors: string[] = []
let form = {
  password: '',
  confirmPassword: ''
}

async function submit(): Promise<void> {
  isLoading = true
  errors = []
  try {
    const _json = await post('/set-password', form)

    window.location.href = '/'
    trackEvent('password-set-succeeded')
  } catch (e) {
    trackEvent('password-set-failed')
    isLoading = false
    errors = (e as FetchError).messages
  }
}

onMount(() => {
  trackEvent('password-set-landed')
})

</script>

<div class="hero bg-base-200 min-h-screen">
  <div class="hero-content flex-col justify-center items-center">
    <div class="card bg-base-100 min-w-[400px] w-full max-w-sm shrink-0 shadow-2xl">
      <div class="card-body flex flex-col gap-4" onkeydown={invokeOnEnter(submit)}>
        <div class="text-neutral-content">
          You currently have a temporary password. Please set a new one.
        </div>
        <span class="label">New password</span>
        <input type="password" class="input w-full" placeholder="New password" data-test-id="password"
               autocorrect="off"
               bind:value={form.password}/>
        <span class="label">Confirm new password</span>
        <input type="password" class="input w-full" placeholder="Confirm password" data-test-id="confirm-password"
               bind:value={form.confirmPassword}/>
        <ErrorPanel {errors}/>
        <Button {isLoading} onClick={submit} dataTestId="submit-set-password-button">Set new password</Button>
        <div class="text-center">
          <a href="/logout" class="underline cursor-pointer text-neutral-content">Logout</a>
        </div>
      </div>
    </div>
  </div>
</div>

<style lang="scss">
</style>
