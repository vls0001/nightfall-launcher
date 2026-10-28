package androidx.test.platform.app;

import android.app.Instrumentation;
import android.os.Bundle;

/** Minimal stand-in for the androidx.test class Robolectric registers its instrumentation with. */
public final class InstrumentationRegistry {
    private static Instrumentation instrumentation;
    private static Bundle arguments = new Bundle();

    public static void registerInstance(Instrumentation i, Bundle args) {
        instrumentation = i;
        arguments = args == null ? new Bundle() : args;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }

    public static Bundle getArguments() {
        return arguments;
    }
}
