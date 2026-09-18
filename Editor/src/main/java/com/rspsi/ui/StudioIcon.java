package com.rspsi.ui;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignM;
import org.kordamp.ikonli.materialdesign2.MaterialDesignO;
import org.kordamp.ikonli.materialdesign2.MaterialDesignR;
import org.kordamp.ikonli.materialdesign2.MaterialDesignT;
import org.kordamp.ikonli.materialdesign2.MaterialDesignU;

/** Semantic icon identities used by the active Studio shell. */
public enum StudioIcon {
    SELECT(MaterialDesignC.CURSOR_DEFAULT),
    TERRAIN(MaterialDesignT.TERRAIN),
    HEIGHT(MaterialDesignT.TERRAIN),
    OBJECT(MaterialDesignC.CUBE_OUTLINE),
    FRAGMENT(MaterialDesignC.CONTENT_COPY),
    ASSETS(MaterialDesignF.FOLDER_STAR),
    INSPECTOR(MaterialDesignI.INFORMATION_OUTLINE),
    VALIDATION(MaterialDesignA.ALERT_CIRCLE_OUTLINE),
    BUILD(MaterialDesignH.HAMMER_WRENCH),
    UNDO(MaterialDesignU.UNDO),
    REDO(MaterialDesignR.REDO),
    SEARCH(MaterialDesignM.MAGNIFY),
    SETTINGS(MaterialDesignC.COG_OUTLINE),
    DETACH(MaterialDesignO.OPEN_IN_NEW),
    RESET_LAYOUT(MaterialDesignR.RESTORE),
    CHEVRON_DOWN(MaterialDesignC.CHEVRON_DOWN),
    CHEVRON_UP(MaterialDesignC.CHEVRON_UP);

    private final Ikon glyph;

    StudioIcon(Ikon glyph) {
        this.glyph = glyph;
    }

    public Ikon glyph() {
        return glyph;
    }
}
