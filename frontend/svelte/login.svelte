<script lang="ts">
import Button from './common/_button.svelte'
import {FetchError, invokeOnEnter, post} from "./common/form";
import ErrorPanel from './common/form/_error_panel.svelte';
import 'altcha'

let isLoading = false
let errors: string[] = []
let form = {
  username: '',
  password: '',
  altcha: ''
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
</script>

<div class="hero bg-base-200 min-h-screen">
  <div class="hero-content flex-col justify-center items-center">
    <div class="card bg-base-100 min-w-[400px] w-full max-w-sm shrink-0 shadow-2xl">
      <div class="card-body flex flex-col gap-4" onkeydown={invokeOnEnter(submit)}>
        <span class="label">Username</span>
        <input type="text" class="input w-full" placeholder="Username" data-test-id="username"
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


<style lang="scss">
</style>
