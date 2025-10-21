<script lang="ts">
// Copied from : https://github.com/sveltejs/svelte-virtual-list
// Because we need to support `index`.

// @ts-nocheck

import {onMount, tick} from 'svelte';

// props
export let items;
export let initialScrollLeft: number = 0;
export let initialScrollTop: number = 0;
export let getItemHeight: (index: number) => number;
export let onBottomReached: (() => void) | null = null;
export let onScrolled: ((scrollLeft: number, scrollTop: number) => void) | null = null;

let bottomElement: HTMLElement;

// read-only, but visible to consumers via bind:start
export let start = 0;
export let end = 0;

// local state
let height_map = [];
let rows;
let viewport;
let contents;
let viewport_height = 0;
let visible;
let mounted;

let top = 0;
let bottom = 0;
let average_height;

$: visible = items.slice(start, end).map((data, i) => {
  return { index: i + start, data };
});

// whenever `items` changes, invalidate the current heightmap
$: if (mounted) refresh(items, viewport_height);

async function refresh(items, viewport_height) {
  const { scrollTop } = viewport;

  await tick(); // wait until the DOM is up to date

  let content_height = top - scrollTop;
  let i = start;

  while (content_height < viewport_height && i < items.length) {
    let row = rows[i - start];

    if (!row) {
      end = i + 1;
      await tick(); // render the newly visible row
      row = rows[i - start];
    }

    const row_height = height_map[i] = getItemHeight(i) || row.offsetHeight;
    content_height += row_height;
    i += 1;
  }

  end = i;

  const remaining = items.length - end;
  average_height = (top + content_height) / end;

  bottom = remaining * average_height;
  height_map.length = items.length;

  await tick();
  viewport.scrollTo(initialScrollLeft, initialScrollTop);
}

async function handle_scroll() {
  const { scrollLeft, scrollTop } = viewport;

  const old_start = start;

  for (let v = 0; v < rows.length; v += 1) {
    height_map[start + v] = getItemHeight(start + v) || rows[v].offsetHeight;
  }

  let i = 0;
  let y = 0;

  while (i < items.length) {
    const row_height = height_map[i] || average_height;
    if (y + row_height > scrollTop) {
      start = i;
      top = y;

      break;
    }

    y += row_height;
    i += 1;
  }

  while (i < items.length) {
    y += height_map[i] || average_height;
    i += 1;

    if (y > scrollTop + viewport_height) break;
  }

  end = i;

  const remaining = items.length - end;
  average_height = y / end;

  while (i < items.length) height_map[i++] = average_height;
  bottom = remaining * average_height;

  // prevent jumping if we scrolled up into unknown territory
  if (start < old_start) {
    await tick();

    let expected_height = 0;
    let actual_height = 0;

    for (let i = start; i < old_start; i +=1) {
      if (rows[i - start]) {
        expected_height += height_map[i];
        actual_height += getItemHeight(i) || rows[i - start].offsetHeight;
      }
    }

    const d = actual_height - expected_height;
    // I don't know what this logic is doing but it creates a bug where:
    // 1. Select one sheet. Scroll to somewhere in the middle.
    // 2. Switch to another sheet
    // 3. Switch back and scroll up a little. The scroll will jump to 0
    // viewport.scrollTo(0, scrollTop + d);
  }

  onScrolled(scrollLeft, scrollTop)
  // TODO if we overestimated the space these
  // rows would occupy we may need to add some
  // more. maybe we can just call handle_scroll again?
}

// trigger initial refresh
onMount(() => {
  rows = contents.getElementsByTagName('svelte-virtual-list-row');
  mounted = true;

  const observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        if (onBottomReached) onBottomReached();
      }
    });
  }, {
    threshold: 0.1,
  });

  if (bottomElement) {
    observer.observe(bottomElement);
  }

  return () => observer.disconnect();
});
</script>

<svelte-virtual-list-viewport
  bind:this={viewport}
  bind:offsetHeight={viewport_height}
  on:scroll={handle_scroll}
  style="position: relative; overflow: auto; -webkit-overflow-scrolling:touch;box-sizing: border-box;display:block; font-size: 0px;"
  class="grow"
>
  <!-- we need the header here because overflow: visible auto; isn't a valid combination. Therefore, the header cannot be outside of this component. -->
  <!-- We wouldn't be able to support scrolling to the right where both rows and the header scroll together -->
  <!-- The header must be sticker with top-0 -->
  <slot name="header" />
  <svelte-virtual-list-contents
    bind:this={contents}
    style="padding-top: {top}px; padding-bottom: {bottom}px; display: block; box-sizing: border-box; font-size: 0px; position: relative;"
  >
    {#each visible as row (row.index)}
      <svelte-virtual-list-row
        style="height: {height_map[row.index]}px; min-height: {height_map[row.index]}px; max-height: {height_map[row.index]}px; display: block; box-sizing: border-box; font-size: 0px;"
      >
        <slot item={row.data} index={row.index}>Missing template</slot>
      </svelte-virtual-list-row>
    {/each}
    <div bind:this={bottomElement} class="absolute bottom-0 left-0 right-0 h-[10px]"></div>
  </svelte-virtual-list-contents>
</svelte-virtual-list-viewport>
