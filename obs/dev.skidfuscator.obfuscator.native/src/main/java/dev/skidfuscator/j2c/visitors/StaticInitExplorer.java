package dev.skidfuscator.j2c.visitors;

import dev.skidfuscator.j2c.JavaToC;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.INVOKESTATIC;

/**
 * This visitor is used to add the loadLibrary used to load dynamically the generated C library.
 *
 * @author D.Pizzolotto
 */
public class StaticInitExplorer extends MethodVisitor {
    private final String libname;
    private int nthInstruction;

    /**
     * Default constructor
     *
     * @param version ASM version
     * @param mv      original method visitor
     * @param libname name of the library
     */
    public StaticInitExplorer(int version, MethodVisitor mv, @NotNull String libname) {
        super(version, mv);
        this.libname = libname.replace('/', '.');
        this.nthInstruction = 0;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (owner.equals("skid/Driver") && name.equals("get")) {
            super.visitMethodInsn(INVOKESTATIC, "skid/Dispatcher", "load", "()V", false);
        }

        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
    }

    @Override
    public void visitCode() {
        if (nthInstruction == 0) {
            if (JavaToC.FORCE) {
            } else {
                //super.visitMethodInsn(INVOKESTATIC, "");
            }
        }
        nthInstruction++;
        super.visitCode();
    }


    @Override
    public void visitMaxs(int maxStack, int maxVars) {
        //since one string will be pushed onto the stack
        super.visitMaxs(maxStack + 1, maxVars);
    }
}
