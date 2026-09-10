package dev.skidfuscator.obfuscator.transform.impl.j2c;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class Dispatcher {
    public static int loaded;
    static {
        OS_NAME = System.getProperty("os.name");
        OS_ARCH = System.getProperty("os.arch");
    }

    public static final String OS_NAME;
    public static final String OS_ARCH;

    private static final int WINDOWS = 0;
    private static final int OSX = 1;
    private static final int SOLARIS = 2;
    private static final int LINUX = 3;

    private static final int X86_32 = 0;
    private static final int X86_64 = 1;
    private static final int AMD_64 = 2;
    private static final int AARCH_64 = 3;
    private static final int PPC = 4;
    public static void load() {
        if (loaded != 0) {
            loaded++;
            return;
        }

        loaded++;

        int os;
        // resolve OS
        if (OS_NAME.contains("Windows")) {
            os = WINDOWS;
        } else if (OS_NAME.contains("Mac OS X")) {
            os = OSX;
        } else if (OS_NAME.contains("Solaris")) {
            os = SOLARIS;
        } else if (OS_NAME.contains("Linux") || OS_NAME.contains("LINUX")) {
            os = LINUX;
        } else {
            throw new IllegalArgumentException("Unknown operating system " + OS_NAME);
        }

        // resolve architecture
        int arch = -1;

        final Map<String, Integer> archMap = new HashMap<>();
        archMap.put("x86", X86_32);
        archMap.put("i386", X86_32);
        archMap.put("i486", X86_32);
        archMap.put("i586", X86_32);
        archMap.put("i686", X86_32);
        archMap.put("x86_64", X86_64);
        archMap.put("amd64", AMD_64);
        archMap.put("aarch64", AARCH_64);
        archMap.put("powerpc", PPC);

        arch = archMap.getOrDefault(OS_ARCH, -1);
        if (arch == -1) {
            throw new IllegalArgumentException("Unknown architecture " + OS_ARCH);
        }

        final String driver;

        switch (os) {
            case OSX: {
                if (arch == AARCH_64) {
                    // M1
                    driver = "/lock/driver-macos-m1.skid";
                } else {
                    driver = "/lock/driver-macos.skid";
                }
                break;
            }

            case LINUX: {
                if (arch == AARCH_64) {
                    // M1
                    driver = "/lock/driver-lin-amd.skid";
                } else {
                    driver = "/lock/driver-lin.skid";
                }
                break;
            }

            case WINDOWS: {
                if (arch == X86_64 || arch == AMD_64) {
                    driver = "/lock/driver-win.skid";
                } else {
                    throw new IllegalStateException("Unknown architecture for windows " + OS_ARCH);
                }
                break;
            }

            default: {
                throw new IllegalStateException("Unknown arch: " + OS_ARCH);
            }
        }

        File file;

        try {
            file = File.createTempFile("lib", null);
            file.deleteOnExit();
            if (!file.exists()) {
                throw new IOException();
            }
        }
        catch (IOException iOException) {
            throw new UnsatisfiedLinkError("Failed to create temp file");
        }
        byte[] byArray = new byte[2048];

        try (InputStream inputStream = Dispatcher.class.getResourceAsStream(driver);
             FileOutputStream fileOutputStream = new FileOutputStream(file)){
            int n;
            while ((n = inputStream.read(byArray)) != -1) {
                ((OutputStream)fileOutputStream).write(byArray, 0, n);
            }
        }
        catch (IOException iOException) {
            throw new UnsatisfiedLinkError("Failed to copy file: " + iOException.getMessage());
        }
        System.load(file.getAbsolutePath());

    }
}
