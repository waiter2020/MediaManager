export default function access(initialState: { currentUser?: API.CurrentUser } | undefined) {
  const perms = initialState?.currentUser?.permissions ?? [];
  const has = (p: string) => perms.includes(p);

  return {
    canManageSystem: has('system:manage'),
    canManageUsers: has('user:manage'),
    canManageLibrary: has('library:create') || has('library:edit'),
    canViewLibrary: has('library:view'),
    canViewMedia: has('media:view'),
    canPlayMedia: has('media:play'),
    canEditMetadata: has('media:edit_metadata'),
    canManageTags: has('tag:manage'),
    canManageCategories: has('category:manage'),
    canViewTasks: has('task:view'),
    canDeleteMedia: has('media:delete'),
  };
}
