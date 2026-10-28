package androidx.test.runner.lifecycle;
public interface ActivityLifecycleMonitor {
    void addLifecycleCallback(ActivityLifecycleCallback cb);
    void removeLifecycleCallback(ActivityLifecycleCallback cb);
    Stage getLifecycleStageOf(android.app.Activity a);
    java.util.Collection<android.app.Activity> getActivitiesInStage(Stage s);
}
