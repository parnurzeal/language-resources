// Copyright 2016 Google, Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.languageresources.my;

import java.util.logging.Logger;

/**
 * A {@code GraphemeCluster} is a graphical unit of Burmese text consisting of a letter and
 * optional modifiers.
 *
 * <p>The definition of a {@code GraphemeCluster} is mostly consistent with the description in the
 * <a href="http://unicode.org/notes/tn11/">Unicode Technical Note #11</a> (Representing Myanmar in
 * Unicode: Details and Examples, Version 4, 2012-12-13), in particular the table starting on page
 * 6. The version here differs from UTN 11 in that it is restricted to character combinations that
 * are used for writing the Burmese language. In the terminology of Unicode, a {@code
 * GraphemeCluster} is a <i>tailored extended grapheme cluster</i> (EGC).
 *
 * <p>When {@code toString()} is called on a {@code GraphemeCluster} object, the resulting string
 * holds the canonical character sequence representation of the grapheme cluster. In particular, it
 * follows the canonical diacritic storage order specified in UTN 11.
 */
class GraphemeCluster {
  private final Logger logger = Logger.getLogger("com.google.languageresources.my.GraphemeCluster");

  private final boolean throwOnError;

  private boolean composing;
  private boolean conflict;

  private char kinzi;
  private char main;
  private char stacked;
  private char asat1;
  private char medialY;
  private char medialR;
  private char medialW;
  private char medialH;
  private char diacriticE;
  private char diacriticI;
  private char diacriticU;
  private char diacriticAi;
  private char anusvara;
  private char vowelA;
  private char dotBelow;
  private char asat2;
  private char visarga;

  private static final char STACKING_IN_PROGRESS = '\uE039';
  private static final int PRIVATE_USE_OFFSET = '\uE000' - '\u1000';

  private static final String NOT_BURMESE = "\u1022\u1028\u1033\u1034\u1035";

  /** Constructs an empty {@code GraphemeCluster} that logs but does not throw errors. */
  public GraphemeCluster() {
    this(false);
  }

  /**
   * Constructs an empty {@code GraphemeCluster} with the specified error handling behavior.
   *
   * @param throwOnError determines if a runtime error should be thrown when an error occurs
   */
  public GraphemeCluster(boolean throwOnError) {
    this.throwOnError = throwOnError;
  }

  /** Resets the {@code GraphemeCluster} to its initial empty state. */
  public void clear() {
    composing = false;
    conflict = false;
    kinzi = 0;
    main = 0;
    stacked = 0;
    asat1 = 0;
    medialY = 0;
    medialR = 0;
    medialW = 0;
    medialH = 0;
    diacriticE = 0;
    diacriticI = 0;
    diacriticU = 0;
    diacriticAi = 0;
    anusvara = 0;
    vowelA = 0;
    dotBelow = 0;
    asat2 = 0;
    visarga = 0;
  }

  /**
   * Determines if the grapheme cluster is non-empty.
   *
   * @return {@code true} iff the grapheme cluster is non-empty
   */
  private boolean isComposing() {
    return composing;
  }

  /**
   * Determines if the grapheme cluster is complete.
   *
   * A cluster is complete when a main letter has been defined
   *
   * @return {@code true} iff the grapheme cluster is complete
   */
  public boolean isComplete() {
    return main != 0 && stacked != STACKING_IN_PROGRESS;
  }

  /**
   * Handles the error condition when an unexpected character was encountered.
   *
   * @param c the character that was unexpected
   * @param slot description of the context in which the given character was unexpected
   */
  private void unexpectedCharacter(int c, String slot) {
    StringBuilder b = new StringBuilder();
    b.append("Unexpected ").append(slot).append(" character: ").appendCodePoint(c);
    b.append(" U+").append(Integer.toHexString(c));
    String msg = b.toString();
    logger.warning(msg);
    if (throwOnError) {
      throw new Error(msg);
    }
  }

  /**
   * Determines if the given codepoint can appear in the <i>kinzi</i> slot of this cluster.
   *
   * @param c the character (Unicode code point) to be tested
   * @return {@code true} iff the character can be used as <i>kinzi</i>
   */
  static boolean canSetKinzi(int c) {
    return c == '\u1004' || c == '\u101B'; // MYANMAR LETTER NGA and RA
  }

