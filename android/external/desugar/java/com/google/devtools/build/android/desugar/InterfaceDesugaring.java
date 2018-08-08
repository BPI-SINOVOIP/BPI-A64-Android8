// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android.desugar;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

/**
 * Visitor that moves methods with bodies from interfaces into a companion class and rewrites
 * call sites accordingly (which is only needed for static interface methods).  Default methods
 * are kept as abstract methods with all their annotations.
 *
 * <p>Any necessary companion classes will be added to the given {@link GeneratedClassStore}.  It's
 * the caller's responsibility to write those out.
 *
 * <p>Relies on {@link DefaultMethodClassFixer} to stub in method bodies for moved default methods.
 * Assumes that lambdas are already desugared.  Ignores bridge methods, which are handled specially.
 */
class InterfaceDesugaring extends ClassVisitor {

  static final String COMPANION_SUFFIX = "$$CC";

  private final ClassReaderFactory bootclasspath;
  private final GeneratedClassStore store;

  private String internalName;
  private int bytecodeVersion;
  private int accessFlags;
  @Nullable private ClassVisitor companion;

  public InterfaceDesugaring(ClassVisitor dest, ClassReaderFactory bootclasspath,
      GeneratedClassStore store) {
    super(Opcodes.ASM5, dest);
    this.bootclasspath = bootclasspath;
    this.store = store;
  }

  @Override
  public void visit(
      int version,
      int access,
      String name,
      String signature,
      String superName,
      String[] interfaces) {
    companion = null;
    internalName = name;
    bytecodeVersion = version;
    accessFlags = access;
    super.visit(version, access, name, signature, superName, interfaces);
  }

  @Override
  public void visitEnd() {
    if (companion != null) {
      companion.visitEnd();
    }
    super.visitEnd();
  }

  @Override
  public MethodVisitor visitMethod(
      int access, String name, String desc, String signature, String[] exceptions) {
    MethodVisitor result;
    if (BitFlags.isSet(accessFlags, Opcodes.ACC_INTERFACE)
        && BitFlags.noneSet(access, Opcodes.ACC_ABSTRACT | Opcodes.ACC_BRIDGE)
        && !"<clinit>".equals(name)) {
      checkArgument(BitFlags.noneSet(access, Opcodes.ACC_NATIVE), "Forbidden per JLS ch 9.4");

      boolean isLambdaBody =
          name.startsWith("lambda$") && BitFlags.isSet(access, Opcodes.ACC_SYNTHETIC);
      if (isLambdaBody) {
        access &= ~Opcodes.ACC_PUBLIC; // undo visibility change from LambdaDesugaring
        // Rename lambda method to reflect the new owner.  Not doing so confuses LambdaDesugaring
        // if it's run over this class again.
        name += COMPANION_SUFFIX;
      }
      if (BitFlags.isSet(access, Opcodes.ACC_STATIC)) {
        // Completely move static interface methods, which requires rewriting call sites
        result =
            companion()
                .visitMethod(access & ~Opcodes.ACC_PRIVATE, name, desc, signature, exceptions);
      } else {
        MethodVisitor abstractDest;
        if (isLambdaBody) {
          // Completely move lambda bodies, which requires rewriting call sites
          access &= ~Opcodes.ACC_PRIVATE;
          abstractDest = null;
        } else {
          // Make default methods abstract but move their implementation into a static method with
          // corresponding signature.  Doesn't require callsite rewriting but implementing classes
          // may need to implement default methods explicitly.
          checkArgument(BitFlags.noneSet(access, Opcodes.ACC_PRIVATE),
              "Unexpected private interface method %s.%s : %s", name, internalName, desc);
          abstractDest = super.visitMethod(
              access | Opcodes.ACC_ABSTRACT, name, desc, signature, exceptions);
        }

        // TODO(b/37110951): adjust signature with explicit receiver type, which may be generic
        MethodVisitor codeDest =
            companion()
                .visitMethod(
                    access | Opcodes.ACC_STATIC,
                    name,
                    companionDefaultMethodDescriptor(internalName, desc),
                    (String) null, // drop signature, since given one doesn't include the new param
                    exceptions);

        result = abstractDest != null ? new MultiplexAnnotations(codeDest, abstractDest) : codeDest;
      }
    } else {
      result = super.visitMethod(access, name, desc, signature, exceptions);
    }
    return result != null ? new InterfaceInvocationRewriter(result) : null;
  }

  /**
   * Returns the descriptor of a static method for an instance method with the given receiver and
   * description, simply by pre-pending the given descriptor's parameter list with the given
   * receiver type.
   */
  static String companionDefaultMethodDescriptor(String interfaceName, String desc) {
    Type type = Type.getMethodType(desc);
    Type[] companionArgs = new Type[type.getArgumentTypes().length + 1];
    companionArgs[0] = Type.getObjectType(interfaceName);
    System.arraycopy(type.getArgumentTypes(), 0, companionArgs, 1, type.getArgumentTypes().length);
    return Type.getMethodDescriptor(type.getReturnType(), companionArgs);
  }

  private ClassVisitor companion() {
    if (companion == null) {
      checkState(BitFlags.isSet(accessFlags, Opcodes.ACC_INTERFACE));
      String companionName = internalName + COMPANION_SUFFIX;

      companion = store.add(companionName);
      companion.visit(
          bytecodeVersion,
          // Companion class must be public so moved methods can be called from anywhere
          (accessFlags | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PUBLIC) & ~Opcodes.ACC_INTERFACE,
          companionName,
          (String) null, // signature
          "java/lang/Object",
          new String[0]);
    }
    return companion;
  }

