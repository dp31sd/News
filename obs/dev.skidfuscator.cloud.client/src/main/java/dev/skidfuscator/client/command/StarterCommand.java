package dev.skidfuscator.client.command;

import dev.skidfuscator.client.starter.StarterFactory;
import dev.skidfuscator.client.starter.StarterSession;
import dev.skidfuscator.client.starter.impl.forge.Forge1_8_8_StarterFactory;
import dev.skidfuscator.client.starter.impl.spigot.Spigot1_8_8_StarterFactory;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import picocli.CommandLine;

import java.io.EOFException;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

@CommandLine.Command(
        aliases = "starter",
        mixinStandardHelpOptions = false,
        version = "Starter 1.0.0",
        description = "Starter command for enterprise templates"
)
public class StarterCommand implements Callable<Integer> {
    private final TextIO textIO = TextIoFactory.getTextIO();
    private final List<StarterFactory> factories = Arrays.asList(
            new Forge1_8_8_StarterFactory(),
            new Spigot1_8_8_StarterFactory()
    );

    @Override
    public Integer call() throws Exception {
        final StarterFactory factory = this.cliObjectSelector(
                factories,
                "Please select which starter you wish to download",
                StarterFactory::getName
        );
        System.out.println("Selected " + factory.getName());
        Path currentRelativePath = Paths.get(".");
        factory.createStart(StarterSession.builder().directory(new File(currentRelativePath.toFile(), "skidfuscator-starter")).build());
        return 0;
    }

    public <T> T cliObjectSelector(List<T> items, String customPromptMessage,
                                         Function<T, String> f) throws EOFException {

        if(items == null || items.isEmpty()) {
            System.out.println("No items found.  Cannot continue");
            throw new EOFException("The provided list of items is empty");
        }

        String userPrompt = (customPromptMessage != null) ? customPromptMessage + ":\n" :
                String.format("Please select one of the %d items from the list:\n", items.size());

        List<String> optionsList = items.stream()
                .map(item -> {
                    return String.format("[%d] - %s", items.indexOf(item), f.apply(item));
                }).collect(Collectors.toList());

        T selectedItems = null;

        optionsList.add(0, userPrompt);

        while(true) {
            textIO.getTextTerminal().println(optionsList);
            selectedItems = items.get(
                    textIO.newIntInputReader()
                            .withMinVal(0)
                            .withMaxVal(items.size() - 1)
                            .read("Option number"));
            break;
        }

        return selectedItems;
    }
}