  /**
   * Sets the <i>kinzi</i> slot of this grapheme cluster.
   *
   * @param c the character (Unicode code point) to be set as <i>kinzi</i>
   */
  void setKinzi(int c) {
    if (canSetKinzi(c)) {
      kinzi = (char) c;
      composing = true;
      standardize();
    } else {
      unexpectedCharacter(c, "kinzi");
    }
  }

  /**
   * Determines if the given character can appear in the stacked consonant slot of this cluster.
   *
   * @param c the character (Unicode code point) to be tested
   * @return {@code true} iff the character can be used as a stacked consonant
   */
  static boolean canSetStacked(int c) {
    return ('\u1000' <= c && c <= '\u1021' && c != '\u101D')
        || c == '\u103F'
        || ('\u1050' <= c && c <= '\u1055');
  }

  /**
   * Sets the stacked consonant slot of this grapheme cluster.
   *
   * @param c the character (Unicode code point) to be set as the stacked consonant
   */
  void setStacked(int c) {
    if (canSetStacked(c)) {
      stacked = (char) c;
      composing = true;
      standardize();
    } else {
      unexpectedCharacter(c, "stacked");
    }
  }

  /**
   * Determines if the given codepoint is a private-use character recognized by this class.
   *
   * @param c the character (Unicode code point) to be tested
   * @return {@code true} iff the character is a private-use character recognized by this class
   */
  private static boolean isPrivateUse(int c) {
    return ('\uE000' <= c && c <= '\uE055');
  }

  /**
   * Determines if the given codepoint can be added to this grapheme cluster.
   *
   * @param c the character (Unicode code point) to be tested
   * @return {@code true} iff the character can be added to this grapheme cluster
   */
  public static boolean canAdd(int c) {
    return ('\u1000' <= c && c <= '\u1055' && NOT_BURMESE.indexOf(c) < 0)
        || canSetKinzi(c - PRIVATE_USE_OFFSET);
  }

  /**
   * Adds a character to a grapheme cluster.
   *
   * @param c character to add to this cluster
   */
  public void add(char c) {
    if (!canAdd(c)) {
      unexpectedCharacter(c, "non-Burmese");
      return;
    }
    switch (c) {
      case '\u102B':
      case '\u102C':
        vowelA = c;
        break;

      case '\u102E':
        diacriticAi = 0;
        // fall through
      case '\u102D':
        diacriticI = c;
        asat1 = 0;
        break;

      case '\u102F':
      case '\u1030':
        diacriticU = c;
        break;

      case '\u1031':
        diacriticE = c;
        break;

      case '\u1032':
        diacriticAi = c;
        asat1 = 0;
        if (diacriticI == '\u102E') {
          diacriticI = 0;
        }
        anusvara = 0;
        break;

      case '\u1036':
        anusvara = c;
        asat1 = 0;
        diacriticAi = 0;
        break;

      case '\u1037':
        dotBelow = c;
        break;

      case '\u1038':
        visarga = c;
        break;

      case '\u1039':
        stacked = STACKING_IN_PROGRESS;
        break;

      case '\u103A':
        if (vowelA == 0) {
          asat1 = c;
          diacriticI = 0;
          diacriticAi = 0;
          anusvara = 0;
        } else {
          asat2 = c;
        }
        break;

      case '\u103B':
        medialY = c;
        break;
      case '\u103C':
        medialR = c;
        break;
      case '\u103D':
        medialW = c;
        break;
      case '\u103E':
        medialH = c;
        break;

      default:
        if (isPrivateUse(c)) {
          setKinzi(c - PRIVATE_USE_OFFSET);
          return;
        }
        if (stacked == STACKING_IN_PROGRESS) {
          setStacked(c);
          return;
        }
        int category = Character.getType(c);
        if (category == Character.COMBINING_SPACING_MARK || category == Character.NON_SPACING_MARK) {
          unexpectedCharacter(c, "main");
        }
        main = c;
        break;
    }
    composing = true;
    standardize();
  }