  /**
   * Rewriter for calls to static interface methods and super calls to default methods, unless
   * they're part of the bootclasspath, as well as all lambda body methods.  Keeps calls to
   * interface methods declared in the bootclasspath as-is (but note that these would presumably
   * fail on devices without those methods).
   */
  private class InterfaceInvocationRewriter extends MethodVisitor {

    public InterfaceInvocationRewriter(MethodVisitor dest) {
      super(Opcodes.ASM5, dest);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
      // Assume that any static interface methods on the classpath are moved
      if (itf) {
        if (name.startsWith("lambda$")) {
          // Redirect lambda invocations to completely remove all lambda methods from interfaces.
          checkArgument(!owner.endsWith(COMPANION_SUFFIX),
              "%s shouldn't consider %s an interface", internalName, owner);
          checkArgument(!bootclasspath.isKnown(owner)); // must be in current input
          if (opcode == Opcodes.INVOKEINTERFACE) {
            opcode = Opcodes.INVOKESTATIC;
            desc = companionDefaultMethodDescriptor(owner, desc);
          } else {
            checkArgument(opcode == Opcodes.INVOKESTATIC,
                "Unexpected opcode %s to invoke %s.%s", opcode, owner, name);
          }
          // Reflect that InterfaceDesugaring moves and renames the lambda body method
          owner += COMPANION_SUFFIX;
          name += COMPANION_SUFFIX;
          checkState(name.equals(LambdaDesugaring.uniqueInPackage(owner, name)),
              "Unexpected lambda body method name %s for %s", name, owner);
          itf = false;
        } else if ((opcode == Opcodes.INVOKESTATIC || opcode == Opcodes.INVOKESPECIAL)
            && !bootclasspath.isKnown(owner)) {
          checkArgument(!owner.endsWith(COMPANION_SUFFIX),
              "%s shouldn't consider %s an interface", internalName, owner);
          if (opcode == Opcodes.INVOKESPECIAL) {
            // Turn Interface.super.m() into Interface$$CC.m(receiver)
            opcode = Opcodes.INVOKESTATIC;
            desc = companionDefaultMethodDescriptor(owner, desc);
          }
          owner += COMPANION_SUFFIX;
          itf = false;
        }
      }
      super.visitMethodInsn(opcode, owner, name, desc, itf);
    }
  }

  /**
   * Method visitor that behaves like a passthrough but additionally duplicates all annotations
   * into a second given {@link MethodVisitor}.
   */
  private static class MultiplexAnnotations extends MethodVisitor {

    private final MethodVisitor annotationOnlyDest;

    public MultiplexAnnotations(@Nullable MethodVisitor dest, MethodVisitor annotationOnlyDest) {
      super(Opcodes.ASM5, dest);
      this.annotationOnlyDest = annotationOnlyDest;
    }

    @Override
    public void visitParameter(String name, int access) {
      super.visitParameter(name, access);
      annotationOnlyDest.visitParameter(name, access);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(
        int typeRef, TypePath typePath, String desc, boolean visible) {
      AnnotationVisitor dest = super.visitTypeAnnotation(typeRef, typePath, desc, visible);
      AnnotationVisitor annoDest =
          annotationOnlyDest.visitTypeAnnotation(typeRef, typePath, desc, visible);
      return new MultiplexAnnotationVisitor(dest, annoDest);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
      AnnotationVisitor dest = super.visitParameterAnnotation(parameter, desc, visible);
      AnnotationVisitor annoDest =
          annotationOnlyDest.visitParameterAnnotation(parameter, desc, visible);
      return new MultiplexAnnotationVisitor(dest, annoDest);
    }
  }

  /**
   * Annotation visitor that recursively passes the visited annotations to any number of given
   * {@link AnnotationVisitor}s.
   */
  private static class MultiplexAnnotationVisitor extends AnnotationVisitor {

    private final AnnotationVisitor[] moreDestinations;

    public MultiplexAnnotationVisitor(@Nullable AnnotationVisitor dest,
        AnnotationVisitor... moreDestinations) {
      super(Opcodes.ASM5, dest);
      this.moreDestinations = moreDestinations;
    }

    @Override
    public void visit(String name, Object value) {
      super.visit(name, value);
      for (AnnotationVisitor dest : moreDestinations) {
        dest.visit(name, value);
      }
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      super.visitEnum(name, desc, value);
      for (AnnotationVisitor dest : moreDestinations) {
        dest.visitEnum(name, desc, value);
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String name, String desc) {
      AnnotationVisitor[] subVisitors = new AnnotationVisitor[moreDestinations.length];
      AnnotationVisitor dest = super.visitAnnotation(name, desc);
      for (int i = 0; i < subVisitors.length; ++i) {
        subVisitors[i] = moreDestinations[i].visitAnnotation(name, desc);
      }
      return new MultiplexAnnotationVisitor(dest, subVisitors);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      AnnotationVisitor[] subVisitors = new AnnotationVisitor[moreDestinations.length];
      AnnotationVisitor dest = super.visitArray(name);
      for (int i = 0; i < subVisitors.length; ++i) {
        subVisitors[i] = moreDestinations[i].visitArray(name);
      }
      return new MultiplexAnnotationVisitor(dest, subVisitors);
    }

    @Override
    public void visitEnd() {
      super.visitEnd();
      for (AnnotationVisitor dest : moreDestinations) {
        dest.visitEnd();
      }
    }
  }
}
