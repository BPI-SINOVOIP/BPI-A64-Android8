package foo;

import static android.arch.lifecycle.Lifecycle.Event.ON_STOP;

import android.arch.lifecycle.Lifecycle.Event;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;

public class TooManyArgs2 {
    @OnLifecycleEvent(ON_STOP)
    public void onStop(LifecycleOwner provider, Event event) {
    }
}
