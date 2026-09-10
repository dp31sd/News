package dev.skidfuscator.client.command;

import com.google.gson.GsonBuilder;
import dev.skidfuscator.client.artemis.ArtemisProvider;
import dev.skidfuscator.client.artemis.SimpleArtemisProvider;
import dev.skidfuscator.client.connector.ConnectedInstance;
import dev.skidfuscator.client.connector.Connector;
import dev.skidfuscator.client.connector.impl.StompConnector;
import dev.skidfuscator.client.util.ConsoleColors;
import dev.skidfuscator.client.util.MiscUtil;
import dev.skidfuscator.migration.ExemptToConfigMigration;
import dev.skidfuscator.obfuscator.SkidfuscatorSession;
import picocli.CommandLine;

import java.io.File;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * @author Ghast
 * @since 06/03/2021
 * SkidfuscatorV2 © 2021
 */

@CommandLine.Command(
        aliases = "obfuscate",
        mixinStandardHelpOptions = true,
        version = "obfuscate 1.0.0",
    description = "Obfuscates and runs a specific jar"
)
public class ObfuscateCommand implements Callable<Integer> {
    @CommandLine.Parameters(
            index = "0",
            description = "The file which will be obfuscated.",
            scope = CommandLine.ScopeType.LOCAL
    )
    private File input;

    @CommandLine.Option(
            names = {"-rt", "--runtime"},
            description = "Path to the runtime jar"
    )
    private File runtime;

    @CommandLine.Option(
            names = {"-li", "--libs"},
            description = "Path to the libs folder"
    )
    private File libs;

