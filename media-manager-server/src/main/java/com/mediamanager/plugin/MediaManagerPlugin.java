package com.mediamanager.plugin;

public interface MediaManagerPlugin {
    String id();
    PluginKind kind();
    String displayName();
    default int defaultPriority() {
        return 100;
    }
}
