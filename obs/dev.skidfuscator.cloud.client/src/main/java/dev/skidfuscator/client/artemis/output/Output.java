package dev.skidfuscator.client.artemis.output;

public class Output {
    private final String name;
    private final byte[] file;

    public Output(String name, byte[] file) {
        this.name = name;
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public byte[] getFile() {
        return file;
    }
}
