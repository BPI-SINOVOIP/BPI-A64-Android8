package foo;

import static android.arch.lifecycle.Lifecycle.Event.ON_STOP;

import android.arch.lifecycle.Lifecycle.Event;
import android.arch.lifecycle.OnLifecycleEvent;

public class InvalidFirstArg1 {
    @OnLifecycleEvent(ON_STOP)
    public void onStop(Event event) {
    }
}
