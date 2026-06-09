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
    canRefreshMetadata: has('media:refresh'),
    canAssignTags: has('tag:assign'),
    canEditLibraryOnly: has('library:edit'),
    canManageTags: has('tag:manage'),
    canManageCategories: has('category:manage'),
    canViewTasks: has('task:view'),
    canDeleteMedia: has('media:delete'),
    canDeleteSourceFile: has('media:delete_file'),
    canScanLibrary: has('library:scan') || has('library:edit'),
    canExecuteScrape: has('library:edit') || has('task:execute'),
    canEditLibraryPlugins: has('library:edit'),
    canDeleteLibrary: has('library:delete'),
    canViewRecycleBin: has('media:view'),
    canAccessSettings:
      has('system:manage') ||
      has('user:manage') ||
      has('category:manage') ||
      has('task:view') ||
      has('library:create') ||
      has('library:edit'),
  };
}
