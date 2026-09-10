package dev.skidfuscator.j2c.encryption;

import dev.skidfuscator.j2c.helpers.AbstractTestCorrectnessTemplate;
import testclasses.encryption.AesCipther;


public class AesCipherTest extends AbstractTestCorrectnessTemplate {
    private final Class<?> className = AesCipther.class;
    private final String[] methodTest = {"doThing"};
    private final Class[][] methodParam = {new Class[]{String.class}};
    private final Object[][] methodArgs = {new Object[]{"A random string"}};

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
