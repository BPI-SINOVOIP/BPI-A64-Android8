/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package foo;

import android.arch.lifecycle.GenericLifecycleObserver;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import java.lang.Override;
import javax.annotation.Generated;

@Generated("android.arch.lifecycle.LifecycleProcessor")
public class OnAnyMethod_LifecycleAdapter implements GenericLifecycleObserver {
  final OnAnyMethod mReceiver;

  OnAnyMethod_LifecycleAdapter(OnAnyMethod receiver) {
    this.mReceiver = receiver;
  }

  @Override
  public void onStateChanged(LifecycleOwner owner, Lifecycle.Event event) {
    if (event == Lifecycle.Event.ON_STOP) {
      mReceiver.onStop(owner);
    }
    mReceiver.any(owner);
    mReceiver.any(owner,event);
  }
}
