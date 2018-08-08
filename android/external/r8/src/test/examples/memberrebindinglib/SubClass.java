// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package memberrebindinglib;

public abstract class SubClass extends SuperClass implements ImplementedInLibrary {
  public int aMethodThatReturnsOne() {
    return 1;
  }

  public int aMethodThatReturnsTwo() {
    return 2;
  }

  public int aMethodThatReturnsFour() {
    return 4;
  }
}
