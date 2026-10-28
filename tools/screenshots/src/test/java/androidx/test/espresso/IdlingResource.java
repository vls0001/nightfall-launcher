package androidx.test.espresso;
public interface IdlingResource {
    String getName();
    boolean isIdleNow();
    void registerIdleTransitionCallback(ResourceCallback cb);
    interface ResourceCallback { void onTransitionToIdle(); }
}
