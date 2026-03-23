package io.github.libxposed.api;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.List;

import io.github.libxposed.api.error.HookFailedError;

/**
 * Xposed interface for modules to operate on application processes.
 * This is a merged interface supporting both API 100 and API 101.
 */
@SuppressWarnings("unused")
public interface XposedInterface {

    // ==================== API 101 Constants ====================

    /**
     * Behavior changes: all modules
     * <ul>
     * <li> Modules cannot be injected into zygote;
     * they are only loaded within the process of the scope.</li>
     * </ul>
     * Behavior changes: Modules targeting 101 or higher
     * <ul>
     * <li>This is the first API version.</li>
     * </ul>
     */
    int API_101 = 101;

    /**
     * The API version of this <b>library</b>. This is a static value for the framework.
     * Modules should use {@link #getApiVersion()} to check the API version at runtime.
     */
    int LIB_API = API_101;

    // ==================== API 100 Constants (backward compat) ====================

    /**
     * SDK API version (API 100 compat).
     */
    int API = 100;

    int FRAMEWORK_PRIVILEGE_ROOT = 0;
    int FRAMEWORK_PRIVILEGE_CONTAINER = 1;
    int FRAMEWORK_PRIVILEGE_APP = 2;
    int FRAMEWORK_PRIVILEGE_EMBEDDED = 3;

    // ==================== Shared Constants ====================

    int PRIORITY_DEFAULT = 50;
    int PRIORITY_LOWEST = Integer.MIN_VALUE;
    int PRIORITY_HIGHEST = Integer.MAX_VALUE;

    // ==================== API 101 Framework Properties ====================

    long PROP_CAP_SYSTEM = 1L;
    long PROP_CAP_REMOTE = 1L << 1;
    long PROP_RT_API_PROTECTION = 1L << 2;

    // ==================== API 100 Inner Interfaces ====================

    /**
     * Contextual interface for before invocation callbacks (API 100).
     */
    interface BeforeHookCallback {
        @NonNull Member getMember();
        @Nullable Object getThisObject();
        @NonNull Object[] getArgs();
        void returnAndSkip(@Nullable Object result);
        void throwAndSkip(@Nullable Throwable throwable);
    }

    /**
     * Contextual interface for after invocation callbacks (API 100).
     */
    interface AfterHookCallback {
        @NonNull Member getMember();
        @Nullable Object getThisObject();
        @NonNull Object[] getArgs();
        @Nullable Object getResult();
        @Nullable Throwable getThrowable();
        boolean isSkipped();
        void setResult(@Nullable Object result);
        void setThrowable(@Nullable Throwable throwable);
    }

    /**
     * Interface for canceling a hook (API 100).
     */
    interface MethodUnhooker<T> {
        @NonNull T getOrigin();
        void unhook();
    }

    // ==================== API 101 Inner Interfaces ====================

