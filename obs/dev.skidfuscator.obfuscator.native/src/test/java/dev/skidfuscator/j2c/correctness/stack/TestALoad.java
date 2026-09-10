package dev.skidfuscator.j2c.correctness.stack;

import dev.skidfuscator.j2c.helpers.AbstractTestCorrectnessTemplate;

public class TestALoad extends AbstractTestCorrectnessTemplate {

    private Class<?> className = testclasses.stack.ALoad.class;
    private String[] methodTest = {"exec"};
    private Class[][] methodParam = {new Class[]{String.class}};
    private Object[][] methodArgs = {new Object[]{"hello"}};


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
