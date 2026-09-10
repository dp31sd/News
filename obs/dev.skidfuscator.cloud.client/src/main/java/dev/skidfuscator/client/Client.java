package dev.skidfuscator.client;

import dev.skidfuscator.client.command.MainCommand;
import dev.skidfuscator.client.command.ObfuscateCommand;
import dev.skidfuscator.client.command.StarterCommand;
import dev.skidfuscator.client.util.TimedLogger;
import org.apache.log4j.LogManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import picocli.CommandLine;

public class Client {
    public static final TimedLogger LOGGER = new TimedLogger(LogManager.getLogger(Client.class));
    public static ThreadPoolTaskScheduler SCHEDULER;
    public static void main(final String[] args) {
        new CommandLine(new MainCommand())
                .addSubcommand("obfuscate", new ObfuscateCommand())
                .addSubcommand("starter", new StarterCommand())
                .execute(args);
    }
}