  /**
   * Standardizes a grapheme cluster by replacing alternative spellings with canonical versions.
   */
  private void standardize() {
    if (kinzi == 0 && canSetKinzi(main) && stacked == STACKING_IN_PROGRESS && asat1 != 0) {
      kinzi = main;
      main = 0;
      stacked = 0;
      asat1 = 0;
      return;
    }
    if (stacked == '\u1005' && medialY == '\u103B') {
      stacked = '\u1008';
      medialY = 0;
      return;
    }
    if (main == '\u1005' && stacked == 0 && medialY == '\u103B') {
      main = '\u1008';
      medialY = 0;
      return;
    }
    if (main == '\u101E' && medialR == '\u103C') {
      main = '\u1029';
      medialR = 0;
      // fall through
    }
    if (main == '\u1029' && diacriticE == '\u1031' && vowelA == '\u102C' && asat2 == '\u103A') {
      main = '\u102A';
      diacriticE = 0;
      vowelA = 0;
      asat2 = 0;
      return;
    }
    if (main == '\u1025') {
      if (kinzi != 0
          || stacked != 0
          || asat1 != 0
          || medialY != 0
          || medialR != 0
          || medialW != 0
          || medialH != 0
          || diacriticE != 0
          || diacriticU != 0
          || diacriticAi != 0
          || vowelA != 0) {
        main = '\u1009';
      } else if (diacriticI != 0 || anusvara != 0) {
        main = '\u1026';
        diacriticI = 0;
        anusvara = 0;
      }
      return;
    }
    if (main == '\u1040') {
      if (medialY == 0 && medialR == 0 && vowelA == '\u102C') {
        main = '\u1010';
        vowelA = 0;
        if (asat2 != 0) {
          asat1 = asat2;
          asat2 = 0;
        }
      } else if (kinzi != 0
          || stacked != 0
          || asat1 != 0
          || medialY != 0
          || medialR != 0
          || medialW != 0
          || medialH != 0
          || diacriticE != 0
          || diacriticI != 0
          || diacriticU != 0
          || diacriticAi != 0
          || anusvara != 0
          || vowelA != 0
          || dotBelow != 0
          || visarga != 0) {
        main = '\u101D';
      }
      return;
    }
  }

  /**
   * Conditionally appends a character to a {@code StringBuilder}. The given character will be
   * appended if it is nonzero and is not a private use character.
   *
   * @param builder {@code StringBuilder} to which the character will be appended
   * @param c character that will be appended if it is valid
   */
  private static void appendIfPresent(StringBuilder builder, char c) {
    if (c != 0 && !isPrivateUse(c)) {
      builder.append(c);
    }
  }

  /**
   * Appends the grapheme cluster to a {@code StringBuilder}.
   *
   * @param builder {@code StringBuilder} to which the grapheme cluster will be appended
   */
  public void appendTo(StringBuilder builder) {
    if (!isComposing()) {
      return;
    }

    if (main == 0) {
      builder.append('\u200C');
    }

    if (kinzi != 0) {
      builder.append(kinzi).append("\u103A\u1039");
    }

    appendIfPresent(builder, main);

    if (stacked != 0) {
      builder.append('\u1039');
      if (stacked != STACKING_IN_PROGRESS) {
        builder.append(stacked);
      }
    }

    char delayedAsat = 0;
    if (medialY == 0 && medialR == 0 && medialW == 0) {
      delayedAsat = asat1;
      appendIfPresent(builder, medialH);
    } else {
      appendIfPresent(builder, asat1);
      appendIfPresent(builder, medialY);
      appendIfPresent(builder, medialR);
      appendIfPresent(builder, medialW);
      appendIfPresent(builder, medialH);
    }

    if (diacriticE == 0
        && diacriticI == 0
        && diacriticU == 0
        && diacriticAi == 0
        && anusvara == 0
        && vowelA == 0) {
      appendIfPresent(builder, dotBelow);
      appendIfPresent(builder, delayedAsat);
    } else {
      appendIfPresent(builder, delayedAsat);
      appendIfPresent(builder, diacriticE);
      appendIfPresent(builder, diacriticI);
      appendIfPresent(builder, diacriticU);
      appendIfPresent(builder, diacriticAi);
      appendIfPresent(builder, anusvara);
      appendIfPresent(builder, vowelA);
      appendIfPresent(builder, dotBelow);
      appendIfPresent(builder, asat2);
    }

    appendIfPresent(builder, visarga);
  }

  /** Returns the grapheme cluster as a {@code String} in canonical storage order. */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    appendTo(builder);
    return builder.toString();
  }
}