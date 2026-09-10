package dev.skidfuscator.client.command;
import picocli.CommandLine;
import java.util.concurrent.Callable;

@CommandLine.Command(
        aliases = "obfuscate",
        mixinStandardHelpOptions = true,
        version = "obfuscate 1.0.0",
        description = "Obfuscates and runs a specific jar")
public class MainCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        return 0;
    }
}
