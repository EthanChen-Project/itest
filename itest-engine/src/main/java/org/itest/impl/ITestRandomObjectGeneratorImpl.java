/**
 * <pre>
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Grzegorz Kochański
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * </pre>
 */
package org.itest.impl;

import org.apache.commons.lang.RandomStringUtils;
import org.itest.ITestConfig;
import org.itest.ITestConstants;
import org.itest.ITestContext;
import org.itest.ITestSuperObject;
import org.itest.annotation.ITestFieldAssignment;
import org.itest.annotation.ITestFieldClass;
import org.itest.exception.ITestException;
import org.itest.exception.ITestIllegalArgumentException;
import org.itest.exception.ITestInitializationException;
import org.itest.exception.ITestMethodExecutionException;
import org.itest.exception.ITestPossibleCycleException;
import org.itest.generator.ITestObjectGenerator;
import org.itest.impl.util.ITestUtils;
import org.itest.param.ITestParamState;
import org.itest.util.reflection.ITestFieldUtil;
import org.itest.util.reflection.ITestFieldUtil.FieldHolder;
import org.itest.util.reflection.ITestTypeUtil;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ITestRandomObjectGeneratorImpl implements ITestObjectGenerator {
    private static final int MAX_DEPTH = 20;

    private static final Class<?> PROXY_CLASS = Proxy.class;

    public static final ITestParamState EMPTY_STATE = new ITestParamStateImpl() {
        {
            elements = Collections.EMPTY_MAP;
        }
    };

    private static final Random random = new Random();

    private static int RANDOM_MAX = 5;

    private static int RANDOM_MIN = 2;

    private final ITestConfig iTestConfig;


    public ITestRandomObjectGeneratorImpl(ITestConfig iTestConfig) {
        this.iTestConfig = iTestConfig;
    }

    @Override
    public Object generate(Type type, ITestParamState initParam, Map<String, Type> itestGenericMap, ITestContext iTestContext) {
        try {
            return generateRandom(type, itestGenericMap, iTestContext);
        } catch (ITestException e) {
            e.addPrefix(type.toString());
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T generateRandom(Class<T> clazz, Map<String, Type> itestGenericMap, ITestContext iTestContext) {
        Object res;
        ITestParamState iTestState = iTestContext.getCurrentParam();
        if (null != iTestState && null == iTestState.getNames()) {
            res = iTestConfig.getITestValueConverter().convert(clazz, iTestState.getValue());
        } else if (Void.class == clazz || void.class == clazz) {
            res = null;
        } else if (String.class == clazz) {
            res = RandomStringUtils.randomAlphanumeric(20);
        } else if (Long.class == clazz || long.class == clazz) {
            res = new Long(random.nextLong());
        } else if (Integer.class == clazz || int.class == clazz) {
            res = new Integer(random.nextInt());
        } else if (Boolean.class == clazz || boolean.class == clazz) {
            res = random.nextBoolean() ? Boolean.TRUE : Boolean.FALSE;
        } else if (Date.class == clazz) {
            res = new Date(random.nextLong());
        } else if (Double.class == clazz || double.class == clazz) {
            res = random.nextDouble();
        } else if (Float.class == clazz || float.class == clazz) {
            res = random.nextFloat();
        } else if (Character.class == clazz || char.class == clazz) {
            res = RandomStringUtils.random(1).charAt(0);
        } else if (clazz.isEnum()) {
            res = clazz.getEnumConstants()[random.nextInt(clazz.getEnumConstants().length)];
        } else if (clazz.isArray()) {
            int size = random.nextInt(RANDOM_MAX - RANDOM_MIN) + RANDOM_MIN;
            if (null != iTestState && iTestState.getSizeParam() != null) {
                size = iTestState.getSizeParam();
            }
            Object array = Array.newInstance(clazz.getComponentType(), size);
            for (int i = 0; i < size; i++) {
                iTestContext.enter(array, String.valueOf(i));
                ITestParamState elementITestState = iTestState == null ? null : iTestState.getElement(String.valueOf(i));
                Object value = generateRandom((Type) clazz.getComponentType(), itestGenericMap, iTestContext);
                Array.set(array, i, value);
                iTestContext.leave(value);
            }
            res = array;
        } else {
            res = newInstance(clazz, iTestContext);
            fillFields(clazz, res, iTestState, Collections.EMPTY_MAP, iTestContext);
        }
        return (T) res;
    }

    protected Object newInstance(Class<?> clazz, ITestContext iTestContext, Type... typeActualArguments) {
        Object res;
        Constructor<?> c = getConstructor(clazz);
        final Map<String, Type> map = new HashMap<String, Type>();
        TypeVariable<?> typeArguments[] = clazz.getTypeParameters();
        for (int i = 0; i < typeActualArguments.length; i++) {
            map.put(typeArguments[i].getName(), typeActualArguments[i]);
        }
        Type[] constructorTypes = c.getGenericParameterTypes();
        Object[] constructorArgs = new Object[constructorTypes.length];
        for (int i = 0; i < constructorTypes.length; i++) {
            Type pt = ITestTypeUtil.getTypeProxy(constructorTypes[i], map);
            iTestContext.enter(iTestContext.getCurrentOwner(), "<init[" + i + "]>");
            iTestContext.setEmptyParam();
            constructorArgs[i] = generateRandom(pt, Collections.EMPTY_MAP, iTestContext);
            iTestContext.leave(constructorArgs[i]);
        }
        try {
            res = c.newInstance(constructorArgs);
        } catch (Exception e) {
            // Object[] args = new Object[constructorArgs.length + 1];
            // args[0] = generateRandom(clazz.getEnclosingClass(), null, null, owner, postProcess);
            // System.arraycopy(constructorArgs, 0, args, 1, constructorArgs.length);
            // try {
            // res = c.newInstance(args);
            // } catch (Exception ee) {
            // throw new RuntimeException(ee);
            // }
            throw new RuntimeException(e);
        }
        return res;
    }

    public <T> T generateRandom(Type type, Map<String, Type> itestGenericMap, ITestContext iTestContext) {
        ITestParamState iTestState = iTestContext.getCurrentParam();
        Class<?> clazz = ITestTypeUtil.getRawClass(type);

        Class<?> requestedClass = getClassFromParam(iTestState);
        if ( null != iTestState && null != iTestState.getAttribute(ITestConstants.ATTRIBUTE_DEFINITION) ) {
            ITestParamState iTestStateLoaded = iTestConfig.getITestParamLoader()
                    .loadITestParam(null == requestedClass ? clazz : requestedClass, iTestState.getAttribute(ITestConstants.ATTRIBUTE_DEFINITION))
                    .getElement(ITestConstants.THIS);
            iTestState = iTestConfig.getITestParamsMerger().merge(new ITestParamAssignmentImpl("", iTestStateLoaded), new ITestParamAssignmentImpl("", iTestState));
            iTestContext.replaceCurrentState(iTestState);
        }

        Object res;
        if ( null != iTestState && null == iTestState.getSizeParam() && null == iTestState.getValue() ) {
            res = null;
        } else if ( null != iTestState && null != iTestState.getAttribute(ITestConstants.REFERENCE_ATTRIBUTE) ) {
            res = iTestContext.findGeneratedObject(iTestState.getAttribute(ITestConstants.REFERENCE_ATTRIBUTE));
        } else if ( PROXY_CLASS == requestedClass ) {
            res = newDynamicProxy(type, itestGenericMap, iTestContext);
        } else if ( Collection.class.isAssignableFrom(clazz) ) {
            res = fillCollection(null, type, itestGenericMap, iTestContext);
        } else if ( Map.class.isAssignableFrom(clazz) ) {
            res = fillMap(null, type, itestGenericMap, iTestContext);
        } else if (null != requestedClass) {
            res = generateRandom(requestedClass, itestGenericMap, iTestContext);
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            Class<?> componentClass;
            int size = random.nextInt(RANDOM_MAX - RANDOM_MIN) + RANDOM_MIN;
            if (null != iTestState && iTestState.getSizeParam() != null) {
                size = iTestState.getSizeParam();
            }
            Map<String, Type> map = new HashMap<String, Type>(itestGenericMap);
            if (arrayType.getGenericComponentType() instanceof ParameterizedType) {
                componentClass = (Class<?>) ((ParameterizedType) arrayType.getGenericComponentType()).getRawType();

            } else {
                componentClass = (Class<?>) arrayType.getGenericComponentType();
            }
            Object array = Array.newInstance(componentClass, size);
            for (int i = 0; i < size; i++) {
                ITestParamState elementITestState = iTestState == null ? null : iTestState.getElement(String.valueOf(i));
                Array.set(array, i, generateRandom(arrayType.getGenericComponentType(), map, iTestContext));
            }
            res = array;
        } else if (null != iTestState && null == iTestState.getNames()) {
            res = iTestConfig.getITestValueConverter().convert(clazz, iTestState.getValue());
        } else if (clazz.isInterface()) {
            res = newDynamicProxy(type, itestGenericMap, iTestContext);
        } else if (type instanceof Class) {
            res = generateRandom(clazz, itestGenericMap, iTestContext);
        } else if (Enum.class == clazz) {
            Type enumType = ITestTypeUtil.getTypeProxy(((ParameterizedType) type).getActualTypeArguments()[0], itestGenericMap);
            res = generateRandom(enumType, itestGenericMap, iTestContext);
        } else if (Class.class == clazz) {
            // probably proxy will be required here
            res = ((ParameterizedType) type).getActualTypeArguments()[0];
        } else {

            Map<String, Type> map = ITestTypeUtil.getTypeMap(type, itestGenericMap);
            res = newInstance(clazz, iTestContext, ITestTypeUtil.getTypeActualArguments(type));
            fillFields(clazz, res, iTestState, map, iTestContext);
        }
        return (T) res;
    }

    private Class<?> getClassFromParam(ITestParamState iTestState) {
        Class<?> clazz = null;
        if (null != iTestState) {
            String reqClass = iTestState.getAttribute(ITestConstants.ATTRIBUTE_CLASS);
            if (null == reqClass && null != iTestState.getElement(ITestConstants.ATTRIBUTE_CLASS)) {
                reqClass = iTestState.getElement(ITestConstants.ATTRIBUTE_CLASS).getValue();
            }
            if (null != reqClass) {
                if (ITestConstants.DYNAMIC.equals(reqClass)) {
                    clazz = PROXY_CLASS;
                } else {
                    clazz = iTestConfig.getITestValueConverter().convert(Class.class, reqClass);
                }
            }
        }
        return clazz;
    }


    protected Object newDynamicProxy(Type type, final Map<String, Type> itestGenericMap, final ITestContext iTestContext) {
        final Class<?> clazz = ITestTypeUtil.getRawClass(type);
        final Map<String, Object> methodResults = new HashMap<String, Object>();

        Object res = Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, new InvocationHandler() {

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String mSignature = ITestUtils.getMethodSingnature(method, true);
                if (methodResults.containsKey(mSignature)) {
                    return methodResults.get(mSignature);
                }
                throw new ITestMethodExecutionException("Implementation of " + clazz.getName() + "." + mSignature + " not provided", null);
            }
        });


        Map<String, Type> map = ITestTypeUtil.getTypeMap(type, new HashMap<String, Type>());

        Class<?> t = clazz;
        do {
            for (Method m : t.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    String signature = ITestUtils.getMethodSingnature(m, true);
                    ITestParamState iTestState = iTestContext.getCurrentParam();
                    ITestParamState mITestState = iTestState == null ? null : iTestState.getElement(signature);
                    if (null == mITestState) {
                        mITestState = iTestState == null ? null : iTestState.getElement(signature = ITestUtils.getMethodSingnature(m, false));
                    }
                    fillMethod(m, res, signature, map, iTestContext, methodResults);
                }
            }
            map = ITestTypeUtil.getTypeMap(clazz, map);
        } while ((t = t.getSuperclass()) != null);

        return res;
    }

    protected void fillMethod(Method m, Object res, String mSignature, Map<String, Type> map, ITestContext iTestContext, Map<String, Object> methodResults) {
        try {
            iTestContext.enter(res, mSignature);
            ITestParamState mITestState = iTestContext.getCurrentParam();
            Type rType = ITestTypeUtil.getTypeProxy(m.getGenericReturnType(), map);
            Object o = generateRandom(rType, map, iTestContext);
            methodResults.put(ITestUtils.getMethodSingnature(m, true), o);
            iTestContext.leave(o);
        } catch (ITestException e) {
            e.addPrefix(mSignature);
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ITestIllegalArgumentException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    protected void fillFields(Class<?> clazz, Object o, ITestParamState itestStateNotUsed, Map<String, Type> map, ITestContext iTestContext) {
        if (iTestContext.depth() > MAX_DEPTH) {
            throw new ITestPossibleCycleException("Possible cycle detected.");
        }
        ITestParamState itestState = iTestContext.getCurrentParam();
        if ( null != itestState && null != itestState.getNames() && o instanceof ITestSuperObject ) {
            ITestSuperObject iTestSuperObject = (ITestSuperObject) o;
            for (String name : itestState.getNames()) {
                iTestContext.enter(o, name);
                Object value = generate((Type) Object.class, null, map, iTestContext);
                iTestSuperObject.setField(name, value);
                iTestContext.leave(value);
            }
        } else {
            Collection<FieldHolder> fieldHolders = collectFields(clazz, map, iTestContext);
            for (FieldHolder f : fieldHolders) {
                fillField(f.getField(), o, f.getTypeMap(), iTestContext);
            }
        }
    }

    protected Collection<FieldHolder> collectFields(Class<?> clazz, Map<String, Type> map, ITestContext iTestContext) {
        return ITestFieldUtil.collectFields(clazz, map);
    }

    protected void fillField(Field f, Object o, Map<String, Type> map, ITestContext iTestContext) {
        f.setAccessible(true);
        try {
            Type fType = ITestTypeUtil.getTypeProxy(f.getGenericType(), map);
            iTestContext.enter(o, f.getName());
            ITestParamState fITestState = iTestContext.getCurrentParam();
            String fITestValue = fITestState == null ? null : fITestState.getValue();
            Object oRes;
            // if ( fITestState != null && null != fITestState.getAttribute(ITestConstants.REFERENCE_ATTRIBUTE) ) {
            // oRes = iTestContext.findGeneratedObject(fITestState.getAttribute(ITestConstants.REFERENCE_ATTRIBUTE));
            // } else
            if (null == fITestState && iTestContext.isStaticAssignmentRegistered(o.getClass(), f.getName())) {
                iTestContext.registerAssignment(o.getClass(), f.getName());
                oRes = null;//TODO: implement it
            } else if (null == fITestState && f.isAnnotationPresent(ITestFieldAssignment.class)) {
                iTestContext.registerAssignment(f.getAnnotation(ITestFieldAssignment.class).value());
                oRes = null;//TODO: implement it
            } else if (null == fITestState && f.isAnnotationPresent(ITestFieldClass.class)) {
                iTestContext.setEmptyParam();
                oRes = generateRandom((Type) f.getAnnotation(ITestFieldClass.class).value(), map, iTestContext);
                f.set(o, oRes);
//            } else if (null != fITestValue) {
//                if (fITestValue.startsWith(":")) {
//                    // TODO: register assignment
//                    oRes = null;//TODO: implement it
//                } else {
//                    oRes = iTestConfig.getITestValueConverter().convert(f.getType(), fITestValue);
//                    f.set(o, oRes);
//                }
            } else {
                oRes = generateRandom(fType, map, iTestContext);
                f.set(o, oRes);
            }
            f.set(o, oRes);
            iTestContext.leave(oRes);
        } catch (ITestException e) {
            e.addPrefix(f.getName());
            throw e;
        } catch (IllegalArgumentException e) {
            throw new ITestIllegalArgumentException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    protected Object fillMap(Object o, Type type, Map<String, Type> map, ITestContext iTestContext) {
        ITestParamState iTestState = iTestContext.getCurrentParam();
        Map<Object, Object> m = (Map<Object, Object>) o;
        if(null!=iTestContext.getCurrentParam() && null!=iTestContext.getCurrentParam().getAttribute(ITestConstants.ATTRIBUTE_CLASS)){
            Class<?> mapClass = iTestConfig.getITestValueConverter().convert(Class.class,
                    iTestContext.getCurrentParam().getAttribute(ITestConstants.ATTRIBUTE_CLASS));
            m = (Map<Object, Object>) newInstance(mapClass, iTestContext);
        }
        if (null == m) {
            m = new HashMap<Object, Object>();
        } else {
            m.clear();
        }
        int size = random.nextInt(RANDOM_MAX - RANDOM_MIN) + RANDOM_MIN;
        if (null != iTestState && iTestState.getSizeParam() != null) {
            size = iTestState.getSizeParam();
        }
        for (int i = 0; i < size; i++) {
            iTestContext.enter(m, String.valueOf(i));
            iTestContext.enter("Map.Entry", "key");
            ITestParamState eITestState = iTestState == null ? null : iTestState.getElement(String.valueOf(i));
            Object key = generateRandom(((ParameterizedType) type).getActualTypeArguments()[0], map, iTestContext);
            iTestContext.leave(key);
            iTestContext.enter("Map.Entry", "value");
            Object value = generateRandom(((ParameterizedType) type).getActualTypeArguments()[1], map, iTestContext);
            iTestContext.leave(value);
            m.put(key, value);
            iTestContext.leave("Map.Entry");
        }
        return m;
    }

    protected Object fillCollection(Object o, Type type, Map<String, Type> map, ITestContext iTestContext) {
        ITestParamState iTestState = iTestContext.getCurrentParam();
        Collection<Object> col = (Collection<Object>) o;
        Class collectionClass;
        if ( null != iTestContext.getCurrentParam() && null != iTestContext.getCurrentParam().getAttribute(ITestConstants.ATTRIBUTE_CLASS) ) {
            collectionClass = iTestConfig.getITestValueConverter().convert(Class.class,
                    iTestContext.getCurrentParam().getAttribute(ITestConstants.ATTRIBUTE_CLASS));
        } else {
            collectionClass = ITestTypeUtil.getRawClass(type);
        }
        if ( !collectionClass.isInterface() ) {
            col = (Collection<Object>) newInstance(collectionClass, iTestContext);
        } else {
            if ( Set.class.isAssignableFrom(collectionClass) ) {
                col = new HashSet<Object>();
            } else {
                col = new ArrayList<Object>();
            }
        }
        if ( null == col ) {
        } else {
            col.clear();
        }
        int size = random.nextInt(RANDOM_MAX - RANDOM_MIN) + RANDOM_MIN;
        if (null != iTestState && iTestState.getSizeParam() != null) {
            size = iTestState.getSizeParam();
        }

        Type elementType=ITestTypeUtil.getTypeProxy(ITestTypeUtil.getParameterType(type,Collection.class,0,map),map);
        for (int i = 0; i < size; i++) {
            iTestContext.enter(col, String.valueOf(i));
            Object value;
            value = generateRandom(elementType, map, iTestContext);
            col.add(value);
            iTestContext.leave(value);
        }
        return col;
    }

    private static Constructor<?> getConstructor(Class<?> clazz) {
        Constructor<?> res;
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        if (constructors.length == 0) {
            throw new ITestInitializationException(clazz, null);
        }
        res = constructors[0];
        for (int i = 1; i < constructors.length; i++) {
            if (constructors[i].getParameterTypes().length == 0) {
                res = constructors[i];
                break;
            }
        }
        try {
            res.setAccessible(true);
        } catch (SecurityException e) {
            throw new ITestInitializationException(clazz, e);
        }
        return res;
    }


    private static void log(String log) {
        // System.out.println(log);
    }

    public interface Builder {
        boolean ignore(Class<?> clazz);

        Object build(Type type);

        Object build(Class<?> clazz);
    }


}
