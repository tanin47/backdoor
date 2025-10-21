<script lang="ts">

import ErrorPanel from "./form/_error_panel.svelte"

let modal: HTMLDialogElement;

let remark_: string | null = null;
let errors_: string[] = []

export function open(errors: string[], remark: string | null = null): void {
  errors_ = errors
  remark_ = remark

  modal.showModal()
}

export function close(): void {
  modal.close()
}


</script>

<dialog bind:this={modal} class="modal2">
  <div class="modal-box !min-w-[480px] !w-auto !max-w-[600px] flex flex-col gap-4">
    <div class="text-lg">Something is wrong!</div>
    <ErrorPanel errors={errors_}/>
    {#if remark_}
      <div class="text-sm">{remark_}</div>
    {/if}
    <div class="flex items-center justify-end gap-2">
      <button type="button" class="btn btn-neutral" onclick={close}>Close</button>
    </div>
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
