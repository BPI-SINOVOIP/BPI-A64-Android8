// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package memberrebinding3;

public class Memberrebinding extends ClassAtBottomOfChain {

  @Override
  void bottomMethod() {

  }

  @Override
  void middleMethod() {

  }

  @Override
  void topMethod() {

  }

  private void test() {
    super.bottomMethod();
    super.middleMethod();
    super.topMethod();
  }

  public static void main(String[] args) {
    new Memberrebinding().test();
  }
}
