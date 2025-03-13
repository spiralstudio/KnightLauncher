package com.lucasallegri.launcher;

import com.lucasallegri.launcher.settings.Settings;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

import static com.lucasallegri.launcher.Log.log;

public class Fonts {

  private static final String fontPathRegular = "/fonts/GoogleSans-Regular.ttf";
  private static final String fontPathMedium = "/fonts/GoogleSans-Medium.ttf";
  public static Font fontReg = null;
  public static Font fontRegBig = null;
  public static Font fontMed = null;
  public static Font fontMedIta = null;
  public static Font fontMedBig = null;
  public static Font fontMedGiant = null;
  private static float sizeMultiplier = 1.2f;

  public static void setup() {

    InputStream fontRegIs;
    InputStream fontRegBigIs;
    InputStream fontMedIs;
    InputStream fontMedItaIs;
    InputStream fontMedBigIs;
    InputStream fontMedGiantIs;

    fontRegIs = LauncherGUI.class.getResourceAsStream(fontPathRegular);
    fontRegBigIs = LauncherGUI.class.getResourceAsStream(fontPathRegular);
    fontMedIs = LauncherGUI.class.getResourceAsStream(fontPathMedium);
    fontMedItaIs = LauncherGUI.class.getResourceAsStream(fontPathMedium);
    fontMedBigIs = LauncherGUI.class.getResourceAsStream(fontPathMedium);
    fontMedGiantIs = LauncherGUI.class.getResourceAsStream(fontPathMedium);

    try {

      fontReg = Font.createFont(Font.TRUETYPE_FONT, fontRegIs);
      fontReg = fontReg.deriveFont(11.0f * sizeMultiplier);
      fontReg = fontReg.deriveFont(Font.ITALIC);

      fontRegBig = Font.createFont(Font.TRUETYPE_FONT, fontRegBigIs);
      fontRegBig = fontRegBig.deriveFont(14.0f * sizeMultiplier);
      fontRegBig = fontRegBig.deriveFont(Font.ITALIC);

      fontMed = Font.createFont(Font.TRUETYPE_FONT, fontMedIs);
      fontMed = fontMed.deriveFont(11.0f * sizeMultiplier);

      fontMedIta = Font.createFont(Font.TRUETYPE_FONT, fontMedItaIs);
      fontMedIta = fontMedIta.deriveFont(11.0f * sizeMultiplier);
      fontMedIta = fontMedIta.deriveFont(Font.ITALIC);

      fontMedBig = Font.createFont(Font.TRUETYPE_FONT, fontMedBigIs);
      fontMedBig = fontMedBig.deriveFont(14.0f * sizeMultiplier);

      fontMedGiant = Font.createFont(Font.TRUETYPE_FONT, fontMedGiantIs);
      fontMedGiant = fontMedGiant.deriveFont(40.0f * sizeMultiplier);

    } catch (FontFormatException | IOException e) {
      log.error(e);
    }

  }

}
