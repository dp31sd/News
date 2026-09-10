package dev.skidfuscator.client.starter;

import lombok.Builder;
import lombok.Data;

import java.io.File;

@Builder
@Data
public class StarterSession {
    private File directory;
}