    interface Invoker<T extends Invoker<T, U>, U extends Executable> {
        sealed interface Type permits Type.Origin, Type.Chain {
            Origin ORIGIN = new Origin();
            record Origin() implements Type {}
            record Chain(int maxPriority) implements Type {
                public static final Chain FULL = new Chain(PRIORITY_HIGHEST);
            }
        }
        T setType(@NonNull Type type);
        Object invoke(Object thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;
        Object invokeSpecial(@NonNull Object thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;
    }

    interface CtorInvoker<T> extends Invoker<CtorInvoker<T>, Constructor<T>> {
        @NonNull T newInstance(Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException;
        @NonNull <U> U newInstanceSpecial(@NonNull Class<U> subClass, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException;
    }

    interface Chain {
        @NonNull Executable getExecutable();
        Object getThisObject();
        @NonNull List<Object> getArgs();
        Object getArg(int index) throws IndexOutOfBoundsException, ClassCastException;
        Object proceed() throws Throwable;
        Object proceed(@NonNull Object[] args) throws Throwable;
        Object proceedWith(@NonNull Object thisObject) throws Throwable;
        Object proceedWith(@NonNull Object thisObject, @NonNull Object[] args) throws Throwable;
    }

    /**
     * Hooker interface. API 101 modules implement intercept(Chain).
     * API 100 modules implement this as a marker and provide static before()/after() methods.
     */
    interface Hooker {
        /**
         * Intercepts a method/constructor call (API 101).
         * Default implementation just proceeds, so API 100 hooker classes don't break.
         */
        default Object intercept(@NonNull Chain chain) throws Throwable {
            return chain.proceed();
        }
    }

    interface HookHandle {
        @NonNull Executable getExecutable();
        void unhook();
    }

    enum ExceptionMode {
        DEFAULT, PROTECTIVE, PASSTHROUGH,
    }

    interface HookBuilder {
        HookBuilder setPriority(int priority);
        HookBuilder setExceptionMode(@NonNull ExceptionMode mode);
        @NonNull HookHandle intercept(@NonNull Hooker hooker);
    }

    // ==================== API 101 Methods ====================

    default int getApiVersion() { return LIB_API; }

    @NonNull String getFrameworkName();
    @NonNull String getFrameworkVersion();
    long getFrameworkVersionCode();
    long getFrameworkProperties();

    @NonNull HookBuilder hook(@NonNull Executable origin);
    @NonNull HookBuilder hookClassInitializer(@NonNull Class<?> origin);

    boolean deoptimize(@NonNull Executable executable);

    @NonNull Invoker<?, Method> getInvoker(@NonNull Method method);
    @NonNull <T> CtorInvoker<T> getInvoker(@NonNull Constructor<T> constructor);

    void log(int priority, @Nullable String tag, @NonNull String msg);
    void log(int priority, @Nullable String tag, @NonNull String msg, @Nullable Throwable tr);

    @NonNull ApplicationInfo getModuleApplicationInfo();

    @NonNull SharedPreferences getRemotePreferences(@NonNull String group);
    @NonNull String[] listRemoteFiles();
    @NonNull ParcelFileDescriptor openRemoteFile(@NonNull String name) throws FileNotFoundException;

    // ==================== API 100 Methods (backward compat, default impls) ====================

    /**
     * Gets the framework privilege (API 100).
     */
    default int getFrameworkPrivilege() {
        return FRAMEWORK_PRIVILEGE_EMBEDDED;
    }

    /**
     * Hook a method with default priority (API 100).
     */
    @NonNull
    default MethodUnhooker<Method> hook(@NonNull Method origin, @NonNull Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("API 100 hook not implemented");
    }

    /**
     * Hook a method with specified priority (API 100).
     */
    @NonNull
    default MethodUnhooker<Method> hook(@NonNull Method origin, int priority, @NonNull Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("API 100 hook not implemented");
    }

    /**
     * Hook a constructor with default priority (API 100).
     */
    @NonNull
    default <T> MethodUnhooker<Constructor<T>> hook(@NonNull Constructor<T> origin, @NonNull Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("API 100 hook not implemented");
    }

    /**
     * Hook a constructor with specified priority (API 100).
     */
    @NonNull
    default <T> MethodUnhooker<Constructor<T>> hook(@NonNull Constructor<T> origin, int priority, @NonNull Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("API 100 hook not implemented");
    }

    /**
     * Hook the static initializer with default priority (API 100).
     */
    @NonNull
    default <T> MethodUnhooker<Constructor<T>> hookClassInitializer(@NonNull Class<T> origin, @NonNull Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("API 100 hookClassInitializer not implemented");
    }

    /**
     * Hook the static initializer with specified priority (API 100).
     */
    @NonNull
    default <T> MethodUnhooker<Constructor<T>> hookClassInitializer(@NonNull Class<T> origin, int priority, @NonNull Class<? extends Hooker> hooker) {
        throw new UnsupportedOperationException("API 100 hookClassInitializer not implemented");
    }

    /**
     * Deoptimize a method (API 100).
     */
    default boolean deoptimize(@NonNull Method method) {
        return deoptimize((Executable) method);
    }

    /**
     * Deoptimize a constructor (API 100).
     */
    default <T> boolean deoptimize(@NonNull Constructor<T> constructor) {
        return deoptimize((Executable) constructor);
    }

    @Nullable
    default Object invokeOrigin(@NonNull Method method, @Nullable Object thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException {
        throw new UnsupportedOperationException("API 100 invokeOrigin not implemented");
    }

    default <T> void invokeOrigin(@NonNull Constructor<T> constructor, @NonNull T thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException {
        throw new UnsupportedOperationException("API 100 invokeOrigin not implemented");
    }

    @Nullable
    default Object invokeSpecial(@NonNull Method method, @NonNull Object thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException {
        throw new UnsupportedOperationException("API 100 invokeSpecial not implemented");
    }

    default <T> void invokeSpecial(@NonNull Constructor<T> constructor, @NonNull T thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException {
        throw new UnsupportedOperationException("API 100 invokeSpecial not implemented");
    }

    @NonNull
    default <T> T newInstanceOrigin(@NonNull Constructor<T> constructor, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException {
        throw new UnsupportedOperationException("API 100 newInstanceOrigin not implemented");
    }

    @NonNull
    default <T, U> U newInstanceSpecial(@NonNull Constructor<T> constructor, @NonNull Class<U> subClass, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException {
        throw new UnsupportedOperationException("API 100 newInstanceSpecial not implemented");
    }

    /**
     * @deprecated Use {@link #log(int, String, String, Throwable)} instead.
     */
    @Deprecated
    default void log(@NonNull String message) {
        log(android.util.Log.INFO, null, message, null);
    }

    /**
     * @deprecated Use {@link #log(int, String, String, Throwable)} instead.
     */
    @Deprecated
    default void log(@NonNull String message, @NonNull Throwable throwable) {
        log(android.util.Log.ERROR, null, message, throwable);
    }

    @Nullable
    default Object parseDex(@NonNull ByteBuffer dexData, boolean includeAnnotations) throws IOException {
        return null;
    }

    /**
     * Gets the application info (API 100 compat, delegates to getModuleApplicationInfo).
     */
    @NonNull
    default ApplicationInfo getApplicationInfo() {
        return getModuleApplicationInfo();
    }
}
