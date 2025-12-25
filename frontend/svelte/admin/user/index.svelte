<script lang="ts">
import AddUserModal from "./_add_user_modal.svelte";
import EditUserModal from "./_edit_user_modal.svelte";
import DeleteUserModal from "./_delete_user_modal.svelte";
import ResetPasswordModal from "./_reset_password_modal.svelte";
import {onMount} from "svelte";
import type {DynamicUser} from "../../common/models";
import {type FetchError, post} from "../../common/form";
import {trackEvent} from "../../common/tracker";

let addModal: AddUserModal;
let editModal: EditUserModal;
let deleteModal: DeleteUserModal;
let resetPasswordModal: ResetPasswordModal;

let isLoading: boolean = false;
let errors: string[] = [];
let users: DynamicUser[] = [];

async function load(): Promise<void> {
  isLoading = true
  errors = []

  try {
    const json = await post('/admin/user/load', {})

    users = json.users
    trackEvent('db-user-list-loaded')
  } catch (e) {
    isLoading = false
    errors = (e as FetchError).messages
    // Show error
  }
}

onMount(() => {
  void load();
});
</script>

<AddUserModal
  bind:this={addModal}
  onAdded={async () => {await load();}}
/>

<EditUserModal
  bind:this={editModal}
  onUpdated={async () => {await load();}}
/>

<DeleteUserModal
  bind:this={deleteModal}
  onDeleted={async () => {await load();}}
/>

<ResetPasswordModal
  bind:this={resetPasswordModal}
  onPasswordReset={async () => {await load();}}
/>

<div class="flex flex-col gap-4">
  <div class="flex justify-between items-center">
    <div class="flex items-baseline gap-2">
      <div class="text-lg font-bold text-primary p-4">Admin Panel</div>
      <button class="btn btn-outline btn-sm btn-secondary" data-test-id="add-new-user-button"
              onclick={() => {addModal.open()}}>
        Add new user
      </button>
    </div>
    <div class="p-2">
      <a href="/" class="btn btn-outline btn-sm flex items-center gap-2 cursor-pointer">
        <i class="ph ph-sign-out"></i>
        <span>Exit</span>
      </a>
    </div>
  </div>
  <div class="px-4">
    <div class="overflow-x-auto rounded-box border border-base-content/5 bg-base-100">
      <table class="table table-sm">
        <thead>
        <tr>
          <th>Username</th>
          <th>Temporary Password</th>
          <th>Actions</th>
        </tr>
        </thead>
        <tbody>
        {#each users as user (user.id)}
          <tr>
            <td>{user.username}</td>
            <td>
              {#if !user.passwordExpiredAt}
                <div class="text-success flex items-center gap-2">
                  <i class="ph ph-check-circle"></i>
                  <span>Permanent password has been set</span>
                </div>
              {:else if user.passwordExpiredAt < new Date().getTime()}
                <div class="text-error flex items-center gap-2">
                  <i class="ph ph-prohibit"></i>
                  Expired
                </div>
              {:else}
                <div class="text-warning flex items-center gap-2">
                  <i class="ph ph-warning"></i>
                  <span>Expires: {new Date(user.passwordExpiredAt).toLocaleString()}</span>
                </div>
              {/if}
            </td>
            <td class="flex items-center gap-2">
              <button class="underline cursor-pointer" data-test-id="edit-button"
                      onclick={() => {editModal.open(user)}}>
                Edit
              </button>
              <button class="underline cursor-pointer" data-test-id="reset-password-button"
                      onclick={() => {resetPasswordModal.open(user)}}>
                Reset Password
              </button>
              <button class="underline cursor-pointer" data-test-id="delete-button"
                      onclick={() => {deleteModal.open(user)}}>
                Delete
              </button>
            </td>
          </tr>
        {/each}
        </tbody>
      </table>
    </div>
  </div>
</div>


<style lang="scss">
</style>
