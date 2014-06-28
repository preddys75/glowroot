/*
 * Copyright 2012-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.weaving;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.RemappingMethodAdapter;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
class WeavingClassVisitor extends ClassVisitor {

    private static final Logger logger = LoggerFactory.getLogger(WeavingClassVisitor.class);

    private static final CharMatcher traceMetricCharMatcher =
            CharMatcher.JAVA_LETTER_OR_DIGIT.or(CharMatcher.is(' '));

    private static final Set<String> invalidTraceMetricSet = Sets.newConcurrentHashSet();

    // this field is just a @NonNull version of the field with the same name in the super class to
    // help with null flow analysis
    private final ClassVisitor cv;

    private final ParsedTypeClassVisitor parsedTypeClassVisitor;

    private final boolean traceMetricWrapperMethods;

    @MonotonicNonNull
    private Type type;

    private boolean nothingAtAllToWeave;

    private int innerMethodCounter;

    // these are for handling class and method metas
    private boolean maybeHasMetas;
    @MonotonicNonNull
    private MethodVisitor clinitMethodVisitor;
    private final Set<Class<?>> classMetas = Sets.newHashSet();
    private final Set<MethodMetaInstance> methodMetaInstances = Sets.newHashSet();
    private int methodMetaCounter;

    public WeavingClassVisitor(ClassVisitor cv, ImmutableList<Advice> advisors,
            ImmutableList<MixinType> mixinTypes, @Nullable ClassLoader loader,
            ParsedTypeCache parsedTypeCache, @Nullable CodeSource codeSource,
            boolean traceMetricWrapperMethods) {
        super(ASM5, cv);
        this.cv = cv;
        parsedTypeClassVisitor = new ParsedTypeClassVisitor(advisors, mixinTypes, loader,
                parsedTypeCache, codeSource);
        this.traceMetricWrapperMethods = traceMetricWrapperMethods;
    }

    @Override
    public void visit(int version, int access, String name, @Nullable String signature,
            @Nullable String superName, String/*@Nullable*/[] interfaceNamesNullable) {
        parsedTypeClassVisitor.visit(version, access, name, signature, superName,
                interfaceNamesNullable);
        if (parsedTypeClassVisitor.isNothingInteresting()
                && parsedTypeClassVisitor.getMatchedMixinTypes().isEmpty()) {
            // performance optimization
            nothingAtAllToWeave = true;
            parsedTypeClassVisitor.visitEndReturningParsedType();
            // not great to use exception for control flow, but no other way to abort weaving here
            // at least save the cost of exception instantiation by re-using exception instance
            throw AbortWeavingException.INSTANCE;
        }
        nothingAtAllToWeave = Modifier.isInterface(access);
        if (nothingAtAllToWeave) {
            return;
        }
        for (AdviceMatcher adviceMatcher : parsedTypeClassVisitor.getAdviceMatchers()) {
            if (!adviceMatcher.getAdvice().getClassMetas().isEmpty()
                    || !adviceMatcher.getAdvice().getMethodMetas().isEmpty()) {
                maybeHasMetas = true;
                break;
            }
        }
        if (!maybeHasMetas) {
            outer: for (ParsedType parsedType : parsedTypeClassVisitor.getSuperParsedTypes()) {
                for (ParsedMethod parsedMethod : parsedType.getParsedMethods()) {
                    for (Advice advice : parsedMethod.getAdvisors()) {
                        if (!advice.getClassMetas().isEmpty()
                                || !advice.getMethodMetas().isEmpty()) {
                            maybeHasMetas = true;
                            break outer;
                        }
                    }
                }
            }
        }
        type = Type.getObjectType(name);
        String/*@Nullable*/[] interfacesIncludingMixins =
                getInterfacesIncludingMixins(interfaceNamesNullable,
                        parsedTypeClassVisitor.getMatchedMixinTypes());
        super.visit(version, access, name, signature, superName, interfacesIncludingMixins);
    }

    @Override
    @Nullable
    public MethodVisitor visitMethod(int access, String name, String desc,
            @Nullable String signature, String/*@Nullable*/[] exceptions) {
        List<Advice> matchingAdvisors = parsedTypeClassVisitor.visitMethodReturningAdvisors(access,
                name, desc, signature, exceptions);
        if (nothingAtAllToWeave) {
            return null;
        }
        checkNotNull(type); // type is non null if there is something to weave
        if (Modifier.isAbstract(access) || Modifier.isNative(access)
                || (access & ACC_SYNTHETIC) != 0) {
            // don't try to weave abstract, native and synthetic methods
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
        if (name.equals("<clinit>") && maybeHasMetas) {
            clinitMethodVisitor =
                    checkNotNull(cv.visitMethod(access, name, desc, signature, exceptions));
            clinitMethodVisitor.visitMethodInsn(INVOKESTATIC, type.getInternalName(),
                    "original$clinit$glowroot", desc, false);
            return cv.visitMethod(access, "original$clinit$glowroot", desc, signature, exceptions);
        }
        if (name.equals("<init>") && !parsedTypeClassVisitor.getMatchedMixinTypes().isEmpty()) {
            return visitInitWithMixin(access, name, desc, signature, exceptions, matchingAdvisors);
        }
        if (matchingAdvisors.isEmpty()) {
            return cv.visitMethod(access, name, desc, signature, exceptions);
        }
        return visitMethodWithAdvice(access, name, desc, signature, exceptions, matchingAdvisors);
    }

    @Override
    public void visitEnd() {
        ParsedType parsedType = parsedTypeClassVisitor.visitEndReturningParsedType();
        if (nothingAtAllToWeave) {
            return;
        }
        checkNotNull(type); // type is non null if there is something to weave
        for (MixinType mixinType : parsedTypeClassVisitor.getMatchedMixinTypes()) {
            addMixin(mixinType);
        }
        handleInheritedMethodsThatNowFulfillAdvice(parsedType);
        // handle static initializer at end, since handleInheritedMethodsThatNowFulfillAdvice()
        // above could add new classMetas
        if (!classMetas.isEmpty() || !methodMetaInstances.isEmpty()) {
            if (clinitMethodVisitor == null) {
                clinitMethodVisitor =
                        checkNotNull(cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null));
            }
            clinitMethodVisitor.visitCode();
            for (Class<?> classMeta : classMetas) {
                String classMetaInternalName = Type.getInternalName(classMeta);
                String classMetaFieldName =
                        "glowroot$class$meta$" + classMetaInternalName.replace('/', '$');
                FieldVisitor fv = cv.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC,
                        classMetaFieldName, "L" + classMetaInternalName + ";", null, null);
                fv.visitEnd();
                clinitMethodVisitor.visitTypeInsn(NEW, classMetaInternalName);
                clinitMethodVisitor.visitInsn(DUP);
                clinitMethodVisitor.visitLdcInsn(type);
                clinitMethodVisitor.visitMethodInsn(INVOKESPECIAL, classMetaInternalName,
                        "<init>", "(Ljava/lang/Class;)V", false);
                clinitMethodVisitor.visitFieldInsn(PUTSTATIC, type.getInternalName(),
                        classMetaFieldName, "L" + classMetaInternalName + ";");
            }
            for (MethodMetaInstance methodMetaInstance : methodMetaInstances) {
                String methodMetaInternalName =
                        Type.getInternalName(methodMetaInstance.getMethodMeta());
                String methodMetaFieldName = "glowroot$method$meta$"
                        + methodMetaInstance.getUniqueNum() + '$'
                        + methodMetaInternalName.replace('/', '$');
                FieldVisitor fv = cv.visitField(ACC_PRIVATE + ACC_FINAL + ACC_STATIC,
                        methodMetaFieldName, "L" + methodMetaInternalName + ";", null, null);
                fv.visitEnd();
                clinitMethodVisitor.visitTypeInsn(NEW, methodMetaInternalName);
                clinitMethodVisitor.visitInsn(DUP);
                clinitMethodVisitor.visitLdcInsn(type);
                loadType(clinitMethodVisitor, methodMetaInstance.getReturnType());

                clinitMethodVisitor.visitIntInsn(BIPUSH, methodMetaInstance.getArgTypes().size());
                clinitMethodVisitor.visitTypeInsn(ANEWARRAY, "java/lang/Class");
                for (int i = 0; i < methodMetaInstance.getArgTypes().size(); i++) {
                    clinitMethodVisitor.visitInsn(DUP);
                    clinitMethodVisitor.visitIntInsn(BIPUSH, i);
                    loadType(clinitMethodVisitor, methodMetaInstance.getArgTypes().get(i));
                    clinitMethodVisitor.visitInsn(AASTORE);
                }

                clinitMethodVisitor.visitMethodInsn(INVOKESPECIAL, methodMetaInternalName,
                        "<init>", "(Ljava/lang/Class;Ljava/lang/Class;[Ljava/lang/Class;)V", false);
                clinitMethodVisitor.visitFieldInsn(PUTSTATIC, type.getInternalName(),
                        methodMetaFieldName, "L" + methodMetaInternalName + ";");
            }
        }
        if (clinitMethodVisitor != null) {
            clinitMethodVisitor.visitInsn(RETURN);
            clinitMethodVisitor.visitMaxs(0, 0);
            clinitMethodVisitor.visitEnd();
        }
        cv.visitEnd();
    }

    boolean isNothingAtAllToWeave() {
        return nothingAtAllToWeave;
    }

    private static void loadType(MethodVisitor clinitMethodVisitor, Type returnType) {
        switch (returnType.getSort()) {
            case Type.VOID:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Void", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.BOOLEAN:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.CHAR:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Character", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.BYTE:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Byte", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.SHORT:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Short", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.INT:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Integer", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.FLOAT:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Float", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.LONG:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE",
                        "Ljava/lang/Class;");
                break;
            case Type.DOUBLE:
                clinitMethodVisitor.visitFieldInsn(GETSTATIC, "java/lang/Double", "TYPE",
                        "Ljava/lang/Class;");
                break;
            default:
                clinitMethodVisitor.visitLdcInsn(returnType);
        }
    }

    private static String/*@Nullable*/[] getInterfacesIncludingMixins(
            String/*@Nullable*/[] interfaceNamesNullable,
            ImmutableList<MixinType> matchedMixinTypes) {
        if (matchedMixinTypes.isEmpty()) {
            return interfaceNamesNullable;
        }
        Set<String> interfacesIncludingMixins = Sets.newHashSet();
        if (interfaceNamesNullable != null) {
            interfacesIncludingMixins.addAll(Arrays.asList(interfaceNamesNullable));
        }
        for (MixinType matchedMixinType : matchedMixinTypes) {
            for (Class<?> mixinInterface : matchedMixinType.getInterfaces()) {
                interfacesIncludingMixins.add(Type.getInternalName(mixinInterface));
            }
        }
        return Iterables.toArray(interfacesIncludingMixins, String.class);
    }

    @RequiresNonNull("type")
    private MethodVisitor visitInitWithMixin(int access, String name, String desc,
            @Nullable String signature, String/*@Nullable*/[] exceptions,
            List<Advice> matchingAdvisors) {
        int methodMetaUniqueNum = collectClassAndMethodMetas(matchingAdvisors, desc);
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        checkNotNull(mv);
        mv = new InitMixins(mv, access, name, desc, parsedTypeClassVisitor.getMatchedMixinTypes(),
                type);
        for (Advice advice : matchingAdvisors) {
            if (advice.getPointcut().traceMetric().length() != 0) {
                logger.warn("cannot add trace metric to <clinit> or <init> methods at this time");
                break;
            }
        }
        return new WeavingMethodVisitor(mv, access, name, desc, type, matchingAdvisors,
                methodMetaUniqueNum);
    }

    @RequiresNonNull("type")
    private MethodVisitor visitMethodWithAdvice(int access, String name, String desc,
            @Nullable String signature, String/*@Nullable*/[] exceptions,
            Iterable<Advice> matchingAdvisors) {
        int methodMetaUniqueNum = collectClassAndMethodMetas(matchingAdvisors, desc);
        if (traceMetricWrapperMethods && !name.equals("<init>")) {
            String innerWrappedName = wrapWithSyntheticTraceMetricMarkerMethods(access, name, desc,
                    signature, exceptions, matchingAdvisors);
            String methodName = name;
            int methodAccess = access;
            if (innerWrappedName != null) {
                methodName = innerWrappedName;
                methodAccess = ACC_PRIVATE + ACC_FINAL + (access & ACC_STATIC);
            }
            MethodVisitor mv =
                    cv.visitMethod(methodAccess, methodName, desc, signature, exceptions);
            checkNotNull(mv);
            return new WeavingMethodVisitor(mv, methodAccess, methodName, desc, type,
                    matchingAdvisors, methodMetaUniqueNum);
        } else {
            MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
            checkNotNull(mv);
            return new WeavingMethodVisitor(mv, access, name, desc, type, matchingAdvisors,
                    methodMetaUniqueNum);
        }
    }

    private int collectClassAndMethodMetas(Iterable<Advice> matchingAdvisors, String desc) {
        Set<Class<?>> methodMetas = Sets.newHashSet();
        for (Advice matchingAdvice : matchingAdvisors) {
            classMetas.addAll(matchingAdvice.getClassMetas());
            methodMetas.addAll(matchingAdvice.getMethodMetas());
        }
        int methodMetaUniqueNum = 0;
        if (!methodMetas.isEmpty()) {
            methodMetaUniqueNum = ++methodMetaCounter;
            for (Class<?> methodMeta : methodMetas) {
                List<Type> argTypes = Arrays.asList(Type.getArgumentTypes(desc));
                Type returnType = Type.getReturnType(desc);
                methodMetaInstances.add(new MethodMetaInstance(methodMeta, returnType, argTypes,
                        methodMetaUniqueNum));
            }

        }
        return methodMetaUniqueNum;
    }

    // returns null if no synthetic trace metric marker methods were needed
    @RequiresNonNull("type")
    @Nullable
    private String wrapWithSyntheticTraceMetricMarkerMethods(int outerAccess, String outerName,
            String desc, @Nullable String signature, String/*@Nullable*/[] exceptions,
            Iterable<Advice> matchingAdvisors) {
        int innerAccess = ACC_PRIVATE + ACC_FINAL + (outerAccess & ACC_STATIC);
        boolean first = true;
        String currMethodName = outerName;
        for (Advice advice : matchingAdvisors) {
            String traceMetric = advice.getPointcut().traceMetric();
            if (traceMetric.isEmpty()) {
                continue;
            }
            if (!traceMetricCharMatcher.matchesAllOf(traceMetric)) {
                logInvalidTraceMetricWarningOnce(traceMetric);
                traceMetric = traceMetricCharMatcher.negate().replaceFrom(traceMetric, '_');
            }
            String nextMethodName = outerName + "$glowroot$trace$metric$"
                    + traceMetric.replace(' ', '$') + '$' + innerMethodCounter++;
            int access = first ? outerAccess : innerAccess;
            MethodVisitor mv = cv.visitMethod(access, currMethodName, desc, signature, exceptions);
            checkNotNull(mv);
            GeneratorAdapter mg = new GeneratorAdapter(mv, access, nextMethodName, desc);
            if (!Modifier.isStatic(outerAccess)) {
                mg.loadThis();
                mg.loadArgs();
                mg.invokeVirtual(type, new Method(nextMethodName, desc));
            } else {
                mg.loadArgs();
                mg.invokeStatic(type, new Method(nextMethodName, desc));
            }
            mg.returnValue();
            mg.endMethod();
            currMethodName = nextMethodName;
            first = false;
        }
        return first ? null : currMethodName;
    }

    @RequiresNonNull("type")
    private void addMixin(MixinType mixinType) {
        ClassReader cr;
        try {
            cr = new ClassReader(mixinType.getImplementation().getName());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return;
        }
        ClassNode cn = new ClassNode();
        cr.accept(cn, ClassReader.SKIP_FRAMES);
        // SuppressWarnings because generics are explicitly removed from asm binaries
        // see http://forge.ow2.org/tracker/?group_id=23&atid=100023&func=detail&aid=316377
        @SuppressWarnings("unchecked")
        List<FieldNode> fieldNodes = cn.fields;
        for (FieldNode fieldNode : fieldNodes) {
            fieldNode.accept(this);
        }
        // SuppressWarnings because generics are explicitly removed from asm binaries
        @SuppressWarnings("unchecked")
        List<MethodNode> methodNodes = cn.methods;
        for (MethodNode mn : methodNodes) {
            if (mn.name.equals("<init>")) {
                continue;
            }
            // SuppressWarnings because generics are explicitly removed from asm binaries
            @SuppressWarnings("unchecked")
            String[] exceptions = Iterables.toArray(mn.exceptions, String.class);
            MethodVisitor mv = cv.visitMethod(mn.access, mn.name, mn.desc, mn.signature,
                    exceptions);
            checkNotNull(mv);
            mn.accept(new RemappingMethodAdapter(mn.access, mn.desc, mv,
                    new SimpleRemapper(cn.name, type.getInternalName())));
        }
    }

    @RequiresNonNull({"type"})
    private void handleInheritedMethodsThatNowFulfillAdvice(ParsedType parsedType) {
        if (parsedType.isInterface() || parsedType.isAbstract()) {
            return;
        }
        Map<ParsedMethod, Set<Advice>> matchingAdvisorSets = Maps.newHashMap();
        for (ParsedType superParsedType : parsedTypeClassVisitor.getSuperParsedTypes()) {
            if (!superParsedType.isInterface()) {
                continue;
            }
            for (ParsedMethod superParsedMethod : superParsedType.getParsedMethods()) {
                Set<Advice> matchingAdvisorSet = matchingAdvisorSets.get(superParsedMethod);
                if (matchingAdvisorSet == null) {
                    matchingAdvisorSet = Sets.newHashSet();
                    matchingAdvisorSets.put(superParsedMethod, matchingAdvisorSet);
                }
                matchingAdvisorSet.addAll(superParsedMethod.getAdvisors());
            }
        }
        for (ParsedMethod parsedMethod : parsedType.getParsedMethods()) {
            matchingAdvisorSets.remove(parsedMethod);
        }
        for (ParsedType superParsedType : parsedTypeClassVisitor.getSuperParsedTypes()) {
            if (superParsedType.isInterface()) {
                continue;
            }
            for (ParsedMethod superParsedMethod : superParsedType.getParsedMethods()) {
                Set<Advice> matchingAdvisorSet = matchingAdvisorSets.get(superParsedMethod);
                if (matchingAdvisorSet == null) {
                    continue;
                }
                matchingAdvisorSet.removeAll(superParsedMethod.getAdvisors());
            }
        }
        for (Entry<ParsedMethod, Set<Advice>> entry : matchingAdvisorSets.entrySet()) {
            ParsedMethod inheritedMethod = entry.getKey();
            Set<Advice> advisors = entry.getValue();
            if (!advisors.isEmpty()) {
                overrideAndWeaveInheritedMethod(parsedType, inheritedMethod, advisors);
            }
        }
    }

    @RequiresNonNull({"type"})
    private void overrideAndWeaveInheritedMethod(ParsedType parsedType,
            ParsedMethod inheritedMethod, Collection<Advice> matchingAdvisors) {
        String[] exceptions = Iterables.toArray(inheritedMethod.getExceptions(), String.class);
        MethodVisitor mv = visitMethodWithAdvice(ACC_PUBLIC, inheritedMethod.getName(),
                inheritedMethod.getDesc(), inheritedMethod.getSignature(), exceptions,
                matchingAdvisors);
        checkNotNull(mv);
        GeneratorAdapter mg = new GeneratorAdapter(mv, ACC_PUBLIC, inheritedMethod.getName(),
                inheritedMethod.getDesc());
        mg.visitCode();
        mg.loadThis();
        mg.loadArgs();
        String superName = parsedType.getSuperName();
        Type superType;
        if (superName == null) {
            superType = Type.getType(Object.class);
        } else {
            superType = Type.getType(TypeNames.toInternal(superName));
        }
        // method is called invokeConstructor, but should really be called invokeSpecial
        mg.invokeConstructor(superType,
                new Method(inheritedMethod.getName(), inheritedMethod.getDesc()));
        mg.returnValue();
        mg.endMethod();
    }

    private static void logInvalidTraceMetricWarningOnce(String traceMetric) {
        if (invalidTraceMetricSet.add(traceMetric)) {
            logger.warn("trace metric must contain only letters, digits and spaces: {}",
                    traceMetric);
        }
    }

    @SuppressWarnings("serial")
    static class AbortWeavingException extends RuntimeException {
        private static AbortWeavingException INSTANCE = new AbortWeavingException();
        private AbortWeavingException() {}
    }

    private static class InitMixins extends AdviceAdapter {

        private final ImmutableList<MixinType> matchedMixinTypes;
        private final Type type;
        private boolean cascadingConstructor;

        InitMixins(MethodVisitor mv, int access, String name, String desc,
                ImmutableList<MixinType> matchedMixinTypes, Type type) {
            super(ASM5, mv, access, name, desc);
            this.matchedMixinTypes = matchedMixinTypes;
            this.type = type;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                boolean itf) {
            if (name.equals("<init>") && owner.equals(type.getInternalName())) {
                cascadingConstructor = true;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        protected void onMethodExit(int opcode) {
            if (cascadingConstructor) {
                // need to call MixinInit exactly once, so don't call MixinInit at end of cascading
                // constructors
                return;
            }
            for (MixinType mixinType : matchedMixinTypes) {
                String initMethodName = mixinType.getInitMethodName();
                if (initMethodName != null) {
                    loadThis();
                    invokeVirtual(type, new Method(initMethodName, "()V"));
                }
            }
        }
    }

    private static class MethodMetaInstance {

        private final Class<?> methodMeta;
        private final Type returnType;
        private final List<Type> argTypes;
        private final int uniqueNum;

        private MethodMetaInstance(Class<?> methodMeta, Type returnType, List<Type> argTypes,
                int uniqueNum) {
            this.methodMeta = methodMeta;
            this.returnType = returnType;
            this.argTypes = argTypes;
            this.uniqueNum = uniqueNum;
        }

        private Class<?> getMethodMeta() {
            return methodMeta;
        }

        private Type getReturnType() {
            return returnType;
        }

        private List<Type> getArgTypes() {
            return argTypes;
        }

        private int getUniqueNum() {
            return uniqueNum;
        }
    }
}
