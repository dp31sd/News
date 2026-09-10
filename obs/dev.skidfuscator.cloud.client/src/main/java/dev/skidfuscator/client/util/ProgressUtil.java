package dev.skidfuscator.client.util;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;

import java.time.temporal.ChronoUnit;

public class ProgressUtil {
    public static ProgressBar progress(final int count) {
        return new ProgressBarBuilder()
                .setTaskName("Executing...").setInitialMax(count)
                .setUpdateIntervalMillis(1000)
                .setStyle(ProgressBarStyle.ASCII)
                .setSpeedUnit(ChronoUnit.SECONDS)
                .setUnit("", 1L)
                .build();
    }
}
