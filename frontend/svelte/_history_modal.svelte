<script lang="ts">

import HistoryModalEntry from './_history_modal_entry.svelte';
import type {SqlHistoryEntry} from "./common/models";
import {getSqlHistoryEntries} from "./history";

export let onSetEditorPanel: (sql: string) => void

let modal: HTMLDialogElement;
let mainInput: HTMLInputElement | null

let keyword = ''
let queries: SqlHistoryEntry[] = [];

async function load(keyword: string): Promise<void> {
  queries = await getSqlHistoryEntries(keyword)
}

export async function open(): Promise<void> {
  await load(keyword);
  modal.showModal()

  void focusOnMainInput()
}

$: if (modal && modal.open) {
  void load(keyword)
}

async function focusOnMainInput(): Promise<void> {
  await new Promise(resolve => setTimeout(resolve, 100))
  mainInput?.blur()
  await new Promise(resolve => setTimeout(resolve, 10))
  mainInput?.focus()
  mainInput?.select()
}

export function close(): void {
  modal.close()
}

</script>

<dialog
  bind:this={modal}
  class="modal2"
  data-test-id="edit-modal"
>
  <div class="modal-box !min-w-[600px] !w-[600px] !max-w-[600px] flex flex-col gap-4">
    <label class="input input-sm w-full">
      <i class="ph ph-magnifying-glass"></i>
      <input
        type="search"
        class="grow"
        placeholder="Search the history"
        data-test-id="search-input"
        bind:this={mainInput}
        bind:value={keyword}
      />
    </label>
    <div class="text-xs text-neutral-content flex flex-row items-center justify-between">
      <span>Found {queries.length} {queries.length === 1 ? 'entry' : 'entries'}</span>
      <span>
        <i class="ph ph-info text-base"
           title="The history is saved locally on your machine and visible only to you."></i>
      </span>
    </div>
    {#if queries.length === 0}
      <div class="min-h-[300px] flex items-center justify-center text-neutral-content italic">
        No history
      </div>
    {:else}
      <div class="min-h-[300px] max-h-[300px] overflow-y-auto">
        <div class="flex flex-col gap-2 overflow-visible">
          {#each queries as query, index (`${query.sql}-${query.executedAt}-${query.database}`)}
            <HistoryModalEntry
              {query}
              onClick={async () => { onSetEditorPanel(query.sql) }}
            />
          {/each}
        </div>
      </div>
    {/if}
  </div>
  <div class="modal-backdrop" onclick={close}>
  </div>
</dialog>
