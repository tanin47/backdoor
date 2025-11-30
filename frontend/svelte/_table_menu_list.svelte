<script lang="ts">
import type {Database, Query, Table} from "./common/models";

export let database: Database
export let queries: Query[]
export let selectedQuery: Query | null

export let onTableClicked: (table: Table) => Promise<void>
export let onQueryClicked: (query: Query) => Promise<void>
export let onLoggingIn: () => void
export let onDeleting: () => void

let expanded = true
</script>

<div>
  {#if database.requireLogin}
    <div
      class="flex items-center gap-2 p-2 cursor-pointer"
      onclick={onLoggingIn}
      data-test-id="database-lock-item"
      data-test-value={database.name}
    >
      <i class="ph ph-lock text-sm"></i>
      <span class="overflow-hidden text-ellipsis font-mono text-xs whitespace-nowrap underline">{database.name}</span>
    </div>
  {:else}
    <div class="flex items-center gap-2 p-2 justify-between cursor-pointer" onclick={() => {expanded = !expanded}}>
      <div class="flex items-center gap-2 overflow-hidden">
        <i class="ph {expanded ? 'ph-caret-down' : 'ph-caret-right' } text-sm"></i>
        <span class="overflow-hidden text-ellipsis font-mono text-xs whitespace-nowrap underline">{database.name}</span>
      </div>
      {#if database.isAdHoc}
        <i
          class="ph ph-trash text-sm z-10 cursor-pointer"
          onclick={(ev) => {
            ev.stopPropagation();
            onDeleting()
          }}
          data-test-id="delete-data-source-button"></i>
      {/if}
    </div>
    <ul
      class="menu flex flex-col w-full text-xs p-0"
      data-test-id="menu-items"
      data-test-value={database.name}
      class:hidden={!expanded}
    >
      {#each database.tables as table (table)}
        <li class="w-full">
        <span
          class="flex items-center gap-2 font-mono w-full"
          onclick={() => {onTableClicked({database: database.name, name: table})}}
          data-test-id="menu-item-table"
          data-test-value={table}
        >
          <i class="ph ph-table"></i>
          <span class="overflow-hidden text-ellipsis whitespace-nowrap">{table}</span>
        </span>
        </li>
      {/each}
      {#each queries.filter(q => q.database === database.name) as query (query.name)}
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
