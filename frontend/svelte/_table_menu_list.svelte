<script lang="ts">
import type {Database, Query, Table} from "./common/models";
import tippy, {type Instance} from 'tippy.js';
import {onMount} from "svelte";

export let database: Database
export let queries: Query[]
export let selectedQuery: Query | null

let moreButton: HTMLElement
let tooltip: HTMLElement

export let onTableClicked: (table: Table) => Promise<void>
export let onQueryClicked: (query: Query) => Promise<void>
export let onLoggingIn: () => void
export let onEditing: () => void
export let onDeleting: () => void

let expanded = true

let moreOptionTooltip: Instance;

onMount(() => {
  if (database.isAdHoc) {
    moreOptionTooltip = tippy(moreButton, {
      content: tooltip,
      allowHTML: true,
      interactive: true,
      trigger: 'click',
      duration: 0,
      offset: [0, 0],
      placement: 'bottom'
    });
    tooltip.style.display = "block";
  }
})
</script>

{#if database.isAdHoc}
  <div class="hidden" bind:this={tooltip}>
    <ul
      class="menu flex flex-col gap-0 border border-gray-500 rounded-lg bg-accent-content p-0"
    >
      <!--    <li>-->
      <!--      <div class="flex items-center gap-1 px-2 py-1 cursor-pointer">-->
      <!--        <i class="ph ph-arrow-clockwise text-xs"></i>-->
      <!--        <span class="text-xs">Refresh</span>-->
      <!--      </div>-->
      <!--    </li>-->
      <li>
        <div
          class="flex items-center gap-1 px-2 py-1 cursor-pointer"
          data-test-id="edit-data-source-button"
          onclick={() => {
            moreOptionTooltip.hide()
            onEditing()
          }}
        >
          <i class="ph ph-pencil-simple text-xs"></i>
          <span class="text-xs">Edit</span>
        </div>
      </li>
      <li>
        <div
          class="flex items-center gap-1 px-2 py-1 cursor-pointer"
          data-test-id="delete-data-source-button"
          onclick={() => {
            moreOptionTooltip.hide()
            onDeleting()
          }}
        >
          <i class="ph ph-trash text-xs"></i>
          <span class="text-xs">Remove</span>
        </div>
      </li>
    </ul>
  </div>
{/if}
<div>
  {#if database.requireLogin}
    <div
      class="flex items-center gap-2 p-2 cursor-pointer"
      onclick={onLoggingIn}
      data-test-id="database-lock-item"
      data-test-value={database.nickname}
    >
      <i class="ph ph-lock text-sm"></i>
      <span
        class="overflow-hidden text-ellipsis font-mono text-xs whitespace-nowrap underline">{database.nickname}</span>
    </div>
  {:else}
    <div class="flex items-center gap-2 justify-between cursor-pointer" onclick={() => {expanded = !expanded}}>
      <div class="flex items-center gap-2 ps-2 py-2 overflow-hidden">
        <i class="ph {expanded ? 'ph-caret-down' : 'ph-caret-right' } text-sm"></i>
        <span
          class="overflow-hidden text-ellipsis font-mono text-xs whitespace-nowrap underline">{database.nickname}</span>
      </div>
      {#if database.isAdHoc}
        <i
          bind:this={moreButton}
          class="ph ph-dots-three-vertical text-sm z-10 px-1 py-2 cursor-pointer"
          data-test-id="more-option-data-source-button"
          onclick={(ev) => {
            ev.stopPropagation()
            moreOptionTooltip.show()
          }}
        ></i>
      {/if}
    </div>
    <ul
      class="menu flex flex-col w-full text-xs p-0"
      data-test-id="menu-items"
      data-test-value={database.nickname}
      class:hidden={!expanded}
    >
      {#each database.tables as table (table)}
        <li class="w-full">
        <span
          class="flex items-center gap-2 font-mono w-full"
          onclick={() => {onTableClicked({database: database.nickname, name: table})}}
          data-test-id="menu-item-table"
          data-test-value={table}
        >
          <i class="ph ph-table"></i>
          <span class="overflow-hidden text-ellipsis whitespace-nowrap">{table}</span>
        </span>
        </li>
      {/each}
      {#each queries.filter(q => q.database === database.nickname) as query (query.name)}
        <li class="w-full">
        <span
          class="flex items-center gap-2 font-mono w-full cursor-pointer"
          onclick={() => {onQueryClicked(query)}}
          data-test-id="menu-item-query"
          data-test-value={query.name}
        >
          <i class="ph ph-file-sql"></i>
          <span
            class="overflow-hidden text-ellipsis whitespace-nowrap"
            class:underline={selectedQuery === query}
          >{query.name}</span>
        </span>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style lang="scss">
</style>
