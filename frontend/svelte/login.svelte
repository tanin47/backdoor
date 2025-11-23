<script lang="ts">
import Button from './common/_button.svelte'
import {FetchError, invokeOnEnter, post} from "./common/form";
import ErrorPanel from './common/form/_error_panel.svelte';
import 'altcha'
import {onMount} from "svelte";
import {trackEvent} from "./common/tracker";

let isLoading = false
let errors: string[] = []
let form = {
  username: '',
  password: '',
  altcha: '',
}
let altcha: any

async function submit(): Promise<void> {
  isLoading = true
  errors = []
  try {
    const _json = await post('/login', form)

    window.location.href = '/'
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
    altcha.reset()
  }
}

let isLocalhost = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1' || window.location.hostname === '[::1]';
let isValidHost = isLocalhost ? true : window.location.protocol === 'https:';

onMount(() => {
  trackEvent('landing_login')
})

</script>

{#if !isValidHost}
  <div class="hero bg-base-200 min-h-screen">
    <div class="hero-content flex-col justify-center items-center">
      <div class="max-w-[600px] text-2xl flex flex-col gap-4">
        <div>
          <div class="badge badge-outline badge-error font-mono badge-lg">
            {window.location.protocol}//{window.location.host}
          </div>
        </div>
        <div>
          <a href="https://github.com/tanin47/backdoor" class="link">Backdoor</a> blocks an HTTP (non-secure) access
          because your session might be hijacked.
        </div>
        <div>
          Please contact your administrator to enable HTTPS (secure) for
          <a href="https://github.com/tanin47/backdoor" class="link">Backdoor</a>.
        </div>
      </div>
    </div>
  </div>
{:else}
  <div class="hero bg-base-200 min-h-screen">
    <div class="hero-content flex-col justify-center items-center">
      <div class="card bg-base-100 min-w-[400px] w-full max-w-sm shrink-0 shadow-2xl">
        <div class="card-body flex flex-col gap-4" onkeydown={invokeOnEnter(submit)}>
          <span class="label">Username</span>
          <input type="text" class="input w-full" placeholder="Username" data-test-id="username"
                 autocorrect="off"
                 bind:value={form.username}/>
          <span class="label">Password</span>
          <input type="password" class="input w-full" placeholder="Password" data-test-id="password"
                 bind:value={form.password}/>
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
          <div class="text-neutral-content">
            If you don't have username and password, please contact your administrator.
          </div>
        </div>
      </div>
    </div>
  </div>
{/if}


<style lang="scss">
</style>
