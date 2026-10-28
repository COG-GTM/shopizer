package com.salesmanager.core.business.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * Validates user supplied file names before they are used to build storage paths.
 * A safe name is a single path segment: no directory separators, no ".." and no control characters.
 */
public final class FileNameValidator {

  private FileNameValidator() {
  }

  public static boolean isSafeFileName(String fileName) {
    if (StringUtils.isBlank(fileName)) {
      return false;
    }
    if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
      return false;
    }
    if (".".equals(fileName) || fileName.indexOf(':') >= 0) {
      return false;
    }
    for (int i = 0; i < fileName.length(); i++) {
      if (Character.isISOControl(fileName.charAt(i))) {
        return false;
      }
    }
    return true;
  }

}
