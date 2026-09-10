package dev.skidfuscator.client.starter.impl;

import dev.skidfuscator.client.starter.StarterFactory;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public abstract class AbstractStarterFactory implements StarterFactory {
    private final String name;

    public AbstractStarterFactory(String name) {
        this.name = name;
    }


    /**
     * @return Returns the name for the Starter factory
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Download mappings from a specified URL.
     *
     * @param output the output file to which the mapping will be downloaded
     * @param url    the url which contains the URL
     */
    protected void downloadMappings(final File output, final String url) {
        // Protective assertions
        assert url.endsWith(".json") : "URL output must be a json!";
        assert !output.exists() : "Output file must be empty! Cannot overwrite!";

        // Downloading using apache commons kekw
        try {
            final URL urlObj = new URL(url);
            FileUtils.copyURLToFile(urlObj, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
