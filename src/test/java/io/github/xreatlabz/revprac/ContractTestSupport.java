package io.github.xreatlabz.revprac;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;

public final class ContractTestSupport {

    private ContractTestSupport() {
    }

    public static Class<?> loadClass(String fqcn) {
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Expected class to exist: " + fqcn, exception);
        }
    }

    public static Object instantiateNoArgs(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not instantiate " + type.getName() + " with a no-arg constructor", exception);
        }
    }

    public static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new AssertionError("Could not invoke " + method, exception);
        }
    }

    public static Object instantiateRecord(Class<?> recordType, Map<String, ?> valuesByComponent) {
        if (!recordType.isRecord()) {
            throw new AssertionError(recordType.getName() + " is not a record");
        }

        RecordComponent[] components = recordType.getRecordComponents();
        Object[] arguments = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            if (!valuesByComponent.containsKey(component.getName())) {
                throw new AssertionError("Missing value for record component " + component.getName() + " on " + recordType.getName());
            }
            arguments[index] = valuesByComponent.get(component.getName());
        }

        try {
            Constructor<?> constructor = recordType.getDeclaredConstructor(Arrays.stream(components)
                    .map(RecordComponent::getType)
                    .toArray(Class[]::new));
            constructor.setAccessible(true);
            return constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not instantiate record " + recordType.getName(), exception);
        }
    }

    public static Object recordComponentValue(Object record, String componentName) {
        try {
            Method accessor = record.getClass().getMethod(componentName);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not read record component " + componentName + " from " + record.getClass().getName(), exception);
        }
    }

    public static Object firstEnumConstant(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new AssertionError(enumType.getName() + " is not an enum with constants");
        }
        return constants[0];
    }
}
