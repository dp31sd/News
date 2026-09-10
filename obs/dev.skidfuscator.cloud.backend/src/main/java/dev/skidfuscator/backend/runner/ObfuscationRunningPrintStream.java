package dev.skidfuscator.backend.runner;

import java.io.*;
import java.nio.charset.Charset;

public class ObfuscationRunningPrintStream extends PrintStream {
    public ObfuscationRunningPrintStream(OutputStream out) {
        super(out);
    }

    public ObfuscationRunningPrintStream(OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
    }

    public ObfuscationRunningPrintStream(OutputStream out, boolean autoFlush, String encoding) throws UnsupportedEncodingException {
        super(out, autoFlush, encoding);
    }

    public ObfuscationRunningPrintStream(OutputStream out, boolean autoFlush, Charset charset) {
        super(out, autoFlush, charset);
    }

    public ObfuscationRunningPrintStream(String fileName) throws FileNotFoundException {
        super(fileName);
    }

    public ObfuscationRunningPrintStream(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(fileName, csn);
    }

    public ObfuscationRunningPrintStream(String fileName, Charset charset) throws IOException {
        super(fileName, charset);
    }

    public ObfuscationRunningPrintStream(File file) throws FileNotFoundException {
        super(file);
    }

    public ObfuscationRunningPrintStream(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, csn);
    }

    public ObfuscationRunningPrintStream(File file, Charset charset) throws IOException {
        super(file, charset);
    }


}
