package com.rspsi.ui;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;

import java.util.Objects;

/** Applies the application theme once at the JavaFX composition root. */
public final class ThemeService {
    public enum Theme {
        PRIMER_DARK
    }

    private ThemeService() {
    }

    public static void apply(Theme theme) {
        Objects.requireNonNull(theme, "theme");
        switch (theme) {
            case PRIMER_DARK -> Application.setUserAgentStylesheet(
                    new PrimerDark().getUserAgentStylesheet());
        }
    }
}
