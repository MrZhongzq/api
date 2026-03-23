package io.github.libxposed.api;

import androidx.annotation.NonNull;

/**
 * Super class which all Xposed module entry classes should extend.<br/>
 * Entry classes will be instantiated exactly once for each process. Modules should not do initialization
 * work before {@link #onModuleLoaded(ModuleLoadedParam)} is called.
 */
@SuppressWarnings("unused")
public abstract class XposedModule extends XposedInterfaceWrapper implements XposedModuleInterface {

    /**
     * Constructor compatible with API 100 style modules.
     * This constructor attaches the framework interface and triggers the initial onModuleLoaded callback.
     *
     * @param xi   The framework interface
     * @param param The module loaded parameters
     */
    @SuppressWarnings("unused")
    public XposedModule(@NonNull XposedInterface xi, @NonNull ModuleLoadedParam param) {
        attachFramework(xi);
        onModuleLoaded(param);
    }

    /**
     * Default no-arg constructor for API 101 style modules.
     * The framework will call {@link #attachFramework(XposedInterface)} separately,
     * followed by {@link #onModuleLoaded(ModuleLoadedParam)}.
     */
    @SuppressWarnings("unused")
    public XposedModule() {
    }
}
