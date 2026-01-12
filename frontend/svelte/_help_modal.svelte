<script lang="ts">

import {Sheet} from "./common/models"
import {type FetchError, post} from "./common/form"
import Button from './common/_button.svelte'
import ErrorPanel from "./common/form/_error_panel.svelte"
import {trackEvent} from "./common/tracker";

let modal: HTMLDialogElement;

export function open(): void {
  modal.showModal()
}

export function close(): void {
  modal.close()
}
</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !max-w-[600px] !w-[600px] text-sm">
    <form method="dialog">
      <button class="btn btn-sm btn-circle btn-ghost absolute right-2 top-2">✕</button>
    </form>
    <h3 class="text-lg font-bold text-primary">We need your help!</h3>
    <p class="pt-3 leading-relaxed">
      We are asking you to help us <span class="underline">report a bug</span> if you encounter one,
      <span class="underline">make a suggestion</span> if it will make your work better,
      and <span class="underline">ask a question</span> if you find anything confusing.
    </p>
    <p class="pt-3 leading-relaxed">
      Please don't hesitate to email us at <code class="underline">support@backdooradmin.app</code>.
    </p>
    <p class="pt-3 leading-relaxed">
      Thank you for using Backdoor, and we hope we can make Backdoor better for you!
    </p>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