    @CommandLine.Option(
            names = {"-ex", "--exempt"},
            description = "Path to the exempt file"
    )
    private File exempt;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Path to the output jar location"
    )
    private File output;

    @CommandLine.Option(
            names = {"-re", "--renamer"},
            description = "Enables renamer for the obfuscation"
    )
    private boolean renamer;

    @CommandLine.Option(
            names = {"-cfg", "--config"},
            description = "Path to the config file"
    )
    private File config;

    @CommandLine.Option(
            names = {"-ph", "--phantom"},
            description = "Declare if phantom computation should be used"
    )
    private boolean phantom;

    @CommandLine.Option(
            names = {"-lc", "--lowcon"},
            description = "Uses sharded packets to upload mappings"
    )
    private boolean lowCon;

    @CommandLine.Option(
            names = {"-fuckit", "--fuckit"},
            description = "Do not use!"
    )
    private boolean fuckit;

    @Override
    public Integer call()  {
        /* Total number of processors or cores available to the JVM */
        final String processors =
                String.format("%19.19s", "Processors:")
                        + "   "
                        + String.format(
                                 "%-19.19s",
                            Runtime.getRuntime().availableProcessors() + " cores"
                );

        final long freeMemory = Math.round(Runtime.getRuntime().freeMemory() / 1E6);
        final String memory =
                String.format("%19.19s", "Current Memory:")
                        + "   "
                        + String.format("%-19.19s", freeMemory + "mb");

        final long maxMemory = Math.round(Runtime.getRuntime().maxMemory() / 1E6);
        final String memoryString = (maxMemory == Long.MAX_VALUE
                ? ConsoleColors.GREEN + "no limit"
                : maxMemory + "mb"
        );
        String topMemory =
                String.format("%19.19s", "Max Memory:")
                        + "   "
                        + String.format("%-19.19s",
                            memoryString + (maxMemory > 1500 ? "" : " ⚠️")
                        );

        topMemory = MiscUtil.replaceColor(
                topMemory,
                memoryString,
                maxMemory > 1500 ? ConsoleColors.GREEN_BRIGHT : ConsoleColors.RED_BRIGHT
        );
        // slight fix for thing
        topMemory = topMemory.replace("⚠️", "⚠️ ");

        final String[] logo = new String[] {
                "",
                "  /$$$$$$  /$$       /$$       /$$  /$$$$$$                                           /$$",
                " /$$__  $$| $$      |__/      | $$ /$$__  $$                                         | $$",
                "| $$  \\__/| $$   /$$ /$$  /$$$$$$$| $$  \\__//$$   /$$  /$$$$$$$  /$$$$$$$  /$$$$$$  /$$$$$$    /$$$$$$   /$$$$$$",
                "|  $$$$$$ | $$  /$$/| $$ /$$__  $$| $$$$   | $$  | $$ /$$_____/ /$$_____/ |____  $$|_  $$_/   /$$__  $$ /$$__  $$",
                " \\____  $$| $$$$$$/ | $$| $$  | $$| $$_/   | $$  | $$|  $$$$$$ | $$        /$$$$$$$  | $$    | $$  \\ $$| $$  \\__/",
                " /$$  \\ $$| $$_  $$ | $$| $$  | $$| $$     | $$  | $$ \\____  $$| $$       /$$__  $$  | $$ /$$| $$  | $$| $$",
                "|  $$$$$$/| $$ \\  $$| $$|  $$$$$$$| $$     |  $$$$$$/ /$$$$$$$/|  $$$$$$$|  $$$$$$$  |  $$$$/|  $$$$$$/| $$",
                " \\______/ |__/  \\__/|__/ \\_______/|__/      \\______/ |_______/  \\_______/ \\_______/   \\___/   \\______/ |__/",
                "",
                "                               ┌───────────────────────────────────────────┐",
                "                               │ "             + processors +            " │",
                "                               │ "               + memory +              " │",
                "                               │ "              + topMemory +            " │",
                "                               └───────────────────────────────────────────┘",
                "",
                "                      Author: Ghast     Version: 2.0.3     Today: "
                        + DateFormat.getDateTimeInstance().format(new Date(Instant.now().toEpochMilli())),
                ""
        };

        for (String s : logo) {
            System.out.println(s);
        }

        if (input == null) {
            return -1;
        }

        if (output == null) {
            output = new File(input.getPath() + "-out.jar");
        }

        if (runtime == null) {
            final String home = System.getProperty("java.home");
            runtime = new File(
                    home,
                    MiscUtil.getJavaVersion() > 8
                            ? "jmods"
                            : "lib/rt.jar"
            );
        }

        if (exempt != null) {
            final File converted = new File(exempt.getAbsolutePath()
                    .substring(0, exempt.getAbsolutePath().lastIndexOf(File.separator))
                    + File.separator + "config.hocon");
            final String warning = ConsoleColors.YELLOW
                    + "██╗    ██╗ █████╗ ██████╗ ███╗   ██╗██╗███╗   ██╗ ██████╗ \n"
                    + "██║    ██║██╔══██╗██╔══██╗████╗  ██║██║████╗  ██║██╔════╝ \n"
                    + "██║ █╗ ██║███████║██████╔╝██╔██╗ ██║██║██╔██╗ ██║██║  ███╗\n"
                    + "██║███╗██║██╔══██║██╔══██╗██║╚██╗██║██║██║╚██╗██║██║   ██║\n"
                    + "╚███╔███╔╝██║  ██║██║  ██║██║ ╚████║██║██║ ╚████║╚██████╔╝\n"
                    + " ╚══╝╚══╝ ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝╚═╝  ╚═══╝ ╚═════╝ \n"
                    + "\n"
                    + "⚠️  Warning! Skidfuscator has deprecated the exempt file!\n"
                    + ConsoleColors.RESET
                    + "\n  Launching migrator service..."
                    + "\n  Config will be found at " + converted
                    + "\n";
            System.out.println(warning);
            new ExemptToConfigMigration().migrate(exempt, converted);

            config = converted;
        }

        final SkidfuscatorSession skidInstance = SkidfuscatorSession.builder()
                .input(input)
                .output(output)
                .libs(libs == null ? new File[0] : libs.listFiles())
                .runtime(runtime)
                .phantom(phantom)
                .lowCon(lowCon)
                .config(config)
                .renamer(renamer)
                .jmod(MiscUtil.getJavaVersion() > 8)
                .fuckit(fuckit)
                .analytics(false)
                .build();

        final Connector connector = new StompConnector();
        final ConnectedInstance instance = connector.connect("65.21.94.242", 25618);
        final ArtemisProvider provider = new SimpleArtemisProvider
                .Factory()
                .simpleLogListener()
                .simpleOutputListener()
                .instance(instance)
                .config(skidInstance)
                .gson(new GsonBuilder().serializeNulls().create())
                .build();

        instance.listen(provider);
        instance.publish(skidInstance);

        return 0;
    }


}