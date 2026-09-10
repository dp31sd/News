package dev.skidfuscator.j2c.correctness.newobj;

import dev.skidfuscator.j2c.helpers.AbstractTestCorrectnessTemplate;

public class TestNewNoLdc extends AbstractTestCorrectnessTemplate {

    private Class<?> className = testclasses.newobj.NewObjectNoLdc.class;
    private String[] methodTest = {"getObject"};
    private Class[][] methodParam = {new Class[]{int.class, long.class, double.class, String.class}};
    private Object[][] methodArgs = {new Object[]{1000000, 10000000000L, 3.5, "hello"}};

    @Override
    public Class<?> getTestClass() {
        return className;
    }

    @Override
    public String[] getTestMethodName() {
        return methodTest;
    }

    @Override
    public Class<?>[][] getTestMethodParams() {
        return methodParam;
    }

    @Override
    public Object[][] getTestMethodArgs() {
        return methodArgs;
    }
}
